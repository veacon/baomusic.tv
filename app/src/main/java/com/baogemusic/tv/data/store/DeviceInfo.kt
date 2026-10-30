package com.baogemusic.tv.data.store

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * 本机设备信息 + 硬件解码能力检测。
 *
 * 思路参考 MyTVB 等 TV 播放器的做法：遍历系统 [MediaCodecList]，挑出「解码器（非编码器）」
 * 且由硬件实现（[MediaCodecInfo.isHardwareAccelerated]，低版本用 OMX 名称启发式兜底）的那些，
 * 把它们支持的音视频 MIME 映射成人类可读格式名（H.264 / H.265 / AV1 / AAC / FLAC …）。
 * 这样用户不必盲猜，直接在「关于与更新」里看到本机到底能硬解哪些格式。
 */
object DeviceInfo {

    data class Row(val label: String, val value: String)

    /** 设备基础信息：型号 / 制造商 / 系统版本 / 内存 / 屏幕。 */
    fun deviceRows(context: Context): List<Row> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

        val dm = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)

        return listOf(
            Row("设备型号", "${Build.BRAND} ${Build.MODEL}".replaceFirstChar { it.uppercase() }),
            Row("制造商", Build.MANUFACTURER.replaceFirstChar { it.uppercase() }),
            Row("系统版本", "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）"),
            Row("运行内存", "%.2f GB".format(totalRamGb)),
            Row("屏幕分辨率", "${dm.widthPixels} × ${dm.heightPixels}"),
            Row("屏幕密度", "${dm.densityDpi} dpi"),
        )
    }

    /** 本机可硬解的音视频格式（友好的中文/英文简称）。 */
    fun hardwareFormats(): List<String> {
        val found = LinkedHashMap<String, String>() // MIME -> 友好名，保序去重
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (isEncoder(info)) continue
            if (!isHardware(info)) continue
            for (type in info.supportedTypes) {
                FRIENDLY[type]?.let { found.putIfAbsent(type, it) }
            }
        }
        return found.values.toList()
    }

    /** MediaCodecInfo.isHardwareAccelerated() 需要 API 29+；低版本用 OMX 名称启发式。 */
    private fun isHardware(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= 29) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !name.startsWith("omx.google") && !name.contains("android")
        }
    }

    /** MediaCodecInfo.isEncoder() 同样需要 API 29+；低版本从名称推断（含 "encoder" 即编码器）。 */
    private fun isEncoder(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= 29) {
            info.isEncoder
        } else {
            info.name.contains("encoder", ignoreCase = true)
        }
    }

    /** MIME → 友好格式名。只关心音乐 / 视频播放常用的那几个。 */
    private val FRIENDLY = mapOf(
        // 视频
        "video/avc" to "H.264",
        "video/hevc" to "H.265 / HEVC",
        "video/x-vnd.on2.vp9" to "VP9",
        "video/av01" to "AV1",
        "video/mp4v-es" to "MPEG-4",
        "video/x-ms-wmv" to "WMV",
        "video/mpeg2" to "MPEG-2",
        "video/dolby-vision" to "杜比视界",
        // 音频
        "audio/mp4a-latm" to "AAC",
        "audio/mpeg" to "MP3",
        "audio/flac" to "FLAC",
        "audio/vorbis" to "Vorbis",
        "audio/opus" to "Opus",
        "audio/alac" to "ALAC",
        "audio/amr-wb" to "AMR-WB",
        "audio/amr-nb" to "AMR-NB",
        "audio/g711-alaw" to "G.711",
        "audio/g711-mlaw" to "G.711",
    )
}
