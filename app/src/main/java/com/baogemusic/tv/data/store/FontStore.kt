package com.baogemusic.tv.data.store

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** 一个用户导入的字体文件（存在 filesDir/fonts 下）。 */
@Serializable
data class UserFont(
    val id: String,
    /** 展示名（上传时的文件名，去掉扩展名）。 */
    val name: String,
    /** 在 fonts 目录下的本地文件名。 */
    val fileName: String,
    val sizeBytes: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

/** 字体上传的限制条件。三个扩展名之外的文件一律拒收，超过 [MAX_BYTES] 也拒收——电视端解码一个
 *  上百 MB 的字体文件会直接把播放器卡住。 */
object FontImportRules {
    val allowedExtensions = listOf("ttf", "otf", "woff2")
    const val MAX_BYTES = 30L * 1024 * 1024
    const val MAX_BYTES_LABEL = "30MB"

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.US)

    fun isExtensionAllowed(name: String): Boolean = extensionOf(name) in allowedExtensions

    /** 文件头魔数校验：只认 TrueType / OTF(CFF) / TTC / WOFF / WOFF2，防止把别的文件改名成 .ttf 传进来。 */
    fun looksLikeFont(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val tag = String(bytes, 0, 4, Charsets.ISO_8859_1)
        return tag == "true" || tag == "ttcf" || tag == "OTTO" || tag == "wOFF" || tag == "wOF2" ||
            (bytes[0] == 0x00.toByte() && bytes[1] == 0x01.toByte() && bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte())
    }
}

/**
 * 用户自定义字体仓库。字体文件放在 filesDir/fonts，清单（[UserFont] 列表）用 [JsonStore] 持久化。
 *
 * 导入做三步校验：扩展名 → 大小 → 文件头魔数，最后用 [Typeface.createFromFile] 实际解析一次；
 * 解析不了（例如 woff2，Android 的字体引擎不支持直接加载）就返回明确错误，不入库。
 */
class FontStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "fonts").apply { mkdirs() }

    private val store by lazy {
        JsonStore(
            File(context.filesDir, "fonts.json"),
            ListSerializer(UserFont.serializer()),
            emptyList(),
        )
    }

    private val _fonts = MutableStateFlow<List<UserFont>>(emptyList())
    val fonts: StateFlow<List<UserFont>> = _fonts.asStateFlow()

    /** Typeface 解析结果缓存：同一个字体在歌词页每帧都要用，不能每次都去读文件。 */
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    suspend fun loadAll() {
        val list = withContext(Dispatchers.IO) {
            dir.mkdirs()
            // 清单里有、磁盘上没有的记录直接丢掉（清数据 / 手动删文件的残留）。
            store.load().filter { File(dir, it.fileName).exists() }
        }
        _fonts.value = list
    }

    /** 从系统文件选择器（SAF）导入。 */
    suspend fun importFromUri(uri: Uri, fallbackName: String? = null): Result<UserFont> {
        val displayName = withContext(Dispatchers.IO) { queryDisplayName(uri) }
        val bytes = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        }
        if (bytes == null) return Result.failure(IllegalStateException("读取文件失败，换个文件试试"))
        return importBytes(bytes, displayName ?: fallbackName ?: "font.ttf")
    }

    /** 从扫码上传暂存的文件导入（LocalServer 先把手机传上来的文件落到 cacheDir）。 */
    suspend fun importFromFile(file: File, displayName: String): Result<UserFont> {
        val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
            ?: return Result.failure(IllegalStateException("读取文件失败，换个文件试试"))
        return importBytes(bytes, displayName)
    }

    suspend fun remove(id: String) {
        val target = _fonts.value.firstOrNull { it.id == id } ?: return
        withContext(Dispatchers.IO) {
            runCatching { File(dir, target.fileName).delete() }
        }
        typefaceCache.remove(id)
        _fonts.value = _fonts.value.filterNot { it.id == id }
        persist()
    }

    /** 解析出可用的 [Typeface]；失败返回 null（调用方负责给提示）。在 IO 线程读文件。 */
    suspend fun typeface(id: String): Typeface? {
        typefaceCache[id]?.let { return it }
        val font = _fonts.value.firstOrNull { it.id == id } ?: return null
        val tf = withContext(Dispatchers.IO) {
            runCatching { Typeface.createFromFile(File(dir, font.fileName)) }.getOrNull()
        }
        return tf?.also { typefaceCache[id] = it }
    }

    private suspend fun importBytes(bytes: ByteArray, displayName: String): Result<UserFont> {
        val name = displayName.substringAfterLast('/').substringAfterLast('\\')
        if (!FontImportRules.isExtensionAllowed(name)) {
            return Result.failure(
                IllegalStateException("只支持 ${FontImportRules.allowedExtensions.joinToString(" / ")} 字体文件"),
            )
        }
        if (bytes.size > FontImportRules.MAX_BYTES) {
            return Result.failure(
                IllegalStateException(
                    "字体不能超过 ${FontImportRules.MAX_BYTES_LABEL}（这个文件 %.1fMB）".format(bytes.size / 1024f / 1024f),
                ),
            )
        }
        if (!FontImportRules.looksLikeFont(bytes)) {
            return Result.failure(IllegalStateException("这不是有效的字体文件"))
        }
        val isWoff2 = FontImportRules.extensionOf(name) == "woff2"
        val ext = FontImportRules.extensionOf(name)
        val fileName = "${System.currentTimeMillis()}-${sanitize(name.substringBeforeLast('.'))}.$ext"
        val target = File(dir, fileName)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            target.writeBytes(bytes)
        }
        // 真正解析一次：woff2 之类的文件在 Android 上解析不了，这里会返回 null。
        val parsed = withContext(Dispatchers.IO) {
            runCatching { Typeface.createFromFile(target) }.getOrNull()
        }
        if (parsed == null) {
            withContext(Dispatchers.IO) { runCatching { target.delete() } }
            return Result.failure(
                IllegalStateException(
                    if (isWoff2) "Android 不能直接加载 woff2，请先在电脑上转换成 ttf / otf"
                    else "字体解析失败，可能文件已损坏",
                ),
            )
        }
        val font = UserFont(
            id = fileName.substringBeforeLast('.'),
            name = name.substringBeforeLast('.').ifBlank { "自定义字体" },
            fileName = fileName,
            sizeBytes = bytes.size.toLong(),
        )
        typefaceCache[font.id] = parsed
        _fonts.value = _fonts.value + font
        persist()
        return Result.success(font)
    }

    private suspend fun persist() {
        val snapshot = _fonts.value
        withContext(Dispatchers.IO) { store.save(snapshot) }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        if (uri.scheme == android.content.ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } else {
            uri.lastPathSegment
        }
    }.getOrNull()

    private fun sanitize(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifBlank { "font" }
}
