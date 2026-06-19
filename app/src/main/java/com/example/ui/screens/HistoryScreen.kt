package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
    val context = LocalContext.current
    val notes by viewModel.filteredNotes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var selectionMode by remember { mutableStateOf(false) }
    var selectedNotes by remember { mutableStateOf(setOf<Int>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // State untuk Animasi Morphing Search Bar
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val isAllPinned = selectedNotes.isNotEmpty() && selectedNotes.all { id -> 
        notes.find { it.id == id }?.isPinned == true 
    }

    val pinnedNotes = notes.filter { it.isPinned }
    val unpinnedNotes = notes.filter { !it.isPinned }

    val gridState = rememberLazyStaggeredGridState()
    val isFabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            viewModel.importAudio(context, it) { newNoteId ->
                onNoteClick(newNoteId)
            }
        }
    }

    // PERBAIKAN FATAL: Memasukkan TopAppBar & Morphing Search Bar murni ke dalam slot `topBar` Scaffold
    // Ini ngejamin warnanya "tumpah" nyundul area baterai dan jam HP secara native!
    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedNotes.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { 
                            selectionMode = false; selectedNotes = emptySet() 
                        }) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                    },
                    actions = {
                        IconButton(onClick = { 
                            viewModel.togglePinMultiple(selectedNotes, !isAllPinned)
                            selectionMode = false; selectedNotes = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.PushPin, 
                                contentDescription = "Pin",
                                tint = if (isAllPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { 
                            viewModel.cloneMultiple(selectedNotes)
                            selectionMode = false; selectedNotes = emptySet()
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = "Clone") }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } else {
                MorphingSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    isFocused = isSearchFocused,
                    onFocusChange = { isSearchFocused = it },
                    onClearFocus = { focusManager.clearFocus() }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { importLauncher.launch("audio/*") },
                    expanded = isFabExpanded,
                    icon = { Icon(Icons.Default.Audiotrack, contentDescription = "Import Audio") },
                    text = { Text("Import Audio") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No results found." else "No notes yet.\nStart recording or import audio!",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    if (pinnedNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Text(
                                text = "Pinned",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(pinnedNotes, key = { it.id }) { note ->
                            val isSelected = selectedNotes.contains(note.id)
                            with(sharedTransitionScope) {
                                NoteCard(
                                    note = note, isSelected = isSelected,
                                    modifier = Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "note-${note.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    ),
                                    onLongClick = { if (!selectionMode) { selectionMode = true; selectedNotes = setOf(note.id) } },
                                    onClick = {
                                        if (selectionMode) {
                                            selectedNotes = if (isSelected) selectedNotes - note.id else selectedNotes + note.id
                                            if (selectedNotes.isEmpty()) selectionMode = false
                                        } else { onNoteClick(note.id) }
                                    }
                                )
                            }
                        }
                    }

                    if (unpinnedNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Text(
                                text = "Collection",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(unpinnedNotes, key = { it.id }) { note ->
                            val isSelected = selectedNotes.contains(note.id)
                            with(sharedTransitionScope) {
                                NoteCard(
                                    note = note, isSelected = isSelected,
                                    modifier = Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "note-${note.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    ),
                                    onLongClick = { if (!selectionMode) { selectionMode = true; selectedNotes = setOf(note.id) } },
                                    onClick = {
                                        if (selectionMode) {
                                            selectedNotes = if (isSelected) selectedNotes - note.id else selectedNotes + note.id
                                            if (selectedNotes.isEmpty()) selectionMode = false
                                        } else { onNoteClick(note.id) }
                                    }
                                )
                            }
                        }
                    }
                    item(span = StaggeredGridItemSpan.FullLine) { Spacer(modifier = Modifier.height(100.dp)) }
                }
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
                    showDeleteDialog = false; selectionMode = false; selectedNotes = emptySet()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

// KOMPONEN MUTAKHIR: Search Bar Morphing Material 3
@Composable
fun MorphingSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onClearFocus: () -> Unit
) {
    val topInsets = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    
    val cornerRadius by animateDpAsState(targetValue = if (isFocused) 0.dp else 50.dp, animationSpec = spring(), label = "corner")
    val horizontalPadding by animateDpAsState(targetValue = if (isFocused) 0.dp else 16.dp, animationSpec = spring(), label = "hPad")
    val topMargin by animateDpAsState(targetValue = if (isFocused) 0.dp else 16.dp, animationSpec = spring(), label = "tMargin")
    
    // Pas fokus, padding atasnya melar biar teks kaga ketutupan poni HP
    val contentTopPadding by animateDpAsState(targetValue = if (isFocused) topInsets + 16.dp else 16.dp, animationSpec = spring(), label = "cTopPad")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = topMargin, bottom = 8.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = contentTopPadding, bottom = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search notes...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onFocusChange(it.isFocused) }
                )
            }
            if (isFocused || query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable {
                        onQueryChange("")
                        onClearFocus()
                    }
                )
            }
        }
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
    val minHeight = remember(note.id) { (140..220).random().dp }
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    
    val displayText = if (!note.summary.isNullOrEmpty()) note.summary 
                      else if (note.rawText.isNotBlank()) note.rawText 
                      else "⏳ Waiting for AI transcription..."

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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                text = displayText!!,
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
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

