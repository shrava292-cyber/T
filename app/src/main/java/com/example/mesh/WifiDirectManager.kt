package com.example.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat

class WifiDirectManager(
    private val context: Context,
    private val onPeersUpdated: (List<MeshPeer>) -> Unit,
    private val onGroupConnectionReady: (info: WifiP2pInfo) -> Unit,
    private val onGroupDisconnected: () -> Unit,
    private val onStateChanged: (WifiDirectRadioState) -> Unit,
    private val onLog: (tag: String, message: String, level: LogLevel) -> Unit
) {
    companion object {
        private const val SERVICE_INSTANCE = "MeshAudioNote"
        private const val SERVICE_TYPE = "_meshaudio._tcp"
    }

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var localServiceInfo: WifiP2pDnsSdServiceInfo? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null

    private val discoveredServicesMetadata = mutableMapOf<String, Map<String, String>>()

    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val mapped = peerList.deviceList.map { device ->
            val txt = discoveredServicesMetadata[device.deviceAddress].orEmpty()
            val nodeId = txt["nodeId"] ?: "P2P-${device.deviceAddress.takeLast(5)}"
            val callsign = txt["callsign"] ?: device.deviceName.ifBlank { nodeId }
            val port = txt["port"]?.toIntOrNull() ?: P2pTransferProtocol.DEFAULT_PORT
            MeshPeer(
                nodeId = nodeId,
                displayName = callsign,
                deviceAddress = device.deviceAddress,
                transportType = PeerTransportType.WIFI_DIRECT,
                isConnected = device.status == WifiP2pDevice.CONNECTED,
                isGroupOwner = device.isGroupOwner,
                tcpPort = port,
                lastSeenMs = System.currentTimeMillis()
            )
        }
        onPeersUpdated(mapped)
    }

    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        if (info.groupFormed) {
            val state = if (info.isGroupOwner) {
                WifiDirectRadioState.CONNECTED_GO
            } else {
                WifiDirectRadioState.CONNECTED_CLIENT
            }
            onStateChanged(state)
            onLog(
                "P2P",
                "Wi-Fi Direct group formed (GO=${info.isGroupOwner}, ip=${info.groupOwnerAddress?.hostAddress})",
                LogLevel.SUCCESS
            )
            onGroupConnectionReady(info)
        }
    }

    private val p2pReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        onStateChanged(WifiDirectRadioState.IDLE)
                    } else {
                        onStateChanged(WifiDirectRadioState.DISABLED)
                        onLog("P2P", "Wi-Fi Direct radio is disabled", LogLevel.WARN)
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (hasWifiDirectPermission()) {
                        manager?.requestPeers(channel, peerListListener)
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        manager?.requestConnectionInfo(channel, connectionInfoListener)
                    } else {
                        onGroupDisconnected()
                        onStateChanged(WifiDirectRadioState.DISCOVERING)
                    }
                }
            }
        }
    }

    fun initialize(): Boolean {
        val mgr = manager
        if (mgr == null) {
            onStateChanged(WifiDirectRadioState.UNAVAILABLE)
            onLog("P2P", "Wi-Fi Direct unavailable on this device", LogLevel.INFO)
            return false
        }
        if (channel == null) {
            channel = mgr.initialize(context, Looper.getMainLooper()) {
                onLog("P2P", "Wi-Fi Direct channel disconnected", LogLevel.WARN)
            }
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(context, p2pReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun startDiscoveryAndAdvertise(localNodeId: String, callsign: String, tcpPort: Int) {
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!hasWifiDirectPermission()) {
            onLog("P2P", "Missing NEARBY_WIFI_DEVICES permission for Wi-Fi Direct", LogLevel.WARN)
            return
        }

        // Register DNS-SD local service for app-specific peer discovery
        val record = mapOf(
            "nodeId" to localNodeId,
            "callsign" to callsign,
            "port" to tcpPort.toString()
        )
        localServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, SERVICE_TYPE, record)
        mgr.clearLocalServices(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                localServiceInfo?.let { info ->
                    mgr.addLocalService(ch, info, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            onLog("P2P", "DNS-SD Mesh service advertised ($callsign :$tcpPort)", LogLevel.INFO)
                        }
                        override fun onFailure(reason: Int) {
                            onLog("P2P", "addLocalService failed (reason=$reason)", LogLevel.WARN)
                        }
                    })
                }
            }
            override fun onFailure(reason: Int) {}
        })

        // Listen for DNS-SD TXT records from nearby MeshAudio nodes
        mgr.setDnsSdResponseListeners(
            ch,
            { _, _, _ -> },
            { _, txtRecordMap, srcDevice ->
                discoveredServicesMetadata[srcDevice.deviceAddress] = txtRecordMap
                if (hasWifiDirectPermission()) {
                    mgr.requestPeers(ch, peerListListener)
                }
            }
        )

        val request = WifiP2pDnsSdServiceRequest.newInstance()
        serviceRequest = request
        mgr.clearServiceRequests(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                mgr.addServiceRequest(ch, request, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        mgr.discoverServices(ch, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                onStateChanged(WifiDirectRadioState.DISCOVERING)
                            }
                            override fun onFailure(reason: Int) {}
                        })
                    }
                    override fun onFailure(reason: Int) {}
                })
            }
            override fun onFailure(reason: Int) {}
        })

        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStateChanged(WifiDirectRadioState.DISCOVERING)
                onLog("P2P", "Wi-Fi Direct peer discovery active", LogLevel.INFO)
            }
            override fun onFailure(reason: Int) {
                onLog("P2P", "Wi-Fi Direct discoverPeers failed (code=$reason)", LogLevel.WARN)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String, onInitiated: (Boolean) -> Unit = {}) {
        val mgr = manager ?: return onInitiated(false)
        val ch = channel ?: return onInitiated(false)
        if (!hasWifiDirectPermission()) return onInitiated(false)

        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            wps.setup = WpsInfo.PBC
        }
        mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onLog("P2P", "Initiated Wi-Fi Direct handshake with $deviceAddress", LogLevel.INFO)
                onInitiated(true)
            }
            override fun onFailure(reason: Int) {
                onLog("P2P", "Wi-Fi Direct connect failed (reason=$reason)", LogLevel.WARN)
                onInitiated(false)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        val mgr = manager ?: return
        val ch = channel ?: return
        runCatching { mgr.stopPeerDiscovery(ch, null) }
        runCatching { mgr.clearLocalServices(ch, null) }
        runCatching { mgr.clearServiceRequests(ch, null) }
        onStateChanged(WifiDirectRadioState.IDLE)
    }

    fun release() {
        stopDiscovery()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(p2pReceiver) }
            receiverRegistered = false
        }
    }

    private fun hasWifiDirectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
