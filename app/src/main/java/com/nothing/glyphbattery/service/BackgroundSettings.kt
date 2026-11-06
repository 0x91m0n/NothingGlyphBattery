package com.nothing.glyphbattery.service

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

internal data class BackgroundStatus(
    val batteryExempt: Boolean,
    val restricted: Boolean,
    val powerSaver: Boolean,
    val recoveryScheduled: Boolean,
    val startFailed: Boolean
)

internal object BackgroundSettings {
    fun read(context: Context): BackgroundStatus {
        val power = context.getSystemService(PowerManager::class.java)
        return BackgroundStatus(
            batteryExempt = power.isIgnoringBatteryOptimizations(context.packageName),
            restricted = context.getSystemService(ActivityManager::class.java).isBackgroundRestricted,
            powerSaver = power.isPowerSaveMode,
            recoveryScheduled = ServiceRecovery.isScheduled(context),
            startFailed = ServiceRunState(context).lastStartFailure.isNotEmpty()
        )
    }

    fun requestBatteryExemption(context: Context): Boolean = open(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
    )

    fun appDetails(context: Context): Boolean = open(
        context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun batterySettings(context: Context): Boolean = open(
        context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Settings.ACTION_SETTINGS)
    )

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    private fun open(context: Context, vararg intents: Intent): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
                // OEM Settings may omit a public action. Try the next documented fallback.
            } catch (_: SecurityException) {
                // Never guess private vendor components or bypass a protected Settings screen.
            }
        }
        return false
    }
}
