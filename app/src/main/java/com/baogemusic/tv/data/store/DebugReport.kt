package com.baogemusic.tv.data.store

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 调试信息 / 崩溃日志本地收集器。
 *
 * 崩溃时把堆栈写到 `filesDir/debug/crash-*.txt`；「关于与更新」里可生成一份汇总报告，
 * 通过内置 HTTP 服务（LocalServer 的 `/debug` 路由）扫码下载，便于快速定位闪退。
 */
object DebugReport {

    private const val DIR = "debug"
    private const val MAX_CRASH_FILES = 10

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { runCatching { mkdirs() } }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    /** 记录一次未捕获异常。返回写出的文件（失败时 null）。 */
    fun recordCrash(context: Context, thread: Thread, throwable: Throwable): File? = runCatching {
        val f = File(dir(context), "crash-${stamp()}.txt")
        f.writeText(
            buildString {
                appendLine("==== 宝听 崩溃报告 ====")
                appendLine("时间: ${Date()}")
                appendLine("线程: ${thread.name} (id=${thread.id})")
                appendLine("设备: ${Build.BRAND} ${Build.MODEL} / ${Build.MANUFACTURER}")
                appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("ABI : ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("指纹: ${Build.FINGERPRINT}")
                appendLine()
                appendLine("---- 堆栈 ----")
                appendLine(throwable.stackTraceToString())
                var cause = throwable.cause
                var depth = 1
                while (cause != null && depth < 10) {
                    appendLine()
                    appendLine("---- cause #$depth ----")
                    appendLine(cause.stackTraceToString())
                    cause = cause.cause
                    depth++
                }
            },
        )
        // 只保留最近若干份崩溃日志，避免无限增长。
        runCatching {
            dir(context).listFiles { file -> file.name.startsWith("crash-") }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_CRASH_FILES)
                ?.forEach { it.delete() }
        }
        f
    }.getOrNull()

    /** 生成汇总报告（设备信息 + 设置 + 崩溃日志 + logcat 摘要），返回文件。 */
    fun build(context: Context, settingsJson: String): File = runCatching {
        val text = buildString {
            appendLine("==== 宝听 调试信息 ====")
            appendLine("时间: ${Date()}")
            appendLine("包名: ${context.packageName}")
            runCatching {
                @Suppress("DEPRECATION")
                val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                appendLine("版本: ${pi.versionName} (${pi.versionCode})")
            }
            appendLine("设备: ${Build.BRAND} ${Build.MODEL} / ${Build.MANUFACTURER}")
            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI : ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("指纹: ${Build.FINGERPRINT}")
            appendLine()
            appendLine("---- 设备信息 ----")
            runCatching {
                DeviceInfo.deviceRows(context).forEach { appendLine("${it.label}: ${it.value}") }
            }
            appendLine()
            appendLine("---- 硬件音频解码 ----")
            runCatching {
                val formats = DeviceInfo.hardwareFormats(audioOnly = true)
                appendLine(if (formats.isEmpty()) "未检测到" else formats.joinToString())
            }
            appendLine()
            appendLine("---- 当前设置 (JSON) ----")
            appendLine(settingsJson)
            appendLine()
            appendLine("---- 崩溃日志 ----")
            val crashFiles = dir(context).listFiles { f -> f.name.startsWith("crash-") }
                ?.sortedByDescending { it.name }
                .orEmpty()
            if (crashFiles.isEmpty()) {
                appendLine("（暂无崩溃记录）")
            } else {
                crashFiles.forEach { f ->
                    appendLine()
                    appendLine("### ${f.name}")
                    runCatching { appendLine(f.readText()) }
                }
            }
            appendLine()
            appendLine("---- logcat（最近，尽力而为）----")
            appendLine(runCatching { readLogcat() }.getOrDefault("（无权限或无输出）"))
        }
        val f = File(dir(context), "debug-report.txt")
        f.writeText(text)
        f
    }.getOrElse {
        // 极端情况下（如磁盘满）也给出一个带异常信息的文件，便于下载排查。
        val f = File(dir(context), "debug-report.txt")
        runCatching { f.writeText("生成调试信息失败：\n${it.stackTraceToString()}") }
        f
    }

    private fun readLogcat(): String {
        val proc = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "1500")
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        runCatching { proc.waitFor(3, TimeUnit.SECONDS) }
        return out.takeIf { it.isNotBlank() } ?: "（无输出）"
    }
}
