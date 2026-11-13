package com.example.mesh

enum class PeerTransportType {
    WIFI_DIRECT,
    BLE_BEACON,
    HYBRID_MESH
}

enum class WifiDirectRadioState {
    UNAVAILABLE,
    DISABLED,
    IDLE,
    DISCOVERING,
    CONNECTED_GO,
    CONNECTED_CLIENT
}

enum class BleRadioState {
    UNAVAILABLE,
    DISABLED,
    IDLE,
    ACTIVE_SCAN_AND_ADVERTISE
}

enum class TransferDirection {
    OUTBOUND,
    INBOUND
}

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN
}

data class MeshPeer(
    val nodeId: String,
    val displayName: String,
    val deviceAddress: String,
    val transportType: PeerTransportType,
    val rssi: Int? = null,
    val isConnected: Boolean = false,
    val isGroupOwner: Boolean = false,
    val hostAddress: String? = null,
    val tcpPort: Int = P2pTransferProtocol.DEFAULT_PORT,
    val lastSeenMs: Long = System.currentTimeMillis(),
    val hopDistance: Int = 1
) {
    fun signalStrengthLabel(): String {
        val r = rssi ?: return "P2P Direct"
        return when {
            r >= -60 -> "Strong (${r} dBm)"
            r >= -78 -> "Good (${r} dBm)"
            else -> "Weak (${r} dBm)"
        }
    }
}

data class TransferProgress(
    val noteId: String,
    val peerName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val chunksCompleted: Int,
    val totalChunks: Int,
    val direction: TransferDirection,
    val isFinished: Boolean = false,
    val error: String? = null
) {
    val fraction: Float
        get() = if (totalBytes > 0L) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class MeshLogEntry(
    val id: Long,
    val timestampMs: Long,
    val tag: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

data class MeshServiceStatus(
    val isServiceRunning: Boolean = false,
    val isDiscovering: Boolean = false,
    val wifiDirectState: WifiDirectRadioState = WifiDirectRadioState.IDLE,
    val bleState: BleRadioState = BleRadioState.IDLE,
    val localNodeId: String = "",
    val localCallsign: String = "",
    val listeningPort: Int = P2pTransferProtocol.DEFAULT_PORT,
    val groupOwnerIp: String? = null,
    val statusSummary: String = "Standby"
)
