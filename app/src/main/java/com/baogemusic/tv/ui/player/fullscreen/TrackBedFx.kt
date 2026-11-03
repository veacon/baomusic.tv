package com.baogemusic.tv.ui.player.fullscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.baogemusic.tv.playback.LyricLine
import com.baogemusic.tv.playback.LyricParser
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 方案 B《声轨 Track Bed》—— 音轨时间轴 · 播放头游标。
 *
 * 把歌词当成一段「时间工程」：一句 = 一个轨道块，**块宽 = 该句时值**，块内小格 = 音节，
 * 屏幕底部一条贯穿的标尺 + 播放头游标就是主控件。交互只保留三件事：
 * ↑ / ↓ 跳上/下一句；OK 一律交回播放页（唤出底部控制栏）。
 *
 * 与「逐字扫光」的差别：不是把一行字染成两半颜色，而是把「时间」本身画出来 ——
 * 看得见句子的长度、看得见还没唱到的部分（虚线占位）、看得见播放头在哪。
 */
@Composable
fun TrackBedFx(
    lyrics: List<LyricLine>,
    positionProvider: () -> Long,
    fontSizeSp: Int,
    showTranslation: Boolean,
    fontFamily: FontFamily?,
    trackName: String,
    artist: String,
    /** 浅色（工程纸）/ 深色（机房）。 */
    light: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = if (light) TrackPaper else TrackRoom
    val grid = if (light) Color(0xFF101418).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.06f)
    val fg = if (light) Color(0xFF12151A) else Color(0xFFF2F4F7)
    val muted = fg.copy(alpha = 0.45f)
    val accent = if (light) Color(0xFF185FA5) else Color(0xFF85B7EB)

    val beat = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { beat.floatValue = it / 1_000_000_000f }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(bg)) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val measurer = rememberTextMeasurer(cacheSize = 256)

        val pps = wPx * 0.080f                   // 每秒像素（字号放大后需要更宽的时间刻度）
        val cursorX = wPx * 0.42f               // 播放头位置（左侧留已唱，右侧给未来）
        val topH = hPx * 0.13f
        val rulerH = hPx * 0.17f
        val rulerY = hPx - rulerH * 0.62f
        // 歌词字号：跟用户的「歌词字号」设置走（默认 40sp ≈ 原来 13sp 的 3 倍）。
        val blockSp = fontSizeSp.coerceIn(30, 52).sp
        val blockH = (hPx * 0.22f).coerceAtLeast(120f)
        val trackTop = ((topH + rulerY) / 2f - blockH / 2f).coerceAtLeast(topH)

        val blockStyle = TextStyle(fontSize = blockSp, fontFamily = fontFamily, fontWeight = FontWeight.Normal)
        val monoStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)

        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") beat.floatValue
            if (size.minDimension < 2f) return@Canvas
            val now = positionProvider()
            val active = LyricParser.activeIndex(now / 1000.0, lyrics)

            // ---- 网格（每 2 秒一条竖线）----------------------------------------------------
            val from = ((now - 10_000) / 1000) * 1000
            var t = from
            while (t <= now + 14_000) {
                val x = cursorX + (t - now) / 1000f * pps
                if (x > -2f && x < size.width + 2f) {
                    drawLine(grid, Offset(x, trackTop - 12f), Offset(x, rulerY), strokeWidth = 1f)
                }
                t += 2000L
            }

            // ---- 轨道块 ------------------------------------------------------------------
            for (i in lyrics.indices) {
                val line = lyrics[i]
                if (line.time < 0) continue
                val startMs = (line.time * 1000).toLong()
                val endMs = nextLineStartMs(lyrics, i)
                val x0 = cursorX + (startMs - now) / 1000f * pps
                val x1 = cursorX + (endMs - now) / 1000f * pps
                if (x1 < -60f || x0 > size.width + 60f) continue
                val w = (x1 - x0).coerceAtLeast(18f)
                val isActive = i == active
                val past = now >= endMs

                // 块底
                if (isActive) {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.12f),
                        topLeft = Offset(x0, trackTop),
                        size = Size(w, blockH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
                    )
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(x0, trackTop),
                        size = Size(w, blockH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
                        style = Stroke(width = 2f),
                    )
                } else if (past) {
                    drawRoundRect(
                        color = fg.copy(alpha = 0.07f),
                        topLeft = Offset(x0, trackTop),
                        size = Size(w, blockH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
                    )
                    drawRoundRect(
                        color = fg.copy(alpha = 0.18f),
                        topLeft = Offset(x0, trackTop),
                        size = Size(w, blockH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
                        style = Stroke(width = 1f),
                    )
                } else {
                    // 未唱：虚线占位框（「看得见的未来」）
                    drawRoundRect(
                        color = fg.copy(alpha = 0.18f),
                        topLeft = Offset(x0, trackTop),
                        size = Size(w, blockH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
                        style = Stroke(width = 1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                    )
                }

                // 块内文字
                val label = line.text.ifBlank { "♪" }
                val layout = measurer.measure(
                    text = label,
                    style = blockStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = (w - 28f).roundToInt().coerceAtLeast(8)),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x0 + 14f, trackTop + 12f),
                    color = if (isActive) fg else if (past) fg.copy(alpha = 0.45f) else fg.copy(alpha = 0.28f),
                )

                // 译文（小字，压在块底部）
                if (showTranslation) {
                    line.translation?.takeIf { it.isNotBlank() }?.let { tr ->
                        val tl = measurer.measure(
                            text = tr,
                            style = blockStyle.copy(fontSize = (fontSizeSp * 0.42f).sp),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            constraints = androidx.compose.ui.unit.Constraints(maxWidth = (w - 28f).roundToInt().coerceAtLeast(8)),
                        )
                        drawText(
                            textLayoutResult = tl,
                            topLeft = Offset(x0 + 14f, trackTop + blockH - tl.size.height - 10f),
                            color = fg.copy(alpha = if (isActive) 0.6f else 0.28f),
                        )
                    }
                }

                // 当前块：音节格（每格 = 一个音节，按时间填充）
                if (isActive) {
                    val words = line.words
                    if (words.isNotEmpty()) {
                        val lineSpan = (endMs - startMs).coerceAtLeast(1L)
                        val cellY = trackTop + blockH + 12f
                        for (wd in words) {
                            val wx = x0 + (wd.startMs - startMs) / lineSpan.toFloat() * w
                            val ww = (wd.endMs - wd.startMs) / lineSpan.toFloat() * w
                            val p = ((now - wd.startMs).toFloat() / (wd.endMs - wd.startMs).coerceAtLeast(1L))
                                .coerceIn(0f, 1f)
                            drawRoundRect(
                                color = accent.copy(alpha = 0.22f),
                                topLeft = Offset(wx, cellY),
                                size = Size(max(ww, 4f), 8f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
                            )
                            if (p > 0f) {
                                drawRoundRect(
                                    color = accent.copy(alpha = 0.55f + 0.45f * p),
                                    topLeft = Offset(wx, cellY),
                                    size = Size(max(ww * p, 3f), 8f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
                                )
                            }
                        }
                    }
                }
            }

            // ---- 播放头 ------------------------------------------------------------------
            drawLine(
                color = fg,
                start = Offset(cursorX, trackTop - 26f),
                end = Offset(cursorX, rulerY + 6f),
                strokeWidth = 2f,
            )
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cursorX - 6f, trackTop - 30f)
                    lineTo(cursorX + 6f, trackTop - 30f)
                    lineTo(cursorX, trackTop - 20f)
                    close()
                },
                color = fg,
            )

            // ---- 标尺 --------------------------------------------------------------------
            drawLine(
                color = fg.copy(alpha = 0.22f),
                start = Offset(0f, rulerY),
                end = Offset(size.width, rulerY),
                strokeWidth = 1f,
            )
            var ts = ((now - 10_000) / 1000) * 1000
            while (ts <= now + 14_000) {
                val x = cursorX + (ts - now) / 1000f * pps
                if (x in -20f..size.width + 20f) {
                    val major = (ts % 5000L == 0L)
                    drawLine(
                        color = fg.copy(alpha = if (major) 0.5f else 0.22f),
                        start = Offset(x, rulerY),
                        end = Offset(x, rulerY + (if (major) 9f else 5f)),
                        strokeWidth = 1f,
                    )
                    if (major) {
                        val lbl = fmtClock(ts)
                        val ml = measurer.measure(lbl, monoStyle)
                        drawText(
                            textLayoutResult = ml,
                            topLeft = Offset(x - ml.size.width / 2f, rulerY + 12f),
                            color = fg.copy(alpha = 0.45f),
                        )
                    }
                }
                ts += 1000L
            }
        }

        // ---- 顶部：歌名 / 歌手 ------------------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 44.dp, top = 30.dp),
        ) {
            Text(trackName, color = fg, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(artist, color = muted, fontSize = 13.sp, maxLines = 1)
        }

        Text(
            text = if (light) "工程纸模式" else "机房模式",
            color = muted,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 44.dp, top = 34.dp),
        )

        // ---- 底部提示 --------------------------------------------------------------------
        Text(
            text = "↑ ↓ 上/下一句    OK 唤出控制栏",
            color = fg.copy(alpha = 0.4f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 44.dp, bottom = 26.dp),
        )
    }
}

/** 某句结束时间（毫秒）：下一句起点，没有就 +4 秒。 */
private fun nextLineStartMs(lyrics: List<LyricLine>, index: Int): Long {
    val line = lyrics.getOrNull(index) ?: return 0L
    val next = lyrics.getOrNull(index + 1)
    return if (next != null && next.time > line.time) (next.time * 1000).toLong()
    else (line.time * 1000).toLong() + 4000L
}

/** mm:ss */
private fun fmtClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

// ============== 配色 =========================================================================

/** 工程纸（浅）。 */
internal val TrackPaper = Color(0xFFF6F7F9)
/** 机房（深）。 */
internal val TrackRoom = Color(0xFF0E1013)
