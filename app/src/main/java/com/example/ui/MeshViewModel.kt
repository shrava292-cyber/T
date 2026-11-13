package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.PlaybackState
import com.example.audio.RecordingState
import com.example.data.local.AudioNoteEntity
import com.example.data.local.AudioNoteRepository
import com.example.data.local.MeshSettings
import com.example.data.local.MeshSettingsRepository
import com.example.data.local.NoteDirection
import com.example.data.local.TransferStatus
import com.example.mesh.MeshCoordinator
import com.example.mesh.MeshLogEntry
import com.example.mesh.MeshPeer
import com.example.mesh.MeshServiceStatus
import com.example.mesh.TransferProgress
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NoteFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    INBOX("Inbox"),
    OUTBOX("Outbox")
}

data class MeshUiState(
    val notes: List<AudioNoteEntity> = emptyList(),
    val unreadCount: Int = 0,
    val peers: List<MeshPeer> = emptyList(),
    val serviceStatus: MeshServiceStatus = MeshServiceStatus(),
    val recordingState: RecordingState = RecordingState(),
    val playbackState: PlaybackState = PlaybackState(),
    val activeTransfer: TransferProgress? = null,
    val logs: List<MeshLogEntry> = emptyList(),
    val settings: MeshSettings = MeshSettings(
        localNodeId = "NODE-INIT",
        callsign = "Field Node",
        autoRelayEnabled = true,
        maxHopTtl = 3,
        backgroundDiscoveryEnabled = true,
        themeMode = ThemeMode.SYSTEM
    ),
    val selectedTargetPeerId: String? = null,
    val selectedFilter: NoteFilter = NoteFilter.ALL,
    val searchQuery: String = "",
    val hasRequiredPermissions: Boolean = false
) {
    val selectedTargetPeer: MeshPeer?
        get() = peers.firstOrNull { it.nodeId == selectedTargetPeerId }

    val filteredNotes: List<AudioNoteEntity>
        get() {
            val base = when (selectedFilter) {
                NoteFilter.ALL -> notes
                NoteFilter.UNREAD -> notes.filter { !it.isRead }
                NoteFilter.INBOX -> notes.filter { it.direction == NoteDirection.INCOMING || it.direction == NoteDirection.RELAYED }
                NoteFilter.OUTBOX -> notes.filter { it.direction == NoteDirection.OUTGOING }
            }
            val query = searchQuery.trim().lowercase()
            if (query.isEmpty()) return base
            return base.filter {
                it.senderName.lowercase().contains(query) ||
                    it.senderId.lowercase().contains(query) ||
                    (it.recipientName?.lowercase()?.contains(query) == true)
            }
        }
}

class MeshViewModel(
    private val noteRepository: AudioNoteRepository,
    private val settingsRepository: MeshSettingsRepository,
    private val recorderManager: AudioRecorderManager,
    private val playerManager: AudioPlayerManager,
    private val meshCoordinator: MeshCoordinator
) : ViewModel() {

    private val selectedTargetPeerId = MutableStateFlow<String?>(null)
    private val selectedFilter = MutableStateFlow(NoteFilter.ALL)
    private val searchQuery = MutableStateFlow("")
    private val hasPermissions = MutableStateFlow(false)

    private val localControlsFlow = combine(
        selectedTargetPeerId,
        selectedFilter,
        searchQuery,
        hasPermissions
    ) { targetId, filter, query, perms ->
        Quad(targetId, filter, query, perms)
    }

    private val meshDataFlow = combine(
        noteRepository.allNotes,
        noteRepository.unreadCount,
        meshCoordinator.peers,
        meshCoordinator.serviceStatus,
        meshCoordinator.activeTransfer
    ) { notes, unread, peers, status, transfer ->
        MeshDataSnapshot(notes, unread, peers, status, transfer)
    }

    val uiState: StateFlow<MeshUiState> = combine(
        meshDataFlow,
        recorderManager.state,
        playerManager.state,
        meshCoordinator.logs,
        settingsRepository.settingsFlow,
        localControlsFlow
    ) { values ->
        val meshData = values[0] as MeshDataSnapshot
        val recState = values[1] as RecordingState
        val playState = values[2] as PlaybackState
        @Suppress("UNCHECKED_CAST")
        val logEntries = values[3] as List<MeshLogEntry>
        val settings = values[4] as MeshSettings
        val controls = values[5] as Quad

        MeshUiState(
            notes = meshData.notes,
            unreadCount = meshData.unread,
            peers = meshData.peers,
            serviceStatus = meshData.status,
            recordingState = recState,
            playbackState = playState,
            activeTransfer = meshData.transfer,
            logs = logEntries,
            settings = settings,
            selectedTargetPeerId = controls.targetId,
            selectedFilter = controls.filter,
            searchQuery = controls.query,
            hasRequiredPermissions = controls.perms
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MeshUiState()
    )

    fun onPermissionsUpdated(granted: Boolean) {
        val wasGranted = hasPermissions.value
        hasPermissions.value = granted
        if (granted && !wasGranted && uiState.value.settings.backgroundDiscoveryEnabled) {
            meshCoordinator.startMeshService()
        }
    }

    fun toggleDiscoveryService() {
        val running = uiState.value.serviceStatus.isServiceRunning
        viewModelScope.launch {
            settingsRepository.setBackgroundDiscovery(!running)
            if (running) {
                meshCoordinator.stopMeshService()
            } else {
                meshCoordinator.startMeshService()
            }
        }
    }

    fun refreshMeshSweep() {
        meshCoordinator.refreshDiscovery()
    }

    fun connectPeer(peer: MeshPeer) {
        meshCoordinator.connectToPeer(peer)
    }

    fun selectTargetPeer(nodeId: String?) {
        selectedTargetPeerId.value = nodeId
    }

    fun startRecording(locked: Boolean = false) {
        playerManager.stop()
        recorderManager.startRecording(
            targetFileProvider = { noteId -> noteRepository.createAudioFileForNote(noteId) },
            locked = locked
        )
    }

    fun lockRecording() {
        recorderManager.setLocked(true)
    }

    fun finishAndBroadcastRecording() {
        val result = recorderManager.stopRecording() ?: return
        val currentState = uiState.value
        val targetPeer = currentState.selectedTargetPeer

        viewModelScope.launch {
            val note = AudioNoteEntity(
                id = result.noteId,
                senderId = currentState.settings.localNodeId,
                senderName = currentState.settings.callsign,
                recipientId = targetPeer?.nodeId,
                recipientName = targetPeer?.displayName,
                filePath = result.file.absolutePath,
                durationMs = result.durationMs,
                fileSizeBytes = result.fileSizeBytes,
                timestampMs = System.currentTimeMillis(),
                direction = NoteDirection.OUTGOING,
                isRead = true,
                transferStatus = TransferStatus.QUEUED,
                hopCount = 0,
                maxHops = currentState.settings.maxHopTtl,
                waveformSamples = result.waveformSamples,
                sha256Checksum = result.sha256Checksum
            )
            noteRepository.saveNote(note)
            meshCoordinator.broadcastOrSendNote(note, targetPeer)
        }
    }

    fun cancelRecording() {
        recorderManager.cancelRecording()
    }

    fun clearRecorderError() {
        recorderManager.clearError()
    }

    fun clearPlaybackError() {
        playerManager.clearError()
    }

    fun togglePlayNote(note: AudioNoteEntity) {
        playerManager.togglePlayback(note) { playedNote ->
            if (!playedNote.isRead) {
                viewModelScope.launch {
                    noteRepository.markAsRead(playedNote.id)
                }
            }
        }
    }

    fun seekNoteWaveform(note: AudioNoteEntity, fraction: Float) {
        playerManager.seekToFraction(note, fraction)
    }

    fun toggleReadStatus(note: AudioNoteEntity) {
        viewModelScope.launch {
            noteRepository.toggleRead(note.id, note.isRead)
        }
    }

    fun markAllNotesRead() {
        viewModelScope.launch {
            noteRepository.markAllAsRead()
        }
    }

    fun relayNote(note: AudioNoteEntity) {
        meshCoordinator.relayExistingNote(note)
    }

    fun deleteNote(note: AudioNoteEntity) {
        if (uiState.value.playbackState.activeNoteId == note.id) {
            playerManager.stop()
        }
        viewModelScope.launch {
            noteRepository.deleteNote(note)
        }
    }

    fun setFilter(filter: NoteFilter) {
        selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateCallsign(callsign: String) {
        viewModelScope.launch {
            settingsRepository.updateCallsign(callsign)
        }
    }

    fun setAutoRelay(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoRelay(enabled)
        }
    }

    fun setMaxHopTtl(hops: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxHopTtl(hops)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    private data class Quad(
        val targetId: String?,
        val filter: NoteFilter,
        val query: String,
        val perms: Boolean
    )

    private data class MeshDataSnapshot(
        val notes: List<AudioNoteEntity>,
        val unread: Int,
        val peers: List<MeshPeer>,
        val status: MeshServiceStatus,
        val transfer: TransferProgress?
    )

    class Factory(
        private val noteRepository: AudioNoteRepository,
        private val settingsRepository: MeshSettingsRepository,
        private val recorderManager: AudioRecorderManager,
        private val playerManager: AudioPlayerManager,
        private val meshCoordinator: MeshCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MeshViewModel(
                noteRepository = noteRepository,
                settingsRepository = settingsRepository,
                recorderManager = recorderManager,
                playerManager = playerManager,
                meshCoordinator = meshCoordinator
            ) as T
        }
    }
}
