package com.baogemusic.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.baogemusic.tv.data.model.Quality
import com.baogemusic.tv.data.model.SourceID
import com.baogemusic.tv.data.model.UserScript
import com.baogemusic.tv.data.store.AppFontFamily
import com.baogemusic.tv.data.store.CacheManager
import com.baogemusic.tv.data.store.CacheQuota
import com.baogemusic.tv.data.store.DeviceInfo
import com.baogemusic.tv.data.store.DiscShape
import com.baogemusic.tv.data.store.CoverScale
import com.baogemusic.tv.data.store.FontImportRules
import com.baogemusic.tv.data.store.FullscreenLyricFx
import com.baogemusic.tv.data.store.GridColumns
import com.baogemusic.tv.data.store.LyricAlignment
import com.baogemusic.tv.data.store.LyricFont
import com.baogemusic.tv.data.store.LyricScaleRange
import com.baogemusic.tv.data.store.LyricSource
import com.baogemusic.tv.data.store.PlayerFx
import com.baogemusic.tv.data.store.PlayerLayout
import com.baogemusic.tv.data.store.Settings
import com.baogemusic.tv.data.store.UserFont
import com.baogemusic.tv.data.store.VinylColor
import com.baogemusic.tv.playback.update.UpdateState
import com.baogemusic.tv.ui.appContainer
import com.baogemusic.tv.ui.components.TvFocusable
import com.baogemusic.tv.ui.components.Stepper
import com.baogemusic.tv.ui.components.TvPill
import com.baogemusic.tv.ui.components.TvSwitchRow
import com.baogemusic.tv.ui.components.TvTextFieldHost
import com.baogemusic.tv.ui.theme.AppColors
import com.baogemusic.tv.ui.theme.pingFangFontFamily
import com.baogemusic.tv.ui.theme.xiaodouFontFamily
import com.baogemusic.tv.ui.theme.rememberAppFontFamily
import com.baogemusic.tv.ui.theme.rememberUserFontFamily

// ============== 播放（含原「通用」页的发现页音源）===========================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaybackPage(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    SettingsPageScaffold(modifier) {
        Section("播放音质") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Quality.orderedHighToLow.reversed().forEachIndexed { index, q ->
                    TvPill(
                        onClick = { onUpdate { it.copy(preferredQuality = q) } },
                        selected = settings.preferredQuality == q,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = qualityIcon(q),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(q.displayName, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Section("内置直连兜底") {
            TvSwitchRow("脚本失败时尝试内置直连（kw/wy）", settings.fallbackEnabled) {
                onUpdate { it.copy(fallbackEnabled = !it.fallbackEnabled) }
            }
        }

        Section("音频解码") {
            TvSwitchRow("Hi-Res 硬件直通（播放无损闪退时请关闭）", settings.audioOffloadEnabled) {
                onUpdate { it.copy(audioOffloadEnabled = !it.audioOffloadEnabled) }
            }
            TvSwitchRow("强制软件解码（播放闪退或没声音时请开启，下一首起生效）", settings.forceSoftwareDecoding) {
                onUpdate { it.copy(forceSoftwareDecoding = !it.forceSoftwareDecoding) }
            }
        }

        Section("播放界面") {
            TvSwitchRow("播放控件自动隐藏", settings.autoHidePlayerControls) {
                onUpdate { it.copy(autoHidePlayerControls = !it.autoHidePlayerControls) }
            }
            Spacer(Modifier.height(8.dp))
            HintText("开启后，播放页 5 秒无操作自动隐藏进度条和播放按钮，按方向键或确认键重新显示。")
        }

        Section("发现页音源") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceID.homePageOrder.forEach { src ->
                    val active = src in settings.homeSources
                    TvPill(
                        onClick = {
                            onUpdate { s ->
                                val next = if (active) s.homeSources - src else s.homeSources + src
                                // 不允许清空：至少留一个源，发现页才有内容。
                                s.copy(homeSources = if (next.isEmpty()) s.homeSources else next)
                            }
                        },
                        selected = active,
                    ) { Text(src.displayName, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            HintText("勾选哪些平台参与推荐页（首页）的内容聚合，变化实时生效。")
        }
    }
}

// ============== 歌词 ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LyricPage(
    settings: Settings,
    fonts: List<UserFont>,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onPickFont: () -> Unit,
    onQrFont: () -> Unit,
    onDeleteFont: (UserFont) -> Unit,
    status: String?,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val currentFamily = rememberAppFontFamily()
    SettingsPageScaffold(modifier) {
        Section("歌词字体") {
            // 预览：用当前选中的字体渲染一行示例歌词，切换后即时可见。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(AppColors.Card, RoundedCornerShape(10.dp))
                    .padding(14.dp),
            ) {
                Text(
                    "夜空中最亮的星",
                    color = AppColors.TextPrimary,
                    fontFamily = currentFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "歌词预览 ABCabc 123 · 字号 ${settings.lyricFontSizeSp}sp",
                    color = AppColors.TextSecondary,
                    fontFamily = currentFamily,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 内置：系统默认 + 苹方 + 小抖动侠情书
                TvPill(
                    onClick = { onUpdate { it.copy(appFontFamily = AppFontFamily.SYSTEM, customFontId = null) } },
                    selected = settings.appFontFamily == AppFontFamily.SYSTEM,
                    modifier = Modifier.focusRequester(firstFocus),
                ) { Text(AppFontFamily.SYSTEM.label, fontSize = 13.sp) }
                TvPill(
                    onClick = { onUpdate { it.copy(appFontFamily = AppFontFamily.PINGFANG_HK, customFontId = null) } },
                    selected = settings.appFontFamily == AppFontFamily.PINGFANG_HK,
                ) {
                    Text(AppFontFamily.PINGFANG_HK.label, fontSize = 13.sp, fontFamily = pingFangFontFamily())
                }
                TvPill(
                    onClick = { onUpdate { it.copy(appFontFamily = AppFontFamily.XIAODOU, customFontId = null) } },
                    selected = settings.appFontFamily == AppFontFamily.XIAODOU,
                ) {
                    Text(AppFontFamily.XIAODOU.label, fontSize = 13.sp, fontFamily = xiaodouFontFamily())
                }
                // 用户上传的字体：名字本身就用自己的字体渲染，等于逐项预览
                fonts.forEach { font ->
                    val family = rememberUserFontFamily(font.id)
                    TvPill(
                        onClick = {
                            onUpdate { it.copy(appFontFamily = AppFontFamily.CUSTOM, customFontId = font.id) }
                        },
                        selected = settings.appFontFamily == AppFontFamily.CUSTOM && settings.customFontId == font.id,
                    ) {
                        Text(
                            font.name,
                            fontFamily = family,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (fonts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    fonts.forEach { font ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${font.name}  %.1fMB".format(font.sizeBytes / 1024f / 1024f),
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TvPill(
                                onClick = { onDeleteFont(font) },
                                accent = AppColors.Danger,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            ) { Text("删除", fontSize = 12.sp) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TvPill(onClick = onPickFont, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("上传字体", fontSize = 13.sp)
                    }
                }
                QrPill("扫码上传", onQrFont)
            }
            Spacer(Modifier.height(8.dp))
            HintText(
                "只支持 ${FontImportRules.allowedExtensions.joinToString(" / ")}，单个不超过 ${FontImportRules.MAX_BYTES_LABEL}。" +
                    "字体仅作用于歌词文字，不影响其他界面；上传后自动应用。",
            )
            status?.let { Text(it, color = AppColors.TextSecondary, fontSize = 12.sp) }
        }

        Section("全屏歌词特效") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FullscreenLyricFx.entries.forEach { fx ->
                    TvPill(
                        onClick = { onUpdate { it.copy(fullscreenLyricFx = fx) } },
                        selected = settings.fullscreenLyricFx == fx,
                    ) { Text(fx.label, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            HintText("全屏歌词特效（参考 YuMusic / QQ 音乐）：商籁为雅致的居中三行歌词 + 细线分隔；星海为深蓝星海背景 + 发光歌词。选择后播放页会以全屏歌词替代「封面 + 歌词」舞台。")
        }

        Section("歌词翻译") {
            TvSwitchRow("显示歌词翻译（外语歌有翻译时在每句下方显示）", settings.showLyricTranslation) {
                onUpdate { it.copy(showLyricTranslation = !it.showLyricTranslation) }
            }
        }

        Section("字号 / 行距") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Stepper(
                    label = "字号",
                    valueText = "${settings.lyricFontSizeSp}",
                    onMinus = {
                        onUpdate {
                            it.copy(lyricFontSizeSp = LyricFont.step(it.lyricFontSizeSp, -LyricFont.STEP))
                        }
                    },
                    onPlus = {
                        onUpdate {
                            it.copy(lyricFontSizeSp = LyricFont.step(it.lyricFontSizeSp, LyricFont.STEP))
                        }
                    },
                )
                Stepper(
                    label = "行距",
                    valueText = "%.1f×".format(settings.lyricLineScale),
                    onMinus = {
                        onUpdate {
                            it.copy(lyricLineScale = LyricScaleRange.step(it.lyricLineScale, -LyricScaleRange.STEP))
                        }
                    },
                    onPlus = {
                        onUpdate {
                            it.copy(lyricLineScale = LyricScaleRange.step(it.lyricLineScale, LyricScaleRange.STEP))
                        }
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            HintText("字号为歌词当前行的实际大小（sp），范围 ${LyricFont.MIN}–${LyricFont.MAX}；行距为倍率，范围 ${LyricScaleRange.MIN}–${LyricScaleRange.MAX}，步长 ${LyricScaleRange.STEP}。")
        }

        Section("歌词对齐") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LyricAlignment.entries.forEach { align ->
                    TvPill(
                        onClick = { onUpdate { it.copy(lyricAlignment = align) } },
                        selected = settings.lyricAlignment == align,
                    ) { Text(align.label, fontSize = 13.sp) }
                }
            }
        }

        LyricSourceSection(settings, onUpdate)
    }
}

/** 歌词数据源：手动排顺序，按顺序自动回退。 */
@Composable
private fun LyricSourceSection(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    // 老版本存档可能缺项：把没排进去的源补在后面，保证四个源都在列表里。
    val order = remember(settings.lyricSources) {
        settings.lyricSources.filter { it in LyricSource.entries } +
            LyricSource.entries.filter { it !in settings.lyricSources }
    }
    Section("歌词数据源") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            order.forEachIndexed { index, src ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${index + 1}",
                        color = if (index == 0) AppColors.AccentGreen else AppColors.TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(18.dp),
                        textAlign = TextAlign.Center,
                    )
                    TvPill(
                        onClick = {
                            // 点名字 = 设为首选（提到最前）
                            onUpdate { s -> s.copy(lyricSources = listOf(src) + order.filter { it != src }) }
                        },
                        selected = index == 0,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text(src.label, fontSize = 13.sp) }
                    if (index > 0) {
                        TvPill(
                            onClick = {
                                onUpdate { s ->
                                    val next = order.toMutableList()
                                    next.removeAt(index)
                                    next.add(index - 1, src)
                                    s.copy(lyricSources = next)
                                }
                            },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text("▲", fontSize = 12.sp) }
                    }
                    if (index < order.size - 1) {
                        TvPill(
                            onClick = {
                                onUpdate { s ->
                                    val next = order.toMutableList()
                                    next.removeAt(index)
                                    next.add(index + 1, src)
                                    s.copy(lyricSources = next)
                                }
                            },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text("▼", fontSize = 12.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TvSwitchRow("按顺序自动回退（关掉后只用第一个源）", settings.lyricSourceFallback) {
            onUpdate { it.copy(lyricSourceFallback = !it.lyricSourceFallback) }
        }
        Spacer(Modifier.height(8.dp))
        HintText(
            "抓歌词时从上往下依次尝试这些平台，前一个拿不到（或返回空）就换下一个；" +
                "曲目本身就来自某个平台时，先用它自己的 songmid 直连，最准。点平台名可设为首选，▲▼ 调整顺序。",
        )
    }
}

// ============== 外观 ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearancePage(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    SettingsPageScaffold(modifier) {
        Section("播放页布局") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerLayout.entries.forEach { layout ->
                    TvPill(
                        onClick = { onUpdate { it.copy(playerLayout = layout) } },
                        selected = settings.playerLayout == layout,
                    ) { Text(layout.label, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            HintText("切换播放页里唱片和歌词谁在左、谁在右（默认「歌词左 · 唱片右」）。")
        }

        Section("唱片") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VinylColor.entries.forEach { color ->
                    TvPill(
                        onClick = { onUpdate { it.copy(vinylColor = color) } },
                        selected = settings.vinylColor == color,
                    ) { Text(color.label, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiscShape.entries.forEach { shape ->
                    TvPill(
                        onClick = { onUpdate { it.copy(discShape = shape) } },
                        selected = settings.discShape == shape,
                    ) { Text(shape.label, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Stepper(
                label = "唱片大小",
                valueText = "%.1f×".format(settings.coverScale),
                onMinus = {
                    onUpdate { s -> s.copy(coverScale = CoverScale.step(s.coverScale, -CoverScale.STEP)) }
                },
                onPlus = {
                    onUpdate { s -> s.copy(coverScale = CoverScale.step(s.coverScale, CoverScale.STEP)) }
                },
            )
            Spacer(Modifier.height(8.dp))
            HintText("播放页封面 / 唱片整体大小的缩放系数（步进 0.1，范围 ${CoverScale.MIN}–${CoverScale.MAX}，默认 ${CoverScale.DEFAULT}）。封面与动效中心会同步缩放，实时生效。")
        }

        Section("播放页动效") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerFx.entries.forEach { fx ->
                    TvPill(
                        onClick = { onUpdate { it.copy(playerFx = fx) } },
                        selected = settings.playerFx == fx,
                    ) { Text(fx.label, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            HintText("动感波光是随音量向外扩散的柔和波纹；波纹特效是更厚、更慢的同心水波涟漪（参考 BMMusicPlayer）；宇宙尘埃特效是细碎尘埃 / 星点在背景中缓慢漂浮闪烁（参考 ParticleView）。三者均从封面中心向外延伸并跟随唱片左右位置，纯 Compose 绘制，老电视和投影仪也能跑。")
        }

        Section("歌单列数") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GridColumns.choices.forEach { n ->
                    TvPill(
                        onClick = { onUpdate { it.copy(gridColumns = n) } },
                        selected = settings.gridColumns == n,
                    ) { Text("$n 列", fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            HintText("歌单页与「我的」页每行显示的卡片数，列数越多卡片和封面越小。")
        }
    }
}

// ============== 下载 ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DownloadPage(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onPickFolder: () -> Unit,
    permHint: Boolean,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val roots = remember { appContainer.downloadStore.availableRoots() }
    val treeUriStr = settings.customDownloadTreeUri
    val usingTree = treeUriStr != null
    val usingCustomFolder = settings.customDownloadDir != null &&
        roots.none { it.dir.absolutePath == settings.customDownloadDir }
    val currentPath = settings.customDownloadDir ?: roots.firstOrNull()?.dir?.absolutePath

    SettingsPageScaffold(modifier) {
        // 缓存：当前大小 + 一键清理 + 磁盘上限（步进 0.5GB，范围 0.5–5GB）
        val context = LocalContext.current
        val cacheScope = rememberCoroutineScope()
        var cacheSizeText by remember { mutableStateOf("计算中…") }
        LaunchedEffect(Unit) {
            cacheSizeText = CacheManager.format(CacheManager.sizeBytes(context))
        }
        val doClearCache: () -> Unit = {
            cacheScope.launch {
                val freed = CacheManager.clear(context, appContainer.imageLoader)
                cacheSizeText = CacheManager.format(CacheManager.sizeBytes(context))
                android.widget.Toast.makeText(
                    context, "已清理 ${CacheManager.format(freed)}", android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }

        Section("缓存") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "当前缓存：$cacheSizeText",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                TvPill(
                    onClick = doClearCache,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("清理缓存", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(10.dp))
            Stepper(
                label = "磁盘缓存上限",
                valueText = "%.1f GB".format(settings.imageCacheGb),
                onMinus = {
                    onUpdate { s -> s.copy(imageCacheGb = CacheQuota.step(s.imageCacheGb, -CacheQuota.STEP_GB)) }
                },
                onPlus = {
                    onUpdate { s -> s.copy(imageCacheGb = CacheQuota.step(s.imageCacheGb, CacheQuota.STEP_GB)) }
                },
            )
            Spacer(Modifier.height(8.dp))
            HintText("图片磁盘缓存上限（步进 0.5GB，范围 0.5–5GB）。内存缓存统一封顶 64MB，避免 1.5GB 小内存设备卡顿或 OOM。清理只删封面等临时缓存，不碰已下载的音乐。")
        }

        Section("下载目录") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                roots.forEachIndexed { index, opt ->
                    val path = opt.dir.absolutePath
                    TvPill(
                        onClick = {
                            onUpdate {
                                it.copy(
                                    customDownloadDir = if (index == 0) null else path,
                                    customDownloadTreeUri = null,
                                )
                            }
                        },
                        selected = !usingTree && path == currentPath,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) { Text(opt.label, fontSize = 13.sp) }
                }
                TvPill(onClick = onPickFolder, selected = usingTree || usingCustomFolder) {
                    Text("选择其他文件夹…", fontSize = 13.sp)
                }
            }
            if (permHint) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "需要存储写入权限：请在系统设置里为本应用开启文件访问（或允许存储权限），返回后再点选择。",
                    color = AppColors.Warning,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    usingTree -> "已选文件夹：" + (
                        treeUriStr?.let {
                            runCatching { appContainer.downloadStore.treeDisplayName(android.net.Uri.parse(it)) }.getOrNull()
                        } ?: "自定义文件夹"
                        )
                    usingCustomFolder -> "已选文件夹：" + (settings.customDownloadDir ?: "")
                    else -> currentPath ?: "（应用默认音乐目录）"
                },
                color = AppColors.TextMuted,
                fontSize = 11.sp,
            )
            Text(
                if (usingTree || usingCustomFolder) {
                    "下载会保存到你选的文件夹（可被文件管理器/其它应用浏览）。切换目录只影响之后的下载。"
                } else {
                    "存储卷目录在应用专属空间（卸载会清除、不易在文件管理器看到）；想存到可见位置请点「选择其他文件夹」。切换只影响之后的下载，已下载的仍可正常播放。"
                },
                color = AppColors.TextMuted,
                fontSize = 11.sp,
            )
        }

        Section("下载并发数") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..6).forEach { n ->
                    TvPill(
                        onClick = { onUpdate { it.copy(maxConcurrentDownloads = n) } },
                        selected = settings.maxConcurrentDownloads == n,
                    ) { Text("$n", fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            HintText("同时下载的最大歌曲数（批量下载时排队，越大越快但越占带宽）。默认 3。")
        }

        Section("批量下载") {
            TvSwitchRow("已下载的也按新音质重下", settings.redownloadOnQualityChange) {
                onUpdate { it.copy(redownloadOnQualityChange = !it.redownloadOnQualityChange) }
            }
            Spacer(Modifier.height(8.dp))
            HintText("开启后，对歌单「下载全部」时，已下载但音质与所选不同的歌会按新音质重新下载（如 128k 升级到 FLAC）；关闭则一律跳过已下载。默认开启。")
        }

        Section("清理") {
            val clearScope = rememberCoroutineScope()
            var showClearDialog by remember { mutableStateOf(false) }
            val downloadedCount = appContainer.downloadStore.completedCount
            TvPill(
                onClick = { showClearDialog = true },
                accent = AppColors.Danger,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) { Text("清理本地下载音乐", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            HintText("删除本机已下载的全部音乐文件与记录（共 $downloadedCount 首）。此操作不可恢复，但不会动设置与其它缓存。")
            if (showClearDialog) {
                ConfirmDeleteDialog(
                    title = "清理本地下载音乐",
                    message = "确定要删除本机已下载的全部 $downloadedCount 首音乐吗？文件删除后无法恢复。",
                    onCancel = { showClearDialog = false },
                    onConfirm = {
                        showClearDialog = false
                        clearScope.launch {
                            val freed = appContainer.downloadStore.clearAllDownloads()
                            android.widget.Toast.makeText(
                                context, "已清理 ${CacheManager.format(freed)}", android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }
    }
}

// ============== 连接与上传 ======================================================================

@Composable
internal fun ConnectPage(
    scripts: List<UserScript>,
    status: String?,
    onImportInput: (String) -> Unit,
    onPickScriptFile: () -> Unit,
    onQrScript: () -> Unit,
    onToggleScript: (UserScript) -> Unit,
    onDeleteScript: (UserScript) -> Unit,
    onPickFont: () -> Unit,
    onQrFont: () -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var url by remember { mutableStateOf("") }

    SettingsPageScaffold(modifier) {
        Section("自定义音源") {
            HintText("粘贴 URL 自动远程拉取；直接粘贴脚本则按 JS 代码导入；或上传 .js 文件；扫码可在手机/电脑操作。")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvTextFieldHost(modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp)) { fieldModifier ->
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().then(fieldModifier),
                        placeholder = { Text("URL（http://...）或直接粘贴 JS 代码", color = AppColors.TextMuted) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onImportInput(url) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            focusedBorderColor = AppColors.AccentGreen,
                            unfocusedBorderColor = AppColors.Card,
                            cursorColor = AppColors.AccentGreen,
                        ),
                    )
                }
                Spacer(Modifier.width(8.dp))
                TvPill(
                    onClick = { onImportInput(url) },
                    selected = true,
                    modifier = Modifier.focusRequester(firstFocus),
                ) { Text("导入", fontSize = 14.sp) }
                Spacer(Modifier.width(8.dp))
                TvPill(
                    onClick = onPickScriptFile,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("上传文件", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                QrPill("扫码", onQrScript)
            }
            status?.let { Text(it, color = AppColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }

            Spacer(Modifier.padding(top = 6.dp))
            if (scripts.isEmpty()) {
                Text("尚未导入任何脚本", color = AppColors.TextMuted, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scripts.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TvFocusable(onClick = { onToggleScript(s) }, modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(s.name, color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                        Text("v${s.version}  ${s.author}", color = AppColors.TextMuted, fontSize = 12.sp)
                                    }
                                    Text(
                                        if (s.enabled) "已启用" else "已停用",
                                        color = if (s.enabled) AppColors.AccentGreen else AppColors.TextMuted,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            TvPill(
                                onClick = { onDeleteScript(s) },
                                accent = AppColors.Danger,
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                            ) { Text("删除", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

// ============== 关于与更新 ======================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AboutPage(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val deviceRows = remember { DeviceInfo.deviceRows(context) }
    val hwFormats = remember { DeviceInfo.hardwareFormats(audioOnly = true) }
    SettingsPageScaffold(modifier) {
        Section("调试") {
            TvSwitchRow("显示调试信息", settings.showDebugInfo) {
                onUpdate { it.copy(showDebugInfo = !it.showDebugInfo) }
            }
            Spacer(Modifier.height(8.dp))
            HintText("排查音源等问题时开启，平时不需要。开启后播放页会显示获取播放地址、播放失败自动跳过、换音源等状态。")
        }

        Section("本机设备") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in deviceRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            row.label,
                            color = AppColors.TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.width(92.dp),
                        )
                        Text(
                            row.value,
                            color = AppColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Section("硬件音频解码") {
            if (hwFormats.isEmpty()) {
                Text("未检测到可硬解的常用音频格式", color = AppColors.TextMuted, fontSize = 13.sp)
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (fmt in hwFormats) {
                        // 仅展示，不可点击 / 不可聚焦（本机信息，不需要操作）。
                        Box(
                            modifier = Modifier
                                .background(AppColors.Card, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(fmt, color = AppColors.TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HintText("自动探测本机 MediaCodec 硬件解码器得到的音频格式（AAC / MP3 / FLAC / Opus …）。仅供排查设备兼容性，无需操作。")
        }

        UpdateSection(firstFocus)
    }
}

/**
 * 检查更新: auto-checks GitHub once on open, then drives the whole download → install flow
 * inline. State lives in [com.baogemusic.tv.playback.update.UpdateManager] so leaving and
 * re-entering Settings resumes wherever it was (e.g. keeps a "有新版本" result).
 */
@Composable
private fun UpdateSection(firstFocus: FocusRequester) {
    val ctx = LocalContext.current
    val mgr = appContainer.updateManager
    val state by mgr.state.collectAsState()
    var permHint by remember { mutableStateOf(false) }

    // Check + download run on the process-lived appScope (not a composable scope) so leaving
    // Settings mid-flight doesn't cancel them.
    LaunchedEffect(Unit) {
        if (mgr.state.value is UpdateState.Idle) appContainer.appScope.launch { mgr.check() }
    }

    Section("检查更新") {
        Text("当前版本 v${mgr.currentVersion}", color = AppColors.TextSecondary, fontSize = 13.sp)

        when (val s = state) {
            is UpdateState.Checking -> Text("正在检查更新…", color = AppColors.TextMuted, fontSize = 13.sp)
            is UpdateState.UpToDate -> Text("已是最新版本", color = AppColors.AccentGreen, fontSize = 13.sp)
            is UpdateState.Failed -> Text("检查失败：${s.message}", color = AppColors.Danger, fontSize = 12.sp)
            is UpdateState.Available -> {
                Text("发现新版本 v${s.release.versionName}", color = AppColors.AccentGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (s.release.notes.isNotBlank()) {
                    Text(
                        s.release.notes,
                        color = AppColors.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            is UpdateState.Downloaded -> {
                Text("下载完成", color = AppColors.AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (permHint) {
                    Text(
                        "请在系统弹出的设置里允许本应用安装未知应用，返回后再点「立即安装」。",
                        color = AppColors.Warning,
                        fontSize = 12.sp,
                    )
                }
            }
            else -> {}
        }

        // ONE stable button: label + action follow the state, node never leaves the tree, so
        // remote focus stays put across 检查→检查中→有更新 transitions.
        val actionLabel = when (val s = state) {
            is UpdateState.Checking -> "检查中…"
            is UpdateState.UpToDate -> "重新检查"
            is UpdateState.Failed -> "重试"
            is UpdateState.Available -> "下载并安装"
            is UpdateState.Downloading -> "正在下载 ${(s.progress * 100).toInt()}%…"
            is UpdateState.Downloaded -> "立即安装"
            is UpdateState.Idle -> "检查更新"
        }
        TvPill(
            onClick = {
                when (val s = state) {
                    is UpdateState.Idle, is UpdateState.UpToDate, is UpdateState.Failed ->
                        appContainer.appScope.launch { mgr.check() }
                    is UpdateState.Available -> appContainer.appScope.launch { mgr.download(s.release) }
                    is UpdateState.Downloaded -> permHint = !mgr.install(ctx, s.file)
                    else -> {}
                }
            },
            selected = true,
            focusRequester = firstFocus,
        ) { Text(actionLabel, fontSize = 14.sp) }
    }
}

/** Per-tier Material icon — small visual cue alongside the displayName in the picker. */
private fun qualityIcon(q: Quality): androidx.compose.ui.graphics.vector.ImageVector = when (q) {
    Quality.K128 -> Icons.Filled.MusicNote
    Quality.K320 -> Icons.Filled.GraphicEq
    Quality.FLAC -> Icons.Filled.HighQuality
    Quality.FLAC24, Quality.HIRES -> Icons.Filled.AutoAwesome
    Quality.ATMOS, Quality.ATMOS_PLUS -> Icons.Filled.SurroundSound
    Quality.MASTER -> Icons.Filled.Diamond
}
