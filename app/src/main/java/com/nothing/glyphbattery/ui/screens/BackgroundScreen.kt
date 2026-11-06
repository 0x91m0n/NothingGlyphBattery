package com.nothing.glyphbattery.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nothing.glyphbattery.R
import com.nothing.glyphbattery.service.BackgroundSettings
import com.nothing.glyphbattery.service.BatteryGlyphServiceStatus
import com.nothing.glyphbattery.ui.theme.*

// Keep the dotted headings; long troubleshooting instructions need normal, legible glyphs.
private val BackgroundBodyStyle = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundScreen(
    serviceStatus: BatteryGlyphServiceStatus,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onBatteryExemption: () -> Unit,
    onAppSettings: () -> Unit,
    onBatterySettings: () -> Unit,
    onCopyReport: () -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var status by remember { mutableStateOf(BackgroundSettings.read(context)) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = BackgroundSettings.read(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(serviceStatus.isRunning) { status = BackgroundSettings.read(context) }

    Scaffold(
        containerColor = NothingBlack,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.background_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, titleContentColor = NothingWhite,
                    navigationIconContentColor = NothingLightGray
                )
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.background_intro), color = NothingLightGray, style = BackgroundBodyStyle)
            SettingsSection(stringResource(R.string.background_checks)) {
                BackgroundCheck(serviceStatus.isRunning,
                    R.string.background_service_active, R.string.background_service_stopped)
                BackgroundCheck(status.batteryExempt,
                    R.string.background_exempt, R.string.background_not_exempt)
                BackgroundCheck(!status.restricted,
                    R.string.background_unrestricted, R.string.background_restricted)
                BackgroundCheck(!status.powerSaver,
                    R.string.background_saver_off, R.string.background_saver_on)
                BackgroundCheck(status.recoveryScheduled,
                    R.string.background_recovery_on, R.string.background_recovery_off)
                if (status.startFailed) {
                    Text(stringResource(R.string.background_start_failed), color = NothingWhite,
                        style = BackgroundBodyStyle, modifier = Modifier.padding(top = 8.dp))
                }
                if (!serviceStatus.isRunning) {
                    Spacer(Modifier.height(8.dp))
                    BackgroundButton(R.string.start_service, onStart, primary = true)
                }
            }
            SettingsSection(stringResource(R.string.background_guide_title)) {
                BackgroundBody(R.string.background_guide)
                Spacer(Modifier.height(12.dp))
                BackgroundButton(R.string.background_allow, onBatteryExemption, primary = true)
                Spacer(Modifier.height(8.dp))
                BackgroundButton(R.string.background_app_settings, onAppSettings)
                Spacer(Modifier.height(8.dp))
                BackgroundButton(R.string.background_battery_settings, onBatterySettings)
            }
            SettingsSection(stringResource(R.string.background_limits_title)) {
                BackgroundBody(R.string.background_limits)
            }
            SettingsSection(stringResource(R.string.background_dark_title)) {
                BackgroundBody(R.string.background_dark)
                Spacer(Modifier.height(12.dp))
                BackgroundBody(R.string.background_copy_help)
                BackgroundButton(R.string.copy_report, onCopyReport)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BackgroundCheck(good: Boolean, goodText: Int, warningText: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(if (good) goodText else warningText),
            style = BackgroundBodyStyle, color = if (good) NothingGreen else NothingWhite)
    }
}

@Composable
private fun BackgroundBody(text: Int) {
    Text(stringResource(text), style = BackgroundBodyStyle, color = NothingLightGray)
}

@Composable
private fun BackgroundButton(text: Int, onClick: () -> Unit, primary: Boolean = false) {
    if (primary) {
        Button(onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NothingWhite, contentColor = NothingBlack)) {
            Text(stringResource(text), fontFamily = FontFamily.SansSerif)
        }
    } else {
        OutlinedButton(onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingWhite)) {
            Text(stringResource(text), fontFamily = FontFamily.SansSerif)
        }
    }
}
