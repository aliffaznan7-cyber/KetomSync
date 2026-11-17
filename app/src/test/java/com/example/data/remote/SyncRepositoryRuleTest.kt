package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ClipboardCategory
import com.example.data.model.DeviceType
import com.example.data.model.RetentionPolicy
import com.example.data.model.SnippetCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun pushClipboardEntry_authenticatedOwner_createsAndObservesEntry() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = SyncRepository(firestore, auth)
        val entryId = "clip_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.pushClipboardEntry(
                content = "https://developer.android.com/jetpack/compose",
                category = ClipboardCategory.LINK,
                isPinned = false,
                deviceName = "Google Pixel 9 Pro",
                deviceType = DeviceType.PHONE,
                retentionPolicy = RetentionPolicy.TWENTY_FOUR_HOURS,
                entryId = entryId
            )
        }
        assertTrue(createResult.isSuccess)

        val entries = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeClipboardEntries().first { list -> list.any { it.id == entryId } }
        }
        val created = entries.first { it.id == entryId }
        assertEquals(ClipboardCategory.LINK.wireValue, created.category)
        assertEquals(false, created.isPinned)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateClipboardPinAndRetention(entryId, true, RetentionPolicy.NEVER)
        }
        assertTrue(updateResult.isSuccess)

        val updatedEntries = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeClipboardEntries().first { list ->
                list.any { it.id == entryId && it.isPinned }
            }
        }
        assertTrue(updatedEntries.first { it.id == entryId }.isPinned)
    }

    @Test
    fun upsertSnippetAndDevice_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = SyncRepository(firestore, auth)

        val snippetRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertSnippet(
                snippetId = null,
                title = "Meeting Note",
                shortcut = "/meet",
                body = "Join {room} at {time}",
                category = SnippetCategory.MACRO
            )
        }
        assertTrue(snippetRes.isSuccess)

        val snippets = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSnippets().first { it.isNotEmpty() }
        }
        assertTrue(snippets.any { it.shortcut == "/meet" })

        val deviceRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerOrHeartbeatDevice(
                deviceId = "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                deviceName = "Xiaomi Pad 7",
                deviceType = DeviceType.TABLET,
                modelName = "Xiaomi Pad 7",
                syncEnabled = true
            )
        }
        assertTrue(deviceRes.isSuccess)
    }

    @Test
    fun deleteClipboardEntry_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SyncRepository(firestore, auth)
        val entryId = "clip_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.pushClipboardEntry(
                content = "Private Alice Secret",
                category = ClipboardCategory.TEXT,
                isPinned = false,
                deviceName = "Pixel 9 Pro",
                deviceType = DeviceType.PHONE,
                retentionPolicy = RetentionPolicy.ONE_HOUR,
                entryId = entryId
            )
        }
        assertTrue(createRes.isSuccess)

        signInTestUser(BOB_EMAIL)
        var denied = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("clipboard_entries").document(entryId)
                    .delete().await()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            denied = fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Expected PERMISSION_DENIED for cross-user delete", denied)
    }

    @Test
    fun observeClipboardEntries_unauthenticated_failsFast() = runBlocking {
        auth.signOut()
        val repository = SyncRepository(firestore, auth)
        var failed = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeClipboardEntries().first()
            }
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue("Expected unauthenticated check to fail", failed)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_ketom@test.com"
        const val BOB_EMAIL = "bob_ketom@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
