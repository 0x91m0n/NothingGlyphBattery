package com.nothing.glyphbattery.model

enum class FillMode {
    CIRCULAR,
    SINGLE
}

enum class FillDirection {
    ABC,
    CBA
}

enum class AnimationMode {
    NONE,
    BREATHING,
    BLINKING,
    WAVE,
    HOURGLASS,
    SMOOTH_FILL,
    PROGRESS_BREATHING,
    PROGRESS_BLINKING,
    PROGRESS_WAVE,
    PROGRESS_HOURGLASS,
    PROGRESS_SMOOTH_FILL;

    val keepsFilledLeds: Boolean
        get() = when (this) {
            PROGRESS_BREATHING, PROGRESS_BLINKING, PROGRESS_WAVE,
            PROGRESS_HOURGLASS, PROGRESS_SMOOTH_FILL -> true
            else -> false
        }
}

enum class ChargeCompleteAction {
    TURN_OFF,
    SINGLE_ZONE,
    ALL_ZONES
}

enum class AutoOffTimer(val minutes: Int) {
    OFF(0),
    MIN_15(15),
    MIN_30(30),
    HOUR_1(60),
    HOUR_2(120),
    CUSTOM(-1)
}

enum class ChargeCompleteTimerPreset(val minutes: Int) {
    OFF(0),
    MIN_5(5),
    MIN_15(15),
    MIN_30(30),
    HOUR_1(60),
    CUSTOM(-1)
}

/**
 * Nothing Phone 3a Glyph zones:
 * A (A1–A11, indices 20–30) — vertical strip, 11 LEDs, individually addressable
 * B (B1–B5,  indices 31–35) — small group, 5 LEDs, individually addressable
 * C (C1–C20, indices 0–19)  — main strip, 20 LEDs, individually addressable
 */
enum class GlyphZone(val ledCount: Int) {
    ZONE_A(11),
    ZONE_B(5),
    ZONE_C(20);

    companion object {
        val ALL_ZONES = entries.toList()
    }
}

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    RUSSIAN("ru")
}

data class GlyphSettings(
    val fillMode: FillMode = FillMode.CIRCULAR,
    val fillDirection: FillDirection = FillDirection.ABC,
    val selectedZone: GlyphZone = GlyphZone.ZONE_A,
    val animationMode: AnimationMode = AnimationMode.NONE,
    val batteryAnimationMode: AnimationMode = AnimationMode.NONE,
    val batteryBrightness: Int = 100,
    val batteryFillMode: FillMode = FillMode.CIRCULAR,
    val batteryFillDirection: FillDirection = FillDirection.ABC,
    val batterySelectedZone: GlyphZone = GlyphZone.ZONE_A,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val brightness: Int = 100,
    val animationSpeed: Int = 100,
    val autoOffTimer: AutoOffTimer = AutoOffTimer.OFF,
    val customAutoOffMinutes: Int = 45,
    val chargeCompleteAction: ChargeCompleteAction = ChargeCompleteAction.TURN_OFF,
    val chargeCompleteZone: GlyphZone = GlyphZone.ZONE_A,
    val chargeFullThreshold: Int = 100,
    val chargeCompleteTimerPreset: ChargeCompleteTimerPreset = ChargeCompleteTimerPreset.OFF,
    val customChargeCompleteTimerMinutes: Int = 30,
    val serviceMode: ServiceMode = ServiceMode.MANUAL
) {
    fun isChargeCompleteTimerEligible(battery: BatterySnapshot): Boolean {
        return chargeCompleteAction != ChargeCompleteAction.TURN_OFF &&
            battery.isChargeComplete(this)
    }
}

enum class ServiceMode {
    MANUAL,
    ALWAYS_ON,
    ALWAYS_ON_CHARGING,
    CHARGING_ONLY
}

data class BatterySnapshot(
    val percent: Int,
    val isPluggedIn: Boolean,
    val isFullStatus: Boolean
) {
    fun normalized(): BatterySnapshot = copy(percent = percent.coerceIn(0, 100))

    fun isChargeComplete(settings: GlyphSettings): Boolean {
        val snapshot = normalized()
        return snapshot.isPluggedIn &&
            (snapshot.isFullStatus || snapshot.percent >= settings.chargeFullThreshold)
    }
}

sealed interface GlyphRenderTarget {
    data object Off : GlyphRenderTarget

    data class Battery(
        val percent: Int,
        val settings: GlyphSettings
    ) : GlyphRenderTarget

    data class ChargeComplete(
        val settings: GlyphSettings
    ) : GlyphRenderTarget
}

enum class GlyphBlockReason {
    SERVICE_STOPPING,
    TIMER_EXPIRED,
    CHARGE_COMPLETE_TIMER_EXPIRED,
    NOT_PLUGGED_IN
}

data class GlyphDecision(
    val target: GlyphRenderTarget,
    val blockReason: GlyphBlockReason? = null
)

object GlyphStateReducer {
    fun reduce(
        settings: GlyphSettings,
        battery: BatterySnapshot,
        timerExpired: Boolean,
        chargeCompleteTimerExpired: Boolean = false,
        serviceStopping: Boolean = false
    ): GlyphDecision {
        val snapshot = battery.normalized()

        if (serviceStopping) {
            return GlyphDecision(GlyphRenderTarget.Off, GlyphBlockReason.SERVICE_STOPPING)
        }
        if (timerExpired) {
            return GlyphDecision(GlyphRenderTarget.Off, GlyphBlockReason.TIMER_EXPIRED)
        }
        if (settings.serviceMode == ServiceMode.CHARGING_ONLY && !snapshot.isPluggedIn) {
            return GlyphDecision(GlyphRenderTarget.Off, GlyphBlockReason.NOT_PLUGGED_IN)
        }

        val chargeComplete = snapshot.isChargeComplete(settings)
        if (chargeComplete) {
            val timerApplies = settings.chargeCompleteAction != ChargeCompleteAction.TURN_OFF
            if (timerApplies && chargeCompleteTimerExpired) {
                return GlyphDecision(
                    GlyphRenderTarget.Off,
                    GlyphBlockReason.CHARGE_COMPLETE_TIMER_EXPIRED
                )
            }
            return GlyphDecision(GlyphRenderTarget.ChargeComplete(settings))
        }

        return GlyphDecision(
            GlyphRenderTarget.Battery(
                percent = snapshot.percent,
                settings = effectiveSettings(settings, snapshot.isPluggedIn)
            )
        )
    }

    private fun effectiveSettings(settings: GlyphSettings, isPluggedIn: Boolean): GlyphSettings {
        if (settings.serviceMode != ServiceMode.ALWAYS_ON_CHARGING || isPluggedIn) {
            return settings
        }
        return settings.copy(
            animationMode = settings.batteryAnimationMode,
            brightness = settings.batteryBrightness,
            fillMode = settings.batteryFillMode,
            fillDirection = settings.batteryFillDirection,
            selectedZone = settings.batterySelectedZone
        )
    }
}
