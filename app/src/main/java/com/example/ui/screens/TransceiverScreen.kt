package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AudioNoteEntity
import com.example.mesh.MeshPeer
import com.example.mesh.TransferDirection
import com.example.mesh.TransferProgress
import com.example.ui.MeshUiState
import com.example.ui.components.AudioNoteCard
import com.example.ui.components.LiveRecordingWaveform
import com.example.ui.components.MeshRadarBanner
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatFileSize

@Composable
fun TransceiverScreen(
    uiState: MeshUiState,
    onRequestPermissions: () -> Unit,
    onToggleDiscovery: () -> Unit,
    onRefreshSweep: () -> Unit,
    onSelectTargetPeer: (String?) -> Unit,
    onStartHoldRecording: () -> Unit,
    onFinishHoldRecording: () -> Unit,
    onToggleHandsFreeRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayToggle: (AudioNoteEntity) -> Unit,
    onSeekNote: (AudioNoteEntity, Float) -> Unit,
    onRelayNote: (AudioNoteEntity) -> Unit,
    onToggleRead: (AudioNoteEntity) -> Unit,
    onDeleteNote: (AudioNoteEntity) -> Unit,
    onOpenAllNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val recentNotes = remember(uiState.notes) { uiState.notes.take(8) }
    val connectedPeersCount = remember(uiState.peers) { uiState.peers.count { it.isConnected || it.hostAddress != null } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!uiState.hasRequiredPermissions) {
                    item(key = "permission_banner") {
                        PermissionRequestCard(onRequestPermissions = onRequestPermissions)
                    }
                }

                item(key = "mesh_radar") {
                    MeshRadarBanner(
                        serviceStatus = uiState.serviceStatus,
                        peerCount = uiState.peers.size,
                        connectedPeerCount = connectedPeersCount,
                        onToggleDiscovery = onToggleDiscovery,
                        onRefreshSweep = onRefreshSweep
                    )
                }

                uiState.activeTransfer?.takeIf { !it.isFinished || it.error != null }?.let { transfer ->
                    item(key = "active_transfer") {
                        ActiveTransferStrip(transfer = transfer)
                    }
                }

                item(key = "recent_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Recent Audio Feed",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (uiState.unreadCount > 0) {
                                    "${uiState.unreadCount} unread voice note(s)"
                                } else {
                                    "Local Room Inbox & Outbox"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (uiState.notes.isNotEmpty()) {
                            TextButton(
                                onClick = onOpenAllNotes,
                                modifier = Modifier.testTag("view_all_notes_button")
                            ) {
                                Text("View All (${uiState.notes.size})")
                            }
                        }
                    }
                }

                if (recentNotes.isEmpty()) {
                    item(key = "empty_feed") {
                        EmptyAudioFeedCard()
                    }
                } else {
                    items(items = recentNotes, key = { it.id }) { note ->
                        AudioNoteCard(
                            note = note,
                            playbackState = uiState.playbackState,
                            onPlayToggle = { onPlayToggle(note) },
                            onSeek = { frac -> onSeekNote(note, frac) },
                            onRelay = { onRelayNote(note) },
                            onToggleRead = { onToggleRead(note) },
                            onDelete = { onDeleteNote(note) }
                        )
                    }
                }
            }

            // Docked Studio Recorder Deck
            StudioRecorderDeck(
                isRecording = uiState.recordingState.isRecording,
                isLocked = uiState.recordingState.isLocked,
                elapsedMs = uiState.recordingState.elapsedMs,
                amplitude = uiState.recordingState.currentAmplitude,
                liveWaveform = uiState.recordingState.liveWaveform,
                hasPermissions = uiState.hasRequiredPermissions,
                peers = uiState.peers,
                selectedTargetPeer = uiState.selectedTargetPeer,
                onSelectTargetPeer = onSelectTargetPeer,
                onRequestPermissions = onRequestPermissions,
                onHoldStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartHoldRecording()
                },
                onHoldRelease = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFinishHoldRecording()
                },
                onToggleHandsFree = onToggleHandsFreeRecording,
                onCancelRecording = onCancelRecording
            )
        }
    }
}

@Composable
private fun PermissionRequestCard(onRequestPermissions: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Radio & Mic Permissions Needed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Grant microphone, Nearby Wi-Fi, and BLE permissions for offline mesh voice notes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.testTag("grant_permissions_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Grant")
            }
        }
    }
}

@Composable
private fun ActiveTransferStrip(transfer: TransferProgress) {
    val isInbound = transfer.direction == TransferDirection.INBOUND
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isInbound) "RX from ${transfer.peerName}" else "TX to ${transfer.peerName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Chunk ${transfer.chunksCompleted}/${transfer.totalChunks} · ${formatFileSize(transfer.bytesTransferred)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            LinearProgressIndicator(
                progress = { transfer.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EmptyAudioFeedCard() {
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_mesh_radar),
                contentDescription = "Offline mesh spectrum illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            Text(
                text = stringResource(R.string.empty_notes_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.empty_notes_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StudioRecorderDeck(
    isRecording: Boolean,
    isLocked: Boolean,
    elapsedMs: Long,
    amplitude: Float,
    liveWaveform: List<Float>,
    hasPermissions: Boolean,
    peers: List<MeshPeer>,
    selectedTargetPeer: MeshPeer?,
    onSelectTargetPeer: (String?) -> Unit,
    onRequestPermissions: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldRelease: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onCancelRecording: () -> Unit
) {
    var peerMenuExpanded by remember { mutableStateOf(false) }

    val pulseScale by animateFloatAsState(
        targetValue = if (isRecording) 1.08f + (amplitude * 0.28f) else 1f,
        animationSpec = spring(stiffness = 450f, dampingRatio = 0.65f),
        label = "micPulseScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isRecording) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = MaterialTheme.shapes.extraLarge
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Target selector & Live timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    FilledTonalButton(
                        onClick = { peerMenuExpanded = true },
                        modifier = Modifier.testTag("target_peer_selector"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedTargetPeer?.displayName ?: stringResource(R.string.broadcast_all),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = peerMenuExpanded,
                        onDismissRequest = { peerMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Nearby Peers (Mesh Broadcast)") },
                            onClick = {
                                peerMenuExpanded = false
                                onSelectTargetPeer(null)
                            }
                        )
                        peers.forEach { peer ->
                            DropdownMenuItem(
                                text = { Text("${peer.displayName} (${peer.nodeId})") },
                                onClick = {
                                    peerMenuExpanded = false
                                    onSelectTargetPeer(peer.nodeId)
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                    }
                    Text(
                        text = if (isRecording) formatDurationMs(elapsedMs) else "AAC · 64kbps",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isRecording) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Live Waveform Display
            AnimatedVisibility(visible = isRecording) {
                LiveRecordingWaveform(
                    amplitudes = liveWaveform,
                    isRecording = isRecording,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            // Action Controls: Cancel (when locked) | Hold-to-Record PTT | Hands-free Lock/Send
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording) {
                    FilledTonalIconButton(
                        onClick = onCancelRecording,
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("cancel_recording_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel recording",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.size(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }

                // Primary Push-To-Talk / Hold-to-Record Orb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                if (isRecording) {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                }
                            )
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecording) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            .pointerInput(hasPermissions, isLocked) {
                                detectTapGestures(
                                    onPress = {
                                        if (!hasPermissions) {
                                            onRequestPermissions()
                                            return@detectTapGestures
                                        }
                                        if (isLocked) {
                                            // If already locked hands-free, tapping stops & broadcasts
                                            onToggleHandsFree()
                                            return@detectTapGestures
                                        }
                                        onHoldStart()
                                        tryAwaitRelease()
                                        if (!isLocked) {
                                            onHoldRelease()
                                        }
                                    }
                                )
                            }
                            .testTag("hold_to_record_button")
                    ) {
                        Icon(
                            imageVector = when {
                                isRecording && isLocked -> Icons.AutoMirrored.Filled.Send
                                isRecording -> Icons.Default.Stop
                                else -> Icons.Default.Mic
                            },
                            contentDescription = stringResource(R.string.hold_to_record),
                            tint = if (isRecording) {
                                MaterialTheme.colorScheme.onSecondary
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Hands-free tap-to-record / lock button
                FilledTonalIconButton(
                    onClick = {
                        if (!hasPermissions) {
                            onRequestPermissions()
                        } else {
                            onToggleHandsFree()
                        }
                    },
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("handsfree_record_button")
                ) {
                    Icon(
                        imageVector = if (isRecording && isLocked) Icons.AutoMirrored.Filled.Send else Icons.Default.Lock,
                        contentDescription = if (isRecording && isLocked) "Finish and broadcast" else "Hands-free record",
                        tint = if (isRecording && isLocked) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Text(
                text = when {
                    !hasPermissions -> stringResource(R.string.grant_permissions)
                    isRecording && isLocked -> stringResource(R.string.tap_to_stop)
                    isRecording -> stringResource(R.string.release_to_send)
                    else -> "Hold mic to record & broadcast · Tap lock for hands-free"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isRecording) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
