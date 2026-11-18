package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.KeyboardLayoutMode
import com.example.data.model.RetentionPolicy
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ketomPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ketomsync_preferences"
)

data class KetomSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val hapticStrength: Float = 0.7f,
    val hapticDurationMs: Int = 14,
    val keyboardLayoutMode: KeyboardLayoutMode = KeyboardLayoutMode.STANDARD,
    val oneHandedWidthFraction: Float = 0.75f,
    val floatingWidthDp: Float = 360f,
    val floatingHeightDp: Float = 270f,
    val floatingOffsetX: Float = 24f,
    val floatingOffsetY: Float = -36f,
    val retentionPolicy: RetentionPolicy = RetentionPolicy.TWENTY_FOUR_HOURS,
    val autoCapitalize: Boolean = true,
    val incognitoMode: Boolean = false,
    val crossDeviceSyncEnabled: Boolean = true
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val HAPTIC_STRENGTH = floatPreferencesKey("haptic_strength")
        val HAPTIC_DURATION_MS = intPreferencesKey("haptic_duration_ms")
        val LAYOUT_MODE = stringPreferencesKey("keyboard_layout_mode")
        val ONE_HANDED_FRACTION = floatPreferencesKey("one_handed_fraction")
        val FLOATING_WIDTH_DP = floatPreferencesKey("floating_width_dp")
        val FLOATING_HEIGHT_DP = floatPreferencesKey("floating_height_dp")
        val FLOATING_OFFSET_X = floatPreferencesKey("floating_offset_x")
        val FLOATING_OFFSET_Y = floatPreferencesKey("floating_offset_y")
        val RETENTION_POLICY = stringPreferencesKey("retention_policy")
        val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")
        val CROSS_DEVICE_SYNC = booleanPreferencesKey("cross_device_sync")
    }

    val settingsFlow: Flow<KetomSettings> = context.ketomPrefsDataStore.data.map { prefs ->
        KetomSettings(
            themeMode = ThemeMode.fromWire(prefs[Keys.THEME_MODE]),
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: true,
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
            hapticStrength = (prefs[Keys.HAPTIC_STRENGTH] ?: 0.7f).coerceIn(0.1f, 1.0f),
            hapticDurationMs = (prefs[Keys.HAPTIC_DURATION_MS] ?: 14).coerceIn(1, 50),
            keyboardLayoutMode = KeyboardLayoutMode.fromWire(prefs[Keys.LAYOUT_MODE]),
            oneHandedWidthFraction = (prefs[Keys.ONE_HANDED_FRACTION] ?: 0.75f).coerceIn(0.65f, 0.85f),
            floatingWidthDp = (prefs[Keys.FLOATING_WIDTH_DP] ?: 360f).coerceIn(280f, 560f),
            floatingHeightDp = (prefs[Keys.FLOATING_HEIGHT_DP] ?: 270f).coerceIn(210f, 380f),
            floatingOffsetX = prefs[Keys.FLOATING_OFFSET_X] ?: 24f,
            floatingOffsetY = prefs[Keys.FLOATING_OFFSET_Y] ?: -36f,
            retentionPolicy = RetentionPolicy.fromWire(prefs[Keys.RETENTION_POLICY]),
            autoCapitalize = prefs[Keys.AUTO_CAPITALIZE] ?: true,
            incognitoMode = prefs[Keys.INCOGNITO_MODE] ?: false,
            crossDeviceSyncEnabled = prefs[Keys.CROSS_DEVICE_SYNC] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.ketomPrefsDataStore.edit { it[Keys.THEME_MODE] = mode.wireValue }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.ketomPrefsDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.ketomPrefsDataStore.edit { it[Keys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun setHapticStrength(strength: Float) {
        context.ketomPrefsDataStore.edit { it[Keys.HAPTIC_STRENGTH] = strength.coerceIn(0.1f, 1.0f) }
    }

    suspend fun setHapticDurationMs(durationMs: Int) {
        context.ketomPrefsDataStore.edit { it[Keys.HAPTIC_DURATION_MS] = durationMs.coerceIn(1, 50) }
    }

    suspend fun setHapticConfig(enabled: Boolean, strength: Float, durationMs: Int) {
        context.ketomPrefsDataStore.edit {
            it[Keys.HAPTIC_ENABLED] = enabled
            it[Keys.HAPTIC_STRENGTH] = strength.coerceIn(0.1f, 1.0f)
            it[Keys.HAPTIC_DURATION_MS] = durationMs.coerceIn(1, 50)
        }
    }

    suspend fun setKeyboardLayoutMode(mode: KeyboardLayoutMode) {
        context.ketomPrefsDataStore.edit { it[Keys.LAYOUT_MODE] = mode.wireValue }
    }

    suspend fun setFloatingDimensions(widthDp: Float, heightDp: Float) {
        context.ketomPrefsDataStore.edit {
            it[Keys.FLOATING_WIDTH_DP] = widthDp.coerceIn(280f, 560f)
            it[Keys.FLOATING_HEIGHT_DP] = heightDp.coerceIn(210f, 380f)
        }
    }

    suspend fun setFloatingOffset(offsetX: Float, offsetY: Float) {
        context.ketomPrefsDataStore.edit {
            it[Keys.FLOATING_OFFSET_X] = offsetX
            it[Keys.FLOATING_OFFSET_Y] = offsetY
        }
    }

    suspend fun setRetentionPolicy(policy: RetentionPolicy) {
        context.ketomPrefsDataStore.edit { it[Keys.RETENTION_POLICY] = policy.wireValue }
    }

    suspend fun setAutoCapitalize(enabled: Boolean) {
        context.ketomPrefsDataStore.edit { it[Keys.AUTO_CAPITALIZE] = enabled }
    }

    suspend fun setIncognitoMode(enabled: Boolean) {
        context.ketomPrefsDataStore.edit { it[Keys.INCOGNITO_MODE] = enabled }
    }

    suspend fun setCrossDeviceSyncEnabled(enabled: Boolean) {
        context.ketomPrefsDataStore.edit { it[Keys.CROSS_DEVICE_SYNC] = enabled }
    }
}
