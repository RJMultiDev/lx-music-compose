package cn.guoyujie666.music.compose

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Future: initialize logging, crash reporting, etc.
    }
}
