package com.nothing.glyphbattery.glyph

import com.nothing.glyphbattery.model.ChargeCompleteAction
import com.nothing.glyphbattery.model.FillDirection
import com.nothing.glyphbattery.model.FillMode
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.GlyphZone

internal object GlyphFrames {
    const val CHANNEL_COUNT = 36
    private const val MAX_INTENSITY = 4000

    val A_CHANNELS = (30 downTo 20).toList()
    val B_CHANNELS = (31..35).toList()
    val C_CHANNELS = (0..19).toList()

    fun off(): IntArray = IntArray(CHANNEL_COUNT)

    fun intensity(brightnessPercent: Int): Int {
        return brightnessPercent.coerceIn(0, 100) * MAX_INTENSITY / 100
    }

    fun static(percent: Int, settings: GlyphSettings): IntArray {
        val frame = off()
        val normalizedPercent = percent.coerceIn(0, 100)
        if (normalizedPercent == 0) return frame

        val intensity = intensity(settings.brightness)
        when (settings.fillMode) {
            FillMode.CIRCULAR -> {
                val (first, second, third) = orderedZones(settings.fillDirection)
                when {
                    normalizedPercent <= 33 -> {
                        addProportional(frame, first, normalizedPercent * 100 / 33, intensity)
                    }
                    normalizedPercent <= 66 -> {
                        addAll(frame, first, intensity)
                        addProportional(
                            frame,
                            second,
                            (normalizedPercent - 33) * 100 / 33,
                            intensity
                        )
                    }
                    else -> {
                        addAll(frame, first, intensity)
                        addAll(frame, second, intensity)
                        addProportional(
                            frame,
                            third,
                            (normalizedPercent - 66) * 100 / 34,
                            intensity
                        )
                    }
                }
            }
            FillMode.SINGLE -> {
                addProportional(
                    frame,
                    channelsForZone(settings.selectedZone),
                    normalizedPercent,
                    intensity
                )
            }
        }
        return frame
    }

    /** Completed LEDs stay on; only the first not-yet-completed LED is animated.
     * Uses floor, unlike legacy static fill's ceil. At 50% of 20 LEDs: 10 on + LED 11.
     * New variants count physical LEDs across the selected path; legacy fill is unchanged.
     */
    fun progressChannels(percent: Int, settings: GlyphSettings): Pair<List<Int>, Int?> {
        val level = percent.coerceIn(0, 100)
        val zones = if (settings.fillMode == FillMode.SINGLE) {
            listOf(channelsForZone(settings.selectedZone))
        } else {
            orderedZones(settings.fillDirection).toList()
        }
        val order = zones.flatten()
        if (level == 0) return emptyList<Int>() to null
        val count = level * order.size / 100
        return order.take(count) to order.getOrNull(count)
    }

    fun progress(percent: Int, settings: GlyphSettings, nextIntensity: Int): IntArray {
        val (completed, next) = progressChannels(percent, settings)
        val maxIntensity = intensity(settings.brightness)
        return channels(completed, maxIntensity).also { frame ->
            next?.let { frame[it] = nextIntensity.coerceIn(0, maxIntensity) }
        }
    }

    fun chargeComplete(settings: GlyphSettings): IntArray {
        val frame = off()
        val channels = when (settings.chargeCompleteAction) {
            ChargeCompleteAction.TURN_OFF -> emptyList()
            ChargeCompleteAction.SINGLE_ZONE -> channelsForZone(settings.chargeCompleteZone)
            ChargeCompleteAction.ALL_ZONES -> A_CHANNELS + B_CHANNELS + C_CHANNELS
        }
        addAll(frame, channels, intensity(10))
        return frame
    }

    fun activeChannels(percent: Int, settings: GlyphSettings): List<Int> {
        val normalizedPercent = percent.coerceIn(0, 100)
        return when (settings.fillMode) {
            FillMode.CIRCULAR -> {
                val (first, second, third) = orderedZones(settings.fillDirection)
                when {
                    normalizedPercent <= 33 -> {
                        val scaled = normalizedPercent * 100 / 33
                        first.take(ledCount(first, scaled))
                    }
                    normalizedPercent <= 66 -> {
                        val scaled = (normalizedPercent - 33) * 100 / 33
                        first + second.take(ledCount(second, scaled))
                    }
                    else -> {
                        val scaled = (normalizedPercent - 66) * 100 / 34
                        first + second + third.take(ledCount(third, scaled))
                    }
                }
            }
            FillMode.SINGLE -> {
                val channels = channelsForZone(settings.selectedZone)
                channels.take(ledCount(channels, normalizedPercent))
            }
        }
    }

    fun activeZoneGroups(percent: Int, settings: GlyphSettings): List<List<Int>> {
        val active = activeChannels(percent, settings)
        if (active.isEmpty()) return emptyList()
        if (settings.fillMode == FillMode.SINGLE) return listOf(active)

        val (first, second, third) = orderedZones(settings.fillDirection)
        return listOf(first, second, third)
            .map { zone -> zone.filter(active::contains) }
            .filter(List<Int>::isNotEmpty)
    }

    fun channels(channels: Collection<Int>, intensity: Int): IntArray {
        return off().also { frame -> addAll(frame, channels, intensity) }
    }

    private fun orderedZones(direction: FillDirection): Triple<List<Int>, List<Int>, List<Int>> {
        return when (direction) {
            FillDirection.ABC -> Triple(A_CHANNELS.reversed(), B_CHANNELS, C_CHANNELS)
            FillDirection.CBA -> Triple(C_CHANNELS.reversed(), B_CHANNELS.reversed(), A_CHANNELS)
        }
    }

    private fun channelsForZone(zone: GlyphZone): List<Int> {
        return when (zone) {
            GlyphZone.ZONE_A -> A_CHANNELS
            GlyphZone.ZONE_B -> B_CHANNELS
            GlyphZone.ZONE_C -> C_CHANNELS
        }
    }

    private fun addProportional(
        frame: IntArray,
        channels: List<Int>,
        percent: Int,
        intensity: Int
    ) {
        channels.take(ledCount(channels, percent)).forEach { channel ->
            frame[channel] = intensity
        }
    }

    private fun addAll(frame: IntArray, channels: Collection<Int>, intensity: Int) {
        channels.forEach { channel ->
            if (channel in frame.indices) frame[channel] = intensity
        }
    }

    private fun ledCount(channels: List<Int>, percent: Int): Int {
        val normalizedPercent = percent.coerceIn(0, 100)
        if (normalizedPercent == 0 || channels.isEmpty()) return 0
        return ((normalizedPercent * channels.size + 99) / 100)
            .coerceIn(1, channels.size)
    }
}
