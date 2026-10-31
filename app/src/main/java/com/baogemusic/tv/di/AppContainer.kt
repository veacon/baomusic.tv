package com.baogemusic.tv.di

import android.content.Context
import com.baogemusic.tv.data.store.LibraryStore
import com.baogemusic.tv.data.store.PlaybackSnapshotStore
import com.baogemusic.tv.data.store.ScriptStore
import com.baogemusic.tv.data.store.SearchHistoryStore
import com.baogemusic.tv.data.store.SettingsStore
import com.baogemusic.tv.playback.EqualizerManager
import com.baogemusic.tv.playback.LyricsFetcher
import com.baogemusic.tv.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.baogemusic.tv.source.OtherSourceFinder
import com.baogemusic.tv.source.SourceManager
import com.baogemusic.tv.source.builtin.BuiltInLyricResolver
import com.baogemusic.tv.source.builtin.BuiltInResolver
import com.baogemusic.tv.source.catalog.Boards
import com.baogemusic.tv.source.catalog.CatalogHttp
import com.baogemusic.tv.source.catalog.Catalogs
import com.baogemusic.tv.source.catalog.Songlists
import com.baogemusic.tv.source.js.ScriptHttpClient
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlin.math.min
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency container (no Hilt). Holds app-wide singletons; created once in [App].
 */
class AppContainer(val appContext: Context) {

    /** Process-wide event bus for hardware-key events (e.g. KEYCODE_MENU). */
    val events: AppEvents = AppEvents()

    /** In-app HTTP server backing the phone-to-TV QR flow. null when start failed. */
    @Volatile var localServer: LocalServer? = null
        private set

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val preload: String by lazy {
        appContext.assets.open("script/user-api-preload.js").use { it.readBytes().toString(Charsets.UTF_8) }
    }

    val catalogHttp by lazy { CatalogHttp(httpClient) }

    val catalogs by lazy { Catalogs(catalogHttp) }
    val hotSearch by lazy { com.baogemusic.tv.source.catalog.HotSearch(catalogHttp) }
    val boards by lazy { Boards(catalogHttp) }
    val songlists by lazy { Songlists(catalogHttp) }
    /** Backs the recommend (discover) right column — heroes / recommendations / boards. */
    val homeStore by lazy { com.baogemusic.tv.ui.recommend.HomeStore(songlists, boards) }

    private val scriptHttp by lazy { ScriptHttpClient(httpClient) }
    private val builtInResolver by lazy { BuiltInResolver(httpClient) }
    private val builtInLyricResolver by lazy { BuiltInLyricResolver(catalogHttp) }
    private val otherSourceFinder by lazy { OtherSourceFinder(catalogs) }

    val sourceManager: SourceManager by lazy {
        SourceManager(preload, scriptHttp, otherSourceFinder, builtInResolver)
    }

    val lyricsFetcher by lazy {
        LyricsFetcher(sourceManager, builtInLyricResolver, otherSourceFinder)
    }

    /** Created eagerly on the main thread in [App] (ExoPlayer needs a consistent looper). */
    lateinit var playbackController: PlaybackController
        private set

    val equalizerManager: EqualizerManager by lazy {
        EqualizerManager { playbackController.audioSessionId }
    }

    /** In-app updater — checks GitHub Releases, downloads the ABI-matched APK, launches install. */
    val updateManager: com.baogemusic.tv.playback.update.UpdateManager by lazy {
        com.baogemusic.tv.playback.update.UpdateManager(appContext, httpClient)
    }

    val fontStore: com.baogemusic.tv.data.store.FontStore by lazy {
        com.baogemusic.tv.data.store.FontStore(appContext)
    }
    val scriptStore: ScriptStore by lazy { ScriptStore(appContext, sourceManager) }
    val libraryStore: LibraryStore by lazy { LibraryStore(appContext) }
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
    val playbackSnapshotStore: PlaybackSnapshotStore by lazy { PlaybackSnapshotStore(appContext) }
    val searchHistoryStore: SearchHistoryStore by lazy { SearchHistoryStore(appContext) }
    val coverCache: com.baogemusic.tv.data.store.CoverCache by lazy {
        com.baogemusic.tv.data.store.CoverCache(appContext)
    }
    val downloadStore: com.baogemusic.tv.data.store.DownloadStore by lazy {
        com.baogemusic.tv.data.store.DownloadStore(appContext)
    }
    val localFolderStore: com.baogemusic.tv.data.store.LocalFolderStore by lazy {
        com.baogemusic.tv.data.store.LocalFolderStore(appContext)
    }
    val downloadCoordinator: com.baogemusic.tv.playback.download.DownloadCoordinator by lazy {
        com.baogemusic.tv.playback.download.DownloadCoordinator(
            store = downloadStore,
            sources = sourceManager,
            coverCache = coverCache,
            http = httpClient,
            lyricsFetcher = lyricsFetcher,
            catalogHttp = catalogHttp,
        )
    }
    val localMusicStore: com.baogemusic.tv.playback.local.LocalMusicStore by lazy {
        com.baogemusic.tv.playback.local.LocalMusicStore(
            context = appContext,
            localFolderStore = localFolderStore,
            coverCache = coverCache,
        )
    }

    // ============ 图片加载：受控内存 + 磁盘缓存，避免 1.5GB 设备 OOM ============
    private var _imageLoader: ImageLoader? = null

    /** 全局唯一 ImageLoader：内存缓存按进程可用内存封顶（≤64MB），磁盘缓存按 [CacheQuota] 上限。
     *  首次访问时用当前设置构建并注册为 Coil 单例，之后 AsyncImage 自动复用。 */
    val imageLoader: ImageLoader
        get() = _imageLoader ?: buildImageLoader(settingsStore.settings.value.imageCacheGb).also {
            _imageLoader = it
            Coil.setImageLoader(it)
        }

    private fun buildImageLoader(cacheGb: Float): ImageLoader {
        val maxMem = Runtime.getRuntime().maxMemory().coerceAtLeast(64L * 1024 * 1024)
        // 内存缓存最多占进程的 1/16，且封顶 64MB（64MB = 67,108,864，仍在 Int 范围内）。
        // 封面多为大图，封顶能压住峰值内存，避免 1.5GB 设备 OOM。
        val memBytes = min(maxMem / 16, 64L * 1024 * 1024).toInt()
        // 磁盘缓存上限按 GB 换算成字节（上限 5GB 仍远小于 Int 上限，安全）。
        val diskBytes = (cacheGb * 1024f * 1024f * 1024f).toLong().coerceAtLeast(64L * 1024 * 1024)
        return ImageLoader.Builder(appContext)
            .memoryCache { MemoryCache.Builder(appContext).maxSizeBytes(memBytes).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "image_cache"))
                    .maxSizeBytes(diskBytes)
                    .build()
            }
            // 关闭硬件位图：动效封面 / 模糊背景需要软件解码绘制，统一走堆内存更可控。
            .allowHardware(false)
            .build()
    }

    /** 缓存上限改变时重建 ImageLoader（先清空旧磁盘缓存，再切到新上限）。 */
    fun rebuildImageLoader(cacheGb: Float) {
        val next = buildImageLoader(cacheGb)
        _imageLoader?.diskCache?.clear()
        _imageLoader = next
        Coil.setImageLoader(next)
    }

    /** Process-lived scope used for operations that mustn't be cancelled by a UI navigation
     *  (e.g. settings → script delete). Public so screens can opt into it explicitly. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun initPlayback() {
        if (!::playbackController.isInitialized) {
            playbackController = PlaybackController(
                appContext,
                sourceManager,
                lyricsFetcher,
                com.baogemusic.tv.playback.AudioSpecProbe(httpClient),
            )
            playbackController.onTrackStarted = { track ->
                appScope.launch { libraryStore.recordHistory(track) }
            }
            // Local-first URL resolver — downloads + SAF imports skip the online cascade.
            // Doubles as the play-time file check: if a COMPLETED download's file is gone we
            // flag it missing (badge disappears, 已下载 tab shows 文件缺失) and fall through to
            // the online cascade; if it's there we clear any stale missing flag.
            playbackController.localUrlResolver = { track ->
                // Resolves both File downloads and SAF-exported downloads to a playable Uri.
                val playable = downloadStore.localPlayableUri(track.id)
                when {
                    playable != null -> {
                        downloadStore.markPresent(track.id)
                        playable
                    }
                    else -> {
                        if (downloadStore.isDownloaded(track.id)) downloadStore.markMissing(track.id)
                        localMusicStore.fileUri(track)?.toString()
                    }
                }
            }
        }
    }

    // Hot-search JSON cache for the phone page (/api/hotsearch). Fetched at most once per 5 min;
    // called from NanoHTTPD's worker thread, so blocking here is fine.
    @Volatile private var hotSearchCache: String = "[]"
    @Volatile private var hotSearchCachedAt: Long = 0L

    /** Blocking hot-search JSON: [{"source":"kw","name":"酷我","words":[...]}, ...]. */
    private fun hotSearchJsonBlocking(): String {
        val now = System.currentTimeMillis()
        if (now - hotSearchCachedAt < 5 * 60_000L && hotSearchCache != "[]") return hotSearchCache
        val json = runCatching {
            kotlinx.coroutines.runBlocking {
                val cols = hotSearch.fetchAll()
                buildString {
                    append('[')
                    cols.forEachIndexed { i, col ->
                        if (i > 0) append(',')
                        append("{\"source\":\"").append(col.source.key)
                        append("\",\"name\":\"").append(col.source.displayName)
                        append("\",\"words\":[")
                        col.words.forEachIndexed { j, w ->
                            if (j > 0) append(',')
                            append('"').append(jsonEscape(w)).append('"')
                        }
                        append("]}")
                    }
                    append(']')
                }
            }
        }.getOrDefault("[]")
        if (json != "[]") { hotSearchCache = json; hotSearchCachedAt = now }
        return json
    }

    private fun jsonEscape(s: String): String = buildString {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }

    /** Fetch a remote text resource (used to import a custom-source script from a URL). */
    suspend fun fetchText(url: String): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        okhttp3.Request.Builder().url(url).build().let { req ->
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                resp.body?.string() ?: throw IllegalStateException("空响应")
            }
        }
    }

    /** Load persisted data and wire settings → playback. Called once at startup. */
    fun bootstrap() {
        // Bring up the LAN HTTP server (best-effort; QR features just won't work if it fails).
        if (localServer == null) {
            localServer = LocalServer.start(
                events,
                hotSearchJson = { hotSearchJsonBlocking() },
                uploadDir = File(appContext.cacheDir, "uploads"),
                debugReport = {
                    runCatching {
                        com.baogemusic.tv.data.store.DebugReport.build(
                            appContext,
                            kotlinx.serialization.json.Json.encodeToString(
                                com.baogemusic.tv.data.store.Settings.serializer(),
                                settingsStore.settings.value,
                            ),
                        )
                    }.getOrNull()
                },
            )
        }
        appScope.launch {
            settingsStore.loadAll()
            libraryStore.loadAll()
            settingsStore.settings.onEach { s ->
                playbackController.preferredQuality = s.preferredQuality
                // 硬件直通会把未解码的数据直接交给音频硬件，绕过解码器；强制软解时一并关掉。
                // Android 8（API 26/27）及以下不少电视芯片（如 MStar/m7221）的音频 HAL 会声称支持
                // offload，实际喂入 FLAC/Hi-Res 时在 native 层直接崩溃（表现为一按播放就闪退），
                // 因此仅在 API 29+（Android 10+）才允许开启。
                playbackController.setAudioOffloadEnabled(
                    s.audioOffloadEnabled &&
                        !s.forceSoftwareDecoding &&
                        android.os.Build.VERSION.SDK_INT >= 29,
                )
                playbackController.forceSoftwareDecoding = s.forceSoftwareDecoding
                sourceManager.fallbackEnabled = s.fallbackEnabled
                // 歌词数据源：按顺序尝试，前一个拿不到就自动回退到下一个。
                lyricsFetcher.sourceOrder = s.lyricSources
                lyricsFetcher.fallbackEnabled = s.lyricSourceFallback
                downloadStore.configuredRoot = s.customDownloadDir?.let { java.io.File(it) }
                downloadStore.downloadTreeUri = s.customDownloadTreeUri?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                downloadCoordinator.setMaxConcurrent(s.maxConcurrentDownloads)
            }.launchIn(appScope)
            // 图片缓存上限改变时重建 ImageLoader（先清空旧磁盘缓存再套用新上限）。
            settingsStore.settings.map { it.imageCacheGb }.distinctUntilChanged()
                .onEach { rebuildImageLoader(it) }
                .launchIn(appScope)
            scriptStore.loadAll()
            fontStore.loadAll()
            searchHistoryStore.load()
            coverCache.loadAll()
            downloadStore.loadAll()
            localFolderStore.loadAll()

            // Restore last session's queue + index + playing state. Run after scripts load so
            // any custom-source needed to resolve URLs is ready.
            val snapshot = playbackSnapshotStore.load()
            playbackController.restoreSnapshot(snapshot)

            // Auto-save the snapshot whenever queue/index/isPlaying changes. distinctUntilChanged
            // already coalesces the high-frequency position ticks since we only project the three
            // fields that matter here.
            playbackController.state
                .map { Triple(it.queue.map { t -> t.id }, it.index, it.isPlaying) }
                .distinctUntilChanged()
                .onEach { playbackSnapshotStore.save(playbackController.snapshot()) }
                .launchIn(appScope)
        }
    }

    /** Synchronous final save — called right before the process is killed on user-confirmed exit. */
    fun saveSnapshotNow() {
        if (::playbackController.isInitialized) {
            runCatching { playbackSnapshotStore.saveBlocking(playbackController.snapshot()) }
        }
    }
}
