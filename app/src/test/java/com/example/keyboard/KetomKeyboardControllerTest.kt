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

    @Test
    fun precedingTextCache_avoidsRepeatedIpcDuringRapidTypingAndBackspace() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val delegate = SandboxEditorBridge("")
        var getTextBeforeCursorCalls = 0
        val countingBridge = object : InputConnectionBridge by delegate {
            override fun getTextBeforeCursor(length: Int): String {
                getTextBeforeCursorCalls++
                return delegate.getTextBeforeCursor(length)
            }
        }
        val controller = KetomKeyboardController(
            bridge = countingBridge,
            scope = scope,
            computeDispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined
        )

        // Single initial hydration on focus
        controller.syncAutoCapitalization(autoCapEnabled = true)
        assertEquals(1, getTextBeforeCursorCalls)

        // Rapid sentence typing including spaces & punctuation should incur ZERO additional IPC calls
        "hello. world ".forEach { ch ->
            if (ch == ' ') {
                controller.onSpacebarTouchDown()
                controller.onSpacebarTapCommit(autoCapEnabled = true)
            } else {
                controller.onKeyTap(ch.toString(), autoCapEnabled = true)
            }
        }
        assertEquals("Hello. World ", delegate.fieldValue.value.text)
        assertEquals(ShiftMode.LOWERCASE, controller.state.value.shiftMode)
        assertEquals(1, getTextBeforeCursorCalls)

        // Rapid backspaces should also hit the in-memory cache with ZERO additional IPC calls
        repeat(6) {
            controller.onBackspacePressDown(autoCapEnabled = true)
            controller.onBackspaceRelease(autoCapEnabled = true)
        }
        assertEquals("Hello. ", delegate.fieldValue.value.text)
        assertEquals(ShiftMode.SHIFT_ONCE, controller.state.value.shiftMode)
        assertEquals(1, getTextBeforeCursorCalls)

        scope.cancel()
    }

    @Test
    fun backgroundSnippetAndClipIndexing_populatesDerivedUiModels() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bridge = SandboxEditorBridge()
        val controller = KetomKeyboardController(
            bridge = bridge,
            scope = scope,
            computeDispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined
        )

        val snippets = listOf(
            com.example.data.local.SnippetEntity(
                id = 1L,
                remoteId = "s1",
                title = "Standup",
                shortcut = "/standup",
                body = "Working on {task} in {repo}",
                category = "macro",
                updatedAtMillis = 100L
            ),
            com.example.data.local.SnippetEntity(
                id = 2L,
                remoteId = "s2",
                title = "Greeting",
                shortcut = "/hi",
                body = "Hello there!",
                category = "reply",
                updatedAtMillis = 90L
            )
        )
        controller.updateSnippetsIndexAsync(snippets)
        assertEquals(2, controller.filteredSnippets.value.size)
        assertEquals(listOf("task", "repo"), controller.filteredSnippets.value.first().variables)

        controller.filterSnippetsAsync("standup")
        assertEquals(1, controller.filteredSnippets.value.size)
        assertEquals("/standup", controller.filteredSnippets.value.first().snippet.shortcut)

        val clips = listOf(
            com.example.data.local.ClipboardEntity(
                id = 10L,
                remoteId = "c1",
                content = "https://example.com\nline2",
                category = ClipboardCategory.LINK.wireValue,
                isPinned = true,
                deviceName = "Xiaomi Pad 7",
                deviceType = com.example.data.model.DeviceType.TABLET.wireValue,
                retentionPolicy = com.example.data.model.RetentionPolicy.NEVER.wireValue,
                createdAtMillis = 100L,
                updatedAtMillis = 100L,
                expiresAtMillis = null
            )
        )
        controller.updateClipboardChipsAsync(clips)
        assertEquals(1, controller.clipChips.value.size)
        assertEquals("https://example.com line", controller.clipChips.value.first().previewText)
        assertTrue(controller.clipChips.value.first().isTabletSource)

        scope.cancel()
    }

    @Test
    fun hapticIntensity_isAppliedAndScaledByController() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bridge = SandboxEditorBridge()
        val settingsFlow = kotlinx.coroutines.flow.MutableStateFlow(
            com.example.data.local.KetomSettings(
                hapticEnabled = true,
                hapticStrength = 0.4f,
                hapticDurationMs = 12
            )
        )
        val emittedIntensities = mutableListOf<Float>()
        val controller = KetomKeyboardController(
            bridge = bridge,
            scope = scope,
            onHapticTick = { emittedIntensities.add(it) },
            computeDispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined
        )
        controller.bindHapticSettings(settingsFlow)

        controller.onKeyTap("a", autoCapEnabled = false)
        assertEquals(1, emittedIntensities.size)
        assertEquals(0.4f, emittedIntensities[0], 0.001f)

        // Updating DataStore-backed settings updates controller intensity immediately
        settingsFlow.value = settingsFlow.value.copy(hapticStrength = 0.9f, hapticDurationMs = 24)
        controller.onEnterTap(autoCapEnabled = false)
        assertEquals(2, emittedIntensities.size)
        assertEquals(0.99f, emittedIntensities[1], 0.001f)
        assertEquals(24, controller.hapticConfig.value.durationMs)

        // Disabling haptics stops key-press vibrations
        settingsFlow.value = settingsFlow.value.copy(hapticEnabled = false)
        controller.onKeyTap("b", autoCapEnabled = false)
        assertEquals(2, emittedIntensities.size)

        scope.cancel()
    }
}
