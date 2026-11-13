package com.example.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class AudioNoteRepository(
    private val context: Context,
    private val dao: AudioNoteDao
) {
    val allNotes: Flow<List<AudioNoteEntity>> = dao.getAllNotes()
    val inboxNotes: Flow<List<AudioNoteEntity>> = dao.getInboxNotes()
    val outboxNotes: Flow<List<AudioNoteEntity>> = dao.getOutboxNotes()
    val unreadCount: Flow<Int> = dao.getUnreadCount()

    fun getAudioDirectory(): File {
        val dir = File(context.filesDir, "mesh_audio_notes")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createAudioFileForNote(noteId: String): File =
        File(getAudioDirectory(), "note_${noteId}.m4a")

    suspend fun saveNote(note: AudioNoteEntity) {
        dao.insertNote(note)
    }

    suspend fun getNoteById(id: String): AudioNoteEntity? = dao.getNoteById(id)

    suspend fun existsById(id: String): Boolean = dao.existsById(id)

    suspend fun markAsRead(id: String) {
        dao.setReadStatus(id, isRead = true)
    }

    suspend fun toggleRead(id: String, currentlyRead: Boolean) {
        dao.setReadStatus(id, isRead = !currentlyRead)
    }

    suspend fun markAllAsRead() {
        dao.markAllRead()
    }

    suspend fun updateTransferStatus(id: String, status: TransferStatus) {
        dao.updateTransferStatus(id, status)
    }

    suspend fun incrementRelayCount(id: String) {
        dao.incrementRelayCount(id)
    }

    suspend fun deleteNote(note: AudioNoteEntity) {
        dao.deleteNoteById(note.id)
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(note.filePath)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
}
