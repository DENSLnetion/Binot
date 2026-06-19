package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.NoteEntity
import com.example.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onNoteClick: (Int) -> Unit
) {
    val notes by viewModel.filteredNotes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var selectionMode by remember { mutableStateOf(false) }
    var selectedNotes by remember { mutableStateOf(setOf<Int>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isAllPinned = selectedNotes.isNotEmpty() && selectedNotes.all { id -> 
        notes.find { it.id == id }?.isPinned == true 
    }

    val pinnedNotes = notes.filter { it.isPinned }
    val unpinnedNotes = notes.filter { !it.isPinned }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            TopAppBar(
                title = { Text("${selectedNotes.size} Selected") },
                navigationIcon = {
                    IconButton(onClick = { 
                        selectionMode = false
                        selectedNotes = emptySet()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        viewModel.togglePinMultiple(selectedNotes, !isAllPinned)
                        selectionMode = false
                        selectedNotes = emptySet()
                    }) {
                        Icon(
                            imageVector = Icons.Default.PushPin, 
                            contentDescription = "Pin",
                            tint = if (isAllPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { 
                        viewModel.cloneMultiple(selectedNotes)
                        selectionMode = false
                        selectedNotes = emptySet()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Clone")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search notes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                shape = RoundedCornerShape(100),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        
        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No results found." else "No notes yet.\nStart recording!",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                if (pinnedNotes.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Text(
                            text = "Pinned",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(pinnedNotes, key = { it.id }) { note ->
                        val isSelected = selectedNotes.contains(note.id)
                        with(sharedTransitionScope) {
                            NoteCard(
                                note = note,
                                isSelected = isSelected,
                                modifier = Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "note-${note.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                ),
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedNotes = setOf(note.id)
                                    }
                                },
                                onClick = {
                                    if (selectionMode) {
                                        selectedNotes = if (isSelected) selectedNotes - note.id else selectedNotes + note.id
                                        if (selectedNotes.isEmpty()) selectionMode = false
                                    } else {
                                        onNoteClick(note.id)
                                    }
                                }
                            )
                        }
                    }
                }

                if (unpinnedNotes.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Text(
                            text = "Collection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(unpinnedNotes, key = { it.id }) { note ->
                        val isSelected = selectedNotes.contains(note.id)
                        with(sharedTransitionScope) {
                            NoteCard(
                                note = note,
                                isSelected = isSelected,
                                modifier = Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "note-${note.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                ),
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedNotes = setOf(note.id)
                                    }
                                },
                                onClick = {
                                    if (selectionMode) {
                                        selectedNotes = if (isSelected) selectedNotes - note.id else selectedNotes + note.id
                                        if (selectedNotes.isEmpty()) selectionMode = false
                                    } else {
                                        onNoteClick(note.id)
                                    }
                                }
                            )
                        }
                    }
                }
                
                item(span = StaggeredGridItemSpan.FullLine) { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Notes") },
            text = { Text("Are you sure you want to delete ${selectedNotes.size} notes?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMultiple(selectedNotes)
                    showDeleteDialog = false
                    selectionMode = false
                    selectedNotes = emptySet()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity, 
    isSelected: Boolean, 
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit, 
    onClick: () -> Unit
) {
    // PERBAIKAN: Ga usah dikunci rigid, biarin dia melar ke bawah minimal 120.dp
    // Jadi kalau rangkuman kepanjangan, tanggalnya ga bakal lenyap kepotong!
    val minHeight = remember(note.id) { (140..220).random().dp }
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                             else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = note.summary ?: note.rawText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatter.format(Date(note.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 8.dp) // Jarak aman buat tanggal
            )
        }
    }
}

