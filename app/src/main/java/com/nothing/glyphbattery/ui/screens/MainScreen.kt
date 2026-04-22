package com.nothing.glyphbattery.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nothing.glyphbattery.R
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
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
                title = {
                    Text(
                        stringResource(R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Background image with darkening
            Image(
                painter = painterResource(R.drawable.bg_main),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.15f
            )
            // Gradient overlay — darker at bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                NothingBlack.copy(alpha = 0.6f),
                                NothingBlack
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Battery ring
            BatteryRing(
                batteryLevel = batteryLevel,
                isCharging = isCharging
            )

            // Status row
            StatusInfo(
                isCharging = isCharging,
                isServiceRunning = isServiceRunning
            )

            // Start/Stop button with animation
            ServiceButton(
                isRunning = isServiceRunning,
                onStart = onStartService,
                onStop = onStopService
            )
        }
        }
    }
}

@Composable
private fun BatteryRing(batteryLevel: Int, isCharging: Boolean) {
    val animatedLevel by animateFloatAsState(
        targetValue = batteryLevel / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "battery"
    )

    val sweepAngle = animatedLevel * 360f

    val arcColor = when {
        batteryLevel >= 100 -> NothingGreen
        batteryLevel > 60  -> NothingWhite
        batteryLevel > 20  -> Color(0xFFFFA726)
        else               -> NothingRed
    }

    // Subtle pulsing glow when charging
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        // Glow behind ring when charging
        if (isCharging) {
            Canvas(modifier = Modifier.size(240.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(arcColor.copy(alpha = glowAlpha), Color.Transparent),
                        radius = size.width / 2f
                    )
                )
            }
        }

        Canvas(modifier = Modifier.size(220.dp)) {
            // Background ring
            drawArc(
                color = NothingMediumGray,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                size = Size(size.width, size.height)
            )
            // Battery arc
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                size = Size(size.width, size.height)
            )
        }

        // Center content
        Text(
            text = "${batteryLevel}",
            style = MaterialTheme.typography.displayLarge,
            color = NothingWhite
        )
    }
}

@Composable
private fun StatusInfo(isCharging: Boolean, isServiceRunning: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Pulsing dot
            val dotColor = if (isServiceRunning) NothingGreen else NothingRed
            val infiniteTransition = rememberInfiniteTransition(label = "dot")
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotAlpha"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(if (isServiceRunning) dotAlpha else 1f)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = (if (isServiceRunning) stringResource(R.string.service_running) else stringResource(R.string.service_stopped)).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = NothingLightGray
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        AnimatedContent(
            targetState = isCharging,
            transitionSpec = {
                fadeIn(tween(400)) + slideInVertically { it / 2 } togetherWith
                        fadeOut(tween(200)) + slideOutVertically { -it / 2 }
            },
            label = "chargingStatus"
        ) { charging ->
            Text(
                text = (if (charging) stringResource(R.string.charging) else stringResource(R.string.not_charging)).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = if (charging) NothingGreen else NothingLightGray.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ServiceButton(isRunning: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val transition = updateTransition(targetState = isRunning, label = "btnTransition")

    val containerColor by transition.animateColor(
        transitionSpec = { tween(400, easing = FastOutSlowInEasing) },
        label = "btnBg"
    ) { running -> if (running) Color.Transparent else NothingWhite }

    val contentColor by transition.animateColor(
        transitionSpec = { tween(400, easing = FastOutSlowInEasing) },
        label = "btnFg"
    ) { running -> if (running) NothingWhite else NothingBlack }

    val borderAlpha by transition.animateFloat(
        transitionSpec = { tween(400, easing = FastOutSlowInEasing) },
        label = "btnBorder"
    ) { running -> if (running) 0.4f else 0f }

    val scale by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) {
                keyframes {
                    durationMillis = 400
                    1f at 0
                    0.92f at 100
                    1.04f at 250
                    1f at 400
                }
            } else {
                keyframes {
                    durationMillis = 400
                    1f at 0
                    0.95f at 120
                    1.06f at 280
                    1f at 400
                }
            }
        },
        label = "btnScale"
    ) { 1f }

    Button(
        onClick = { if (isRunning) onStop() else onStart() },
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (borderAlpha > 0f) Modifier.border(
                    1.dp, NothingLightGray.copy(alpha = borderAlpha), RoundedCornerShape(50)
                ) else Modifier
            ),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
    ) {
        AnimatedContent(
            targetState = isRunning,
            transitionSpec = {
                (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.8f)) togetherWith
                        (fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f))
            },
            label = "btnText"
        ) { running ->
            Text(
                text = (if (running) stringResource(R.string.stop_service) else stringResource(R.string.start_service)).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontSize = 15.sp
            )
        }
    }
}
