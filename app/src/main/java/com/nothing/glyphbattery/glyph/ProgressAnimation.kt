package com.nothing.glyphbattery.glyph

import com.nothing.glyphbattery.model.AnimationMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Pure, periodic envelope for the next LED. The renderer owns timing and SDK writes. */
internal object ProgressAnimation {
    const val FRAME_MS = 40L

    fun factor(mode: AnimationMode, elapsedMillis: Long): Double {
        require(mode.keepsFilledLeds)
        val period = when (mode) {
            AnimationMode.PROGRESS_BREATHING -> 2_200L
            AnimationMode.PROGRESS_BLINKING -> 1_000L
            AnimationMode.PROGRESS_WAVE -> 1_400L
            AnimationMode.PROGRESS_HOURGLASS -> 1_600L
            AnimationMode.PROGRESS_SMOOTH_FILL -> 1_800L
            else -> error("Not a progress animation")
        }
        val phase = Math.floorMod(elapsedMillis, period).toDouble() / period
        return when (mode) {
            AnimationMode.PROGRESS_BREATHING -> (1 - cos(2 * PI * phase)) / 2
            AnimationMode.PROGRESS_BLINKING -> if (phase < 0.5) 1.0 else 0.0
            // One LED cannot show a spatial wave: a soft crest passes across its brightness.
            AnimationMode.PROGRESS_WAVE -> sin(PI * phase).let { it * it * it * it }
            AnimationMode.PROGRESS_HOURGLASS -> 1 - kotlin.math.abs(2 * phase - 1)
            AnimationMode.PROGRESS_SMOOTH_FILL -> when {
                phase < 0.65 -> (phase / 0.65).let { it * it * (3 - 2 * it) }
                phase < 0.85 -> 1.0
                else -> 0.0
            }
            else -> error("Not a progress animation")
        }.coerceIn(0.0, 1.0)
    }
}
