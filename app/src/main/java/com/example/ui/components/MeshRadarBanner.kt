package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.mesh.BleRadioState
import com.example.mesh.MeshServiceStatus
import com.example.mesh.WifiDirectRadioState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeshRadarBanner(
    serviceStatus: MeshServiceStatus,
    peerCount: Int,
    connectedPeerCount: Int,
    onToggleDiscovery: () -> Unit,
    onRefreshSweep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = serviceStatus.isServiceRunning
    val borderColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        },
        label = "radarBorder"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle atmospheric mesh radar artwork backdrop
            Image(
                painter = painterResource(id = R.drawable.img_mesh_radar),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(if (isActive) 0.22f else 0.10f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CellTower,
                                contentDescription = "Mesh radio status",
                                tint = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                )
                                Text(
                                    text = if (isActive) "OFFLINE MESH ONLINE" else "MESH RADIO STANDBY",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "$peerCount nearby · $connectedPeerCount P2P linked",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = onRefreshSweep,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("refresh_mesh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh mesh discovery"
                            )
                        }
                        Switch(
                            checked = isActive,
                            onCheckedChange = { onToggleDiscovery() },
                            modifier = Modifier.testTag("toggle_discovery_switch"),
                            thumbContent = {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RadioStatusPill(
                        icon = Icons.Default.WifiTethering,
                        label = when (serviceStatus.wifiDirectState) {
                            WifiDirectRadioState.CONNECTED_GO -> "Wi-Fi P2P · Group Owner"
                            WifiDirectRadioState.CONNECTED_CLIENT -> "Wi-Fi P2P · Client"
                            WifiDirectRadioState.DISCOVERING -> "Wi-Fi P2P · Scanning"
                            WifiDirectRadioState.DISABLED -> "Wi-Fi P2P · Off"
                            WifiDirectRadioState.UNAVAILABLE -> "Wi-Fi P2P · N/A"
                            WifiDirectRadioState.IDLE -> "Wi-Fi P2P · Ready"
                        },
                        highlighted = serviceStatus.wifiDirectState == WifiDirectRadioState.CONNECTED_GO ||
                            serviceStatus.wifiDirectState == WifiDirectRadioState.CONNECTED_CLIENT ||
                            serviceStatus.wifiDirectState == WifiDirectRadioState.DISCOVERING
                    )

                    RadioStatusPill(
                        icon = Icons.Default.BluetoothSearching,
                        label = when (serviceStatus.bleState) {
                            BleRadioState.ACTIVE_SCAN_AND_ADVERTISE -> "BLE · Beacon Active"
                            BleRadioState.DISABLED -> "BLE · Off"
                            BleRadioState.UNAVAILABLE -> "BLE · N/A"
                            BleRadioState.IDLE -> "BLE · Ready"
                        },
                        highlighted = serviceStatus.bleState == BleRadioState.ACTIVE_SCAN_AND_ADVERTISE
                    )

                    RadioStatusPill(
                        icon = Icons.Default.Lock,
                        label = "AES-256-GCM · :${serviceStatus.listeningPort}",
                        highlighted = isActive
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioStatusPill(
    icon: ImageVector,
    label: String,
    highlighted: Boolean
) {
    val bgColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fgColor = if (highlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fgColor
            )
        }
    }
}
