package com.example.data.local

import androidx.room.TypeConverter

class AudioNoteConverters {
    @TypeConverter
    fun fromNoteDirection(value: NoteDirection): String = value.name

    @TypeConverter
    fun toNoteDirection(value: String): NoteDirection =
        runCatching { NoteDirection.valueOf(value) }.getOrDefault(NoteDirection.INCOMING)

    @TypeConverter
    fun fromTransferStatus(value: TransferStatus): String = value.name

    @TypeConverter
    fun toTransferStatus(value: String): TransferStatus =
        runCatching { TransferStatus.valueOf(value) }.getOrDefault(TransferStatus.LOCAL_ONLY)
}
