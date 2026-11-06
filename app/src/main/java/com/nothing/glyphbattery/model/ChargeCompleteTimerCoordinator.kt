package com.nothing.glyphbattery.model

data class ChargeCompleteTimerRuntime(
    val deadlineEpochMillis: Long = 0L,
    val configurationKey: String = ""
)

sealed interface ChargeCompleteTimerPersistence {
    data object None : ChargeCompleteTimerPersistence
    data object Clear : ChargeCompleteTimerPersistence
    data class Save(val runtime: ChargeCompleteTimerRuntime) : ChargeCompleteTimerPersistence
}

data class ChargeCompleteTimerUpdate(
    val deadlineEpochMillis: Long?,
    val expired: Boolean,
    val persistence: ChargeCompleteTimerPersistence = ChargeCompleteTimerPersistence.None
)

/**
 * Owns the persisted charge-complete deadline independently from Android and the Glyph SDK.
 * All methods are expected to be called by the service event loop.
 */
class ChargeCompleteTimerCoordinator(
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var deadlineEpochMillis: Long? = null
    private var configurationKey: String? = null
    private var expired = false

    fun restore(runtime: ChargeCompleteTimerRuntime): ChargeCompleteTimerUpdate {
        deadlineEpochMillis = runtime.deadlineEpochMillis.takeIf { it > 0L }
        configurationKey = runtime.configurationKey.takeIf { it.isNotBlank() }
        if (deadlineEpochMillis == null || configurationKey == null) {
            deadlineEpochMillis = null
            configurationKey = null
        }
        expired = deadlineEpochMillis?.let { clock() >= it } == true
        return snapshot()
    }

    fun synchronize(
        eligible: Boolean,
        preset: ChargeCompleteTimerPreset,
        customMinutes: Int
    ): ChargeCompleteTimerUpdate {
        if (!eligible || preset == ChargeCompleteTimerPreset.OFF) {
            return clear()
        }

        val minutes = if (preset == ChargeCompleteTimerPreset.CUSTOM) {
            customMinutes.coerceIn(1, 1440)
        } else {
            preset.minutes
        }
        val newConfigurationKey = "${preset.name}:$minutes"
        if (deadlineEpochMillis == null || configurationKey != newConfigurationKey) {
            val deadline = clock() + minutes.toLong() * 60_000L
            deadlineEpochMillis = deadline
            configurationKey = newConfigurationKey
            expired = false
            return ChargeCompleteTimerUpdate(
                deadlineEpochMillis = deadline,
                expired = false,
                persistence = ChargeCompleteTimerPersistence.Save(
                    ChargeCompleteTimerRuntime(deadline, newConfigurationKey)
                )
            )
        }

        expired = clock() >= checkNotNull(deadlineEpochMillis)
        return snapshot()
    }

    fun deadlineReached(expectedDeadlineEpochMillis: Long): ChargeCompleteTimerUpdate {
        val deadline = deadlineEpochMillis
        if (deadline == expectedDeadlineEpochMillis && clock() >= expectedDeadlineEpochMillis) {
            expired = true
        }
        return snapshot()
    }

    fun reset(): ChargeCompleteTimerUpdate = clear()

    fun snapshot(): ChargeCompleteTimerUpdate = ChargeCompleteTimerUpdate(
        deadlineEpochMillis = deadlineEpochMillis,
        expired = expired
    )

    private fun clear(): ChargeCompleteTimerUpdate {
        val hadPersistedState = deadlineEpochMillis != null || configurationKey != null
        deadlineEpochMillis = null
        configurationKey = null
        expired = false
        return ChargeCompleteTimerUpdate(
            deadlineEpochMillis = null,
            expired = false,
            persistence = if (hadPersistedState) {
                ChargeCompleteTimerPersistence.Clear
            } else {
                ChargeCompleteTimerPersistence.None
            }
        )
    }
}
