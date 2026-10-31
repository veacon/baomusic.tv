package com.baogemusic.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.baogemusic.tv.data.model.Track
import com.baogemusic.tv.data.store.FullscreenLyricFx
import com.baogemusic.tv.data.store.LyricAlignment
import com.baogemusic.tv.data.store.PlayerFx
import com.baogemusic.tv.data.store.PlayerLayout
import com.baogemusic.tv.playback.LyricParser
import com.baogemusic.tv.playback.RepeatMode
import com.baogemusic.tv.ui.appContainer
import com.baogemusic.tv.ui.components.Artwork
import com.baogemusic.tv.ui.components.TvFocusable
import com.baogemusic.tv.ui.components.TvPill
import com.baogemusic.tv.ui.theme.AppColors
import com.baogemusic.tv.ui.theme.rememberAppFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
  val controller = appContainer.playbackController
  val state by controller.state.collectAsState()
  val lyrics by controller.lyrics.collectAsState()
  val love by appContainer.libraryStore.love.collectAsState()
  val settings by appContainer.settingsStore.settings.collectAsState()
  val audioLevel by controller.audioLevel.collectAsState()
  val scope = rememberCoroutineScope()
  var showQueue by remember { mutableStateOf(false) }
  val playFocus = remember { FocusRequester() }
  val progressFocus = remember { FocusRequester() }

  val track = state.currentTrack

  var showEqDialog by remember { mutableStateOf(false) }
  var showPicker by remember { mutableStateOf(false) }

  // 核心自适应算法：获取屏幕物理总高度（DP），彻底摆脱内部组件相互挤压的局限
  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val screenHeight = configuration.screenHeightDp.dp
  // 唱片 / 封面尺寸按屏高的比例给，不同布局各取一个系数（大屏自动等比放大）

  val wantsFocus = !showQueue && !showEqDialog && !showPicker
  LaunchedEffect(wantsFocus) {
    if (wantsFocus) tryRequestFocus(playFocus)
  }

  // 播放控件自动隐藏：空闲 5 秒隐藏进度条 + 控制栏。隐藏后 Compose 内没有焦点，按键只会落到
  // Activity，所以在 Activity.dispatchKeyEvent 层拦截：方向键 / 确认键只负责唤出控件（吞掉该次
  // 按键，避免确认键误触暂停）；返回、菜单、媒体键照常放行。
  var controlsVisible by remember { mutableStateOf(true) }
  var idleTick by remember { mutableStateOf(0) }
  val autoHideActive = settings.autoHidePlayerControls && wantsFocus
  LaunchedEffect(autoHideActive, controlsVisible, idleTick) {
    if (!autoHideActive) {
      controlsVisible = true
      return@LaunchedEffect
    }
    if (controlsVisible) {
      delay(AUTO_HIDE_MS)
      controlsVisible = false
    }
  }
  androidx.compose.runtime.DisposableEffect(autoHideActive) {
    val events = appContainer.events
    // 唤出控件那一次按键的后续（长按重复 + 抬起）也吞掉，否则抬起时焦点已在播放键上会触发暂停。
    var wakeKey: Int? = null
    val interceptor: (android.view.KeyEvent) -> Boolean = interceptor@{ e ->
      idleTick++
      if (e.keyCode == wakeKey) {
        if (e.action == android.view.KeyEvent.ACTION_UP) wakeKey = null
        return@interceptor true
      }
      if (controlsVisible || e.keyCode !in WAKE_KEYS) return@interceptor false
      if (e.action == android.view.KeyEvent.ACTION_DOWN) {
        wakeKey = e.keyCode
        controlsVisible = true
        scope.launch { tryRequestFocus(playFocus) }
      }
      true
    }
    if (autoHideActive) events.keyInterceptor = interceptor
    onDispose { if (events.keyInterceptor === interceptor) events.keyInterceptor = null }
  }

  BackHandler(enabled = true) {
    if (showQueue) showQueue = false else onClose()
  }

  LaunchedEffect(Unit) {
    appContainer.events.menuKey.collect { showQueue = !showQueue }
  }

  Box(modifier = modifier.fillMaxSize().background(AppColors.BgDeep)) {
    CoverBackdrop(picURL = track?.picURL)

    if (track == null) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("暂无播放", color = AppColors.TextSecondary)
      }
      return@Box
    }

    // 歌词字体：只作用于歌词文字（设置 → 歌词 → 字体），不影响其他界面文字。
    val lyricFont = rememberAppFontFamily()
    val lyricStatus by controller.lyricStatus.collectAsState()
    val activeSp = settings.lyricFontSizeSp
    val lineFactor = com.baogemusic.tv.data.store.LyricScaleRange.lineFactorOf(settings.lyricLineScale)
    // 歌词沿用用户在设置里选的对齐方式（默认左对齐）。
    val effectiveAlign = settings.lyricAlignment
    val coverOnLeft = settings.playerLayout == PlayerLayout.LYRIC_RIGHT
    // 老存档 / 异常值兜底：把缩放系数夹到合法范围，避免超大尺寸布局或 Canvas 半径异常导致渲染崩溃。
    val safeCoverScale = com.baogemusic.tv.data.store.CoverScale.clamp(settings.coverScale)

    // 歌名 + 平台标签 + 歌手 + 专辑 + 调试信息。BetterLyrics 模式下移到封面下方，故抽成可复用块。
    val trackMeta: @Composable () -> Unit = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          track.name,
          color = AppColors.TextPrimary,
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }
      Spacer(Modifier.height(8.dp))
      Text(
        track.singer,
        color = AppColors.TextSecondary,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      track.albumName?.takeIf { it.isNotBlank() }?.let { album ->
        Spacer(Modifier.height(4.dp))
        Text(
          "专辑：$album",
          color = AppColors.TextMuted,
          fontSize = 13.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      // 播放调试信息（设置里开启）：获取地址 / 失败自动跳过 / 换源降级，排查音源问题用。
      val status = state.error ?: (if (state.resolving) "正在获取播放地址…" else state.warning)
      if (settings.showDebugInfo && status != null) {
        Spacer(Modifier.height(6.dp))
        Text(
          status,
          color = if (state.error != null) AppColors.Danger else AppColors.Warning,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // 封面容器（含动感波光，波纹中心 = 封面中心，并随方向左右移动）。scale 控制整体大小。
    val coverContainer: @Composable (Modifier, Float) -> Unit = { m, scale ->
      Box(modifier = m.size(screenHeight * scale), contentAlignment = Alignment.Center) {
        if (settings.playerFx != PlayerFx.OFF) {
          PlayerBackdrop(
            fx = settings.playerFx,
            isPlaying = state.isPlaying,
            level = audioLevel,
            glow = vinylPalette(settings.vinylColor).glow,
            modifier = Modifier.fillMaxSize(),
          )
        }
        Box(modifier = Modifier.size(screenHeight * (scale * 0.72f)), contentAlignment = Alignment.Center) {
          PlayerDisc(
            picURL = track.picURL,
            isPlaying = state.isPlaying,
            palette = vinylPalette(settings.vinylColor),
            shape = settings.discShape,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }

    // 右栏歌词面板；BetterLyrics 模式下标题信息已移到封面下方，故 showMeta=false 只放歌词。
    val lyricPanel: @Composable (Modifier, Boolean) -> Unit = { m, showMeta ->
      Column(modifier = m.fillMaxHeight()) {
        if (showMeta) {
          trackMeta()
          Spacer(Modifier.height(20.dp))
        }
        // 歌词区：填满剩余高度
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          LyricPane(
            lyrics,
            state.positionMs,
            lyricFontSizeSp = activeSp,
            lyricAlignment = effectiveAlign,
            lineFactor = lineFactor,
            showTranslation = settings.showLyricTranslation,
            singleLine = false,
            fontFamily = lyricFont,
            failureHint = (lyricStatus as? com.baogemusic.tv.playback.LyricStatus.Failed)?.message,
            loading = lyricStatus is com.baogemusic.tv.playback.LyricStatus.Loading,
            onRetry = { controller.retryLyrics() },
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }

    // 整个「左封面 + 右歌词」舞台，自适应填满传入的 modifier。
    val stage: @Composable (Modifier) -> Unit = { m ->
      Box(modifier = m.fillMaxSize()) {
        Row(
          modifier = Modifier.fillMaxSize(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (coverOnLeft) {
            coverContainer(Modifier, 0.72f * safeCoverScale)
            Spacer(Modifier.width(40.dp))
            lyricPanel(Modifier.weight(1f), true)
          } else {
            lyricPanel(Modifier.weight(1f), true)
            Spacer(Modifier.width(40.dp))
            coverContainer(Modifier, 0.72f * safeCoverScale)
          }
        }
      }
    }

    // 全屏歌词特效：铺满整屏（含左上角歌名 / 歌手）；底部控件在需要时浮出覆盖其上。
    val fullscreenFx = settings.fullscreenLyricFx
    // 「胶片」用歌手写真做背景：只在选中该特效时联网取一次（按歌手名缓存），切歌自动换图。
    var artistPhoto by remember(track.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(fullscreenFx, track.id) {
      artistPhoto = if (fullscreenFx == FullscreenLyricFx.CINEMA) {
        appContainer.artistPhotos.photoFor(track)
      } else {
        null
      }
    }
    if (fullscreenFx != FullscreenLyricFx.NONE) {
      FullscreenLyricStage(
        fx = fullscreenFx,
        lyrics = lyrics,
        positionMs = state.positionMs,
        fontSp = activeSp,
        lineFactor = lineFactor,
        showTranslation = settings.showLyricTranslation,
        fontFamily = lyricFont,
        trackName = track.name,
        artist = track.singer,
        artistPhoto = artistPhoto,
        coverPic = track.picURL,
        modifier = Modifier.fillMaxSize(),
      )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
      if (fullscreenFx != FullscreenLyricFx.NONE) {
        // 全屏歌词模式：封面 / 波形让位，只保留底部控制栏（自动隐藏）。
        Spacer(Modifier.weight(1f))
      } else {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          stage(Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        com.baogemusic.tv.ui.components.Waveform(
          isPlaying = state.isPlaying,
          level = audioLevel,
          modifier = Modifier.fillMaxWidth().height(40.dp),
        )
      }
      AnimatedVisibility(visible = controlsVisible) {
        Column {
          Spacer(Modifier.height(6.dp))
          ProgressBar(
            state.positionMs,
            state.durationMs,
            onSeek = { controller.seekTo(it) },
            focusRequester = progressFocus,
            downFocusRequester = playFocus,
          )
          Spacer(Modifier.height(14.dp))
          val audioSpec by controller.audioSpec.collectAsState()
          TransportBar(
            state = state,
            love = love,
            audioSpec = audioSpec,
            onTogglePlay = { controller.togglePlay() },
            onPrev = { controller.prev() },
            onNext = { controller.next() },
            onCycleRepeat = {
              val mode = state.repeatMode
              val shuffle = state.shuffle
              when {
                mode == RepeatMode.ALL && !shuffle -> {
                  controller.setRepeatMode(RepeatMode.ONE)
                }
                mode == RepeatMode.ONE && !shuffle -> {
                  controller.setRepeatMode(RepeatMode.ALL)
                  if (!state.shuffle) controller.toggleShuffle()
                }
                else -> {
                  controller.setRepeatMode(RepeatMode.ALL)
                  if (state.shuffle) controller.toggleShuffle()
                }
              }
            },
            onToggleFav = { showPicker = true },
            onShowQueue = { showQueue = true },
            onTuneClick = { showEqDialog = true },
            fullscreenLyricOn = fullscreenFx != FullscreenLyricFx.NONE,
            onToggleFullscreenLyric = {
              scope.launch {
                appContainer.settingsStore.update { s ->
                  s.copy(
                    fullscreenLyricFx = when (s.fullscreenLyricFx) {
                      FullscreenLyricFx.NONE -> FullscreenLyricFx.LIGHT
                      FullscreenLyricFx.LIGHT -> FullscreenLyricFx.STARSEA
                      FullscreenLyricFx.STARSEA -> FullscreenLyricFx.CINEMA
                      FullscreenLyricFx.CINEMA -> FullscreenLyricFx.NONE
                    },
                  )
                }
              }
            },
            playFocusRequester = playFocus,
            progressFocusRequester = progressFocus,
          )
        }
      }
    }

    QueueDrawer(
      visible = showQueue,
      queue = state.queue,
      currentIndex = state.index,
      onSelect = { idx -> controller.playAt(idx) },
      modifier = Modifier.align(Alignment.CenterEnd),
    )

    if (showEqDialog) {
      EqualizerDialog(onDismiss = { showEqDialog = false })
    }
    if (showPicker) {
      com.baogemusic.tv.ui.components.PlaylistPickerDialog(
        track = track,
        onDismiss = { showPicker = false },
        showDownloadButton = true,
      )
    }
  }
}

// ============== Vinyl disc (去除了中心小黑洞，封面直达圆心) =====================================================================

/**
 * 唱片 / 封面展示：圆形黑胶（旋转盘面 + 纹路）或圆角方形封面（Apple / 网易云的方形封面观感）。
 * 方形模式下不画黑胶纹路，只保留封面 + 外圈光晕，并随音量做轻微呼吸缩放。
 */
@Composable
private fun PlayerDisc(
  picURL: String?,
  isPlaying: Boolean,
  palette: VinylPalette,
  shape: com.baogemusic.tv.data.store.DiscShape,
  modifier: Modifier = Modifier,
) {
  if (shape == com.baogemusic.tv.data.store.DiscShape.ROUNDED_SQUARE) {
    SquareCover(picURL, isPlaying, palette, modifier)
  } else {
    VinylDisc(picURL, isPlaying, palette, modifier)
  }
}

@Composable
private fun SquareCover(
  picURL: String?,
  isPlaying: Boolean,
  palette: VinylPalette,
  modifier: Modifier = Modifier,
) {
  val level by appContainer.playbackController.audioLevel.collectAsState()
  val pulse by animateFloatAsState(
    targetValue = if (isPlaying) 1f + (level.coerceIn(0f, 1f) * 0.035f) else 1f,
    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
    label = "squarePulse",
  )
  val radius = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
  Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    // 外圈柔光（与黑胶配色一致）
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
        .background(
          Brush.radialGradient(
            colors = listOf(
              palette.glow.copy(alpha = 0.42f * palette.glowStrength),
              palette.glow.copy(alpha = 0.14f * palette.glowStrength),
              palette.glow.copy(alpha = 0.00f),
            ),
          ),
        ),
    )
    Box(
      modifier = Modifier
        .fillMaxSize(0.82f)
        .aspectRatio(1f)
        .graphicsLayer { scaleX = pulse; scaleY = pulse }
        .clip(radius),
    ) {
      Artwork(picURL, modifier = Modifier.fillMaxSize(), shape = radius)
      // 一圈极淡的内描边，让方形封面在深色背景上有边界
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.White.copy(alpha = 0.06f))
          .then(Modifier),
      )
    }
  }
}

@Composable
private fun VinylDisc(
  picURL: String?,
  isPlaying: Boolean,
  palette: VinylPalette,
  modifier: Modifier = Modifier,
) {
  val rotation = remember { Animatable(0f) }
  LaunchedEffect(isPlaying) {
    if (isPlaying) {
      while (true) {
        rotation.animateTo(
          rotation.value + 360f,
          animationSpec = tween(durationMillis = 30_000, easing = LinearEasing),
        )
      }
    }
  }
  val circle = androidx.compose.foundation.shape.CircleShape

  Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    // 1) 外圈光晕（颜色随黑胶配色） (填满全尺寸)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(circle)
        .background(
          Brush.radialGradient(
            colors = listOf(
              palette.glow.copy(alpha = 0.55f * palette.glowStrength),
              palette.glow.copy(alpha = 0.20f * palette.glowStrength),
              palette.glow.copy(alpha = 0.00f),
            ),
          ),
        ),
    )
    // 2) 黑胶主体盘面 (比例计算：300 / 360 ≈ 83.33%)
    Box(
      modifier = Modifier
        .fillMaxSize(0.8333f)
        .aspectRatio(1f)
        .graphicsLayer { rotationZ = rotation.value },
      contentAlignment = Alignment.Center,
    ) {
      VinylGrooves(palette, modifier = Modifier.fillMaxSize())

      // 3) 内圈专辑封面 (比例计算：220 / 300 ≈ 73.33%)
      // 优化：封面图现在无缝延伸到轴心最中心，不再被黑色块覆盖
      Box(
        modifier = Modifier
          .fillMaxSize(0.7333f)
          .aspectRatio(1f)
          .clip(circle)
          .background(AppColors.BgDeep),
      ) {
        Artwork(picURL, modifier = Modifier.fillMaxSize(), shape = circle)
      }
    }
    // 💡 4) 原本位于此处的中心金属轴孔 Box 已被彻底移除，解决了黑洞不好看的问题
  }
}

@Composable
private fun VinylGrooves(palette: VinylPalette, modifier: Modifier) {
  androidx.compose.foundation.Canvas(modifier = modifier.aspectRatio(1f)) {
    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension / 2f
    val innerRadius = outerRadius * 0.7333f

    val baseScale = outerRadius / 150.dp.toPx()

    drawCircle(color = palette.base, radius = outerRadius, center = center)

    drawCircle(
      brush = Brush.radialGradient(
        colors = palette.body,
        center = center,
        radius = outerRadius,
      ),
      radius = outerRadius,
      center = center,
    )

    val grooveCount = 32
    for (i in 0 until grooveCount) {
      val frac = i / (grooveCount - 1).toFloat()
      val r = innerRadius + (outerRadius - innerRadius) * frac
      val alpha = 0.20f + 0.10f * ((i % 5) / 5f)
      val w = (if (i % 7 == 0) 1.2.dp.toPx() else 0.6.dp.toPx()) * baseScale
      drawCircle(
        color = Color.Black.copy(alpha = alpha),
        radius = r,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
      )
    }

    drawCircle(
      color = palette.rim.copy(alpha = 0.45f),
      radius = innerRadius + 2.dp.toPx() * baseScale,
      center = center,
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx() * baseScale),
    )

    drawCircle(
      color = palette.rim.copy(alpha = 0.55f),
      radius = outerRadius - 1.dp.toPx() * baseScale,
      center = center,
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx() * baseScale),
    )
  }
}

/** 黑胶配色：盘面底色 / 径向渐变 / 内外描边 / 外圈光晕。GREEN 即原版配色。 */
private class VinylPalette(
  val base: Color,
  val body: List<Color>,
  val rim: Color,
  val glow: Color,
  val glowStrength: Float = 1f,
)

private fun vinylPalette(color: com.baogemusic.tv.data.store.VinylColor): VinylPalette = when (color) {
  com.baogemusic.tv.data.store.VinylColor.GREEN -> VinylPalette(
    base = Color(0xFF0E2818),
    body = listOf(Color(0xFF0A1F12), Color(0xFF112F1C), Color(0xFF1B4528)),
    rim = AppColors.AccentGreen,
    glow = AppColors.AccentGreen,
  )
  // 真黑胶：近黑盘面 + 淡灰描边，光晕压得很弱，避免发灰
  com.baogemusic.tv.data.store.VinylColor.BLACK -> VinylPalette(
    base = Color(0xFF0B0B0B),
    body = listOf(Color(0xFF080808), Color(0xFF141414), Color(0xFF242424)),
    rim = Color(0xFF9A9A9A),
    glow = Color(0xFFFFFFFF),
    glowStrength = 0.25f,
  )
  com.baogemusic.tv.data.store.VinylColor.RED -> VinylPalette(
    base = Color(0xFF2A0D12),
    body = listOf(Color(0xFF1E080C), Color(0xFF341018), Color(0xFF4E1A24)),
    rim = Color(0xFFE0566B),
    glow = Color(0xFFE0566B),
  )
  com.baogemusic.tv.data.store.VinylColor.BLUE -> VinylPalette(
    base = Color(0xFF0C1A2E),
    body = listOf(Color(0xFF081222), Color(0xFF102440), Color(0xFF1A365C)),
    rim = Color(0xFF5B9BFF),
    glow = Color(0xFF5B9BFF),
  )
  com.baogemusic.tv.data.store.VinylColor.GOLD -> VinylPalette(
    base = Color(0xFF2A1E08),
    body = listOf(Color(0xFF1E1505), Color(0xFF35260A), Color(0xFF4F3A12)),
    rim = Color(0xFFE8B04A),
    glow = Color(0xFFE8B04A),
  )
}

// ============== 下方其余交互逻辑组件保持完全不变 =================================================================

@Composable
private fun EqualizerDialog(onDismiss: () -> Unit) {
  val eq = appContainer.equalizerManager
  val presets = remember { eq.presets }
  var selected by remember { mutableStateOf(eq.currentPresetIndex) }
  val activeRowFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    tryRequestFocus(activeRowFocus)
  }

  androidx.compose.ui.window.Dialog(
    onDismissRequest = onDismiss,
    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Column(
      modifier = Modifier
        .width(360.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(AppColors.BgPanel)
        .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
      Text("均衡器", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(4.dp))
      Text(
        if (presets.isEmpty()) "本机不支持系统均衡器" else "选择一个预设，立即生效",
        color = AppColors.TextSecondary,
        fontSize = 12.sp,
      )
      Spacer(Modifier.height(14.dp))
      if (presets.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          presets.forEachIndexed { idx, label ->
            val active = idx == selected
            val rowModifier = Modifier
              .fillMaxWidth()
              .let { if (active) it.focusRequester(activeRowFocus) else it }
            TvFocusable(
              onClick = {
                selected = idx
                eq.applyPreset(idx)
              },
              modifier = rowModifier,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  label,
                  color = if (active) AppColors.AccentGreen else AppColors.TextPrimary,
                  fontSize = 14.sp,
                  fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                  modifier = Modifier.weight(1f),
                )
                if (active) {
                  Text("✓", color = AppColors.AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TransportBar(
  state: com.baogemusic.tv.playback.PlaybackState,
  love: com.baogemusic.tv.data.model.Playlist,
  audioSpec: com.baogemusic.tv.playback.AudioSpec?,
  onTogglePlay: () -> Unit,
  onPrev: () -> Unit,
  onNext: () -> Unit,
  onCycleRepeat: () -> Unit,
  onToggleFav: () -> Unit,
  onShowQueue: () -> Unit,
  onTuneClick: () -> Unit,
  /** 当前是否处于全屏歌词特效（非「关闭」）。用于切换图标的高亮与含义。 */
  fullscreenLyricOn: Boolean = false,
  /** 切换全屏歌词特效（关闭 → 光感 → 星海 → 关闭）。 */
  onToggleFullscreenLyric: () -> Unit = {},
  playFocusRequester: FocusRequester? = null,
  progressFocusRequester: FocusRequester? = null,
) {
  val track = state.currentTrack
  val faved = track?.let { t -> love.tracks.any { it.id == t.id } } ?: false
  val tuneFocus = remember { FocusRequester() }
  val qualityFocus = remember { FocusRequester() }
  val repeatFocus = remember { FocusRequester() }
  val prevFocus = remember { FocusRequester() }
  val playFocus = playFocusRequester ?: remember { FocusRequester() }
  val nextFocus = remember { FocusRequester() }
  val faveFocus = remember { FocusRequester() }
  val lyricsFocus = remember { FocusRequester() }
  val queueFocus = remember { FocusRequester() }
  // 播放页盖在主界面上，主界面的控件仍在下面参与焦点查找。交给系统按位置找的话，会找到被盖住的
  // 控件（比如推荐页迷你播放条的上一曲，#24），所以控制栏每个方向都写死。
  // 音质标签只在成功播放过后才有（第一首就失败时没有），两侧的跳转按有无标签切换。
  val hasQuality = state.quality != null

  // 向下拦住（控制栏在最底部），向上统一回进度条。
  Box(
    modifier = Modifier.fillMaxWidth().focusProperties {
      down = FocusRequester.Cancel
      if (progressFocusRequester != null) up = progressFocusRequester
    },
  ) {
    Row(
      modifier = Modifier.align(Alignment.CenterStart),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      IconPill(
        Icons.Filled.Tune,
        onClick = onTuneClick,
        focusRequester = tuneFocus,
        modifier = Modifier.focusProperties {
          left = FocusRequester.Cancel
          right = if (hasQuality) qualityFocus else repeatFocus
        },
      )
      val displayed = com.baogemusic.tv.playback.displayQuality(state.quality, audioSpec)
      displayed?.let { q ->
        TvPill(
          onClick = { },
          selected = true,
          focusRequester = qualityFocus,
          modifier = Modifier.focusProperties {
            left = tuneFocus
            right = repeatFocus
          },
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
          Text(q.badgeLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        audioSpec?.let { spec ->
          Text(
            spec.displayText,
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }
    Row(
      modifier = Modifier.align(Alignment.Center),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val repeatIcon = when {
        state.shuffle -> Icons.Filled.Shuffle
        state.repeatMode == RepeatMode.ONE -> Icons.Filled.RepeatOne
        else -> Icons.Filled.Repeat
      }
      IconPill(
        repeatIcon,
        active = true,
        focusRequester = repeatFocus,
        onClick = onCycleRepeat,
        modifier = Modifier.focusProperties {
          left = if (hasQuality) qualityFocus else tuneFocus
          right = prevFocus
        },
      )
      IconPill(
        Icons.Filled.SkipPrevious,
        focusRequester = prevFocus,
        onClick = onPrev,
        modifier = Modifier.focusProperties {
          left = repeatFocus
          right = playFocus
        },
      )
      IconPill(
        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        large = true,
        focusRequester = playFocus,
        onClick = onTogglePlay,
        modifier = Modifier.focusProperties {
          left = prevFocus
          right = nextFocus
        },
      )
      IconPill(
        Icons.Filled.SkipNext,
        focusRequester = nextFocus,
        onClick = onNext,
        modifier = Modifier.focusProperties {
          left = playFocus
          right = faveFocus
        },
      )
      IconPill(
        if (faved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        active = faved,
        focusRequester = faveFocus,
        onClick = onToggleFav,
        modifier = Modifier.focusProperties {
          left = nextFocus
          right = lyricsFocus
        },
      )
    }
    Row(
      modifier = Modifier.align(Alignment.CenterEnd),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // 全屏歌词：关闭 → 光感 → 星海 → 关闭 循环切换。
      IconPill(
        if (fullscreenLyricOn) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
        active = fullscreenLyricOn,
        focusRequester = lyricsFocus,
        onClick = onToggleFullscreenLyric,
        modifier = Modifier.focusProperties {
          left = faveFocus
          right = queueFocus
        },
      )
      IconPill(
        Icons.AutoMirrored.Filled.QueueMusic,
        focusRequester = queueFocus,
        onClick = onShowQueue,
        modifier = Modifier.focusProperties {
          left = lyricsFocus
          right = FocusRequester.Cancel
        },
      )
    }
  }
}

private const val AUTO_HIDE_MS = 5_000L
/** 控件隐藏时用来唤出控件的键：方向键 + 确认键。 */
private val WAKE_KEYS = setOf(
  android.view.KeyEvent.KEYCODE_DPAD_UP,
  android.view.KeyEvent.KEYCODE_DPAD_DOWN,
  android.view.KeyEvent.KEYCODE_DPAD_LEFT,
  android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
  android.view.KeyEvent.KEYCODE_DPAD_CENTER,
  android.view.KeyEvent.KEYCODE_ENTER,
  android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
)
private const val SCRUB_TAP_MS = 220L
private const val SCRUB_TAP_STEP_MS = 5_000L
private const val SCRUB_BASE_PER_SEC = 6_000f
private const val SCRUB_MAX_PER_SEC = 60_000f
private const val SCRUB_ACCEL_PER_SEC2 = 40_000f
private const val SCRUB_RELEASE_TIMEOUT_MS = 1_000L

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ProgressBar(
  positionMs: Long,
  durationMs: Long,
  onSeek: (Long) -> Unit,
  focusRequester: FocusRequester,
  downFocusRequester: FocusRequester,
) {
  var focused by remember { mutableStateOf(false) }
  var scrubDir by remember { mutableStateOf(0) }
  var previewMs by remember { mutableStateOf(0L) }
  var scrubStartPos by remember { mutableStateOf(0L) }
  var scrubStartAt by remember { mutableStateOf(0L) }
  var lastKeyAt by remember { mutableStateOf(0L) }

  fun commit() {
    if (scrubDir == 0) return
    val held = android.os.SystemClock.uptimeMillis() - scrubStartAt
    val target = if (held <= SCRUB_TAP_MS) {
      (scrubStartPos + scrubDir * SCRUB_TAP_STEP_MS).coerceIn(0L, durationMs)
    } else {
      previewMs.coerceIn(0L, durationMs)
    }
    onSeek(target)
    scrubDir = 0
  }

  LaunchedEffect(scrubDir) {
    if (scrubDir == 0) return@LaunchedEffect
    var lastT = android.os.SystemClock.uptimeMillis()
    while (scrubDir != 0) {
      withFrameNanos { }
      val now = android.os.SystemClock.uptimeMillis()
      val dt = (now - lastT).coerceAtLeast(0L)
      lastT = now
      val heldSec = (now - scrubStartAt) / 1000f
      val speed = (SCRUB_BASE_PER_SEC + SCRUB_ACCEL_PER_SEC2 * heldSec).coerceAtMost(SCRUB_MAX_PER_SEC)
      previewMs = (previewMs + scrubDir * speed * (dt / 1000f)).toLong().coerceIn(0L, durationMs)
      if (now - lastKeyAt > SCRUB_RELEASE_TIMEOUT_MS) commit()
    }
  }

  val shownMs = if (scrubDir != 0) previewMs else positionMs
  val fraction = if (durationMs > 0) (shownMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
  Column(
    modifier = Modifier
      .fillMaxWidth()
      // 播放页是盖在主界面上的浮层，进度条上方没有可聚焦控件；不拦住的话再按上键焦点会
      // 穿到被盖住的顶部导航，之后怎么按都回不来。
      .focusRequester(focusRequester)
      .focusProperties {
        up = FocusRequester.Cancel
        down = downFocusRequester
      }
      .onFocusChanged { fs ->
        focused = fs.isFocused
        if (!fs.isFocused && scrubDir != 0) commit()
      }
      .onPreviewKeyEvent { evt ->
        if (durationMs <= 0) return@onPreviewKeyEvent false
        val dir = when (evt.key) {
          Key.DirectionLeft -> -1
          Key.DirectionRight -> 1
          else -> return@onPreviewKeyEvent false
        }
        when (evt.type) {
          KeyEventType.KeyDown -> {
            lastKeyAt = android.os.SystemClock.uptimeMillis()
            if (scrubDir == 0) {
              scrubStartPos = positionMs
              previewMs = positionMs
              scrubStartAt = lastKeyAt
            }
            scrubDir = dir
            true
          }
          KeyEventType.KeyUp -> {
            commit(); true
          }
          else -> false
        }
      }
      .focusable(),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(if (focused) 6.dp else 3.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(if (focused) AppColors.Card.copy(alpha = 0.9f) else AppColors.Card),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(fraction)
          .fillMaxHeight()
          .background(AppColors.AccentGreen),
      )
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        fmt(shownMs),
        color = if (focused) AppColors.AccentGreen else AppColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
      )
      Text(fmt(durationMs), color = AppColors.TextMuted, fontSize = 11.sp)
    }
  }
}

/** 上下渐隐遮罩：滚进 / 滚出视野的歌词淡入淡出，避免被面板边缘硬切。用于歌词列表外层。 */
private fun Modifier.lyricFadeEdges(fadeFraction: Float = 0.12f): Modifier = this
  .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
  .drawWithContent {
    drawContent()
    val fade = size.height * fadeFraction
    drawRect(
      topLeft = Offset(0f, 0f),
      size = Size(size.width, fade),
      brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
      blendMode = BlendMode.DstIn,
    )
    drawRect(
      topLeft = Offset(0f, size.height - fade),
      size = Size(size.width, fade),
      brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = 0f, endY = fade),
      blendMode = BlendMode.DstIn,
    )
  }

/** KTV 式逐字染色：已唱过的部分用高亮色覆盖（只在文字像素上着色，不会盖到背景）。 */
private fun Modifier.lyricSweep(progress: () -> Float, color: Color): Modifier = this
  .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
  .drawWithContent {
    drawContent()
    val p = progress().coerceIn(0f, 1f)
    if (p > 0.002f) {
      drawRect(
        topLeft = Offset.Zero,
        size = Size(size.width * p, size.height),
        // 右端留一点渐隐，扫描边缘不那么生硬
        brush = Brush.horizontalGradient(listOf(color, color, color.copy(alpha = 0.4f))),
        blendMode = BlendMode.SrcAtop,
      )
    }
  }

/** 没有歌词时的占位面板。抓取中显示进度，抓不到时明确说清试过哪些源，并给一个「重新获取」。 */
@Composable
private fun LyricEmptyPane(
  failureHint: String?,
  loading: Boolean,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      when {
        loading -> "正在获取歌词…"
        failureHint != null -> "暂无歌词"
        else -> "纯音乐，请欣赏"
      },
      color = AppColors.TextMuted,
      fontSize = 16.sp,
    )
    failureHint?.takeIf { !loading }?.let { hint ->
      Spacer(Modifier.height(8.dp))
      Text(
        hint,
        color = AppColors.Warning,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(0.7f),
      )
      Spacer(Modifier.height(14.dp))
      TvPill(onClick = onRetry, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)) {
        Text("重新获取", fontSize = 13.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
private fun LyricPane(
  lyrics: List<com.baogemusic.tv.playback.LyricLine>,
  positionMs: Long,
  lyricFontSizeSp: Int,
  lyricAlignment: LyricAlignment,
  /** 行距倍率（字号 × 该值），同时作用于行高与行间距。 */
  lineFactor: Float,
  showTranslation: Boolean,
  /** 神光模式：一次只显示一句话（切换时淡入淡出 + 轻微上移），其余歌词不占位。 */
  singleLine: Boolean = false,
  /** 仅作用于歌词的字体（设置 → 歌词 → 字体）。null 表示系统默认字体。 */
  fontFamily: FontFamily? = null,
  /** 全部数据源都没拿到歌词时的说明（含已尝试过哪些源）。 */
  failureHint: String? = null,
  loading: Boolean = false,
  onRetry: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  if (lyrics.isEmpty()) {
    LyricEmptyPane(
      failureHint = failureHint,
      loading = loading,
      onRetry = onRetry,
      modifier = modifier,
    )
    return
  }
  val active = LyricParser.activeIndex(positionMs / 1000.0, lyrics)
  val listState = rememberLazyListState()
  // 实测面板高度，让当前行停在纵向约 1/3 处，而不是贴着顶部 padding——落点更接近主流播放器的观感。
  var paneHeightPx by remember { mutableIntStateOf(0) }
  LaunchedEffect(active, paneHeightPx) {
    if (active < 0 || paneHeightPx <= 0) return@LaunchedEffect
    val anchor = paneHeightPx * 0.32f
    // 目标行已在视野内时按固定缓动平移（比 animateScrollToItem 的 spring 更稳、不回弹）；
    // 不在视野内（切歌 / 长跳转）才用 animateScrollToItem 直接跳过去。
    val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active }
    if (visible != null) {
      runCatching { listState.animateScrollBy(visible.offset - anchor, tween(420, easing = FastOutSlowInEasing)) }
    } else {
      runCatching { listState.animateScrollToItem(active, scrollOffset = -anchor.roundToInt()) }
    }
  }

  // KTV 逐字推进：positionMs 每秒才 tick 一次，这里用 Animatable 线性补到目标值，
  // 视觉上是连续扫过而不是一秒一跳。
  val sweep = remember { Animatable(0f) }
  val positionSec = positionMs / 1000.0
  var lastActive by remember { mutableIntStateOf(-1) }
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
      // 纯文本歌词（无时间轴）或算不出时长 → 整行直接点亮，不做扫描。
      if (start < 0 || end == null || end <= start) 1f
      else ((positionSec - start) / (end - start)).coerceIn(0.0, 1.0).toFloat()
    }
    sweep.animateTo(target, tween(durationMillis = 900, easing = LinearEasing))
  }

  // 神光模式：一屏一句，切句时淡入 + 轻微上移，旧句淡出 + 上移
  if (singleLine) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      androidx.compose.animation.AnimatedContent(
        targetState = active,
        transitionSpec = {
          (androidx.compose.animation.fadeIn(tween(320)) +
            androidx.compose.animation.slideInVertically(tween(320)) { it / 4 }) togetherWith
            (androidx.compose.animation.fadeOut(tween(220)) +
              androidx.compose.animation.slideOutVertically(tween(220)) { -it / 4 })
        },
        label = "singleLyric",
      ) { idx ->
        val line = lyrics.getOrNull(idx)
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            line?.text ?: "",
            color = AppColors.TextPrimary,
            fontFamily = fontFamily,
            fontSize = lyricFontSizeSp.sp,
            lineHeight = (lyricFontSizeSp * lineFactor).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .lyricSweep({ sweep.value }, AppColors.LyricActive),
          )
          line?.translation?.takeIf { showTranslation }?.let { t ->
            Spacer(Modifier.height(8.dp))
            Text(
              t,
              color = AppColors.LyricActive.copy(alpha = 0.72f),
              fontFamily = fontFamily,
              fontSize = com.baogemusic.tv.data.store.LyricScaleRange.translationOf(lyricFontSizeSp).sp,
              lineHeight = (com.baogemusic.tv.data.store.LyricScaleRange.translationOf(lyricFontSizeSp) * lineFactor).sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }
    return
  }

  val inactiveSp = com.baogemusic.tv.data.store.LyricScaleRange.inactiveOf(lyricFontSizeSp)
  val translationSp = com.baogemusic.tv.data.store.LyricScaleRange.translationOf(lyricFontSizeSp)
  val factor = lineFactor
  // 行距倍率同时作用于「行内行高」和「行间留白」，倍率越大整块歌词越松。
  val rowGap = (lyricFontSizeSp * (factor - 1f) * 0.75f).dp

  val textAlign = when (lyricAlignment) {
    LyricAlignment.LEFT -> TextAlign.Start
    LyricAlignment.CENTER -> TextAlign.Center
    LyricAlignment.RIGHT -> TextAlign.End
  }
  val rowAlign = when (lyricAlignment) {
    LyricAlignment.LEFT -> Alignment.Start
    LyricAlignment.CENTER -> Alignment.CenterHorizontally
    LyricAlignment.RIGHT -> Alignment.End
  }
  // 缩放锚点跟着对齐方式走，当前行放大时不会把文字推出面板边界。
  val scaleOrigin = when (lyricAlignment) {
    LyricAlignment.LEFT -> TransformOrigin(0f, 0.5f)
    LyricAlignment.CENTER -> TransformOrigin(0.5f, 0.5f)
    LyricAlignment.RIGHT -> TransformOrigin(1f, 0.5f)
  }

  val floatSpec = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
  val colorSpec = tween<Color>(durationMillis = 320, easing = FastOutSlowInEasing)

  Box(modifier = modifier.onSizeChanged { paneHeightPx = it.height }) {
    LazyColumn(
      state = listState,
      modifier = Modifier.matchParentSize().lyricFadeEdges(),
      verticalArrangement = Arrangement.spacedBy(rowGap),
      horizontalAlignment = rowAlign,
      contentPadding = PaddingValues(vertical = 72.dp),
    ) {
      itemsIndexed(lyrics) { i, line ->
        val isActive = i == active
        val distance = if (active >= 0) abs(i - active) else 0
        val targetAlpha = if (isActive) 1f else (1f - distance * 0.18f).coerceAtLeast(0.25f)
        // 逐行动画：切换当前行时透明度 / 缩放 / 颜色平滑过渡，而不是硬跳变。
        val alpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = floatSpec, label = "lyricAlpha")
        val scale by animateFloatAsState(
          targetValue = if (isActive) 1.06f else 1f,
          animationSpec = floatSpec,
          label = "lyricScale",
        )
        // 当前行底色压暗，已唱部分由 lyricSweep 用高亮色扫过 —— 标准 KTV 观感；
        // 非当前行再暗一档，靠 alpha 距离衰减拉开层次。
        val color by animateColorAsState(
          targetValue = if (isActive) AppColors.TextSecondary else AppColors.TextMuted,
          animationSpec = colorSpec,
          label = "lyricColor",
        )
        Column(
          horizontalAlignment = rowAlign,
          modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
            transformOrigin = scaleOrigin
          },
        ) {
          Text(
            line.text,
            color = color,
            fontFamily = fontFamily,
            fontSize = (if (isActive) lyricFontSizeSp else inactiveSp).sp,
            // 显式行高：默认行高跟不上大字号，长歌词换行时上下两行会重叠（#21）。
            lineHeight = ((if (isActive) lyricFontSizeSp else inactiveSp) * factor).sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            textAlign = textAlign,
            modifier = Modifier
              .fillMaxWidth()
              .then(if (isActive) Modifier.lyricSweep({ sweep.value }, AppColors.LyricActive) else Modifier),
          )
          line.translation?.takeIf { showTranslation }?.let { t ->
            Text(
              t,
              color = if (isActive) AppColors.LyricActive.copy(alpha = 0.7f) else AppColors.TextMuted,
              fontFamily = fontFamily,
              fontSize = translationSp.sp,
              lineHeight = (translationSp * factor).sp,
              textAlign = textAlign,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun IconPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  active: Boolean = false,
  large: Boolean = false,
  focusRequester: FocusRequester? = null,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  TvPill(
    onClick = onClick,
    modifier = modifier,
    shape = androidx.compose.foundation.shape.CircleShape,
    contentPadding = androidx.compose.foundation.layout.PaddingValues(if (large) 14.dp else 10.dp),
    selected = active,
    focusRequester = focusRequester,
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(if (large) 30.dp else 22.dp))
  }
}

private fun fmt(ms: Long): String {
  val s = (ms / 1000).toInt()
  return "%02d:%02d".format(s / 60, s % 60)
}

private suspend fun tryRequestFocus(target: FocusRequester) {
  repeat(5) {
    kotlinx.coroutines.delay(50)
    if (runCatching { target.requestFocus() }.isSuccess) return
  }
}

@Composable
private fun QueueDrawer(
  visible: Boolean,
  queue: List<Track>,
  currentIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val firstFocus = remember { FocusRequester() }

  LaunchedEffect(visible) {
    if (visible && queue.isNotEmpty()) {
      val target = currentIndex.coerceIn(0, queue.size - 1)
      runCatching { listState.scrollToItem(target) }
      delay(80)
      runCatching { firstFocus.requestFocus() }
    }
  }

  AnimatedVisibility(
    visible = visible,
    enter = slideInHorizontally(initialOffsetX = { it }),
    exit = slideOutHorizontally(targetOffsetX = { it }),
    modifier = modifier,
  ) {
    Box(modifier = Modifier.fillMaxHeight().width(360.dp).background(AppColors.BgPanel.copy(alpha = 0.95f))) {
      Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
          "播放队列 (${queue.size})",
          color = AppColors.TextPrimary,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        val rowKeys = remember(queue) { com.baogemusic.tv.ui.components.uniqueKeys(queue.map { it.id }) }
        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          itemsIndexed(queue, key = { i, _ -> rowKeys[i] }) { idx, track ->
            val isCurrent = idx == currentIndex
            QueueRow(
              track = track,
              index = idx,
              isCurrent = isCurrent,
              modifier = if (isCurrent) Modifier.focusRequester(firstFocus) else Modifier,
              onClick = { onSelect(idx) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun QueueRow(
  track: Track,
  index: Int,
  isCurrent: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  TvFocusable(onClick = onClick, modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.width(22.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (isCurrent) {
          com.baogemusic.tv.ui.components.MiniWaveform(
            modifier = Modifier.size(width = 18.dp, height = 14.dp),
          )
        } else {
          Text(
            "${index + 1}",
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }
      Spacer(Modifier.width(8.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          track.name,
          color = if (isCurrent) AppColors.AccentGreen else AppColors.TextPrimary,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          track.singer,
          color = AppColors.TextMuted,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * 播放页背景动效：神光 / 动感波光。
 *
 * 参考网易云「神光模式」（中心柔光 + 随节奏呼吸）与 QQ 音乐「动感波光」（随音量向外扩散的波纹），
 * 但全部用 Compose 的 Canvas 直接画（径向渐变 + Screen 混合），不依赖 WebView / WebGL / 着色器，
 * 所以在 Android 8 的电视、投影仪这类改过内核 / 没有硬件加速的设备上也能正常跑。
 */
/**
 * 播放页背景动效：动感波光（参考 QQ 音乐）。
 *
 * 纯 Compose Canvas 绘制（径向渐变 + Screen 混合），不依赖 WebView / WebGL / 着色器，
 * 所以在 Android 8 的电视、投影仪这类改过内核 / 没有硬件加速的设备上也能正常跑。
 *
 * 波纹从 [PlayerBackdrop] 自身的中心向外扩散——它被包在封面容器里、与封面同心，
 * 因此波纹天然从「专辑中心」向外延伸，并随唱片在左 / 在右（[PlayerLayout]）一起移动。
 */
@Composable
private fun PlayerBackdrop(
  fx: PlayerFx,
  isPlaying: Boolean,
  level: Float,
  glow: Color,
  modifier: Modifier = Modifier,
) {
  if (fx == PlayerFx.OFF) return
  if (fx == PlayerFx.RIPPLE) {
    RippleBackdrop(isPlaying = isPlaying, level = level, glow = glow, modifier = modifier)
    return
  }
  if (fx == PlayerFx.DUST) {
    DustBackdrop(isPlaying = isPlaying, level = level, glow = glow, modifier = modifier)
    return
  }
  val transition = rememberInfiniteTransition(label = "playerFx")
  val t by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3_400, easing = LinearEasing),
    ),
    label = "fxTime",
  )
  // 音量做一次平滑，避免波形抖动导致光效闪烁
  val smooth by animateFloatAsState(
    targetValue = if (isPlaying) level.coerceIn(0f, 1f) else 0f,
    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
    label = "fxLevel",
  )
  androidx.compose.foundation.Canvas(
    modifier = modifier
      .fillMaxSize()
      .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
  ) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val base = size.minDimension
    val center = Offset(cx, cy)
    if (base <= 0f) return@Canvas
    // 动感波光：三圈随时间向外扩散的波纹 + 中心呼吸光（中心 = 专辑中心）
    for (i in 0 until 3) {
      val p = (t + i / 3f) % 1f
      val r = base * (0.20f + p * 0.58f) * (1f + smooth * 0.20f)
      val alpha = (1f - p) * (0.14f + smooth * 0.24f)
      drawCircle(
        color = glow.copy(alpha = alpha),
        radius = r,
        center = center,
        style = Stroke(width = base * 0.010f * (1f - p * 0.4f)),
        blendMode = BlendMode.Screen,
      )
    }
    val r = base * (0.30f + smooth * 0.10f)
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(glow.copy(alpha = 0.20f + smooth * 0.16f), Color.Transparent),
        center = center,
        radius = r,
      ),
      radius = r,
      center = center,
      blendMode = BlendMode.Screen,
    )
  }
}

/**
 * 波纹特效（参考 BMMusicPlayer）：从封面中心一圈圈荡开的同心水波，带细微白色高光边，
 * 比「动感波光」更厚、更慢、更有水面连续涟漪的感觉。中心 = 专辑中心（Canvas 包在封面容器里），
 * 因此天然从封面中心向外延伸，并随唱片在左 / 在右一起移动。纯 Compose Canvas 绘制。
 */
@Composable
private fun RippleBackdrop(
  isPlaying: Boolean,
  level: Float,
  glow: Color,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "rippleFx")
  val t by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 4_200, easing = LinearEasing),
    ),
    label = "rippleTime",
  )
  val smooth by animateFloatAsState(
    targetValue = if (isPlaying) level.coerceIn(0f, 1f) else 0f,
    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
    label = "rippleLevel",
  )
  androidx.compose.foundation.Canvas(
    modifier = modifier
      .fillMaxSize()
      .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
  ) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val base = size.minDimension
    val center = Offset(cx, cy)
    if (base <= 0f) return@Canvas
    val rings = 6
    for (i in 0 until rings) {
      val p = ((t + i.toFloat() / rings) % 1f)
      val r = base * (0.08f + p * 0.62f) * (1f + smooth * 0.15f)
      val alpha = ((1f - p) * (0.10f + smooth * 0.22f)).coerceIn(0f, 1f)
      // 主波：较厚的光环
      drawCircle(
        color = glow.copy(alpha = alpha),
        radius = r,
        center = center,
        style = Stroke(width = base * 0.018f * (1f - p * 0.5f)),
        blendMode = BlendMode.Screen,
      )
      // 内沿高光：让涟漪更接近水面反光
      drawCircle(
        color = Color.White.copy(alpha = alpha * 0.28f),
        radius = r * 0.985f,
        center = center,
        style = Stroke(width = base * 0.004f),
        blendMode = BlendMode.Screen,
      )
    }
    // 中心柔和涟漪光
    val rr = base * (0.22f + smooth * 0.08f)
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(glow.copy(alpha = 0.16f + smooth * 0.14f), Color.Transparent),
        center = center,
        radius = rr,
      ),
      radius = rr,
      center = center,
      blendMode = BlendMode.Screen,
    )
  }
}

/**
 * 宇宙尘埃特效（参考 ParticleView）：大量细碎尘埃 / 星点在背景中缓慢上浮、明暗闪烁，
 * 中心叠一层极淡星云。粒子位置用固定随机种子生成，保证每帧稳定不抖动。纯 Compose Canvas 绘制。
 */
@Composable
private fun DustBackdrop(
  isPlaying: Boolean,
  level: Float,
  glow: Color,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "dustFx")
  val t by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 12_000, easing = LinearEasing),
    ),
    label = "dustTime",
  )
  val smooth by animateFloatAsState(
    targetValue = if (isPlaying) level.coerceIn(0f, 1f) else 0f,
    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
    label = "dustLevel",
  )
  // 固定种子的粒子：x0, y0, 尺寸系数, 相位, 闪烁速度, 漂移系数
  val dots = remember {
    val rnd = kotlin.random.Random(2024)
    List(110) {
      floatArrayOf(
        rnd.nextFloat(),
        rnd.nextFloat(),
        0.4f + rnd.nextFloat() * 1.8f,
        rnd.nextFloat(),
        0.4f + rnd.nextFloat() * 1.4f,
        0.4f + rnd.nextFloat() * 0.9f,
      )
    }
  }
  androidx.compose.foundation.Canvas(
    modifier = modifier
      .fillMaxSize()
      .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
  ) {
    val w = size.width
    val h = size.height
    val base = size.minDimension
    if (base <= 0f) return@Canvas
    val center = Offset(w / 2f, h / 2f)
    // 中心星云
    val nebula = base * 0.52f
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(glow.copy(alpha = 0.10f + smooth * 0.10f), Color.Transparent),
        center = center,
        radius = nebula,
      ),
      radius = nebula,
      center = center,
      blendMode = BlendMode.Screen,
    )
    for (d in dots) {
      // 缓慢上浮 + 轻微左右摆动
      val yNorm = (((d[1] - t * 0.16f * d[5]) % 1f) + 1f) % 1f
      val xNorm = (d[0] + sin(t * 2f * PI.toFloat() + d[3] * 6.283f) * 0.012f)
      val px = xNorm * w
      val py = yNorm * h
      val twinkle = 0.35f + 0.65f * abs(sin((t * d[4] + d[3]) * 2f * PI.toFloat()))
      val alpha = (twinkle * (0.45f + smooth * 0.35f)).coerceIn(0f, 1f)
      val radius = base * 0.0035f * d[2] * (0.7f + twinkle * 0.6f)
      drawCircle(color = glow.copy(alpha = alpha), radius = radius, center = Offset(px, py), blendMode = BlendMode.Screen)
      // 少量大颗粒带光晕
      if (d[2] > 1.8f) {
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(glow.copy(alpha = alpha * 0.6f), Color.Transparent),
            center = Offset(px, py),
            radius = radius * 4f,
          ),
          radius = radius * 4f,
          center = Offset(px, py),
          blendMode = BlendMode.Screen,
        )
      }
    }
  }
}

// ============== Cover backdrop =================================================================

@Composable
private fun CoverBackdrop(picURL: String?) {
  Box(modifier = Modifier.fillMaxSize()) {
    if (!picURL.isNullOrBlank()) {
      val context = androidx.compose.ui.platform.LocalContext.current
      coil.compose.AsyncImage(
        model = coil.request.ImageRequest.Builder(context)
          .data(picURL)
          .transformations(com.baogemusic.tv.ui.components.BlurTransformation(context, radius = 25f, sampling = 2f))
          .crossfade(400)
          .build(),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
      )
      Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
      Box(
        modifier = Modifier.fillMaxSize().background(
          Brush.verticalGradient(
            0f to AppColors.BgDeep.copy(alpha = 0.55f),
            0.28f to Color.Transparent,
            0.72f to Color.Transparent,
            1f to AppColors.BgDeep.copy(alpha = 0.72f),
          ),
        ),
      )
    } else {
      Box(
        modifier = Modifier.fillMaxSize().background(
          Brush.verticalGradient(listOf(AppColors.BgPanel, AppColors.BgDeep)),
        ),
      )
    }
  }
}

// ============== 全屏歌词特效（光感 / 星海 / 胶片）=============================================

/**
 * 全屏歌词特效（参考 YuMusic / QQ 音乐），铺满整屏，左上角显示歌名 / 歌手：
 * - 光感：模糊专辑 + 柔和暗角，居中三行，逐句淡入；
 * - 星海：深蓝星海 + 漂浮星点，蓝白辉光；
 * - 胶片：歌手写真（网易云艺人宣传照）做背景，缓慢推近 + 电影黑边 + 颗粒，底部字幕式歌词；
 *   取不到写真时回退到模糊专辑封面。
 */
@Composable
private fun FullscreenLyricStage(
  fx: FullscreenLyricFx,
  lyrics: List<com.baogemusic.tv.playback.LyricLine>,
  positionMs: Long,
  fontSp: Int,
  lineFactor: Float,
  showTranslation: Boolean,
  fontFamily: FontFamily?,
  trackName: String,
  artist: String,
  artistPhoto: String? = null,
  coverPic: String? = null,
  modifier: Modifier = Modifier,
) {
  val active = LyricParser.activeIndex(positionMs / 1000.0, lyrics)
  val prev = lyrics.getOrNull(active - 1)
  val next = lyrics.getOrNull(active + 1)
  val cinema = fx == FullscreenLyricFx.CINEMA
  val activeColor = when (fx) {
    FullscreenLyricFx.STARSEA -> Color(0xFFC7E6FF)
    FullscreenLyricFx.CINEMA -> Color.White
    else -> AppColors.LyricActive
  }
  val glowRadius = when (fx) {
    FullscreenLyricFx.STARSEA -> 34f
    FullscreenLyricFx.CINEMA -> 26f
    else -> 20f
  }

  Box(modifier.fillMaxSize()) {
    // ---- 背景：铺满整屏 ----
    when (fx) {
      FullscreenLyricFx.STARSEA -> StarField(Modifier.fillMaxSize())
      FullscreenLyricFx.CINEMA -> CinemaBackdrop(
        photoUrl = artistPhoto ?: coverPic,
        modifier = Modifier.fillMaxSize(),
      )
      else -> {
        // 光感：径向暗角（中间透、四周压暗）+ 上下渐隐，突出中间歌词。
        Box(
          Modifier.fillMaxSize().background(
            Brush.radialGradient(
              colors = listOf(
                Color.Transparent,
                AppColors.BgDeep.copy(alpha = 0.40f),
                AppColors.BgDeep.copy(alpha = 0.86f),
              ),
              radius = 2200f,
            ),
          ),
        )
        Box(
          Modifier.fillMaxSize().background(
            Brush.verticalGradient(
              listOf(
                AppColors.BgDeep.copy(alpha = 0.55f),
                Color.Transparent,
                AppColors.BgDeep.copy(alpha = 0.72f),
              ),
            ),
          ),
        )
      }
    }

    // ---- 左上角：歌名 + 歌手 ----
    Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 44.dp)) {
      Text(
        trackName,
        color = AppColors.TextPrimary,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        artist,
        color = AppColors.TextSecondary,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    if (lyrics.isEmpty()) {
      Text(
        "暂无歌词",
        color = AppColors.TextSecondary,
        fontSize = 18.sp,
        modifier = Modifier.align(Alignment.Center),
      )
    } else if (cinema) {
      // ---- 胶片：底部字幕式（只显示当前句 + 翻译）----
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(start = 120.dp, end = 120.dp, bottom = 150.dp),
      ) {
        androidx.compose.animation.AnimatedContent(
          targetState = active,
          transitionSpec = {
            androidx.compose.animation.fadeIn(tween(400)) togetherWith
              androidx.compose.animation.fadeOut(tween(260))
          },
          label = "cinemaLyric",
        ) { idx ->
          val l = lyrics.getOrNull(idx)
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              l?.text ?: "",
              color = activeColor,
              fontSize = (fontSp * 0.92f).sp,
              lineHeight = (fontSp * 0.92f * lineFactor).sp,
              fontFamily = fontFamily,
              fontWeight = FontWeight.Medium,
              textAlign = TextAlign.Center,
              style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                  color = Color.Black.copy(alpha = 0.85f),
                  blurRadius = glowRadius,
                ),
              ),
              modifier = Modifier.fillMaxWidth(),
            )
            l?.translation?.takeIf { showTranslation }?.let { t ->
              Spacer(Modifier.height(8.dp))
              Text(
                t,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = (fontSp * 0.5f).sp,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }
    } else {
      // ---- 居中三行（光感 / 星海）----
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .align(Alignment.Center)
          .fillMaxWidth()
          .padding(horizontal = 120.dp),
      ) {
        Text(
          prev?.text ?: "",
          color = AppColors.TextMuted.copy(alpha = 0.45f),
          fontSize = (fontSp * 0.52f).sp,
          fontFamily = fontFamily,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(30.dp))

        androidx.compose.animation.AnimatedContent(
          targetState = active,
          transitionSpec = {
            androidx.compose.animation.fadeIn(tween(420)) togetherWith
              androidx.compose.animation.fadeOut(tween(300))
          },
          label = "fullscreenLyric",
        ) { idx ->
          val l = lyrics.getOrNull(idx)
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              l?.text ?: "",
              color = activeColor,
              fontSize = fontSp.sp,
              lineHeight = (fontSp * lineFactor).sp,
              fontFamily = fontFamily,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                  color = activeColor.copy(alpha = 0.78f),
                  blurRadius = glowRadius,
                ),
              ),
              modifier = Modifier.fillMaxWidth(),
            )
            l?.translation?.takeIf { showTranslation }?.let { t ->
              Spacer(Modifier.height(10.dp))
              Text(
                t,
                color = activeColor.copy(alpha = 0.72f),
                fontSize = (fontSp * 0.5f).sp,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }

        Spacer(Modifier.height(30.dp))
        Text(
          next?.text ?: "",
          color = AppColors.TextMuted.copy(alpha = 0.35f),
          fontSize = (fontSp * 0.46f).sp,
          fontFamily = fontFamily,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

/** 星海背景：深蓝渐变 + 缓慢闪烁 / 漂浮的星点。 */
@Composable
private fun StarField(modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "starSea")
  val t by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(animation = tween(durationMillis = 9_000, easing = LinearEasing)),
    label = "starTime",
  )
  val stars = remember {
    val rnd = kotlin.random.Random(7)
    List(150) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.3f + rnd.nextFloat() * 1.8f, rnd.nextFloat()) }
  }
  androidx.compose.foundation.Canvas(modifier.fillMaxSize()) {
    if (size.minDimension <= 0f) return@Canvas
    drawRect(
      brush = Brush.verticalGradient(
        listOf(Color(0xFF050D18).copy(alpha = 0.82f), Color(0xFF0A1B2E).copy(alpha = 0.82f)),
      ),
    )
    for (s in stars) {
      val yNorm = (((s[1] - t * 0.05f * s[2]) % 1f) + 1f) % 1f
      val twinkle = 0.35f + 0.65f * abs(sin((t * s[2] + s[3]) * 2f * PI.toFloat()))
      val radius = size.minDimension * 0.0026f * s[2] * (0.6f + twinkle * 0.8f)
      drawCircle(
        color = Color(0xFFDCEBFF).copy(alpha = (twinkle * 0.9f).coerceIn(0f, 1f)),
        radius = radius,
        center = Offset(s[0] * size.width, yNorm * size.height),
        blendMode = BlendMode.Screen,
      )
    }
  }
}

/**
 * 胶片：歌手写真（网易云艺人宣传照）做背景，配合缓慢推近的「肯·伯恩斯」镜头 + 上下电影黑边 +
 * 细颗粒 + 纵向暗角，歌词走底部字幕。
 *
 * [photoUrl] 是写真地址；没取到写真时调用方会退一步传专辑封面。刻意**不做模糊**——
 * 播放页根层已经有一层模糊封面了，再模糊一张大图在 1.5GB 内存的盒子上纯属浪费。
 * 两者都没有就落到纯黑底，保证任何情况下都能播放。
 */
@Composable
private fun CinemaBackdrop(
  photoUrl: String?,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "cinemaFx")
  val t by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(animation = tween(durationMillis = 7_000, easing = LinearEasing)),
    label = "cinemaTime",
  )
  // 肯·伯恩斯镜头：20 秒推到 1.08 倍再拉回，慢到几乎察觉不到，但有呼吸感。
  val kenBurns by transition.animateFloat(
    initialValue = 1f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 20_000, easing = LinearEasing),
      repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
    ),
    label = "cinemaKenBurns",
  )
  val grain = remember {
    val rnd = kotlin.random.Random(99)
    List(90) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.4f + rnd.nextFloat() * 1.6f, rnd.nextFloat()) }
  }

  Box(modifier.fillMaxSize()) {
    // ---- 底层：写真 / 模糊封面 / 纯黑 ----
    if (!photoUrl.isNullOrBlank()) {
      val context = androidx.compose.ui.platform.LocalContext.current
      coil.compose.AsyncImage(
        model = coil.request.ImageRequest.Builder(context)
          .data(photoUrl)
          .size(1280, 720)
          .crossfade(700)
          .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            scaleX = kenBurns
            scaleY = kenBurns
          },
      )
      // 压暗 + 底部加重：写真再亮，字幕也得看得清。
      Box(
        Modifier.fillMaxSize().background(
          Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.58f),
            0.34f to Color.Black.copy(alpha = 0.16f),
            0.70f to Color.Black.copy(alpha = 0.48f),
            1f to Color.Black.copy(alpha = 0.90f),
          ),
        ),
      )
      // 轻微冷调，更像电影调色
      Box(
        Modifier.fillMaxSize().background(
          Brush.horizontalGradient(
            listOf(Color(0xFF0A1420).copy(alpha = 0.18f), Color.Transparent, Color(0xFF160E0A).copy(alpha = 0.16f)),
          ),
        ),
      )
    } else {
      Box(Modifier.fillMaxSize().background(Color(0xFF050506)))
    }

    // ---- 上层：颗粒 + 黑边 + 暗角（不画不透明底色，以免盖住写真）----
    androidx.compose.foundation.Canvas(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
      if (size.minDimension <= 0f) return@Canvas
      for (g in grain) {
        val a = (0.02f + 0.05f * abs(sin((t * g[2] + g[3]) * 2f * PI.toFloat()))).coerceIn(0f, 1f)
        drawCircle(
          color = Color.White.copy(alpha = a),
          radius = g[2] * 1.3f,
          center = Offset(g[0] * size.width, g[1] * size.height),
        )
      }
      // 上下电影黑边
      val bar = size.height * 0.10f
      drawRect(Color.Black, topLeft = Offset(0f, 0f), size = Size(size.width, bar))
      drawRect(Color.Black, topLeft = Offset(0f, size.height - bar), size = Size(size.width, bar))
      // 纵向暗角
      drawRect(
        brush = Brush.verticalGradient(
          listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent, Color.Black.copy(alpha = 0.42f)),
        ),
      )
    }
  }
}


