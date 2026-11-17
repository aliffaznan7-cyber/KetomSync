package com.example.data.local

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.data.model.ClipboardCategory
import com.example.data.model.DeviceType
import com.example.data.model.FirestoreClipboardEntry
import com.example.data.model.FirestoreSnippet
import com.example.data.model.RetentionPolicy
import com.example.data.model.SnippetCategory
import com.example.data.remote.SyncRepository
import com.example.data.util.ClipboardCategorizer
import com.example.data.util.DeviceInfoProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.util.UUID

class ClipboardSyncCoordinator(
    private val context: Context,
    private val database: KetomDatabase,
    private val syncRepository: SyncRepository
) {
    private val clipboardDao = database.clipboardDao()
    private val snippetDao = database.snippetDao()
    private val deviceProfile = DeviceInfoProvider.getProfile(context)

    private val _incognitoClips = MutableStateFlow<List<ClipboardEntity>>(emptyList())
    val incognitoClips = _incognitoClips.asStateFlow()

    fun observeEffectiveClips(incognitoFlow: Flow<Boolean>): Flow<List<ClipboardEntity>> {
        return combine(
            clipboardDao.observeAll(),
            _incognitoClips,
            incognitoFlow
        ) { localClips, sessionClips, isIncognito ->
            if (isIncognito) {
                sessionClips + localClips.filter { it.isPinned }
            } else {
                localClips
            }
        }
    }

    fun observeLocalSnippets(): Flow<List<SnippetEntity>> = snippetDao.observeAll()

    suspend fun ensureStarterSnippetsSeeded() {
        if (snippetDao.count() == 0) {
            val now = System.currentTimeMillis()
            val starters = listOf(
                SnippetEntity(
                    remoteId = "starter_standup",
                    title = "Daily Standup",
                    shortcut = "/standup",
                    body = "Hi {team}, today I'm working on {task} from {device}.",
                    category = SnippetCategory.MACRO.wireValue,
                    updatedAtMillis = now
                ),
                SnippetEntity(
                    remoteId = "starter_eta",
                    title = "Quick ETA Reply",
                    shortcut = "/eta",
                    body = "On my way to {location}! ETA {minutes} mins.",
                    category = SnippetCategory.REPLY.wireValue,
                    updatedAtMillis = now - 1000L
                ),
                SnippetEntity(
                    remoteId = "starter_adb",
                    title = "Git Conventional Commit",
                    shortcut = "/commit",
                    body = "feat({scope}): {summary}",
                    category = SnippetCategory.CODE.wireValue,
                    updatedAtMillis = now - 2000L
                )
            )
            starters.forEach { snippetDao.insert(it) }
        }
    }

    suspend fun recordCapturedText(
        rawText: String,
        retentionPolicy: RetentionPolicy,
        isIncognito: Boolean,
        syncToCloud: Boolean
    ) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        val category = ClipboardCategorizer.categorize(text)
        val now = System.currentTimeMillis()

        if (isIncognito) {
            val current = _incognitoClips.value.filterNot { it.content == text }
            val ephemeral = ClipboardEntity(
                id = -now,
                remoteId = null,
                content = text,
                category = category.wireValue,
                isPinned = false,
                deviceName = "${deviceProfile.deviceName} (Incognito)",
                deviceType = deviceProfile.deviceType.wireValue,
                retentionPolicy = RetentionPolicy.ONE_HOUR.wireValue,
                createdAtMillis = now,
                updatedAtMillis = now,
                expiresAtMillis = null
            )
            _incognitoClips.value = (listOf(ephemeral) + current).take(25)
            return
        }

        val expiresAt = retentionPolicy.durationMillis?.let { now + it }
        val existing = clipboardDao.findByContent(text)
        if (existing != null) {
            clipboardDao.insert(
                existing.copy(
                    category = category.wireValue,
                    retentionPolicy = retentionPolicy.wireValue,
                    updatedAtMillis = now,
                    expiresAtMillis = if (existing.isPinned) null else expiresAt
                )
            )
            clipboardDao.deleteExpired(now)
            clipboardDao.trimUnpinnedToLimit(100)
            return
        }

        val remoteId = "clip_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val insertedId = clipboardDao.insert(
            ClipboardEntity(
                remoteId = remoteId,
                content = text,
                category = category.wireValue,
                isPinned = false,
                deviceName = deviceProfile.deviceName,
                deviceType = deviceProfile.deviceType.wireValue,
                retentionPolicy = retentionPolicy.wireValue,
                createdAtMillis = now,
                updatedAtMillis = now,
                expiresAtMillis = expiresAt
            )
        )
        clipboardDao.deleteExpired(now)
        clipboardDao.trimUnpinnedToLimit(100)

        if (syncToCloud && Firebase.auth.currentUser != null) {
            val pushRes = syncRepository.pushClipboardEntry(
                content = text,
                category = category,
                isPinned = false,
                deviceName = deviceProfile.deviceName,
                deviceType = deviceProfile.deviceType,
                retentionPolicy = retentionPolicy,
                entryId = remoteId
            )
            if (pushRes.isSuccess) {
                clipboardDao.updateRemoteId(insertedId, remoteId)
            }
        }
    }

    suspend fun mergeRemoteClips(remoteEntries: List<FirestoreClipboardEntry>) {
        val now = System.currentTimeMillis()
        remoteEntries.forEach { remote ->
            if (remote.id.isBlank() || remote.content.isBlank()) return@forEach
            val byRemote = clipboardDao.findByRemoteId(remote.id)
            val byContent = byRemote ?: clipboardDao.findByContent(remote.content)
            val createdMs = remote.createdAt?.toDate()?.time ?: now
            val updatedMs = remote.updatedAt?.toDate()?.time ?: now
            val policy = RetentionPolicy.fromWire(remote.retentionPolicy)
            val expiresAt = if (remote.isPinned) null else policy.durationMillis?.let { updatedMs + it }

            if (byContent == null) {
                clipboardDao.insert(
                    ClipboardEntity(
                        remoteId = remote.id,
                        content = remote.content,
                        category = ClipboardCategory.fromWire(remote.category).wireValue,
                        isPinned = remote.isPinned,
                        deviceName = remote.deviceName.ifBlank { "Synced Device" },
                        deviceType = DeviceType.fromWire(remote.deviceType).wireValue,
                        retentionPolicy = policy.wireValue,
                        createdAtMillis = createdMs,
                        updatedAtMillis = updatedMs,
                        expiresAtMillis = expiresAt
                    )
                )
            } else if (updatedMs > byContent.updatedAtMillis || byContent.remoteId == null) {
                clipboardDao.insert(
                    byContent.copy(
                        remoteId = remote.id,
                        isPinned = remote.isPinned,
                        retentionPolicy = policy.wireValue,
                        updatedAtMillis = maxOf(updatedMs, byContent.updatedAtMillis),
                        expiresAtMillis = expiresAt
                    )
                )
            }
        }
        clipboardDao.deleteExpired(now)
        clipboardDao.trimUnpinnedToLimit(100)
    }

    suspend fun mergeRemoteSnippets(remoteSnippets: List<FirestoreSnippet>) {
        remoteSnippets.forEach { remote ->
            if (remote.id.isBlank()) return@forEach
            val existing = snippetDao.findByRemoteId(remote.id)
            val updatedMs = remote.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
            if (existing == null || updatedMs > existing.updatedAtMillis) {
                snippetDao.insert(
                    SnippetEntity(
                        id = existing?.id ?: 0L,
                        remoteId = remote.id,
                        title = remote.title,
                        shortcut = remote.shortcut,
                        body = remote.body,
                        category = remote.category,
                        updatedAtMillis = updatedMs
                    )
                )
            }
        }
    }

    suspend fun togglePin(entry: ClipboardEntity, syncToCloud: Boolean) {
        if (entry.id < 0) return
        val nextPinned = !entry.isPinned
        val now = System.currentTimeMillis()
        val policy = RetentionPolicy.fromWire(entry.retentionPolicy)
        val expiresAt = if (nextPinned) null else policy.durationMillis?.let { now + it }
        clipboardDao.updatePinAndRetention(
            id = entry.id,
            isPinned = nextPinned,
            retentionPolicy = policy.wireValue,
            expiresAtMillis = expiresAt,
            updatedAtMillis = now
        )
        if (syncToCloud && Firebase.auth.currentUser != null && !entry.remoteId.isNullOrBlank()) {
            syncRepository.updateClipboardPinAndRetention(entry.remoteId, nextPinned, policy)
        }
    }

    suspend fun updateRetentionForAllUnpinned(policy: RetentionPolicy) {
        clipboardDao.deleteExpired(System.currentTimeMillis())
    }

    suspend fun deleteEntry(entry: ClipboardEntity, syncToCloud: Boolean) {
        if (entry.id < 0) {
            _incognitoClips.value = _incognitoClips.value.filterNot { it.id == entry.id }
            return
        }
        clipboardDao.deleteById(entry.id)
        if (syncToCloud && Firebase.auth.currentUser != null && !entry.remoteId.isNullOrBlank()) {
            syncRepository.deleteClipboardEntry(entry.remoteId)
        }
    }

    suspend fun clearUnpinnedHistory() {
        clipboardDao.clearUnpinned()
        _incognitoClips.value = emptyList()
    }

    fun clearIncognitoSession() {
        _incognitoClips.value = emptyList()
    }

    suspend fun saveSnippet(
        existingId: Long? = null,
        existingRemoteId: String? = null,
        title: String,
        shortcut: String,
        body: String,
        category: SnippetCategory,
        syncToCloud: Boolean
    ) {
        val now = System.currentTimeMillis()
        val remoteId = existingRemoteId ?: "snip_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val localId = snippetDao.insert(
            SnippetEntity(
                id = existingId ?: 0L,
                remoteId = remoteId,
                title = title.trim(),
                shortcut = shortcut.trim(),
                body = body.trim(),
                category = category.wireValue,
                updatedAtMillis = now
            )
        )
        if (syncToCloud && Firebase.auth.currentUser != null) {
            val res = syncRepository.upsertSnippet(
                snippetId = existingRemoteId,
                title = title,
                shortcut = shortcut,
                body = body,
                category = category
            )
            res.getOrNull()?.let { assignedId ->
                snippetDao.updateRemoteId(localId, assignedId)
            }
        }
    }

    suspend fun deleteSnippet(snippet: SnippetEntity, syncToCloud: Boolean) {
        snippetDao.deleteById(snippet.id)
        if (syncToCloud && Firebase.auth.currentUser != null && !snippet.remoteId.isNullOrBlank()) {
            syncRepository.deleteSnippet(snippet.remoteId)
        }
    }

    fun copyToSystemClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("KetomSync", text))
    }

    fun readSystemClipboardText(): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }
}
