package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ClipboardEntity::class, SnippetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KetomDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun snippetDao(): SnippetDao

    companion object {
        @Volatile
        private var INSTANCE: KetomDatabase? = null

        fun getInstance(context: Context): KetomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KetomDatabase::class.java,
                    "ketomsync.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
