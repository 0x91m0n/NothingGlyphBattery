package com.nothing.glyphbattery.glyph

import com.nothing.glyphbattery.model.AnimationMode
import com.nothing.glyphbattery.model.FillDirection
import com.nothing.glyphbattery.model.FillMode
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.GlyphZone
import org.junit.Assert.*
import org.junit.Test

class ProgressAnimationTest {
    private val modes = AnimationMode.entries.filter { it.keepsFilledLeds }

    @Test fun `all five effects have an additional persistent-fill variant`() {
        assertEquals(5, modes.size)
        assertEquals(6, AnimationMode.entries.count { !it.keepsFilledLeds })
    }

    @Test fun `half of zone C stays on and the eleventh LED blinks`() {
        val settings = GlyphSettings(fillMode = FillMode.SINGLE, selectedZone = GlyphZone.ZONE_C,
            animationMode = AnimationMode.PROGRESS_BLINKING)
        val on = GlyphFrames.progress(50, settings, 4000)
        val off = GlyphFrames.progress(50, settings, 0)
        for (index in 0..9) {
            assertEquals(4000, on[index])
            assertEquals(4000, off[index])
        }
        assertEquals(4000, on[10])
        assertEquals(0, off[10])
        for (index in 11..35) assertEquals(0, on[index])
    }

    @Test fun `the animated LED becomes solid only when real battery advances`() {
        val settings = GlyphSettings(fillMode = FillMode.SINGLE, selectedZone = GlyphZone.ZONE_C)
        val before = GlyphFrames.progress(50, settings, 0)
        val after = GlyphFrames.progress(55, settings, 0)
        assertEquals(0, before[10])
        assertEquals(4000, after[10])
        assertEquals(0, after[11])
        assertEquals(4000, GlyphFrames.progress(55, settings, 4000)[11])
    }

    @Test fun `all effects keep the filled portion unchanged throughout their cycles`() {
        for (mode in modes) {
            val settings = GlyphSettings(fillMode = FillMode.SINGLE, selectedZone = GlyphZone.ZONE_C,
                brightness = 65, animationMode = mode)
            val nextValues = mutableSetOf<Int>()
            for (time in 0L..4400L step 40) {
                val factor = ProgressAnimation.factor(mode, time)
                assertTrue(factor in 0.0..1.0)
                val frame = GlyphFrames.progress(50, settings, (2600 * factor).toInt())
                assertEquals(36, frame.size)
                for (index in 0..9) assertEquals(2600, frame[index])
                for (index in 11..35) assertEquals(0, frame[index])
                nextValues += frame[10]
            }
            assertTrue("$mode must animate the next LED", nextValues.size > 1)
        }
    }

    @Test fun `blink has on and off phases without dimming completed LEDs`() {
        assertEquals(1.0, ProgressAnimation.factor(AnimationMode.PROGRESS_BLINKING, 0), 0.0)
        assertEquals(0.0, ProgressAnimation.factor(AnimationMode.PROGRESS_BLINKING, 500), 0.0)
        assertEquals(1.0, ProgressAnimation.factor(AnimationMode.PROGRESS_BLINKING, 1000), 0.0)
    }

    @Test fun `zone boundaries and reverse circular direction select the next physical zone`() {
        val abc = GlyphSettings(fillDirection = FillDirection.ABC)
        val cba = GlyphSettings(fillDirection = FillDirection.CBA)
        // 11 + 5 + 20 LEDs, counted by physical channel rather than equal thirds per zone.
        assertEquals(31, GlyphFrames.progressChannels(31, abc).second)
        assertEquals(0, GlyphFrames.progressChannels(45, abc).second)
        assertEquals(35, GlyphFrames.progressChannels(56, cba).second)
        assertEquals(30, GlyphFrames.progressChannels(70, cba).second)
        assertEquals(18, GlyphFrames.progressChannels(50, abc).first.size)
        assertEquals(18, GlyphFrames.progressChannels(50, cba).first.size)
    }

    @Test fun `all layouts are monotonic with one next LED no duplicates and bounded intensities`() {
        val layouts = FillDirection.entries.map { GlyphSettings(fillDirection = it, brightness = 40) } +
            GlyphZone.entries.map { GlyphSettings(fillMode = FillMode.SINGLE, selectedZone = it, brightness = 40) }
        for (settings in layouts) {
            var previous = emptySet<Int>()
            for (percent in 0..100) {
                val (completed, next) = GlyphFrames.progressChannels(percent, settings)
                assertTrue(completed.containsAll(previous))
                assertEquals(completed.size, completed.toSet().size)
                assertFalse(next in completed)
                val frame = GlyphFrames.progress(percent, settings, 9999)
                assertTrue(frame.all { it in 0..1600 })
                assertEquals(completed.size + if (next == null) 0 else 1, frame.count { it > 0 })
                previous = completed.toSet()
            }
        }
    }

    @Test fun `zero and clamped negative are off and full has no extra LED`() {
        for (mode in modes) for (zone in GlyphZone.entries) {
            val settings = GlyphSettings(fillMode = FillMode.SINGLE, selectedZone = zone, animationMode = mode)
            assertTrue(GlyphFrames.progress(0, settings, 4000).all { it == 0 })
            assertTrue(GlyphFrames.progress(-1, settings, 4000).all { it == 0 })
            assertNull(GlyphFrames.progressChannels(100, settings).second)
            assertEquals(zone.ledCount, GlyphFrames.progress(120, settings, 0).count { it == 4000 })
        }
        assertEquals(36, GlyphFrames.progress(100, GlyphSettings(), 0).count { it == 4000 })
    }
}
