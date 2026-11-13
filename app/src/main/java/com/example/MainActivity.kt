package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.MeshAudioApp
import com.example.ui.MeshViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MeshAudioApplication
        val factory = MeshViewModel.Factory(
            noteRepository = app.noteRepository,
            settingsRepository = app.settingsRepository,
            recorderManager = app.recorderManager,
            playerManager = app.playerManager,
            meshCoordinator = app.meshCoordinator
        )
        val viewModel = ViewModelProvider(this, factory)[MeshViewModel::class.java]

        setContent {
            MeshAudioApp(viewModel = viewModel)
        }
    }
}
