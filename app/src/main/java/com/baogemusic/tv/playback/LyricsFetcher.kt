package com.baogemusic.tv.playback

import com.baogemusic.tv.data.model.Track
import com.baogemusic.tv.data.store.LyricSource
import com.baogemusic.tv.source.OtherSourceFinder
import com.baogemusic.tv.source.SourceManager
import com.baogemusic.tv.source.builtin.BuiltInLyricResolver
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * 一次歌词抓取的结果。
 * @param lines 解析好的歌词行；空列表表示没拿到。
 * @param hit 命中的内置数据源；null 表示来自用户脚本（或压根没命中）。
 * @param tried 按顺序实际尝试过的内置数据源，用于失败时给用户明确提示。
 */
data class LyricResult(
    val lines: List<LyricLine>,
    val hit: LyricSource?,
    val tried: List<LyricSource>,
)

/**
 * Fetches lyrics: user script → built-in direct (per the user's source order) → cross-source.
 *
 * 之前只按「曲目所属平台」抓一次，某一家的接口抽风就整首歌没歌词，还看不出原因。现在改成：
 * 1. 用户脚本（保留原优先级，自建源常常能补到官方没有的翻译）；
 * 2. 曲目所属平台直连——同一个 songmid，最准；
 * 3. 按「设置 → 歌词 → 数据源」里的顺序逐个平台换源尝试，前一个拿不到就自动回退下一个；
 * 4. 全部失败时返回 [LyricResult.tried]，UI 据此提示「已按顺序尝试过哪些源」。
 */
class LyricsFetcher(
    private val sources: SourceManager,
    private val builtIn: BuiltInLyricResolver,
    private val otherSourceFinder: OtherSourceFinder,
) {
    private val cache = ConcurrentHashMap<String, List<LyricLine>>()

    /** 用户在设置里排的数据源顺序（只取启用的）。由 AppContainer 跟设置同步写入。 */
    @Volatile
    var sourceOrder: List<LyricSource> = LyricSource.defaultOrder

    /** 关掉后只用排在第一位的源，不做回退。 */
    @Volatile
    var fallbackEnabled: Boolean = true

    suspend fun fetch(track: Track): LyricResult {
        cache[track.id]?.let { return LyricResult(it, null, emptyList()) }
        val order = sourceOrder.filter { it in LyricSource.entries }.ifEmpty { LyricSource.defaultOrder }
        val active = if (fallbackEnabled) order else order.take(1)
        val tried = LinkedHashSet<LyricSource>()

        // 1) 用户脚本优先（与改造前一致）
        tryScript(track)?.let { return finish(track, it, null, tried.toList()) }

        // 2) 与曲目同平台 → 直连，songmid 完全对得上
        val own = LyricSource.of(track.source)
        if (own != null && own in active) {
            tried += own
            builtIn.fetch(track)?.let { return finish(track, it, own, tried.toList()) }
        }

        // 3) 换源：一次搜索拿到其它平台的同名曲目，再按用户顺序逐个试
        val matches = runCatching { otherSourceFinder.findMatches(track, maxIntervalDiff = 10) }
            .getOrDefault(emptyList())
        for (src in active) {
            if (src in tried) continue
            val alt = matches.firstOrNull { LyricSource.of(it.source) == src } ?: continue
            tried += src
            builtIn.fetch(alt)?.let { return finish(track, it, src, tried.toList()) }
            tryScript(alt)?.let { return finish(track, it, src, tried.toList()) }
        }

        // 4) 最后兜底：不依赖「换源匹配」，直接按「歌名 + 歌手」跨平台搜同名歌词
        //    （酷狗 → 网易云 → QQ）。很多「正常歌却显示没歌词」都是卡在这一步之前。
        runCatching { builtIn.searchByName(track) }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return finish(track, it, null, tried.toList()) }

        return LyricResult(emptyList(), null, tried.toList())
    }

    private fun finish(
        track: Track,
        lines: List<LyricLine>,
        hit: LyricSource?,
        tried: List<LyricSource>,
    ): LyricResult {
        cache[track.id] = lines
        return LyricResult(lines, hit, tried)
    }

    private suspend fun tryScript(track: Track): List<LyricLine>? {
        val result = runCatching { sources.requestLyric(track) }.getOrNull() ?: return null
        val lyric = result.optString("lyric")
        val tlyric = result.optString("tlyric").ifEmpty { null }
        return LyricParser.parse(lyric, tlyric).ifEmpty { null }
    }

    fun clear() {
        cache.clear()
        builtIn.clear()
    }
}
