package com.nothing.glyphbattery.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nothing.glyphbattery.R
import com.nothing.glyphbattery.glyph.GlyphConnectionState
import com.nothing.glyphbattery.service.BatteryGlyphServiceStatus
import com.nothing.glyphbattery.ui.theme.NothingBlack
import com.nothing.glyphbattery.ui.theme.NothingLightGray
import com.nothing.glyphbattery.ui.theme.NothingWhite
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    status: BatteryGlyphServiceStatus,
    onBack: () -> Unit,
    onReconnect: () -> Unit,
    onZoneSelfTest: () -> Unit,
    onCopyReport: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.diagnostics).uppercase(),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = NothingLightGray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = NothingWhite
                )
            )
        },
        containerColor = NothingBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection(title = stringResource(R.string.diagnostics)) {
                DiagnosticRow(stringResource(R.string.diagnostic_service), status.health.name)
                DiagnosticRow(stringResource(R.string.diagnostic_sdk), status.sdkConnection.toString())
                DiagnosticRow(stringResource(R.string.diagnostic_target), status.target.name)
                DiagnosticRow(
                    stringResource(R.string.diagnostic_block_reason),
                    status.blockReason?.name ?: stringResource(R.string.diagnostic_none)
                )
                DiagnosticRow(
                    stringResource(R.string.diagnostic_battery),
                    status.batteryPercent?.let { percent ->
                        "$percent% · plugged=${status.isPluggedIn}"
                    } ?: stringResource(R.string.diagnostic_inactive)
                )
                DiagnosticRow(
                    stringResource(R.string.diagnostic_general_timer),
                    formatTimer(status.generalTimerDeadlineEpochMillis, now)
                )
                DiagnosticRow(
                    stringResource(R.string.diagnostic_charge_timer),
                    if (status.chargeCompleteTimerExpired) {
                        stringResource(R.string.diagnostic_expired)
                    } else {
                        formatTimer(status.chargeCompleteTimerDeadlineEpochMillis, now)
                    }
                )
                DiagnosticRow(
                    stringResource(R.string.diagnostic_last_render),
                    buildString {
                        append(status.lastRenderResult.name)
                        status.lastRenderEpochMillis?.let { timestamp ->
                            append(" · ")
                            append(DateFormat.getTimeInstance().format(Date(timestamp)))
                        }
                    }
                )
                DiagnosticRow(
                    stringResource(R.string.diagnostic_errors),
                    status.errorCount.toString()
                )
            }

            SettingsSection(title = "Glyph SDK") {
                if (!status.isRunning) {
                    Text(
                        stringResource(R.string.start_service_for_diagnostics),
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingLightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                DiagnosticActionButton(
                    text = stringResource(R.string.glyph_reconnect),
                    enabled = status.isRunning && status.developerAction == null,
                    onClick = onReconnect
                )
                Spacer(modifier = Modifier.height(8.dp))
                DiagnosticActionButton(
                    text = stringResource(R.string.zone_self_test),
                    enabled = status.sdkConnection == GlyphConnectionState.Ready &&
                        status.developerAction == null,
                    onClick = onZoneSelfTest
                )
                Spacer(modifier = Modifier.height(8.dp))
                DiagnosticActionButton(
                    text = stringResource(R.string.copy_report),
                    enabled = true,
                    onClick = onCopyReport
                )
            }

            SettingsSection(title = stringResource(R.string.diagnostic_events)) {
                if (status.recentEvents.isEmpty()) {
                    Text(
                        stringResource(R.string.diagnostic_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingLightGray
                    )
                } else {
                    status.recentEvents.forEach { event ->
                        Text(
                            text = event,
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingLightGray,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = NothingLightGray,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = NothingWhite,
            modifier = Modifier.weight(1.25f)
        )
    }
}

@Composable
private fun DiagnosticActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = NothingWhite,
            contentColor = NothingBlack
        )
    ) {
        Text(text.uppercase())
    }
}

@Composable
private fun formatTimer(deadlineEpochMillis: Long?, now: Long): String {
    if (deadlineEpochMillis == null) return stringResource(R.string.diagnostic_inactive)
    val remainingSeconds = ((deadlineEpochMillis - now).coerceAtLeast(0L) + 999L) / 1_000L
    if (remainingSeconds == 0L) return stringResource(R.string.diagnostic_expired)
    val hours = remainingSeconds / 3_600L
    val minutes = (remainingSeconds % 3_600L) / 60L
    val seconds = remainingSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
