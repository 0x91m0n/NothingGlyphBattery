package com.nothing.glyphbattery.service

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.nothing.glyphbattery.model.ServiceMode
import com.nothing.glyphbattery.model.RecoveryPolicy

/** A small, local-only run intent. Not included in cloud backup or device transfer. */
internal class ServiceRunState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("service_runtime", Context.MODE_PRIVATE)
    val requested: Boolean get() = prefs.getBoolean("requested", false)
    val explicitStop: Boolean get() = prefs.getBoolean("explicit_stop", false)
    val requestedAt: Long get() = prefs.getLong("requested_at", 0L)
    var lastStartFailure: String
        get() = prefs.getString("start_failure", "").orEmpty()
        set(value) { prefs.edit().putString("start_failure", value).apply() }

    fun readGeneralTimer() = com.nothing.glyphbattery.model.GeneralTimerRuntime(
        prefs.getLong("general_deadline", 0L), prefs.getString("general_configuration", "").orEmpty()
    )

    fun saveGeneralTimer(runtime: com.nothing.glyphbattery.model.GeneralTimerRuntime) {
        prefs.edit().putLong("general_deadline", runtime.deadline)
            .putString("general_configuration", runtime.configuration).apply()
    }

    fun request() {
        if (!BatteryGlyphService.status.value.isRunning) {
            saveGeneralTimer(com.nothing.glyphbattery.model.GeneralTimerRuntime())
        }
        prefs.edit().putBoolean("requested", true).putBoolean("explicit_stop", false)
            .putLong("requested_at", System.currentTimeMillis()).putString("start_failure", "").apply()
    }

    fun stop() {
        saveGeneralTimer(com.nothing.glyphbattery.model.GeneralTimerRuntime())
        prefs.edit().putBoolean("requested", false).putBoolean("explicit_stop", true).apply()
    }

    fun resetForBoot(mode: ServiceMode) {
        if (mode == ServiceMode.MANUAL) {
            stop()
            return
        }
        // Preserve an automatic session's absolute timer deadline across reboot. A genuinely
        // new session (no previous run request) still receives a fresh deadline on request().
        if (!requested) request()
        else prefs.edit().putBoolean("explicit_stop", false).apply()
    }
}

internal object ServiceRecovery {
    private const val JOB_ID = 1002
    private const val INTERVAL_MS = 15 * 60 * 1000L

    fun ensureScheduled(context: Context): Boolean = try {
        schedule(context)
    } catch (error: RuntimeException) {
        // A scheduler quota/OS failure must not crash a user-initiated foreground start.
        Log.w("ServiceRecovery", "Unable to schedule recovery", error)
        false
    }

    private fun schedule(context: Context): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return true
        val result = scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, GlyphRecoveryJob::class.java))
                .setPeriodic(INTERVAL_MS)
                .setPersisted(true)
                .build()
        )
        if (result != JobScheduler.RESULT_SUCCESS) Log.w("ServiceRecovery", "Recovery job not scheduled")
        return result == JobScheduler.RESULT_SUCCESS
    }

    fun cancel(context: Context) = context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)

    fun isScheduled(context: Context): Boolean =
        context.getSystemService(JobScheduler::class.java).getPendingJob(JOB_ID) != null

    fun latestExit(context: Context): ApplicationExitInfo? = runCatching {
        context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
    }.getOrNull()

    fun shouldResume(context: Context): Boolean {
        val state = ServiceRunState(context)
        val exit = latestExit(context)
        val systemStop = exit != null && exit.timestamp >= state.requestedAt &&
            exit.reason == ApplicationExitInfo.REASON_USER_REQUESTED
        if (systemStop) {
            state.stop()
            cancel(context)
        }
        return RecoveryPolicy.shouldResume(state.requested, state.explicitStop, systemStop)
    }

    fun canStartInBackground(context: Context): Boolean = RecoveryPolicy.canStartInBackground(
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        context.getSystemService(ActivityManager::class.java).isBackgroundRestricted
    )
}

/** Best effort, not an exact alarm or a promise of immediate recovery. Never overrides Stop. */
class GlyphRecoveryJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        if (!ServiceRecovery.shouldResume(this)) {
            ServiceRecovery.cancel(this)
        } else if (!BatteryGlyphService.status.value.isRunning && ServiceRecovery.canStartInBackground(this)) {
            BatteryGlyphService.start(this, userInitiated = false)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
