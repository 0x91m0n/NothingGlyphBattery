package com.nothing.glyphbattery.model

internal data class GeneralTimerRuntime(val deadline: Long = 0L, val configuration: String = "")

internal object GeneralTimerRecovery {
    fun restoreOrStart(
        stored: GeneralTimerRuntime,
        configuration: String,
        minutes: Int,
        now: Long
    ): GeneralTimerRuntime {
        if (minutes <= 0) return GeneralTimerRuntime()
        if (stored.configuration == configuration && stored.deadline > 0L) return stored
        return GeneralTimerRuntime(now + minutes.coerceIn(1, 1440) * 60_000L, configuration)
    }
}
