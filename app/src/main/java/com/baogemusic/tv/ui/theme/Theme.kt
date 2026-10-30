package com.baogemusic.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

private val WalkmanColorScheme: ColorScheme = darkColorScheme(
    primary = AppColors.AccentGreen,
    onPrimary = AppColors.BgDeep,
    secondary = AppColors.AccentGreen,
    background = AppColors.BgDeep,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.BgPanel,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.Card,
    onSurfaceVariant = AppColors.TextSecondary,
    border = AppColors.FocusBorder,
)

/** Spacing / radius tokens (mirrors DS.Spacing / DS.Radius). */
object Dimens {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    val radiusSmall = 8.dp
    val radiusMedium = 12.dp
    val radiusLarge = 18.dp
    val radiusXLarge = 28.dp
}

@Composable
fun WalkmanTvTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme() // TV is always dark here
    // 全局字体：设置页「外观 → 字体」选中的字体，整个 App 的文字都跟着变。
    val appFont = rememberAppFontFamily()
    val typography = remember(appFont) { WalkmanTypography.withFontFamily(appFont) }
    MaterialTheme(
        colorScheme = WalkmanColorScheme,
        typography = typography,
    ) {
        // tv-material3 与 material3 各有一份 LocalTextStyle，都要注入，否则 AlertDialog /
        // TextField 里的文字会退回系统字体。
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.tv.material3.LocalTextStyle provides
                androidx.tv.material3.LocalTextStyle.current.copy(fontFamily = appFont),
            androidx.compose.material3.LocalTextStyle provides
                androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = appFont),
            content = content,
        )
    }
}

private val WalkmanTypography = Typography().let { base ->
    base.copy(
        displayMedium = base.displayMedium.copy(fontSize = 32.sp),
        titleLarge = base.titleLarge.copy(fontSize = 22.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 13.sp),
    )
}

/** 把选中的字体铺到整套排版样式上——对话框、输入框这些走 typography 的控件才不会漏掉。 */
private fun Typography.withFontFamily(family: androidx.compose.ui.text.font.FontFamily?): Typography =
    if (family == null) this else copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
