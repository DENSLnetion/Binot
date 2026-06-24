package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit
) {
    val trashedNotes by viewModel.trashedNotes.collectAsState()
    var selectedNotes by remember { mutableStateOf(setOf<Int>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Kalkulasi margin aman untuk notch/cutout supaya konsisten dengan screen lain
    val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            if (selectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth() 
                ) {
                    TopAppBar(
                        // Terapkan batas aman notch di sini
                        windowInsets = WindowInsets(top = safeTopMargin),
                        title = { Text("${selectedNotes.size} Selected") },
                        navigationIcon = {
                            IconButton(onClick = { selectionMode = false; selectedNotes = emptySet() }) { Icon(Icons.Default.Close, "Cancel") }
                        },
                        actions = {
                            IconButton(onClick = { 
                                viewModel.restoreMultipleFromTrash(selectedNotes)
                                selectionMode = false
                                selectedNotes = emptySet()
                            }) { Icon(Icons.Default.Restore, "Restore") }
                            IconButton(onClick = { showDeleteConfirmDialog = true }) { Icon(Icons.Default.DeleteForever, "Delete Permanently", tint = MaterialTheme.colorScheme.error) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            } else {
                TopAppBar(
                    // Terapkan batas aman notch di sini juga
                    windowInsets = WindowInsets(top = safeTopMargin),
                    title = { Text("Trash") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        if (trashedNotes.isNotEmpty()) {
                            TextButton(onClick = { showEmptyTrashDialog = true }) {
                                Text("Empty Trash", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                )
            }
        }
    ) { innerPadding ->
        if (trashedNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Trash is empty.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                items(trashedNotes, key = { it.id }) { note ->
                    val isSelected = selectedNotes.contains(note.id)
                    TrashedNoteCard(
                        note = note,
                        isSelected = isSelected,
                        onLongClick = { if (!selectionMode) { selectionMode = true; selectedNotes = setOf(note.id) } },
                        onClick = { 
                            if (selectionMode) { 
                                selectedNotes = if (isSelected) selectedNotes - note.id else selectedNotes + note.id 
                                if (selectedNotes.isEmpty()) selectionMode = false 
                            } else {
                                selectionMode = true
                                selectedNotes = setOf(note.id)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text("Empty Trash?") },
            text = { Text("All notes in the trash will be permanently deleted. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyTrashDialog = false
                }) { Text("Empty", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showEmptyTrashDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Permanently?") },
            text = { Text("Selected notes will be permanently deleted. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePermanentlyMultiple(selectedNotes)
                    showDeleteConfirmDialog = false
                    selectionMode = false
                    selectedNotes = emptySet()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashedNoteCard(
    note: NoteEntity,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val minHeight = remember(note.id) { kotlin.random.Random(note.id).nextInt(140, 221).dp }
    val formatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val displayText = if (!note.summary.isNullOrEmpty()) note.summary else if (note.rawText.isNotBlank()) note.rawText else "Empty Note"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            Text(displayText, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.weight(1f))
            Text(formatter.format(Date(note.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp))
        }
    }
}
