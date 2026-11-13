package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NoteDirection {
    INCOMING,
    OUTGOING,
    RELAYED
}

enum class TransferStatus {
    LOCAL_ONLY,
    QUEUED,
    SENDING,
    DELIVERED,
    FAILED,
    RECEIVED
}

@Entity(tableName = "audio_notes")
data class AudioNoteEntity(
    @PrimaryKey
    val id: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String? = null,
    val recipientName: String? = null,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val timestampMs: Long,
    val direction: NoteDirection,
    val isRead: Boolean,
    val transferStatus: TransferStatus,
    val hopCount: Int = 0,
    val maxHops: Int = 3,
    val waveformSamples: String = "",
    val sha256Checksum: String = "",
    val relayCount: Int = 0
) {
    fun waveformList(): List<Float> {
        if (waveformSamples.isBlank()) return List(36) { 0.25f }
        return waveformSamples.split(",")
            .mapNotNull { it.toFloatOrNull()?.coerceIn(0.05f, 1f) }
            .ifEmpty { List(36) { 0.25f } }
    }
}
