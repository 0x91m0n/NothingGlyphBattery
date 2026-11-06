package com.nothing.glyphbattery.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphStateReducerTest {
    @Test
    fun `service shutdown has priority over every display state`() {
        val decision = GlyphStateReducer.reduce(
            GlyphSettings(
                serviceMode = ServiceMode.ALWAYS_ON,
                chargeCompleteAction = ChargeCompleteAction.ALL_ZONES
            ),
            BatterySnapshot(100, isPluggedIn = true, isFullStatus = true),
            timerExpired = false,
            serviceStopping = true
        )

        assertEquals(GlyphRenderTarget.Off, decision.target)
        assertEquals(GlyphBlockReason.SERVICE_STOPPING, decision.blockReason)
    }

    @Test
    fun `charging only is off immediately after unplugging at full charge`() {
        val settings = GlyphSettings(
            serviceMode = ServiceMode.CHARGING_ONLY,
            chargeFullThreshold = 100,
            chargeCompleteAction = ChargeCompleteAction.ALL_ZONES
        )

        val decision = GlyphStateReducer.reduce(
            settings,
            BatterySnapshot(percent = 100, isPluggedIn = false, isFullStatus = false),
            timerExpired = false
        )

        assertEquals(GlyphRenderTarget.Off, decision.target)
        assertEquals(GlyphBlockReason.NOT_PLUGGED_IN, decision.blockReason)
    }

    @Test
    fun `charging only is off after unplugging at every supported threshold`() {
        listOf(70, 80, 90, 100).forEach { threshold ->
            val decision = GlyphStateReducer.reduce(
                GlyphSettings(
                    serviceMode = ServiceMode.CHARGING_ONLY,
                    chargeFullThreshold = threshold,
                    chargeCompleteAction = ChargeCompleteAction.SINGLE_ZONE
                ),
                BatterySnapshot(threshold, isPluggedIn = false, isFullStatus = false),
                timerExpired = false
            )

            assertEquals("threshold=$threshold", GlyphRenderTarget.Off, decision.target)
        }
    }

    @Test
    fun `charge complete applies at threshold only while plugged in`() {
        val settings = GlyphSettings(
            serviceMode = ServiceMode.ALWAYS_ON,
            chargeFullThreshold = 80
        )

        val plugged = GlyphStateReducer.reduce(
            settings,
            BatterySnapshot(80, isPluggedIn = true, isFullStatus = false),
            timerExpired = false
        )
        val unplugged = GlyphStateReducer.reduce(
            settings,
            BatterySnapshot(80, isPluggedIn = false, isFullStatus = false),
            timerExpired = false
        )

        assertTrue(plugged.target is GlyphRenderTarget.ChargeComplete)
        assertTrue(unplugged.target is GlyphRenderTarget.Battery)
        assertNull(unplugged.blockReason)
    }

    @Test
    fun `android full status applies only while plugged in`() {
        val settings = GlyphSettings(chargeFullThreshold = 100)

        val plugged = GlyphStateReducer.reduce(
            settings,
            BatterySnapshot(95, isPluggedIn = true, isFullStatus = true),
            timerExpired = false
        )
        val unplugged = GlyphStateReducer.reduce(
            settings,
            BatterySnapshot(95, isPluggedIn = false, isFullStatus = true),
            timerExpired = false
        )

        assertTrue(plugged.target is GlyphRenderTarget.ChargeComplete)
        assertTrue(unplugged.target is GlyphRenderTarget.Battery)
    }

    @Test
    fun `timer expiration has priority over charge complete`() {
        val decision = GlyphStateReducer.reduce(
            GlyphSettings(
                serviceMode = ServiceMode.CHARGING_ONLY,
                chargeFullThreshold = 70
            ),
            BatterySnapshot(100, isPluggedIn = true, isFullStatus = true),
            timerExpired = true
        )

        assertEquals(GlyphRenderTarget.Off, decision.target)
        assertEquals(GlyphBlockReason.TIMER_EXPIRED, decision.blockReason)
    }

    @Test
    fun `charge complete timer blocks both lighting actions`() {
        listOf(
            ChargeCompleteAction.SINGLE_ZONE,
            ChargeCompleteAction.ALL_ZONES
        ).forEach { action ->
            val decision = GlyphStateReducer.reduce(
                GlyphSettings(
                    chargeFullThreshold = 80,
                    chargeCompleteAction = action
                ),
                BatterySnapshot(80, isPluggedIn = true, isFullStatus = false),
                timerExpired = false,
                chargeCompleteTimerExpired = true
            )

            assertEquals(action.name, GlyphRenderTarget.Off, decision.target)
            assertEquals(
                action.name,
                GlyphBlockReason.CHARGE_COMPLETE_TIMER_EXPIRED,
                decision.blockReason
            )
        }
    }

    @Test
    fun `charge complete timer does not apply to turn off action`() {
        val decision = GlyphStateReducer.reduce(
            GlyphSettings(chargeCompleteAction = ChargeCompleteAction.TURN_OFF),
            BatterySnapshot(100, isPluggedIn = true, isFullStatus = false),
            timerExpired = false,
            chargeCompleteTimerExpired = true
        )

        assertTrue(decision.target is GlyphRenderTarget.ChargeComplete)
        assertNull(decision.blockReason)
    }

    @Test
    fun `general timer stays higher priority than charge complete timer`() {
        val decision = GlyphStateReducer.reduce(
            GlyphSettings(chargeCompleteAction = ChargeCompleteAction.ALL_ZONES),
            BatterySnapshot(100, isPluggedIn = true, isFullStatus = true),
            timerExpired = true,
            chargeCompleteTimerExpired = true
        )

        assertEquals(GlyphBlockReason.TIMER_EXPIRED, decision.blockReason)
    }

    @Test
    fun `charge complete expiration does not block battery after unplugging`() {
        val decision = GlyphStateReducer.reduce(
            GlyphSettings(
                serviceMode = ServiceMode.ALWAYS_ON,
                chargeFullThreshold = 80,
                chargeCompleteAction = ChargeCompleteAction.ALL_ZONES
            ),
            BatterySnapshot(80, isPluggedIn = false, isFullStatus = false),
            timerExpired = false,
            chargeCompleteTimerExpired = true
        )

        assertTrue(decision.target is GlyphRenderTarget.Battery)
        assertNull(decision.blockReason)
    }

    @Test
    fun `charge complete expiration does not block below threshold`() {
        val decision = GlyphStateReducer.reduce(
            GlyphSettings(
                chargeFullThreshold = 90,
                chargeCompleteAction = ChargeCompleteAction.SINGLE_ZONE
            ),
            BatterySnapshot(89, isPluggedIn = true, isFullStatus = false),
            timerExpired = false,
            chargeCompleteTimerExpired = true
        )

        assertTrue(decision.target is GlyphRenderTarget.Battery)
    }

    @Test
    fun `each supported limit and Android full enter charge complete`() {
        listOf(70, 80, 90, 100).forEach { threshold ->
            assertTrue(
                "threshold=$threshold",
                BatterySnapshot(threshold, isPluggedIn = true, isFullStatus = false)
                    .isChargeComplete(GlyphSettings(chargeFullThreshold = threshold))
            )
        }
        assertTrue(
            BatterySnapshot(20, isPluggedIn = true, isFullStatus = true)
                .isChargeComplete(GlyphSettings(chargeFullThreshold = 100))
        )
    }

    @Test
    fun `timer eligibility covers limits full lighting actions unplug and drop`() {
        listOf(
            ChargeCompleteAction.SINGLE_ZONE,
            ChargeCompleteAction.ALL_ZONES
        ).forEach { action ->
            listOf(70, 80, 90, 100).forEach { threshold ->
                val settings = GlyphSettings(
                    chargeFullThreshold = threshold,
                    chargeCompleteAction = action
                )
                assertTrue(
                    "$action threshold=$threshold",
                    settings.isChargeCompleteTimerEligible(
                        BatterySnapshot(threshold, isPluggedIn = true, isFullStatus = false)
                    )
                )
                assertFalse(
                    settings.isChargeCompleteTimerEligible(
                        BatterySnapshot(threshold, isPluggedIn = false, isFullStatus = false)
                    )
                )
                assertFalse(
                    settings.isChargeCompleteTimerEligible(
                        BatterySnapshot(threshold - 1, isPluggedIn = true, isFullStatus = false)
                    )
                )
            }
        }

        assertTrue(
            GlyphSettings(chargeCompleteAction = ChargeCompleteAction.ALL_ZONES)
                .isChargeCompleteTimerEligible(
                    BatterySnapshot(20, isPluggedIn = true, isFullStatus = true)
                )
        )
        assertFalse(
            GlyphSettings(chargeCompleteAction = ChargeCompleteAction.TURN_OFF)
                .isChargeCompleteTimerEligible(
                    BatterySnapshot(100, isPluggedIn = true, isFullStatus = true)
                )
        )
    }

    @Test
    fun `always on charging uses battery profile while unplugged`() {
        val settings = GlyphSettings(
            serviceMode = ServiceMode.ALWAYS_ON_CHARGING,
            animationMode = AnimationMode.WAVE,
            brightness = 90,
            fillMode = FillMode.CIRCULAR,
            batteryAnimationMode = AnimationMode.BLINKING,
            batteryBrightness = 30,
            batteryFillMode = FillMode.SINGLE,
            batterySelectedZone = GlyphZone.ZONE_B
        )

        val target = GlyphStateReducer.reduce(
            settings,
            BatterySnapshot(50, isPluggedIn = false, isFullStatus = false),
            timerExpired = false
        ).target as GlyphRenderTarget.Battery

        assertEquals(AnimationMode.BLINKING, target.settings.animationMode)
        assertEquals(30, target.settings.brightness)
        assertEquals(FillMode.SINGLE, target.settings.fillMode)
        assertEquals(GlyphZone.ZONE_B, target.settings.selectedZone)
    }

    @Test
    fun `every progress variant reaches the renderer in both profiles and obeys timers`() {
        AnimationMode.entries.filter { it.keepsFilledLeds }.forEach { mode ->
            val settings = GlyphSettings(
                serviceMode = ServiceMode.ALWAYS_ON_CHARGING,
                animationMode = mode,
                batteryAnimationMode = mode,
                brightness = 90,
                batteryBrightness = 30,
                fillMode = FillMode.CIRCULAR,
                batteryFillMode = FillMode.SINGLE,
                batterySelectedZone = GlyphZone.ZONE_C
            )
            for (plugged in listOf(false, true)) {
                val battery = BatterySnapshot(50, isPluggedIn = plugged, isFullStatus = false)
                val target = GlyphStateReducer.reduce(settings, battery, timerExpired = false)
                    .target as GlyphRenderTarget.Battery
                assertEquals(mode, target.settings.animationMode)
                assertEquals(if (plugged) 90 else 30, target.settings.brightness)
                assertEquals(if (plugged) FillMode.CIRCULAR else FillMode.SINGLE, target.settings.fillMode)
                assertEquals(GlyphRenderTarget.Off,
                    GlyphStateReducer.reduce(settings, battery, timerExpired = true).target)
            }
            assertTrue(GlyphStateReducer.reduce(settings,
                BatterySnapshot(100, isPluggedIn = true, isFullStatus = true), timerExpired = false)
                .target is GlyphRenderTarget.ChargeComplete)
            // Enum names are persisted, so every extra value must round-trip without migration.
            assertEquals(mode, AnimationMode.valueOf(mode.name))
        }
    }

    @Test
    fun `battery percent is normalized before rendering`() {
        val target = GlyphStateReducer.reduce(
            GlyphSettings(),
            BatterySnapshot(150, isPluggedIn = false, isFullStatus = false),
            timerExpired = false
        ).target as GlyphRenderTarget.Battery

        assertEquals(100, target.percent)
    }
}
