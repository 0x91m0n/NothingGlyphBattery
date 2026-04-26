package com.nothing.glyphbattery.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.nothing.glyphbattery.MainActivity
import com.nothing.glyphbattery.R
import com.nothing.glyphbattery.data.SettingsStore
import com.nothing.glyphbattery.glyph.GlyphController
import com.nothing.glyphbattery.model.AutoOffTimer
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.ServiceMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BatteryGlyphService : Service() {

    companion object {
        private const val TAG = "BatteryGlyphService"
        private const val CHANNEL_ID = "glyph_battery_silent"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.nothing.glyphbattery.START"
        const val ACTION_STOP = "com.nothing.glyphbattery.STOP"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, BatteryGlyphService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BatteryGlyphService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private lateinit var glyphController: GlyphController
    private lateinit var settingsStore: SettingsStore
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentSettings = GlyphSettings()
    private var autoOffJob: Job? = null
    private var glyphsDisabledByTimer = false
    private var previousBatteryPercent = -1
    private var celebrationPlayed = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { handleBatteryUpdate(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        glyphController = GlyphController(this)
        settingsStore = SettingsStore(this)
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Load settings before startForeground so correct channel is used
                runBlocking {
                    currentSettings = settingsStore.settings.first()
                }
                startForeground(NOTIFICATION_ID, buildNotification())
                initService()
            }
        }
        return START_STICKY
    }

    private fun initService() {
        glyphController.init {
            Log.d(TAG, "Glyph ready, registering battery receiver")
            registerBatteryReceiver()
            observeSettings()
            restartAutoOffTimer()
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsStore.settings.collect { settings ->
                val timerChanged = currentSettings.autoOffTimer != settings.autoOffTimer ||
                    (settings.autoOffTimer == AutoOffTimer.CUSTOM && currentSettings.customAutoOffMinutes != settings.customAutoOffMinutes)
                currentSettings = settings
                if (timerChanged) restartAutoOffTimer()
                if (!glyphsDisabledByTimer) {
                    val batteryPercent = getCurrentBatteryLevel()
                    glyphController.updateBatteryGlyph(batteryPercent, currentSettings)
                }
            }
        }
    }

    private fun restartAutoOffTimer() {
        autoOffJob?.cancel()
        glyphsDisabledByTimer = false
        val minutes = if (currentSettings.autoOffTimer == AutoOffTimer.CUSTOM)
            currentSettings.customAutoOffMinutes else currentSettings.autoOffTimer.minutes
        if (minutes > 0) {
            autoOffJob = serviceScope.launch {
                delay(minutes * 60_000L)
                Log.d(TAG, "Auto-off timer expired, turning off glyphs")
                glyphsDisabledByTimer = true
                glyphController.turnOff()
            }
        }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(batteryReceiver, filter)

        // Apply immediately
        val batteryPercent = getCurrentBatteryLevel()
        glyphController.updateBatteryGlyph(batteryPercent, currentSettings)
    }

    private fun handleBatteryUpdate(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = (level * 100 / scale).coerceIn(0, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val isPluggedIn = plugged != 0
        val isCharging = isPluggedIn
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL || percent == 100

        // 100% charge complete action
        if (isFull && !celebrationPlayed) {
            celebrationPlayed = true
            glyphsDisabledByTimer = false
            glyphController.showChargeComplete(currentSettings)
            previousBatteryPercent = percent
            return
        }
        if (!isCharging && !isFull) {
            celebrationPlayed = false
        }

        if (glyphsDisabledByTimer) {
            // Timer expired — keep glyphs off (except for 100% celebration above)
        } else if (currentSettings.serviceMode == ServiceMode.CHARGING_ONLY && !isCharging) {
            glyphController.turnOff()
        } else {
            glyphController.updateBatteryGlyph(percent, currentSettings)
        }

        previousBatteryPercent = percent
    }

    private fun getCurrentBatteryLevel(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        // Delete old channels if they exist
        nm.deleteNotificationChannel("glyph_battery_channel")
        nm.deleteNotificationChannel("glyph_battery_channel_silent")
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            description = "Required for background service"
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        releaseWakeLock()
        serviceScope.cancel()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        glyphController.release()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GlyphBattery::ServiceLock"
        ).apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
