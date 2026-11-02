package com.baogemusic.tv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.baogemusic.tv.ui.RootScreen
import com.baogemusic.tv.ui.theme.AppColors
import com.baogemusic.tv.ui.theme.WalkmanTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WalkmanTvTheme {
                // Keep the TV screen awake while audio/video is playing.
                val playing = App.container.playbackController.state.collectAsState().value.isPlaying
                LaunchedEffect(playing) {
                    if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                RootScreen(modifier = Modifier.fillMaxSize().background(AppColors.BgDeep))
            }
        }
    }

    /**
     * Key dispatch, in order:
     *  1. screen-level interceptor（播放页自动隐藏控件的唤出，见 PlayerScreen）；
     *  2. 遥控器媒体键 —— 上一首 / 播放暂停 / 下一首 / 播放 / 暂停。MediaSessionService 暂未启用，
     *     没有 MediaSession 时这些实体键在应用内无人响应，这里直接接管；只处理 DOWN 首击，
     *     DOWN/UP 都吞掉避免泄漏到别处。
     *  3. 其余交给 Compose。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (App.container.events.keyInterceptor?.invoke(event) == true) return true
        if (event.keyCode in MEDIA_KEYS) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val c = App.container.playbackController
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> c.prev()
                    KeyEvent.KEYCODE_MEDIA_NEXT -> c.next()
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> c.togglePlay()
                    KeyEvent.KEYCODE_MEDIA_PLAY -> if (!c.state.value.isPlaying) c.togglePlay()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> if (c.state.value.isPlaying) c.togglePlay()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_TV_CONTENTS_MENU,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_GUIDE -> {
                App.container.events.postMenuKey()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        /** 遥控器上的媒体实体键（各厂商键值不一，这一组是标准集合）。 */
        val MEDIA_KEYS = setOf(
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
        )
    }
}
