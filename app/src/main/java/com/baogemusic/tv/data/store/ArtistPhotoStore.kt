package com.baogemusic.tv.data.store

import android.content.Context
import com.baogemusic.tv.data.model.SourceID
import com.baogemusic.tv.data.model.Track
import com.baogemusic.tv.source.catalog.MOBILE_UA
import com.baogemusic.tv.source.catalog.urlEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/**
 * 歌手写真（艺人宣传照）解析器，用于「胶片」全屏歌词的背景图。
 *
 * 数据来源是网易云的公开接口（无需登录、无需加密签名，和 App 里搜索 / 歌单 / 歌词走的是同一套
 * `music.163.com` 的 `api` 端点）：
 *  1. 曲目本身就来自网易云 → `/api/song/detail` 拿到 `artists[0].id`；
 *  2. 其它平台曲目 → `/api/search/get` 用「歌名 + 歌手」搜一首，取首个歌手的 id；
 *  3. `/api/artist/{id}` 返回的 `img1v1Url` 就是歌手页顶部那张写真（1124×1124 级别）。
 *
 * `img1v1Id == 0` 时 `img1v1Url` 只是一个灰色默认头像（固定 URL 里的 5639395138885805），
 * 这种要判掉，否则背景会变成一块灰。命中失败会缓存空串，避免每次播放都重试。
 *
 * 结果按「歌手名」缓存在内存 + 磁盘（filesDir/artist_photos.json），同一歌手的所有歌共用一张。
 */
class ArtistPhotoStore(
    context: Context,
    private val client: OkHttpClient,
) {
    private val store = JsonStore<Map<String, String>>(
        file = File(context.filesDir, "artist_photos.json"),
        serializer = MapSerializer(String.serializer(), String.serializer()),
        default = emptyMap(),
    )
    private val mutex = Mutex()
    @Volatile private var loaded = false
    private val cache = LinkedHashMap<String, String>()

    /**
     * 取这首歌的歌手写真 URL；取不到返回 null（调用方自行回退到模糊封面）。
     * 网络异常一律吞掉——背景图不能影响播放。
     */
    suspend fun photoFor(track: Track): String? = withContext(Dispatchers.IO) {
        val key = keyOf(track)
        if (key.isEmpty()) return@withContext null

        ensureLoaded()
        mutex.withLock {
            cache[key]?.let { return@withLock it.ifBlank { null } }
        }

        val url = runCatching {
            val artistId = resolveArtistId(track) ?: return@runCatching null
            fetchArtistPhoto(artistId)
        }.getOrNull()

        mutex.withLock {
            cache[key] = url.orEmpty()
            while (cache.size > MAX_ENTRIES) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
            store.save(cache)
        }
        url
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return@withLock
            runCatching { cache.putAll(store.load()) }
            loaded = true
        }
    }

    private fun keyOf(track: Track): String =
        track.singer.trim().lowercase().replace(Regex("\\s+"), " ")

    // ---- 网络 -------------------------------------------------------------------------------

    /** 网易云曲目直查；其它平台按「歌名 + 歌手」搜一首，取首个歌手的 id。 */
    private suspend fun resolveArtistId(track: Track): String? {
        if (track.source == SourceID.WY) {
            val text = getText(
                "https://music.163.com/api/song/detail/?ids=%5B${track.songmid}%5D",
            )
            val artist = text?.let {
                runCatching {
                    JSONObject(it).optJSONArray("songs")
                        ?.optJSONObject(0)
                        ?.optJSONArray("artists")
                        ?.optJSONObject(0)
                }.getOrNull()
            }
            artist?.optString("id")?.toIdOrNull()?.let { return it }
        }

        val keyword = "${track.name} ${track.singer}".trim()
        if (keyword.isEmpty()) return null
        val text = postForm(
            "https://music.163.com/api/search/get",
            "s=${urlEncode(keyword)}&type=1&limit=5",
        ) ?: return null
        val songs = runCatching {
            JSONObject(text).optJSONObject("result")?.optJSONArray("songs")
        }.getOrNull() ?: return null

        val targetName = normalize(track.name)
        for (i in 0 until minOf(songs.length(), 5)) {
            val song = songs.optJSONObject(i) ?: continue
            // 歌名差太多的结果直接跳过，避免同名不同歌时抓到别人的写真。
            val name = normalize(song.optString("name"))
            if (targetName.isNotEmpty() && name.isNotEmpty() &&
                !name.contains(targetName) && !targetName.contains(name)
            ) continue
            val id = song.optJSONArray("artists")?.optJSONObject(0)?.optString("id")
            id?.toIdOrNull()?.let { return it }
        }
        return null
    }

    private suspend fun fetchArtistPhoto(artistId: String): String? {
        val text = getText("https://music.163.com/api/artist/$artistId") ?: return null
        val artist = runCatching { JSONObject(text).optJSONObject("artist") }.getOrNull() ?: return null
        // img1v1Id 为 0 → img1v1Url 只是默认灰头像，不能当写真。
        val hasRealPortrait = artist.optLong("img1v1Id", 0L) > 0L
        val portrait = artist.optString("img1v1Url").ifBlank { null }
        if (hasRealPortrait && portrait != null && portrait.isNotDefaultAvatar()) return portrait
        // 退而求其次：歌手头像
        return artist.optString("picUrl").ifBlank { null }?.takeIf { it.isNotDefaultAvatar() }
    }

    private suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).neteaseHeaders().get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    private suspend fun postForm(url: String, form: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = form.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
            val req = Request.Builder().url(url).neteaseHeaders().post(body).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    private fun Request.Builder.neteaseHeaders(): Request.Builder = apply {
        header("Referer", "https://music.163.com/")
        header("User-Agent", MOBILE_UA)
    }

    private companion object {
        const val MAX_ENTRIES = 400

        /** 固定默认灰头像：URL 与歌手无关，直接判掉。 */
        fun String.isNotDefaultAvatar(): Boolean =
            isNotBlank() && !contains("5639395138885805")

        fun String?.toIdOrNull(): String? =
            this?.takeIf { it.isNotBlank() && it != "0" }

        val dropChars = Regex("[\\s'.,，&\"、()（）`~\\-<>|/\\[\\]!！?？]")

        fun normalize(s: String): String = s.replace(dropChars, "").lowercase()
    }
}
