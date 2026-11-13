package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [AudioNoteEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(AudioNoteConverters::class)
abstract class MeshDatabase : RoomDatabase() {
    abstract fun audioNoteDao(): AudioNoteDao

    companion object {
        @Volatile
        private var INSTANCE: MeshDatabase? = null

        fun getInstance(context: Context): MeshDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeshDatabase::class.java,
                    "local_mesh_audio_notes.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
