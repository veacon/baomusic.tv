package com.baogemusic.tv.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.baogemusic.tv.R
import com.baogemusic.tv.data.store.AppFontFamily
import com.baogemusic.tv.ui.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全局字体：跟随「外观 → 字体」设置，作用于整个 App 的所有文字（不只是歌词）。
 *
 * 实现方式是把它注入 [androidx.tv.material3.LocalTextStyle]（以及 material3 的那一份），
 * 这样界面里所有 `Text(...)` 都会继承到该 fontFamily，不需要每个页面单独传参。
 */
@Composable
fun rememberAppFontFamily(): FontFamily? {
    val settings by appContainer.settingsStore.settings.collectAsState()
    val customId = settings.customFontId.takeIf { settings.appFontFamily == AppFontFamily.CUSTOM }
    // 用户字体要从磁盘解析 Typeface（文件可能十几 MB），放到 IO 线程读，拿到后再上屏。
    var userTypeface by remember(customId) { mutableStateOf<Typeface?>(null) }
    LaunchedEffect(customId) {
        userTypeface = customId?.let { withContext(Dispatchers.IO) { appContainer.fontStore.typeface(it) } }
    }
    return when (settings.appFontFamily) {
        AppFontFamily.SYSTEM -> null
        AppFontFamily.MINGMEI -> FontFamily(Font(R.font.aa_ming_mei_spring))
        AppFontFamily.XIAODOU -> FontFamily(Font(R.font.xiaodou_poetry))
        AppFontFamily.CUSTOM -> userTypeface?.let { FontFamily(it) }
    }
}

/** 单个用户字体的 [FontFamily]（设置页里逐项预览用）。未加载完成时返回 null。 */
@Composable
fun rememberUserFontFamily(fontId: String?): FontFamily? {
    if (fontId == null) return null
    var typeface by remember(fontId) { mutableStateOf<Typeface?>(null) }
    LaunchedEffect(fontId) {
        typeface = withContext(Dispatchers.IO) { appContainer.fontStore.typeface(fontId) }
    }
    return typeface?.let { FontFamily(it) }
}

/** 内置「Aa祝你我明媚向春天」的 [FontFamily]（设置页预览用）。 */
@Composable
fun mingMeiFontFamily(): FontFamily = FontFamily(Font(R.font.aa_ming_mei_spring))

/** 内置「小抖动侠情书」的 [FontFamily]（设置页预览用）。 */
@Composable
fun xiaodouFontFamily(): FontFamily = FontFamily(Font(R.font.xiaodou_poetry))
