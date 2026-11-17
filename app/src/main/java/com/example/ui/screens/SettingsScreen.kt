package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlin.math.roundToInt

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

        // Tactile & Haptic Controls (1-50 ms)
        item {
            SettingsSectionCard(
                icon = Icons.Default.Vibration,
                title = "Haptic Strength & Pulse Duration",
                modifier = Modifier.widthIn(max = 820.dp)
            ) {
                SettingsToggleRow(
                    title = "Keypress & Gesture Haptics",
                    subtitle = "Vibrate on key taps, backspace word delete, and 12dp cursor steps",
                    checked = settings.hapticEnabled,
                    onCheckedChange = viewModel::setHapticEnabled,
                    testTag = "haptics_switch"
                )
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Haptic Strength", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${(settings.hapticStrength * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.hapticStrength,
                        onValueChange = viewModel::setHapticStrength,
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
                        valueRange = 1f..50f,
                        steps = 48,
                        enabled = settings.hapticEnabled,
                        modifier = Modifier.testTag("haptic_duration_slider")
                    )
                }
                OutlinedButton(
                    onClick = {
                        haptics.pulse(
                            enabled = settings.hapticEnabled,
                            strength = settings.hapticStrength,
                            durationMs = settings.hapticDurationMs
                        )
                    },
                    enabled = settings.hapticEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test ${settings.hapticDurationMs}ms Haptic Pulse")
                }
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
