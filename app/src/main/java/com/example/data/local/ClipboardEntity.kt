package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.ClipboardCategory
import com.example.data.model.DeviceType
import com.example.data.model.RetentionPolicy

@Entity(
    tableName = "clipboard_entries",
    indices = [Index(value = ["remoteId"], unique = true)]
)
data class ClipboardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: String? = null,
    val content: String,
    val category: String = ClipboardCategory.TEXT.wireValue,
    val isPinned: Boolean = false,
    val deviceName: String,
    val deviceType: String = DeviceType.PHONE.wireValue,
    val retentionPolicy: String = RetentionPolicy.TWENTY_FOUR_HOURS.wireValue,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long? = null
)
