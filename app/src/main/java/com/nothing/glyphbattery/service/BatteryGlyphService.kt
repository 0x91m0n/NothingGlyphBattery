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
import android.util.Log
import com.nothing.glyphbattery.MainActivity
import com.nothing.glyphbattery.R
import com.nothing.glyphbattery.data.SettingsStore
import com.nothing.glyphbattery.glyph.GlyphController
import com.nothing.glyphbattery.model.AutoOffTimer
import com.nothing.glyphbattery.model.GlyphSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BatteryGlyphService : Service() {

    companion object {
        private const val TAG = "BatteryGlyphService"
        private const val CHANNEL_ID = "glyph_battery_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.nothing.glyphbattery.START"
        const val ACTION_STOP = "com.nothing.glyphbattery.STOP"

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

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { handleBatteryUpdate(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        glyphController = GlyphController(this)
        settingsStore = SettingsStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(0))
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
                val timerChanged = currentSettings.autoOffTimer != settings.autoOffTimer
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
        if (currentSettings.autoOffTimer != AutoOffTimer.OFF) {
            autoOffJob = serviceScope.launch {
                delay(currentSettings.autoOffTimer.minutes * 60_000L)
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
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL || percent == 100

        // 100% celebration: blink all 2x then stay on
        if (isFull && !celebrationPlayed) {
            celebrationPlayed = true
            glyphsDisabledByTimer = false // override timer for celebration
            glyphController.playCelebration(currentSettings.brightness)
            updateNotification(percent)
            previousBatteryPercent = percent
            return
        }
        // Reset celebration flag when unplugged or battery drops
        if (!isCharging && !isFull) {
            celebrationPlayed = false
        }

        if (glyphsDisabledByTimer) {
            // Timer expired — keep glyphs off (except for 100% celebration above)
        } else if (currentSettings.onlyWhenCharging && !isCharging) {
            glyphController.turnOff()
        } else {
            glyphController.updateBatteryGlyph(percent, currentSettings)
        }

        previousBatteryPercent = percent
        updateNotification(percent)
    }

    private fun getCurrentBatteryLevel(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(batteryPercent: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, batteryPercent))
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(batteryPercent: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(batteryPercent))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        glyphController.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
