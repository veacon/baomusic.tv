package com.baogemusic.tv.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.baogemusic.tv.data.model.UserScript
import com.baogemusic.tv.data.store.AppFontFamily
import com.baogemusic.tv.data.store.Settings
import com.baogemusic.tv.data.store.UserFont
import com.baogemusic.tv.di.getLanIp
import com.baogemusic.tv.ui.appContainer
import com.baogemusic.tv.ui.components.FolderBrowser
import com.baogemusic.tv.ui.components.QrDialog
import com.baogemusic.tv.ui.components.TvFocusable
import com.baogemusic.tv.ui.components.TvPill
import com.baogemusic.tv.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.io.File

/** 设置一级分类。每个分类一行入口，点进去才是具体配置（[SettingsScreen] 负责分发）。 */
internal enum class SettingsPage(
    val label: String,
    val summary: (Settings, Int, Int) -> String,
) {
    PLAYBACK("播放", { s, _, _ -> "音质 ${s.preferredQuality.displayName} · 解码 · 发现页音源 ${s.homeSources.size} 个" }),
    LYRIC("歌词", { s, _, _ -> "字号 ${s.lyricFontSizeSp}sp · 行距 %.1f× · ${s.lyricAlignment.label} · 字体 ${s.appFontFamily.label} · 数据源 ${s.lyricSources.firstOrNull()?.label ?: "默认"}".format(s.lyricLineScale) }),
    APPEARANCE("外观", { s, _, _ -> "${s.discShape.label} · ${s.playerFx.label} · ${s.gridColumns} 列" }),
    DOWNLOAD("下载", { s, _, _ -> "并发 ${s.maxConcurrentDownloads} · 缓存 ${"%.1f".format(s.imageCacheGb)}GB · ${if (s.customDownloadDir == null && s.customDownloadTreeUri == null) "默认目录" else "自定义目录"}" }),
    CONNECT("连接与上传", { _, scripts, _ -> "音源脚本 $scripts 个 · 扫码导入" }),
    ABOUT("关于与更新", { _, _, _ -> "版本与检查更新 · 设备信息 · 调试" }),
}

/**
 * 设置二级菜单。一级页面只放分类入口（[SettingsPage]），具体配置下沉到各自的二级页；
 * 返回键（[androidx.activity.compose.BackHandler]）从二级页退回一级页，再按才交给外层导航。
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by appContainer.settingsStore.settings.collectAsState()
    val scripts by appContainer.scriptStore.scripts.collectAsState()
    val fonts by appContainer.fontStore.fonts.collectAsState()

    var page by remember { mutableStateOf<SettingsPage?>(null) }
    val pageFocus = remember { FocusRequester() }
    // 每个分类行一个 FocusRequester：从二级页返回时把焦点还给用户点进去的那一行，
    // 而不是回到列表顶部。
    val rowFocus = remember { SettingsPage.entries.associateWith { FocusRequester() } }
    var lastPage by remember { mutableStateOf<SettingsPage?>(null) }

    // 二级页返回一级页。RootScreen 的返回（回到推荐页）在外层，内层优先拿到。
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }

    LaunchedEffect(page) {
        val current = page
        if (current == null) {
            // 返回一级页：定位到上一次进入的那一分类（首次进入则落在第一行）
            val target = lastPage ?: SettingsPage.entries.first()
            runCatching { rowFocus[target]?.requestFocus() }
        } else {
            lastPage = current
            runCatching { pageFocus.requestFocus() }
        }
    }

    // ---- 通用状态 ----
    var status by remember { mutableStateOf<String?>(null) }
    var qrTarget by remember { mutableStateOf<QrTarget?>(null) }
    var pendingDeleteScript by remember { mutableStateOf<UserScript?>(null) }
    var pendingDeleteFont by remember { mutableStateOf<UserFont?>(null) }

    val update: ((Settings) -> Settings) -> Unit = { transform ->
        scope.launch { appContainer.settingsStore.update(transform) }
    }

    // ---- 音源脚本：系统文件选择器 ----
    val scriptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "正在读取文件…"
            val raw = readTextFromUri(context, uri)
            if (raw.isNullOrEmpty()) {
                status = "读取文件失败"
            } else {
                status = "正在导入文件…"
                status = appContainer.scriptStore.import(raw).fold({ it.message }, { "导入失败：${it.message}" })
            }
        }
    }

    // ---- 字体：系统文件选择器（限制 ttf / otf / woff2） ----
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "正在读取字体…"
            val result = appContainer.fontStore.importFromUri(uri)
            status = applyImportedFont(result)
        }
    }

    fun openFontPicker() {
        runCatching {
            fontPicker.launch(
                arrayOf("font/ttf", "font/otf", "font/woff2", "application/font-sfnt", "application/octet-stream", "*/*"),
            )
        }.onFailure { status = "未找到可用的文件选择器" }
    }

    // ---- 下载目录：应用内文件夹浏览器（电视常常没有 SAF） ----
    var showDownloadBrowser by remember { mutableStateOf(false) }
    var dlPermHint by remember { mutableStateOf(false) }
    val dlPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showDownloadBrowser = true else dlPermHint = true }

    fun openDownloadPicker() {
        if (hasStorageWriteAccess(context)) {
            showDownloadBrowser = true
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            dlPermHint = true
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        } else {
            dlPermLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // ---- 手机 / 电脑扫码推送过来的内容 ----
    LaunchedEffect(Unit) {
        appContainer.events.qrScriptUrl.collect { u ->
            qrTarget = null
            importFromUrl(scope, u) { status = it }
        }
    }
    LaunchedEffect(Unit) {
        appContainer.events.qrScriptText.collect { raw ->
            qrTarget = null
            status = "正在导入上传的脚本…"
            status = appContainer.scriptStore.import(raw).fold({ it.message }, { "导入失败：${it.message}" })
        }
    }
    LaunchedEffect(Unit) {
        appContainer.events.qrFontFile.collect { path ->
            qrTarget = null
            status = "正在导入字体…"
            val file = File(path)
            status = applyImportedFont(appContainer.fontStore.importFromFile(file, file.name))
            runCatching { file.delete() }
        }
    }

    Crossfade(targetState = page, label = "settingsPage") { current ->
        when (current) {
            null -> SettingsHome(
                settings = settings,
                scriptCount = scripts.size,
                fontCount = fonts.size,
                onOpen = { page = it },
                focusOf = { rowFocus[it] },
                modifier = modifier,
            )
            SettingsPage.PLAYBACK -> PlaybackPage(settings, update, pageFocus, modifier)
            SettingsPage.LYRIC -> LyricPage(
                settings = settings,
                fonts = fonts,
                onUpdate = update,
                onPickFont = ::openFontPicker,
                onQrFont = { qrTarget = QrTarget.FONT },
                onDeleteFont = { pendingDeleteFont = it },
                status = status,
                firstFocus = pageFocus,
                modifier = modifier,
            )
            SettingsPage.APPEARANCE -> AppearancePage(
                settings = settings,
                onUpdate = update,
                firstFocus = pageFocus,
                modifier = modifier,
            )
            SettingsPage.DOWNLOAD -> DownloadPage(
                settings = settings,
                onUpdate = update,
                onPickFolder = ::openDownloadPicker,
                permHint = dlPermHint,
                firstFocus = pageFocus,
                modifier = modifier,
            )
            SettingsPage.CONNECT -> ConnectPage(
                scripts = scripts,
                status = status,
                onImportInput = { importInput(scope, it) { status = it } },
                onPickScriptFile = {
                    runCatching { scriptPicker.launch(arrayOf("application/javascript", "text/javascript", "text/plain", "*/*")) }
                        .onFailure { status = "未找到可用的文件选择器" }
                },
                onQrScript = { qrTarget = QrTarget.SCRIPT },
                onToggleScript = { scope.launch { appContainer.scriptStore.setEnabled(it.id, !it.enabled) } },
                onDeleteScript = { pendingDeleteScript = it },
                firstFocus = pageFocus,
                modifier = modifier,
            )
            SettingsPage.ABOUT -> AboutPage(
                settings = settings,
                onUpdate = update,
                onShowDebug = { qrTarget = QrTarget.DEBUG },
                firstFocus = pageFocus,
                modifier = modifier,
            )
        }
    }

    // ---- 覆盖层：确认框 / 扫码 / 文件夹 ----
    pendingDeleteScript?.let { target ->
        ConfirmDeleteDialog(
            title = "删除自定义音源",
            message = "确定要删除「${target.name}」吗？删除后需要重新导入才能使用。",
            onCancel = { pendingDeleteScript = null },
            onConfirm = {
                pendingDeleteScript = null
                appContainer.appScope.launch {
                    appContainer.scriptStore.remove(target.id)
                    status = "已删除：${target.name}"
                }
            },
        )
    }
    pendingDeleteFont?.let { target ->
        ConfirmDeleteDialog(
            title = "删除字体",
            message = "确定要删除字体「${target.name}」吗？",
            onCancel = { pendingDeleteFont = null },
            onConfirm = {
                pendingDeleteFont = null
                scope.launch {
                    appContainer.fontStore.remove(target.id)
                    // 正在用这个字体就退回系统默认，免得留下一个空引用。
                    if (settings.customFontId == target.id) {
                        appContainer.settingsStore.update { it.copy(appFontFamily = AppFontFamily.SYSTEM, customFontId = null) }
                    }
                    status = "已删除字体：${target.name}"
                }
            },
        )
    }

    qrTarget?.let { target ->
        val ip = remember { getLanIp() }
        val port = appContainer.localServer?.boundPort
        if (ip != null && port != null) {
            val path = when (target) {
                QrTarget.FONT -> "font"
                QrTarget.SCRIPT -> "script"
                QrTarget.DEBUG -> "debug"
            }
            val title = when (target) {
                QrTarget.FONT -> "扫码上传字体"
                QrTarget.SCRIPT -> "扫码导入音源"
                QrTarget.DEBUG -> "扫码下载调试信息"
            }
            val subtitle = when (target) {
                QrTarget.FONT -> "手机扫码、电脑打开地址都可以，上传 ttf / otf / woff2 字体（不超过 ${com.baogemusic.tv.data.store.FontImportRules.MAX_BYTES_LABEL}）"
                QrTarget.SCRIPT -> "手机扫码、电脑打开地址都可以，可粘贴脚本 URL 或上传 .js 文件"
                QrTarget.DEBUG -> "手机扫码或电脑打开地址即可下载调试报告（设备信息 / 设置 / 崩溃日志 / logcat）。"
            }
            QrDialog(
                url = "http://$ip:$port/$path",
                title = title,
                subtitle = subtitle,
                onDismiss = { qrTarget = null },
            )
        } else {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { qrTarget = null },
                title = { Text("无法启动扫码") },
                text = { Text("请确认电视已连接 Wi-Fi 且本地服务已启动后再试。") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { qrTarget = null }) {
                        Text("好", color = AppColors.AccentGreen)
                    }
                },
                containerColor = AppColors.BgPanel,
                titleContentColor = AppColors.TextPrimary,
                textContentColor = AppColors.TextSecondary,
            )
        }
    }

    if (showDownloadBrowser) {
        FolderBrowser(
            onPick = { dir ->
                dlPermHint = false
                showDownloadBrowser = false
                scope.launch {
                    appContainer.settingsStore.update {
                        it.copy(customDownloadDir = dir.absolutePath, customDownloadTreeUri = null)
                    }
                }
            },
            onCancel = { showDownloadBrowser = false },
        )
    }
}

/** 扫码 / 文件选择器传上来的字体：入库成功就顺手应用成全局字体。 */
private suspend fun applyImportedFont(result: Result<UserFont>): String = result.fold(
    { font ->
        appContainer.settingsStore.update {
            it.copy(appFontFamily = AppFontFamily.CUSTOM, customFontId = font.id)
        }
        "已导入并应用：${font.name}（%.1fMB）".format(font.sizeBytes / 1024f / 1024f)
    },
    { "字体导入失败：${it.message ?: "未知错误"}" },
)

private enum class QrTarget { SCRIPT, FONT, DEBUG }

// ============== 一级页：分类入口 =================================================================

@Composable
private fun SettingsHome(
    settings: Settings,
    scriptCount: Int,
    fontCount: Int,
    onOpen: (SettingsPage) -> Unit,
    focusOf: (SettingsPage) -> FocusRequester?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsPage.entries.forEachIndexed { index, p ->
            CategoryRow(
                title = p.label,
                summary = p.summary(settings, scriptCount, fontCount),
                onClick = { onOpen(p) },
                modifier = focusOf(p)?.let { Modifier.focusRequester(it) } ?: Modifier,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFocusable(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(summary, color = AppColors.TextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = AppColors.TextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ============== 共用小组件 =======================================================================

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = AppColors.AccentGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
internal fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
    TvFocusable(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = AppColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                if (on) "开" else "关",
                color = if (on) AppColors.AccentGreen else AppColors.TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun HintText(text: String) {
    Text(text, color = AppColors.TextMuted, fontSize = 11.sp)
}

/** 二级页统一的滚动容器。 */
@Composable
internal fun SettingsPageScaffold(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

@Composable
internal fun QrPill(text: String, onClick: () -> Unit) {
    TvPill(onClick = onClick, shape = CircleShape, contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(AppColors.BgPanel, RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Text(title, color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(message, color = AppColors.TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TvPill(
                    onClick = onCancel,
                    focusRequester = cancelFocus,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                ) { Text("取消", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                TvPill(
                    onClick = onConfirm,
                    selected = true,
                    accent = AppColors.Danger,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                ) { Text("确定", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ============== helpers ==========================================================================

private suspend fun readTextFromUri(context: android.content.Context, uri: Uri): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

/** Whether we can write to arbitrary folders with java.io.File right now. */
private fun hasStorageWriteAccess(ctx: android.content.Context): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        android.os.Environment.isExternalStorageManager()
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

/**
 * Auto-detects the input: if it looks like an http(s) URL, fetches the URL and imports the
 * body; otherwise treats the input as raw JS code and imports it directly.
 */
private fun importInput(scope: kotlinx.coroutines.CoroutineScope, input: String, onStatus: (String) -> Unit) {
    if (input.isBlank()) return
    val trimmed = input.trim()
    val isUrl = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    scope.launch {
        onStatus(if (isUrl) "正在下载脚本…" else "正在导入脚本…")
        val result = runCatching {
            val raw = if (isUrl) appContainer.fetchText(trimmed) else trimmed
            appContainer.scriptStore.import(raw).getOrThrow()
        }
        onStatus(result.fold({ it.message }, { "导入失败：${it.message}" }))
    }
}

private fun importFromUrl(scope: kotlinx.coroutines.CoroutineScope, url: String, onStatus: (String) -> Unit) {
    if (url.isBlank()) return
    scope.launch {
        onStatus("正在导入…")
        val result = runCatching {
            val raw = appContainer.fetchText(url.trim())
            appContainer.scriptStore.import(raw).getOrThrow()
        }
        onStatus(result.fold({ it.message }, { "导入失败：${it.message}" }))
    }
}
