package com.baogemusic.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
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
    // 字体只在「歌词」里单独应用（见 PlayerScreen / LyricPage），此处不再全局注入，
    // 以免选中字体后整个 App 的界面文字都跟着变。
    MaterialTheme(
        colorScheme = WalkmanColorScheme,
        typography = WalkmanTypography,
    ) {
        content()
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
