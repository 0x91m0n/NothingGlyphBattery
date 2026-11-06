package com.nothing.glyphbattery.model

/** Bounded delay, not a busy retry loop; a recovered connection resets the next retry. */
internal class ReconnectBackoff {
    private var attempt = 0
    fun nextDelayMillis(): Long {
        val delay = (1_000L shl attempt).coerceAtMost(60_000L)
        attempt = (attempt + 1).coerceAtMost(6)
        return delay
    }
    fun reset() { attempt = 0 }
}
