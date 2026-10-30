package com.baogemusic.tv.ui.recommend

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.baogemusic.tv.playback.LyricParser
import com.baogemusic.tv.ui.NavSection
import com.baogemusic.tv.ui.appContainer
import com.baogemusic.tv.ui.components.Artwork
import com.baogemusic.tv.ui.components.TvFocusable
import com.baogemusic.tv.ui.components.TvPill
import com.baogemusic.tv.ui.theme.AppColors

@Composable
fun RecommendScreen(
  onNavigate: (NavSection) -> Unit,
  onOpenPlayer: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // 获取屏幕物理总高度（DP）
  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val screenHeight = configuration.screenHeightDp.dp

  // 优化：左侧面板总宽度精调为屏幕高度的 55%
  val panelDynamicWidth = screenHeight * 0.55f

  Row(modifier = modifier.fillMaxSize().padding(vertical = 8.dp)) {
    NowPlayingPanel(
      onOpenPlayer = onOpenPlayer,
      modifier = Modifier.width(panelDynamicWidth).fillMaxHeight()
    )
    Spacer(Modifier.width(16.dp))
    RecommendGrid(
      onNavigate = onNavigate,
      onOpenPlayer = onOpenPlayer,
      modifier = Modifier.weight(1f).fillMaxHeight(),
    )
  }
}

@Composable
private fun NowPlayingPanel(onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
  val controller = appContainer.playbackController
  val state by controller.state.collectAsState()
  val lyrics by controller.lyrics.collectAsState()
  val track = state.currentTrack

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val screenHeight = configuration.screenHeightDp.dp

  // 优化：封面尺寸精准锁定为屏幕高度的 46%（完美的黄金分割占比）
  val coverDynamicSize = screenHeight * 0.46f

  Column(
    modifier = modifier.padding(start = 0.dp, end = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center // 保持纵向绝对居中，留白非常匀称
  ) {
    // 自适应封面
    TvFocusable(
      onClick = onOpenPlayer,
      modifier = Modifier.size(coverDynamicSize),
      shape = RoundedCornerShape(12.dp),
    ) {
      Artwork(track?.picURL, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(12.dp))
    }
    Spacer(Modifier.height(12.dp))
    if (track != null) {
      Text(track.name, color = AppColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
      Text(track.singer, color = AppColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
      Spacer(Modifier.height(8.dp))
      val active = LyricParser.activeIndex(state.positionMs / 1000.0, lyrics)
      val line = lyrics.getOrNull(active)?.text ?: if (state.resolving) "解析中…" else ""
      Text(line, color = AppColors.LyricIdle, fontSize = 13.sp, maxLines = 1)
    } else {
      Text("暂无播放，去推荐里点歌开始吧", color = AppColors.TextSecondary, fontSize = 14.sp)
    }

    if (track != null) {
      Spacer(Modifier.height(10.dp))
      val audioLevel by controller.audioLevel.collectAsState()

      com.baogemusic.tv.ui.components.Waveform(
        isPlaying = state.isPlaying,
        level = audioLevel,
        modifier = Modifier.width(coverDynamicSize).height(28.dp),
      )
      Spacer(Modifier.height(6.dp))

      MiniProgressBar(
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        modifier = Modifier.width(coverDynamicSize),
      )
    }
    Spacer(Modifier.height(14.dp))
    // 底部控制按钮
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CircleControl(Icons.Filled.SkipPrevious) { controller.prev() }
      Spacer(Modifier.width(20.dp))
      CircleControl(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow) { controller.togglePlay() }
      Spacer(Modifier.width(20.dp))
      CircleControl(Icons.Filled.SkipNext) { controller.next() }
    }
  }
}

/** 只读进度条 */
@Composable
private fun MiniProgressBar(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
  val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
  Column(modifier = modifier) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(2.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(AppColors.Card),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(fraction)
          .fillMaxHeight()
          .background(AppColors.AccentGreen),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(fmtMillis(positionMs), color = AppColors.TextMuted, fontSize = 10.sp)
      Text(fmtMillis(durationMs), color = AppColors.TextMuted, fontSize = 10.sp)
    }
  }
}

private fun fmtMillis(ms: Long): String {
  val s = (ms / 1000).toInt().coerceAtLeast(0)
  return "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun CircleControl(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
  TvPill(
    onClick = onClick,
    shape = androidx.compose.foundation.shape.CircleShape,
    contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
  }
}

// ============== 右侧推荐网格（保持完全一致） =====================================================

private data class DetailView(val title: String, val subtitle: String?, val tracks: List<com.baogemusic.tv.data.model.Track>)

@Composable
private fun RecommendGrid(
  onNavigate: (NavSection) -> Unit,
  onOpenPlayer: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val settings by appContainer.settingsStore.settings.collectAsState()
  val home by appContainer.homeStore.state.collectAsState()
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  var detail by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<DetailView?>(null) }
  var loadingDetail by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

  androidx.compose.runtime.LaunchedEffect(settings.homeSources) {
    appContainer.homeStore.loadIfNeeded(settings.homeSources)
  }

  val activeLabel = home.sources.joinToString(" · ") { it.displayName }

  fun openSonglist(info: com.baogemusic.tv.data.model.SonglistInfo) {
    if (loadingDetail) return
    scope.launch {
      loadingDetail = true
      val svc = appContainer.songlists.serviceFor(info.source)
      val d = runCatching { svc?.fetchDetail(info) }.getOrNull()
      detail = DetailView(
        title = info.name,
        subtitle = info.author.takeIf { it.isNotBlank() } ?: info.source.displayName,
        tracks = d?.tracks ?: emptyList(),
      )
      loadingDetail = false
    }
  }

  fun openBoard(board: com.baogemusic.tv.data.model.BoardInfo) {
    if (loadingDetail) return
    scope.launch {
      loadingDetail = true
      val svc = appContainer.boards.serviceFor(board.source)
      val tracks = runCatching { svc?.fetchTracks(board.bangid, 1) ?: emptyList() }.getOrDefault(emptyList())
      detail = DetailView(
        title = board.name,
        subtitle = board.source.displayName,
        tracks = tracks,
      )
      loadingDetail = false
    }
  }

  Box(modifier = modifier) {
    androidx.compose.foundation.lazy.LazyColumn(
      modifier = Modifier.fillMaxSize().focusGroup(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      when {
        settings.homeSources.isEmpty() -> item { NoSourcesHint(onNavigate) }
        home.isLoading && home.heroes.isEmpty() -> item { LoadingPlaceholder() }
        else -> {
          if (home.recommendations.isNotEmpty()) {
            item {
              SectionHeader("推荐歌单", activeLabel, trailing = "查看全部") {
                onNavigate(NavSection.Songlist)
              }
            }
            item {
              // 两行网格展示（原来是一行横向列表 + 顶部轮播，卡片太大且占位过多）
              HomeCardGrid(
                home.recommendations.map { info ->
                  HomeCard(
                    key = "${info.source.key}_${info.id}",
                    picURL = info.picURL,
                    title = info.name,
                    subtitle = info.playCount?.let { "▶ $it" } ?: info.source.displayName,
                    fallbackTint = info.source.tintColor(),
                    onClick = { openSonglist(info) },
                  )
                },
              )
            }
          }
          if (home.boards.isNotEmpty()) {
            item {
              SectionHeader("排行榜", activeLabel, trailing = "查看全部") {
                onNavigate(NavSection.Leaderboard)
              }
            }
            item {
              // 与「推荐歌单」共用同一套两行网格，卡片尺寸完全一致（不再因标题长短而宽窄不一）
              HomeCardGrid(
                home.boards.map { board ->
                  HomeCard(
                    key = "${board.source.key}_${board.id}",
                    picURL = board.picURL,
                    title = board.name,
                    subtitle = board.source.displayName,
                    fallbackTint = board.source.tintColor(),
                    onClick = { openBoard(board) },
                  )
                },
              )
            }
          }
          item { StatsRow(onNavigate) }
        }
      }
    }

    detail?.let { d ->
      com.baogemusic.tv.ui.components.TracksDetailOverlay(
        title = d.title,
        subtitle = d.subtitle,
        tracks = d.tracks,
        onBack = { detail = null },
        onOpenPlayer = onOpenPlayer,
      )
    }
  }
}

@Composable
private fun StatsRow(onNavigate: (NavSection) -> Unit) {
  val played by appContainer.libraryStore.history.collectAsState()
  val loved by appContainer.libraryStore.love.collectAsState()
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxWidth().height(72.dp),
  ) {
    StatTile(
      label = "已播",
      count = played.tracks.size,
      picURL = StatThumbs.PLAYED,
      modifier = Modifier.weight(1f),
    ) { onNavigate(NavSection.Library) }
    StatTile(
      label = "收藏",
      count = loved.tracks.size,
      picURL = StatThumbs.LOVE,
      modifier = Modifier.weight(1f),
    ) { onNavigate(NavSection.Library) }
  }
}

private object StatThumbs {
  const val PLAYED = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=200&q=60"
  const val LOVE   = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=200&q=60"
}

@Composable
private fun StatTile(label: String, count: Int, picURL: String, modifier: Modifier, onClick: () -> Unit) {
  TvFocusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(36.dp)) {
    Row(
      modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(50))) {
        AsyncImage(
          model = picURL,
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
      }
      Spacer(Modifier.width(12.dp))
      Text(
        label,
        color = AppColors.TextSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f),
      )
      Text(
        "$count",
        color = AppColors.TextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun SectionHeader(
  title: String,
  subtitle: String?,
  trailing: String?,
  onTrailingClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
    verticalAlignment = Alignment.Bottom,
  ) {
    Text(title, color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    if (!subtitle.isNullOrBlank()) {
      Spacer(Modifier.width(10.dp))
      Text(
        subtitle,
        color = AppColors.TextMuted,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 4.dp),
      )
    }
    Spacer(Modifier.weight(1f))
    if (!trailing.isNullOrBlank()) {
      TvPill(
        onClick = onTrailingClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
      ) {
        Text(trailing, fontSize = 12.sp)
      }
    }
  }
}

/** 首页卡片的统一数据模型：「推荐歌单」与「排行榜」共用，保证两处卡片尺寸完全一致。 */
private data class HomeCard(
  val key: String,
  val picURL: String?,
  val title: String,
  val subtitle: String?,
  val fallbackTint: Color,
  val onClick: () -> Unit,
)

/**
 * 两行等宽网格：每一张卡片都 `weight(1f)`，宽高严格一致（不再因标题长短而宽窄不一）。
 * 「推荐歌单」与「排行榜」都走这里，所以两处的卡片大小完全统一。
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun HomeCardGrid(items: List<HomeCard>) {
  val unique = androidx.compose.runtime.remember(items) { items.distinctBy { it.key } }
  if (unique.isEmpty()) return
  val cols = ((unique.size + 1) / 2).coerceIn(3, 8)
  val rows = unique.chunked(cols).take(2)
  Column(
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.focusRestorer(),
  ) {
    rows.forEach { rowItems ->
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        rowItems.forEach { item ->
          AlbumCard(
            picURL = item.picURL,
            title = item.title,
            subtitle = item.subtitle,
            fallbackTint = item.fallbackTint,
            onClick = item.onClick,
            modifier = Modifier.weight(1f),
          )
        }
        // 末行不足时用等宽占位补齐，避免剩余卡片被拉伸变形
        repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}

@Composable
private fun AlbumCard(
  picURL: String?,
  title: String,
  subtitle: String?,
  fallbackTint: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    TvFocusable(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().aspectRatio(1f),
      shape = RoundedCornerShape(12.dp),
    ) {
      Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
        if (!picURL.isNullOrBlank()) {
          AsyncImage(
            model = picURL,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
          )
        } else {
          Box(
            modifier = Modifier.fillMaxSize().background(
              Brush.linearGradient(listOf(fallbackTint, fallbackTint.copy(alpha = 0.5f))),
            ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.Filled.PlayArrow,
              contentDescription = null,
              tint = Color.White.copy(alpha = 0.7f),
              modifier = Modifier.size(40.dp),
            )
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      title,
      color = AppColors.TextPrimary,
      fontSize = 16.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    if (!subtitle.isNullOrBlank()) {
      Text(
        subtitle,
        color = AppColors.TextMuted,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun LoadingPlaceholder() {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth().height(260.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(AppColors.Card),
    )
    repeat(2) {
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(4) {
          Box(
            modifier = Modifier
              .size(170.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(AppColors.Card),
          )
        }
      }
    }
  }
}

@Composable
private fun NoSourcesHint(onNavigate: (NavSection) -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("没有勾选任何音源", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("去设置里勾选一个音源后再来发现", color = AppColors.TextMuted, fontSize = 13.sp)
    Spacer(Modifier.height(16.dp))
    TvPill(onClick = { onNavigate(NavSection.Settings) }, selected = true) {
      Text("打开设置", fontSize = 13.sp)
    }
  }
}

private fun com.baogemusic.tv.data.model.SourceID.tintColor(): Color = when (this) {
  com.baogemusic.tv.data.model.SourceID.KW -> AppColors.SourceKw
  com.baogemusic.tv.data.model.SourceID.KG -> AppColors.SourceKg
  com.baogemusic.tv.data.model.SourceID.TX -> AppColors.SourceTx
  com.baogemusic.tv.data.model.SourceID.WY -> AppColors.SourceWy
  com.baogemusic.tv.data.model.SourceID.MG -> AppColors.SourceMg
  com.baogemusic.tv.data.model.SourceID.LOCAL -> AppColors.SourceLocal
}
