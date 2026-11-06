package com.nothing.glyphbattery.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nothing.glyphbattery.data.SettingsStore
import com.nothing.glyphbattery.model.ServiceMode
import com.nothing.glyphbattery.service.BatteryGlyphService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(context.applicationContext).settings.first()
                val app = context.applicationContext
                val state = com.nothing.glyphbattery.service.ServiceRunState(app)
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                    state.resetForBoot(settings.serviceMode)
                } else {
                    // Observe a system Stop before considering a new automatic-mode request.
                    // A package update must preserve the active session and its timer deadline.
                    com.nothing.glyphbattery.service.ServiceRecovery.shouldResume(app)
                    if (settings.serviceMode != ServiceMode.MANUAL && !state.requested && !state.explicitStop) {
                        state.request()
                    }
                }
                if (com.nothing.glyphbattery.service.ServiceRecovery.shouldResume(app)) {
                    BatteryGlyphService.start(app, userInitiated = false)
                } else {
                    com.nothing.glyphbattery.service.ServiceRecovery.cancel(app)
                }
            } catch (error: Exception) {
                Log.e("BootReceiver", "Unable to restore Glyph service", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
