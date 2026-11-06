package com.nothing.glyphbattery.glyph

import com.nothing.glyphbattery.model.AnimationMode
import com.nothing.glyphbattery.model.ChargeCompleteAction
import com.nothing.glyphbattery.model.FillMode
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.GlyphZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphFramesTest {
    @Test
    fun `off frame clears all 36 channels`() {
        val frame = GlyphFrames.off()

        assertEquals(36, frame.size)
        assertTrue(frame.all { it == 0 })
    }

    @Test
    fun `full circular battery lights all channels`() {
        val frame = GlyphFrames.static(
            100,
            GlyphSettings(fillMode = FillMode.CIRCULAR, brightness = 100)
        )

        assertEquals(36, frame.count { it == 4000 })
    }

    @Test
    fun `zero battery always creates an off frame`() {
        val frame = GlyphFrames.static(
            0,
            GlyphSettings(fillMode = FillMode.SINGLE, selectedZone = GlyphZone.ZONE_C)
        )

        assertTrue(frame.all { it == 0 })
    }

    @Test
    fun `low nonzero battery lights exactly one channel without stale channels`() {
        val settings = GlyphSettings(
            fillMode = FillMode.SINGLE,
            selectedZone = GlyphZone.ZONE_B,
            animationMode = AnimationMode.WAVE
        )

        assertEquals(1, GlyphFrames.activeChannels(10, settings).size)
        assertEquals(1, GlyphFrames.static(10, settings).count { it == 4000 })
        assertEquals(35, GlyphFrames.static(10, settings).count { it == 0 })
    }

    @Test
    fun `one percent still lights one channel`() {
        val frame = GlyphFrames.static(
            1,
            GlyphSettings(fillMode = FillMode.SINGLE, selectedZone = GlyphZone.ZONE_C)
        )

        assertEquals(1, frame.count { it == 4000 })
        assertEquals(35, frame.count { it == 0 })
    }

    @Test
    fun `single-zone charge completion lights only selected zone`() {
        val frame = GlyphFrames.chargeComplete(
            GlyphSettings(
                chargeCompleteAction = ChargeCompleteAction.SINGLE_ZONE,
                chargeCompleteZone = GlyphZone.ZONE_B
            )
        )

        assertEquals(5, frame.count { it == 400 })
        assertEquals(31, frame.count { it == 0 })
    }

    @Test
    fun `turn-off charge completion is a complete zero frame`() {
        val frame = GlyphFrames.chargeComplete(
            GlyphSettings(chargeCompleteAction = ChargeCompleteAction.TURN_OFF)
        )

        assertTrue(frame.all { it == 0 })
    }
}
