package com.example.mesh

import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.local.AudioNoteEntity
import com.example.data.local.AudioNoteRepository
import com.example.data.local.MeshSettings
import com.example.data.local.MeshSettingsRepository
import com.example.data.local.NoteDirection
import com.example.data.local.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

class MeshCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val noteRepository: AudioNoteRepository,
    private val settingsRepository: MeshSettingsRepository
) {
    private val _peers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val peers: StateFlow<List<MeshPeer>> = _peers.asStateFlow()

    private val _activeTransfer = MutableStateFlow<TransferProgress?>(null)
    val activeTransfer: StateFlow<TransferProgress?> = _activeTransfer.asStateFlow()

    private val _serviceStatus = MutableStateFlow(MeshServiceStatus())
    val serviceStatus: StateFlow<MeshServiceStatus> = _serviceStatus.asStateFlow()

    private val _logs = MutableStateFlow<List<MeshLogEntry>>(emptyList())
    val logs: StateFlow<List<MeshLogEntry>> = _logs.asStateFlow()

    private val logSeq = AtomicLong(1L)
    private val peerMap = mutableMapOf<String, MeshPeer>()
    private val seenNoteIds = mutableSetOf<String>()
    private var currentSettings: MeshSettings? = null
    private var currentGroupOwnerIp: String? = null
    private var isGroupOwner: Boolean = false

    val transferProtocol = P2pTransferProtocol(
        scope = scope,
        tempFileProvider = { noteId -> noteRepository.createAudioFileForNote(noteId) },
        onNoteReceived = { header, file, remoteIp ->
            handleIncomingNote(header, file, remoteIp)
        },
        onProgress = { progress ->
            _activeTransfer.value = progress
        },
        onLog = { tag, msg, level -> addLog(tag, msg, level) }
    )

    private val bleHelper = BleDiscoveryHelper(
        context = context,
        onPeerDiscovered = { blePeer -> mergeDiscoveredPeer(blePeer) },
        onStateChanged = { bleState ->
            _serviceStatus.update { it.copy(bleState = bleState) }
        },
        onLog = { tag, msg, level -> addLog(tag, msg, level) }
    )

    private val wifiDirectManager = WifiDirectManager(
        context = context,
        onPeersUpdated = { p2pPeers -> mergeWifiDirectPeers(p2pPeers) },
        onGroupConnectionReady = { info -> onWifiDirectConnected(info) },
        onGroupDisconnected = {
            currentGroupOwnerIp = null
            isGroupOwner = false
            _serviceStatus.update { it.copy(groupOwnerIp = null) }
            addLog("P2P", "Wi-Fi Direct group disconnected", LogLevel.INFO)
        },
        onStateChanged = { p2pState ->
            _serviceStatus.update { it.copy(wifiDirectState = p2pState) }
        },
        onLog = { tag, msg, level -> addLog(tag, msg, level) }
    )

    init {
        scope.launch {
            settingsRepository.ensureNodeIdInitialized()
            settingsRepository.settingsFlow.collect { settings ->
                currentSettings = settings
                _serviceStatus.update {
                    it.copy(
                        localNodeId = settings.localNodeId,
                        localCallsign = settings.callsign
                    )
                }
            }
        }
    }

    fun startMeshService() {
        val intent = Intent(context, MeshDiscoveryService::class.java).apply {
            action = MeshDiscoveryService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure {
                    // Fallback to direct radio start if FGS start restricted
                    startRadiosInternal()
                }
        } else {
            context.startService(intent)
        }
    }

    fun stopMeshService() {
        val intent = Intent(context, MeshDiscoveryService::class.java).apply {
            action = MeshDiscoveryService.ACTION_STOP
        }
        runCatching { context.startService(intent) }
        stopRadiosInternal()
    }

    internal fun startRadiosInternal() {
        scope.launch {
            val settings = currentSettings ?: settingsRepository.settingsFlow.first()
            val port = transferProtocol.startServer(P2pTransferProtocol.DEFAULT_PORT)
            wifiDirectManager.initialize()
            wifiDirectManager.startDiscoveryAndAdvertise(
                localNodeId = settings.localNodeId,
                callsign = settings.callsign,
                tcpPort = port
            )
            bleHelper.start(localNodeId = settings.localNodeId, tcpPort = port)
            _serviceStatus.update {
                it.copy(
                    isServiceRunning = true,
                    isDiscovering = true,
                    listeningPort = port,
                    statusSummary = "Advertising & Scanning (${settings.localNodeId})"
                )
            }
            addLog("MESH", "Discovery service online (Node ${settings.localNodeId}, TCP :$port)", LogLevel.SUCCESS)
        }
    }

    internal fun stopRadiosInternal() {
        bleHelper.stop()
        wifiDirectManager.stopDiscovery()
        transferProtocol.stopServer()
        _serviceStatus.update {
            it.copy(
                isServiceRunning = false,
                isDiscovering = false,
                statusSummary = "Mesh Standby"
            )
        }
        addLog("MESH", "Mesh radios placed on standby", LogLevel.INFO)
    }

    fun refreshDiscovery() {
        scope.launch {
            val settings = currentSettings ?: settingsRepository.settingsFlow.first()
            val port = transferProtocol.startServer(P2pTransferProtocol.DEFAULT_PORT)
            wifiDirectManager.initialize()
            wifiDirectManager.startDiscoveryAndAdvertise(settings.localNodeId, settings.callsign, port)
            bleHelper.stop()
            bleHelper.start(settings.localNodeId, port)
            addLog("MESH", "Refreshed BLE & Wi-Fi Direct discovery sweep", LogLevel.INFO)
        }
    }

    fun connectToPeer(peer: MeshPeer) {
        addLog("P2P", "Connecting to ${peer.displayName} (${peer.deviceAddress})", LogLevel.INFO)
        wifiDirectManager.connectToPeer(peer.deviceAddress)
    }

    fun broadcastOrSendNote(note: AudioNoteEntity, targetPeer: MeshPeer? = null) {
        scope.launch(Dispatchers.IO) {
            val settings = currentSettings ?: settingsRepository.settingsFlow.first()
            val file = File(note.filePath)
            if (!file.exists()) {
                noteRepository.updateTransferStatus(note.id, TransferStatus.FAILED)
                return@launch
            }

            val targets = resolveTransferTargets(targetPeer)
            if (targets.isEmpty()) {
                noteRepository.updateTransferStatus(note.id, TransferStatus.LOCAL_ONLY)
                addLog(
                    "QUEUE",
                    "Saved offline locally (${note.fileSizeBytes / 1024} KB). No connected P2P socket peer yet.",
                    LogLevel.INFO
                )
                return@launch
            }

            noteRepository.updateTransferStatus(note.id, TransferStatus.SENDING)
            val totalChunks = ceil(file.length().toDouble() / P2pTransferProtocol.CHUNK_SIZE_BYTES).toInt().coerceAtLeast(1)
            val header = NoteTransferHeader(
                noteId = note.id,
                senderId = settings.localNodeId,
                senderName = settings.callsign,
                recipientId = targetPeer?.nodeId,
                durationMs = note.durationMs,
                totalBytes = file.length(),
                totalChunks = totalChunks,
                chunkSize = P2pTransferProtocol.CHUNK_SIZE_BYTES,
                hopCount = note.hopCount,
                maxHops = settings.maxHopTtl,
                waveformSamples = note.waveformSamples,
                sha256Checksum = note.sha256Checksum,
                seenNodeIds = listOf(settings.localNodeId)
            )

            var anySuccess = false
            for (peer in targets) {
                val host = peer.hostAddress ?: continue
                val result = transferProtocol.sendNote(
                    hostAddress = host,
                    port = peer.tcpPort,
                    peerName = peer.displayName,
                    header = header,
                    audioFile = file
                )
                if (result.isSuccess) {
                    anySuccess = true
                    noteRepository.incrementRelayCount(note.id)
                }
            }

            noteRepository.updateTransferStatus(
                note.id,
                if (anySuccess) TransferStatus.DELIVERED else TransferStatus.FAILED
            )
        }
    }

    fun relayExistingNote(note: AudioNoteEntity) {
        scope.launch(Dispatchers.IO) {
            val settings = currentSettings ?: settingsRepository.settingsFlow.first()
            val file = File(note.filePath)
            if (!file.exists()) return@launch

            val targets = resolveTransferTargets(null)
            if (targets.isEmpty()) {
                addLog("RELAY", "No connected Wi-Fi Direct peer to relay ${note.id.take(6)}", LogLevel.WARN)
                return@launch
            }

            val nextHop = (note.hopCount + 1).coerceAtMost(note.maxHops)
            val totalChunks = ceil(file.length().toDouble() / P2pTransferProtocol.CHUNK_SIZE_BYTES).toInt().coerceAtLeast(1)
            val header = NoteTransferHeader(
                noteId = note.id,
                senderId = note.senderId,
                senderName = note.senderName,
                recipientId = note.recipientId,
                durationMs = note.durationMs,
                totalBytes = file.length(),
                totalChunks = totalChunks,
                chunkSize = P2pTransferProtocol.CHUNK_SIZE_BYTES,
                hopCount = nextHop,
                maxHops = note.maxHops,
                waveformSamples = note.waveformSamples,
                sha256Checksum = note.sha256Checksum,
                seenNodeIds = listOf(note.senderId, settings.localNodeId)
            )

            for (peer in targets) {
                val host = peer.hostAddress ?: continue
                val res = transferProtocol.sendNote(host, peer.tcpPort, peer.displayName, header, file)
                if (res.isSuccess) {
                    noteRepository.incrementRelayCount(note.id)
                }
            }
        }
    }

    private suspend fun handleIncomingNote(
        header: NoteTransferHeader,
        file: File,
        remoteIp: String
    ) {
        val settings = currentSettings ?: settingsRepository.settingsFlow.first()
        if (seenNoteIds.contains(header.noteId) || noteRepository.existsById(header.noteId)) {
            addLog("RELAY", "Deduplicated note ${header.noteId.take(6)} already in local store", LogLevel.INFO)
            return
        }
        seenNoteIds.add(header.noteId)

        // Record remote host for future outbound transfers if not yet registered
        updatePeerHostFromSocket(header.senderId, header.senderName, remoteIp)

        val incomingHop = header.hopCount + 1
        val entity = AudioNoteEntity(
            id = header.noteId,
            senderId = header.senderId,
            senderName = header.senderName,
            recipientId = header.recipientId,
            filePath = file.absolutePath,
            durationMs = header.durationMs,
            fileSizeBytes = file.length(),
            timestampMs = System.currentTimeMillis(),
            direction = if (incomingHop > 1) NoteDirection.RELAYED else NoteDirection.INCOMING,
            isRead = false,
            transferStatus = TransferStatus.RECEIVED,
            hopCount = incomingHop,
            maxHops = header.maxHops,
            waveformSamples = header.waveformSamples,
            sha256Checksum = header.sha256Checksum,
            relayCount = 0
        )
        noteRepository.saveNote(entity)

        // Multi-hop mesh relay if broadcast note and TTL remaining
        if (settings.autoRelayEnabled &&
            header.recipientId == null &&
            incomingHop < header.maxHops
        ) {
            val relayTargets = resolveTransferTargets(null).filter { peer ->
                peer.nodeId !in header.seenNodeIds && peer.hostAddress != remoteIp
            }
            if (relayTargets.isNotEmpty()) {
                addLog(
                    "RELAY",
                    "Auto-relaying ${header.noteId.take(6)} (hop $incomingHop/${header.maxHops}) to ${relayTargets.size} peer(s)",
                    LogLevel.INFO
                )
                val forwardedHeader = header.copy(
                    hopCount = incomingHop,
                    seenNodeIds = (header.seenNodeIds + settings.localNodeId).distinct()
                )
                for (peer in relayTargets) {
                    val host = peer.hostAddress ?: continue
                    val res = transferProtocol.sendNote(host, peer.tcpPort, peer.displayName, forwardedHeader, file)
                    if (res.isSuccess) {
                        noteRepository.incrementRelayCount(entity.id)
                    }
                }
            }
        }
    }

    private fun resolveTransferTargets(specificPeer: MeshPeer?): List<MeshPeer> {
        if (specificPeer != null && specificPeer.hostAddress != null) {
            return listOf(specificPeer)
        }
        val activePeers = synchronized(peerMap) {
            peerMap.values.filter { it.hostAddress != null }
        }
        if (activePeers.isNotEmpty()) return activePeers

        val goIp = currentGroupOwnerIp
        return if (!isGroupOwner && !goIp.isNullOrBlank()) {
            listOf(
                MeshPeer(
                    nodeId = "GROUP-OWNER",
                    displayName = "Wi-Fi Direct Group Owner",
                    deviceAddress = goIp,
                    transportType = PeerTransportType.WIFI_DIRECT,
                    isConnected = true,
                    isGroupOwner = true,
                    hostAddress = goIp,
                    tcpPort = P2pTransferProtocol.DEFAULT_PORT
                )
            )
        } else {
            emptyList()
        }
    }

    private fun onWifiDirectConnected(info: WifiP2pInfo) {
        val goIp = info.groupOwnerAddress?.hostAddress
        currentGroupOwnerIp = goIp
        isGroupOwner = info.isGroupOwner
        _serviceStatus.update { it.copy(groupOwnerIp = goIp) }

        if (!info.isGroupOwner && !goIp.isNullOrBlank()) {
            synchronized(peerMap) {
                val existing = peerMap.values.firstOrNull { it.isConnected || it.transportType != PeerTransportType.BLE_BEACON }
                val key = existing?.nodeId ?: "P2P-GO-$goIp"
                val updated = (existing ?: MeshPeer(
                    nodeId = key,
                    displayName = "Group Owner ($goIp)",
                    deviceAddress = goIp,
                    transportType = PeerTransportType.WIFI_DIRECT
                )).copy(
                    isConnected = true,
                    isGroupOwner = true,
                    hostAddress = goIp
                )
                peerMap[key] = updated
                _peers.value = peerMap.values.sortedByDescending { it.lastSeenMs }
            }
        }
    }

    private fun mergeDiscoveredPeer(peer: MeshPeer) {
        synchronized(peerMap) {
            val existing = peerMap[peer.nodeId]
            val merged = if (existing == null) {
                peer
            } else {
                existing.copy(
                    displayName = peer.displayName.ifBlank { existing.displayName },
                    transportType = if (existing.transportType == PeerTransportType.WIFI_DIRECT) {
                        PeerTransportType.HYBRID_MESH
                    } else {
                        peer.transportType
                    },
                    rssi = peer.rssi ?: existing.rssi,
                    tcpPort = peer.tcpPort,
                    lastSeenMs = System.currentTimeMillis()
                )
            }
            peerMap[peer.nodeId] = merged
            _peers.value = peerMap.values.sortedByDescending { it.lastSeenMs }
        }
    }

    private fun mergeWifiDirectPeers(p2pPeers: List<MeshPeer>) {
        synchronized(peerMap) {
            for (peer in p2pPeers) {
                val existing = peerMap[peer.nodeId]
                val merged = if (existing == null) {
                    peer
                } else {
                    existing.copy(
                        displayName = peer.displayName,
                        deviceAddress = peer.deviceAddress,
                        transportType = if (existing.transportType == PeerTransportType.BLE_BEACON) {
                            PeerTransportType.HYBRID_MESH
                        } else {
                            PeerTransportType.WIFI_DIRECT
                        },
                        isConnected = peer.isConnected,
                        isGroupOwner = peer.isGroupOwner,
                        tcpPort = peer.tcpPort,
                        lastSeenMs = System.currentTimeMillis()
                    )
                }
                peerMap[peer.nodeId] = merged
            }
            _peers.value = peerMap.values.sortedByDescending { it.lastSeenMs }
        }
    }

    private fun updatePeerHostFromSocket(nodeId: String, displayName: String, ip: String) {
        synchronized(peerMap) {
            val existing = peerMap[nodeId]
            peerMap[nodeId] = (existing ?: MeshPeer(
                nodeId = nodeId,
                displayName = displayName,
                deviceAddress = ip,
                transportType = PeerTransportType.WIFI_DIRECT
            )).copy(
                displayName = displayName,
                isConnected = true,
                hostAddress = ip,
                lastSeenMs = System.currentTimeMillis()
            )
            _peers.value = peerMap.values.sortedByDescending { it.lastSeenMs }
        }
    }

    fun addLog(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val entry = MeshLogEntry(
            id = logSeq.getAndIncrement(),
            timestampMs = System.currentTimeMillis(),
            tag = tag,
            message = message,
            level = level
        )
        _logs.update { current ->
            (listOf(entry) + current).take(60)
        }
    }

    fun release() {
        wifiDirectManager.release()
        bleHelper.stop()
        transferProtocol.stopServer()
    }
}
