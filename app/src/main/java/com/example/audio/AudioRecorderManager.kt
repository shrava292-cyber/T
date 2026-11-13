package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
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
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.ln

data class RecordingState(
    val isRecording: Boolean = false,
    val isLocked: Boolean = false,
    val elapsedMs: Long = 0L,
    val currentAmplitude: Float = 0f,
    val liveWaveform: List<Float> = List(36) { 0.08f },
    val errorMessage: String? = null
)

data class RecordedClipResult(
    val noteId: String,
    val file: File,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val waveformSamples: String,
    val sha256Checksum: String
)

class AudioRecorderManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var currentNoteId: String? = null
    private var startedAtMs: Long = 0L
    private var tickerJob: Job? = null
    private val rawAmplitudes = mutableListOf<Float>()

    fun startRecording(targetFileProvider: (noteId: String) -> File, locked: Boolean = false): Boolean {
        if (_state.value.isRecording) return true

        val noteId = UUID.randomUUID().toString()
        val file = targetFileProvider(noteId)
        file.parentFile?.mkdirs()

        return try {
            val recorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setAudioChannels(1)
                setMaxDuration(120_000) // 2 minute max per mesh audio note
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            outputFile = file
            currentNoteId = noteId
            startedAtMs = System.currentTimeMillis()
            rawAmplitudes.clear()

            _state.value = RecordingState(
                isRecording = true,
                isLocked = locked,
                elapsedMs = 0L,
                currentAmplitude = 0.1f,
                liveWaveform = List(36) { 0.08f },
                errorMessage = null
            )

            tickerJob?.cancel()
            tickerJob = scope.launch(Dispatchers.Default) {
                while (isActive) {
                    val ampRaw = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val normalized = normalizeAmplitude(ampRaw)
                    rawAmplitudes.add(normalized)
                    val elapsed = System.currentTimeMillis() - startedAtMs
                    val tailBars = buildTailWaveform(rawAmplitudes, barCount = 36)
                    _state.update {
                        it.copy(
                            elapsedMs = elapsed,
                            currentAmplitude = normalized,
                            liveWaveform = tailBars
                        )
                    }
                    delay(50L)
                }
            }
            true
        } catch (e: Exception) {
            cleanupFailedRecording(file)
            _state.value = RecordingState(
                isRecording = false,
                errorMessage = "Microphone unavailable: ${e.localizedMessage ?: "Permission or hardware error"}"
            )
            false
        }
    }

    fun setLocked(locked: Boolean) {
        _state.update { if (it.isRecording) it.copy(isLocked = locked) else it }
    }

    fun stopRecording(minDurationMs: Long = 350L): RecordedClipResult? {
        if (!_state.value.isRecording) return null
        tickerJob?.cancel()
        tickerJob = null

        val durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
        val file = outputFile
        val noteId = currentNoteId

        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) {
            // Stop can throw if duration was too short
        } finally {
            mediaRecorder = null
            outputFile = null
            currentNoteId = null
        }

        val capturedCopy = rawAmplitudes.toList()
        rawAmplitudes.clear()
        _state.value = RecordingState(isRecording = false)

        if (file == null || noteId == null || !file.exists() || durationMs < minDurationMs || file.length() < 256L) {
            if (file != null && file.exists()) {
                file.delete()
            }
            if (durationMs < minDurationMs) {
                _state.update { it.copy(errorMessage = "Hold longer to record a voice note") }
            }
            return null
        }

        val finalWaveform = resampleWaveform(capturedCopy, targetBars = 36)
        val waveformString = finalWaveform.joinToString(",") { "%.2f".format(it) }
        val checksum = sha256Hex(file)

        return RecordedClipResult(
            noteId = noteId,
            file = file,
            durationMs = durationMs,
            fileSizeBytes = file.length(),
            waveformSamples = waveformString,
            sha256Checksum = checksum
        )
    }

    fun cancelRecording() {
        tickerJob?.cancel()
        tickerJob = null
        val file = outputFile
        runCatching {
            mediaRecorder?.stop()
        }
        runCatching {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        }
        mediaRecorder = null
        outputFile = null
        currentNoteId = null
        rawAmplitudes.clear()
        if (file != null && file.exists()) {
            file.delete()
        }
        _state.value = RecordingState(isRecording = false)
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    private fun cleanupFailedRecording(file: File) {
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        outputFile = null
        currentNoteId = null
        if (file.exists()) file.delete()
    }

    private fun normalizeAmplitude(maxAmplitude: Int): Float {
        if (maxAmplitude <= 0) return 0.08f
        val logScaled = ln(1.0 + maxAmplitude.toDouble()) / ln(1.0 + 32767.0)
        return logScaled.toFloat().coerceIn(0.08f, 1.0f)
    }

    private fun buildTailWaveform(samples: List<Float>, barCount: Int): List<Float> {
        if (samples.isEmpty()) return List(barCount) { 0.08f }
        if (samples.size >= barCount) {
            return samples.takeLast(barCount)
        }
        val padding = List(barCount - samples.size) { 0.08f }
        return padding + samples
    }

    private fun resampleWaveform(samples: List<Float>, targetBars: Int): List<Float> {
        if (samples.isEmpty()) return List(targetBars) { 0.25f }
        if (samples.size <= targetBars) {
            return List(targetBars) { idx ->
                val mapped = (idx * samples.size) / targetBars
                samples[mapped.coerceIn(0, samples.lastIndex)]
            }
        }
        val bucketSize = samples.size.toFloat() / targetBars
        return List(targetBars) { idx ->
            val start = (idx * bucketSize).toInt().coerceIn(0, samples.lastIndex)
            val end = (((idx + 1) * bucketSize).toInt()).coerceIn(start + 1, samples.size)
            val avg = samples.subList(start, end).average().toFloat()
            avg.coerceIn(0.08f, 1.0f)
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
