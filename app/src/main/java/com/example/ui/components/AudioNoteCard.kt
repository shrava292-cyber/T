package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.PlaybackState
import com.example.data.local.AudioNoteEntity
import com.example.data.local.NoteDirection
import com.example.data.local.TransferStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AudioNoteCard(
    note: AudioNoteEntity,
    playbackState: PlaybackState,
    onPlayToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onRelay: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isThisActive = playbackState.activeNoteId == note.id
    val isPlaying = isThisActive && playbackState.isPlaying
    val progress = if (isThisActive) playbackState.progress else 0f
    val currentTimeText = if (isThisActive && playbackState.currentPositionMs > 0L) {
        "${formatDurationMs(playbackState.currentPositionMs)} / ${formatDurationMs(note.durationMs)}"
    } else {
        formatDurationMs(note.durationMs)
    }

    var menuExpanded by remember { mutableStateOf(false) }

    val borderColor = when {
        isPlaying -> MaterialTheme.colorScheme.primary
        !note.isRead -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isPlaying || !note.isRead) 1.2.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .testTag("audio_note_card_${note.id}"),
        shape = MaterialTheme.shapes.medium,
        color = if (!note.isRead) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row: Direction badge, Sender, Timestamp, Unread indicator & Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    DirectionIconBadge(direction = note.direction)

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!note.isRead) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary)
                                )
                            }
                            Text(
                                text = when (note.direction) {
                                    NoteDirection.OUTGOING -> {
                                        if (note.recipientName != null) "To ${note.recipientName}"
                                        else "Broadcast (${note.senderName})"
                                    }
                                    NoteDirection.INCOMING, NoteDirection.RELAYED -> note.senderName
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${note.senderId} · ${formatTimestamp(note.timestampMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TransferStatusBadge(
                        status = note.transferStatus,
                        hopCount = note.hopCount,
                        maxHops = note.maxHops
                    )

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("note_menu_button_${note.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Note options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Relay to Mesh Peers") },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRelay()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (note.isRead) "Mark as Unread" else "Mark as Read") },
                                leadingIcon = {
                                    Icon(
                                        if (note.isRead) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleRead()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Note") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            // Playback controls + Interactive Waveform
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledIconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("play_note_button_${note.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlaying) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (isPlaying) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause audio note" else "Play audio note"
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    SeekablePlaybackWaveform(
                        amplitudes = note.waveformList(),
                        progress = progress,
                        isPlaying = isPlaying,
                        onSeek = onSeek,
                        playedColor = if (isPlaying) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentTimeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPlaying) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = "${formatFileSize(note.fileSizeBytes)} · AAC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionIconBadge(direction: NoteDirection) {
    val (icon, bg, fg) = when (direction) {
        NoteDirection.OUTGOING -> Triple(
            Icons.AutoMirrored.Filled.CallMade,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        NoteDirection.INCOMING -> Triple(
            Icons.AutoMirrored.Filled.CallReceived,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        NoteDirection.RELAYED -> Triple(
            Icons.Default.Hub,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = direction.name,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TransferStatusBadge(
    status: TransferStatus,
    hopCount: Int,
    maxHops: Int
) {
    val (label, container, content) = when (status) {
        TransferStatus.DELIVERED -> Triple(
            "SENT",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        TransferStatus.SENDING -> Triple(
            "TX...",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        TransferStatus.QUEUED, TransferStatus.LOCAL_ONLY -> Triple(
            "LOCAL",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        TransferStatus.FAILED -> Triple(
            "RETRY",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        TransferStatus.RECEIVED -> Triple(
            if (hopCount > 1) "HOP $hopCount/$maxHops" else "DIRECT",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == TransferStatus.DELIVERED) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = content
            )
        }
    }
}

private fun formatTimestamp(timestampMs: Long): String {
    val formatter = SimpleDateFormat("HH:mm · MMM d", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}
