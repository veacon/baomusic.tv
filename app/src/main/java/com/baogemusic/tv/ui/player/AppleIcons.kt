package com.baogemusic.tv.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Apple Music 网页播放器（music.apple.com）风格的按钮图标。
 *
 * 形制按其 SF Symbols 图标字形手绘还原：
 *  - 播放：实心圆角三角（play.fill）；暂停：两根圆角竖条（pause.fill）；
 *  - 上一首 / 下一首：双三角（◀◀ / ▶▶，快退 / 快进形制）；
 *  - 随机：两支交叉箭头（shuffle）；队列：三条横线（Playing Next 列表）；
 *  - 喜欢：心形（heart / heart.fill）；均衡器：三根竖推子（EQ 推子形）。
 *
 * 全部基于 24×24 视口；描边形图标用 Round 端点/接头，与 SF Symbols 的细线观感一致。
 * 颜色统一画成黑色，交给 `Icon(tint=…)` 的 ColorFilter 整体染色。
 */
internal object AppleIcons {

    /** play.fill —— 实心圆角三角。 */
    val Play: ImageVector by lazy {
        buildVector("apple.play") {
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7.6f, 4.9f)
                lineTo(19f, 12f)
                lineTo(7.6f, 19.1f)
                close()
            }
        }
    }

    /** pause.fill —— 两根圆角竖条。 */
    val Pause: ImageVector by lazy {
        buildVector("apple.pause") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 5f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(8.4f, 6.2f)
                lineTo(8.4f, 17.8f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 5f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(15.6f, 6.2f)
                lineTo(15.6f, 17.8f)
            }
        }
    }

    /** 上一首 —— 双左指三角（◀◀）。 */
    val Previous: ImageVector by lazy {
        buildVector("apple.previous") {
            // 左三角
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(11f, 5.6f)
                lineTo(3f, 12f)
                lineTo(11f, 18.4f)
                close()
            }
            // 右三角
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20f, 5.6f)
                lineTo(12f, 12f)
                lineTo(20f, 18.4f)
                close()
            }
        }
    }

    /** 下一首 —— 双右指三角（▶▶）。 */
    val Next: ImageVector by lazy {
        buildVector("apple.next") {
            // 左三角
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 5.6f)
                lineTo(12f, 12f)
                lineTo(4f, 18.4f)
                close()
            }
            // 右三角
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(13f, 5.6f)
                lineTo(21f, 12f)
                lineTo(13f, 18.4f)
                close()
            }
        }
    }

    /** heart —— 空心（未收藏）。 */
    val Heart: ImageVector by lazy {
        buildVector("apple.heart") {
            heartPath(fill = null)
        }
    }

    /** heart.fill —— 实心（已收藏）。 */
    val HeartFilled: ImageVector by lazy {
        buildVector("apple.heart.fill") {
            heartPath(fill = SolidColor(Color.Black))
        }
    }

    /** Apple 标志 —— 被咬一口的苹果官方 logo（右侧缺口 + 顶部叶梗）。 */
    val AppleLogo: ImageVector by lazy {
        buildVector("apple.logo") {
            // 采用标准 Apple logo 轮廓：顶部叶梗 + 左右两瓣 + 右侧被咬一口的圆弧缺口。
            path(fill = SolidColor(Color.Black)) {
                moveTo(16.365f, 12.824f)
                curveTo(16.351f, 10.056f, 18.216f, 8.511f, 18.216f, 8.511f)
                curveTo(16.862f, 6.634f, 14.736f, 6.382f, 14.736f, 6.382f)
                curveTo(13.001f, 6.208f, 11.282f, 7.421f, 10.386f, 7.421f)
                curveTo(9.469f, 7.421f, 8.039f, 6.398f, 6.556f, 6.427f)
                curveTo(4.646f, 6.457f, 2.894f, 7.549f, 1.913f, 9.245f)
                curveTo(-0.085f, 12.709f, 1.365f, 17.76f, 3.325f, 20.539f)
                curveTo(4.287f, 21.955f, 5.422f, 23.534f, 6.931f, 23.473f)
                curveTo(8.402f, 23.413f, 8.957f, 22.527f, 10.674f, 22.527f)
                curveTo(12.366f, 22.527f, 12.878f, 23.473f, 14.426f, 23.441f)
                curveTo(15.996f, 23.413f, 16.983f, 22.011f, 17.921f, 20.587f)
                curveTo(19.005f, 19.014f, 19.451f, 17.484f, 19.48f, 17.384f)
                curveTo(19.447f, 17.368f, 16.382f, 16.203f, 16.365f, 12.824f)
                close()
                moveTo(13.66f, 4.411f)
                curveTo(14.455f, 3.44f, 14.991f, 2.116f, 14.852f, 0.78f)
                curveTo(13.695f, 0.828f, 12.271f, 1.563f, 11.45f, 2.561f)
                curveTo(10.724f, 3.442f, 10.077f, 4.803f, 10.235f, 6.11f)
                curveTo(11.507f, 6.208f, 12.818f, 5.433f, 13.66f, 4.411f)
                close()
            }
        }
    }

    /**
     * 「O」圆环标志 —— Apple Music 页控制行最左端（上一首左边），点一下切回标准界面。
     * 画成一个正圆描边（四段三次贝塞尔近似），视觉上就是字母 O。
     */
    val O: ImageVector by lazy {
        buildVector("apple.o") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12f, 4.2f)
                curveTo(16.31f, 4.2f, 19.8f, 7.69f, 19.8f, 12f)
                curveTo(19.8f, 16.31f, 16.31f, 19.8f, 12f, 19.8f)
                curveTo(7.69f, 19.8f, 4.2f, 16.31f, 4.2f, 12f)
                curveTo(4.2f, 7.69f, 7.69f, 4.2f, 12f, 4.2f)
                close()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun buildVector(
        name: String,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

    /** SF Symbols 心形轮廓（与 heart / heart.fill 同一路径）。 */
    private fun ImageVector.Builder.heartPath(fill: SolidColor?) {
        path(
            fill = fill,
            stroke = if (fill == null) SolidColor(Color.Black) else null,
            strokeLineWidth = if (fill == null) 2f else 0f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 21f)
            curveTo(12f, 21f, 4f, 15.2f, 4f, 9.6f)
            curveTo(4f, 6.6f, 6.4f, 4.4f, 9f, 4.4f)
            curveTo(10.7f, 4.4f, 11.6f, 5.4f, 12f, 6.4f)
            curveTo(12.4f, 5.4f, 13.3f, 4.4f, 15f, 4.4f)
            curveTo(17.6f, 4.4f, 20f, 6.6f, 20f, 9.6f)
            curveTo(20f, 15.2f, 12f, 21f, 12f, 21f)
            close()
        }
    }
}
