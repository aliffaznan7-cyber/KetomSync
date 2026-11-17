package com.example.keyboard

import com.example.data.model.ClipboardCategory
import com.example.data.util.ClipboardCategorizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KetomKeyboardControllerTest {

    @Test
    fun tapBackspace_deletesSingleUnicodeGraphemeAndRestoresViaUndo() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bridge = SandboxEditorBridge("Hello 👋")
        val controller = KetomKeyboardController(bridge, scope)

        // Tap backspace once should remove the multi-codeunit emoji 👋
        controller.onBackspacePressDown(autoCapEnabled = true)
        controller.onBackspaceRelease(autoCapEnabled = true)
        assertEquals("Hello ", bridge.fieldValue.value.text)
        assertEquals(1, controller.state.value.undoBufferCount)

        // Undo restores 👋
        controller.onUndoTap(autoCapEnabled = true)
        assertEquals("Hello 👋", bridge.fieldValue.value.text)
        assertEquals(0, controller.state.value.undoBufferCount)
        scope.cancel()
    }

    @Test
    fun undoBuffer_capsAtFiftyEntries() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bridge = SandboxEditorBridge("a".repeat(60))
        val controller = KetomKeyboardController(bridge, scope)

        repeat(55) {
            controller.onBackspacePressDown(autoCapEnabled = false)
            controller.onBackspaceRelease(autoCapEnabled = false)
        }
        assertEquals(5, bridge.fieldValue.value.text.length)
        assertEquals(50, controller.state.value.undoBufferCount)
        scope.cancel()
    }

    @Test
    fun holdBackspace_deletesPrecedingWordWithHighlight() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bridge = SandboxEditorBridge("KetomSync keyboard ")
        val controller = KetomKeyboardController(bridge, scope)

        controller.onBackspacePressDown(autoCapEnabled = true)
        delay(440L) // within 300-600ms window
        controller.onBackspaceRelease(autoCapEnabled = true)

        assertEquals("KetomSync ", bridge.fieldValue.value.text)
        assertTrue(controller.state.value.isWordDeleteHighlight)
        scope.cancel()
    }

    @Test
    fun swipeLeftFromBackspace_selectsWordsAndSwipeRightCancels() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bridge = SandboxEditorBridge("one two three four")
        val controller = KetomKeyboardController(bridge, scope)

        controller.onBackspacePressDown(autoCapEnabled = false)
        // Swipe left 2 words (stepWidth = 40f -> -85f selects 2 words: "three four")
        controller.onBackspaceSwipeHorizontal(-85f, 40f)
        assertEquals(2, controller.state.value.swipeSelectedWordCount)
        assertEquals("three four", bridge.getSelectedText())

        // Swipe back right to cancel selection
        controller.onBackspaceSwipeHorizontal(0f, 40f)
        assertEquals(0, controller.state.value.swipeSelectedWordCount)
        assertEquals(null, bridge.getSelectedText())

        controller.onBackspaceRelease(autoCapEnabled = false)
        assertEquals("one two three four", bridge.fieldValue.value.text)
        scope.cancel()
    }

    @Test
    fun spacebarCursorStep_movesCursorAccurately() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bridge = SandboxEditorBridge("abcdef")
        val controller = KetomKeyboardController(bridge, scope)

        controller.onSpacebarDragStart()
        controller.onSpacebarCursorStep(-2)
        assertEquals(4, bridge.fieldValue.value.selection.end)
        controller.onSpacebarCursorStep(1)
        assertEquals(5, bridge.fieldValue.value.selection.end)
        controller.onSpacebarDragEnd(autoCapEnabled = false)
        scope.cancel()
    }

    @Test
    fun autoCapitalization_detectsSentenceBoundaries() {
        assertTrue(KetomKeyboardController.shouldAutoCapitalize(""))
        assertTrue(KetomKeyboardController.shouldAutoCapitalize("Hello. "))
        assertTrue(KetomKeyboardController.shouldAutoCapitalize("Line one\n"))
        assertFalse(KetomKeyboardController.shouldAutoCapitalize("Hello "))
        assertFalse(KetomKeyboardController.shouldAutoCapitalize("Hello."))
    }

    @Test
    fun clipboardCategorizer_andMacroExpansion_workAccurately() {
        assertEquals(ClipboardCategory.LINK, ClipboardCategorizer.categorize("https://kotlinlang.org"))
        assertEquals(ClipboardCategory.CODE, ClipboardCategorizer.categorize("fun main() { println(1) }"))
        assertEquals(ClipboardCategory.ADDRESS, ClipboardCategorizer.categorize("742 Evergreen St, 90210"))
        assertEquals(ClipboardCategory.TEXT, ClipboardCategorizer.categorize("Pick up milk later"))

        val vars = ClipboardCategorizer.extractTemplateVariables("Hi {name}, meet at {place}!")
        assertEquals(listOf("name", "place"), vars)
        val expanded = ClipboardCategorizer.expandTemplate(
            "Hi {name}, meet at {place}!",
            mapOf("name" to "Aliff", "place" to "GMI")
        )
        assertEquals("Hi Aliff, meet at GMI!", expanded)
    }
}
