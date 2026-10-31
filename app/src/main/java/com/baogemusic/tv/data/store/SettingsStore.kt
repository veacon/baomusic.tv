package com.baogemusic.tv.data.store

import android.content.Context
import com.baogemusic.tv.data.model.Quality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File
import kotlin.math.roundToInt

/** Lyric text alignment in the full-screen player. LEFT is the default — the lyric pane sits on
 *  the left half of the screen, and ragged-right lines are easier to read at large font sizes. */
@Serializable
enum class LyricAlignment(val label: String) {
    LEFT("左对齐"),
    CENTER("居中"),
    RIGHT("右对齐"),
}

/** 歌词行距（倍率）。1.0–3.0，步长 0.1（设置页里是步进器）。行高 = 字号 × 该值。 */
object LyricScaleRange {
    const val MIN = 0.5f
    const val MAX = 2.0f
    const val STEP = 0.1f
    /** 1.0 倍时的行高倍率（字号 × 该值）。 */
    const val BASE_LINE_FACTOR = 1.5f

    fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)
    /** 步进后保留一位小数，避免浮点累加出 1.4000001 这种显示值。 */
    fun step(value: Float, delta: Float): Float =
        clamp(((value + delta) * 10).roundToInt() / 10f)
    fun lineFactorOf(scale: Float): Float = BASE_LINE_FACTOR * scale
    /** 非当前行字号：约为当前行的 0.68 倍。 */
    fun inactiveOf(activeFontSp: Int): Int = (activeFontSp * 0.68f).roundToInt().coerceAtLeast(12)
    /** 翻译行字号：约为当前行的 0.52 倍。 */
    fun translationOf(activeFontSp: Int): Int = (activeFontSp * 0.52f).roundToInt().coerceAtLeast(10)
}

/** 歌词当前行字号（sp，绝对值）。设置页直接以数值显示（如 45），不再是倍率。 */
object LyricFont {
    const val MIN = 16
    const val MAX = 72
    const val STEP = 1
    const val DEFAULT = 40
    fun clamp(value: Int): Int = value.coerceIn(MIN, MAX)
    fun step(value: Int, delta: Int): Int = clamp(value + delta)
}

/** 歌词字体（只作用于歌词）。SYSTEM 用系统默认（思源/Noto 一类），
 *  MINGMEI 是随包内置的「Aa祝你我明媚向春天」（res/font），CUSTOM 用用户导入的字体
 *  （见 [com.baogemusic.tv.data.store.FontStore]）。
 *  注：MINGMEI 保留了旧的序列化名 `PINGFANG_HK`（苹方已按需求下线，老存档自动落到这个字体）。 */
@Serializable
enum class AppFontFamily(val label: String) {
    SYSTEM("系统默认"),
    @SerialName("PINGFANG_HK") MINGMEI("明媚向春天"),
    XIAODOU("小抖动侠情书"),
    CUSTOM("自定义字体"),
}

/** 歌词数据源。内置直连用各家公开接口抓取，用户可自己排序：按顺序尝试，前一个拿不到就换下一个。
 *  排序只影响内置直连，用户脚本的歌词仍然优先（与改造前一致）。 */
@Serializable
enum class LyricSource(val label: String, val sourceId: com.baogemusic.tv.data.model.SourceID) {
    @SerialName("wy") NETEASE("网易云", com.baogemusic.tv.data.model.SourceID.WY),
    @SerialName("tx") QQ("QQ音乐", com.baogemusic.tv.data.model.SourceID.TX),
    @SerialName("kg") KUGOU("酷狗", com.baogemusic.tv.data.model.SourceID.KG),
    @SerialName("kw") KUWO("酷我", com.baogemusic.tv.data.model.SourceID.KW),
    ;

    companion object {
        /** 默认顺序：网易云 → QQ音乐 → 酷狗 → 酷我。 */
        val defaultOrder: List<LyricSource> = listOf(NETEASE, QQ, KUGOU, KUWO)
        fun of(id: com.baogemusic.tv.data.model.SourceID?): LyricSource? =
            entries.firstOrNull { it.sourceId == id }
    }
}

/** 全屏播放页「唱片 / 歌词」的左右排布。LYRIC_LEFT 是歌词在左、唱片在右；
 *  LYRIC_RIGHT 是网易云同款（唱片在左、歌词在右）。 */
@Serializable
enum class PlayerLayout(val label: String) {
    LYRIC_LEFT("歌词左 · 唱片右"),
    LYRIC_RIGHT("唱片左 · 歌词右"),
}

/** 唱片 / 封面的展示形状。 */
@Serializable
enum class DiscShape(val label: String) {
    CIRCLE("圆形唱片"),
    ROUNDED_SQUARE("圆角方形"),
}

/** 播放页背景动效。
 *  - WAVE：参考 QQ 音乐「动感波光」（随音量向外扩散的波纹）；
 *  - RIPPLE：波纹特效（参考 BMMusicPlayer，连续同心水波从封面中心一圈圈荡开）；
 *  - DUST：宇宙尘埃特效（参考 ParticleView，细碎尘埃 / 星点在背景中缓慢漂浮、闪烁）。
 *  全部用纯 Compose Canvas 绘制（不依赖 Web 内核），在 Android 8 的电视 / 投影仪上也能跑。 */
@Serializable
enum class PlayerFx(val label: String) {
    OFF("关闭"),
    WAVE("动感波光"),
    RIPPLE("波纹特效"),
    DUST("宇宙尘埃特效"),
}

/** 全屏歌词特效（参考 YuMusic / QQ 音乐）：
 *  - NONE：常规两栏歌词；
 *  - LIGHT（光感）：全屏居中三行歌词 + 柔和暗角，逐句淡入（原「商籁」）；
 *  - STARSEA（星海）：全屏深蓝星海背景 + 蓝白辉光歌词；
 *  - CINEMA（胶片）：歌手写真做背景（肯·伯恩斯缓慢推近）+ 电影黑边 + 颗粒，底部字幕式歌词。
 *  已下线的「极光 AURORA」「霓虹 NEON」由 [FullscreenLyricFxSerializer] 兜底降级为光感，
 *  老存档不会因为枚举值消失而整份设置被重置。 */
@Serializable(with = FullscreenLyricFxSerializer::class)
enum class FullscreenLyricFx(val label: String) {
    NONE("关闭"),
    LIGHT("光感"),
    STARSEA("星海"),
    CINEMA("胶片"),
}

/** 手写枚举序列化器：按名字存，读取时把历史/已下线的值映射到现存项。 */
object FullscreenLyricFxSerializer : KSerializer<FullscreenLyricFx> {
    override val descriptor =
        PrimitiveSerialDescriptor("FullscreenLyricFx", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FullscreenLyricFx) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): FullscreenLyricFx {
        return when (val raw = decoder.decodeString()) {
            // 已下线或历史命名 → 光感
            "SONNET", "AURORA", "NEON" -> FullscreenLyricFx.LIGHT
            else -> FullscreenLyricFx.entries.firstOrNull { it.name == raw }
                ?: FullscreenLyricFx.NONE
        }
    }
}

/** 歌单 / 我的页面的卡片列数。列数越多卡片越小。 */
object GridColumns {
    val choices = listOf(4, 5, 6, 7, 8)
    const val DEFAULT = 5
    fun clamp(value: Int): Int = value.coerceIn(choices.first(), choices.last())
}

/** 播放页唱片（封面）整体大小的缩放系数：0.5–1.2，步长 0.1，默认 1.0（即各布局的原始尺寸）。
 *  改动即时生效，封面与动效中心都会同步缩放。 */
object CoverScale {
    const val MIN = 0.5f
    const val MAX = 3.0f
    const val STEP = 0.1f
    const val DEFAULT = 1.0f
    fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)
    /** 步长 0.1：先按步长归一再乘回，避免 0.50000001 这类浮点累积误差。 */
    fun step(value: Float, delta: Float): Float =
        clamp((((value + delta) / STEP).roundToInt()) * STEP)
}

/** 图片磁盘缓存上限（GB）。内存缓存另在 ImageLoader 里按进程可用内存封顶（≤64MB），
 *  避免 1.5GB 这类小内存设备 OOM。步长 0.5GB，范围 0.5–5GB。改动后即时重建 ImageLoader 生效。 */
object CacheQuota {
    const val MIN_GB = 0.5f
    const val MAX_GB = 5.0f
    const val STEP_GB = 0.5f
    const val DEFAULT_GB = 0.5f
    fun clamp(value: Float): Float = value.coerceIn(MIN_GB, MAX_GB)
    /** 步长 0.5GB：先按步长归一再乘回，避免 0.50000001 这类浮点累积误差。 */
    fun step(value: Float, delta: Float): Float =
        clamp((((value + delta) / STEP_GB).roundToInt()) * STEP_GB)
}

/** Color theme for the player's spinning vinyl disc. GREEN is the original look and the default;
 *  BLACK (a real record) was requested in #8. Palettes live in PlayerScreen. */
@Serializable
enum class VinylColor(val label: String) {
    GREEN("翠绿"),
    BLACK("经典黑"),
    RED("酒红"),
    BLUE("深蓝"),
    GOLD("琥珀金"),
}

@Serializable
data class Settings(
    val preferredQuality: Quality = Quality.FLAC24,
    val fallbackEnabled: Boolean = true,
    /** Audio offload (DSP direct path / HDMI passthrough). Bit-perfect Hi-Res on good HALs,
     *  but some vendor HALs (小米/鸿蒙/坚果、以及 Android 8 的 MStar/m7221 等) 声称支持却在 native
     *  层崩溃，所以默认关闭；只有用户主动打开才会启用。 */
    val audioOffloadEnabled: Boolean = false,
    /** 强制软件解码（音频）。部分电视、盒子的硬件解码器会闪退或没声音，开启后只用系统软件解码器，
     *  同时关闭硬件直通。**默认开启**：稳定性优先，牺牲一点 Hi-Res 直通。 */
    val forceSoftwareDecoding: Boolean = true,
    val showLyricTranslation: Boolean = true,
    /** 歌词当前行字号（sp，绝对值，如 45）。设置页直接以数值显示，步进 1。 */
    val lyricFontSizeSp: Int = LyricFont.DEFAULT,
    /** 歌词行距倍率，0.5–2.0（步长 0.1）；实际行高 = 字号 × 1.5 × 该值。 */
    val lyricLineScale: Float = 0.9f,
    /** 歌词对齐方式：左对齐 / 居中 / 右对齐。 */
    val lyricAlignment: LyricAlignment = LyricAlignment.LEFT,
    /** 歌词字体（内置字体见 res/font；CUSTOM 走 [com.baogemusic.tv.data.store.FontStore]）。
     *  仅作用于歌词文字，不影响其他界面文字。 */
    val appFontFamily: AppFontFamily = AppFontFamily.SYSTEM,
    /** [AppFontFamily.CUSTOM] 选中的用户字体 id（FontStore 里的 UserFont.id）。 */
    val customFontId: String? = null,
    /** 歌词数据源顺序：按顺序尝试，前一个拿不到歌词就自动回退到下一个。 */
    val lyricSources: List<LyricSource> = LyricSource.defaultOrder,
    /** 允许自动回退：关掉后只用排在第一位的源。 */
    val lyricSourceFallback: Boolean = true,
    val vinylColor: VinylColor = VinylColor.GREEN,
    /** 播放页唱片 / 歌词的左右排布：默认「歌词左 · 唱片右」。 */
    val playerLayout: PlayerLayout = PlayerLayout.LYRIC_LEFT,
    /** 唱片 / 封面形状：默认圆形唱片。 */
    val discShape: DiscShape = DiscShape.CIRCLE,
    /** 播放页背景动效：关闭 / 动感波光 / 波纹特效 / 宇宙尘埃特效。 */
    val playerFx: PlayerFx = PlayerFx.WAVE,
    /** 播放页全屏歌词特效：关闭 / 光感 / 星海。 */
    val fullscreenLyricFx: FullscreenLyricFx = FullscreenLyricFx.NONE,
    /** 播放页唱片（封面）整体大小的缩放系数：0.5–3.0（步长 0.1，默认 1.0）。
     *  改动即时生效，封面、动效中心都会同步缩放。详见 [CoverScale]。 */
    val coverScale: Float = CoverScale.DEFAULT,
    /** 歌单页 / 我的页面的卡片列数（4–8），列数越多卡片越小。 */
    val gridColumns: Int = GridColumns.DEFAULT,
    /** 图片磁盘缓存上限（GB）。详见 [CacheQuota]。 */
    val imageCacheGb: Float = CacheQuota.DEFAULT_GB,
    /** Player screen: hide progress bar + transport after a few idle seconds; any D-pad / OK key
     *  brings them back. On by default. */
    val autoHidePlayerControls: Boolean = true,
    /** 显示调试信息: app-wide switch for diagnostic UI (off by default). Currently: the player's
     *  status line (正在获取播放地址 / 播放失败已跳过 / 已换音源 …). Gate future debug UI on it too. */
    val showDebugInfo: Boolean = false,
    /** Absolute path of the chosen download root (a writable storage volume). null ⇒ default
     *  app-scoped Music dir on primary storage. See DownloadStore.availableRoots(). */
    val customDownloadDir: String? = null,
    /** A user-picked SAF folder (content tree Uri) to download into. When non-null it takes
     *  precedence over [customDownloadDir] — files are exported into this folder so they live in
     *  a browsable location (e.g. a USB drive folder). null ⇒ use the File-based volume root. */
    val customDownloadTreeUri: String? = null,
    /** Max simultaneous download transfers (batch or otherwise). Clamped to 1..8 on apply. */
    val maxConcurrentDownloads: Int = 3,
    /** Batch "下载全部": when a track is already downloaded but at a different quality than the
     *  chosen target, re-download it to match (true) vs. always skip already-downloaded (false). */
    val redownloadOnQualityChange: Boolean = true,
    /** Sources that feed the discover (home) page. 默认仅网易云 + QQ 音乐。 */
    val homeSources: Set<com.baogemusic.tv.data.model.SourceID> = setOf(
        com.baogemusic.tv.data.model.SourceID.WY,
        com.baogemusic.tv.data.model.SourceID.TX,
    ),
    /** 设置结构版本，仅用于一次性迁移（见 [SettingsStore.loadAll]）。不要手动改。 */
    val schemaVersion: Int = 0,
)

/** App preferences persisted as JSON. */
class SettingsStore(context: Context) {
    private val store = JsonStore(
        File(context.filesDir, "settings.json"),
        Settings.serializer(),
        Settings(),
    )

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    suspend fun loadAll() {
        val loaded = withContext(Dispatchers.IO) { store.load() }
        // 一次性迁移：v1 起「强制软件解码」默认开启、硬件直通默认关闭。
        // 已装过旧版本的机器里存的是 false，光改默认值对它们无效，必须在这里改写一次，
        // 否则老电视（Android 8 / MStar 等）升级后仍会一按播放就闪退。
        val migrated = if (loaded.schemaVersion < SCHEMA_SOFTWARE_DECODE) {
            loaded.copy(
                forceSoftwareDecoding = true,
                audioOffloadEnabled = false,
                schemaVersion = SCHEMA_SOFTWARE_DECODE,
            )
        } else {
            loaded
        }
        _settings.value = migrated
        if (migrated != loaded) {
            withContext(Dispatchers.IO) { store.save(migrated) }
        }
    }

    suspend fun update(transform: (Settings) -> Settings) {
        _settings.value = transform(_settings.value)
        withContext(Dispatchers.IO) { store.save(_settings.value) }
    }

    private companion object {
        /** 迁移版本号：1 = 强制软件解码默认开 + 硬件直通默认关。 */
        const val SCHEMA_SOFTWARE_DECODE = 1
    }
}
