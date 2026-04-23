package com.nothing.glyphbattery.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nothing.glyphbattery.data.SettingsStore
import com.nothing.glyphbattery.model.ServiceMode
import com.nothing.glyphbattery.service.BatteryGlyphService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ChargeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val settings = runBlocking {
            SettingsStore(context).settings.first()
        }
        if (settings.serviceMode != ServiceMode.CHARGING_ONLY) return

        when (intent?.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                if (!BatteryGlyphService.isRunning) {
                    BatteryGlyphService.start(context)
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                if (BatteryGlyphService.isRunning) {
                    BatteryGlyphService.stop(context)
                }
            }
        }
    }
}
