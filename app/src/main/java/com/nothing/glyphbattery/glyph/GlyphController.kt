package com.nothing.glyphbattery.glyph

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nothing.glyphbattery.model.AnimationMode
import com.nothing.glyphbattery.model.ChargeCompleteAction
import com.nothing.glyphbattery.model.FillDirection
import com.nothing.glyphbattery.model.FillMode
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.GlyphZone
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager
import kotlinx.coroutines.*
import kotlin.math.roundToInt
import kotlin.math.sin

class GlyphController(private val context: Context) {

    companion object {
        private const val TAG = "GlyphController"
        private const val MAX_INTENSITY = 4000

        // Default (bottom-to-top): used in Single mode and as base
        private val A_CHANNELS = (30 downTo 20).toList()   // 11 LEDs, A_11→A_1
        private val B_CHANNELS = (31..35).toList()          // 5 LEDs, B_1→B_5
        private val C_CHANNELS = (0..19).toList()            // 20 LEDs, C_1→C_20

        // Reversed lists for circular snake pattern
        private val A_CHANNELS_REV = A_CHANNELS.reversed()  // A_1→A_11 (top-to-bottom)
        private val B_CHANNELS_REV = B_CHANNELS.reversed()  // B_5→B_1 (top-to-bottom)
        private val C_CHANNELS_REV = C_CHANNELS.reversed()  // C_20→C_1 (top-to-bottom)
    }

    private var glyphManager: GlyphManager? = null
    private var isSessionOpen = false
    private var animationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun init(onReady: () -> Unit = {}) {
        try {
            glyphManager = GlyphManager.getInstance(context.applicationContext)
            glyphManager?.init(object : GlyphManager.Callback {
                override fun onServiceConnected(componentName: ComponentName?) {
                    try {
                        if (Common.is24111()) {
                            glyphManager?.register(Glyph.DEVICE_24111)
                        }
                        glyphManager?.openSession()
                        isSessionOpen = true
                        Log.d(TAG, "Glyph session opened")
                        onReady()
                    } catch (e: GlyphException) {
                        Log.e(TAG, "Failed to open session", e)
                    }
                }

                override fun onServiceDisconnected(componentName: ComponentName?) {
                    isSessionOpen = false
                    Log.d(TAG, "Glyph service disconnected")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init GlyphManager", e)
        }
    }

    // ─── Public API ────────────────────────────────────────────────

    private fun intensityFor(brightnessPercent: Int): Int {
        return (brightnessPercent * MAX_INTENSITY / 100).coerceIn(0, MAX_INTENSITY)
    }

    /**
     * Returns ordered zone groups with circular (snake) fill directions.
     * ABC: A top→bottom, B bottom→top, C bottom→top
     * CBA: C top→bottom, B top→bottom, A bottom→top
     */
    private fun orderedZones(settings: GlyphSettings): Triple<List<Int>, List<Int>, List<Int>> {
        return when (settings.fillDirection) {
            FillDirection.ABC -> Triple(A_CHANNELS_REV, B_CHANNELS, C_CHANNELS)
            FillDirection.CBA -> Triple(C_CHANNELS_REV, B_CHANNELS_REV, A_CHANNELS)
        }
    }

    private fun channelsForZone(zone: GlyphZone): List<Int> {
        return when (zone) {
            GlyphZone.ZONE_A -> A_CHANNELS
            GlyphZone.ZONE_B -> B_CHANNELS
            GlyphZone.ZONE_C -> C_CHANNELS
        }
    }

    /** Applies speed multiplier: higher speed% = shorter delays. */
    private fun delayMs(baseMs: Long, settings: GlyphSettings): Long {
        return (baseMs * 100L / settings.animationSpeed.coerceAtLeast(25)).coerceAtLeast(10)
    }

    fun updateBatteryGlyph(batteryPercent: Int, settings: GlyphSettings) {
        if (!isSessionOpen) return
        animationJob?.cancel()

        if (settings.animationMode == AnimationMode.NONE) {
            showStatic(batteryPercent, settings)
        } else {
            animationJob = scope.launch {
                runAnimation(batteryPercent, settings)
            }
        }
    }

    fun showChargeComplete(settings: GlyphSettings) {
        if (!isSessionOpen) return
        val gm = glyphManager ?: return
        animationJob?.cancel()
        val lowIntensity = intensityFor(10)
        try {
            when (settings.chargeCompleteAction) {
                ChargeCompleteAction.TURN_OFF -> gm.turnOff()
                ChargeCompleteAction.SINGLE_ZONE -> {
                    val channels = channelsForZone(settings.chargeCompleteZone)
                    val builder = gm.glyphFrameBuilder
                    for (ch in channels) builder.buildChannel(ch, lowIntensity)
                    gm.toggle(builder.build())
                }
                ChargeCompleteAction.ALL_ZONES -> {
                    val allChannels = A_CHANNELS + B_CHANNELS + C_CHANNELS
                    val builder = gm.glyphFrameBuilder
                    for (ch in allChannels) builder.buildChannel(ch, lowIntensity)
                    gm.toggle(builder.build())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showChargeComplete failed", e)
        }
    }

    fun turnOff() {
        animationJob?.cancel()
        try { glyphManager?.turnOff() } catch (_: Exception) {}
    }

    fun release() {
        turnOff()
        scope.cancel()
        try {
            if (isSessionOpen) {
                glyphManager?.closeSession()
                isSessionOpen = false
            }
            glyphManager?.unInit()
        } catch (_: Exception) {}
        glyphManager = null
    }

    // ─── Static display ────────────────────────────────────────────

    private fun showStatic(percent: Int, settings: GlyphSettings) {
        val gm = glyphManager ?: return
        try {
            if (percent <= 0) { gm.turnOff(); return }
            val intensity = intensityFor(settings.brightness)
            val builder = gm.glyphFrameBuilder
            buildFrame(builder, percent, settings, intensity)
            gm.toggle(builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "showStatic failed", e)
        }
    }

    private fun buildFrame(
        builder: GlyphFrame.Builder, percent: Int, settings: GlyphSettings, intensity: Int
    ) {
        when (settings.fillMode) {
            FillMode.CIRCULAR -> {
                val (z1, z2, z3) = orderedZones(settings)
                when {
                    percent <= 33 -> {
                        val scaled = (percent * 100 / 33).coerceIn(0, 100)
                        addProportional(builder, z1, scaled, intensity)
                    }
                    percent <= 66 -> {
                        addAll(builder, z1, intensity)
                        val scaled = ((percent - 33) * 100 / 33).coerceIn(0, 100)
                        addProportional(builder, z2, scaled, intensity)
                    }
                    else -> {
                        addAll(builder, z1, intensity)
                        addAll(builder, z2, intensity)
                        val scaled = ((percent - 66) * 100 / 34).coerceIn(0, 100)
                        addProportional(builder, z3, scaled, intensity)
                    }
                }
            }
            FillMode.SINGLE -> {
                when (settings.selectedZone) {
                    GlyphZone.ZONE_A -> addProportional(builder, A_CHANNELS, percent, intensity)
                    GlyphZone.ZONE_B -> addProportional(builder, B_CHANNELS, percent, intensity)
                    GlyphZone.ZONE_C -> addProportional(builder, C_CHANNELS, percent, intensity)
                }
            }
        }
    }

    private fun getActiveChannels(percent: Int, settings: GlyphSettings): List<Int> {
        return when (settings.fillMode) {
            FillMode.CIRCULAR -> {
                val (z1, z2, z3) = orderedZones(settings)
                when {
                    percent <= 33 -> {
                        val scaled = (percent * 100 / 33).coerceIn(0, 100)
                        z1.take(ledCount(z1, scaled))
                    }
                    percent <= 66 -> {
                        val scaled = ((percent - 33) * 100 / 33).coerceIn(0, 100)
                        z1 + z2.take(ledCount(z2, scaled))
                    }
                    else -> {
                        val scaled = ((percent - 66) * 100 / 34).coerceIn(0, 100)
                        z1 + z2 + z3.take(ledCount(z3, scaled))
                    }
                }
            }
            FillMode.SINGLE -> {
                when (settings.selectedZone) {
                    GlyphZone.ZONE_A -> A_CHANNELS.take(ledCount(A_CHANNELS, percent))
                    GlyphZone.ZONE_B -> B_CHANNELS.take(ledCount(B_CHANNELS, percent))
                    GlyphZone.ZONE_C -> C_CHANNELS.take(ledCount(C_CHANNELS, percent))
                }
            }
        }
    }

    /** Returns zone groups that have active channels, in order. Used for sequential breathing. */
    private fun getActiveZoneGroups(percent: Int, settings: GlyphSettings): List<List<Int>> {
        return when (settings.fillMode) {
            FillMode.CIRCULAR -> {
                val (z1, z2, z3) = orderedZones(settings)
                val groups = mutableListOf<List<Int>>()
                when {
                    percent <= 33 -> {
                        val scaled = (percent * 100 / 33).coerceIn(0, 100)
                        val taken = z1.take(ledCount(z1, scaled))
                        if (taken.isNotEmpty()) groups.add(taken)
                    }
                    percent <= 66 -> {
                        groups.add(z1)
                        val scaled = ((percent - 33) * 100 / 33).coerceIn(0, 100)
                        val taken = z2.take(ledCount(z2, scaled))
                        if (taken.isNotEmpty()) groups.add(taken)
                    }
                    else -> {
                        groups.add(z1)
                        groups.add(z2)
                        val scaled = ((percent - 66) * 100 / 34).coerceIn(0, 100)
                        val taken = z3.take(ledCount(z3, scaled))
                        if (taken.isNotEmpty()) groups.add(taken)
                    }
                }
                groups
            }
            FillMode.SINGLE -> {
                val ch = when (settings.selectedZone) {
                    GlyphZone.ZONE_A -> A_CHANNELS.take(ledCount(A_CHANNELS, percent))
                    GlyphZone.ZONE_B -> B_CHANNELS.take(ledCount(B_CHANNELS, percent))
                    GlyphZone.ZONE_C -> C_CHANNELS.take(ledCount(C_CHANNELS, percent))
                }
                if (ch.isNotEmpty()) listOf(ch) else emptyList()
            }
        }
    }

    // ─── Animations ────────────────────────────────────────────────

    private suspend fun runAnimation(percent: Int, settings: GlyphSettings) {
        try {
            when (settings.animationMode) {
                AnimationMode.BREATHING -> animBreathing(percent, settings)
                AnimationMode.BLINKING -> animBlinking(percent, settings)
                AnimationMode.WAVE -> animWave(percent, settings)
                AnimationMode.HOURGLASS -> animHourglass(percent, settings)
                AnimationMode.SMOOTH_FILL -> animSmoothFill(percent, settings)
                AnimationMode.NONE -> {}
            }
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            Log.e(TAG, "Animation failed", e)
        }
    }

    /**
     * Breathing: sequential zone-by-zone pulsing. Each zone breathes in turn,
     * others stay off. Uses sine curve for smooth ramp.
     */
    private suspend fun animBreathing(percent: Int, settings: GlyphSettings) {
        val gm = glyphManager ?: return
        val zoneGroups = getActiveZoneGroups(percent, settings)
        if (zoneGroups.isEmpty()) return
        val maxIntensity = intensityFor(settings.brightness)
        val stepMs = delayMs(40, settings)
        val stepsPerZone = 50

        while (currentCoroutineContext().isActive) {
            for (group in zoneGroups) {
                for (step in 0 until stepsPerZone) {
                    val phase = step.toDouble() / stepsPerZone
                    val factor = ((sin(phase * 2 * Math.PI - Math.PI / 2) + 1) / 2)
                    val intensity = (factor * maxIntensity).roundToInt().coerceIn(1, maxIntensity)

                    val builder = gm.glyphFrameBuilder
                    for (ch in group) builder.buildChannel(ch, intensity)
                    gm.toggle(builder.build())
                    delay(stepMs)
                }
            }
            delay(delayMs(200, settings))
        }
    }

    /** Blinking: equal on/off intervals with speed control. */
    private suspend fun animBlinking(percent: Int, settings: GlyphSettings) {
        val gm = glyphManager ?: return
        val intensity = intensityFor(settings.brightness)
        val intervalMs = delayMs(500, settings)

        while (currentCoroutineContext().isActive) {
            val builder = gm.glyphFrameBuilder
            buildFrame(builder, percent, settings, intensity)
            gm.toggle(builder.build())
            delay(intervalMs)
            gm.turnOff()
            delay(intervalMs)
        }
    }

    /** Wave: a lit window sweeps back and forth across the unified channel list. */
    private suspend fun animWave(percent: Int, settings: GlyphSettings) {
        val gm = glyphManager ?: return
        val channels = getActiveChannels(percent, settings)
        if (channels.isEmpty()) return
        val intensity = intensityFor(settings.brightness)
        val stepDelay = delayMs(80, settings)

        val windowSize = (channels.size / 3).coerceAtLeast(1)
        var pos = 0
        var direction = 1

        while (currentCoroutineContext().isActive) {
            val builder = gm.glyphFrameBuilder
            for (offset in 0 until windowSize) {
                val idx = (pos + offset).coerceIn(0, channels.size - 1)
                builder.buildChannel(channels[idx], intensity)
            }
            gm.toggle(builder.build())
            delay(stepDelay)
            pos += direction
            if (pos >= channels.size - windowSize || pos <= 0) direction = -direction
        }
    }

    /** Hourglass: fills one by one, then empties in same direction. */
    private suspend fun animHourglass(percent: Int, settings: GlyphSettings) {
        val gm = glyphManager ?: return
        val channels = getActiveChannels(percent, settings)
        if (channels.isEmpty()) return
        val intensity = intensityFor(settings.brightness)
        val stepDelay = delayMs(80, settings)

        while (currentCoroutineContext().isActive) {
            for (i in channels.indices) {
                val builder = gm.glyphFrameBuilder
                for (j in 0..i) builder.buildChannel(channels[j], intensity)
                gm.toggle(builder.build())
                delay(stepDelay)
            }
            delay(delayMs(400, settings))
            for (i in channels.indices) {
                val builder = gm.glyphFrameBuilder
                for (j in (i + 1) until channels.size) {
                    builder.buildChannel(channels[j], intensity)
                }
                if (i < channels.size - 1) gm.toggle(builder.build()) else gm.turnOff()
                delay(stepDelay)
            }
            delay(delayMs(250, settings))
        }
    }

    /** Smooth fill: LEDs appear one by one with brightness ramp on leading LED. */
    private suspend fun animSmoothFill(percent: Int, settings: GlyphSettings) {
        val gm = glyphManager ?: return
        val channels = getActiveChannels(percent, settings)
        if (channels.isEmpty()) return
        val maxIntensity = intensityFor(settings.brightness)
        val rampSteps = 5
        val rampDelay = delayMs(35, settings)

        while (currentCoroutineContext().isActive) {
            for (i in channels.indices) {
                for (step in 1..rampSteps) {
                    val leadIntensity = maxIntensity * step / rampSteps
                    val builder = gm.glyphFrameBuilder
                    for (j in 0 until i) builder.buildChannel(channels[j], maxIntensity)
                    builder.buildChannel(channels[i], leadIntensity)
                    gm.toggle(builder.build())
                    delay(rampDelay)
                }
            }
            delay(delayMs(800, settings))
            gm.turnOff()
            delay(delayMs(300, settings))
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private fun addProportional(
        builder: GlyphFrame.Builder, channels: List<Int>, percent: Int, intensity: Int
    ) {
        val count = ledCount(channels, percent)
        for (i in 0 until count) builder.buildChannel(channels[i], intensity)
    }

    private fun addAll(builder: GlyphFrame.Builder, channels: List<Int>, intensity: Int) {
        for (ch in channels) builder.buildChannel(ch, intensity)
    }

    private fun ledCount(channels: List<Int>, percent: Int): Int {
        return (percent * channels.size / 100).coerceIn(0, channels.size)
    }
}
