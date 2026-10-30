package com.baogemusic.tv.data.store

import android.content.Context
import coil.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 缓存管理：计算与清理「可重建」的缓存，控制 App 在 1.5GB 小内存设备上的存储 / 内存占用。
 *
 * 覆盖对象：
 *  - Coil 内存缓存（Bitmap LRU，已被 ImageLoader 封顶在 ≤64MB，但这里提供一键清空）
 *  - Coil 磁盘缓存（封面 / 歌词图，大小由 [com.baogemusic.tv.data.store.CacheQuota] 控制）
 *  - 内嵌封面缓存（EmbeddedCovers，从本地音频抽取）
 *  - 扫码上传临时目录（uploads）
 *
 * 不碰下载的音乐文件（在 Music 目录，属于用户数据，误删会丢歌）。
 */
object CacheManager {

    /** 当前可清理缓存的总大小（字节）：遍历系统指定的 cacheDir（本就是可丢弃目录，安全）。 */
    suspend fun sizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        dirSize(context.cacheDir)
    }

    /**
     * 清理可重建的缓存，返回释放的字节数。
     * 内存缓存即时清空；磁盘相关目录整体删除后由各自组件懒加载重建。
     */
    suspend fun clear(context: Context, imageLoader: ImageLoader): Long = withContext(Dispatchers.IO) {
        val before = sizeBytes(context)
        // 1) Coil 内存缓存：把占着的 Bitmap 交还给池子。
        imageLoader.memoryCache?.clear()
        // 2) Coil 磁盘缓存 + 其它已知缓存子目录：整目录删，下次访问自动重建。
        imageLoader.diskCache?.clear()
        File(context.cacheDir, "EmbeddedCovers").deleteRecursively()
        File(context.cacheDir, "uploads").deleteRecursively()
        context.cacheDir.listFiles()?.forEach { child ->
            if (child.isDirectory) child.deleteRecursively()
        }
        before - sizeBytes(context)
    }

    /** 字节数格式化为人类可读（MB / GB）。 */
    fun format(sizeBytes: Long): String = when {
        sizeBytes <= 0 -> "0 MB"
        sizeBytes < 1024L * 1024L -> "< 1 MB"
        sizeBytes < 1024L * 1024L * 1024L ->
            "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(sizeBytes / (1024.0 * 1024.0 * 1024.0))
    }

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists() || !dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
