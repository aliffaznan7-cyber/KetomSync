package com.example.keyboard

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

interface InputConnectionBridge {
    fun commitText(text: String)
    fun getTextBeforeCursor(length: Int): String
    fun getTextAfterCursor(length: Int): String
    fun getSelectedText(): String?
    fun setSelectionOffsets(beforeCursorLength: Int, selectionCharCount: Int)
    fun clearSelectionToEnd()
    fun deleteSurroundingText(beforeLength: Int, afterLength: Int)
    fun moveCursorBy(deltaChars: Int): Boolean
    fun performEnterAction()
    fun highlightDeletedPreview(active: Boolean)
}

class AndroidInputConnectionBridge(
    private val inputConnectionProvider: () -> InputConnection?,
    private val editorInfoProvider: () -> EditorInfo?
) : InputConnectionBridge {

    private var currentTextLengthBeforeCursorSnapshot: Int = 0

    override fun commitText(text: String) {
        val ic = inputConnectionProvider() ?: return
        ic.beginBatchEdit()
        ic.commitText(text, 1)
        ic.endBatchEdit()
    }

    override fun getTextBeforeCursor(length: Int): String {
        val ic = inputConnectionProvider() ?: return ""
        return ic.getTextBeforeCursor(length, 0)?.toString() ?: ""
    }

    override fun getTextAfterCursor(length: Int): String {
        val ic = inputConnectionProvider() ?: return ""
        return ic.getTextAfterCursor(length, 0)?.toString() ?: ""
    }

    override fun getSelectedText(): String? {
        val ic = inputConnectionProvider() ?: return null
        return ic.getSelectedText(0)?.toString()
    }

    override fun setSelectionOffsets(beforeCursorLength: Int, selectionCharCount: Int) {
        val ic = inputConnectionProvider() ?: return
        val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        if (extracted != null) {
            val end = extracted.selectionEnd.coerceAtLeast(0)
            val start = (end - selectionCharCount).coerceAtLeast(0)
            ic.setSelection(start, end)
        } else {
            // Fallback using composing region highlight if ExtractedText isn't supported
            val before = ic.getTextBeforeCursor(beforeCursorLength.coerceAtLeast(selectionCharCount), 0) ?: return
            val safeCount = selectionCharCount.coerceAtMost(before.length)
            currentTextLengthBeforeCursorSnapshot = safeCount
            ic.beginBatchEdit()
            ic.setComposingText(before.subSequence(before.length - safeCount, before.length), 1)
            ic.endBatchEdit()
        }
    }

    override fun clearSelectionToEnd() {
        val ic = inputConnectionProvider() ?: return
        val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        if (extracted != null) {
            val end = extracted.selectionEnd.coerceAtLeast(0)
            ic.setSelection(end, end)
        } else {
            ic.finishComposingText()
        }
        currentTextLengthBeforeCursorSnapshot = 0
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        val ic = inputConnectionProvider() ?: return
        ic.beginBatchEdit()
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty() && beforeLength > 0 && afterLength == 0) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(beforeLength, afterLength)
        }
        ic.endBatchEdit()
    }

    override fun moveCursorBy(deltaChars: Int): Boolean {
        if (deltaChars == 0) return false
        val ic = inputConnectionProvider() ?: return false
        val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        if (extracted != null) {
            val currentPos = extracted.selectionEnd.coerceAtLeast(0)
            val textLen = extracted.text?.length ?: Int.MAX_VALUE
            val target = (currentPos + deltaChars).coerceIn(0, textLen)
            if (target == currentPos) return false
            return ic.setSelection(target, target)
        }
        // Fallback via DPAD key events
        val keyCode = if (deltaChars < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val steps = kotlin.math.abs(deltaChars)
        repeat(steps) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        return true
    }

    override fun performEnterAction() {
        val ic = inputConnectionProvider() ?: return
        val actionId = (editorInfoProvider()?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(actionId)
        } else {
            ic.commitText("\n", 1)
        }
    }

    override fun highlightDeletedPreview(active: Boolean) {
        // Visual highlight is rendered in keyboard status strip & keycap glow
    }
}
