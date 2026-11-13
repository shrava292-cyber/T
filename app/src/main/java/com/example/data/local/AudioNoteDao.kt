package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioNoteDao {
    @Query("SELECT * FROM audio_notes ORDER BY timestampMs DESC")
    fun getAllNotes(): Flow<List<AudioNoteEntity>>

    @Query("SELECT * FROM audio_notes WHERE direction IN ('INCOMING', 'RELAYED') ORDER BY timestampMs DESC")
    fun getInboxNotes(): Flow<List<AudioNoteEntity>>

    @Query("SELECT * FROM audio_notes WHERE direction = 'OUTGOING' ORDER BY timestampMs DESC")
    fun getOutboxNotes(): Flow<List<AudioNoteEntity>>

    @Query("SELECT COUNT(*) FROM audio_notes WHERE isRead = 0 AND direction IN ('INCOMING', 'RELAYED')")
    fun getUnreadCount(): Flow<Int>

    @Query("SELECT * FROM audio_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): AudioNoteEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM audio_notes WHERE id = :id)")
    suspend fun existsById(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: AudioNoteEntity)

    @Query("UPDATE audio_notes SET isRead = :isRead WHERE id = :id")
    suspend fun setReadStatus(id: String, isRead: Boolean)

    @Query("UPDATE audio_notes SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    @Query("UPDATE audio_notes SET transferStatus = :status WHERE id = :id")
    suspend fun updateTransferStatus(id: String, status: TransferStatus)

    @Query("UPDATE audio_notes SET relayCount = relayCount + 1 WHERE id = :id")
    suspend fun incrementRelayCount(id: String)

    @Query("DELETE FROM audio_notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)
}
