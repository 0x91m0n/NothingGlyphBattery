package com.nothing.glyphbattery.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nothing.glyphbattery.R
import com.nothing.glyphbattery.model.AnimationMode
import com.nothing.glyphbattery.model.AppLanguage
import com.nothing.glyphbattery.model.AutoOffTimer
import com.nothing.glyphbattery.model.ChargeCompleteAction
import com.nothing.glyphbattery.model.FillDirection
import com.nothing.glyphbattery.model.FillMode
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.GlyphZone
import com.nothing.glyphbattery.model.ServiceMode
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
    onUpdateChargingAnimationMode: (AnimationMode) -> Unit,
    onUpdateBatteryBrightness: (Int) -> Unit,
    onUpdateBatteryFillMode: (FillMode) -> Unit,
    onUpdateBatteryFillDirection: (FillDirection) -> Unit,
    onUpdateBatteryZone: (GlyphZone) -> Unit,
    onUpdateLanguage: (AppLanguage) -> Unit,
    onUpdateBrightness: (Int) -> Unit,
    onUpdateAnimationSpeed: (Int) -> Unit,
    onUpdateAutoOffTimer: (AutoOffTimer) -> Unit,
    onUpdateCustomAutoOffMinutes: (Int) -> Unit,
    onUpdateChargeCompleteAction: (ChargeCompleteAction) -> Unit,
    onUpdateChargeCompleteZone: (GlyphZone) -> Unit,
    onUpdateChargeFullThreshold: (Int) -> Unit,
    onUpdateServiceMode: (ServiceMode) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings).uppercase(),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            AnimatedVisibility(
                visible = settings.fillMode == FillMode.CIRCULAR,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingsSection(title = stringResource(R.string.fill_direction)) {
                    FillDirectionSelector(
                        selected = settings.fillDirection,
                        onSelect = onUpdateFillDirection
                    )
                }
            }

            // Glyph Zone (only for SINGLE mode)
            AnimatedVisibility(
                visible = settings.fillMode == FillMode.SINGLE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
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
            AnimatedVisibility(
                visible = settings.animationMode != AnimationMode.NONE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
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
                    customMinutes = settings.customAutoOffMinutes,
                    onSelect = onUpdateAutoOffTimer,
                    onUpdateCustomMinutes = onUpdateCustomAutoOffMinutes
                )
            }

            // Charge Complete
            SettingsSection(title = stringResource(R.string.charge_complete)) {
                ChargeCompleteSelector(
                    selected = settings.chargeCompleteAction,
                    onSelect = onUpdateChargeCompleteAction
                )
            }

            // Zone picker for single-zone charge complete
            AnimatedVisibility(
                visible = settings.chargeCompleteAction == ChargeCompleteAction.SINGLE_ZONE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingsSection(title = stringResource(R.string.charge_complete_zone)) {
                    GlyphZoneSelector(
                        selected = settings.chargeCompleteZone,
                        onSelect = onUpdateChargeCompleteZone
                    )
                }
            }

            // Charge full threshold
            SettingsSection(title = stringResource(R.string.charge_full_threshold)) {
                ChargeFullThresholdSelector(
                    selected = settings.chargeFullThreshold,
                    onSelect = onUpdateChargeFullThreshold
                )
            }

            // Service Mode
            SettingsSection(title = stringResource(R.string.service_mode)) {
                ServiceModeSelector(
                    selected = settings.serviceMode,
                    onSelect = onUpdateServiceMode
                )
            }

            // Battery Animation (only in ALWAYS_ON_CHARGING mode)
            AnimatedVisibility(
                visible = settings.serviceMode == ServiceMode.ALWAYS_ON_CHARGING,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    SettingsSection(title = stringResource(R.string.charging_animation_mode)) {
                        AnimationModeSelector(
                            selected = settings.chargingAnimationMode,
                            onSelect = onUpdateChargingAnimationMode
                        )
                    }
                    SettingsSection(title = stringResource(R.string.battery_brightness)) {
                        BrightnessSlider(
                            brightness = settings.batteryBrightness,
                            onUpdate = onUpdateBatteryBrightness
                        )
                    }
                    SettingsSection(title = stringResource(R.string.battery_fill_mode)) {
                        FillModeSelector(
                            selected = settings.batteryFillMode,
                            onSelect = onUpdateBatteryFillMode
                        )
                    }
                    AnimatedVisibility(
                        visible = settings.batteryFillMode == FillMode.CIRCULAR,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        SettingsSection(title = stringResource(R.string.fill_direction)) {
                            FillDirectionSelector(
                                selected = settings.batteryFillDirection,
                                onSelect = onUpdateBatteryFillDirection
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = settings.batteryFillMode == FillMode.SINGLE,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        SettingsSection(title = stringResource(R.string.battery_glyph_zone)) {
                            GlyphZoneSelector(
                                selected = settings.batterySelectedZone,
                                onSelect = onUpdateBatteryZone
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "made by 0x91m0n",
                style = MaterialTheme.typography.bodySmall,
                color = NothingLightGray.copy(alpha = 0.2f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, NothingCardBorder, RoundedCornerShape(20.dp))
            .background(NothingDarkGray)
            .padding(16.dp)
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = NothingLightGray,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val options = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.lang_system),
        AppLanguage.ENGLISH to stringResource(R.string.lang_english),
        AppLanguage.RUSSIAN to stringResource(R.string.lang_russian)
    )
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .background(NothingMediumGray)
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .menuAnchor(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = NothingWhite
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = NothingLightGray
            )
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(NothingDarkGray)
        ) {
            options.forEach { (lang, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected == lang) NothingWhite else NothingLightGray
                        )
                    },
                    onClick = {
                        onSelect(lang)
                        expanded = false
                    },
                    trailingIcon = if (selected == lang) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = NothingWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
            }
        }
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
        Spacer(modifier = Modifier.height(4.dp))
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
        Spacer(modifier = Modifier.height(4.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoOffTimerSelector(
    selected: AutoOffTimer,
    customMinutes: Int,
    onSelect: (AutoOffTimer) -> Unit,
    onUpdateCustomMinutes: (Int) -> Unit
) {
    val presets = listOf(
        AutoOffTimer.OFF to stringResource(R.string.timer_off),
        AutoOffTimer.MIN_15 to stringResource(R.string.timer_15),
        AutoOffTimer.MIN_30 to stringResource(R.string.timer_30),
        AutoOffTimer.HOUR_1 to stringResource(R.string.timer_60),
        AutoOffTimer.HOUR_2 to stringResource(R.string.timer_120),
        AutoOffTimer.CUSTOM to stringResource(R.string.timer_custom)
    )
    var expanded by remember { mutableStateOf(false) }
    var showCustomInput by remember { mutableStateOf(selected == AutoOffTimer.CUSTOM) }
    var customText by remember(customMinutes) { mutableStateOf(customMinutes.toString()) }

    val customLabel = stringResource(R.string.timer_custom)
    val selectedLabel = if (selected == AutoOffTimer.CUSTOM) {
        "$customLabel ($customMinutes ${stringResource(R.string.timer_custom_hint).lowercase()})"
    } else {
        presets.firstOrNull { it.first == selected }?.second ?: ""
    }

    Column {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { expanded = true }
                    .background(NothingMediumGray)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .menuAnchor(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NothingWhite
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = NothingLightGray
                )
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(NothingDarkGray)
            ) {
                presets.forEach { (timer, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected == timer) NothingWhite else NothingLightGray
                            )
                        },
                        onClick = {
                            onSelect(timer)
                            showCustomInput = timer == AutoOffTimer.CUSTOM
                            expanded = false
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showCustomInput || selected == AutoOffTimer.CUSTOM,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { newValue ->
                        customText = newValue.filter { it.isDigit() }.take(4)
                    },
                    label = {
                        Text(
                            stringResource(R.string.timer_custom_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NothingWhite,
                        unfocusedTextColor = NothingWhite,
                        cursorColor = NothingWhite,
                        focusedBorderColor = NothingWhite,
                        unfocusedBorderColor = NothingLightGray,
                        focusedLabelColor = NothingWhite,
                        unfocusedLabelColor = NothingLightGray
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        customText.toIntOrNull()?.let { mins ->
                            if (mins in 1..1440) {
                                onUpdateCustomMinutes(mins)
                                onSelect(AutoOffTimer.CUSTOM)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NothingMediumGray)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Apply",
                        tint = NothingWhite
                    )
                }
            }
        }
    }
}

@Composable
fun ChargeCompleteSelector(selected: ChargeCompleteAction, onSelect: (ChargeCompleteAction) -> Unit) {
    val options = listOf(
        ChargeCompleteAction.TURN_OFF to (stringResource(R.string.charge_complete_off) to stringResource(R.string.charge_complete_off_desc)),
        ChargeCompleteAction.SINGLE_ZONE to (stringResource(R.string.charge_complete_single) to stringResource(R.string.charge_complete_single_desc)),
        ChargeCompleteAction.ALL_ZONES to (stringResource(R.string.charge_complete_all) to stringResource(R.string.charge_complete_all_desc))
    )
    options.forEach { (action, textPair) ->
        SelectableRow(
            label = textPair.first,
            subtitle = textPair.second,
            isSelected = selected == action,
            onClick = { onSelect(action) }
        )
    }
}

@Composable
fun SettingsToggle(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) NothingMediumGray.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 6.dp),
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
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) NothingMediumGray else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingLightGray
                )
            }
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(NothingWhite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = NothingBlack,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeFullThresholdSelector(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(70, 80, 90, 100)
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .background(NothingMediumGray)
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .menuAnchor(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$selected%",
                style = MaterialTheme.typography.bodyLarge,
                color = NothingWhite
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = NothingLightGray
            )
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(NothingDarkGray)
        ) {
            options.forEach { threshold ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "$threshold%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected == threshold) NothingWhite else NothingLightGray
                        )
                    },
                    onClick = {
                        onSelect(threshold)
                        expanded = false
                    },
                    trailingIcon = if (selected == threshold) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = NothingWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
fun ServiceModeSelector(selected: ServiceMode, onSelect: (ServiceMode) -> Unit) {
    val options = listOf(
        ServiceMode.MANUAL to (stringResource(R.string.mode_manual) to stringResource(R.string.mode_manual_desc)),
        ServiceMode.ALWAYS_ON to (stringResource(R.string.mode_always_on) to stringResource(R.string.mode_always_on_desc)),
        ServiceMode.ALWAYS_ON_CHARGING to (stringResource(R.string.mode_always_on_charging) to stringResource(R.string.mode_always_on_charging_desc)),
        ServiceMode.CHARGING_ONLY to (stringResource(R.string.mode_charging_only) to stringResource(R.string.mode_charging_only_desc))
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
