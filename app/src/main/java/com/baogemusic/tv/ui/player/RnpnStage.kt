package com.baogemusic.tv.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
 * 控制行五个按钮，左右各两个围绕中间的播放/暂停：
 * 「O（切回标准）· 上一首 · 播放暂停 · 下一首 · 喜欢」。
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
@OptIn(ExperimentalComposeUiApi::class)
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
    /** 控制行最左侧的「O」按钮：切回标准界面。 */
    onSwitchStandard: () -> Unit = {},
    /**
     * 逐字扫光的时间来源（毫秒）。由页面传入播放器的**媒体时钟**（ExoPlayer.currentPosition），
     * 每帧读取一次即可拿到连续、无跳变的位置；为 null 时退回按 positionMs + 帧间隔外推。
     */
    positionProvider: (() -> Long)? = null,
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
                onSwitchStandard = onSwitchStandard,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(px(leftW)),
            )

            Spacer(Modifier.width(px(columnGap)))

            RnpnLyrics(
                lyrics = lyrics,
                positionMs = positionMs,
                isPlaying = isPlaying,
                fontSizeSp = lyricFontSizeSp,
                showTranslation = showTranslation,
                fontFamily = lyricFontFamily,
                ink = ink,
                sweepColor = palette.vivid,
                positionProvider = positionProvider,
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
    /** 控制行最左边的「O」：切回标准界面。 */
    onSwitchStandard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prevFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val favFocus = remember { FocusRequester() }
    val progressFocus = remember { FocusRequester() }
    // 「O」：控制行最左端（上一首左边），进度条按上键回到这里。
    val homeFocus = remember { FocusRequester() }
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

        // 控制行 5 个按钮：小 4 + 大 1 + 4 间距 ≈ 0.89 栏宽，永不横向溢出；
        // 左右各两个对称分布在播放键两侧，播放键因此正好落在栏（≈整块舞台）的水平中心。
        val btnSmall = wPx * 0.145f
        val btnBig = wPx * 0.19f
        val btnGap = wPx * 0.030f
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
                upFocusRequester = homeFocus,
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

            // 控制行：O · 上一首 · 播放暂停 · 下一首 · 喜欢。
            // 五个按钮对称排布，播放/暂停落在整行（= 左栏）正中；「O」取代了之前浮在左上角
            // 的独立按钮，观感统一，也不必再和进度条抢焦点路径。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(px(ctrlRowH)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RnpnIconButton(
                    icon = AppleIcons.O,
                    tint = ink.copy(alpha = 0.62f),
                    glow = ink,
                    onClick = onSwitchStandard,
                    size = px(btnSmall * 0.92f),
                    iconScale = 0.86f,
                    innerModifier = Modifier
                        .focusRequester(homeFocus)
                        .focusProperties {
                            up = progressFocus
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = prevFocus
                        },
                )
                Spacer(Modifier.width(px(btnGap)))
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
                            left = homeFocus
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
    /** 按上键去的位置；null = 拦住（不再穿到别的控件）。 */
    upFocusRequester: FocusRequester? = null,
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
                up = upFocusRequester ?: FocusRequester.Cancel
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
    /** 是否正在播放：逐字进度只在播放时按墙钟外推（暂停时冻结）。 */
    isPlaying: Boolean,
    fontSizeSp: Int,
    showTranslation: Boolean,
    fontFamily: FontFamily?,
    ink: Color,
    sweepColor: Color,
    /** 逐字扫光的时间来源；见 [RnpnStage.positionProvider]。 */
    positionProvider: (() -> Long)? = null,
    modifier: Modifier = Modifier,
) {
    if (lyrics.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = ink.copy(alpha = 0.45f), fontSize = 18.sp)
        }
        return
    }

    val active = LyricParser.activeIndex(positionMs / 1000.0, lyrics)

    // ---- 逐字扫光进度（流畅度优化）----
    // 旧实现：LaunchedEffect(active, positionSec) 里跑 700ms tween。positionMs 每秒才更新一次，
    // 于是每秒「动 0.7s → 静止 0.3s」，肉眼就是卡顿；v1.2 改成帧时钟外推，但位置源仍是秒级
    // 快照 + 自累计，一旦累计与真实音频有偏差，每秒校正一次就是一次肉眼可见的「抽一下」。
    // 现在每帧直接向播放器要**媒体时钟**（ExoPlayer.currentPosition 内部按 elapsedRealtime
    // 外推，误差在毫秒级），扫光位置与音频完全同步，没有校正跳变；仅在暂停时冻结。
    val sweepProgress = remember { mutableFloatStateOf(1f) }
    val latestPosMs by rememberUpdatedState(positionMs)
    val latestPlaying by rememberUpdatedState(isPlaying)
    val latestProvider by rememberUpdatedState(positionProvider)
    val curLine = lyrics.getOrNull(active.coerceAtLeast(0))
    val nextLine = lyrics.getOrNull(active + 1)
    val lineStartSec = curLine?.time ?: -1.0
    val lineEndSec = nextLine?.time ?: -1.0
    LaunchedEffect(active, lineStartSec, lineEndSec) {
        // 纯文本歌词 / 算不出时长 → 整行直接点亮，不做扫描。
        if (lineStartSec < 0 || lineEndSec <= lineStartSec) {
            sweepProgress.floatValue = 1f
            return@LaunchedEffect
        }
        val span = (lineEndSec - lineStartSec).toFloat()
        var lastFrame = 0L
        var estSec = latestPosMs / 1000f
        while (true) {
            val now = withFrameNanos { it }
            val ms = if (latestPlaying) {
                val real = runCatching { latestProvider?.invoke()?.takeIf { it > 0L } }.getOrNull()
                if (real != null) real else {
                    // 没有媒体时钟时的兜底：按帧间隔外推，只在偏差大时校正。
                    if (lastFrame != 0L) estSec += (now - lastFrame) / 1_000_000_000f
                    if (abs(latestPosMs / 1000f - estSec) > 0.35f) estSec = latestPosMs / 1000f
                    (estSec * 1000f).toLong()
                }
            } else {
                latestPosMs
            }
            lastFrame = now
            sweepProgress.floatValue =
                ((ms / 1000f - lineStartSec.toFloat()) / span).coerceIn(0f, 1f)
        }
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
        // 当前行不再放大：与其它行同字号，靠颜色/光晕区分层级。
        val curSp = baseSp
        val curPx = basePx

        // 每行高度（px）：原文行 +（有译文时）译文行 + 译间距。当前行字号与其它行一致，
        // 所以行高与 active 无关 → 行槽位可以静态累加，只让整列做一次平滑滚动。
        fun lineHeightPx(i: Int): Float {
            val px = if (i == active) curPx else basePx
            val hasTrans = showTranslation && !lyrics[i].translation.isNullOrBlank()
            return px * 1.2f * (if (hasTrans) 2f else 1f) + (if (hasTrans) px * 0.3f else 0f)
        }
        val heights = FloatArray(n) { lineHeightPx(it) }
        val space = basePx * 1.2f              // 行距（px）
        // 静态槽位：pos[i] = 第 i 行行顶在「内容坐标」里的位置（与 active 无关）。
        val pos = FloatArray(n)
        for (i in 1 until n) pos[i] = pos[i - 1] + heights[i - 1] + space
        val d = active.coerceIn(0, n - 1)

        // ---- 换行时整列平滑滚动 ----
        // 旧实现按 active 现算每行 top[] 并直接写进 translationY：换行瞬间整列「啪」地跳一格，
        // 与逐字扫光叠在一起就是「抽动感」。改成：槽位固定，只让 scrollY 这一个值做 420ms 缓动，
        // 换行时整列滑过去、当前行精确停在面板正中（目标值可静态算准，不会像列表滚动那样偏行）。
        val scrollTarget = pos[d] - (boxH - heights[d]) / 2f
        // 用 Animatable + 绘制阶段读值：滚动过程中不触发任何重组，只是重绘位移，
        // 否则每帧重组 13 行 Text 反而更卡。
        val scrollY = remember(lyrics) { Animatable(scrollTarget) }
        LaunchedEffect(scrollTarget) {
            scrollY.animateTo(scrollTarget, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
        // 当前行「长出来」的一点点放大：换行时新行从 0.94 回到 1，避免字号硬切。
        val curGrow = remember { Animatable(1f) }
        LaunchedEffect(d) {
            curGrow.snapTo(0.94f)
            curGrow.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
        }

        for (i in 0 until n) {
            // 可见性按「滚动到位后」的位置判断（不用动画中间值，避免读状态引起逐帧重组）。
            val settledY = pos[i] - scrollTarget
            val visible = settledY + heights[i] > -boxH * 0.9f && settledY < boxH * 1.9f
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
                // 当前行的「长出来」只在绘制阶段读值（lambda），不会引起整列重组。
                growProvider = if (isCurrent) ({ curGrow.value }) else null,
                sweepProgress = if (isCurrent) ({ sweepProgress.floatValue }) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        translationY = pos[i] - scrollY.value     // px，与容器高同单位
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
    /** 当前行的「长出来」缩放（绘制阶段读值）；非当前行为 null。 */
    growProvider: (() -> Float)? = null,
    /** 当前行的逐字扫光进度；非当前行为 null。 */
    sweepProgress: (() -> Float)?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val transAlpha = if (isCurrent) 0.9f else 0.45f
    // 当前行：纯白打底（未唱部分），已唱部分由 lyricSweep 填成封面最鲜艳色。
    // 非当前行用更低的暖白透明度拉开层次。
    val originalColor = if (isCurrent) {
        Color.White
    } else {
        ink.copy(alpha = 0.45f)
    }
    // 行模糊只有紧邻的一行做：RenderEffect 是「每行一层」的离线渲染，电视 GPU 上很贵，
    // 远处行改用透明度 / 缩放表现景深即可（旧版每行都糊，是逐字动画掉帧的主因之一）。
    val textMod = if (fx.blurPx > 0.01f && fx.blurPx < 2f) Modifier.blur(radius = (fx.blurPx / 2f).dp) else Modifier
    val growMod = if (growProvider != null) {
        Modifier.graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0.5f)
            val s = growProvider()
            scaleX = s
            scaleY = s
        }
    } else Modifier
    val transGapDp = with(density) { (fontPx * 0.3f).toDp() }
    // 当前行文字**实际占用宽度**：扫光只在这段宽度里推进，否则短句会提前扫完、长句拖后，
    // 每句速度都不一样，正是「抽动感」的来源之一。排版结果变了才更新。
    var textWidthPx by remember(line.text) { mutableFloatStateOf(0f) }

    Column(modifier.then(growMod).then(textMod)) {
        if (isCurrent) {
            // 当前行 = 两层：底下是**静态**的白色光晕层（文字透明 + shadow 只画辉光），
            // 上面是参与逐字扫光的纯色文字层。
            // 旧版把 shadow 直接挂在扫光文字上：逐字扫光每帧重画文字，等于每帧重新做一次
            // 18px 高斯模糊 → 电视上直接掉帧；拆开后辉光层的显示列表可以长期复用。
            Box(Modifier.fillMaxWidth()) {
                Text(
                    line.text,
                    color = Color.Transparent,
                    fontSize = fontSp.sp,
                    lineHeight = lineSp.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    fontFamily = fontFamily,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.White.copy(alpha = 0.55f),
                            blurRadius = 18f,
                        ),
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    // 独立图层：内容不变时整层缓存，不会跟着扫光每帧重画。
                    modifier = Modifier.fillMaxWidth().graphicsLayer(),
                )
                Text(
                    line.text,
                    color = originalColor,
                    fontSize = fontSp.sp,
                    lineHeight = lineSp.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    fontFamily = fontFamily,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    // 排版结果里取最宽那一行的右边界 = 文字真实宽度。
                    onTextLayout = { r ->
                        var w = 0f
                        for (li in 0 until r.lineCount) w = max(w, r.getLineRight(li))
                        textWidthPx = w
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (sweepProgress != null) {
                                Modifier.lyricSweep(sweepProgress, sweepColor) { textWidthPx }
                            } else Modifier,
                        ),
                )
            }
        } else {
            Text(
                line.text,
                color = originalColor,
                fontSize = fontSp.sp,
                lineHeight = lineSp.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Start,
                fontFamily = fontFamily,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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

                // 取封面里饱和度最高的色相，但**不再拉满**：饱和度压到 0.55、亮度抬到 0.90，
                // 再向白混 0.22 —— 得到柔和不刺眼的粉彩扫光色（用户反馈此前过于鲜艳刺眼）。
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
                    val soft = android.graphics.Color.HSVToColor(
                        floatArrayOf(bestHue, 0.55f, 0.90f),
                    )
                    // 向白混 22%：进一步降锐度，成为柔和的粉彩高光。
                    val cc = Color(soft)
                    val towardWhite = 0.22f
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
