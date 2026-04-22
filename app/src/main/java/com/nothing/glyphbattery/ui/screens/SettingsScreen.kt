package com.nothing.glyphbattery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nothing.glyphbattery.R
import com.nothing.glyphbattery.model.AnimationMode
import com.nothing.glyphbattery.model.AppLanguage
import com.nothing.glyphbattery.model.AutoOffTimer
import com.nothing.glyphbattery.model.FillDirection
import com.nothing.glyphbattery.model.FillMode
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.GlyphZone
import com.nothing.glyphbattery.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: GlyphSettings,
    onBack: () -> Unit,
    onUpdateFillMode: (FillMode) -> Unit,
    onUpdateFillDirection: (FillDirection) -> Unit,
    onUpdateZone: (GlyphZone) -> Unit,
    onUpdateAnimationMode: (AnimationMode) -> Unit,
    onUpdateLanguage: (AppLanguage) -> Unit,
    onUpdateBrightness: (Int) -> Unit,
    onUpdateAnimationSpeed: (Int) -> Unit,
    onUpdateAutoOffTimer: (AutoOffTimer) -> Unit,
    onUpdateAutoStart: (Boolean) -> Unit,
    onUpdateOnlyWhenCharging: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language
            SettingsSection(title = stringResource(R.string.language)) {
                LanguageSelector(
                    selected = settings.language,
                    onSelect = onUpdateLanguage
                )
            }

            // Fill Mode
            SettingsSection(title = stringResource(R.string.fill_mode)) {
                FillModeSelector(
                    selected = settings.fillMode,
                    onSelect = onUpdateFillMode
                )
            }

            // Fill Direction (only for CIRCULAR mode)
            if (settings.fillMode == FillMode.CIRCULAR) {
                SettingsSection(title = stringResource(R.string.fill_direction)) {
                    FillDirectionSelector(
                        selected = settings.fillDirection,
                        onSelect = onUpdateFillDirection
                    )
                }
            }

            // Glyph Zone (only for SINGLE mode)
            if (settings.fillMode == FillMode.SINGLE) {
                SettingsSection(title = stringResource(R.string.glyph_zone)) {
                    GlyphZoneSelector(
                        selected = settings.selectedZone,
                        onSelect = onUpdateZone
                    )
                }
            }

            // Animation
            SettingsSection(title = stringResource(R.string.animation_mode)) {
                AnimationModeSelector(
                    selected = settings.animationMode,
                    onSelect = onUpdateAnimationMode
                )
            }

            // Brightness
            SettingsSection(title = stringResource(R.string.brightness)) {
                BrightnessSlider(
                    brightness = settings.brightness,
                    onUpdate = onUpdateBrightness
                )
            }

            // Animation Speed (only when animation is active)
            if (settings.animationMode != AnimationMode.NONE) {
                SettingsSection(title = stringResource(R.string.animation_speed)) {
                    AnimationSpeedSlider(
                        speed = settings.animationSpeed,
                        onUpdate = onUpdateAnimationSpeed
                    )
                }
            }

            // Auto-off Timer
            SettingsSection(title = stringResource(R.string.auto_off_timer)) {
                AutoOffTimerSelector(
                    selected = settings.autoOffTimer,
                    onSelect = onUpdateAutoOffTimer
                )
            }

            // Toggles
            SettingsSection(title = "") {
                SettingsToggle(
                    label = stringResource(R.string.auto_start),
                    checked = settings.autoStart,
                    onToggle = onUpdateAutoStart
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsToggle(
                    label = stringResource(R.string.only_when_charging),
                    checked = settings.onlyWhenCharging,
                    onToggle = onUpdateOnlyWhenCharging
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NothingDarkGray)
            .padding(16.dp)
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NothingWhite,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        content()
    }
}

@Composable
fun LanguageSelector(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val options = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.lang_system),
        AppLanguage.ENGLISH to stringResource(R.string.lang_english),
        AppLanguage.RUSSIAN to stringResource(R.string.lang_russian)
    )
    options.forEach { (lang, label) ->
        SelectableRow(
            label = label,
            isSelected = selected == lang,
            onClick = { onSelect(lang) }
        )
    }
}

@Composable
fun FillModeSelector(selected: FillMode, onSelect: (FillMode) -> Unit) {
    val options = listOf(
        FillMode.CIRCULAR to (stringResource(R.string.mode_circular) to stringResource(R.string.mode_circular_desc)),
        FillMode.SINGLE to (stringResource(R.string.mode_single) to stringResource(R.string.mode_single_desc))
    )
    options.forEach { (mode, textPair) ->
        SelectableRow(
            label = textPair.first,
            subtitle = textPair.second,
            isSelected = selected == mode,
            onClick = { onSelect(mode) }
        )
    }
}

@Composable
fun GlyphZoneSelector(selected: GlyphZone, onSelect: (GlyphZone) -> Unit) {
    val zoneNames = mapOf(
        GlyphZone.ZONE_A to stringResource(R.string.zone_a),
        GlyphZone.ZONE_B to stringResource(R.string.zone_b),
        GlyphZone.ZONE_C to stringResource(R.string.zone_c)
    )
    GlyphZone.ALL_ZONES.forEach { zone ->
        SelectableRow(
            label = zoneNames[zone] ?: zone.name,
            isSelected = selected == zone,
            onClick = { onSelect(zone) }
        )
    }
}

@Composable
fun AnimationModeSelector(selected: AnimationMode, onSelect: (AnimationMode) -> Unit) {
    val options = listOf(
        AnimationMode.NONE to (stringResource(R.string.anim_none) to stringResource(R.string.anim_none_desc)),
        AnimationMode.BREATHING to (stringResource(R.string.anim_breathing) to stringResource(R.string.anim_breathing_desc)),
        AnimationMode.BLINKING to (stringResource(R.string.anim_blinking) to stringResource(R.string.anim_blinking_desc)),
        AnimationMode.WAVE to (stringResource(R.string.anim_wave) to stringResource(R.string.anim_wave_desc)),
        AnimationMode.HOURGLASS to (stringResource(R.string.anim_hourglass) to stringResource(R.string.anim_hourglass_desc)),
        AnimationMode.SMOOTH_FILL to (stringResource(R.string.anim_smooth_fill) to stringResource(R.string.anim_smooth_fill_desc))
    )
    options.forEach { (mode, textPair) ->
        SelectableRow(
            label = textPair.first,
            subtitle = textPair.second,
            isSelected = selected == mode,
            onClick = { onSelect(mode) }
        )
    }
}

@Composable
fun BrightnessSlider(brightness: Int, onUpdate: (Int) -> Unit) {
    var sliderValue by remember(brightness) { mutableFloatStateOf(brightness / 100f) }

    Column {
        Text(
            text = "${(sliderValue * 100).toInt()}%",
            style = MaterialTheme.typography.bodyLarge,
            color = NothingWhite
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onUpdate((sliderValue * 100).toInt().coerceIn(10, 100)) },
            valueRange = 0.1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = NothingWhite,
                activeTrackColor = NothingWhite,
                inactiveTrackColor = NothingMediumGray
            )
        )
    }
}

@Composable
fun FillDirectionSelector(selected: FillDirection, onSelect: (FillDirection) -> Unit) {
    val options = listOf(
        FillDirection.ABC to (stringResource(R.string.direction_abc) to stringResource(R.string.direction_abc_desc)),
        FillDirection.CBA to (stringResource(R.string.direction_cba) to stringResource(R.string.direction_cba_desc))
    )
    options.forEach { (dir, textPair) ->
        SelectableRow(
            label = textPair.first,
            subtitle = textPair.second,
            isSelected = selected == dir,
            onClick = { onSelect(dir) }
        )
    }
}

@Composable
fun AnimationSpeedSlider(speed: Int, onUpdate: (Int) -> Unit) {
    var sliderValue by remember(speed) { mutableFloatStateOf(speed / 200f) }

    Column {
        Text(
            text = "${(sliderValue * 200).toInt()}%",
            style = MaterialTheme.typography.bodyLarge,
            color = NothingWhite
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onUpdate((sliderValue * 200).toInt().coerceIn(25, 200)) },
            valueRange = 0.125f..1f,
            colors = SliderDefaults.colors(
                thumbColor = NothingWhite,
                activeTrackColor = NothingWhite,
                inactiveTrackColor = NothingMediumGray
            )
        )
    }
}

@Composable
fun AutoOffTimerSelector(selected: AutoOffTimer, onSelect: (AutoOffTimer) -> Unit) {
    val options = listOf(
        AutoOffTimer.OFF to stringResource(R.string.timer_off),
        AutoOffTimer.MIN_15 to stringResource(R.string.timer_15),
        AutoOffTimer.MIN_30 to stringResource(R.string.timer_30),
        AutoOffTimer.HOUR_1 to stringResource(R.string.timer_60),
        AutoOffTimer.HOUR_2 to stringResource(R.string.timer_120)
    )
    options.forEach { (timer, label) ->
        SelectableRow(
            label = label,
            isSelected = selected == timer,
            onClick = { onSelect(timer) }
        )
    }
}

@Composable
fun SettingsToggle(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = NothingWhite
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NothingBlack,
                checkedTrackColor = NothingWhite,
                uncheckedThumbColor = NothingLightGray,
                uncheckedTrackColor = NothingMediumGray
            )
        )
    }
}

@Composable
fun SelectableRow(
    label: String,
    subtitle: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) NothingMediumGray else NothingDarkGray)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = NothingWhite
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NothingLightGray
                )
            }
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = NothingWhite,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}
