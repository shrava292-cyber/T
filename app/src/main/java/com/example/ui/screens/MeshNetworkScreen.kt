package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.mesh.LogLevel
import com.example.mesh.MeshLogEntry
import com.example.mesh.MeshPeer
import com.example.mesh.PeerTransportType
import com.example.ui.MeshUiState
import com.example.ui.components.MeshRadarBanner
import com.example.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MeshNetworkScreen(
    uiState: MeshUiState,
    onToggleDiscovery: () -> Unit,
    onRefreshSweep: () -> Unit,
    onConnectPeer: (MeshPeer) -> Unit,
    onTargetPeer: (MeshPeer) -> Unit,
    onUpdateCallsign: (String) -> Unit,
    onToggleAutoRelay: (Boolean) -> Unit,
    onMaxHopsChange: (Int) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var callsignDraft by remember(uiState.settings.callsign) {
        mutableStateOf(uiState.settings.callsign)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "mesh_header") {
                Column {
                    Text(
                        text = "Mesh Peers & Protocol",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Local Node: ${uiState.settings.localNodeId} · Port ${uiState.serviceStatus.listeningPort}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item(key = "radar_banner") {
                MeshRadarBanner(
                    serviceStatus = uiState.serviceStatus,
                    peerCount = uiState.peers.size,
                    connectedPeerCount = uiState.peers.count { it.isConnected || it.hostAddress != null },
                    onToggleDiscovery = onToggleDiscovery,
                    onRefreshSweep = onRefreshSweep
                )
            }

            item(key = "peers_section_title") {
                Text(
                    text = "Nearby Discovered Devices (${uiState.peers.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (uiState.peers.isEmpty()) {
                item(key = "empty_peers") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_peers_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.empty_peers_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(items = uiState.peers, key = { it.nodeId }) { peer ->
                    PeerNodeCard(
                        peer = peer,
                        isTargeted = uiState.selectedTargetPeerId == peer.nodeId,
                        onConnect = { onConnectPeer(peer) },
                        onTarget = { onTargetPeer(peer) }
                    )
                }
            }

            item(key = "mesh_settings") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Node & Relay Configuration",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = callsignDraft,
                                onValueChange = { callsignDraft = it },
                                label = { Text("Node Callsign") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("callsign_input")
                            )
                            FilledTonalButton(
                                onClick = { onUpdateCallsign(callsignDraft) },
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("save_callsign_button")
                            ) {
                                Text("Save")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Multi-Hop Auto-Relay",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Forward incoming broadcast notes to other nearby peers automatically",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.settings.autoRelayEnabled,
                                onCheckedChange = onToggleAutoRelay,
                                modifier = Modifier.testTag("auto_relay_switch")
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Max Relay Hop TTL",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${uiState.settings.maxHopTtl} hops",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = uiState.settings.maxHopTtl.toFloat(),
                                onValueChange = { onMaxHopsChange(it.roundToInt()) },
                                valueRange = 1f..5f,
                                steps = 3,
                                modifier = Modifier.testTag("max_hops_slider")
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Appearance Theme",
                                style = MaterialTheme.typography.titleSmall
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                ThemeMode.entries.forEachIndexed { idx, mode ->
                                    SegmentedButton(
                                        selected = uiState.settings.themeMode == mode,
                                        onClick = { onThemeModeChange(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = idx,
                                            count = ThemeMode.entries.size
                                        ),
                                        modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                                    ) {
                                        Text(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> "Auto"
                                                ThemeMode.DARK -> "Dark"
                                                ThemeMode.LIGHT -> "Light"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "protocol_log_header") {
                Text(
                    text = "Live P2P Protocol Console",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item(key = "protocol_logs") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (uiState.logs.isEmpty()) {
                            Text(
                                text = "Awaiting radio events...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.logs.take(16).forEach { entry ->
                                ProtocolLogRow(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerNodeCard(
    peer: MeshPeer,
    isTargeted: Boolean,
    onConnect: () -> Unit,
    onTarget: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (peer.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
            .testTag("peer_card_${peer.nodeId}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (peer.isConnected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (peer.transportType) {
                        PeerTransportType.WIFI_DIRECT -> Icons.Default.Wifi
                        PeerTransportType.BLE_BEACON -> Icons.Default.Bluetooth
                        PeerTransportType.HYBRID_MESH -> Icons.Default.Hub
                    },
                    contentDescription = peer.transportType.name,
                    tint = if (peer.isConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${peer.nodeId} · ${peer.signalStrengthLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onTarget,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(if (isTargeted) "Targeted" else "Target")
                }

                if (!peer.isConnected) {
                    Button(
                        onClick = onConnect,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Link")
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Connected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtocolLogRow(entry: MeshLogEntry) {
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampMs))
    val tagColor = when (entry.level) {
        LogLevel.SUCCESS -> MaterialTheme.colorScheme.primary
        LogLevel.WARN -> MaterialTheme.colorScheme.secondary
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$timeStr [${entry.tag}]",
            style = MaterialTheme.typography.labelSmall,
            color = tagColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
