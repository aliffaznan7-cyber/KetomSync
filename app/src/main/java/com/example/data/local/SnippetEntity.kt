package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.SnippetCategory

@Entity(
    tableName = "snippets",
    indices = [Index(value = ["remoteId"], unique = true)]
)
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: String? = null,
    val title: String,
    val shortcut: String,
    val body: String,
    val category: String = SnippetCategory.MACRO.wireValue,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
