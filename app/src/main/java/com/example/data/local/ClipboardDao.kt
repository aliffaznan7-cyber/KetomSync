package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_entries ORDER BY isPinned DESC, updatedAtMillis DESC")
    fun observeAll(): Flow<List<ClipboardEntity>>

    @Query("SELECT * FROM clipboard_entries WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): ClipboardEntity?

    @Query("SELECT * FROM clipboard_entries WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): ClipboardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ClipboardEntity): Long

    @Query("UPDATE clipboard_entries SET isPinned = :isPinned, retentionPolicy = :retentionPolicy, expiresAtMillis = :expiresAtMillis, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun updatePinAndRetention(
        id: Long,
        isPinned: Boolean,
        retentionPolicy: String,
        expiresAtMillis: Long?,
        updatedAtMillis: Long
    )

    @Query("UPDATE clipboard_entries SET remoteId = :remoteId WHERE id = :id")
    suspend fun updateRemoteId(id: Long, remoteId: String)

    @Query("DELETE FROM clipboard_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clipboard_entries WHERE isPinned = 0 AND expiresAtMillis IS NOT NULL AND expiresAtMillis <= :nowMillis")
    suspend fun deleteExpired(nowMillis: Long)

    @Query(
        """
        DELETE FROM clipboard_entries
        WHERE isPinned = 0 AND id NOT IN (
            SELECT id FROM clipboard_entries
            WHERE isPinned = 0
            ORDER BY updatedAtMillis DESC
            LIMIT :maxUnpinned
        )
        """
    )
    suspend fun trimUnpinnedToLimit(maxUnpinned: Int = 100)

    @Query("DELETE FROM clipboard_entries WHERE isPinned = 0")
    suspend fun clearUnpinned()

    @Query("DELETE FROM clipboard_entries")
    suspend fun clearAll()
}
