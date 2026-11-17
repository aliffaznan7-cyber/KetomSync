package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.KeyboardLayoutMode
import com.example.data.util.HapticFeedbackHelper
import com.example.keyboard.KetomKeyboardController
import com.example.keyboard.KetomKeyboardDeck
import com.example.keyboard.SandboxEditorBridge
import com.example.ui.viewmodel.KetomViewModel

@Composable
fun KeyboardSandboxScreen(
    viewModel: KetomViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val snippets by viewModel.snippets.collectAsStateWithLifecycle()

    val haptics = remember(context) { HapticFeedbackHelper(context) }
    val bridge = remember { SandboxEditorBridge() }
    val controller = remember(bridge, scope) {
        KetomKeyboardController(
            bridge = bridge,
            scope = scope,
            onHapticTick = { mult ->
                haptics.pulse(
                    enabled = settings.hapticEnabled,
                    strength = settings.hapticStrength,
                    durationMs = settings.hapticDurationMs,
                    multiplier = mult
                )
            }
        )
    }

    val fieldValue by bridge.fieldValue.collectAsStateWithLifecycle()
    val keyboardState by controller.state.collectAsStateWithLifecycle()

    LaunchedEffect(settings.autoCapitalize) {
        controller.syncAutoCapitalization(settings.autoCapitalize)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            val isWide = maxWidth >= 700.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LayoutQuickSelector(
                            currentMode = settings.keyboardLayoutMode,
                            onSelect = viewModel::setKeyboardLayoutMode
                        )
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = { bridge.updateFromExternalField(it) },
                            label = { Text("KetomSync Live Gesture Sandbox") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("sandbox_editor_field")
                        )
                    }
                    GestureTelemetryCard(
                        undoCount = keyboardState.undoBufferCount,
                        shiftLabel = keyboardState.shiftMode.name,
                        selectedWords = keyboardState.swipeSelectedWordCount,
                        cursorDelta = keyboardState.cursorDeltaIndicator,
                        modifier = Modifier.weight(0.8f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LayoutQuickSelector(
                        currentMode = settings.keyboardLayoutMode,
                        onSelect = viewModel::setKeyboardLayoutMode
                    )
                    OutlinedTextField(
                        value = fieldValue,
                        onValueChange = { bridge.updateFromExternalField(it) },
                        label = { Text("Live Gesture Sandbox") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("sandbox_editor_field")
                    )
                    GestureTelemetryCard(
                        undoCount = keyboardState.undoBufferCount,
                        shiftLabel = keyboardState.shiftMode.name,
                        selectedWords = keyboardState.swipeSelectedWordCount,
                        cursorDelta = keyboardState.cursorDeltaIndicator,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        KetomKeyboardDeck(
            controller = controller,
            settings = settings,
            recentClips = clips,
            snippets = snippets,
            onToggleIncognito = { viewModel.setIncognitoMode(!settings.incognitoMode) },
            onCycleLayoutMode = viewModel::cycleKeyboardLayoutMode,
            onFlipOneHandedSide = viewModel::flipOneHandedSide,
            onUpdateFloatingSize = viewModel::setFloatingDimensions,
            onUpdateFloatingOffset = viewModel::setFloatingOffset,
            applyNavigationInsets = false
        )
    }
}

@Composable
private fun LayoutQuickSelector(
    currentMode: KeyboardLayoutMode,
    onSelect: (KeyboardLayoutMode) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(KeyboardLayoutMode.entries) { mode ->
            FilterChip(
                selected = currentMode == mode,
                onClick = { onSelect(mode) },
                label = { Text(mode.label) }
            )
        }
    }
}

@Composable
private fun GestureTelemetryCard(
    undoCount: Int,
    shiftLabel: String,
    selectedWords: Int,
    cursorDelta: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gesture Hints",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Undo: $undoCount/50 • $shiftLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "• Drag Spacebar horizontally: 1 char per 12 dp (${if (cursorDelta >= 0) "+" else ""}$cursorDelta)\n• Swipe left on Backspace: select words ($selectedWords) • Hold 350ms+: word delete -> 35 ch/s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
