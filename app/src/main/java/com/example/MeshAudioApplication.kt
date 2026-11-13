package com.example

import android.app.Application
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.data.local.AudioNoteRepository
import com.example.data.local.MeshDatabase
import com.example.data.local.MeshSettingsRepository
import com.example.mesh.MeshCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MeshAudioApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: MeshDatabase by lazy { MeshDatabase.getInstance(this) }
    val noteRepository: AudioNoteRepository by lazy {
        AudioNoteRepository(this, database.audioNoteDao())
    }
    val settingsRepository: MeshSettingsRepository by lazy {
        MeshSettingsRepository(this)
    }
    val recorderManager: AudioRecorderManager by lazy {
        AudioRecorderManager(this, applicationScope)
    }
    val playerManager: AudioPlayerManager by lazy {
        AudioPlayerManager(applicationScope)
    }
    val meshCoordinator: MeshCoordinator by lazy {
        MeshCoordinator(this, applicationScope, noteRepository, settingsRepository)
    }
}
