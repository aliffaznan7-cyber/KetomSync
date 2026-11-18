package com.example.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.SnippetFolder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ClipboardEntity
import com.example.data.local.KetomSettings
import com.example.data.local.SnippetEntity
import com.example.data.model.ClipboardCategory
import com.example.data.model.KeyboardLayoutMode
import com.example.data.util.ClipboardCategorizer
import com.example.ui.theme.LocalKetomTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class KeyboardFormFactor {
    PHONE_COMPACT,
    TABLET_PORTRAIT,
    TABLET_LANDSCAPE
}

private data class AdaptiveKeyboardMetrics(
    val formFactor: KeyboardFormFactor,
    val isTablet: Boolean,
    val keyHeight: Dp,
    val colGap: Dp,
    val rowGap: Dp,
    val row2Inset: Dp,
    val deckHorizontalPadding: Dp,
    val deckBottomPadding: Dp,
    val keyCornerRadius: Dp,
    val charFontSize: TextUnit,
    val modifierFontSize: TextUnit,
    val spacebarFontSize: TextUnit,
    val glyphScale: Float,
    val utilityStripHeight: Dp,
    val utilityButtonSize: Dp,
    val wordSwipeStepDp: Dp
)

private fun computeAdaptiveMetrics(
    availableWidth: Dp,
    boundedHeight: Dp?
): AdaptiveKeyboardMetrics {
    val formFactor = when {
        availableWidth >= 880.dp -> KeyboardFormFactor.TABLET_LANDSCAPE
        availableWidth >= 600.dp -> KeyboardFormFactor.TABLET_PORTRAIT
        else -> KeyboardFormFactor.PHONE_COMPACT
    }

    val base = when (formFactor) {
        KeyboardFormFactor.PHONE_COMPACT -> AdaptiveKeyboardMetrics(
            formFactor = formFactor,
            isTablet = false,
            keyHeight = 48.dp,
            colGap = 4.dp,
            rowGap = 6.dp,
            row2Inset = 14.dp,
            deckHorizontalPadding = 6.dp,
            deckBottomPadding = 8.dp,
            keyCornerRadius = 10.dp,
            charFontSize = 18.sp,
            modifierFontSize = 14.sp,
            spacebarFontSize = 12.sp,
            glyphScale = 1.0f,
            utilityStripHeight = 42.dp,
            utilityButtonSize = 36.dp,
            wordSwipeStepDp = 38.dp
        )

        KeyboardFormFactor.TABLET_PORTRAIT -> AdaptiveKeyboardMetrics(
            formFactor = formFactor,
            isTablet = true,
            keyHeight = 64.dp,
            colGap = 8.dp,
            rowGap = 8.dp,
            row2Inset = 22.dp,
            deckHorizontalPadding = 14.dp,
            deckBottomPadding = 10.dp,
            keyCornerRadius = 12.dp,
            charFontSize = 21.sp,
            modifierFontSize = 15.sp,
            spacebarFontSize = 13.sp,
            glyphScale = 1.18f,
            utilityStripHeight = 48.dp,
            utilityButtonSize = 40.dp,
            wordSwipeStepDp = 46.dp
        )

        KeyboardFormFactor.TABLET_LANDSCAPE -> {
            val isExtraWide = availableWidth >= 1080.dp
            AdaptiveKeyboardMetrics(
                formFactor = formFactor,
                isTablet = true,
                keyHeight = if (isExtraWide) 68.dp else 66.dp,
                colGap = if (isExtraWide) 12.dp else 10.dp,
                rowGap = 9.dp,
                row2Inset = (availableWidth * 0.025f).coerceIn(24.dp, 36.dp),
                deckHorizontalPadding = (availableWidth * 0.02f).coerceIn(18.dp, 34.dp),
                deckBottomPadding = 12.dp,
                keyCornerRadius = 13.dp,
                charFontSize = if (isExtraWide) 22.sp else 21.sp,
                modifierFontSize = 16.sp,
                spacebarFontSize = 14.sp,
                glyphScale = 1.28f,
                utilityStripHeight = 50.dp,
                utilityButtonSize = 42.dp,
                wordSwipeStepDp = 50.dp
            )
        }
    }

    // In constrained multi-window / split-screen containers, prevent overflowing available vertical space
    if (boundedHeight != null && boundedHeight > 0.dp) {
        val overhead = base.utilityStripHeight + base.rowGap * 3 + base.deckBottomPadding + 20.dp
        val maxPermittedKeyHeight = ((boundedHeight - overhead) / 4f).coerceAtLeast(38.dp)
        if (maxPermittedKeyHeight < base.keyHeight) {
            return base.copy(keyHeight = maxPermittedKeyHeight)
        }
    }
    return base
}

private enum class MatrixKeyKind {
    CHAR,
    SHIFT,
    SYMBOL_SUB_LAYER,
    BACKSPACE,
    SYMBOLS_TOGGLE,
    SPACEBAR,
    ENTER
}

private data class MatrixKeySpec(
    val id: String,
    val rawValue: String,
    val label: String,
    val kind: MatrixKeyKind,
    val weight: Float
)

private data class LaidOutKey(
    val spec: MatrixKeySpec,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

private class PointerGestureSession(
    val key: MatrixKeySpec,
    var lastX: Float,
    var totalDragX: Float = 0f,
    var stepAccumulator: Float = 0f,
    var isSpaceDragging: Boolean = false
)

/**
 * Fires [onDown] immediately on ACTION_DOWN (`awaitFirstDown`) with zero click/release delay
 * and drives an immediate visual pressed highlight.
 */
private fun Modifier.onTouchDownAction(
    key: Any? = Unit,
    onPressedChange: (Boolean) -> Unit = {},
    onDown: () -> Unit
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onPressedChange(true)
        onDown()
        try {
            while (true) {
                val event = awaitPointerEvent()
                val tracked = event.changes.firstOrNull { it.id == down.id } ?: break
                if (tracked.changedToUpIgnoreConsumed() || !tracked.pressed) {
                    break
                }
            }
        } finally {
            onPressedChange(false)
        }
    }
}

/**
 * Scroll-safe low-latency tap modifier for items inside LazyRow / LazyColumn:
 * highlights immediately on ACTION_DOWN without Material ripple lag and triggers
 * immediately if not dragged into a scroll gesture.
 */
private fun Modifier.onScrollSafeInstantTap(
    key: Any? = Unit,
    touchSlopPx: Float,
    onPressedChange: (Boolean) -> Unit = {},
    onTap: () -> Unit
): Modifier = pointerInput(key, touchSlopPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPressedChange(true)
        var cancelledByDragOrConsume = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val tracked = event.changes.firstOrNull { it.id == down.id }
                if (tracked == null || tracked.changedToUpIgnoreConsumed() || !tracked.pressed) {
                    break
                }
                val distSq = (tracked.position - down.position).getDistanceSquared()
                if (tracked.isConsumed || distSq > touchSlopPx * touchSlopPx) {
                    cancelledByDragOrConsume = true
                    onPressedChange(false)
                    break
                }
            }
        } finally {
            onPressedChange(false)
        }
        if (!cancelledByDragOrConsume) {
            onTap()
        }
    }
}

@Composable
fun KetomKeyboardDeck(
    controller: KetomKeyboardController,
    settings: KetomSettings,
    recentClips: List<ClipboardEntity>,
    snippets: List<SnippetEntity>,
    onToggleIncognito: () -> Unit,
    onCycleLayoutMode: () -> Unit,
    onFlipOneHandedSide: () -> Unit,
    onUpdateFloatingSize: (Float, Float) -> Unit,
    onUpdateFloatingOffset: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    applyNavigationInsets: Boolean = false,
    decorBottomInsetPx: Int = 0,
    decorLeftInsetPx: Int = 0,
    decorRightInsetPx: Int = 0
) {
    val keyboardState by controller.state.collectAsStateWithLifecycle()
    val clipChips by controller.clipChips.collectAsStateWithLifecycle()
    val filteredSnippets by controller.filteredSnippets.collectAsStateWithLifecycle()

    val tokens = LocalKetomTokens.current
    var showSnippetsDrawer by remember { mutableStateOf(false) }
    var promptingSnippet by remember { mutableStateOf<IndexedSnippetUiModel?>(null) }
    var lastShiftTapMillis by remember { mutableLongStateOf(0L) }

    // Offload clipboard formatting & snippet variable indexing off Main Thread (Dispatchers.Default)
    LaunchedEffect(recentClips) {
        controller.updateClipboardChipsAsync(recentClips)
    }
    LaunchedEffect(snippets) {
        controller.updateSnippetsIndexAsync(snippets)
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val bottomInsetDp = if (applyNavigationInsets) {
        val composeBottomPx = maxOf(
            WindowInsets.navigationBars.getBottom(density),
            WindowInsets.captionBar.getBottom(density),
            WindowInsets.mandatorySystemGestures.getBottom(density),
            WindowInsets.displayCutout.getBottom(density)
        )
        with(density) { maxOf(composeBottomPx, decorBottomInsetPx).toDp() }
    } else {
        0.dp
    }
    val leftInsetDp = if (applyNavigationInsets) {
        val composeLeftPx = maxOf(
            WindowInsets.navigationBars.getLeft(density, layoutDirection),
            WindowInsets.displayCutout.getLeft(density, layoutDirection)
        )
        with(density) { maxOf(composeLeftPx, decorLeftInsetPx).toDp() }
    } else {
        0.dp
    }
    val rightInsetDp = if (applyNavigationInsets) {
        val composeRightPx = maxOf(
            WindowInsets.navigationBars.getRight(density, layoutDirection),
            WindowInsets.displayCutout.getRight(density, layoutDirection)
        )
        with(density) { maxOf(composeRightPx, decorRightInsetPx).toDp() }
    } else {
        0.dp
    }
    val isFloating = settings.keyboardLayoutMode == KeyboardLayoutMode.FLOATING

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = leftInsetDp, end = rightInsetDp)
    ) {
        val availableWidth = maxWidth
        val boundedHeight = if (constraints.hasBoundedHeight) maxHeight else null

        // Real-time adaptive metrics driven by BoxWithConstraints (adapts cleanly across
        // phones, Xiaomi Pad 7 portrait/landscape, and split-screen modes)
        val effectiveSurfaceWidth = when (settings.keyboardLayoutMode) {
            KeyboardLayoutMode.FLOATING -> {
                val minFloatingW = if (availableWidth >= 600.dp) 400f else 280f
                val maxFloatingW = (availableWidth.value - 24f).coerceAtLeast(minFloatingW)
                settings.floatingWidthDp.coerceIn(minFloatingW, maxFloatingW).dp
            }
            KeyboardLayoutMode.ONE_HANDED_LEFT,
            KeyboardLayoutMode.ONE_HANDED_RIGHT -> {
                if (availableWidth >= 600.dp) {
                    (availableWidth * 0.56f).coerceIn(380.dp, 560.dp)
                } else {
                    availableWidth * settings.oneHandedWidthFraction
                }
            }
            KeyboardLayoutMode.STANDARD -> availableWidth
        }

        val metrics = remember(effectiveSurfaceWidth, boundedHeight, settings.keyboardLayoutMode) {
            computeAdaptiveMetrics(
                availableWidth = effectiveSurfaceWidth,
                boundedHeight = if (settings.keyboardLayoutMode == KeyboardLayoutMode.STANDARD) boundedHeight else null
            )
        }

        val deckAlignment = when (settings.keyboardLayoutMode) {
            KeyboardLayoutMode.ONE_HANDED_LEFT -> Alignment.BottomStart
            KeyboardLayoutMode.ONE_HANDED_RIGHT -> Alignment.BottomEnd
            KeyboardLayoutMode.FLOATING -> Alignment.BottomCenter
            KeyboardLayoutMode.STANDARD -> Alignment.BottomCenter
        }

        val matrixHeightDp = metrics.keyHeight * 4 + metrics.rowGap * 3

        val deckContent: @Composable (Modifier) -> Unit = { containerMod ->
            Surface(
                modifier = containerMod.testTag("ketom_keyboard_deck"),
                shape = if (isFloating) {
                    RoundedCornerShape(20.dp)
                } else {
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                },
                color = tokens.keyboardDeck,
                tonalElevation = 6.dp,
                shadowElevation = if (isFloating) 12.dp else 4.dp,
                border = if (isFloating) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                } else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (isFloating) 8.dp else metrics.deckHorizontalPadding,
                            end = if (isFloating) 8.dp else metrics.deckHorizontalPadding,
                            top = if (metrics.isTablet && !isFloating) 10.dp else 6.dp,
                            bottom = if (isFloating) 6.dp else metrics.deckBottomPadding + bottomInsetDp
                        )
                ) {
                    if (isFloating) {
                        val maxDragX = with(density) { (availableWidth * 0.45f).toPx() }.coerceAtLeast(220f)
                        FloatingHandleBar(
                            onDrag = { dx, dy ->
                                onUpdateFloatingOffset(
                                    (settings.floatingOffsetX + dx).coerceIn(-maxDragX, maxDragX),
                                    (settings.floatingOffsetY + dy).coerceIn(-420f, 80f)
                                )
                            },
                            onExitFloating = onCycleLayoutMode
                        )
                    }

                    KeyboardUtilityAndClipboardStrip(
                        keyboardState = keyboardState,
                        settings = settings,
                        clipChips = clipChips,
                        showSnippetsDrawer = showSnippetsDrawer,
                        metrics = metrics,
                        onUndoTap = { controller.onUndoTap(settings.autoCapitalize) },
                        onClipTap = { clip ->
                            controller.pasteText(clip.content, settings.autoCapitalize)
                        },
                        onToggleSnippets = {
                            showSnippetsDrawer = !showSnippetsDrawer
                            promptingSnippet = null
                        },
                        onToggleIncognito = onToggleIncognito,
                        onCycleLayoutMode = onCycleLayoutMode
                    )

                    Spacer(modifier = Modifier.height(if (metrics.isTablet) 8.dp else 6.dp))

                    when {
                        promptingSnippet != null -> {
                            SnippetVariablePromptPanel(
                                indexedSnippet = promptingSnippet!!,
                                panelHeight = matrixHeightDp,
                                onCancel = { promptingSnippet = null },
                                onInsert = { expanded ->
                                    controller.pasteText(expanded, settings.autoCapitalize)
                                    promptingSnippet = null
                                    showSnippetsDrawer = false
                                }
                            )
                        }

                        showSnippetsDrawer -> {
                            KeyboardSnippetsDrawer(
                                snippets = filteredSnippets,
                                drawerHeight = matrixHeightDp,
                                onSearchQueryChanged = { query ->
                                    controller.filterSnippetsAsync(query)
                                },
                                onSelectSnippet = { indexed ->
                                    if (indexed.variables.isEmpty()) {
                                        controller.pasteText(indexed.snippet.body, settings.autoCapitalize)
                                        showSnippetsDrawer = false
                                    } else {
                                        promptingSnippet = indexed
                                    }
                                },
                                onClose = { showSnippetsDrawer = false }
                            )
                        }

                        else -> {
                            val effectiveMetrics = if (isFloating) {
                                val floatingKeyH = ((settings.floatingHeightDp - 82f) / 4f).coerceIn(38f, 60f).dp
                                metrics.copy(keyHeight = floatingKeyH)
                            } else {
                                metrics
                            }

                            KeyboardKeyMatrix(
                                controller = controller,
                                layer = keyboardState.layer,
                                shiftMode = keyboardState.shiftMode,
                                isBackspaceHighlighted = keyboardState.isWordDeleteHighlight ||
                                    keyboardState.swipeSelectedWordCount > 0,
                                isSpacebarCursorActive = keyboardState.isSpacebarCursorActive,
                                autoCapEnabled = settings.autoCapitalize,
                                metrics = effectiveMetrics,
                                lastShiftTapMillis = lastShiftTapMillis,
                                onUpdateShiftTapMillis = { lastShiftTapMillis = it }
                            )
                        }
                    }

                    if (isFloating) {
                        val maxFloatingW = (availableWidth.value - 24f).coerceAtLeast(560f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            val localDensity = LocalDensity.current
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .pointerInput(settings.floatingWidthDp, settings.floatingHeightDp, maxFloatingW) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dW = with(localDensity) { dragAmount.x.toDp().value }
                                            val dH = with(localDensity) { dragAmount.y.toDp().value }
                                            onUpdateFloatingSize(
                                                (settings.floatingWidthDp + dW).coerceIn(280f, maxFloatingW),
                                                (settings.floatingHeightDp + dH).coerceIn(220f, 420f)
                                            )
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Resize floating keyboard",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${effectiveSurfaceWidth.value.roundToInt()}dp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        when (settings.keyboardLayoutMode) {
            KeyboardLayoutMode.FLOATING -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = bottomInsetDp + 6.dp),
                    contentAlignment = deckAlignment
                ) {
                    deckContent(
                        Modifier
                            .offset {
                                IntOffset(
                                    settings.floatingOffsetX.roundToInt(),
                                    settings.floatingOffsetY.roundToInt()
                                )
                            }
                            .width(effectiveSurfaceWidth)
                    )
                }
            }

            KeyboardLayoutMode.ONE_HANDED_LEFT,
            KeyboardLayoutMode.ONE_HANDED_RIGHT -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (settings.keyboardLayoutMode == KeyboardLayoutMode.ONE_HANDED_RIGHT) {
                        OneHandedGutterControls(
                            isRightDocked = true,
                            onFlipSide = onFlipOneHandedSide,
                            onExpandFull = onCycleLayoutMode,
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = bottomInsetDp)
                        )
                    }
                    deckContent(Modifier.width(effectiveSurfaceWidth))
                    if (settings.keyboardLayoutMode == KeyboardLayoutMode.ONE_HANDED_LEFT) {
                        OneHandedGutterControls(
                            isRightDocked = false,
                            onFlipSide = onFlipOneHandedSide,
                            onExpandFull = onCycleLayoutMode,
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = bottomInsetDp)
                        )
                    }
                }
            }

            KeyboardLayoutMode.STANDARD -> {
                // Full-width proportional layout: spans edge-to-edge on both phones and large
                // tablets (portrait & landscape) with zero awkward horizontal side gaps.
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    deckContent(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun FloatingHandleBar(
    onDrag: (Float, Float) -> Unit,
    onExitFloating: () -> Unit
) {
    var dockPressed by remember { mutableStateOf(false) }
    val currentOnExitFloating by rememberUpdatedState(onExitFloating)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Drag floating keyboard",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Floating Keyboard",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (dockPressed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            },
            modifier = Modifier.onTouchDownAction(
                onPressedChange = { dockPressed = it },
                onDown = { currentOnExitFloating() }
            )
        ) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = "Dock keyboard",
                modifier = Modifier
                    .padding(4.dp)
                    .size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun OneHandedGutterControls(
    isRightDocked: Boolean,
    onFlipSide: () -> Unit,
    onExpandFull: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalKetomTokens.current
    var flipPressed by remember { mutableStateOf(false) }
    var expandPressed by remember { mutableStateOf(false) }
    val currentOnFlip by rememberUpdatedState(onFlipSide)
    val currentOnExpand by rememberUpdatedState(onExpandFull)

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (flipPressed) tokens.keycapPressed else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .testTag("one_handed_flip_button")
                .onTouchDownAction(
                    onPressedChange = { flipPressed = it },
                    onDown = { currentOnFlip() }
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isRightDocked) "←" else "→",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = if (expandPressed) tokens.keycapPressed else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .testTag("one_handed_expand_button")
                .onTouchDownAction(
                    onPressedChange = { expandPressed = it },
                    onDown = { currentOnExpand() }
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Cycle keyboard layout",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun KeyboardUtilityAndClipboardStrip(
    keyboardState: KeyboardUiState,
    settings: KetomSettings,
    clipChips: List<ClipChipUiModel>,
    showSnippetsDrawer: Boolean,
    metrics: AdaptiveKeyboardMetrics,
    onUndoTap: () -> Unit,
    onClipTap: (ClipboardEntity) -> Unit,
    onToggleSnippets: () -> Unit,
    onToggleIncognito: () -> Unit,
    onCycleLayoutMode: () -> Unit
) {
    val tokens = LocalKetomTokens.current
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    val buttonSize = metrics.utilityButtonSize
    val chipHeight = metrics.utilityButtonSize
    val stripGap = if (metrics.isTablet) 8.dp else 4.dp

    val currentOnUndo by rememberUpdatedState(onUndoTap)
    val currentOnToggleSnippets by rememberUpdatedState(onToggleSnippets)
    val currentOnToggleIncognito by rememberUpdatedState(onToggleIncognito)
    val currentOnCycleLayoutMode by rememberUpdatedState(onCycleLayoutMode)

    var undoPressed by remember { mutableStateOf(false) }
    var snippetsPressed by remember { mutableStateOf(false) }
    var incognitoPressed by remember { mutableStateOf(false) }
    var layoutCyclePressed by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Contextual Gesture Status / Word Highlight / Undo Banner
        AnimatedVisibility(
            visible = keyboardState.isWordDeleteHighlight ||
                keyboardState.swipeSelectedWordCount > 0 ||
                keyboardState.isSpacebarCursorActive
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    keyboardState.swipeSelectedWordCount > 0 || keyboardState.isWordDeleteHighlight ->
                        tokens.deleteHighlight.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val statusText = when {
                        keyboardState.swipeSelectedWordCount > 0 ->
                            "Selecting ${keyboardState.swipeSelectedWordCount} word(s) — release to delete"
                        keyboardState.isWordDeleteHighlight ->
                            "Deleted \"${keyboardState.lastDeletedPreview ?: ""}\""
                        else ->
                            "Cursor move ${if (keyboardState.cursorDeltaIndicator >= 0) "+" else ""}${keyboardState.cursorDeltaIndicator}"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (keyboardState.undoBufferCount > 0 && keyboardState.isWordDeleteHighlight) {
                        Text(
                            text = "Tap Undo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .onTouchDownAction(onDown = { currentOnUndo() })
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Main Top Clipboard & Utility Strip (scales cleanly for phones and tablets)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.utilityStripHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stripGap)
        ) {
            if (keyboardState.undoBufferCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (undoPressed) tokens.keycapPressed else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .height(chipHeight)
                        .testTag("keyboard_undo_button")
                        .onTouchDownAction(
                            onPressedChange = { undoPressed = it },
                            onDown = { currentOnUndo() }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (metrics.isTablet) 12.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo deletion",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(if (metrics.isTablet) 18.dp else 16.dp)
                        )
                        Text(
                            text = "${keyboardState.undoBufferCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (settings.incognitoMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (incognitoPressed) {
                        tokens.incognitoAccent.copy(alpha = 0.35f)
                    } else {
                        tokens.incognitoAccent.copy(alpha = 0.2f)
                    },
                    border = BorderStroke(1.dp, tokens.incognitoAccent.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .height(chipHeight)
                        .testTag("keyboard_incognito_badge")
                        .onTouchDownAction(
                            onPressedChange = { incognitoPressed = it },
                            onDown = { currentOnToggleIncognito() }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (metrics.isTablet) 12.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Incognito active",
                            tint = tokens.incognitoAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Incognito",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.incognitoAccent
                        )
                    }
                }
            }

            // Recent Clipboard Chips Strip (Pre-computed off Main Thread)
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (metrics.isTablet) 8.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                if (clipChips.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.keycapModifier.copy(alpha = 0.7f),
                            modifier = Modifier.height(chipHeight)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = tokens.syncAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Clipboard sync ready",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(clipChips, key = { "${it.entity.id}_${it.entity.updatedAtMillis}" }) { chip ->
                        var chipPressed by remember { mutableStateOf(false) }
                        val currentClipTap by rememberUpdatedState(onClipTap)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (chipPressed) tokens.keycapPressed else tokens.keycapStandard,
                            border = BorderStroke(
                                1.dp,
                                if (chip.entity.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .height(chipHeight)
                                .testTag("clipboard_chip_${chip.entity.id}")
                                .onScrollSafeInstantTap(
                                    key = chip.entity.id,
                                    touchSlopPx = touchSlopPx,
                                    onPressedChange = { chipPressed = it },
                                    onTap = { currentClipTap(chip.entity) }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = if (metrics.isTablet) 14.dp else 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = when (chip.category) {
                                        ClipboardCategory.LINK -> Icons.Default.Link
                                        ClipboardCategory.CODE -> Icons.Default.Code
                                        ClipboardCategory.ADDRESS -> Icons.Default.LocationOn
                                        ClipboardCategory.TEXT ->
                                            if (chip.isTabletSource) Icons.Default.TabletAndroid else Icons.Default.PhoneAndroid
                                    },
                                    contentDescription = chip.category.label,
                                    tint = tokens.syncAccent,
                                    modifier = Modifier.size(if (metrics.isTablet) 15.dp else 13.dp)
                                )
                                Text(
                                    text = chip.previewText,
                                    style = if (chip.category == ClipboardCategory.CODE) {
                                        MaterialTheme.typography.labelSmall
                                    } else {
                                        MaterialTheme.typography.labelMedium
                                    },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Snippets & Macros button
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    snippetsPressed -> tokens.keycapPressed
                    showSnippetsDrawer -> MaterialTheme.colorScheme.primaryContainer
                    else -> tokens.keycapModifier
                },
                modifier = Modifier
                    .size(buttonSize)
                    .testTag("keyboard_snippets_button")
                    .onTouchDownAction(
                        onPressedChange = { snippetsPressed = it },
                        onDown = { currentOnToggleSnippets() }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.SnippetFolder,
                        contentDescription = "Snippets and macros",
                        tint = if (showSnippetsDrawer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(if (metrics.isTablet) 20.dp else 18.dp)
                    )
                }
            }

            // Incognito toggle icon
            if (!settings.incognitoMode) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (incognitoPressed) tokens.keycapPressed else tokens.keycapModifier,
                    modifier = Modifier
                        .size(buttonSize)
                        .testTag("keyboard_incognito_toggle")
                        .onTouchDownAction(
                            onPressedChange = { incognitoPressed = it },
                            onDown = { currentOnToggleIncognito() }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Enable incognito mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(if (metrics.isTablet) 19.dp else 17.dp)
                        )
                    }
                }
            }

            // Layout mode cycle (Full / 1H Left / 1H Right / Floating)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (layoutCyclePressed) tokens.keycapPressed else tokens.keycapModifier,
                modifier = Modifier
                    .size(buttonSize)
                    .testTag("keyboard_layout_cycle_button")
                    .onTouchDownAction(
                        onPressedChange = { layoutCyclePressed = it },
                        onDown = { currentOnCycleLayoutMode() }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Switch keyboard layout mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (metrics.isTablet) 19.dp else 17.dp)
                    )
                }
            }
        }
    }
}

/**
 * Adaptive single-Canvas keyboard key matrix:
 * - Dynamically scales keycap height (48.dp phone -> 64–68.dp tablet), font sizes
 *   (18.sp -> 21–22.sp), horizontal spacing (4.dp -> 8–12.dp), and glyph proportions
 *   for both portrait and landscape orientations.
 * - Multi-touch `awaitEachGesture { awaitFirstDown() }` triggers keystrokes, haptics,
 *   and keycap highlights immediately on ACTION_DOWN.
 */
@Composable
private fun KeyboardKeyMatrix(
    controller: KetomKeyboardController,
    layer: KeyboardLayer,
    shiftMode: ShiftMode,
    isBackspaceHighlighted: Boolean,
    isSpacebarCursorActive: Boolean,
    autoCapEnabled: Boolean,
    metrics: AdaptiveKeyboardMetrics,
    lastShiftTapMillis: Long,
    onUpdateShiftTapMillis: (Long) -> Unit
) {
    val tokens = LocalKetomTokens.current
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val textMeasurer = rememberTextMeasurer()

    val rowGapPx = with(density) { metrics.rowGap.toPx() }
    val colGapPx = with(density) { metrics.colGap.toPx() }
    val row2InsetPx = with(density) { metrics.row2Inset.toPx() }
    val cornerRadiusPx = with(density) { metrics.keyCornerRadius.toPx() }
    val shadowOffsetPx = remember(density) { with(density) { 1.dp.toPx() } }
    val borderStrokePx = remember(density) { with(density) { 1.dp.toPx() } }
    val iconStrokePx = with(density) { (1.8f * metrics.glyphScale).dp.toPx() }
    val keyHeightPx = with(density) { metrics.keyHeight.toPx() }
    val wordStepPx = with(density) { metrics.wordSwipeStepDp.toPx() }
    val spaceStepPx = remember(density) { with(density) { 12.dp.toPx() } }
    val touchSlopPx = viewConfig.touchSlop

    val totalHeightDp = metrics.keyHeight * 4 + metrics.rowGap * 3

    // Pointer-id -> keyId map read strictly inside Canvas draw scope (no recomposition on key press)
    val pressedKeyIds = remember { mutableStateMapOf<Long, String>() }

    val rowSpecs = remember(layer, shiftMode, metrics.isTablet) {
        buildKeyMatrixSpecs(layer = layer, shiftMode = shiftMode, isTablet = metrics.isTablet)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeightDp)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }

        val laidOutKeys = remember(rowSpecs, widthPx, keyHeightPx, rowGapPx, colGapPx, row2InsetPx, layer) {
            layoutKeyMatrix(
                rowSpecs = rowSpecs,
                totalWidthPx = widthPx,
                keyHeightPx = keyHeightPx,
                rowGapPx = rowGapPx,
                colGapPx = colGapPx,
                row2HorizontalInsetPx = if (layer == KeyboardLayer.ALPHA) row2InsetPx else 0f
            )
        }

        val onSurfaceColor = colorScheme.onSurface
        val onSurfaceVariantColor = colorScheme.onSurfaceVariant
        val onPrimaryContainerColor = colorScheme.onPrimaryContainer
        val primaryContainerColor = colorScheme.primaryContainer
        val secondaryContainerColor = colorScheme.secondaryContainer
        val secondaryBorderColor = colorScheme.secondary

        val charTextStyle = remember(onSurfaceColor, metrics.charFontSize) {
            TextStyle(
                color = onSurfaceColor,
                fontSize = metrics.charFontSize,
                fontWeight = FontWeight.Medium
            )
        }
        val modifierTextStyle = remember(onSurfaceColor, metrics.modifierFontSize) {
            TextStyle(
                color = onSurfaceColor,
                fontSize = metrics.modifierFontSize,
                fontWeight = FontWeight.SemiBold
            )
        }
        val spacebarTextStyle = remember(onSurfaceVariantColor, metrics.spacebarFontSize) {
            TextStyle(
                color = onSurfaceVariantColor,
                fontSize = metrics.spacebarFontSize,
                fontWeight = FontWeight.Medium
            )
        }

        val measuredLabels = remember(
            laidOutKeys,
            charTextStyle,
            modifierTextStyle,
            spacebarTextStyle,
            isSpacebarCursorActive
        ) {
            val map = HashMap<String, TextLayoutResult>(laidOutKeys.size)
            laidOutKeys.forEach { item ->
                when (item.spec.kind) {
                    MatrixKeyKind.CHAR -> {
                        map[item.spec.id] = textMeasurer.measure(item.spec.label, charTextStyle)
                    }
                    MatrixKeyKind.SYMBOL_SUB_LAYER, MatrixKeyKind.SYMBOLS_TOGGLE -> {
                        map[item.spec.id] = textMeasurer.measure(item.spec.label, modifierTextStyle)
                    }
                    MatrixKeyKind.SPACEBAR -> {
                        val label = if (isSpacebarCursorActive) "◄  cursor  ►" else "KetomSync"
                        map[item.spec.id] = textMeasurer.measure(label, spacebarTextStyle)
                    }
                    else -> Unit
                }
            }
            map
        }

        val latestLaidOutKeys by rememberUpdatedState(laidOutKeys)
        val latestAutoCap by rememberUpdatedState(autoCapEnabled)
        val latestLastShiftTapMillis by rememberUpdatedState(lastShiftTapMillis)
        val latestOnUpdateShiftTapMillis by rememberUpdatedState(onUpdateShiftTapMillis)
        val latestWordStepPx by rememberUpdatedState(wordStepPx)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp)
                .semantics { contentDescription = "KetomSync adaptive low-latency keyboard key matrix" }
                .testTag("ketom_keyboard_matrix")
                .pointerInput(spaceStepPx, touchSlopPx) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val activePointers = HashMap<Long, PointerGestureSession>(4)

                        fun handlePointerDown(change: PointerInputChange) {
                            val keysSnapshot = latestLaidOutKeys
                            val hit = findNearestKey(keysSnapshot, change.position.x, change.position.y) ?: return
                            val pid = change.id.value
                            pressedKeyIds[pid] = hit.spec.id
                            val session = PointerGestureSession(
                                key = hit.spec,
                                lastX = change.position.x
                            )
                            activePointers[pid] = session
                            change.consume()

                            val autoCap = latestAutoCap
                            // Immediate ACTION_DOWN execution (zero release delay)
                            when (hit.spec.kind) {
                                MatrixKeyKind.CHAR -> {
                                    controller.onKeyTap(hit.spec.rawValue, autoCap)
                                }
                                MatrixKeyKind.SHIFT -> {
                                    val now = System.currentTimeMillis()
                                    val nextTs = controller.onShiftTap(latestLastShiftTapMillis, now)
                                    latestOnUpdateShiftTapMillis(nextTs)
                                }
                                MatrixKeyKind.SYMBOL_SUB_LAYER -> {
                                    controller.cycleSymbolSubLayer()
                                }
                                MatrixKeyKind.SYMBOLS_TOGGLE -> {
                                    controller.toggleSymbolsLayer()
                                }
                                MatrixKeyKind.ENTER -> {
                                    controller.onEnterTap(autoCap)
                                }
                                MatrixKeyKind.BACKSPACE -> {
                                    controller.onBackspacePressDown(autoCap)
                                }
                                MatrixKeyKind.SPACEBAR -> {
                                    controller.onSpacebarTouchDown()
                                }
                            }
                        }

                        handlePointerDown(firstDown)

                        try {
                            while (activePointers.isNotEmpty()) {
                                val event = awaitPointerEvent()
                                val autoCap = latestAutoCap
                                val activeWordStepPx = latestWordStepPx

                                for (change in event.changes) {
                                    val pid = change.id.value
                                    if (change.changedToDownIgnoreConsumed() && !activePointers.containsKey(pid)) {
                                        handlePointerDown(change)
                                        continue
                                    }

                                    val session = activePointers[pid] ?: continue
                                    if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                                        activePointers.remove(pid)
                                        pressedKeyIds.remove(pid)
                                        when (session.key.kind) {
                                            MatrixKeyKind.BACKSPACE -> {
                                                controller.onBackspaceRelease(autoCap)
                                            }
                                            MatrixKeyKind.SPACEBAR -> {
                                                if (session.isSpaceDragging) {
                                                    controller.onSpacebarDragEnd(autoCap)
                                                } else {
                                                    controller.onSpacebarTapCommit(autoCap)
                                                }
                                            }
                                            else -> Unit // Already committed immediately on ACTION_DOWN
                                        }
                                        continue
                                    }

                                    val dx = change.position.x - session.lastX
                                    session.lastX = change.position.x
                                    if (dx != 0f) {
                                        when (session.key.kind) {
                                            MatrixKeyKind.BACKSPACE -> {
                                                session.totalDragX += dx
                                                controller.onBackspaceSwipeHorizontal(session.totalDragX, activeWordStepPx)
                                                if (abs(session.totalDragX) > activeWordStepPx * 0.4f) {
                                                    change.consume()
                                                }
                                            }
                                            MatrixKeyKind.SPACEBAR -> {
                                                session.totalDragX += dx
                                                if (!session.isSpaceDragging && abs(session.totalDragX) > touchSlopPx) {
                                                    session.isSpaceDragging = true
                                                    controller.onSpacebarDragStart()
                                                }
                                                if (session.isSpaceDragging) {
                                                    change.consume()
                                                    session.stepAccumulator += dx
                                                    val steps = (session.stepAccumulator / spaceStepPx).toInt()
                                                    if (steps != 0) {
                                                        session.stepAccumulator -= steps * spaceStepPx
                                                        controller.onSpacebarCursorStep(steps)
                                                    }
                                                }
                                            }
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                        } finally {
                            // Guarantee clean state if pointer gesture is cancelled externally
                            activePointers.forEach { (pid, session) ->
                                pressedKeyIds.remove(pid)
                                if (session.key.kind == MatrixKeyKind.BACKSPACE) {
                                    controller.onBackspaceRelease(latestAutoCap)
                                } else if (session.key.kind == MatrixKeyKind.SPACEBAR && session.isSpaceDragging) {
                                    controller.onSpacebarDragEnd(latestAutoCap)
                                }
                            }
                            activePointers.clear()
                        }
                    }
                }
        ) {
            val corner = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            val shadowColor = Color.Black.copy(alpha = 0.14f)

            for (i in laidOutKeys.indices) {
                val item = laidOutKeys[i]
                val spec = item.spec
                val isPressed = pressedKeyIds.containsValue(spec.id)
                val keyW = item.right - item.left
                val keyH = item.bottom - item.top

                val bgColor = when (spec.kind) {
                    MatrixKeyKind.BACKSPACE -> when {
                        isBackspaceHighlighted -> tokens.deleteHighlight.copy(alpha = 0.35f)
                        isPressed -> tokens.keycapPressed
                        else -> tokens.keycapModifier
                    }
                    MatrixKeyKind.SPACEBAR -> when {
                        isSpacebarCursorActive -> secondaryContainerColor
                        isPressed -> tokens.keycapPressed
                        else -> tokens.keycapStandard
                    }
                    MatrixKeyKind.ENTER -> if (isPressed) tokens.keycapPressed else primaryContainerColor
                    MatrixKeyKind.SHIFT -> when {
                        isPressed -> tokens.keycapPressed
                        shiftMode != ShiftMode.LOWERCASE -> primaryContainerColor
                        else -> tokens.keycapModifier
                    }
                    MatrixKeyKind.SYMBOL_SUB_LAYER -> when {
                        isPressed -> tokens.keycapPressed
                        layer == KeyboardLayer.SYMBOLS_2 -> primaryContainerColor
                        else -> tokens.keycapModifier
                    }
                    MatrixKeyKind.SYMBOLS_TOGGLE -> when {
                        isPressed -> tokens.keycapPressed
                        layer != KeyboardLayer.ALPHA -> primaryContainerColor
                        else -> tokens.keycapModifier
                    }
                    MatrixKeyKind.CHAR -> if (isPressed) tokens.keycapPressed else tokens.keycapStandard
                }

                // Subtle 1dp bottom shadow bevel
                drawRoundRect(
                    color = shadowColor,
                    topLeft = Offset(item.left, item.top + shadowOffsetPx),
                    size = Size(keyW, keyH),
                    cornerRadius = corner
                )

                // Keycap surface
                drawRoundRect(
                    color = bgColor,
                    topLeft = Offset(item.left, item.top),
                    size = Size(keyW, keyH),
                    cornerRadius = corner
                )

                // Highlight borders (Backspace word delete / Spacebar cursor drag)
                if (spec.kind == MatrixKeyKind.BACKSPACE && isBackspaceHighlighted) {
                    drawRoundRect(
                        color = tokens.deleteHighlight,
                        topLeft = Offset(item.left, item.top),
                        size = Size(keyW, keyH),
                        cornerRadius = corner,
                        style = Stroke(width = borderStrokePx)
                    )
                } else if (spec.kind == MatrixKeyKind.SPACEBAR && isSpacebarCursorActive) {
                    drawRoundRect(
                        color = secondaryBorderColor,
                        topLeft = Offset(item.left, item.top),
                        size = Size(keyW, keyH),
                        cornerRadius = corner,
                        style = Stroke(width = borderStrokePx)
                    )
                }

                val centerX = (item.left + item.right) * 0.5f
                val centerY = (item.top + item.bottom) * 0.5f

                when (spec.kind) {
                    MatrixKeyKind.SHIFT -> {
                        val tint = if (shiftMode != ShiftMode.LOWERCASE) onPrimaryContainerColor else onSurfaceColor
                        drawShiftGlyph(
                            cx = centerX,
                            cy = centerY,
                            isCapsLock = shiftMode == ShiftMode.CAPS_LOCK,
                            color = tint,
                            strokeWidth = iconStrokePx,
                            scale = metrics.glyphScale
                        )
                    }
                    MatrixKeyKind.BACKSPACE -> {
                        val tint = if (isBackspaceHighlighted) tokens.deleteHighlight else onSurfaceColor
                        drawBackspaceGlyph(
                            cx = centerX,
                            cy = centerY,
                            color = tint,
                            strokeWidth = iconStrokePx,
                            scale = metrics.glyphScale
                        )
                    }
                    MatrixKeyKind.ENTER -> {
                        drawEnterGlyph(
                            cx = centerX,
                            cy = centerY,
                            color = onPrimaryContainerColor,
                            strokeWidth = iconStrokePx,
                            scale = metrics.glyphScale
                        )
                    }
                    else -> {
                        val layoutResult = measuredLabels[spec.id]
                        if (layoutResult != null) {
                            drawText(
                                textLayoutResult = layoutResult,
                                topLeft = Offset(
                                    x = centerX - layoutResult.size.width * 0.5f,
                                    y = centerY - layoutResult.size.height * 0.5f
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildKeyMatrixSpecs(
    layer: KeyboardLayer,
    shiftMode: ShiftMode,
    isTablet: Boolean
): List<List<MatrixKeySpec>> {
    fun formatLabel(ch: String): String {
        if (layer != KeyboardLayer.ALPHA || ch.length != 1 || !ch[0].isLetter()) return ch
        return if (shiftMode == ShiftMode.LOWERCASE) ch.lowercase() else ch.uppercase()
    }

    val row1Chars = when (layer) {
        KeyboardLayer.ALPHA -> listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        KeyboardLayer.SYMBOLS_1 -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        KeyboardLayer.SYMBOLS_2 -> listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "Δ")
    }
    val row2Chars = when (layer) {
        KeyboardLayer.ALPHA -> listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        KeyboardLayer.SYMBOLS_1 -> listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")
        KeyboardLayer.SYMBOLS_2 -> listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\")
    }
    val row3Chars = when (layer) {
        KeyboardLayer.ALPHA -> listOf("z", "x", "c", "v", "b", "n", "m")
        KeyboardLayer.SYMBOLS_1 -> listOf("*", "\"", "'", ":", ";", "!", "?")
        KeyboardLayer.SYMBOLS_2 -> listOf("%", "©", "®", "™", "✓", "[", "]")
    }

    val sideModWeight = if (isTablet) 1.35f else 1.45f
    val spaceWeight = if (isTablet) 4.8f else 4.4f

    val row1 = row1Chars.map { ch ->
        MatrixKeySpec("r1_$ch", ch, formatLabel(ch), MatrixKeyKind.CHAR, 1f)
    }
    val row2 = row2Chars.map { ch ->
        MatrixKeySpec("r2_$ch", ch, formatLabel(ch), MatrixKeyKind.CHAR, 1f)
    }

    val row3Left = if (layer == KeyboardLayer.ALPHA) {
        MatrixKeySpec("key_shift", "", "", MatrixKeyKind.SHIFT, sideModWeight)
    } else {
        val label = if (layer == KeyboardLayer.SYMBOLS_1) "=\\<" else "?123"
        MatrixKeySpec("key_sub_layer", "", label, MatrixKeyKind.SYMBOL_SUB_LAYER, sideModWeight)
    }
    val row3Center = row3Chars.map { ch ->
        MatrixKeySpec("r3_$ch", ch, formatLabel(ch), MatrixKeyKind.CHAR, 1f)
    }
    val row3Right = MatrixKeySpec("key_backspace", "", "", MatrixKeyKind.BACKSPACE, sideModWeight)
    val row3 = listOf(row3Left) + row3Center + listOf(row3Right)

    val row4 = listOf(
        MatrixKeySpec(
            id = "key_symbols",
            rawValue = "",
            label = if (layer == KeyboardLayer.ALPHA) "?123" else "ABC",
            kind = MatrixKeyKind.SYMBOLS_TOGGLE,
            weight = 1.35f
        ),
        MatrixKeySpec("r4_comma", ",", ",", MatrixKeyKind.CHAR, 0.95f),
        MatrixKeySpec("key_spacebar", " ", "KetomSync", MatrixKeyKind.SPACEBAR, spaceWeight),
        MatrixKeySpec("r4_period", ".", ".", MatrixKeyKind.CHAR, 0.95f),
        MatrixKeySpec("key_enter", "\n", "", MatrixKeyKind.ENTER, sideModWeight)
    )

    return listOf(row1, row2, row3, row4)
}

private fun layoutKeyMatrix(
    rowSpecs: List<List<MatrixKeySpec>>,
    totalWidthPx: Float,
    keyHeightPx: Float,
    rowGapPx: Float,
    colGapPx: Float,
    row2HorizontalInsetPx: Float
): List<LaidOutKey> {
    if (totalWidthPx <= 0f || keyHeightPx <= 0f) return emptyList()
    val result = ArrayList<LaidOutKey>(36)

    for (rowIndex in rowSpecs.indices) {
        val row = rowSpecs[rowIndex]
        val horizontalInset = if (rowIndex == 1) row2HorizontalInsetPx else 0f
        val usableWidth = (totalWidthPx - horizontalInset * 2f - colGapPx * (row.size - 1)).coerceAtLeast(1f)
        val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
        val top = rowIndex * (keyHeightPx + rowGapPx)
        val bottom = top + keyHeightPx

        var x = horizontalInset
        for (key in row) {
            val w = usableWidth * (key.weight / totalWeight)
            result.add(
                LaidOutKey(
                    spec = key,
                    left = x,
                    top = top,
                    right = x + w,
                    bottom = bottom
                )
            )
            x += w + colGapPx
        }
    }
    return result
}

private fun findNearestKey(keys: List<LaidOutKey>, x: Float, y: Float): LaidOutKey? {
    if (keys.isEmpty()) return null
    for (i in keys.indices) {
        val k = keys[i]
        if (k.contains(x, y)) return k
    }
    // Snap across inter-key spacing gaps to the nearest keycap edge
    var best: LaidOutKey? = null
    var bestDistSq = Float.MAX_VALUE
    for (i in keys.indices) {
        val k = keys[i]
        val clampedX = x.coerceIn(k.left, k.right)
        val clampedY = y.coerceIn(k.top, k.bottom)
        val dx = x - clampedX
        val dy = y - clampedY
        val distSq = dx * dx + dy * dy
        if (distSq < bestDistSq) {
            bestDistSq = distSq
            best = k
        }
    }
    return best
}

private fun DrawScope.drawShiftGlyph(
    cx: Float,
    cy: Float,
    isCapsLock: Boolean,
    color: Color,
    strokeWidth: Float,
    scale: Float
) {
    val u = 1.dp.toPx() * scale
    val yOffset = if (isCapsLock) -1.5f * u else 0f
    val path = Path().apply {
        moveTo(cx, cy - 8f * u + yOffset)
        lineTo(cx + 8f * u, cy + 0.5f * u + yOffset)
        lineTo(cx + 3.6f * u, cy + 0.5f * u + yOffset)
        lineTo(cx + 3.6f * u, cy + 7f * u + yOffset)
        lineTo(cx - 3.6f * u, cy + 7f * u + yOffset)
        lineTo(cx - 3.6f * u, cy + 0.5f * u + yOffset)
        lineTo(cx - 8f * u, cy + 0.5f * u + yOffset)
        close()
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    if (isCapsLock) {
        drawLine(
            color = color,
            start = Offset(cx - 5f * u, cy + 9f * u),
            end = Offset(cx + 5f * u, cy + 9f * u),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawBackspaceGlyph(
    cx: Float,
    cy: Float,
    color: Color,
    strokeWidth: Float,
    scale: Float
) {
    val u = 1.dp.toPx() * scale
    val outline = Path().apply {
        moveTo(cx - 9.5f * u, cy)
        lineTo(cx - 4.2f * u, cy - 6.5f * u)
        lineTo(cx + 9f * u, cy - 6.5f * u)
        lineTo(cx + 9f * u, cy + 6.5f * u)
        lineTo(cx - 4.2f * u, cy + 6.5f * u)
        close()
    }
    drawPath(
        path = outline,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    val crossOffset = 2.8f * u
    val crossCenterX = cx + 2f * u
    drawLine(
        color = color,
        start = Offset(crossCenterX - crossOffset, cy - crossOffset),
        end = Offset(crossCenterX + crossOffset, cy + crossOffset),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(crossCenterX + crossOffset, cy - crossOffset),
        end = Offset(crossCenterX - crossOffset, cy + crossOffset),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawEnterGlyph(
    cx: Float,
    cy: Float,
    color: Color,
    strokeWidth: Float,
    scale: Float
) {
    val u = 1.dp.toPx() * scale
    val stem = Path().apply {
        moveTo(cx + 7f * u, cy - 5f * u)
        lineTo(cx + 7f * u, cy + 2f * u)
        lineTo(cx - 7f * u, cy + 2f * u)
    }
    drawPath(
        path = stem,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    val arrowHead = Path().apply {
        moveTo(cx - 3.2f * u, cy - 1.8f * u)
        lineTo(cx - 7f * u, cy + 2f * u)
        lineTo(cx - 3.2f * u, cy + 5.8f * u)
    }
    drawPath(
        path = arrowHead,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
private fun KeyboardSnippetsDrawer(
    snippets: List<IndexedSnippetUiModel>,
    drawerHeight: Dp,
    onSearchQueryChanged: (String) -> Unit,
    onSelectSnippet: (IndexedSnippetUiModel) -> Unit,
    onClose: () -> Unit
) {
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    var query by remember { mutableStateOf("") }
    var closePressed by remember { mutableStateOf(false) }
    val currentOnClose by rememberUpdatedState(onClose)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(drawerHeight)
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Snippets & Prompted Macros",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = CircleShape,
                color = if (closePressed) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                modifier = Modifier
                    .size(36.dp)
                    .onTouchDownAction(
                        onPressedChange = { closePressed = it },
                        onDown = { currentOnClose() }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close snippets drawer",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSearchQueryChanged(it)
            },
            placeholder = { Text("Search snippets or /shortcut") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drawer_snippet_search")
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(snippets, key = { it.snippet.id }) { indexed ->
                val snippet = indexed.snippet
                val vars = indexed.variables
                var itemPressed by remember { mutableStateOf(false) }
                val currentSelect by rememberUpdatedState(onSelectSnippet)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (itemPressed) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drawer_snippet_${snippet.id}")
                        .onScrollSafeInstantTap(
                            key = snippet.id,
                            touchSlopPx = touchSlopPx,
                            onPressedChange = { itemPressed = it },
                            onTap = { currentSelect(indexed) }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = snippet.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = snippet.shortcut,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = snippet.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (vars.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${vars.size} var",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Insert snippet",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnippetVariablePromptPanel(
    indexedSnippet: IndexedSnippetUiModel,
    panelHeight: Dp,
    onCancel: () -> Unit,
    onInsert: (String) -> Unit
) {
    val snippet = indexedSnippet.snippet
    val variables = indexedSnippet.variables
    val values = remember(snippet.id) { mutableStateMapOf<String, String>() }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Expand ${snippet.shortcut}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = snippet.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(variables, key = { it }) { varName ->
                OutlinedTextField(
                    value = values[varName] ?: "",
                    onValueChange = { values[varName] = it },
                    label = { Text(varName) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("macro_var_input_$varName")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val snapshotValues = values.toMap()
                    coroutineScope.launch {
                        val expanded = withContext(Dispatchers.Default) {
                            ClipboardCategorizer.expandTemplate(snippet.body, snapshotValues)
                        }
                        onInsert(expanded)
                    }
                },
                modifier = Modifier
                    .weight(1.4f)
                    .testTag("macro_insert_button")
            ) {
                Text("Insert Expanded")
            }
        }
    }
}
