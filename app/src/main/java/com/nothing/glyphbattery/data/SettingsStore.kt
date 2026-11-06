package com.nothing.glyphbattery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.nothing.glyphbattery.model.AnimationMode
import com.nothing.glyphbattery.model.AppLanguage
import com.nothing.glyphbattery.model.AutoOffTimer
import com.nothing.glyphbattery.model.ChargeCompleteAction
import com.nothing.glyphbattery.model.ChargeCompleteTimerPreset
import com.nothing.glyphbattery.model.ChargeCompleteTimerRuntime
import com.nothing.glyphbattery.model.FillDirection
import com.nothing.glyphbattery.model.FillMode
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.GlyphZone
import com.nothing.glyphbattery.model.ServiceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "glyph_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class SettingsStore(context: Context) {
    private val context = context.applicationContext

    companion object {
        private val FILL_MODE = stringPreferencesKey("fill_mode")
        private val FILL_DIRECTION = stringPreferencesKey("fill_direction")
        private val SELECTED_ZONE = stringPreferencesKey("selected_zone")
        private val ANIMATION_MODE = stringPreferencesKey("animation_mode")
        private val CHARGING_ANIMATION_MODE = stringPreferencesKey("charging_animation_mode")
        private val BATTERY_BRIGHTNESS = intPreferencesKey("battery_brightness")
        private val BATTERY_FILL_MODE = stringPreferencesKey("battery_fill_mode")
        private val BATTERY_FILL_DIRECTION = stringPreferencesKey("battery_fill_direction")
        private val BATTERY_SELECTED_ZONE = stringPreferencesKey("battery_selected_zone")
        private val LANGUAGE = stringPreferencesKey("language")
        private val BRIGHTNESS = intPreferencesKey("brightness")
        private val ANIMATION_SPEED = intPreferencesKey("animation_speed")
        private val AUTO_OFF_TIMER = stringPreferencesKey("auto_off_timer")
        private val CUSTOM_AUTO_OFF_MINUTES = intPreferencesKey("custom_auto_off_minutes")
        private val CHARGE_COMPLETE_ACTION = stringPreferencesKey("charge_complete_action")
        private val CHARGE_COMPLETE_ZONE = stringPreferencesKey("charge_complete_zone")
        private val CHARGE_FULL_THRESHOLD = intPreferencesKey("charge_full_threshold")
        private val CHARGE_COMPLETE_TIMER_PRESET = stringPreferencesKey("charge_complete_timer_preset")
        private val CUSTOM_CHARGE_COMPLETE_TIMER_MINUTES = intPreferencesKey("custom_charge_complete_timer_minutes")
        private val CHARGE_COMPLETE_DEADLINE_EPOCH_MILLIS = longPreferencesKey("charge_complete_deadline_epoch_millis")
        private val CHARGE_COMPLETE_TIMER_CONFIGURATION = stringPreferencesKey("charge_complete_timer_configuration")
        private val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        private val SERVICE_MODE = stringPreferencesKey("service_mode")
        private val SUPPORTED_CHARGE_THRESHOLDS = setOf(70, 80, 90, 100)
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
            batteryAnimationMode = prefs[CHARGING_ANIMATION_MODE]?.let { runCatching { AnimationMode.valueOf(it) }.getOrNull() }
                ?: AnimationMode.NONE,
            batteryBrightness = (prefs[BATTERY_BRIGHTNESS] ?: 100).coerceIn(10, 100),
            batteryFillMode = prefs[BATTERY_FILL_MODE]?.let { runCatching { FillMode.valueOf(it) }.getOrNull() }
                ?: FillMode.CIRCULAR,
            batteryFillDirection = prefs[BATTERY_FILL_DIRECTION]?.let { runCatching { FillDirection.valueOf(it) }.getOrNull() }
                ?: FillDirection.ABC,
            batterySelectedZone = prefs[BATTERY_SELECTED_ZONE]?.let { runCatching { GlyphZone.valueOf(it) }.getOrNull() }
                ?: GlyphZone.ZONE_A,
            language = prefs[LANGUAGE]?.let { code ->
                AppLanguage.entries.find { it.code == code }
            } ?: AppLanguage.SYSTEM,
            brightness = (prefs[BRIGHTNESS] ?: 100).coerceIn(10, 100),
            animationSpeed = (prefs[ANIMATION_SPEED] ?: 100).coerceIn(25, 200),
            autoOffTimer = prefs[AUTO_OFF_TIMER]?.let { runCatching { AutoOffTimer.valueOf(it) }.getOrNull() }
                ?: AutoOffTimer.OFF,
            customAutoOffMinutes = (prefs[CUSTOM_AUTO_OFF_MINUTES] ?: 45).coerceIn(1, 1440),
            chargeCompleteAction = prefs[CHARGE_COMPLETE_ACTION]?.let { runCatching { ChargeCompleteAction.valueOf(it) }.getOrNull() }
                ?: ChargeCompleteAction.TURN_OFF,
            chargeCompleteZone = prefs[CHARGE_COMPLETE_ZONE]?.let { runCatching { GlyphZone.valueOf(it) }.getOrNull() }
                ?: GlyphZone.ZONE_A,
            chargeFullThreshold = (prefs[CHARGE_FULL_THRESHOLD] ?: 100)
                .takeIf(SUPPORTED_CHARGE_THRESHOLDS::contains)
                ?: 100,
            chargeCompleteTimerPreset = prefs[CHARGE_COMPLETE_TIMER_PRESET]?.let {
                runCatching { ChargeCompleteTimerPreset.valueOf(it) }.getOrNull()
            } ?: ChargeCompleteTimerPreset.OFF,
            customChargeCompleteTimerMinutes =
                (prefs[CUSTOM_CHARGE_COMPLETE_TIMER_MINUTES] ?: 30).coerceIn(1, 1440),
            serviceMode = prefs[SERVICE_MODE]?.let { runCatching { ServiceMode.valueOf(it) }.getOrNull() }
                ?: ServiceMode.MANUAL
        )
    }.distinctUntilChanged()

    val developerMode: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[DEVELOPER_MODE] ?: false }
        .distinctUntilChanged()

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

    suspend fun updateBatteryAnimationMode(mode: AnimationMode) {
        context.dataStore.edit { it[CHARGING_ANIMATION_MODE] = mode.name }
    }

    suspend fun updateBatteryBrightness(brightness: Int) {
        context.dataStore.edit { it[BATTERY_BRIGHTNESS] = brightness.coerceIn(10, 100) }
    }

    suspend fun updateBatteryFillMode(mode: FillMode) {
        context.dataStore.edit { it[BATTERY_FILL_MODE] = mode.name }
    }

    suspend fun updateBatteryFillDirection(direction: FillDirection) {
        context.dataStore.edit { it[BATTERY_FILL_DIRECTION] = direction.name }
    }

    suspend fun updateBatterySelectedZone(zone: GlyphZone) {
        context.dataStore.edit { it[BATTERY_SELECTED_ZONE] = zone.name }
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

    suspend fun updateChargeFullThreshold(threshold: Int) {
        val normalized = threshold.takeIf(SUPPORTED_CHARGE_THRESHOLDS::contains) ?: 100
        context.dataStore.edit { it[CHARGE_FULL_THRESHOLD] = normalized }
    }

    suspend fun updateChargeCompleteTimerPreset(preset: ChargeCompleteTimerPreset) {
        context.dataStore.edit { it[CHARGE_COMPLETE_TIMER_PRESET] = preset.name }
    }

    suspend fun updateCustomChargeCompleteTimerMinutes(minutes: Int) {
        context.dataStore.edit {
            it[CUSTOM_CHARGE_COMPLETE_TIMER_MINUTES] = minutes.coerceIn(1, 1440)
        }
    }

    suspend fun updateDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { it[DEVELOPER_MODE] = enabled }
    }

    suspend fun readChargeCompleteTimerRuntime(): ChargeCompleteTimerRuntime {
        val prefs = context.dataStore.data.first()
        return ChargeCompleteTimerRuntime(
            deadlineEpochMillis = prefs[CHARGE_COMPLETE_DEADLINE_EPOCH_MILLIS] ?: 0L,
            configurationKey = prefs[CHARGE_COMPLETE_TIMER_CONFIGURATION].orEmpty()
        )
    }

    suspend fun saveChargeCompleteTimerRuntime(runtime: ChargeCompleteTimerRuntime) {
        context.dataStore.edit { prefs ->
            prefs[CHARGE_COMPLETE_DEADLINE_EPOCH_MILLIS] =
                runtime.deadlineEpochMillis.coerceAtLeast(0L)
            prefs[CHARGE_COMPLETE_TIMER_CONFIGURATION] = runtime.configurationKey
        }
    }

    suspend fun clearChargeCompleteTimerRuntime() {
        context.dataStore.edit { prefs ->
            prefs.remove(CHARGE_COMPLETE_DEADLINE_EPOCH_MILLIS)
            prefs.remove(CHARGE_COMPLETE_TIMER_CONFIGURATION)
        }
    }

    suspend fun updateServiceMode(mode: ServiceMode) {
        context.dataStore.edit { it[SERVICE_MODE] = mode.name }
    }

}
