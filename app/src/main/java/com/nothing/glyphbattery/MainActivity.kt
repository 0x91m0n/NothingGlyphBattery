package com.nothing.glyphbattery

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.nothing.glyphbattery.service.BackgroundSettings
import com.nothing.glyphbattery.service.ServiceRecovery
import com.nothing.glyphbattery.service.ServiceRunState
import com.nothing.glyphbattery.ui.screens.BackgroundScreen
import kotlinx.coroutines.flow.first
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nothing.glyphbattery.data.SettingsStore
import com.nothing.glyphbattery.data.AppLanguageManager
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.ServiceMode
import com.nothing.glyphbattery.service.BatteryGlyphService
import com.nothing.glyphbattery.ui.screens.MainScreen
import com.nothing.glyphbattery.ui.screens.DiagnosticsScreen
import com.nothing.glyphbattery.ui.screens.SettingsScreen
import com.nothing.glyphbattery.ui.theme.GlyphBatteryTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = (application as GlyphBatteryApp).settingsStore

        setContent {
            GlyphBatteryTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val mode = settingsStore.settings.first().serviceMode
            // The read may finish before Activity reaches RESUMED or after it pauses.
            // Wait for a real visible-state exemption rather than dropping recovery.
            lifecycle.withResumed {
                val state = ServiceRunState(this@MainActivity)
                val resume = ServiceRecovery.shouldResume(this@MainActivity)
                if (!BatteryGlyphService.status.value.isRunning &&
                    (resume || (mode != ServiceMode.MANUAL && !state.explicitStop))
                ) {
                    BatteryGlyphService.start(this@MainActivity, userInitiated = !resume)
                }
            }
        }
    }

    @Composable
    private fun AppNavigation() {
        val navController = rememberNavController()
        val settings by settingsStore.settings.collectAsStateWithLifecycle(GlyphSettings())
        val developerMode by settingsStore.developerMode.collectAsStateWithLifecycle(false)
        val serviceStatus by BatteryGlyphService.status.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var developerSwitchVisible by rememberSaveable { mutableStateOf(false) }

        var batteryLevel by remember { mutableIntStateOf(getCurrentBattery()) }
        var isCharging by remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    intent?.let {
                        val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
                        if (level >= 0 && scale > 0) {
                            batteryLevel = (level * 100 / scale).coerceIn(0, 100)
                        }
                        isCharging = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
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
                    serviceStatus = serviceStatus,
                    onStartService = ::requestServiceStart,
                    onStopService = { BatteryGlyphService.stop(this@MainActivity) },
                    onNavigateSettings = { navController.navigate("settings") },
                    onOpenBackground = { navController.navigate("background") }
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
                    onUpdateBatteryAnimationMode = { scope.launch { settingsStore.updateBatteryAnimationMode(it) } },
                    onUpdateBatteryBrightness = { scope.launch { settingsStore.updateBatteryBrightness(it) } },
                    onUpdateBatteryFillMode = { scope.launch { settingsStore.updateBatteryFillMode(it) } },
                    onUpdateBatteryFillDirection = { scope.launch { settingsStore.updateBatteryFillDirection(it) } },
                    onUpdateBatteryZone = { scope.launch { settingsStore.updateBatterySelectedZone(it) } },
                    onUpdateLanguage = { lang ->
                        scope.launch {
                            settingsStore.updateLanguage(lang)
                            AppLanguageManager.apply(this@MainActivity, lang)
                        }
                    },
                    onUpdateBrightness = { scope.launch { settingsStore.updateBrightness(it) } },
                    onUpdateAnimationSpeed = { scope.launch { settingsStore.updateAnimationSpeed(it) } },
                    onUpdateAutoOffTimer = { scope.launch { settingsStore.updateAutoOffTimer(it) } },
                    onUpdateCustomAutoOffMinutes = { scope.launch { settingsStore.updateCustomAutoOffMinutes(it) } },
                    onUpdateChargeCompleteAction = { scope.launch { settingsStore.updateChargeCompleteAction(it) } },
                    onUpdateChargeCompleteZone = { scope.launch { settingsStore.updateChargeCompleteZone(it) } },
                    onUpdateChargeFullThreshold = { scope.launch { settingsStore.updateChargeFullThreshold(it) } },
                    onUpdateChargeCompleteTimerPreset = {
                        scope.launch { settingsStore.updateChargeCompleteTimerPreset(it) }
                    },
                    onUpdateCustomChargeCompleteTimerMinutes = {
                        scope.launch { settingsStore.updateCustomChargeCompleteTimerMinutes(it) }
                    },
                    onUpdateServiceMode = { mode ->
                        scope.launch {
                            settingsStore.updateServiceMode(mode)
                            if (mode != ServiceMode.MANUAL) requestServiceStart()
                        }
                    },
                    developerMode = developerMode,
                    developerSwitchVisible = developerSwitchVisible,
                    onRevealDeveloperSwitch = { developerSwitchVisible = true },
                    onUpdateDeveloperMode = { enabled ->
                        if (!enabled) developerSwitchVisible = false
                        scope.launch { settingsStore.updateDeveloperMode(enabled) }
                    },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                    onOpenBackground = { navController.navigate("background") }
                )
            }
            composable("background") {
                BackgroundScreen(
                    serviceStatus = serviceStatus,
                    onBack = { navController.popBackStack() },
                    onStart = ::requestServiceStart,
                    onBatteryExemption = { openSystemSettings { BackgroundSettings.requestBatteryExemption(this@MainActivity) } },
                    onAppSettings = { openSystemSettings { BackgroundSettings.appDetails(this@MainActivity) } },
                    onBatterySettings = { openSystemSettings { BackgroundSettings.batterySettings(this@MainActivity) } },
                    onCopyReport = ::copyDiagnosticReport
                )
            }
            composable("diagnostics") {
                if (!developerMode) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    DiagnosticsScreen(
                        status = serviceStatus,
                        onBack = { navController.popBackStack() },
                        onReconnect = {
                            BatteryGlyphService.requestGlyphReconnect(this@MainActivity)
                        },
                        onZoneSelfTest = {
                            BatteryGlyphService.requestZoneSelfTest(this@MainActivity)
                        },
                        onCopyReport = ::copyDiagnosticReport
                    )
                }
            }
        }
    }

    private fun getCurrentBattery(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }

    private fun requestServiceStart() {
        if (!BatteryGlyphService.start(this)) {
            Toast.makeText(this, R.string.background_start_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openSystemSettings(open: () -> Boolean) {
        if (!open()) Toast.makeText(this, R.string.background_settings_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun copyDiagnosticReport() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(
                getString(R.string.diagnostics),
                BatteryGlyphService.diagnosticReport(this)
            )
        )
        Toast.makeText(this, R.string.report_copied, Toast.LENGTH_SHORT).show()
    }

}
