package com.example.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.SnippetFolder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ClipboardEntity
import com.example.data.local.KetomSettings
import com.example.data.local.SnippetEntity
import com.example.data.model.ClipboardCategory
import com.example.data.model.DeviceType
import com.example.data.model.KeyboardLayoutMode
import com.example.data.util.ClipboardCategorizer
import com.example.ui.theme.LocalKetomTokens
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val tokens = LocalKetomTokens.current
    var showSnippetsDrawer by remember { mutableStateOf(false) }
    var promptingSnippet by remember { mutableStateOf<SnippetEntity?>(null) }
    var lastShiftTapMillis by remember { mutableLongStateOf(0L) }

    val density = LocalDensity.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
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
        val isTabletWidth = availableWidth >= 600.dp

        val deckAlignment = when (settings.keyboardLayoutMode) {
            KeyboardLayoutMode.ONE_HANDED_LEFT -> Alignment.BottomStart
            KeyboardLayoutMode.ONE_HANDED_RIGHT -> Alignment.BottomEnd
            KeyboardLayoutMode.FLOATING -> Alignment.BottomCenter
            KeyboardLayoutMode.STANDARD -> Alignment.BottomCenter
        }

        val deckContent: @Composable (Modifier) -> Unit = { containerMod ->
            Surface(
                modifier = containerMod
                    .testTag("ketom_keyboard_deck"),
                shape = if (settings.keyboardLayoutMode == KeyboardLayoutMode.FLOATING) {
                    RoundedCornerShape(20.dp)
                } else {
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                },
                color = tokens.keyboardDeck,
                tonalElevation = 6.dp,
                shadowElevation = if (settings.keyboardLayoutMode == KeyboardLayoutMode.FLOATING) 12.dp else 4.dp,
                border = if (settings.keyboardLayoutMode == KeyboardLayoutMode.FLOATING) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                } else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 6.dp,
                            end = 6.dp,
                            top = 6.dp,
                            bottom = if (isFloating) 6.dp else 8.dp + bottomInsetDp
                        )
                ) {
                    if (settings.keyboardLayoutMode == KeyboardLayoutMode.FLOATING) {
                        FloatingHandleBar(
                            onDrag = { dx, dy ->
                                onUpdateFloatingOffset(
                                    (settings.floatingOffsetX + dx).coerceIn(-220f, 220f),
                                    (settings.floatingOffsetY + dy).coerceIn(-360f, 80f)
                                )
                            },
                            onExitFloating = onCycleLayoutMode
                        )
                    }

                    KeyboardUtilityAndClipboardStrip(
                        keyboardState = keyboardState,
                        settings = settings,
                        recentClips = recentClips,
                        showSnippetsDrawer = showSnippetsDrawer,
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

                    Spacer(modifier = Modifier.height(6.dp))

                    when {
                        promptingSnippet != null -> {
                            SnippetVariablePromptPanel(
                                snippet = promptingSnippet!!,
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
                                snippets = snippets,
                                onSelectSnippet = { snippet ->
                                    val vars = ClipboardCategorizer.extractTemplateVariables(snippet.body)
                                    if (vars.isEmpty()) {
                                        controller.pasteText(snippet.body, settings.autoCapitalize)
                                        showSnippetsDrawer = false
                                    } else {
                                        promptingSnippet = snippet
                                    }
                                },
                                onClose = { showSnippetsDrawer = false }
                            )
                        }

                        else -> {
                            val keyHeight = when {
                                settings.keyboardLayoutMode == KeyboardLayoutMode.FLOATING ->
                                    ((settings.floatingHeightDp - 78f) / 4f).coerceIn(38f, 56f).dp
                                isTabletWidth -> 54.dp
                                else -> 48.dp
                            }

                            KeyboardKeyMatrix(
                                controller = controller,
                                keyboardState = keyboardState,
                                autoCapEnabled = settings.autoCapitalize,
                                keyHeight = keyHeight,
                                lastShiftTapMillis = lastShiftTapMillis,
                                onUpdateShiftTapMillis = { lastShiftTapMillis = it }
                            )
                        }
                    }

                    if (settings.keyboardLayoutMode == KeyboardLayoutMode.FLOATING) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            val density = LocalDensity.current
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .pointerInput(settings.floatingWidthDp, settings.floatingHeightDp) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dW = with(density) { dragAmount.x.toDp().value }
                                            val dH = with(density) { dragAmount.y.toDp().value }
                                            onUpdateFloatingSize(
                                                (settings.floatingWidthDp + dW).coerceIn(280f, 560f),
                                                (settings.floatingHeightDp + dH).coerceIn(220f, 380f)
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
                                    text = "${settings.floatingWidthDp.roundToInt()}dp",
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
                            .width(settings.floatingWidthDp.dp)
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
                                .weight(0.25f)
                                .padding(bottom = bottomInsetDp)
                        )
                    }
                    deckContent(Modifier.weight(0.75f))
                    if (settings.keyboardLayoutMode == KeyboardLayoutMode.ONE_HANDED_LEFT) {
                        OneHandedGutterControls(
                            isRightDocked = false,
                            onFlipSide = onFlipOneHandedSide,
                            onExpandFull = onCycleLayoutMode,
                            modifier = Modifier
                                .weight(0.25f)
                                .padding(bottom = bottomInsetDp)
                        )
                    }
                }
            }

            KeyboardLayoutMode.STANDARD -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    deckContent(
                        Modifier
                            .widthIn(max = 820.dp)
                            .fillMaxWidth()
                    )
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
            onClick = onExitFloating,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
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
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            onClick = onFlipSide,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .testTag("one_handed_flip_button")
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
            onClick = onExpandFull,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .testTag("one_handed_expand_button")
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
    recentClips: List<ClipboardEntity>,
    showSnippetsDrawer: Boolean,
    onUndoTap: () -> Unit,
    onClipTap: (ClipboardEntity) -> Unit,
    onToggleSnippets: () -> Unit,
    onToggleIncognito: () -> Unit,
    onCycleLayoutMode: () -> Unit
) {
    val tokens = LocalKetomTokens.current

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
                                .clickable(onClick = onUndoTap)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Main Top Clipboard & Utility Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (keyboardState.undoBufferCount > 0) {
                Surface(
                    onClick = onUndoTap,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("keyboard_undo_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo deletion",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
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
                    onClick = onToggleIncognito,
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.incognitoAccent.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, tokens.incognitoAccent.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("keyboard_incognito_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Incognito active",
                            tint = tokens.incognitoAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Incognito",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.incognitoAccent
                        )
                    }
                }
            }

            // Recent Clipboard Chips Strip (Instant one-tap paste between Pixel 9 Pro & Xiaomi Pad 7)
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                if (recentClips.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.keycapModifier.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = tokens.syncAccent,
                                    modifier = Modifier.size(14.dp)
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
                    items(recentClips.take(10), key = { "${it.id}_${it.updatedAtMillis}" }) { clip ->
                        val cat = ClipboardCategory.fromWire(clip.category)
                        val isTabletSource = clip.deviceType == DeviceType.TABLET.wireValue
                        Surface(
                            onClick = { onClipTap(clip) },
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.keycapStandard,
                            border = BorderStroke(
                                1.dp,
                                if (clip.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("clipboard_chip_${clip.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = when (cat) {
                                        ClipboardCategory.LINK -> Icons.Default.Link
                                        ClipboardCategory.CODE -> Icons.Default.Code
                                        ClipboardCategory.ADDRESS -> Icons.Default.LocationOn
                                        ClipboardCategory.TEXT ->
                                            if (isTabletSource) Icons.Default.TabletAndroid else Icons.Default.PhoneAndroid
                                    },
                                    contentDescription = cat.label,
                                    tint = tokens.syncAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = clip.content.replace('\n', ' ').take(24),
                                    style = if (cat == ClipboardCategory.CODE) {
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
                onClick = onToggleSnippets,
                shape = RoundedCornerShape(10.dp),
                color = if (showSnippetsDrawer) MaterialTheme.colorScheme.primaryContainer else tokens.keycapModifier,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("keyboard_snippets_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.SnippetFolder,
                        contentDescription = "Snippets and macros",
                        tint = if (showSnippetsDrawer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Incognito toggle icon (when not already shown as wide badge)
            if (!settings.incognitoMode) {
                Surface(
                    onClick = onToggleIncognito,
                    shape = RoundedCornerShape(10.dp),
                    color = tokens.keycapModifier,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("keyboard_incognito_toggle")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Enable incognito mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Layout mode cycle (Full / 1H Left / 1H Right / Floating)
            Surface(
                onClick = onCycleLayoutMode,
                shape = RoundedCornerShape(10.dp),
                color = tokens.keycapModifier,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("keyboard_layout_cycle_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Switch keyboard layout mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardKeyMatrix(
    controller: KetomKeyboardController,
    keyboardState: KeyboardUiState,
    autoCapEnabled: Boolean,
    keyHeight: androidx.compose.ui.unit.Dp,
    lastShiftTapMillis: Long,
    onUpdateShiftTapMillis: (Long) -> Unit
) {
    val row1 = when (keyboardState.layer) {
        KeyboardLayer.ALPHA -> listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        KeyboardLayer.SYMBOLS_1 -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        KeyboardLayer.SYMBOLS_2 -> listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "Δ")
    }
    val row2 = when (keyboardState.layer) {
        KeyboardLayer.ALPHA -> listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        KeyboardLayer.SYMBOLS_1 -> listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")
        KeyboardLayer.SYMBOLS_2 -> listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\")
    }
    val row3 = when (keyboardState.layer) {
        KeyboardLayer.ALPHA -> listOf("z", "x", "c", "v", "b", "n", "m")
        KeyboardLayer.SYMBOLS_1 -> listOf("*", "\"", "'", ":", ";", "!", "?")
        KeyboardLayer.SYMBOLS_2 -> listOf("%", "©", "®", "™", "✓", "[", "]")
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            row1.forEach { ch ->
                val label = formatKeyLabel(ch, keyboardState)
                CharacterKeyButton(
                    label = label,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { controller.onKeyTap(ch, autoCapEnabled) }
                )
            }
        }

        // Row 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (keyboardState.layer == KeyboardLayer.ALPHA) 14.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            row2.forEach { ch ->
                val label = formatKeyLabel(ch, keyboardState)
                CharacterKeyButton(
                    label = label,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { controller.onKeyTap(ch, autoCapEnabled) }
                )
            }
        }

        // Row 3: Shift / Symbol Toggle + Keys + Smart Backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tokens = LocalKetomTokens.current
            if (keyboardState.layer == KeyboardLayer.ALPHA) {
                ModifierKeyButton(
                    height = keyHeight,
                    isActive = keyboardState.shiftMode != ShiftMode.LOWERCASE,
                    modifier = Modifier
                        .weight(1.45f)
                        .testTag("key_shift"),
                    onClick = {
                        val nextTs = controller.onShiftTap(lastShiftTapMillis, System.currentTimeMillis())
                        onUpdateShiftTapMillis(nextTs)
                    }
                ) {
                    Icon(
                        imageVector = if (keyboardState.shiftMode == ShiftMode.CAPS_LOCK) {
                            Icons.Default.KeyboardCapslock
                        } else {
                            Icons.Default.KeyboardArrowUp
                        },
                        contentDescription = "Shift",
                        tint = if (keyboardState.shiftMode != ShiftMode.LOWERCASE) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(21.dp)
                    )
                }
            } else {
                ModifierKeyButton(
                    height = keyHeight,
                    isActive = keyboardState.layer == KeyboardLayer.SYMBOLS_2,
                    modifier = Modifier.weight(1.45f),
                    onClick = { controller.cycleSymbolSubLayer() }
                ) {
                    Text(
                        text = if (keyboardState.layer == KeyboardLayer.SYMBOLS_1) "=\\<" else "?123",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            row3.forEach { ch ->
                val label = formatKeyLabel(ch, keyboardState)
                CharacterKeyButton(
                    label = label,
                    height = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { controller.onKeyTap(ch, autoCapEnabled) }
                )
            }

            // Smart Backspace Key (Tap, 300-600ms Word Delete, >600ms Accelerated Delete, Swipe-Left Word Select)
            SmartBackspaceKey(
                height = keyHeight,
                isHighlighted = keyboardState.isWordDeleteHighlight || keyboardState.swipeSelectedWordCount > 0,
                highlightColor = tokens.deleteHighlight,
                modifier = Modifier.weight(1.45f),
                onPressDown = { controller.onBackspacePressDown(autoCapEnabled) },
                onSwipeHorizontal = { dragPx, stepPx ->
                    controller.onBackspaceSwipeHorizontal(dragPx, stepPx)
                },
                onRelease = { controller.onBackspaceRelease(autoCapEnabled) }
            )
        }

        // Row 4: ?123, Comma, Spacebar (12dp/char cursor control), Period, Enter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModifierKeyButton(
                height = keyHeight,
                isActive = keyboardState.layer != KeyboardLayer.ALPHA,
                modifier = Modifier
                    .weight(1.35f)
                    .testTag("key_symbols"),
                onClick = { controller.toggleSymbolsLayer() }
            ) {
                Text(
                    text = if (keyboardState.layer == KeyboardLayer.ALPHA) "?123" else "ABC",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            CharacterKeyButton(
                label = ",",
                height = keyHeight,
                modifier = Modifier.weight(0.95f),
                onClick = { controller.onKeyTap(",", autoCapEnabled) }
            )

            SpacebarCursorKey(
                height = keyHeight,
                isCursorActive = keyboardState.isSpacebarCursorActive,
                modifier = Modifier.weight(4.4f),
                onDragStart = { controller.onSpacebarDragStart() },
                onStepCursor = { steps -> controller.onSpacebarCursorStep(steps) },
                onDragEnd = { controller.onSpacebarDragEnd(autoCapEnabled) },
                onTapSpace = { controller.onKeyTap(" ", autoCapEnabled) }
            )

            CharacterKeyButton(
                label = ".",
                height = keyHeight,
                modifier = Modifier.weight(0.95f),
                onClick = { controller.onKeyTap(".", autoCapEnabled) }
            )

            ModifierKeyButton(
                height = keyHeight,
                isActive = true,
                modifier = Modifier
                    .weight(1.45f)
                    .testTag("key_enter"),
                onClick = { controller.onEnterTap(autoCapEnabled) }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = "Enter",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatKeyLabel(ch: String, state: KeyboardUiState): String {
    if (state.layer != KeyboardLayer.ALPHA || ch.length != 1 || !ch[0].isLetter()) return ch
    return if (state.shiftMode == ShiftMode.LOWERCASE) ch.lowercase() else ch.uppercase()
}

@Composable
private fun CharacterKeyButton(
    label: String,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tokens = LocalKetomTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) tokens.keycapPressed else tokens.keycapStandard,
        animationSpec = spring(),
        label = "key_bg"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        shadowElevation = 1.dp,
        modifier = modifier
            .height(height)
            .testTag("key_$label")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ModifierKeyButton(
    height: androidx.compose.ui.unit.Dp,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val tokens = LocalKetomTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else tokens.keycapModifier,
        modifier = modifier.height(height)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun SmartBackspaceKey(
    height: androidx.compose.ui.unit.Dp,
    isHighlighted: Boolean,
    highlightColor: Color,
    modifier: Modifier = Modifier,
    onPressDown: () -> Unit,
    onSwipeHorizontal: (accumulatedDragPx: Float, stepWidthPx: Float) -> Unit,
    onRelease: () -> Unit
) {
    val tokens = LocalKetomTokens.current
    val density = LocalDensity.current
    val wordStepPx = remember(density) { with(density) { 38.dp.toPx() } }
    var isPressed by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isHighlighted -> highlightColor.copy(alpha = 0.35f)
            isPressed -> tokens.keycapPressed
            else -> tokens.keycapModifier
        },
        label = "backspace_bg"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = if (isHighlighted) BorderStroke(1.dp, highlightColor) else null,
        modifier = modifier
            .height(height)
            .semantics { contentDescription = "Backspace, hold or swipe left to select words" }
            .testTag("key_backspace")
            .pointerInput(wordStepPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    onPressDown()
                    var totalDragX = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                            break
                        }
                        val dx = change.positionChange().x
                        if (dx != 0f) {
                            totalDragX += dx
                            onSwipeHorizontal(totalDragX, wordStepPx)
                            if (abs(totalDragX) > wordStepPx * 0.4f) {
                                change.consume()
                            }
                        }
                    }

                    isPressed = false
                    onRelease()
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace",
                tint = if (isHighlighted) highlightColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SpacebarCursorKey(
    height: androidx.compose.ui.unit.Dp,
    isCursorActive: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onStepCursor: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onTapSpace: () -> Unit
) {
    val tokens = LocalKetomTokens.current
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    // Exact spec: 1 character per 12 dp horizontal drag
    val stepPx = remember(density) { with(density) { 12.dp.toPx() } }
    val touchSlop = viewConfig.touchSlop
    var isPressed by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isCursorActive -> MaterialTheme.colorScheme.secondaryContainer
            isPressed -> tokens.keycapPressed
            else -> tokens.keycapStandard
        },
        label = "spacebar_bg"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = if (isCursorActive) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
        modifier = modifier
            .height(height)
            .semantics { contentDescription = "Spacebar, drag horizontally to move cursor" }
            .testTag("key_spacebar")
            .pointerInput(stepPx, touchSlop) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    var totalDragX = 0f
                    var stepAccumulator = 0f
                    var dragging = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                            break
                        }
                        val dx = change.positionChange().x
                        if (dx != 0f) {
                            totalDragX += dx
                            if (!dragging && abs(totalDragX) > touchSlop) {
                                dragging = true
                                onDragStart()
                            }
                            if (dragging) {
                                change.consume()
                                stepAccumulator += dx
                                val steps = (stepAccumulator / stepPx).toInt()
                                if (steps != 0) {
                                    stepAccumulator -= steps * stepPx
                                    onStepCursor(steps)
                                }
                            }
                        }
                    }

                    isPressed = false
                    if (dragging) {
                        onDragEnd()
                    } else {
                        onTapSpace()
                    }
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SpaceBar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isCursorActive) "◄  cursor  ►" else "KetomSync",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KeyboardSnippetsDrawer(
    snippets: List<SnippetEntity>,
    onSelectSnippet: (SnippetEntity) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(206.dp)
            .padding(horizontal = 4.dp)
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
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close snippets drawer"
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(snippets, key = { it.id }) { snippet ->
                val vars = remember(snippet.body) { ClipboardCategorizer.extractTemplateVariables(snippet.body) }
                Surface(
                    onClick = { onSelectSnippet(snippet) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drawer_snippet_${snippet.id}")
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
    snippet: SnippetEntity,
    onCancel: () -> Unit,
    onInsert: (String) -> Unit
) {
    val variables = remember(snippet.body) { ClipboardCategorizer.extractTemplateVariables(snippet.body) }
    val values = remember(snippet.id) { mutableStateMapOf<String, String>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(216.dp)
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
                    val expanded = ClipboardCategorizer.expandTemplate(snippet.body, values)
                    onInsert(expanded)
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
