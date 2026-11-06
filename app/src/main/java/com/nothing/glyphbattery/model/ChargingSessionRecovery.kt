package com.nothing.glyphbattery.model

/** Owned by the service event loop, not by the battery BroadcastReceiver. */
class ChargingSessionRecovery {
    private var pluggedIn: Boolean? = null
    private var generation = 0L
    private var pending: Long? = null

    /** The initial sticky snapshot is not a new cable connection. Duplicates do nothing. */
    fun observe(connected: Boolean): Long? {
        if (pluggedIn == connected) return null
        val previous = pluggedIn
        pluggedIn = connected
        generation++
        pending = if (previous == false && connected) generation else null
        return pending
    }

    /** Debounced events cannot revive an unplugged or superseded charging session. */
    fun consume(token: Long): Boolean {
        if (pending != token || pluggedIn != true) return false
        pending = null
        return true
    }
}
