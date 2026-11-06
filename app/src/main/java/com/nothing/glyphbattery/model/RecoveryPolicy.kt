package com.nothing.glyphbattery.model

/** Pure recovery gates; explicit user stops must never turn into automatic restarts. */
internal object RecoveryPolicy {
    fun shouldResume(requested: Boolean, explicitStop: Boolean, systemUserStop: Boolean): Boolean =
        requested && !explicitStop && !systemUserStop

    fun canStartInBackground(exempt: Boolean, restricted: Boolean): Boolean = exempt && !restricted
}
