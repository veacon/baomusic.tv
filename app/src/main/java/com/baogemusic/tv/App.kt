package com.baogemusic.tv

import android.app.Application
import com.baogemusic.tv.data.store.DebugReport
import com.baogemusic.tv.di.AppContainer
import com.whl.quickjs.android.QuickJSLoader

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 崩溃兜底：先把堆栈写到本地（供「关于 → 调试信息」扫码下载），再交给系统默认处理。
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { DebugReport.recordCrash(this, thread, error) }
            previousHandler?.uncaughtException(thread, error)
        }
        // Load the QuickJS native lib once for the whole process (custom-source engine).
        QuickJSLoader.init()
        container = AppContainer(this)
        // ExoPlayer must be created on the main thread.
        container.initPlayback()
        container.bootstrap()
    }

    companion object {
        lateinit var instance: App
            private set
        lateinit var container: AppContainer
            private set
    }
}
