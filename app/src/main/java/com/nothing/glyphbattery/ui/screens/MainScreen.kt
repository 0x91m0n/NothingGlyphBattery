package com.nothing.glyphbattery.ui.screens

import android.os.BatteryManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nothing.glyphbattery.R
import com.nothing.glyphbattery.model.FillDirection
import com.nothing.glyphbattery.model.FillMode
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.GlyphZone
import com.nothing.glyphbattery.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    batteryLevel: Int,
    isCharging: Boolean,
    isServiceRunning: Boolean,
    settings: GlyphSettings,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NothingBlack,
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Battery visualization
            BatteryCircle(
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                settings = settings
            )

            // Status info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.battery_level, batteryLevel),
                    style = MaterialTheme.typography.headlineLarge,
                    color = NothingWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isCharging) stringResource(R.string.charging) else stringResource(R.string.not_charging),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCharging) NothingGreen else NothingLightGray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isServiceRunning) stringResource(R.string.service_running) else stringResource(R.string.service_stopped),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isServiceRunning) NothingGreen else NothingRed
                )
            }

            // Glyph zone preview
            GlyphZonePreview(batteryLevel = batteryLevel, settings = settings)

            // Start/Stop button
            Button(
                onClick = { if (isServiceRunning) onStopService() else onStartService() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) NothingMediumGray else NothingWhite,
                    contentColor = if (isServiceRunning) NothingWhite else NothingBlack
                )
            ) {
                Text(
                    text = if (isServiceRunning) stringResource(R.string.stop_service) else stringResource(R.string.start_service),
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun BatteryCircle(batteryLevel: Int, isCharging: Boolean, settings: GlyphSettings) {
    val animatedLevel by animateFloatAsState(
        targetValue = batteryLevel / 100f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "battery"
    )

    val sweepAngle = animatedLevel * 360f
    val arcColor = when {
        batteryLevel > 60 -> NothingGreen
        batteryLevel > 20 -> Color(0xFFFFA726)
        else -> NothingRed
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Canvas(modifier = Modifier.size(200.dp)) {
            // Background arc
            drawArc(
                color = NothingMediumGray,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                size = Size(size.width, size.height)
            )
            // Battery level arc
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                size = Size(size.width, size.height)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${batteryLevel}%",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = NothingWhite
            )
            if (isCharging) {
                Text("⚡", fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun GlyphZonePreview(batteryLevel: Int, settings: GlyphSettings) {
    val zoneLabels = listOf("A" to GlyphZone.ZONE_A, "B" to GlyphZone.ZONE_B, "C" to GlyphZone.ZONE_C)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NothingDarkGray)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.glyph_zone),
            style = MaterialTheme.typography.titleLarge,
            color = NothingWhite,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            zoneLabels.forEach { (label, zone) ->
                val fillRatio = calculateZoneFill(zone, batteryLevel, settings)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(GlyphBright.copy(alpha = fillRatio.coerceIn(0.05f, 1f)))
                            .border(1.dp, NothingLightGray.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (fillRatio > 0.5f) NothingBlack else NothingLightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${zone.ledCount} LED",
                        fontSize = 10.sp,
                        color = NothingLightGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (settings.fillMode) {
                FillMode.CIRCULAR -> stringResource(R.string.mode_circular)
                FillMode.SINGLE -> stringResource(R.string.mode_single)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = NothingLightGray
        )
    }
}

/**
 * Returns 0.0–1.0 fill ratio for UI preview of each zone.
 * Mirrors the logic in GlyphController.addChannelsForMode().
 */
private fun calculateZoneFill(zone: GlyphZone, batteryPercent: Int, settings: GlyphSettings): Float {
    val p = batteryPercent
    return when (settings.fillMode) {
        FillMode.CIRCULAR -> {
            // Order: first/second/third based on direction
            val order = when (settings.fillDirection) {
                FillDirection.ABC -> listOf(GlyphZone.ZONE_A, GlyphZone.ZONE_B, GlyphZone.ZONE_C)
                FillDirection.CBA -> listOf(GlyphZone.ZONE_C, GlyphZone.ZONE_B, GlyphZone.ZONE_A)
            }
            when (zone) {
                order[0] -> (p / 33f).coerceIn(0f, 1f)
                order[1] -> ((p - 33) / 33f).coerceIn(0f, 1f)
                order[2] -> ((p - 66) / 34f).coerceIn(0f, 1f)
                else -> 0f
            }
        }
        FillMode.SINGLE -> {
            if (zone == settings.selectedZone) p / 100f else 0f
        }
    }
}
