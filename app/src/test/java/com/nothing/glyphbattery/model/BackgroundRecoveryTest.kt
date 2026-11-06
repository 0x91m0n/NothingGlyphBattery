package com.nothing.glyphbattery.model

import org.junit.Assert.*
import org.junit.Test

class BackgroundRecoveryTest {
    @Test fun `only an outstanding request without any user stop may resume`() {
        for (requested in listOf(false, true)) {
            for (explicitStop in listOf(false, true)) {
                for (systemStop in listOf(false, true)) {
                    assertEquals(requested && !explicitStop && !systemStop,
                        RecoveryPolicy.shouldResume(requested, explicitStop, systemStop))
                }
            }
        }
    }

    @Test fun `background recovery requires exemption and no background restriction`() {
        assertFalse(RecoveryPolicy.canStartInBackground(false, false))
        assertFalse(RecoveryPolicy.canStartInBackground(false, true))
        assertFalse(RecoveryPolicy.canStartInBackground(true, true))
        assertTrue(RecoveryPolicy.canStartInBackground(true, false))
    }

    @Test fun `general timer preserves both future and expired deadlines after process death`() {
        val stored = GeneralTimerRuntime(60_000, "MANUAL:CUSTOM:1")
        assertEquals(stored, GeneralTimerRecovery.restoreOrStart(stored, stored.configuration, 1, 30_000))
        val expired = GeneralTimerRecovery.restoreOrStart(stored, stored.configuration, 1, 90_000)
        assertEquals(60_000L, expired.deadline)
        assertTrue(expired.deadline <= 90_000)
    }

    @Test fun `configuration change and new session start a new interval while off clears it`() {
        val stored = GeneralTimerRuntime(60_000, "old")
        assertEquals(210_000L, GeneralTimerRecovery.restoreOrStart(stored, "new", 2, 90_000).deadline)
        assertEquals(150_000L, GeneralTimerRecovery.restoreOrStart(GeneralTimerRuntime(), "new", 1, 90_000).deadline)
        assertEquals(GeneralTimerRuntime(), GeneralTimerRecovery.restoreOrStart(stored, "old", 0, 90_000))
    }

    @Test fun `reconnect delay backs off to one minute and resets on success`() {
        val backoff = ReconnectBackoff()
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L, 60000L),
            List(7) { backoff.nextDelayMillis() })
        repeat(100) { assertEquals(60000L, backoff.nextDelayMillis()) }
        backoff.reset()
        assertEquals(1000L, backoff.nextDelayMillis())
    }
}
