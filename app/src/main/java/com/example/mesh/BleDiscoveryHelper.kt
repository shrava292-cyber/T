package com.example.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.UUID

class BleDiscoveryHelper(
    private val context: Context,
    private val onPeerDiscovered: (MeshPeer) -> Unit,
    private val onStateChanged: (BleRadioState) -> Unit,
    private val onLog: (tag: String, message: String, level: LogLevel) -> Unit
) {
    companion object {
        val MESH_SERVICE_UUID: UUID = UUID.fromString("0000A850-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var isScanning = false
    private var isAdvertising = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val record = result.scanRecord ?: return
            val serviceData = record.getServiceData(ParcelUuid(MESH_SERVICE_UUID))

            val (nodeId, port) = if (serviceData != null && serviceData.size >= 6) {
                val buf = ByteBuffer.wrap(serviceData)
                val tcpPort = buf.short.toInt() and 0xFFFF
                val idBytes = ByteArray(serviceData.size - 2)
                buf.get(idBytes)
                val parsedId = String(idBytes, Charsets.UTF_8).ifBlank { "NODE-${device.address.takeLast(5)}" }
                parsedId to tcpPort
            } else {
                "NODE-${device.address.takeLast(5)}" to P2pTransferProtocol.DEFAULT_PORT
            }

            @SuppressLint("MissingPermission")
            val deviceName = if (hasConnectPermission()) {
                runCatching { device.name }.getOrNull()
            } else null

            val displayName = record.deviceName ?: deviceName ?: nodeId
            onPeerDiscovered(
                MeshPeer(
                    nodeId = nodeId,
                    displayName = displayName,
                    deviceAddress = device.address,
                    transportType = PeerTransportType.BLE_BEACON,
                    rssi = result.rssi,
                    tcpPort = port,
                    lastSeenMs = System.currentTimeMillis()
                )
            )
        }

        override fun onScanFailed(errorCode: Int) {
            onLog("BLE", "BLE scan failed (code=$errorCode)", LogLevel.WARN)
            isScanning = false
            emitState()
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            emitState()
            onLog("BLE", "BLE Mesh beacon advertising started", LogLevel.SUCCESS)
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            emitState()
            onLog("BLE", "BLE advertising unavailable (code=$errorCode)", LogLevel.WARN)
        }
    }

    fun start(localNodeId: String, tcpPort: Int) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            onStateChanged(BleRadioState.UNAVAILABLE)
            onLog("BLE", "Bluetooth hardware unavailable on this device", LogLevel.INFO)
            return
        }
        if (!adapter.isEnabled) {
            onStateChanged(BleRadioState.DISABLED)
            onLog("BLE", "Bluetooth is disabled; enable Bluetooth for BLE beacons", LogLevel.WARN)
            return
        }

        startAdvertising(localNodeId, tcpPort)
        startScanning()
        emitState()
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(localNodeId: String, tcpPort: Int) {
        if (isAdvertising || !hasAdvertisePermission()) return
        val bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return
        advertiser = bleAdvertiser

        val shortId = localNodeId.take(10).toByteArray(Charsets.UTF_8)
        val serviceDataBytes = ByteBuffer.allocate(2 + shortId.size)
            .putShort(tcpPort.toShort())
            .put(shortId)
            .array()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .addServiceData(ParcelUuid(MESH_SERVICE_UUID), serviceDataBytes)
            .build()

        runCatching {
            bleAdvertiser.startAdvertising(settings, data, advertiseCallback)
        }.onFailure {
            onLog("BLE", "Could not start BLE advertiser: ${it.message}", LogLevel.WARN)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (isScanning || !hasScanPermission()) return
        val bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        scanner = bleScanner

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        runCatching {
            bleScanner.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            onLog("BLE", "BLE Mesh beacon scan active", LogLevel.INFO)
        }.onFailure {
            onLog("BLE", "Could not start BLE scan: ${it.message}", LogLevel.WARN)
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (isScanning && hasScanPermission()) {
            runCatching { scanner?.stopScan(scanCallback) }
        }
        if (isAdvertising && hasAdvertisePermission()) {
            runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        }
        isScanning = false
        isAdvertising = false
        emitState()
    }

    private fun emitState() {
        val adapter = bluetoothAdapter
        val state = when {
            adapter == null -> BleRadioState.UNAVAILABLE
            !adapter.isEnabled -> BleRadioState.DISABLED
            isScanning || isAdvertising -> BleRadioState.ACTIVE_SCAN_AND_ADVERTISE
            else -> BleRadioState.IDLE
        }
        onStateChanged(state)
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
    }
}
