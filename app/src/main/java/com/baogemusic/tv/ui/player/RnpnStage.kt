package com.baogemusic.tv.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.imageLoader
import com.baogemusic.tv.data.model.Track
import com.baogemusic.tv.playback.LyricLine
import com.baogemusic.tv.playback.LyricParser
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Apple Music 风格播放页 —— 按 betterncm-mac 的 RNPN 歌词窗「一比一」还原。
 *
 * 参考实现（本文件的关键常数都能在上游找到出处）：
 *  - `resources/lyrics/web/js/lyrics.js` 的 `computeFontPx` / `effects` / `layout` 三个函数；
 *  - `resources/lyrics/web/css/rnpn-lyrics.css` 的 `.rnp-lyrics-line` 透明度与字号规则；
 *  - `resources/lyrics/web/css/rnpn-main.css` 的 `.sd`（封面栏 clamp(200px,20vw,500px)）
 *    与 `.lyric`（歌词栏 width:calc(45% - 10px) / left:min(50vw,60%)）布局；
 *  - `resources/lyrics/web/css/rnpn-bg.css` 的背景（模糊封面 + 强调色蒙版 55%）。
 *
 * 与上游的对应关系：
 *  - 每行 `translateY(top) scale(k) blur(k) opacity(k)` —— [rnpnLineEffects]；
 *  - 当前行锚在容器 50% 高度（RNPN `alignment` 默认 50），其余行按 `h*scale + 1.2*fontPx` 依次推开；
 *  - `transform-origin: left center` 对应 [TransformOrigin] (0f, 0.5f)；
 *  - 歌词行内原文/译文透明度：当前行原文 1.0、译文 0.8；非当前行 0.4 —— CSS 的
 *    `.rnp-lyrics-line[offset="0"]>div` 与 `:not([offset="0"])>div`。
 */

/** RNPN 歌词行的距离效果。k = 该行到当前行的行距（0 = 当前行）。 */
internal data class RnpnLineFx(val scale: Float, val blurPx: Float, val opacity: Float)

/** upstream `ZOOM_RANGE`。非当前行的缩放衰减幅度。 */
private const val RNPN_ZOOM_RANGE = 0.08f

/**
 * 对应 upstream `effects(k)`。三个开关默认全开（RNPN 默认行为）：
 *  - zoom：非当前行按 `(1 - 0.2k)^3` 递减，幅度 [RNPN_ZOOM_RANGE]；
 *  - blur：当前行 0，其余 `min(0.5 + k, 4.5)` px；
 *  - fade：k ≤ 1 不衰减，之后每远一行降 0.4，到 0 为止。
 */
internal fun rnpnLineEffects(
    k: Int,
    zoom: Boolean = true,
    blur: Boolean = true,
    fade: Boolean = true,
): RnpnLineFx {
    val scale = if (zoom) {
        max(1f - 0.2f * k, 0f).toDouble().pow(3.0).toFloat() * RNPN_ZOOM_RANGE + 1f - RNPN_ZOOM_RANGE
    } else 1f
    val blurPx = if (blur && k != 0) min(0.5f + k, 4.5f) else 0f
    val opacity = if (fade) {
        if (k <= 1) 1f else max(1f - 0.4f * (k - 1), 0f)
    } else 1f
    return RnpnLineFx(scale, blurPx, opacity)
}

/**
 * 整页 Apple Music 风格舞台。RNPN 是整屏全幅（不像「复刻网易」还留底部控制栏），
 * 因此调用方直接给 fillMaxSize。
 */
@Composable
internal fun RnpnStage(
    track: Track,
    lyrics: List<LyricLine>,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isShuffle: Boolean,
    isFaved: Boolean,
    lyricFontSizeSp: Int,
    showTranslation: Boolean,
    lyricFontFamily: FontFamily?,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleFav: () -> Unit,
    onShowQueue: () -> Unit,
    onShowMore: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 浅色强调：从封面取主色，背景提亮、文字压暗 —— 对应 RNPN 的
    // --rnp-accent-color-overlay / --rnp-accent-color-shade-2。
    val accent = rememberCoverAccent(track.picURL)
    val bgTint = accent.blend(Color(0xFFF2EDE6), ratio = 0.42f).copy(alpha = 0.55f)
    val ink = accent.darken(0.82f)

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Box(modifier = modifier.fillMaxSize()) {
        RnpnBackground(track.picURL, bgTint)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── 左栏：封面 + 歌名 + 歌手 + 进度 + 控制（.sd：clamp(200px, 20vw, 500px)）
            RnpnLeftColumn(
                track = track,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                isShuffle = isShuffle,
                isFaved = isFaved,
                ink = ink,
                onTogglePlay = onTogglePlay,
                onPrev = onPrev,
                onNext = onNext,
                onToggleShuffle = onToggleShuffle,
                onToggleFav = onToggleFav,
                onShowQueue = onShowQueue,
                onShowMore = onShowMore,
                onSeekTo = onSeekTo,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(screenWidth * 0.22f),
            )

            Spacer(Modifier.width(screenWidth * 0.05f))

            // ── 右栏：RNPN 歌词（.lyric：left:min(50vw,60%)、width:calc(45% - 10px)）
            RnpnLyrics(
                lyrics = lyrics,
                positionMs = positionMs,
                fontSizeSp = lyricFontSizeSp,
                showTranslation = showTranslation,
                fontFamily = lyricFontFamily,
                ink = ink,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(end = 24.dp),
            )
        }
    }
}

/** 背景：模糊封面 + 强调色蒙版（`.rnp-background-blur` + `::before` 蒙版 55%）。 */
@Composable
private fun RnpnBackground(picURL: String?, tint: Color) {
    Box(Modifier.fillMaxSize()) {
        if (!picURL.isNullOrBlank()) {
            val context = LocalContext.current
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(picURL)
                    .transformations(
                        com.baogemusic.tv.ui.components.BlurTransformation(context, radius = 36f, sampling = 3f),
                    )
                    .crossfade(500)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(tint))
        // 再叠一层暖白，把整体提到 Apple Music 的浅色调（截图里的奶油底色）。
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.18f),
                    0.5f to Color.White.copy(alpha = 0.06f),
                    1f to Color.White.copy(alpha = 0.20f),
                ),
            ),
        )
    }
}

// ============== 左栏 ============================================================================

@Composable
private fun RnpnLeftColumn(
    track: Track,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isShuffle: Boolean,
    isFaved: Boolean,
    ink: Color,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleFav: () -> Unit,
    onShowQueue: () -> Unit,
    onShowMore: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        // 封面：rectangle-cover 模式 —— 圆角 16px + 柔和投影
        Box(Modifier.fillMaxWidth()) {
            coil.compose.AsyncImage(
                model = track.picURL,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
            )
            // 收藏：封面右下角圆形玻璃钮（.rnp-cover-favorite-button）
            RnpnIconButton(
                icon = if (isFaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (isFaved) Color(0xFFFF3B5C) else ink.copy(alpha = 0.72f),
                onClick = onToggleFav,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                glass = true,
            )
        }

        Spacer(Modifier.height(26.dp))

        Text(
            track.name,
            color = ink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.singer,
                    color = ink.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                track.albumName?.takeIf { it.isNotBlank() }?.let { album ->
                    Text(
                        album,
                        color = ink.copy(alpha = 0.38f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // ···  更多（.rnp-info-more-button）
            RnpnIconButton(
                icon = Icons.Filled.MoreHoriz,
                tint = ink.copy(alpha = 0.62f),
                onClick = onShowMore,
            )
        }

        Spacer(Modifier.height(22.dp))

        RnpnProgress(
            positionMs = positionMs,
            durationMs = durationMs,
            ink = ink,
            onSeekTo = onSeekTo,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(positionMs), color = ink.copy(alpha = 0.45f), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "-" + fmt((durationMs - positionMs).coerceAtLeast(0)),
                color = ink.copy(alpha = 0.45f),
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(22.dp))

        // 控制行：shuffle / 上一首 / 播放暂停 / 下一首 / 队列
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RnpnIconButton(
                icon = Icons.Filled.Shuffle,
                tint = if (isShuffle) ink else ink.copy(alpha = 0.55f),
                onClick = onToggleShuffle,
            )
            RnpnIconButton(
                icon = Icons.Filled.SkipPrevious,
                tint = ink.copy(alpha = 0.85f),
                onClick = onPrev,
            )
            RnpnIconButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                tint = ink,
                onClick = onTogglePlay,
                large = true,
            )
            RnpnIconButton(
                icon = Icons.Filled.SkipNext,
                tint = ink.copy(alpha = 0.85f),
                onClick = onNext,
            )
            RnpnIconButton(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                tint = ink.copy(alpha = 0.55f),
                onClick = onShowQueue,
            )
        }
    }
}

@Composable
private fun RnpnIconButton(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    glass: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(if (large) 46.dp else 38.dp)
            .then(
                if (glass) {
                    Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))
                } else Modifier,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(if (large) 30.dp else 24.dp),
        )
    }
}

@Composable
private fun RnpnProgress(
    positionMs: Long,
    durationMs: Long,
    ink: Color,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frac = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = frac,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "rnpnProgress",
    )
    Box(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(2.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onSeekTo((durationMs * frac).toLong()) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(ink.copy(alpha = 0.18f)))
        Box(Modifier.fillMaxWidth(animated).height(3.dp).background(ink.copy(alpha = 0.88f)))
    }
}

// ============== 右栏：RNPN 歌词 =================================================================

/**
 * RNPN 歌词列。逐行绝对定位，按 [rnpnLineEffects] 做缩放 / 模糊 / 淡出，
 * 顶部与底部 3% 用 alpha 蒙版收口（`.rnp-lyrics` 的 mask-image）。
 */
@Composable
private fun RnpnLyrics(
    lyrics: List<LyricLine>,
    positionMs: Long,
    fontSizeSp: Int,
    showTranslation: Boolean,
    fontFamily: FontFamily?,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    if (lyrics.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = ink.copy(alpha = 0.45f), fontSize = 18.sp)
        }
        return
    }

    val active = LyricParser.activeIndex(positionMs / 1000.0, lyrics)
    // upstream computeFontPx：base * k，k 取窗口/屏幕比例（TV 全屏 k≈1），下限 16px。
    val cfg = LocalConfiguration.current
    val kScale = min(1f, min(cfg.screenWidthDp / 1080f, cfg.screenHeightDp / 608f))
    val fontPx = max(16f, fontSizeSp * kScale)
    val space = 1.2f * fontPx
    val alignFactor = 0.5f // RNPN alignment 默认 50%

    BoxWithConstraints(modifier = modifier.fillMaxSize().rnpnMaskEdges()) {
        val boxH = constraints.maxHeight.toFloat()
        val n = lyrics.size
        // 每行高度 = 原文 1.2 行高 +（有译文时）译文 1.2 行高 + 0.3em 行间距。
        // 译文与原文同号：CSS `--lyric-translation-size-em` 默认 1em。
        val heights = FloatArray(n) { i ->
            val hasTrans = showTranslation && !lyrics[i].translation.isNullOrBlank()
            fontPx * 1.2f * (if (hasTrans) 2f else 1f) + (if (hasTrans) fontPx * 0.3f else 0f)
        }
        val d = active.coerceIn(0, n - 1)
        val top = FloatArray(n)
        top[d] = boxH * alignFactor - heights[d] / 2f
        for (i in d - 1 downTo 0) {
            val fx = rnpnLineEffects(abs(i - d))
            top[i] = top[i + 1] - heights[i] * fx.scale - space
        }
        for (i in d + 1 until n) {
            top[i] = top[i - 1] + heights[i - 1] * rnpnLineEffects(abs((i - 1) - d)).scale + space
        }

        for (i in 0 until n) {
            // 离屏太远的行不绘制（upstream 的 offscreen class），省合成开销
            val visible = top[i] + heights[i] > -boxH * 0.6f && top[i] < boxH * 1.6f
            if (!visible) continue
            val fx = rnpnLineEffects(abs(i - d))
            RnpnLyricLine(
                line = lyrics[i],
                isCurrent = i == d,
                fontPx = fontPx,
                showTranslation = showTranslation,
                fontFamily = fontFamily,
                ink = ink,
                fx = fx,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // transform-origin: left center
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        translationY = top[i]
                        scaleX = fx.scale
                        scaleY = fx.scale
                        alpha = fx.opacity
                    },
            )
        }
    }
}

@Composable
private fun RnpnLyricLine(
    line: LyricLine,
    isCurrent: Boolean,
    fontPx: Float,
    showTranslation: Boolean,
    fontFamily: FontFamily?,
    ink: Color,
    fx: RnpnLineFx,
    modifier: Modifier = Modifier,
) {
    // `.rnp-lyrics-line>div`：当前行原文 1.0 / 译文 0.8；非当前行 0.4。
    val originalAlpha = if (isCurrent) 1f else 0.4f
    val transAlpha = if (isCurrent) 0.8f else 0.4f
    // 行模糊：Compose 的 Modifier.blur 在 API 31+ 才有 RenderEffect 支撑，
    // Android 8/9 上自动降级为不模糊（缩放 + 淡出仍然完整）。
    val textMod = if (fx.blurPx > 0.01f) {
        Modifier.blur(radius = (fx.blurPx / 2f).dp)
    } else Modifier

    Column(modifier.then(textMod)) {
        Text(
            line.text,
            color = ink.copy(alpha = originalAlpha),
            fontSize = fontPx.sp,
            lineHeight = (fontPx * 1.2f).sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Start,
            fontFamily = fontFamily,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showTranslation) {
            line.translation?.takeIf { it.isNotBlank() }?.let { t ->
                Spacer(Modifier.height((fontPx * 0.3f).dp))
                Text(
                    t,
                    color = ink.copy(alpha = transAlpha),
                    fontSize = fontPx.sp,
                    lineHeight = (fontPx * 1.2f).sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start,
                    fontFamily = fontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 顶部 / 底部 3% 的 alpha 渐隐（`.rnp-lyrics` 的 `-webkit-mask-image`）。 */
private fun Modifier.rnpnMaskEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.03f to Color.Black,
                0.97f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

// ============== 颜色工具 ========================================================================

/**
 * 从封面取主色（RNPN 的 `--rnp-accent-color-*` 全部由专辑封面算出）。
 * 拉不到封面时退回暖棕灰，保证浅色文字在任何图上都可读。
 */
@Composable
private fun rememberCoverAccent(picURL: String?): Color {
    var accent by remember(picURL) { mutableStateOf(Color(0xFF8A6E5C)) }
    if (!picURL.isNullOrBlank()) {
        val context = LocalContext.current
        LaunchedEffect(picURL) {
            runCatching {
                val req = coil.request.ImageRequest.Builder(context)
                    .data(picURL)
                    .allowHardware(false)
                    .size(64, 64)
                    .build()
                val result = context.imageLoader.execute(req)
                val bmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bmp != null) {
                    var r = 0L; var g = 0L; var b = 0L; var n = 0L
                    for (y in 0 until bmp.height step 2) {
                        for (x in 0 until bmp.width step 2) {
                            val c = bmp.getPixel(x, y)
                            r += android.graphics.Color.red(c)
                            g += android.graphics.Color.green(c)
                            b += android.graphics.Color.blue(c)
                            n++
                        }
                    }
                    if (n > 0) {
                        accent = Color(
                            (r / n).toInt().coerceIn(0, 255),
                            (g / n).toInt().coerceIn(0, 255),
                            (b / n).toInt().coerceIn(0, 255),
                        )
                    }
                }
            }
        }
    }
    return accent
}

/** mm:ss，与 PlayerScreen 里的 fmt 同规格（各自文件内私有，互不影响）。 */
private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%02d:%02d".format(s / 60, s % 60)
}

/** 把主色向 [other] 插值 —— 用来把深色封面主色调成 Apple Music 的浅底。 */
private fun Color.blend(other: Color, ratio: Float = 0.42f): Color = Color(
    red = red + (other.red - red) * ratio,
    green = green + (other.green - green) * ratio,
    blue = blue + (other.blue - blue) * ratio,
    alpha = 1f,
)

/** 压暗作文字色（RNPN 的 shade-2）。 */
private fun Color.darken(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = 1f,
)
