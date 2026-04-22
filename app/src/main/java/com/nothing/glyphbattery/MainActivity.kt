package com.nothing.glyphbattery

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nothing.glyphbattery.data.SettingsStore
import com.nothing.glyphbattery.model.AppLanguage
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.service.BatteryGlyphService
import com.nothing.glyphbattery.ui.screens.MainScreen
import com.nothing.glyphbattery.ui.screens.SettingsScreen
import com.nothing.glyphbattery.ui.theme.GlyphBatteryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = (application as GlyphBatteryApp).settingsStore
        requestNotificationPermission()

        setContent {
            GlyphBatteryTheme {
                AppNavigation()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val settings = try {
            kotlinx.coroutines.runBlocking {
                SettingsStore(newBase).settings.first()
            }
        } catch (_: Exception) { GlyphSettings() }

        if (settings.language != AppLanguage.SYSTEM) {
            val locale = Locale(settings.language.code)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    @Composable
    private fun AppNavigation() {
        val navController = rememberNavController()
        val settings by settingsStore.settings.collectAsStateWithLifecycle(GlyphSettings())
        val scope = rememberCoroutineScope()

        var batteryLevel by remember { mutableIntStateOf(getCurrentBattery()) }
        var isCharging by remember { mutableStateOf(false) }
        var isServiceRunning by remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    intent?.let {
                        val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                        batteryLevel = (level * 100 / scale).coerceIn(0, 100)
                        isCharging = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ==
                                BatteryManager.BATTERY_STATUS_CHARGING
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            registerReceiver(receiver, filter)
            onDispose { unregisterReceiver(receiver) }
        }

        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainScreen(
                    batteryLevel = batteryLevel,
                    isCharging = isCharging,
                    isServiceRunning = isServiceRunning,
                    settings = settings,
                    onStartService = {
                        BatteryGlyphService.start(this@MainActivity)
                        isServiceRunning = true
                    },
                    onStopService = {
                        BatteryGlyphService.stop(this@MainActivity)
                        isServiceRunning = false
                    },
                    onNavigateSettings = { navController.navigate("settings") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onUpdateFillMode = { scope.launch { settingsStore.updateFillMode(it) } },
                    onUpdateFillDirection = { scope.launch { settingsStore.updateFillDirection(it) } },
                    onUpdateZone = { scope.launch { settingsStore.updateSelectedZone(it) } },
                    onUpdateAnimationMode = { scope.launch { settingsStore.updateAnimationMode(it) } },
                    onUpdateLanguage = { lang ->
                        scope.launch {
                            settingsStore.updateLanguage(lang)
                            recreate()
                        }
                    },
                    onUpdateBrightness = { scope.launch { settingsStore.updateBrightness(it) } },
                    onUpdateAnimationSpeed = { scope.launch { settingsStore.updateAnimationSpeed(it) } },
                    onUpdateAutoOffTimer = { scope.launch { settingsStore.updateAutoOffTimer(it) } },
                    onUpdateCustomAutoOffMinutes = { scope.launch { settingsStore.updateCustomAutoOffMinutes(it) } },
                    onUpdateChargeCompleteAction = { scope.launch { settingsStore.updateChargeCompleteAction(it) } },
                    onUpdateChargeCompleteZone = { scope.launch { settingsStore.updateChargeCompleteZone(it) } },
                    onUpdateAutoStart = { scope.launch { settingsStore.updateAutoStart(it) } },
                    onUpdateOnlyWhenCharging = { scope.launch { settingsStore.updateOnlyWhenCharging(it) } },
                    onUpdateShowNotification = { scope.launch { settingsStore.updateShowNotification(it) } }
                )
            }
        }
    }

    private fun getCurrentBattery(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
