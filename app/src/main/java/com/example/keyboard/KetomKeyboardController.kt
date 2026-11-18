package com.example.keyboard

import com.example.data.local.ClipboardEntity
import com.example.data.local.KetomSettings
import com.example.data.local.SnippetEntity
import com.example.data.model.ClipboardCategory
import com.example.data.model.DeviceType
import com.example.data.util.ClipboardCategorizer
import com.example.data.util.HapticFeedbackHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

data class ClipChipUiModel(
    val entity: ClipboardEntity,
    val previewText: String,
    val category: ClipboardCategory,
    val isTabletSource: Boolean
)

data class IndexedSnippetUiModel(
    val snippet: SnippetEntity,
    val variables: List<String>
)

data class HapticConfig(
    val enabled: Boolean = true,
    val intensity: Float = 0.7f,
    val durationMs: Int = 14
)

/**
 * Low-latency stateful controller implementing KetomSync's Gboard-grade input,
 * zero-IPC preceding text caching, accelerated backspace, undo, spacebar cursor drag,
 * auto-capitalization, customizable haptic intensity, and background off-main-thread
 * snippet/clipboard indexing.
 */
class KetomKeyboardController(
    private val bridge: InputConnectionBridge,
    private val scope: CoroutineScope,
    private val onHapticTick: (Float) -> Unit = {},
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val hapticHelper: HapticFeedbackHelper? = null,
    initialHapticConfig: HapticConfig = HapticConfig()
) {
    private val _state = MutableStateFlow(KeyboardUiState())
    val state: StateFlow<KeyboardUiState> = _state.asStateFlow()

    private val _hapticConfig = MutableStateFlow(initialHapticConfig)
    val hapticConfig: StateFlow<HapticConfig> = _hapticConfig.asStateFlow()

    private val _clipChips = MutableStateFlow<List<ClipChipUiModel>>(emptyList())
    val clipChips: StateFlow<List<ClipChipUiModel>> = _clipChips.asStateFlow()

    private val _indexedSnippets = MutableStateFlow<List<IndexedSnippetUiModel>>(emptyList())
    val indexedSnippets: StateFlow<List<IndexedSnippetUiModel>> = _indexedSnippets.asStateFlow()

    private val _filteredSnippets = MutableStateFlow<List<IndexedSnippetUiModel>>(emptyList())
    val filteredSnippets: StateFlow<List<IndexedSnippetUiModel>> = _filteredSnippets.asStateFlow()

    // 50-entry / 50-character undo buffer
    private val undoLock = Any()
    private val undoStack = ArrayDeque<String>(MAX_UNDO_ITEMS)

    // In-memory preceding text cache to eliminate per-keystroke getTextBeforeCursor() Binder IPC
    private val cacheLock = Any()
    private val precedingTextCache = StringBuilder(MAX_TEXT_CACHE_CHARS)
    @Volatile
    private var isCacheValid = false
    @Volatile
    private var cacheMayBeTruncated = false
    @Volatile
    private var possiblyHasSelection = true

    private var backspaceHoldJob: Job? = null
    private var highlightResetJob: Job? = null
    private var chipPrepJob: Job? = null
    private var snippetPrepJob: Job? = null
    private var cacheWarmJob: Job? = null
    private var settingsBindJob: Job? = null

    private var didHoldActionTrigger = false
    private var swipeSelectActive = false
    private var swipeSelectedCharLength = 0
    private var swipeLastWordCount = 0

    /**
     * Updates the active key-press haptic feedback intensity, duration, and enable state
     * applied by this controller on every key press and gesture.
     */
    fun updateHapticConfig(enabled: Boolean, intensity: Float, durationMs: Int) {
        _hapticConfig.value = HapticConfig(
            enabled = enabled,
            intensity = intensity.coerceIn(0f, 1.0f),
            durationMs = durationMs.coerceIn(1, 50)
        )
    }

    /**
     * Binds controller haptic intensity directly to a DataStore-backed [KetomSettings] flow
     * so changes saved in Settings take effect immediately across key presses.
     */
    fun bindHapticSettings(settingsFlow: Flow<KetomSettings>) {
        settingsBindJob?.cancel()
        settingsBindJob = scope.launch {
            settingsFlow.collectLatest { settings ->
                updateHapticConfig(
                    enabled = settings.hapticEnabled,
                    intensity = settings.hapticStrength,
                    durationMs = settings.hapticDurationMs
                )
            }
        }
    }

    /**
     * Precomputes clipboard chip display models off the Main Thread (Dispatchers.Default)
     * so the top keyboard strip never performs string sanitization or enum parsing on frame draw.
     */
    fun updateClipboardChipsAsync(clips: List<ClipboardEntity>) {
        chipPrepJob?.cancel()
        chipPrepJob = scope.launch(computeDispatcher) {
            val prepared = clips.take(MAX_RECENT_CLIP_CHIPS).map { clip ->
                ClipChipUiModel(
                    entity = clip,
                    previewText = clip.content.replace('\n', ' ').take(24),
                    category = ClipboardCategory.fromWire(clip.category),
                    isTabletSource = clip.deviceType == DeviceType.TABLET.wireValue
                )
            }
            _clipChips.value = prepared
        }
    }

    /**
     * Extracts template variables and applies optional search filtering off the Main Thread
     * (Dispatchers.Default) to avoid regex/allocation spikes while typing.
     */
    fun updateSnippetsIndexAsync(snippets: List<SnippetEntity>, query: String = "") {
        snippetPrepJob?.cancel()
        snippetPrepJob = scope.launch(computeDispatcher) {
            val indexed = snippets.map { snippet ->
                IndexedSnippetUiModel(
                    snippet = snippet,
                    variables = ClipboardCategorizer.extractTemplateVariables(snippet.body)
                )
            }
            _indexedSnippets.value = indexed
            _filteredSnippets.value = applySnippetFilter(indexed, query)
        }
    }

    /**
     * Filters pre-indexed snippets off the Main Thread.
     */
    fun filterSnippetsAsync(query: String) {
        val currentIndexed = _indexedSnippets.value
        snippetPrepJob?.cancel()
        snippetPrepJob = scope.launch(computeDispatcher) {
            _filteredSnippets.value = applySnippetFilter(currentIndexed, query)
        }
    }

    /**
     * Offloads background clipboard/history maintenance tasks to Dispatchers.IO
     * so active typing never competes with storage or sync IPC.
     */
    fun dispatchHistoryUpdateAsync(block: suspend () -> Unit) {
        scope.launch(ioDispatcher) {
            block()
        }
    }

    /**
     * Marks the in-memory preceding text cache stale (for example, after an external cursor jump
     * or switching target editor fields).
     */
    fun invalidateTextCache() {
        synchronized(cacheLock) {
            isCacheValid = false
            cacheMayBeTruncated = false
            possiblyHasSelection = true
            precedingTextCache.setLength(0)
        }
    }

    /**
     * Warms the preceding text cache off the Main Thread (Dispatchers.IO) on focus changes,
     * then updates auto-capitalization without stalling UI layout/draw.
     */
    fun warmTextCacheAsync(autoCapEnabled: Boolean) {
        cacheWarmJob?.cancel()
        cacheWarmJob = scope.launch(ioDispatcher) {
            val snapshot = bridge.getTextBeforeCursor(MAX_TEXT_CACHE_CHARS)
            synchronized(cacheLock) {
                precedingTextCache.setLength(0)
                precedingTextCache.append(snapshot)
                isCacheValid = true
                cacheMayBeTruncated = snapshot.length >= MAX_TEXT_CACHE_CHARS
                possiblyHasSelection = true
            }
            if (autoCapEnabled && _state.value.shiftMode != ShiftMode.CAPS_LOCK) {
                val shouldCap = shouldAutoCapitalize(snapshot)
                _state.update {
                    it.copy(shiftMode = if (shouldCap) ShiftMode.SHIFT_ONCE else ShiftMode.LOWERCASE)
                }
            }
        }
    }

    /**
     * Hydrates the preceding text cache from the bridge once and syncs auto-capitalization.
     * Use [invalidateTextCache] + [syncAutoCapitalization] on editor focus changes.
     */
    fun syncAutoCapitalization(autoCapEnabled: Boolean) {
        val before = hydratePrecedingTextCacheFromBridge(64)
        if (!autoCapEnabled) return
        if (_state.value.shiftMode == ShiftMode.CAPS_LOCK) return

        val shouldCap = shouldAutoCapitalize(before)
        _state.update {
            it.copy(shiftMode = if (shouldCap) ShiftMode.SHIFT_ONCE else ShiftMode.LOWERCASE)
        }
    }

    fun onKeyTap(rawLabel: String, autoCapEnabled: Boolean) {
        emitHaptic(1.0f)
        val currentState = _state.value
        val charToInsert = if (currentState.layer == KeyboardLayer.ALPHA &&
            rawLabel.length == 1 &&
            rawLabel[0].isLetter()
        ) {
            when (currentState.shiftMode) {
                ShiftMode.LOWERCASE -> rawLabel.lowercase()
                ShiftMode.SHIFT_ONCE, ShiftMode.CAPS_LOCK -> rawLabel.uppercase()
            }
        } else {
            rawLabel
        }

        bridge.commitText(charToInsert)
        appendToPrecedingCache(charToInsert)

        if (currentState.layer == KeyboardLayer.ALPHA) {
            if (currentState.shiftMode == ShiftMode.SHIFT_ONCE) {
                _state.update { it.copy(shiftMode = ShiftMode.LOWERCASE) }
            }
            if (charToInsert == " " || charToInsert == "\n") {
                syncAutoCapitalizationFromCache(autoCapEnabled)
            }
        }
    }

    fun onEnterTap(autoCapEnabled: Boolean) {
        emitHaptic(1.1f)
        bridge.performEnterAction()
        appendToPrecedingCache("\n")
        if (autoCapEnabled && _state.value.shiftMode != ShiftMode.CAPS_LOCK) {
            syncAutoCapitalizationFromCache(autoCapEnabled)
        }
    }

    fun onShiftTap(lastTapMillis: Long, nowMillis: Long): Long {
        emitHaptic(1.0f)
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
        emitHaptic(1.0f)
        _state.update {
            val next = if (it.layer == KeyboardLayer.ALPHA) KeyboardLayer.SYMBOLS_1 else KeyboardLayer.ALPHA
            it.copy(layer = next)
        }
    }

    fun cycleSymbolSubLayer() {
        emitHaptic(1.0f)
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
                    ?: getCachedTextBeforeCursor(selectedCount)
                bridge.clearSelectionToEnd()
                if (selectedText.isNotEmpty()) {
                    bridge.deleteSurroundingText(selectedText.length, 0)
                    removeFromPrecedingCacheTail(selectedText.length)
                    pushToUndo(selectedText)
                    triggerWordHighlight(selectedText)
                    emitHaptic(1.2f)
                }
            } else {
                bridge.clearSelectionToEnd()
            }
            possiblyHasSelection = false
            swipeSelectActive = false
            swipeSelectedCharLength = 0
            swipeLastWordCount = 0
            _state.update { it.copy(swipeSelectedWordCount = 0) }
            syncAutoCapitalizationFromCache(autoCapEnabled)
            return
        }

        if (!didHoldActionTrigger) {
            deleteSingleUnicodeChar(autoCapEnabled)
        }
        didHoldActionTrigger = false
    }

    /**
     * Swipe left from Backspace: selects previous words progressively using the
     * in-memory preceding text cache (zero repeated getTextBeforeCursor IPC);
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
            possiblyHasSelection = false
            _state.update { it.copy(swipeSelectedWordCount = 0) }
            return
        }

        val before = getCachedTextBeforeCursor(400)
        val charLen = computeLastNWordsCharLength(before, desiredWords)
        swipeSelectedCharLength = charLen
        if (charLen > 0) {
            bridge.setSelectionOffsets(before.length, charLen)
            possiblyHasSelection = true
            emitHaptic(0.6f)
        } else {
            bridge.clearSelectionToEnd()
            possiblyHasSelection = false
        }
        _state.update { it.copy(swipeSelectedWordCount = if (charLen > 0) desiredWords else 0) }
    }

    fun onUndoTap(autoCapEnabled: Boolean) {
        val (chunk, remainingCount) = synchronized(undoLock) {
            val popped = undoStack.removeLastOrNull() ?: return
            popped to undoStack.size
        }
        emitHaptic(1.0f)
        bridge.commitText(chunk)
        appendToPrecedingCache(chunk)
        _state.update {
            it.copy(
                undoBufferCount = remainingCount,
                lastDeletedPreview = null
            )
        }
        syncAutoCapitalizationFromCache(autoCapEnabled)
    }

    fun onSpacebarTouchDown() {
        emitHaptic(0.85f)
    }

    fun onSpacebarTapCommit(autoCapEnabled: Boolean) {
        val currentState = _state.value
        bridge.commitText(" ")
        appendToPrecedingCache(" ")

        if (currentState.layer == KeyboardLayer.ALPHA) {
            if (currentState.shiftMode == ShiftMode.SHIFT_ONCE) {
                _state.update { it.copy(shiftMode = ShiftMode.LOWERCASE) }
            }
            syncAutoCapitalizationFromCache(autoCapEnabled)
        }
    }

    fun onSpacebarDragStart() {
        _state.update { it.copy(isSpacebarCursorActive = true, cursorDeltaIndicator = 0) }
    }

    fun onSpacebarCursorStep(steps: Int) {
        if (steps == 0) return
        val moved = bridge.moveCursorBy(steps)
        if (moved) {
            emitHaptic(0.45f)
            _state.update { it.copy(cursorDeltaIndicator = it.cursorDeltaIndicator + steps) }
        }
    }

    fun onSpacebarDragEnd(autoCapEnabled: Boolean) {
        _state.update { it.copy(isSpacebarCursorActive = false, cursorDeltaIndicator = 0) }
        invalidateTextCache()
        syncAutoCapitalization(autoCapEnabled)
    }

    fun pasteText(text: String, autoCapEnabled: Boolean) {
        emitHaptic(1.0f)
        bridge.commitText(text)
        appendToPrecedingCache(text)
        syncAutoCapitalizationFromCache(autoCapEnabled)
    }

    private fun deleteSingleUnicodeChar(autoCapEnabled: Boolean): Boolean {
        if (possiblyHasSelection) {
            val selected = bridge.getSelectedText()
            if (!selected.isNullOrEmpty()) {
                bridge.deleteSurroundingText(1, 0)
                invalidateTextCache()
                possiblyHasSelection = false
                pushToUndo(selected)
                emitHaptic(0.9f)
                syncAutoCapitalizationFromCache(autoCapEnabled)
                return true
            }
            possiblyHasSelection = false
        }

        val before = getCachedTextBeforeCursor(16)
        if (before.isEmpty()) return false
        val charCount = lastGraphemeLength(before)
        val deleted = before.takeLast(charCount)
        bridge.deleteSurroundingText(charCount, 0)
        removeFromPrecedingCacheTail(charCount)
        pushToUndo(deleted)
        emitHaptic(0.85f)
        syncAutoCapitalizationFromCache(autoCapEnabled)
        return true
    }

    private fun deletePrecedingWordWithHighlight(autoCapEnabled: Boolean) {
        val before = getCachedTextBeforeCursor(128)
        if (before.isEmpty()) return
        val len = computeLastNWordsCharLength(before, 1)
        if (len <= 0) return
        val deleted = before.takeLast(len)
        bridge.highlightDeletedPreview(true)
        bridge.deleteSurroundingText(len, 0)
        removeFromPrecedingCacheTail(len)
        possiblyHasSelection = false
        pushToUndo(deleted)
        triggerWordHighlight(deleted)
        emitHaptic(1.15f)
        syncAutoCapitalizationFromCache(autoCapEnabled)
    }

    private fun emitHaptic(keyMultiplier: Float) {
        val cfg = _hapticConfig.value
        if (!cfg.enabled || cfg.intensity <= 0f || keyMultiplier <= 0f) return
        val effectiveIntensity = (cfg.intensity * keyMultiplier).coerceIn(0.05f, 1.5f)
        hapticHelper?.pulse(
            enabled = true,
            strength = cfg.intensity,
            durationMs = cfg.durationMs,
            multiplier = keyMultiplier
        )
        onHapticTick(effectiveIntensity)
    }

    private fun syncAutoCapitalizationFromCache(autoCapEnabled: Boolean) {
        if (!autoCapEnabled) return
        if (_state.value.shiftMode == ShiftMode.CAPS_LOCK) return

        val before = getCachedTextBeforeCursor(64)
        val shouldCap = shouldAutoCapitalize(before)
        _state.update {
            it.copy(shiftMode = if (shouldCap) ShiftMode.SHIFT_ONCE else ShiftMode.LOWERCASE)
        }
    }

    private fun getCachedTextBeforeCursor(maxLength: Int): String {
        synchronized(cacheLock) {
            val canServeFromMemory = isCacheValid && (
                !cacheMayBeTruncated ||
                    precedingTextCache.length >= maxLength ||
                    precedingTextCache.length >= MIN_CACHE_TAIL_REFILL
                )
            if (canServeFromMemory) {
                val len = precedingTextCache.length
                val start = (len - maxLength).coerceAtLeast(0)
                return precedingTextCache.substring(start, len)
            }
        }
        return hydratePrecedingTextCacheFromBridge(maxLength)
    }

    private fun hydratePrecedingTextCacheFromBridge(maxLength: Int): String {
        val snapshot = bridge.getTextBeforeCursor(MAX_TEXT_CACHE_CHARS)
        synchronized(cacheLock) {
            precedingTextCache.setLength(0)
            precedingTextCache.append(snapshot)
            isCacheValid = true
            cacheMayBeTruncated = snapshot.length >= MAX_TEXT_CACHE_CHARS
            val len = precedingTextCache.length
            val start = (len - maxLength).coerceAtLeast(0)
            return precedingTextCache.substring(start, len)
        }
    }

    private fun appendToPrecedingCache(text: String) {
        if (text.isEmpty()) return
        synchronized(cacheLock) {
            possiblyHasSelection = false
            if (!isCacheValid) return
            precedingTextCache.append(text)
            if (precedingTextCache.length > MAX_TEXT_CACHE_CHARS) {
                val excess = precedingTextCache.length - MAX_TEXT_CACHE_CHARS
                precedingTextCache.delete(0, excess)
                cacheMayBeTruncated = true
            }
        }
    }

    private fun removeFromPrecedingCacheTail(charCount: Int) {
        if (charCount <= 0) return
        synchronized(cacheLock) {
            possiblyHasSelection = false
            if (!isCacheValid) return
            val removeCount = minOf(charCount, precedingTextCache.length)
            val start = precedingTextCache.length - removeCount
            precedingTextCache.delete(start, precedingTextCache.length)
        }
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
        val newSize = synchronized(undoLock) {
            // Maintain max 50 undo entries / characters
            if (undoStack.size >= MAX_UNDO_ITEMS) {
                undoStack.removeFirst()
            }
            undoStack.addLast(chunk)
            undoStack.size
        }
        _state.update { it.copy(undoBufferCount = newSize) }
    }

    private fun applySnippetFilter(
        indexed: List<IndexedSnippetUiModel>,
        query: String
    ): List<IndexedSnippetUiModel> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return indexed
        return indexed.filter { item ->
            item.snippet.title.lowercase().contains(q) ||
                item.snippet.shortcut.lowercase().contains(q) ||
                item.snippet.body.lowercase().contains(q)
        }
    }

    companion object {
        const val MAX_UNDO_ITEMS = 50
        const val MAX_TEXT_CACHE_CHARS = 512
        private const val MIN_CACHE_TAIL_REFILL = 32
        private const val MAX_RECENT_CLIP_CHIPS = 10

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
