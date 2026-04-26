package com.nothing.glyphbattery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.nothing.glyphbattery.model.AnimationMode
import com.nothing.glyphbattery.model.AppLanguage
import com.nothing.glyphbattery.model.AutoOffTimer
import com.nothing.glyphbattery.model.ChargeCompleteAction
import com.nothing.glyphbattery.model.FillDirection
import com.nothing.glyphbattery.model.FillMode
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.GlyphZone
import com.nothing.glyphbattery.model.ServiceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "glyph_settings")

class SettingsStore(private val context: Context) {

    companion object {
        private val FILL_MODE = stringPreferencesKey("fill_mode")
        private val FILL_DIRECTION = stringPreferencesKey("fill_direction")
        private val SELECTED_ZONE = stringPreferencesKey("selected_zone")
        private val ANIMATION_MODE = stringPreferencesKey("animation_mode")
        private val LANGUAGE = stringPreferencesKey("language")
        private val BRIGHTNESS = intPreferencesKey("brightness")
        private val ANIMATION_SPEED = intPreferencesKey("animation_speed")
        private val AUTO_OFF_TIMER = stringPreferencesKey("auto_off_timer")
        private val CUSTOM_AUTO_OFF_MINUTES = intPreferencesKey("custom_auto_off_minutes")
        private val CHARGE_COMPLETE_ACTION = stringPreferencesKey("charge_complete_action")
        private val CHARGE_COMPLETE_ZONE = stringPreferencesKey("charge_complete_zone")
        private val SERVICE_MODE = stringPreferencesKey("service_mode")
    }

    val settings: Flow<GlyphSettings> = context.dataStore.data.map { prefs ->
        GlyphSettings(
            fillMode = prefs[FILL_MODE]?.let { runCatching { FillMode.valueOf(it) }.getOrNull() }
                ?: FillMode.CIRCULAR,
            fillDirection = prefs[FILL_DIRECTION]?.let { runCatching { FillDirection.valueOf(it) }.getOrNull() }
                ?: FillDirection.ABC,
            selectedZone = prefs[SELECTED_ZONE]?.let { runCatching { GlyphZone.valueOf(it) }.getOrNull() }
                ?: GlyphZone.ZONE_A,
            animationMode = prefs[ANIMATION_MODE]?.let { runCatching { AnimationMode.valueOf(it) }.getOrNull() }
                ?: AnimationMode.NONE,
            language = prefs[LANGUAGE]?.let { code ->
                AppLanguage.entries.find { it.code == code }
            } ?: AppLanguage.SYSTEM,
            brightness = prefs[BRIGHTNESS] ?: 100,
            animationSpeed = prefs[ANIMATION_SPEED] ?: 100,
            autoOffTimer = prefs[AUTO_OFF_TIMER]?.let { runCatching { AutoOffTimer.valueOf(it) }.getOrNull() }
                ?: AutoOffTimer.OFF,
            customAutoOffMinutes = prefs[CUSTOM_AUTO_OFF_MINUTES] ?: 45,
            chargeCompleteAction = prefs[CHARGE_COMPLETE_ACTION]?.let { runCatching { ChargeCompleteAction.valueOf(it) }.getOrNull() }
                ?: ChargeCompleteAction.TURN_OFF,
            chargeCompleteZone = prefs[CHARGE_COMPLETE_ZONE]?.let { runCatching { GlyphZone.valueOf(it) }.getOrNull() }
                ?: GlyphZone.ZONE_A,
            serviceMode = prefs[SERVICE_MODE]?.let { runCatching { ServiceMode.valueOf(it) }.getOrNull() }
                ?: ServiceMode.MANUAL
        )
    }

    suspend fun updateFillMode(mode: FillMode) {
        context.dataStore.edit { it[FILL_MODE] = mode.name }
    }

    suspend fun updateFillDirection(direction: FillDirection) {
        context.dataStore.edit { it[FILL_DIRECTION] = direction.name }
    }

    suspend fun updateSelectedZone(zone: GlyphZone) {
        context.dataStore.edit { it[SELECTED_ZONE] = zone.name }
    }

    suspend fun updateAnimationMode(mode: AnimationMode) {
        context.dataStore.edit { it[ANIMATION_MODE] = mode.name }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.dataStore.edit { it[LANGUAGE] = language.code }
    }

    suspend fun updateBrightness(brightness: Int) {
        context.dataStore.edit { it[BRIGHTNESS] = brightness.coerceIn(10, 100) }
    }

    suspend fun updateAnimationSpeed(speed: Int) {
        context.dataStore.edit { it[ANIMATION_SPEED] = speed.coerceIn(25, 200) }
    }

    suspend fun updateAutoOffTimer(timer: AutoOffTimer) {
        context.dataStore.edit { it[AUTO_OFF_TIMER] = timer.name }
    }

    suspend fun updateCustomAutoOffMinutes(minutes: Int) {
        context.dataStore.edit { it[CUSTOM_AUTO_OFF_MINUTES] = minutes.coerceIn(1, 1440) }
    }

    suspend fun updateChargeCompleteAction(action: ChargeCompleteAction) {
        context.dataStore.edit { it[CHARGE_COMPLETE_ACTION] = action.name }
    }

    suspend fun updateChargeCompleteZone(zone: GlyphZone) {
        context.dataStore.edit { it[CHARGE_COMPLETE_ZONE] = zone.name }
    }

    suspend fun updateServiceMode(mode: ServiceMode) {
        context.dataStore.edit { it[SERVICE_MODE] = mode.name }
    }

}
