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
    SMOOTH_FILL
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
    val chargingAnimationMode: AnimationMode = AnimationMode.NONE,
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
    val serviceMode: ServiceMode = ServiceMode.MANUAL
)

enum class ServiceMode {
    MANUAL,
    ALWAYS_ON,
    ALWAYS_ON_CHARGING,
    CHARGING_ONLY
}
