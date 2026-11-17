package com.example.keyboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.BreakIterator
import kotlin.math.roundToLong

enum class ShiftMode {
    LOWERCASE,
    SHIFT_ONCE,
    CAPS_LOCK
}

enum class KeyboardLayer {
    ALPHA,
    SYMBOLS_1,
    SYMBOLS_2
}

data class KeyboardUiState(
    val shiftMode: ShiftMode = ShiftMode.SHIFT_ONCE,
    val layer: KeyboardLayer = KeyboardLayer.ALPHA,
    val undoBufferCount: Int = 0,
    val lastDeletedPreview: String? = null,
    val isWordDeleteHighlight: Boolean = false,
    val swipeSelectedWordCount: Int = 0,
    val isSpacebarCursorActive: Boolean = false,
    val cursorDeltaIndicator: Int = 0
)

/**
 * Stateful controller implementing KetomSync's Gboard-grade input, backspace, undo,
 * spacebar cursor drag, and auto-capitalization behaviors.
 */
class KetomKeyboardController(
    private val bridge: InputConnectionBridge,
    private val scope: CoroutineScope,
    private val onHapticTick: (Float) -> Unit = {}
) {
    private val _state = MutableStateFlow(KeyboardUiState())
    val state: StateFlow<KeyboardUiState> = _state.asStateFlow()

    // 50-entry / 50-character undo buffer
    private val undoStack = ArrayDeque<String>(MAX_UNDO_ITEMS)

    private var backspaceHoldJob: Job? = null
    private var highlightResetJob: Job? = null
    private var didHoldActionTrigger = false
    private var swipeSelectActive = false
    private var swipeSelectedCharLength = 0
    private var swipeLastWordCount = 0

    fun syncAutoCapitalization(autoCapEnabled: Boolean) {
        if (!autoCapEnabled) return
        if (_state.value.shiftMode == ShiftMode.CAPS_LOCK) return

        val before = bridge.getTextBeforeCursor(64)
        val shouldCap = shouldAutoCapitalize(before)
        _state.update {
            it.copy(shiftMode = if (shouldCap) ShiftMode.SHIFT_ONCE else ShiftMode.LOWERCASE)
        }
    }

    fun onKeyTap(rawLabel: String, autoCapEnabled: Boolean) {
        onHapticTick(1.0f)
        val charToInsert = if (_state.value.layer == KeyboardLayer.ALPHA && rawLabel.length == 1 && rawLabel[0].isLetter()) {
            when (_state.value.shiftMode) {
                ShiftMode.LOWERCASE -> rawLabel.lowercase()
                ShiftMode.SHIFT_ONCE, ShiftMode.CAPS_LOCK -> rawLabel.uppercase()
            }
        } else {
            rawLabel
        }

        bridge.commitText(charToInsert)

        if (_state.value.layer == KeyboardLayer.ALPHA) {
            if (_state.value.shiftMode == ShiftMode.SHIFT_ONCE) {
                _state.update { it.copy(shiftMode = ShiftMode.LOWERCASE) }
            }
            if (charToInsert == " " || charToInsert == "\n") {
                syncAutoCapitalization(autoCapEnabled)
            }
        }
    }

    fun onEnterTap(autoCapEnabled: Boolean) {
        onHapticTick(1.1f)
        bridge.performEnterAction()
        if (autoCapEnabled && _state.value.shiftMode != ShiftMode.CAPS_LOCK) {
            syncAutoCapitalization(autoCapEnabled)
        }
    }

    fun onShiftTap(lastTapMillis: Long, nowMillis: Long): Long {
        onHapticTick(1.0f)
        val isDoubleTap = (nowMillis - lastTapMillis) < 320L
        _state.update { current ->
            val next = when {
                isDoubleTap -> ShiftMode.CAPS_LOCK
                current.shiftMode == ShiftMode.LOWERCASE -> ShiftMode.SHIFT_ONCE
                else -> ShiftMode.LOWERCASE
            }
            current.copy(shiftMode = next)
        }
        return nowMillis
    }

    fun toggleSymbolsLayer() {
        onHapticTick(1.0f)
        _state.update {
            val next = if (it.layer == KeyboardLayer.ALPHA) KeyboardLayer.SYMBOLS_1 else KeyboardLayer.ALPHA
            it.copy(layer = next)
        }
    }

    fun cycleSymbolSubLayer() {
        onHapticTick(1.0f)
        _state.update {
            val next = if (it.layer == KeyboardLayer.SYMBOLS_1) KeyboardLayer.SYMBOLS_2 else KeyboardLayer.SYMBOLS_1
            it.copy(layer = next)
        }
    }

    fun onBackspacePressDown(autoCapEnabled: Boolean) {
        didHoldActionTrigger = false
        swipeSelectActive = false
        swipeSelectedCharLength = 0
        swipeLastWordCount = 0

        backspaceHoldJob?.cancel()
        backspaceHoldJob = scope.launch {
            // Phase 1: 300-600 ms hold -> delete preceding word + trailing whitespace with highlight
            delay(360L)
            if (swipeSelectActive) return@launch
            didHoldActionTrigger = true
            deletePrecedingWordWithHighlight(autoCapEnabled)

            // Phase 2: Past 600 ms -> accelerated character deletion (10 chars/s -> 35 chars/s over 1.5s)
            delay(240L)
            val accelStart = System.currentTimeMillis()
            while (!swipeSelectActive) {
                val deleted = deleteSingleUnicodeChar(autoCapEnabled)
                if (!deleted) break
                val elapsed = (System.currentTimeMillis() - accelStart).coerceAtLeast(0L)
                val progress = (elapsed.toFloat() / 1500f).coerceIn(0f, 1f)
                // Ramp from 10 chars/s (100ms) up to ~35 chars/s (~28ms)
                val rate = 10f + (25f * progress)
                val delayMs = (1000f / rate).roundToLong().coerceIn(28L, 100L)
                delay(delayMs)
            }
        }
    }

    fun onBackspaceRelease(autoCapEnabled: Boolean) {
        backspaceHoldJob?.cancel()
        backspaceHoldJob = null

        if (swipeSelectActive) {
            val selectedCount = swipeSelectedCharLength
            if (selectedCount > 0) {
                val selectedText = bridge.getSelectedText()
                    ?: bridge.getTextBeforeCursor(selectedCount)
                bridge.clearSelectionToEnd()
                if (selectedText.isNotEmpty()) {
                    bridge.deleteSurroundingText(selectedText.length, 0)
                    pushToUndo(selectedText)
                    triggerWordHighlight(selectedText)
                    onHapticTick(1.2f)
                }
            } else {
                bridge.clearSelectionToEnd()
            }
            swipeSelectActive = false
            swipeSelectedCharLength = 0
            swipeLastWordCount = 0
            _state.update { it.copy(swipeSelectedWordCount = 0) }
            syncAutoCapitalization(autoCapEnabled)
            return
        }

        if (!didHoldActionTrigger) {
            deleteSingleUnicodeChar(autoCapEnabled)
        }
        didHoldActionTrigger = false
    }

    /**
     * Swipe left from Backspace: selects previous words progressively;
     * moving back right clears/shrinks selection.
     */
    fun onBackspaceSwipeHorizontal(accumulatedDragPx: Float, stepWidthPx: Float) {
        if (!swipeSelectActive && accumulatedDragPx < -stepWidthPx * 0.45f) {
            backspaceHoldJob?.cancel()
            swipeSelectActive = true
        }
        if (!swipeSelectActive) return

        val desiredWords = if (accumulatedDragPx >= -stepWidthPx * 0.25f) {
            0
        } else {
            ((-accumulatedDragPx) / stepWidthPx).toInt().coerceIn(0, 12)
        }

        if (desiredWords == swipeLastWordCount) return
        swipeLastWordCount = desiredWords

        if (desiredWords == 0) {
            swipeSelectedCharLength = 0
            bridge.clearSelectionToEnd()
            _state.update { it.copy(swipeSelectedWordCount = 0) }
            return
        }

        val before = bridge.getTextBeforeCursor(400)
        val charLen = computeLastNWordsCharLength(before, desiredWords)
        swipeSelectedCharLength = charLen
        if (charLen > 0) {
            bridge.setSelectionOffsets(before.length, charLen)
            onHapticTick(0.6f)
        } else {
            bridge.clearSelectionToEnd()
        }
        _state.update { it.copy(swipeSelectedWordCount = if (charLen > 0) desiredWords else 0) }
    }

    fun onUndoTap(autoCapEnabled: Boolean) {
        val chunk = undoStack.removeLastOrNull() ?: return
        onHapticTick(1.0f)
        bridge.commitText(chunk)
        _state.update {
            it.copy(
                undoBufferCount = undoStack.size,
                lastDeletedPreview = null
            )
        }
        syncAutoCapitalization(autoCapEnabled)
    }

    fun onSpacebarDragStart() {
        _state.update { it.copy(isSpacebarCursorActive = true, cursorDeltaIndicator = 0) }
    }

    fun onSpacebarCursorStep(steps: Int) {
        if (steps == 0) return
        val moved = bridge.moveCursorBy(steps)
        if (moved) {
            onHapticTick(0.45f)
            _state.update { it.copy(cursorDeltaIndicator = it.cursorDeltaIndicator + steps) }
        }
    }

    fun onSpacebarDragEnd(autoCapEnabled: Boolean) {
        _state.update { it.copy(isSpacebarCursorActive = false, cursorDeltaIndicator = 0) }
        syncAutoCapitalization(autoCapEnabled)
    }

    fun pasteText(text: String, autoCapEnabled: Boolean) {
        onHapticTick(1.0f)
        bridge.commitText(text)
        syncAutoCapitalization(autoCapEnabled)
    }

    private fun deleteSingleUnicodeChar(autoCapEnabled: Boolean): Boolean {
        val selected = bridge.getSelectedText()
        if (!selected.isNullOrEmpty()) {
            bridge.deleteSurroundingText(1, 0)
            pushToUndo(selected)
            onHapticTick(0.9f)
            syncAutoCapitalization(autoCapEnabled)
            return true
        }
        val before = bridge.getTextBeforeCursor(16)
        if (before.isEmpty()) return false
        val charCount = lastGraphemeLength(before)
        val deleted = before.takeLast(charCount)
        bridge.deleteSurroundingText(charCount, 0)
        pushToUndo(deleted)
        onHapticTick(0.85f)
        syncAutoCapitalization(autoCapEnabled)
        return true
    }

    private fun deletePrecedingWordWithHighlight(autoCapEnabled: Boolean) {
        val before = bridge.getTextBeforeCursor(128)
        if (before.isEmpty()) return
        val len = computeLastNWordsCharLength(before, 1)
        if (len <= 0) return
        val deleted = before.takeLast(len)
        bridge.highlightDeletedPreview(true)
        bridge.deleteSurroundingText(len, 0)
        pushToUndo(deleted)
        triggerWordHighlight(deleted)
        onHapticTick(1.15f)
        syncAutoCapitalization(autoCapEnabled)
    }

    private fun triggerWordHighlight(deleted: String) {
        highlightResetJob?.cancel()
        _state.update {
            it.copy(
                isWordDeleteHighlight = true,
                lastDeletedPreview = deleted.trim().ifEmpty { "space" }.take(18)
            )
        }
        highlightResetJob = scope.launch {
            delay(650L)
            _state.update { it.copy(isWordDeleteHighlight = false) }
        }
    }

    private fun pushToUndo(chunk: String) {
        if (chunk.isEmpty()) return
        // Maintain max 50 undo entries / characters
        if (undoStack.size >= MAX_UNDO_ITEMS) {
            undoStack.removeFirst()
        }
        undoStack.addLast(chunk)
        _state.update { it.copy(undoBufferCount = undoStack.size) }
    }

    companion object {
        const val MAX_UNDO_ITEMS = 50

        fun shouldAutoCapitalize(textBeforeCursor: String): Boolean {
            if (textBeforeCursor.isEmpty()) return true
            if (textBeforeCursor.endsWith('\n')) return true
            // Capitalize after full stop / exclamation / question mark followed by one or more spaces
            val trimmedEnd = textBeforeCursor.dropLastWhile { it == ' ' }
            if (trimmedEnd.length < textBeforeCursor.length) {
                val lastNonSpace = trimmedEnd.lastOrNull()
                if (lastNonSpace == '.' || lastNonSpace == '!' || lastNonSpace == '?') {
                    return true
                }
            }
            return false
        }

        fun lastGraphemeLength(text: String): Int {
            if (text.isEmpty()) return 0
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(text)
            val end = iterator.last()
            val prev = iterator.previous()
            return if (prev != BreakIterator.DONE && end > prev) end - prev else 1
        }

        fun computeLastNWordsCharLength(text: String, wordCount: Int): Int {
            if (text.isEmpty() || wordCount <= 0) return 0
            var idx = text.length
            var wordsFound = 0

            while (wordsFound < wordCount && idx > 0) {
                // Include trailing whitespace belonging to this word step
                while (idx > 0 && text[idx - 1].isWhitespace() && text[idx - 1] != '\n') {
                    idx--
                }
                if (idx == 0) break
                if (text[idx - 1] == '\n') {
                    if (wordsFound == 0) idx--
                    break
                }
                // Consume word characters or single punctuation token
                if (text[idx - 1].isLetterOrDigit() || text[idx - 1] == '_' || text[idx - 1] == '\'') {
                    while (idx > 0 && (text[idx - 1].isLetterOrDigit() || text[idx - 1] == '_' || text[idx - 1] == '\'')) {
                        idx--
                    }
                } else {
                    idx--
                }
                wordsFound++
            }
            return text.length - idx
        }
    }
}
