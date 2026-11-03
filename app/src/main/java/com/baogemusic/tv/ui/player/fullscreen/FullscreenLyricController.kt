package com.baogemusic.tv.ui.player.fullscreen

import android.view.KeyEvent
import com.baogemusic.tv.data.store.FullscreenLyricFx
import com.baogemusic.tv.playback.LyricLine
import com.baogemusic.tv.playback.LyricParser

/**
 * 两套新全屏歌词（墨卷 / 声轨）的按键处理。
 *
 * 墨卷是纯展示（竖排 + 逐字落墨），不占用遥控器；声轨只保留一件事：
 *  - ↑ / ↓ 跳到上一句 / 下一句的起点。
 * 其余按键（含 OK，短按长按都一样）一律交回播放页 —— OK 统一用来唤出底部控制栏。
 */
class FullscreenLyricController {

    /**
     * 处理一个按键。
     *
     * @param livePositionMs 播放器的媒体时钟（毫秒级连续），用于算「当前行」。
     * @param onSeek 绝对跳转目标（毫秒）。
     * @return true = 该按键已被特效消费。
     */
    fun onKey(
        fx: FullscreenLyricFx,
        e: KeyEvent,
        lyrics: List<LyricLine>,
        livePositionMs: () -> Long,
        onSeek: (Long) -> Unit,
    ): Boolean {
        if (fx != FullscreenLyricFx.TRACK_BED) return false
        if (e.action != KeyEvent.ACTION_DOWN) {
            return e.keyCode == KeyEvent.KEYCODE_DPAD_UP || e.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
        }
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                val active = LyricParser.activeIndex(livePositionMs() / 1000.0, lyrics)
                val dir = if (e.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1
                val target = (active + dir).coerceIn(0, (lyrics.size - 1).coerceAtLeast(0))
                val t = lyrics.getOrNull(target)?.time ?: return true
                onSeek((t * 1000).toLong().coerceAtLeast(0L))
                return true
            }
        }
        return false
    }
}
