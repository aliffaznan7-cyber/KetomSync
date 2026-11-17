package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = KetomPrimaryDark,
    onPrimary = KetomOnPrimaryDark,
    primaryContainer = KetomPrimaryContainerDark,
    onPrimaryContainer = KetomOnPrimaryContainerDark,
    secondary = KetomSecondaryDark,
    onSecondary = KetomOnSecondaryDark,
    secondaryContainer = KetomSecondaryContainerDark,
    onSecondaryContainer = KetomOnSecondaryContainerDark,
    tertiary = KetomTertiaryDark,
    onTertiary = KetomOnTertiaryDark,
    tertiaryContainer = KetomTertiaryContainerDark,
    onTertiaryContainer = KetomOnTertiaryContainerDark,
    background = KetomBackgroundDark,
    onBackground = KetomOnBackgroundDark,
    surface = KetomSurfaceDark,
    onSurface = KetomOnSurfaceDark,
    surfaceVariant = KetomSurfaceVariantDark,
    onSurfaceVariant = KetomOnSurfaceVariantDark,
    outline = KetomOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = KetomPrimaryLight,
    onPrimary = KetomOnPrimaryLight,
    primaryContainer = KetomPrimaryContainerLight,
    onPrimaryContainer = KetomOnPrimaryContainerLight,
    secondary = KetomSecondaryLight,
    onSecondary = KetomOnSecondaryLight,
    secondaryContainer = KetomSecondaryContainerLight,
    onSecondaryContainer = KetomOnSecondaryContainerLight,
    tertiary = KetomTertiaryLight,
    onTertiary = KetomOnTertiaryLight,
    tertiaryContainer = KetomTertiaryContainerLight,
    onTertiaryContainer = KetomOnTertiaryContainerLight,
    background = KetomBackgroundLight,
    onBackground = KetomOnBackgroundLight,
    surface = KetomSurfaceLight,
    onSurface = KetomOnSurfaceLight,
    surfaceVariant = KetomSurfaceVariantLight,
    onSurfaceVariant = KetomOnSurfaceVariantLight,
    outline = KetomOutlineLight
)

@Immutable
data class KetomSurfaceTokens(
    val keyboardDeck: Color,
    val keycapStandard: Color,
    val keycapModifier: Color,
    val keycapPressed: Color,
    val incognitoAccent: Color,
    val syncAccent: Color,
    val deleteHighlight: Color
)

val LocalKetomTokens = staticCompositionLocalOf {
    KetomSurfaceTokens(
        keyboardDeck = Color(0xFF0E1525),
        keycapStandard = Color(0xFF1E293B),
        keycapModifier = Color(0xFF162033),
        keycapPressed = Color(0xFF334155),
        incognitoAccent = IncognitoAmber,
        syncAccent = SyncPulseCyan,
        deleteHighlight = DeleteHighlightRose
    )
}

@Composable
fun KetomSyncTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val tokens = if (darkTheme) {
        KetomSurfaceTokens(
            keyboardDeck = colorScheme.surfaceContainerLowest,
            keycapStandard = colorScheme.surfaceContainerHigh,
            keycapModifier = colorScheme.surfaceContainer,
            keycapPressed = colorScheme.primaryContainer,
            incognitoAccent = IncognitoAmber,
            syncAccent = colorScheme.secondary,
            deleteHighlight = DeleteHighlightRose
        )
    } else {
        KetomSurfaceTokens(
            keyboardDeck = colorScheme.surfaceContainerLow,
            keycapStandard = colorScheme.surfaceContainerLowest,
            keycapModifier = colorScheme.surfaceContainerHigh,
            keycapPressed = colorScheme.primaryContainer,
            incognitoAccent = IncognitoAmber,
            syncAccent = colorScheme.secondary,
            deleteHighlight = DeleteHighlightRose
        )
    }

    CompositionLocalProvider(LocalKetomTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
