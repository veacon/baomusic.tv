@file:Suppress("DEPRECATION")

package com.baogemusic.tv.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.max

/**
 * Coil [Transformation] that pre-scales the input bitmap and blurs the result via
 * [ScriptIntrinsicBlur]. Equivalent to what `io.coil-kt:coil-transformations:BlurTransformation`
 * used to provide before that artifact stopped tracking the coil-compose 2.x releases.
 *
 * Runs on RenderScript so it works back to API 21 — covers our minSdk + the 32-bit ARM
 * Android-10 TVs where [androidx.compose.ui.draw.blur] (API 31+) isn't available.
 *
 * RenderScript was deprecated in API 31 but still ships (and works) on later platforms; we
 * suppress the deprecation warnings file-wide since there is no equivalent path that covers
 * API 21–30 + 31+ uniformly.
 *
 * @param radius   Gaussian radius (0 < r ≤ 25 — RenderScript's hard cap).
 * @param sampling Pre-downscale factor (≥1). Bigger value = faster + softer.
 */
class BlurTransformation(
    private val context: Context,
    private val radius: Float = 20f,
    private val sampling: Float = 2f,
) : Transformation {

    override val cacheKey: String = "${javaClass.name}-radius=$radius-sampling=$sampling"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val srcW = input.width.toFloat()
        val srcH = input.height.toFloat()
        // 目标短边封顶 720px：1.5GB 内存的老电视上，模糊只需要「一团色块」，
        // 按原图尺寸糊一张 1080p 纯属浪费 native 内存（RenderScript 崩起来是直接 SIGSEGV）。
        val cap = 720f
        val sampleBySize = max(srcW, srcH) / cap
        val s = max(sampling.coerceAtLeast(1f), sampleBySize.coerceAtLeast(1f))
        val dstW = max(1, (srcW / s).toInt())
        val dstH = max(1, (srcH / s).toInt())

        // 1) Downscale into ARGB_8888 — RenderScript blur needs uchar4 input.
        val scaled = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        Canvas(scaled).apply {
            scale(1f / s, 1f / s)
            drawBitmap(input, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // 2) Apply Gaussian via RenderScript.
        // 复用同一个 RenderScript 上下文：每张封面都 new 一个既慢又容易把 native 内存耗光，
        // 而 RenderScript 不是线程安全的，所以整段加锁串行化。
        synchronized(RS_LOCK) {
            var inAlloc: Allocation? = null
            var outAlloc: Allocation? = null
            var script: ScriptIntrinsicBlur? = null
            try {
                val rs = rsContext ?: RenderScript.create(context).also { rsContext = it }
                inAlloc = Allocation.createFromBitmap(rs, scaled)
                outAlloc = Allocation.createTyped(rs, inAlloc.type)
                script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
                script.setRadius(radius.coerceIn(0.1f, 25f))
                script.setInput(inAlloc)
                script.forEach(outAlloc)
                outAlloc.copyTo(scaled)
            } catch (t: Throwable) {
                // 模糊失败就退回未模糊的降采样图，绝不让它把播放带崩。
                return scaled
            } finally {
                script?.destroy()
                outAlloc?.destroy()
                inAlloc?.destroy()
            }
        }
        return scaled
    }

    private companion object {
        private val RS_LOCK = Any()
        @Volatile private var rsContext: RenderScript? = null
    }
}
