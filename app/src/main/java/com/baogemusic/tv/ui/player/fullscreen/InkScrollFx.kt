package com.baogemusic.tv.ui.player.fullscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.baogemusic.tv.playback.LyricLine
import com.baogemusic.tv.playback.LyricParser
import com.baogemusic.tv.playback.LyricWord
import com.baogemusic.tv.playback.LyricWordParser
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 方案 A《墨卷 Ink Scroll》—— 竖排手卷 · 逐字落墨。
 *
 * 排版：竖排、自右向左（传统手卷）。当前句在右侧（大字），已写过的句子以淡墨留在左边，
 * 未来句留白。逐字不是「颜色扫过」，而是**落笔**：字从淡到浓、微微缩放，字心洇开一圈墨晕；
 * 一句唱完整列向左「沉入纸面」。背景是宣纸纹（不用封面实拍图），封面只以朱砂方章落在左下角。
 *
 * 全部动效都由播放位置推导（落笔进度 = 现在 − 该字起始时间），所以天然与音频同步，
 * 也不需要维护动画状态；逐帧量只在绘制阶段读取，不触发重组。
 */
@Composable
fun InkScrollFx(
    lyrics: List<LyricLine>,
    /** 播放器的媒体时钟（毫秒，帧级连续）。逐字进度以它为唯一真值。 */
    positionProvider: () -> Long,
    fontSizeSp: Int,
    showTranslation: Boolean,
    fontFamily: FontFamily?,
    trackName: String,
    artist: String,
    /** 浅色纸面（宣纸）/ 深色纸面（夜墨）。 */
    light: Boolean,
    modifier: Modifier = Modifier,
) {
    val paper = if (light) InkPaper else InkNight
    val ink = if (light) InkNightInk else InkNightInkLight
    val faint = ink.copy(alpha = 0.32f)
    val seal = if (light) InkSeal else InkSealLight

    if (lyrics.isEmpty()) {
        Box(modifier.fillMaxSize().background(paper), contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = ink.copy(alpha = 0.45f), fontSize = 18.sp)
        }
        return
    }

    // 每帧推进一次「心跳」：Canvas 只在绘制阶段读它，于是只触发重绘、不会引起重组。
    val beat = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { beat.floatValue = it / 1_000_000_000f }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(paper)) {
        val density = LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val measurer = rememberTextMeasurer(cacheSize = 512)

        // 字号：以屏高为基准（竖排按高度排），同时受用户的歌词字号设置约束。
        val userPx = with(density) { fontSizeSp.sp.toPx() }
        val minFont = max(18f, userPx * 0.9f)
        val maxFont = max(minFont, userPx * 1.7f)
        val fontPx = (hPx * 0.052f).coerceIn(minFont, maxFont)
        val histFontPx = fontPx * 0.82f
        val charH = fontPx * 1.28f
        val padH = wPx * 0.055f
        val padV = hPx * 0.10f
        val contentH = (hPx - padV * 2f).coerceAtLeast(fontPx * 4f)
        val charsPerCol = max(4, floor(contentH / charH).toInt())

        // 列间距：用户反馈两列太挤 —— 由 1.32 倍字宽放宽到 1.85 倍，两组之间再留一道空隙。
        val slotCur = fontPx * 1.85f
        val slotHist = histFontPx * 1.85f
        val groupGap = fontPx * 0.55f

        val curStyle = TextStyle(
            fontSize = fontPx.sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.06.em,
        )
        val histStyle = curStyle.copy(fontSize = histFontPx.sp)
        val transStyle = curStyle.copy(fontSize = (fontPx * 0.42f).sp, fontWeight = FontWeight.Normal)

        // 纸纹噪点：固定种子，只算一次（不能在绘制阶段调 remember）。
        val speckles = remember { speckleField() }
        // 每句的逐字时间片缓存：切歌时重算一次，绘制阶段直接取。
        val charCache = remember(lyrics) { HashMap<Int, List<InkChar>>() }
        // 绘制异常只记一次日志，避免逐帧刷屏。
        var reportedError = false

        /** 真正的绘制体：抽成局部函数，外层包 runCatching —— 视觉特效再怎么样也不该让播放页闪退。 */
        fun DrawScope.render() {
            // 心跳读一次，保证每帧重绘（下面的进度是纯计算，读不到 state 变化）。
            @Suppress("UNUSED_EXPRESSION") beat.floatValue
            if (size.minDimension < 2f) return

            val now = positionProvider()
            val active = LyricParser.activeIndex(now / 1000.0, lyrics)
            if (active < 0) return

            // ---- 纸纹：细噪点 + 极淡横向纤维 ------------------------------------------------
            for (s in speckles) {
                drawCircle(
                    color = ink.copy(alpha = s[3]),
                    radius = s[2],
                    center = Offset(s[0] * size.width, s[1] * size.height),
                )
            }
            for (i in 1..7) {
                val y = size.height * (i / 8f)
                drawRect(
                    color = ink.copy(alpha = 0.018f),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, 1f),
                )
            }
            // 四周压一点点，中心透出来（宣纸的「受光」感）。半径必须 > 0，
            // 否则 android.graphics.RadialGradient 会抛 IllegalArgumentException（闪退来源之一）。
            val vignetteR = size.maxDimension * 0.75f
            if (vignetteR > 1f) {
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to ink.copy(alpha = 0.10f),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = vignetteR,
                    ),
                )
            }

            // ---- 列排布：从右往左 -----------------------------------------------------------
            val slots = ArrayList<InkSlot>(8)
            var cursorX = size.width - padH
            val focusCols = splitColumns(charCache, lyrics, active, charsPerCol)
            for (c in focusCols) {
                if (cursorX - fontPx * 0.5f < 0f) break
                slots += InkSlot(c, cursorX - fontPx * 0.5f, isFocus = true)
                cursorX -= slotCur
            }
            // 当前句与历史句之间再让开一道空隙
            cursorX -= groupGap
            var li = active - 1
            var dist = 1
            while (li >= 0 && dist <= 3) {
                val cols = splitColumns(charCache, lyrics, li, charsPerCol)
                for (c in cols) {
                    if (cursorX - histFontPx * 0.5f < padH * 0.5f) break
                    slots += InkSlot(c, cursorX - histFontPx * 0.5f, isFocus = false, dist = dist)
                    cursorX -= slotHist
                }
                li--
                dist++
            }

            // ---- 逐列逐字绘制 --------------------------------------------------------------
            for (slot in slots) {
                val lineIndex = slot.chars.firstOrNull()?.lineIndex ?: continue
                val line = lyrics.getOrNull(lineIndex) ?: continue
                val style = if (slot.isFocus) curStyle else histStyle
                val step = if (slot.isFocus) charH else charH * 0.82f
                val colTop = (size.height - slot.chars.size * step) / 2f

                // 整列的淡出与「沉纸」位移
                var alphaMul = if (slot.isFocus) {
                    1f
                } else {
                    when (slot.dist) {
                        1 -> 0.52f
                        2 -> 0.40f
                        else -> 0.30f
                    }
                }
                var xShift = 0f
                if (slot.dist == 1) {
                    val lineEnd = lineEndMs(lyrics, lineIndex)
                    val sink = ((now - (lineEnd + 400)) / 600f).coerceIn(0f, 1f)
                    if (sink < 1f) {
                        alphaMul = 1f - (1f - 0.52f) * sink
                        xShift = -12f * sink
                    }
                }

                val x0 = slot.x + xShift
                slot.chars.forEachIndexed { ci, ch ->
                    val age = now - ch.startMs
                    val written = age >= 0
                    val p = if (written) (age / 180f).coerceIn(0f, 1f) else 0f
                    val ease = p * p * (3f - 2f * p)
                    // 未写 = 极淡的「预告」；写的过程 = 落笔（淡 → 浓，缩放 0.86 → 1）
                    val baseAlpha = if (!written) 0.16f else 0.16f + 0.84f * ease
                    val scale = if (!written) 1f else 0.86f + 0.14f * ease
                    // 拖腔：单字唱超过 1.2 秒时轻微呼吸
                    val hold = (ch.endMs - ch.startMs) >= 1200L && written && age <= (ch.endMs - ch.startMs)
                    val breathe = if (hold) 1f + 0.04f * sin(beat.floatValue * 2f * PI.toFloat() * 1.2f) else 1f
                    val alpha = (baseAlpha * alphaMul).coerceIn(0f, 1f)
                    if (alpha <= 0.01f) return@forEachIndexed

                    val cy = colTop + ci * step
                    val layout: TextLayoutResult = measurer.measure(ch.ch, style)
                    val w = layout.size.width
                    val h = layout.size.height
                    val center = Offset(x0, cy + h * 0.5f)

                    // 墨晕：刚落笔的字，字心扩散一圈淡墨（半径必须 > 0）
                    if (written && age < 300L) {
                        val t = age / 300f
                        val r = fontPx * 1.4f * t
                        if (r > 0.8f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(ink.copy(alpha = 0.42f * (1f - t)), Color.Transparent),
                                    center = center,
                                    radius = r,
                                ),
                                radius = r,
                                center = center,
                            )
                        }
                    }

                    withTransform({
                        translate(center.x, center.y)
                        scale(scale * breathe, scale * breathe)
                        translate(-center.x, -center.y)
                    }) {
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(x0 - w / 2f, cy),
                            color = ink,
                            alpha = alpha,
                        )
                    }
                }

                // 译文：竖排「夹注」小字，贴在原文列右侧半字宽处
                if (showTranslation) {
                    val tr = line.translation?.takeIf { it.isNotBlank() }
                    if (tr != null) {
                        val tl = measurer.measure(tr, transStyle)
                        drawText(
                            textLayoutResult = tl,
                            topLeft = Offset(x0 + fontPx * 0.62f, colTop),
                            color = ink,
                            alpha = 0.42f * alphaMul,
                        )
                    }
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            // 兜底：绘制异常只丢这一帧，不影响播放（异常记一次日志，方便定位）。
            runCatching { render() }.onFailure { err ->
                if (!reportedError) {
                    reportedError = true
                    android.util.Log.e("InkScrollFx", "draw failed", err)
                }
            }
        }

        // ---- 左下角：朱砂方章（落款）+ 歌名 / 歌手 ------------------------------------------
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 40.dp, bottom = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(seal, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = trackName.take(1).ifEmpty { "歌" },
                    color = paper,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(trackName, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text(artist, color = faint, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

// ============== 数据结构与工具 ==============================================================

/** 一个字（含它在句中的位置与音节时间）。 */
private class InkChar(
    val ch: String,
    val startMs: Long,
    val endMs: Long,
    val lineIndex: Int,
)

/** 一列（竖排的一串字）。 */
private class InkColumn(val chars: List<InkChar>)

/** 列在画面上的位置与状态。 */
private class InkSlot(
    val column: InkColumn,
    val x: Float,
    val isFocus: Boolean,
    val dist: Int = 0,
) {
    val chars: List<InkChar> get() = column.chars
}

/** 取某一句的逐字时间片：优先用真音节，没有就按标点权重估算。结果缓存复用。 */
private fun lineChars(
    lyrics: List<LyricLine>,
    index: Int,
    cache: HashMap<Int, List<InkChar>>,
): List<InkChar> {
    cache[index]?.let { return it }
    val line = lyrics.getOrNull(index)
    if (line == null) return emptyList()
    // 无时间戳的纯文本行（time = -1）不参与竖排。
    if (line.time < 0) return emptyList()
    val words: List<LyricWord> = if (line.words.isNotEmpty()) {
        line.words
    } else {
        val next = lyrics.getOrNull(index + 1)
        val endMs = if (next != null && next.time > line.time) (next.time * 1000).toLong()
        else (line.time * 1000).toLong() + 4000L
        LyricWordParser.distribute(line.text, (line.time * 1000).toLong(), endMs)
    }
    val out = ArrayList<InkChar>(words.size)
    for (w in words) {
        // 一个「词」可能是多个字（英文单词 / 标签词）。按 code point 拆，
        // 避免把 emoji 拆成半个代理对（送到排版层会出问题）。
        var i = 0
        while (i < w.text.length) {
            val cp = w.text.codePointAt(i)
            out += InkChar(String(Character.toChars(cp)), w.startMs, w.endMs, index)
            i += Character.charCount(cp)
        }
    }
    cache[index] = out
    return out
}

/** 一句按列高断成若干列（优先在标点后断开）。 */
private fun splitColumns(
    cache: HashMap<Int, List<InkChar>>,
    lyrics: List<LyricLine>,
    index: Int,
    charsPerCol: Int,
): List<InkColumn> {
    val chars = lineChars(lyrics, index, cache)
    if (chars.isEmpty()) return emptyList()
    val cols = mutableListOf<InkColumn>()
    var i = 0
    while (i < chars.size) {
        var end = min(i + charsPerCol, chars.size)
        if (end < chars.size) {
            val brk = (end downTo (i + charsPerCol / 2)).firstOrNull {
                chars[it - 1].ch in "，。、；：？！,.;:!?"
            }
            if (brk != null) end = brk
        }
        cols += InkColumn(chars.subList(i, end))
        i = end
    }
    return cols
}

/** 某句结束的时间（毫秒）：下一句的起点，没有就用 4 秒兜底。 */
private fun lineEndMs(lyrics: List<LyricLine>, index: Int): Long {
    val line = lyrics.getOrNull(index) ?: return 0L
    val next = lyrics.getOrNull(index + 1)
    return if (next != null && next.time > line.time) (next.time * 1000).toLong()
    else (line.time * 1000).toLong() + 4000L
}

/** 纸面噪点：固定种子，换歌不换纹理（避免每帧重新生成）。 */
private fun speckleField(): Array<FloatArray> {
    val rnd = kotlin.random.Random(20261103)
    return Array(220) {
        floatArrayOf(
            rnd.nextFloat(),
            rnd.nextFloat(),
            0.6f + rnd.nextFloat() * 1.4f,
            0.012f + rnd.nextFloat() * 0.022f,
        )
    }
}

// ============== 配色 =========================================================================

/** 宣纸（浅）。 */
internal val InkPaper = Color(0xFFF3EADA)
/** 夜墨（深）：纸底。 */
internal val InkNight = Color(0xFF121110)
/** 墨色：浅纸用焦墨，深纸用米白。 */
internal val InkNightInk = Color(0xFF1B1A17)
internal val InkNightInkLight = Color(0xFFEDE7DA)
/** 朱砂。 */
internal val InkSeal = Color(0xFFB23A2E)
internal val InkSealLight = Color(0xFFE0564B)
