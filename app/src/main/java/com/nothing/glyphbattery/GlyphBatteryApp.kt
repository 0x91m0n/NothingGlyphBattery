package com.nothing.glyphbattery

import android.app.Application
import com.nothing.glyphbattery.data.AppLanguageManager
import com.nothing.glyphbattery.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlyphBatteryApp : Application() {

    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            val language = withContext(Dispatchers.IO) {
                settingsStore.settings.first().language
            }
            AppLanguageManager.apply(this@GlyphBatteryApp, language)
        }
    }
}
