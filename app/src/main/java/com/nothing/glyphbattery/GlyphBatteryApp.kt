package com.nothing.glyphbattery

import android.app.Application
import android.content.res.Configuration
import com.nothing.glyphbattery.data.SettingsStore
import com.nothing.glyphbattery.model.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

class GlyphBatteryApp : Application() {

    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        applyLanguage()
    }

    fun applyLanguage() {
        val settings = runBlocking { settingsStore.settings.first() }
        if (settings.language != AppLanguage.SYSTEM) {
            val locale = Locale(settings.language.code)
            Locale.setDefault(locale)
            val config = Configuration(resources.configuration)
            config.setLocale(locale)
            createConfigurationContext(config)
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }
}
