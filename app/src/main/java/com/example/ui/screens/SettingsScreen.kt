package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.GoogleAuthHelper
import com.example.data.model.KeyboardLayoutMode
import com.example.data.model.RetentionPolicy
import com.example.data.model.ThemeMode
import com.example.data.util.HapticFeedbackHelper
import com.example.ui.viewmodel.KetomViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlin.math.abs
import kotlin.math.roundToInt

private data class HapticIntensityPreset(
    val id: String,
    val label: String,
    val strength: Float,
    val durationMs: Int
)

private val hapticIntensityPresets = listOf(
    HapticIntensityPreset("light", "Light", 0.35f, 8),
    HapticIntensityPreset("balanced", "Balanced", 0.70f, 14),
    HapticIntensityPreset("crisp", "Crisp", 0.88f, 18),
    HapticIntensityPreset("heavy", "Heavy", 1.00f, 26)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: KetomViewModel,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val haptics = remember(context) { HapticFeedbackHelper(context) }
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val userEmail = Firebase.auth.currentUser?.email ?: "Signed in"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Appearance & Dynamic Coloring
        item {
            SettingsSectionCard(
                icon = Icons.Default.ColorLens,
                title = "Appearance & Dynamic Color",
                modifier = Modifier.widthIn(max = 820.dp)
            ) {
                Text(
                    text = "Theme Synchronization",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                SettingsToggleRow(
                    title = "Android Wallpaper Dynamic Color",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Adapt keyboard and companion surfaces to Material You wallpaper"
                    } else {
                        "Requires Android 12+ (uses KetomSync palette on older OS)"
                    },
                    checked = settings.dynamicColorEnabled,
                    onCheckedChange = viewModel::setDynamicColor,
                    testTag = "dynamic_color_switch"
                )
            }
        }

        // Tactile & Haptic Intensity Controls (Saved via DataStore -> Applied in KetomKeyboardController)
        item {
            SettingsSectionCard(
                icon = Icons.Default.Vibration,
                title = "Haptic Intensity & Tactile Feel",
                modifier = Modifier.widthIn(max = 820.dp)
            ) {
                SettingsToggleRow(
                    title = "Keypress & Gesture Haptics",
                    subtitle = "Vibrate on key taps, backspace word delete, and 12dp cursor steps",
                    checked = settings.hapticEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setHapticEnabled(enabled)
                        if (enabled) {
                            haptics.pulse(true, settings.hapticStrength, settings.hapticDurationMs)
                        }
                    },
                    testTag = "haptics_switch"
                )
                Text(
                    text = "Intensity Presets",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    hapticIntensityPresets.forEach { preset ->
                        val isSelected = settings.hapticEnabled &&
                            abs(settings.hapticStrength - preset.strength) < 0.03f &&
                            settings.hapticDurationMs == preset.durationMs
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.applyHapticPreset(preset.strength, preset.durationMs)
                                haptics.pulse(true, preset.strength, preset.durationMs)
                            },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("haptic_preset_${preset.id}")
                        )
                    }
                }
                Column {
                    val strengthPct = (settings.hapticStrength * 100).roundToInt()
                    val intensityDescriptor = when {
                        !settings.hapticEnabled -> "Off"
                        strengthPct < 40 -> "Light"
                        strengthPct < 75 -> "Balanced"
                        strengthPct < 95 -> "Firm"
                        else -> "Max"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Keypress Intensity", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "$strengthPct% • $intensityDescriptor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.hapticStrength,
                        onValueChange = viewModel::setHapticStrength,
                        onValueChangeFinished = {
                            haptics.pulse(
                                enabled = settings.hapticEnabled,
                                strength = settings.hapticStrength,
                                durationMs = settings.hapticDurationMs
                            )
                        },
                        valueRange = 0.1f..1.0f,
                        enabled = settings.hapticEnabled,
                        modifier = Modifier.testTag("haptic_strength_slider")
                    )
                }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pulse Duration (1–50 ms)", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${settings.hapticDurationMs} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.hapticDurationMs.toFloat(),
                        onValueChange = { viewModel.setHapticDurationMs(it.roundToInt().coerceIn(1, 50)) },
                        onValueChangeFinished = {
                            haptics.pulse(
                                enabled = settings.hapticEnabled,
                                strength = settings.hapticStrength,
                                durationMs = settings.hapticDurationMs
                            )
                        },
                        valueRange = 1f..50f,
                        steps = 48,
                        enabled = settings.hapticEnabled,
                        modifier = Modifier.testTag("haptic_duration_slider")
                    )
                }
                HapticKeyPreviewRow(
                    enabled = settings.hapticEnabled,
                    strength = settings.hapticStrength,
                    durationMs = settings.hapticDurationMs,
                    haptics = haptics
                )
            }
        }

        // Ergonomics & Input Layout
        item {
            SettingsSectionCard(
                icon = Icons.Default.Keyboard,
                title = "Keyboard Mode & Typing",
                modifier = Modifier.widthIn(max = 820.dp)
            ) {
                Text(
                    text = "Keyboard Geometry (Phone / Tablet)",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyboardLayoutMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.keyboardLayoutMode == mode,
                            onClick = { viewModel.setKeyboardLayoutMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                SettingsToggleRow(
                    title = "Auto-Capitalization",
                    subtitle = "Capitalize at sentence start and after full stop + space",
                    checked = settings.autoCapitalize,
                    onCheckedChange = viewModel::setAutoCapitalize,
                    testTag = "autocap_switch"
                )
            }
        }

        // Privacy, Retention & Account
        item {
            SettingsSectionCard(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy, Retention & Sync Account",
                modifier = Modifier.widthIn(max = 820.dp)
            ) {
                Text(
                    text = "Unpinned Clipboard Retention",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetentionPolicy.entries.forEach { policy ->
                        FilterChip(
                            selected = settings.retentionPolicy == policy,
                            onClick = { viewModel.setRetentionPolicy(policy) },
                            label = { Text(policy.label) }
                        )
                    }
                }
                SettingsToggleRow(
                    title = "Cross-Device Cloud Sync",
                    subtitle = "Sync clipboard chips and macros across Pixel 9 Pro & Xiaomi Pad 7",
                    checked = settings.crossDeviceSyncEnabled,
                    onCheckedChange = viewModel::setCrossDeviceSyncEnabled,
                    testTag = "cloud_sync_switch"
                )
                SettingsToggleRow(
                    title = "Incognito Mode",
                    subtitle = "Pause cloud sync, avoid learning, and clear ephemeral session on exit",
                    checked = settings.incognitoMode,
                    onCheckedChange = viewModel::setIncognitoMode,
                    testTag = "incognito_switch"
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Sync Account", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                GoogleAuthHelper.signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onSignOutComplete = onSignedOut
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text("Sign Out")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun HapticKeyPreviewRow(
    enabled: Boolean,
    strength: Float,
    durationMs: Int,
    haptics: HapticFeedbackHelper
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentStrength by rememberUpdatedState(strength)
    val currentDurationMs by rememberUpdatedState(durationMs)
    val keys = remember {
        listOf(
            "Q" to 1.0f,
            "W" to 1.0f,
            "Space" to 0.85f,
            "⌫" to 0.85f,
            "↵" to 1.1f
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Tap keys to test keypress feel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            keys.forEach { (label, multiplier) ->
                var isPressed by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        isPressed -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(if (label == "Space") 1.8f else 1f)
                        .height(44.dp)
                        .testTag("haptic_preview_key_$label")
                        .pointerInput(label) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                isPressed = true
                                haptics.pulse(
                                    enabled = currentEnabled,
                                    strength = currentStrength,
                                    durationMs = currentDurationMs,
                                    multiplier = multiplier
                                )
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.changedToUpIgnoreConsumed() || !change.pressed) break
                                    }
                                } finally {
                                    isPressed = false
                                }
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    }
                }
            }
        }
    }
}
