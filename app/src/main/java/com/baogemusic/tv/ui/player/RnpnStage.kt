package com.baogemusic.tv.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.imageLoader
import com.baogemusic.tv.data.model.Track
import com.baogemusic.tv.playback.LyricLine
import com.baogemusic.tv.playback.LyricParser
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Apple Music 风格播放页。
 *
 * 布局与歌词排布参考 betterncm-mac 的 RNPN 歌词窗（左封面栏 + 右歌词栏），背景参考
 * TIMER-err/qplayer 的流体背景（封面取色的漂移色团）。按钮图标用 music.apple.com
 * 网页播放器形制（见 [AppleIcons]），聚焦时按钮外圈出光晕。
 * 控制按钮只保留 上一首 · 播放暂停 · 下一首 · 喜欢 四个。
 *
 * 【尺寸约定】本文件所有布局尺寸都由「实测像素 + LocalDensity」推出，不写死 dp：
 *  - 左栏用 BoxWithConstraints 拿到真实宽/高，封面、按钮、间距全部按栏宽/栏高比例计算，
 *    任何分辨率 / 字体缩放下控制行都不会横向或纵向溢出；
 *  - 歌词区把 sp 统一换算成渲染像素（px）后再做行高与定位运算——旧版把 sp 数值当 px 用，
 *    与像素的容器高度混算，导致当前行偏下、不在正中。
 */

/** RNPN 歌词行的距离效果。k = 该行到当前行的行距（0 = 当前行）。 */
internal data class RnpnLineFx(val scale: Float, val blurPx: Float, val opacity: Float)

/** upstream `ZOOM_RANGE`。非当前行的缩放衰减幅度。 */
private const val RNPN_ZOOM_RANGE = 0.08f

/** 对应上游 `effects(k)`：zoom / blur / fade。 */
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

/** 整页 Apple Music 舞台：左栏信息 + 右栏歌词，铺在流体背景上。 */
@Composable
internal fun RnpnStage(
    track: Track,
    lyrics: List<LyricLine>,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isFaved: Boolean,
    lyricFontSizeSp: Int,
    showTranslation: Boolean,
    lyricFontFamily: FontFamily?,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleFav: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberCoverPalette(track.picURL)
    // 背景已改深色，文字用浅色（暖白），不再用深色 ink。
    val ink = Color(0xFFF5F1EA)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        fun px(v: Float): Dp = with(density) { v.toDp() }

        // 边距 / 左栏宽 / 栏间距：全部按屏幕宽比例，电视 720p / 1080p / 4K 都等比。
        // 左栏占 1/3（用户反馈之前太挤）。
        val padH = wPx * 0.030f
        val padV = hPx * 0.045f
        val contentW = wPx - padH * 2
        val leftW = contentW / 3f
        val columnGap = contentW * 0.04f

        FluidBackground(picURL = track.picURL, palette = palette.colors)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = px(padH), vertical = px(padV)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RnpnLeftColumn(
                track = track,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                isFaved = isFaved,
                ink = ink,
                onTogglePlay = onTogglePlay,
                onPrev = onPrev,
                onNext = onNext,
                onToggleFav = onToggleFav,
                onSeekTo = onSeekTo,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(px(leftW)),
            )

            Spacer(Modifier.width(px(columnGap)))

            RnpnLyrics(
                lyrics = lyrics,
                positionMs = positionMs,
                fontSizeSp = lyricFontSizeSp,
                showTranslation = showTranslation,
                fontFamily = lyricFontFamily,
                ink = ink,
                sweepColor = palette.vivid,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
            )
        }
    }
}

// ============== 流体背景 ========================================================================

/**
 * 流体背景：封面主色的几个大色团缓慢漂移（Lissajous 轨迹，26s 一圈无跳变），
 * 底下压一张重度模糊封面保留纹理，最后覆一层薄白纱保证浅色主题下文字可读。
 * 纯 Canvas，不依赖 RenderEffect / 着色器，Android 8 可跑。
 */
@Composable
private fun FluidBackground(picURL: String?, palette: List<Color>) {
    // 更深、更饱和的底色：不往纯白 blend，保留封面主色的浓郁度。
    val baseTop = palette[0].darken(0.35f).blend(Color(0xFF141118), 0.30f)
    val baseBottom = palette[1].darken(0.45f).blend(Color(0xFF0B0B10), 0.36f)
    val t by rememberInfiniteTransition(label = "fluid").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 22_000, easing = LinearEasing)),
        label = "fluidT",
    )

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(baseTop, baseBottom)))) {
        if (!picURL.isNullOrBlank()) {
            val context = LocalContext.current
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(picURL)
                    .transformations(
                        com.baogemusic.tv.ui.components.BlurTransformation(context, radius = 44f, sampling = 3f),
                    )
                    .crossfade(600)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.32f },
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            if (size.minDimension <= 0f) return@Canvas
            val twoPi = 2f * PI.toFloat()
            for (i in 0 until 4) {
                // 更浓的色团：用原始主色（不向浅色 blend），alpha 更高，色块更明显。
                val c = palette[i % palette.size]
                val ph = i * 1.91f
                // 漂移幅度加大、频率略快，变化更强烈。
                val cx = size.width * (0.5f + 0.42f * sin((t * 0.75f + ph * 0.11f) * twoPi))
                val cy = size.height * (0.5f + 0.40f * cos((t * 0.58f + ph * 0.17f) * twoPi))
                val r = size.minDimension * (0.55f + 0.22f * sin((t * 0.42f + ph) * twoPi))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            c.copy(alpha = 0.72f),
                            c.copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
        }

        // 压暗 + 白纱换成更轻的一层，让色团透出来（变化更强烈），同时保证文字可读。
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xFF0B0B12).copy(alpha = 0.28f),
                    0.45f to Color.Transparent,
                    1f to Color(0xFF0B0B12).copy(alpha = 0.46f),
                ),
            ),
        )
    }
}

// ============== 左栏 ============================================================================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RnpnLeftColumn(
    track: Track,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isFaved: Boolean,
    ink: Color,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleFav: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressFocus = remember { FocusRequester() }
    val prevFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val favFocus = remember { FocusRequester() }
    // 进页面焦点落在播放键上；首帧节点未挂载会抛异常，按 50ms 重试（同 PlayerScreen 的 tryRequestFocus）。
    LaunchedEffect(Unit) {
        repeat(6) {
            if (runCatching { playFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            kotlinx.coroutines.delay(50)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        fun px(v: Float): Dp = with(density) { v.toDp() }

        // 控制行：按钮直径按栏宽比例。3 小 1 大 + 3 间距 ≈ 0.86 栏宽，永不横向溢出。
        val btnSmall = wPx * 0.17f
        val btnBig = wPx * 0.215f
        val btnGap = wPx * 0.045f
        val ctrlRowH = btnBig * 1.5f              // 含光晕余量
        // 进度区（进度条 + 时间行）
        val progressBarH = wPx * 0.085f
        val timeRowH = with(density) { 12.sp.toPx() } * 1.5f
        // 文本区（sp -> px，与真实渲染一致）
        val titlePx = with(density) { 22.sp.toPx() }
        val singerPx = with(density) { 14.sp.toPx() }
        val albumPx = with(density) { 13.sp.toPx() }
        val hasAlbum = !track.albumName.isNullOrBlank()
        val textBlockH = titlePx * 1.35f + singerPx * 1.35f +
            (if (hasAlbum) albumPx * 1.35f else 0f) + wPx * 0.05f
        // 垂直间距按栏高比例
        val vGap = hPx * 0.022f
        // 封面 = min(栏宽, 剩余高度)，任何分辨率/字体下控制行都完整可见。
        val cover = min(
            wPx,
            (hPx - textBlockH - progressBarH - timeRowH - ctrlRowH - vGap * 5)
                .coerceAtLeast(wPx * 0.35f),
        )

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            // 封面：圆角 + 轻底，居中，尺寸自适应
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                coil.compose.AsyncImage(
                    model = track.picURL,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(px(cover))
                        .clip(RoundedCornerShape(px(cover * 0.07f)))
                        .background(Color.White.copy(alpha = 0.14f)),
                )
            }

            Spacer(Modifier.height(px(vGap)))

            // 歌名 22sp（长歌名单行省略，不再溢出）
            Text(
                track.name,
                color = ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(px(titlePx * 0.22f)))
            Text(
                track.singer,
                color = ink.copy(alpha = 0.48f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (hasAlbum) {
                Spacer(Modifier.height(px(albumPx * 0.18f)))
                Text(
                    track.albumName!!,
                    color = ink.copy(alpha = 0.36f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(px(vGap)))

            RnpnProgress(
                positionMs = positionMs,
                durationMs = durationMs,
                ink = ink,
                onSeekTo = onSeekTo,
                focusRequester = progressFocus,
                downFocusRequester = playFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(px(progressBarH)),
            )
            Spacer(Modifier.height(px(vGap * 0.4f)))
            Row(Modifier.fillMaxWidth()) {
                Text(fmt(positionMs), color = ink.copy(alpha = 0.45f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "-" + fmt((durationMs - positionMs).coerceAtLeast(0)),
                    color = ink.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(px(vGap)))

            // 控制行：上一首 · 播放暂停 · 下一首 · 喜欢（与 music.apple.com 网页播放器同序）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(px(ctrlRowH)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RnpnIconButton(
                    icon = AppleIcons.Previous,
                    tint = ink.copy(alpha = 0.88f),
                    glow = ink,
                    onClick = onPrev,
                    size = px(btnSmall),
                    innerModifier = Modifier
                        .focusRequester(prevFocus)
                        .focusProperties {
                            up = progressFocus
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = playFocus
                        },
                )
                Spacer(Modifier.width(px(btnGap)))
                RnpnIconButton(
                    icon = if (isPlaying) AppleIcons.Pause else AppleIcons.Play,
                    tint = ink,
                    glow = ink,
                    onClick = onTogglePlay,
                    size = px(btnBig),
                    iconScale = 0.52f,
                    innerModifier = Modifier
                        .focusRequester(playFocus)
                        .focusProperties {
                            up = progressFocus
                            down = FocusRequester.Cancel
                            left = prevFocus
                            right = nextFocus
                        },
                )
                Spacer(Modifier.width(px(btnGap)))
                RnpnIconButton(
                    icon = AppleIcons.Next,
                    tint = ink.copy(alpha = 0.88f),
                    glow = ink,
                    onClick = onNext,
                    size = px(btnSmall),
                    innerModifier = Modifier
                        .focusRequester(nextFocus)
                        .focusProperties {
                            up = progressFocus
                            down = FocusRequester.Cancel
                            left = playFocus
                            right = favFocus
                        },
                )
                Spacer(Modifier.width(px(btnGap)))
                RnpnIconButton(
                    icon = if (isFaved) AppleIcons.HeartFilled else AppleIcons.Heart,
                    tint = if (isFaved) Color(0xFFE5484D) else ink.copy(alpha = 0.80f),
                    glow = if (isFaved) Color(0xFFE5484D) else ink,
                    onClick = onToggleFav,
                    size = px(btnSmall),
                    innerModifier = Modifier
                        .focusRequester(favFocus)
                        .focusProperties {
                            up = progressFocus
                            down = FocusRequester.Cancel
                            left = nextFocus
                            right = FocusRequester.Cancel
                        },
                )
            }
        }
    }
}

/** 圆形图标按钮：聚焦时按钮外圈出径向光晕并轻微放大（外层光晕，不画描边圆环）。 */
@Composable
private fun RnpnIconButton(
    icon: ImageVector,
    tint: Color,
    glow: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconScale: Float = 0.62f,
    innerModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // 外层光晕画在按钮外面，不会被按钮尺寸裁掉
        if (focused) {
            Box(
                Modifier
                    .size(size * 1.85f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                glow.copy(alpha = 0.38f),
                                glow.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                        ),
                        CircleShape,
                    ),
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val s = if (focused) 1.10f else 1f
                    scaleX = s
                    scaleY = s
                }
                .then(innerModifier)
                .onFocusChanged { focused = it.isFocused }
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .focusable(interactionSource = interaction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * iconScale),
            )
        }
    }
}

/**
 * 进度条：总高由外部按栏宽比例传入，条厚取总高的一小部分（不写死 dp）。
 * 点击按点击位置跳转；可聚焦，遥控器左右键 ±5 秒（长按连发），按下键跳到播放键。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RnpnProgress(
    positionMs: Long,
    durationMs: Long,
    ink: Color,
    onSeekTo: (Long) -> Unit,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val frac = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = frac,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "rnpnProgress",
    )
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (focused) 1f else 0.92f }
            .focusRequester(focusRequester)
            .focusProperties {
                up = FocusRequester.Cancel
                down = downFocusRequester
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (e.key) {
                    Key.DirectionLeft -> -5_000L
                    Key.DirectionRight -> 5_000L
                    else -> return@onPreviewKeyEvent false
                }
                onSeekTo((positionMs + step).coerceIn(0L, durationMs.coerceAtLeast(0L)))
                true
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0L && size.width > 0) {
                        onSeekTo((durationMs * (offset.x / size.width).coerceIn(0f, 1f)).toLong())
                    }
                }
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        // 条厚 = 区块高的 16%，聚焦时略粗，比例随栏宽缩放
        val trackH = if (focused) 0.22f else 0.16f
        Box(Modifier.fillMaxWidth().fillMaxHeight(trackH).background(ink.copy(alpha = 0.20f)))
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight(trackH).background(ink.copy(alpha = 0.90f)))
    }
}

// ============== 右栏：逐字歌词 ==================================================================

/**
 * 歌词列：逐行绝对定位，按 [rnpnLineEffects] 缩放 / 模糊 / 淡出，当前行再加逐字扫光。
 *
 * 【单位统一】字号用 sp 渲染，但行高与定位全部换算成 px：
 * fontSp（sp 数值）→ fontPx = fontSp.sp.toPx()。容器高 boxH 也是 px，
 * 因此 top[当前行] = boxH/2 - 行高/2 把当前行**中心**精确放在面板正中。
 */
@Composable
private fun RnpnLyrics(
    lyrics: List<LyricLine>,
    positionMs: Long,
    fontSizeSp: Int,
    showTranslation: Boolean,
    fontFamily: FontFamily?,
    ink: Color,
    sweepColor: Color,
    modifier: Modifier = Modifier,
) {
    if (lyrics.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = ink.copy(alpha = 0.45f), fontSize = 18.sp)
        }
        return
    }

    val active = LyricParser.activeIndex(positionMs / 1000.0, lyrics)

    // 逐字扫光进度：positionMs 每秒 tick 一次，用 Animatable 线性补间，视觉上连续扫过。
    val sweep = remember { Animatable(1f) }
    var lastActive by remember { mutableIntStateOf(-1) }
    val positionSec = positionMs / 1000.0
    LaunchedEffect(active, positionSec) {
        if (active != lastActive) {
            lastActive = active
            sweep.snapTo(0f)
        }
        val line = lyrics.getOrNull(active)
        val next = lyrics.getOrNull(active + 1)
        val target = if (line == null) {
            0f
        } else {
            val start = line.time
            val end = next?.time
            if (start < 0 || end == null || end <= start) 1f
            else ((positionSec - start) / (end - start)).coerceIn(0.0, 1.0).toFloat()
        }
        sweep.animateTo(target, tween(durationMillis = 700, easing = LinearEasing))
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().rnpnMaskEdges()) {
        val density = LocalDensity.current
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val n = lyrics.size

        // upstream computeFontPx：base * k，k 取窗口/参考分辨率比例（上限 1）。
        val kScale = min(1f, min(boxW / 1080f, boxH / 608f))
        // fontSp 用于 Text 渲染；fontPx 是同一字号的真实渲染像素，用于行高/定位。
        val baseSp = max(12f, fontSizeSp * kScale)
        val basePx = with(density) { baseSp.sp.toPx() }
        // 当前行放大 1.2 倍（用户需求），非当前行保持原号。
        val curSp = baseSp * 1.2f
        val curPx = basePx * 1.2f
        val alignFactor = 0.5f                // 当前行中心锚在容器 50% 高度

        // 每行高度（px）：原文行 +（有译文时）译文行 + 译间距；当前行用放大的字号。
        fun lineHeightPx(i: Int): Float {
            val px = if (i == active) curPx else basePx
            val hasTrans = showTranslation && !lyrics[i].translation.isNullOrBlank()
            return px * 1.2f * (if (hasTrans) 2f else 1f) + (if (hasTrans) px * 0.3f else 0f)
        }
        val heights = FloatArray(n) { lineHeightPx(it) }
        val space = basePx * 1.2f              // 行距（px）
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
            val visible = top[i] + heights[i] > -boxH * 0.6f && top[i] < boxH * 1.6f
            if (!visible) continue
            val fx = rnpnLineEffects(abs(i - d))
            val isCurrent = i == d
            RnpnLyricLine(
                line = lyrics[i],
                isCurrent = isCurrent,
                fontSp = if (isCurrent) curSp else baseSp,
                lineSp = (if (isCurrent) curSp else baseSp) * 1.2f,
                fontPx = if (isCurrent) curPx else basePx,
                showTranslation = showTranslation,
                fontFamily = fontFamily,
                ink = ink,
                sweepColor = sweepColor,
                fx = fx,
                sweepProgress = if (isCurrent) ({ sweep.value }) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        translationY = top[i]     // px，与容器高同单位
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
    fontSp: Float,
    lineSp: Float,
    fontPx: Float,
    showTranslation: Boolean,
    fontFamily: FontFamily?,
    ink: Color,
    sweepColor: Color,
    fx: RnpnLineFx,
    /** 当前行的逐字扫光进度；非当前行为 null。 */
    sweepProgress: (() -> Float)?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val transAlpha = if (isCurrent) 0.9f else 0.45f
    // 当前行：纯白打底（未唱部分）+ 白色光晕阴影，保证在流体背景上始终清晰可读；
    // 已唱部分由 lyricSweep 填成封面最鲜艳色。非当前行用更低的暖白透明度拉开层次。
    val originalColor = if (isCurrent) {
        Color.White
    } else {
        ink.copy(alpha = 0.45f)
    }
    // 当前行加白色光晕阴影，进一步从背景里「提」出来。
    val textStyle = if (isCurrent) {
        androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color.White.copy(alpha = 0.55f),
                blurRadius = 18f,
            ),
        )
    } else {
        androidx.compose.ui.text.TextStyle()
    }
    // 行模糊：Modifier.blur 在 API 31+ 才有 RenderEffect，Android 8/9 自动降级为不模糊。
    val textMod = if (fx.blurPx > 0.01f) Modifier.blur(radius = (fx.blurPx / 2f).dp) else Modifier
    val transGapDp = with(density) { (fontPx * 0.3f).toDp() }

    Column(modifier.then(textMod)) {
        Text(
            line.text,
            color = originalColor,
            fontSize = fontSp.sp,
            lineHeight = lineSp.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Start,
            fontFamily = fontFamily,
            style = textStyle,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (sweepProgress != null) {
                        Modifier.lyricSweep(sweepProgress, sweepColor)
                    } else Modifier,
                ),
        )
        if (showTranslation) {
            line.translation?.takeIf { it.isNotBlank() }?.let { t ->
                Spacer(Modifier.height(transGapDp))
                Text(
                    t,
                    color = ink.copy(alpha = transAlpha),
                    fontSize = fontSp.sp,
                    lineHeight = lineSp.sp,
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

/** 顶部 / 底部 3% 的 alpha 渐隐。 */
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

/** 封面取色结果：主色列表（流体背景用）+ 最鲜艳色（逐字扫光用）。 */
private class CoverPalette(val colors: List<Color>, val vivid: Color)

/** 从封面取色：整体平均色 + 左上区域 + 右下区域（流体背景），以及全图饱和度最高的「最鲜艳色」（扫光）。 */
@Composable
private fun rememberCoverPalette(picURL: String?): CoverPalette {
    val fallback = listOf(Color(0xFFB79A88), Color(0xFF9A8FA8), Color(0xFF8FA8A0))
    val fallbackVivid = Color(0xFF00ACC1)
    var palette by remember(picURL) { mutableStateOf(CoverPalette(fallback, fallbackVivid)) }
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
                    ?: return@runCatching

                fun region(x0: Int, x1: Int, y0: Int, y1: Int): Color {
                    var r = 0L; var g = 0L; var b = 0L; var n = 0L
                    for (y in y0 until y1) {
                        for (x in x0 until x1) {
                            val c = bmp.getPixel(x, y)
                            r += android.graphics.Color.red(c)
                            g += android.graphics.Color.green(c)
                            b += android.graphics.Color.blue(c)
                            n++
                        }
                    }
                    return if (n == 0L) fallback[0] else Color(
                        (r / n).toInt().coerceIn(0, 255),
                        (g / n).toInt().coerceIn(0, 255),
                        (b / n).toInt().coerceIn(0, 255),
                    )
                }

                // 全图最鲜艳（饱和度最高）的颜色：HSV 里 S 最大，且亮度适中（不太黑也不太白）。
                // 取到后把饱和度拉满、亮度抬到近顶，作为高光扫光色（否则会显得暗淡）。
                fun vividColor(): Color {
                    var bestSat = -1f
                    var bestHue = 0f
                    val hsv = FloatArray(3)
                    for (y in 0 until bmp.height step 2) {
                        for (x in 0 until bmp.width step 2) {
                            val c = bmp.getPixel(x, y)
                            android.graphics.Color.colorToHSV(c, hsv)
                            val v = hsv[2]
                            // 只挑中等偏亮、且饱和度高的颜色，避免死黑/死白
                            if (v < 0.30f || v > 0.95f) continue
                            if (hsv[1] > bestSat) {
                                bestSat = hsv[1]
                                bestHue = hsv[0]
                            }
                        }
                    }
                    if (bestSat < 0f) return fallbackVivid
                    // 饱和度拉满到 1.0（原色可能只有 0.5~0.7，视觉偏灰）、亮度提到 0.98 做高光。
                    val boosted = android.graphics.Color.HSVToColor(
                        floatArrayOf(bestHue, 1.0f, 0.98f),
                    )
                    // 若原色本身就偏亮（如黄/青），再向白微混一点，形成"高光"而非纯饱和色。
                    val sat = bestSat.coerceIn(0f, 1f)
                    val towardWhite = (1f - sat) * 0.25f
                    val cc = Color(boosted)
                    return Color(
                        red = cc.red + (1f - cc.red) * towardWhite,
                        green = cc.green + (1f - cc.green) * towardWhite,
                        blue = cc.blue + (1f - cc.blue) * towardWhite,
                    )
                }

                val w = bmp.width
                val h = bmp.height
                if (w > 1 && h > 1) {
                    palette = CoverPalette(
                        colors = listOf(
                            region(0, w, 0, h),
                            region(0, w / 2, 0, h / 2),
                            region(w / 2, w, h / 2, h),
                        ),
                        vivid = vividColor(),
                    )
                }
            }
        }
    }
    return palette
}

/** mm:ss。 */
private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%02d:%02d".format(s / 60, s % 60)
}

/** 把主色向 [other] 插值 —— 把深色封面主色调成 Apple Music 的浅底。 */
private fun Color.blend(other: Color, ratio: Float = 0.42f): Color = Color(
    red = red + (other.red - red) * ratio,
    green = green + (other.green - green) * ratio,
    blue = blue + (other.blue - blue) * ratio,
    alpha = 1f,
)

/** 压暗作文字色。 */
private fun Color.darken(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = 1f,
)
