package com.nothing.glyphbattery.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeCompleteTimerCoordinatorTest {
    private var now = 1_000_000L
    private fun coordinator() = ChargeCompleteTimerCoordinator { now }

    @Test
    fun `entering eligible state starts and persists a deadline`() {
        val update = coordinator().synchronize(
            eligible = true,
            preset = ChargeCompleteTimerPreset.MIN_5,
            customMinutes = 30
        )

        assertEquals(now + 5 * 60_000L, update.deadlineEpochMillis)
        assertFalse(update.expired)
        assertTrue(update.persistence is ChargeCompleteTimerPersistence.Save)
    }

    @Test
    fun `deadline expires without replacing persisted deadline`() {
        val timer = coordinator()
        val started = timer.synchronize(true, ChargeCompleteTimerPreset.MIN_5, 30)
        now = checkNotNull(started.deadlineEpochMillis)

        val expired = timer.deadlineReached(now)

        assertTrue(expired.expired)
        assertEquals(now, expired.deadlineEpochMillis)
        assertEquals(ChargeCompleteTimerPersistence.None, expired.persistence)
    }

    @Test
    fun `stale deadline event cannot expire a newer interval`() {
        val timer = coordinator()
        val first = timer.synchronize(true, ChargeCompleteTimerPreset.MIN_5, 30)
        now += 1_000L
        val second = timer.synchronize(true, ChargeCompleteTimerPreset.MIN_15, 30)
        now = checkNotNull(second.deadlineEpochMillis)

        val update = timer.deadlineReached(checkNotNull(first.deadlineEpochMillis))

        assertFalse(update.expired)
        assertEquals(second.deadlineEpochMillis, update.deadlineEpochMillis)
    }

    @Test
    fun `future persisted deadline survives process recreation`() {
        val runtime = ChargeCompleteTimerRuntime(now + 60_000L, "CUSTOM:1")

        val update = coordinator().restore(runtime)

        assertFalse(update.expired)
        assertEquals(runtime.deadlineEpochMillis, update.deadlineEpochMillis)
        assertEquals(ChargeCompleteTimerPersistence.None, update.persistence)
    }

    @Test
    fun `expired persisted deadline stays expired after process recreation`() {
        val runtime = ChargeCompleteTimerRuntime(now - 1L, "CUSTOM:1")

        val update = coordinator().restore(runtime)

        assertTrue(update.expired)
        assertEquals(runtime.deadlineEpochMillis, update.deadlineEpochMillis)
    }

    @Test
    fun `restored matching configuration does not restart interval`() {
        val oldDeadline = now + 60_000L
        val timer = coordinator()
        timer.restore(ChargeCompleteTimerRuntime(oldDeadline, "CUSTOM:1"))

        val update = timer.synchronize(true, ChargeCompleteTimerPreset.CUSTOM, 1)

        assertEquals(oldDeadline, update.deadlineEpochMillis)
        assertEquals(ChargeCompleteTimerPersistence.None, update.persistence)
    }

    @Test
    fun `changing duration during countdown starts a new interval`() {
        val timer = coordinator()
        timer.synchronize(true, ChargeCompleteTimerPreset.MIN_5, 30)
        now += 10_000L

        val update = timer.synchronize(true, ChargeCompleteTimerPreset.MIN_15, 30)

        assertEquals(now + 15 * 60_000L, update.deadlineEpochMillis)
        assertFalse(update.expired)
        assertTrue(update.persistence is ChargeCompleteTimerPersistence.Save)
    }

    @Test
    fun `switching preset to custom at same duration is a configuration change`() {
        val timer = coordinator()
        timer.synchronize(true, ChargeCompleteTimerPreset.MIN_30, 30)
        now += 10_000L

        val update = timer.synchronize(true, ChargeCompleteTimerPreset.CUSTOM, 30)

        assertEquals(now + 30 * 60_000L, update.deadlineEpochMillis)
        assertTrue(update.persistence is ChargeCompleteTimerPersistence.Save)
    }

    @Test
    fun `off immediately clears active limitation`() {
        val timer = coordinator()
        timer.synchronize(true, ChargeCompleteTimerPreset.MIN_5, 30)

        val update = timer.synchronize(true, ChargeCompleteTimerPreset.OFF, 30)

        assertNull(update.deadlineEpochMillis)
        assertFalse(update.expired)
        assertEquals(ChargeCompleteTimerPersistence.Clear, update.persistence)
    }

    @Test
    fun `leaving charge-complete state clears session and reentry starts fresh`() {
        val timer = coordinator()
        val first = timer.synchronize(true, ChargeCompleteTimerPreset.MIN_5, 30)
        now += 30_000L

        val left = timer.synchronize(false, ChargeCompleteTimerPreset.MIN_5, 30)
        val reentered = timer.synchronize(true, ChargeCompleteTimerPreset.MIN_5, 30)

        assertEquals(ChargeCompleteTimerPersistence.Clear, left.persistence)
        assertNull(left.deadlineEpochMillis)
        assertEquals(now + 5 * 60_000L, reentered.deadlineEpochMillis)
        assertTrue(reentered.deadlineEpochMillis != first.deadlineEpochMillis)
    }

    @Test
    fun `manual stop reset clears deadline and expired flag`() {
        val timer = coordinator()
        val started = timer.synchronize(true, ChargeCompleteTimerPreset.CUSTOM, 1)
        now = checkNotNull(started.deadlineEpochMillis)
        timer.deadlineReached(now)

        val reset = timer.reset()

        assertNull(reset.deadlineEpochMillis)
        assertFalse(reset.expired)
        assertEquals(ChargeCompleteTimerPersistence.Clear, reset.persistence)
    }

    @Test
    fun `custom value is clamped to supported bounds`() {
        val timer = coordinator()
        val minimum = timer.synchronize(true, ChargeCompleteTimerPreset.CUSTOM, -5)
        timer.reset()
        val maximum = timer.synchronize(true, ChargeCompleteTimerPreset.CUSTOM, 9_999)

        assertEquals(now + 60_000L, minimum.deadlineEpochMillis)
        assertEquals(now + 1_440L * 60_000L, maximum.deadlineEpochMillis)
    }
}
