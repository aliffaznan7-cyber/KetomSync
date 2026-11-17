package com.example.keyboard

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-app sandbox implementation of InputConnectionBridge so users can test every
 * KetomSync keyboard gesture (tap/hold/swipe backspace, undo, 12dp/char spacebar cursor,
 * auto-capitalization, clipboard chips, and macro expansion) immediately inside the app.
 */
class SandboxEditorBridge(
    initialText: String = "Try typing here or test KetomSync gestures! "
) : InputConnectionBridge {

    private val _fieldValue = MutableStateFlow(
        TextFieldValue(
            text = initialText,
            selection = TextRange(initialText.length)
        )
    )
    val fieldValue: StateFlow<TextFieldValue> = _fieldValue.asStateFlow()

    fun updateFromExternalField(newValue: TextFieldValue) {
        _fieldValue.value = newValue
    }

    override fun commitText(text: String) {
        _fieldValue.update { current ->
            val min = minOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
            val max = maxOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
            val updatedText = buildString {
                append(current.text.substring(0, min))
                append(text)
                append(current.text.substring(max))
            }
            val newCursor = min + text.length
            TextFieldValue(text = updatedText, selection = TextRange(newCursor))
        }
    }

    override fun getTextBeforeCursor(length: Int): String {
        val current = _fieldValue.value
        val start = minOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
        val from = (start - length).coerceAtLeast(0)
        return current.text.substring(from, start)
    }

    override fun getTextAfterCursor(length: Int): String {
        val current = _fieldValue.value
        val end = maxOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
        val to = (end + length).coerceAtMost(current.text.length)
        return current.text.substring(end, to)
    }

    override fun getSelectedText(): String? {
        val current = _fieldValue.value
        val min = minOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
        val max = maxOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
        return if (max > min) current.text.substring(min, max) else null
    }

    override fun setSelectionOffsets(beforeCursorLength: Int, selectionCharCount: Int) {
        _fieldValue.update { current ->
            val end = maxOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
            val start = (end - selectionCharCount).coerceAtLeast(0)
            current.copy(selection = TextRange(start, end))
        }
    }

    override fun clearSelectionToEnd() {
        _fieldValue.update { current ->
            val end = maxOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
            current.copy(selection = TextRange(end, end))
        }
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        _fieldValue.update { current ->
            val min = minOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
            val max = maxOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
            if (max > min && beforeLength > 0 && afterLength == 0) {
                val updated = current.text.removeRange(min, max)
                TextFieldValue(text = updated, selection = TextRange(min))
            } else {
                val delStart = (min - beforeLength).coerceAtLeast(0)
                val delEnd = (max + afterLength).coerceAtMost(current.text.length)
                val updated = current.text.removeRange(delStart, delEnd)
                TextFieldValue(text = updated, selection = TextRange(delStart))
            }
        }
    }

    override fun moveCursorBy(deltaChars: Int): Boolean {
        val current = _fieldValue.value
        val pos = current.selection.end.coerceIn(0, current.text.length)
        val target = (pos + deltaChars).coerceIn(0, current.text.length)
        if (target == pos) return false
        _fieldValue.value = current.copy(selection = TextRange(target, target))
        return true
    }

    override fun performEnterAction() {
        commitText("\n")
    }

    override fun highlightDeletedPreview(active: Boolean) {
        // Rendered via keyboard status banner
    }
}
