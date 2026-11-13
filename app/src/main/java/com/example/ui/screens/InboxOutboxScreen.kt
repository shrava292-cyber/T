package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AudioNoteEntity
import com.example.ui.MeshUiState
import com.example.ui.NoteFilter
import com.example.ui.components.AudioNoteCard

@Composable
fun InboxOutboxScreen(
    uiState: MeshUiState,
    onSelectFilter: (NoteFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onPlayToggle: (AudioNoteEntity) -> Unit,
    onSeekNote: (AudioNoteEntity, Float) -> Unit,
    onRelayNote: (AudioNoteEntity) -> Unit,
    onToggleRead: (AudioNoteEntity) -> Unit,
    onDeleteNote: (AudioNoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val notes = uiState.filteredNotes

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Inbox & Outbox",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${uiState.notes.size} stored offline · ${uiState.unreadCount} unread",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.unreadCount > 0) {
                    TextButton(
                        onClick = onMarkAllRead,
                        modifier = Modifier.testTag("mark_all_read_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(" Read All")
                    }
                }
            }

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notes_search_input"),
                placeholder = { Text("Search by peer callsign or node ID") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search notes")
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NoteFilter.entries) { filter ->
                    val count = when (filter) {
                        NoteFilter.ALL -> uiState.notes.size
                        NoteFilter.UNREAD -> uiState.unreadCount
                        NoteFilter.INBOX -> uiState.notes.count { it.direction.name != "OUTGOING" }
                        NoteFilter.OUTBOX -> uiState.notes.count { it.direction.name == "OUTGOING" }
                    }
                    FilterChip(
                        selected = uiState.selectedFilter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = { Text("${filter.label} ($count)") },
                        modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                    )
                }
            }

            if (notes.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_mesh_radar),
                            contentDescription = "Empty inbox mesh illustration",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )
                        Text(
                            text = if (uiState.notes.isEmpty()) stringResource(R.string.empty_notes_title) else "No Matching Notes",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (uiState.notes.isEmpty()) {
                                stringResource(R.string.empty_notes_subtitle)
                            } else {
                                "Try clearing your search query or switching filters."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = notes, key = { it.id }) { note ->
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
        }
    }
}
