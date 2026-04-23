package com.nothing.glyphbattery.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nothing.glyphbattery.data.SettingsStore
import com.nothing.glyphbattery.model.ServiceMode
import com.nothing.glyphbattery.service.BatteryGlyphService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = runBlocking {
                SettingsStore(context).settings.first()
            }
            if (settings.serviceMode == ServiceMode.ALWAYS_ON) {
                BatteryGlyphService.start(context)
            }
        }
    }
}
