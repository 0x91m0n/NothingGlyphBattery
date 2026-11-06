package com.nothing.glyphbattery.model

import com.nothing.glyphbattery.glyph.GlyphConnectionSnapshot
import com.nothing.glyphbattery.glyph.GlyphConnectionState
import org.junit.Assert.*
import org.junit.Test

class ChargingSessionRecoveryTest {
    @Test fun `initial sticky battery uses normal initialization without a second bind`() {
        for (plugged in listOf(false, true)) {
            assertNull(ChargingSessionRecovery().observe(plugged))
        }
    }

    @Test fun `overnight connection renews even when reducer target and percent did not change`() {
        for (mode in listOf(ServiceMode.MANUAL, ServiceMode.ALWAYS_ON, ServiceMode.ALWAYS_ON_CHARGING)) {
            val settings = GlyphSettings(serviceMode = mode)
            val unplugged = BatterySnapshot(50, false, false)
            assertEquals(GlyphStateReducer.reduce(settings, unplugged, false).target,
                GlyphStateReducer.reduce(settings, unplugged.copy(isPluggedIn = true), false).target)
            val recovery = ChargingSessionRecovery()
            recovery.observe(false)
            val token = requireNotNull(recovery.observe(true))
            assertTrue(recovery.consume(token))
            assertFalse(recovery.consume(token))
        }
    }

    @Test fun `charging only also renews its previously idle session`() {
        val recovery = ChargingSessionRecovery()
        recovery.observe(false)
        assertTrue(recovery.consume(requireNotNull(recovery.observe(true))))
    }

    @Test fun `duplicate battery events cannot start reconnect storms`() {
        val recovery = ChargingSessionRecovery()
        recovery.observe(false)
        val token = requireNotNull(recovery.observe(true))
        repeat(100) { assertNull(recovery.observe(true)) }
        assertTrue(recovery.consume(token))
        assertNull(recovery.observe(true))
    }

    @Test fun `unplug cancels delayed renewal and rejects obsolete replug events`() {
        val recovery = ChargingSessionRecovery()
        recovery.observe(false)
        val old = requireNotNull(recovery.observe(true))
        recovery.observe(false)
        assertFalse(recovery.consume(old))
        val current = requireNotNull(recovery.observe(true))
        assertFalse(recovery.consume(old))
        assertTrue(recovery.consume(current))
    }

    @Test fun `new binding Ready is distinct even if collector misses Connecting`() {
        val old = GlyphConnectionSnapshot(1, GlyphConnectionState.Ready)
        val current = GlyphConnectionSnapshot(2, GlyphConnectionState.Ready)
        assertNotEquals(old, current)
    }

    @Test fun `renewal does not override expired timers or limit action`() {
        val plugged = BatterySnapshot(80, true, false)
        for (mode in ServiceMode.entries) {
            val settings = GlyphSettings(serviceMode = mode, chargeFullThreshold = 80)
            assertEquals(GlyphBlockReason.TIMER_EXPIRED,
                GlyphStateReducer.reduce(settings, plugged, timerExpired = true).blockReason)
            assertEquals(GlyphRenderTarget.ChargeComplete(settings),
                GlyphStateReducer.reduce(settings, plugged, timerExpired = false).target)
        }
    }
}
