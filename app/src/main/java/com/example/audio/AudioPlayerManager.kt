package com.example.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.data.local.AudioNoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val activeNoteId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

class AudioPlayerManager(
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    fun togglePlayback(note: AudioNoteEntity, onStartedPlaying: (AudioNoteEntity) -> Unit = {}) {
        val current = _state.value
        if (current.activeNoteId == note.id) {
            val player = mediaPlayer
            if (player != null) {
                if (current.isPlaying) {
                    player.pause()
                    stopTicker()
                    _state.update { it.copy(isPlaying = false) }
                } else {
                    player.start()
                    startTicker()
                    _state.update { it.copy(isPlaying = true) }
                }
                return
            }
        }
        startNewPlayback(note, onStartedPlaying)
    }

    private fun startNewPlayback(note: AudioNoteEntity, onStartedPlaying: (AudioNoteEntity) -> Unit) {
        stop()
        val file = File(note.filePath)
        if (!file.exists() || file.length() == 0L) {
            _state.value = PlaybackState(
                errorMessage = "Audio payload file missing on disk"
            )
            return
        }

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopTicker()
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            currentPositionMs = 0L
                        )
                    }
                    runCatching { seekTo(0) }
                }
                start()
            }

            mediaPlayer = player
            val resolvedDuration = player.duration.toLong().takeIf { it > 0L } ?: note.durationMs
            _state.value = PlaybackState(
                activeNoteId = note.id,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = resolvedDuration,
                errorMessage = null
            )
            startTicker()
            onStartedPlaying(note)
        } catch (e: Exception) {
            stop()
            _state.value = PlaybackState(
                errorMessage = "Playback error: ${e.localizedMessage ?: "Unsupported audio"}"
            )
        }
    }

    fun seekToFraction(note: AudioNoteEntity, fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (_state.value.activeNoteId != note.id) {
            startNewPlayback(note) {}
        }
        val player = mediaPlayer ?: return
        val duration = _state.value.durationMs.takeIf { it > 0L } ?: note.durationMs
        val targetMs = (duration * clamped).toInt()
        runCatching {
            player.seekTo(targetMs)
            _state.update { it.copy(currentPositionMs = targetMs.toLong()) }
        }
    }

    fun stop() {
        stopTicker()
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _state.value = PlaybackState()
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun startTicker() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && runCatching { player.isPlaying }.getOrDefault(false)) {
                    val pos = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
                    _state.update { it.copy(currentPositionMs = pos) }
                }
                delay(40L)
            }
        }
    }

    private fun stopTicker() {
        progressJob?.cancel()
        progressJob = null
    }
}
