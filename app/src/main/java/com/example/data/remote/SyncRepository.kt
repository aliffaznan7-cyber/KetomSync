package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.ClipboardCategory
import com.example.data.model.DeviceType
import com.example.data.model.FirestoreClipboardEntry
import com.example.data.model.FirestoreDevice
import com.example.data.model.FirestoreSnippet
import com.example.data.model.RetentionPolicy
import com.example.data.model.SnippetCategory
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeClipboardEntries(limitCount: Long = 100L): Flow<List<FirestoreClipboardEntry>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/clipboard_entries"
        emitAll(
            db.collection("users").document(uid).collection("clipboard_entries")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(limitCount)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreClipboardEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun pushClipboardEntry(
        content: String,
        category: ClipboardCategory,
        isPinned: Boolean,
        deviceName: String,
        deviceType: DeviceType,
        retentionPolicy: RetentionPolicy,
        entryId: String = "clip_${UUID.randomUUID().toString().replace("-", "").take(20)}"
    ): Result<String> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Content cannot be blank"))
        val safeContent = trimmed.take(20000)
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("clipboard_entries").document(entryId)
        val payload = mapOf(
            "userId" to uid,
            "content" to safeContent,
            "category" to category.wireValue,
            "isPinned" to isPinned,
            "deviceName" to deviceName.take(80).ifBlank { "Android Device" },
            "deviceType" to deviceType.wireValue,
            "retentionPolicy" to retentionPolicy.wireValue,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(entryId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateClipboardPinAndRetention(
        entryId: String,
        isPinned: Boolean,
        retentionPolicy: RetentionPolicy
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("clipboard_entries").document(entryId)
        val updates = mapOf(
            "isPinned" to isPinned,
            "retentionPolicy" to retentionPolicy.wireValue,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteClipboardEntry(entryId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("clipboard_entries").document(entryId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeSnippets(): Flow<List<FirestoreSnippet>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/snippets"
        emitAll(
            db.collection("users").document(uid).collection("snippets")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreSnippet::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertSnippet(
        snippetId: String?,
        title: String,
        shortcut: String,
        body: String,
        category: SnippetCategory
    ): Result<String> {
        val uid = requireUserId()
        val id = snippetId?.takeIf { it.isNotBlank() }
            ?: "snip_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("users").document(uid).collection("snippets").document(id)
        return try {
            if (snippetId == null) {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to title.trim().take(100),
                    "shortcut" to shortcut.trim().take(40),
                    "body" to body.trim().take(5000),
                    "category" to category.wireValue,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "title" to title.trim().take(100),
                    "shortcut" to shortcut.trim().take(40),
                    "body" to body.trim().take(5000),
                    "category" to category.wireValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (snippetId == null) OperationType.CREATE else OperationType.UPDATE,
                docRef.path,
                auth
            )
            Result.failure(e)
        }
    }

    suspend fun deleteSnippet(snippetId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("snippets").document(snippetId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeDevices(): Flow<List<FirestoreDevice>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/devices"
        emitAll(
            db.collection("users").document(uid).collection("devices")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreDevice::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun registerOrHeartbeatDevice(
        deviceId: String,
        deviceName: String,
        deviceType: DeviceType,
        modelName: String,
        syncEnabled: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "deviceName" to deviceName.take(80),
                        "deviceType" to deviceType.wireValue,
                        "modelName" to modelName.take(80),
                        "syncEnabled" to syncEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "deviceName" to deviceName.take(80),
                        "deviceType" to deviceType.wireValue,
                        "modelName" to modelName.take(80),
                        "syncEnabled" to syncEnabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }
}
