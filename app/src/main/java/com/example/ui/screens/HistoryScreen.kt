package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onNoteClick: (Int) -> Unit,
    onTrashClick: () -> Unit
) {
    val context = LocalContext.current
    val notes by viewModel.filteredNotes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val latestRelease by viewModel.latestRelease.collectAsState()
    
    val uniqueLabels by viewModel.uniqueLabels.collectAsState()
    val selectedLabels by viewModel.selectedLabels.collectAsState()
    val isMultiSelectLabelMode by viewModel.isMultiSelectLabelMode.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    var selectionMode by remember { mutableStateOf(false) }
    var selectedNotes by remember { mutableStateOf(setOf<Int>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNewLabelDialog by remember { mutableStateOf(false) }
    var newLabelInput by remember { mutableStateOf("") }

    // States untuk manajemen label
    var labelBeingManaged by remember { mutableStateOf<String?>(null) }
    var isRenamingLabelMode by remember { mutableStateOf(false) }
    var showDeleteLabelDialog by remember { mutableStateOf(false) }
    var renameLabelInput by remember { mutableStateOf("") }
    var showDeleteMultipleLabelsDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val isAllPinned = selectedNotes.isNotEmpty() && selectedNotes.all { id -> 
        notes.find { it.id == id }?.isPinned == true 
    }

    val pinnedNotes = notes.filter { it.isPinned }
    val unpinnedNotes = notes.filter { !it.isPinned }

    val gridState = rememberLazyStaggeredGridState()
    val isFabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

    val currentVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0" } 
        catch (e: Exception) { "1.0.0" }
    }

    val isTransitioning = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState

    LaunchedEffect(Unit) {
        viewModel.checkForAppUpdate(currentVersion)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importAudio(context, it) { newNoteId -> onNoteClick(newNoteId) } }
    }

    BackHandler(enabled = isSearchFocused || searchQuery.isNotEmpty() || selectionMode || drawerState.isOpen || (labelBeingManaged != null && isRenamingLabelMode)) {
        if (labelBeingManaged != null && isRenamingLabelMode) {
            isRenamingLabelMode = false // Batalin rename, kotak bakal nguncup lagi
        } else if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (selectionMode) {
            selectionMode = false; selectedNotes = emptySet()
        } else if (isSearchFocused) {
            focusManager.clearFocus() 
        } else {
            viewModel.updateSearchQuery("") 
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isTransitioning, 
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(24.dp))
                    Text("Sort By", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        data class SortOption(val icon: androidx.compose.ui.graphics.vector.ImageVector, val description: String)
                        val sortOptions = listOf(
                            SortOption(androidx.compose.material.icons.Icons.Default.AccessTime, "Terbaru"),
                            SortOption(androidx.compose.material.icons.Icons.Default.History, "Terlama"),
                            SortOption(androidx.compose.material.icons.Icons.AutoMirrored.Default.Sort, "A-Z")
                        )
                        sortOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = sortOptions.size),
                                onClick = { viewModel.setSortMode(index) },
                                selected = sortMode == index
                            ) { Icon(option.icon, contentDescription = option.description, modifier = Modifier.size(18.dp)) }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Labels",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (isMultiSelectLabelMode && selectedLabels.isNotEmpty()) {
                            IconButton(onClick = { showDeleteMultipleLabelsDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected Labels", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = { viewModel.setMultiSelectLabelMode(!isMultiSelectLabelMode) }) {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = "Toggle Multi-Select",
                                tint = if (isMultiSelectLabelMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    NavigationDrawerItem(
                        label = { Text("All Notes") },
                        selected = selectedLabels.isEmpty(),
                        onClick = {
                            viewModel.clearLabelFilter()
                            if (!isMultiSelectLabelMode) coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    uniqueLabels.forEach { label ->
                        val isLabelSelected = label in selectedLabels
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    if (isLabelSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = {
                                        viewModel.toggleLabelFilter(label)
                                        if (!isMultiSelectLabelMode) coroutineScope.launch { drawerState.close() }
                                    },
                                    onLongClick = {
                                        labelBeingManaged = label
                                        isRenamingLabelMode = false
                                        renameLabelInput = label
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (isMultiSelectLabelMode) {
                                Checkbox(
                                    checked = isLabelSelected,
                                    onCheckedChange = { viewModel.toggleLabelFilter(label) }
                                )
                            } else {
                                Icon(
                                    Icons.Default.Label,
                                    contentDescription = null,
                                    tint = if (isLabelSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                label,
                                color = if (isLabelSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    NavigationDrawerItem(
                        label = { Text("Create New Label") },
                        icon = { Icon(Icons.Default.Add, null) },
                        selected = false,
                        onClick = { showNewLabelDialog = true; coroutineScope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    NavigationDrawerItem(
                        label = { Text("Trash", color = MaterialTheme.colorScheme.error) },
                        icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        selected = false,
                        onClick = { 
                            coroutineScope.launch { drawerState.close() }
                            onTrashClick() 
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (selectionMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout)
                    ) {
                        TopAppBar(
                            title = { Text("${selectedNotes.size} Selected") },
                            navigationIcon = {
                                IconButton(onClick = { selectionMode = false; selectedNotes = emptySet() }) { Icon(Icons.Default.Close, "Cancel") }
                            },
                            actions = {
                                IconButton(onClick = { viewModel.togglePinMultiple(selectedNotes, !isAllPinned); selectionMode = false; selectedNotes = emptySet() }) {
                                    Icon(Icons.Default.PushPin, "Pin", tint = if (isAllPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.cloneMultiple(selectedNotes); selectionMode = false; selectedNotes = emptySet() }) { Icon(Icons.Default.ContentCopy, "Clone") }
                                IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    }
                } else {
                    with(animatedVisibilityScope) {
                        MorphingSearchBar(
                            query = searchQuery,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            isFocused = isSearchFocused,
                            onFocusChange = { isSearchFocused = it },
                            onClearFocus = { focusManager.clearFocus() },
                            onMenuClick = {
                                if (!isTransitioning) {
                                    coroutineScope.launch { drawerState.open() }
                                }
                            },
                            modifier = Modifier.animateEnterExit(
                                enter = scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                                exit = scaleOut(targetScale = 0.9f) + fadeOut()
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!selectionMode) {
                    with(sharedTransitionScope) {
                        ExtendedFloatingActionButton(
                            onClick = { importLauncher.launch("audio/*") },
                            expanded = isFabExpanded,
                            icon = { Icon(Icons.Default.Audiotrack, "Import Audio") },
                            text = { Text("Import Audio") },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                                .alpha(if (animatedVisibilityScope.transition.targetState == EnterExitState.Visible) 1f else 0f)
                                .then(with(animatedVisibilityScope) { Modifier.animateEnterExit(enter = scaleIn(initialScale = 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))) })
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (notes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedLabels.isNotEmpty()) "No results found." else "No notes yet.\nStart recording or import audio!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    with(animatedVisibilityScope) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .animateEnterExit(
                                    enter = slideInVertically(initialOffsetY = { 100 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                                    exit = slideOutVertically(targetOffsetY = { 100 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut()
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp
                        ) {
                            if (pinnedNotes.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Text("Pinned", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp))
                                }
                                items(pinnedNotes, key = { it.id }) { note ->
                                    DismissibleNoteCard(
                                        note = note,
                                        modifier = Modifier.animateItem(),
                                        isSelected = selectedNotes.contains(note.id),
                                        selectedLabels = selectedLabels,
                                        selectionMode = selectionMode,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        viewModel = viewModel,
                                        parentScope = coroutineScope,
                                        snackbarHostState = snackbarHostState,
                                        onSelect = { 
                                            if (selectionMode) { 
                                                selectedNotes = if (selectedNotes.contains(note.id)) selectedNotes - note.id else selectedNotes + note.id 
                                                if (selectedNotes.isEmpty()) selectionMode = false 
                                            } else { onNoteClick(note.id) }
                                        },
                                        onLongSelect = { if (!selectionMode) { selectionMode = true; selectedNotes = setOf(note.id) } }
                                    )
                                }
                            }

                            if (unpinnedNotes.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Text("Collection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp))
                                }
                                items(unpinnedNotes, key = { it.id }) { note ->
                                    DismissibleNoteCard(
                                        note = note,
                                        modifier = Modifier.animateItem(),
                                        isSelected = selectedNotes.contains(note.id),
                                        selectedLabels = selectedLabels,
                                        selectionMode = selectionMode,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        viewModel = viewModel,
                                        parentScope = coroutineScope,
                                        snackbarHostState = snackbarHostState,
                                        onSelect = { 
                                            if (selectionMode) { 
                                                selectedNotes = if (selectedNotes.contains(note.id)) selectedNotes - note.id else selectedNotes + note.id 
                                                if (selectedNotes.isEmpty()) selectionMode = false 
                                            } else { onNoteClick(note.id) }
                                        },
                                        onLongSelect = { if (!selectionMode) { selectionMode = true; selectedNotes = setOf(note.id) } }
                                    )
                                }
                            }
                            item(span = StaggeredGridItemSpan.FullLine) { Spacer(modifier = Modifier.height(100.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showNewLabelDialog) {
        AlertDialog(
            onDismissRequest = { showNewLabelDialog = false },
            title = { Text("Create New Label") },
            text = { 
                OutlinedTextField(
                    value = newLabelInput, 
                    onValueChange = { newLabelInput = it }, 
                    label = { Text("Label Name") }, 
                    singleLine = true, 
                    modifier = Modifier.fillMaxWidth()
                ) 
            },
            confirmButton = {
                Button(onClick = {
                    if (newLabelInput.isNotBlank()) {
                        viewModel.createIndependentLabel(newLabelInput.trim())
                        showNewLabelDialog = false
                        newLabelInput = ""
                    }
                }) { Text("Create Label") }
            },
            dismissButton = { TextButton(onClick = { showNewLabelDialog = false }) { Text("Cancel") } }
        )
    }

    // Modal Dinamis Melar-Nguncup (Morphing) Fix Import & Animasi Halus
    if (labelBeingManaged != null && !showDeleteLabelDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { 
            if (isRenamingLabelMode) {
                isRenamingLabelMode = false // Batalin rename pake klik luar, balik ke awal
            } else {
                labelBeingManaged = null 
            }
        }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    // Mengubah spring supaya transisinya mulus dan mentulnya kerasa natural
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy, 
                            stiffness = Spring.StiffnessMedium
                        )
                    ) 
            ) {
                Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                    Text(
                        text = if (isRenamingLabelMode) "Renaming Label" else "Manage Label",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = labelBeingManaged ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Baris Rename (Tetap ada, nggak ngilang)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isRenamingLabelMode) { isRenamingLabelMode = true }
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit, 
                            contentDescription = "Rename", 
                            tint = if (isRenamingLabelMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Rename Label", 
                            color = if (isRenamingLabelMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, 
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isRenamingLabelMode) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // Baris Delete (Bakal ngilang kalau mode Rename nyala)
                    AnimatedVisibility(
                        visible = !isRenamingLabelMode,
                        enter = fadeIn(animationSpec = tween(250)) + expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)),
                        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDeleteLabelDialog = true }
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(16.dp))
                            Text("Delete Label", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    // Tampilan Form Input & Tombol (Bakal muncul memanjang kalau mode Rename nyala)
                    AnimatedVisibility(
                        visible = isRenamingLabelMode,
                        enter = fadeIn(animationSpec = tween(300, delayMillis = 100)) + expandVertically(expandFrom = Alignment.Top, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)),
                        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                    ) {
                        Column {
                            OutlinedTextField(
                                value = renameLabelInput,
                                onValueChange = { renameLabelInput = it },
                                label = { Text("New Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { isRenamingLabelMode = false }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    val oldLabel = labelBeingManaged
                                    if (oldLabel != null && renameLabelInput.isNotBlank() && renameLabelInput.trim() != oldLabel) {
                                        viewModel.renameLabel(oldLabel, renameLabelInput.trim())
                                    }
                                    isRenamingLabelMode = false
                                    labelBeingManaged = null
                                }) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteLabelDialog && labelBeingManaged != null) {
        AlertDialog(
            onDismissRequest = { showDeleteLabelDialog = false; labelBeingManaged = null },
            title = { Text("Delete Label") },
            text = { Text("Label \"${labelBeingManaged}\" will be removed from all notes. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    labelBeingManaged?.let { viewModel.deleteLabel(it) }
                    showDeleteLabelDialog = false
                    labelBeingManaged = null
                    isRenamingLabelMode = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLabelDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteMultipleLabelsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMultipleLabelsDialog = false },
            title = { Text("Delete Labels") },
            text = { Text("${selectedLabels.size} label${if (selectedLabels.size > 1) "s" else ""} will be removed from all notes. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMultipleLabels(selectedLabels)
                    showDeleteMultipleLabelsDialog = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMultipleLabelsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Notes") },
            text = { Text("Are you sure you want to delete ${selectedNotes.size} notes?") },
            confirmButton = {
                TextButton(onClick = {
                    val idsToDelete = selectedNotes
                    viewModel.deleteMultiple(idsToDelete)
                    showDeleteDialog = false
                    selectionMode = false
                    selectedNotes = emptySet()
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "${idsToDelete.size} note${if (idsToDelete.size > 1) "s" else ""} moved to Trash",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoDelete()
                        } else {
                            viewModel.clearRecentlyDeleted()
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    if (latestRelease != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.dismissUpdateNotification() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NewReleases, contentDescription = "Update", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("New Update Available!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Version ${latestRelease!!.tag_name} is ready to download.", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.4f), RoundedCornerShape(12.dp)).padding(16.dp)) {
                    Text(latestRelease!!.body ?: "Performance improvements and new features.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { val apkUrl = latestRelease!!.assets?.firstOrNull()?.browser_download_url ?: latestRelease!!.html_url; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))); viewModel.dismissUpdateNotification() }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Download Update (APK)") }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestRelease!!.html_url))); viewModel.dismissUpdateNotification() }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("View on GitHub") }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DismissibleNoteCard(
    note: NoteEntity,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    selectedLabels: Set<String>,
    selectionMode: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: HistoryViewModel,
    parentScope: CoroutineScope, 
    snackbarHostState: SnackbarHostState,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                viewModel.deleteMultiple(setOf(note.id))
                parentScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Note moved to Trash",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    } else {
                        viewModel.clearRecentlyDeleted()
                    }
                }
                false
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                label = "deleteColor"
            )
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
        modifier = modifier
    ) {
        with(sharedTransitionScope) {
            NoteCard(
                note = note, 
                isSelected = isSelected,
                selectedLabels = selectedLabels,
                modifier = Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState("note-${note.id}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
                    boundsTransform = { _, _ ->
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    }
                ),
                onLongClick = onLongSelect,
                onClick = onSelect,
                onLabelClick = { label -> viewModel.toggleLabelFilter(label) }
            )
        }
    }
}

@Composable
fun MorphingSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onClearFocus: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier 
) {
    val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets
    
    val cornerRadius by animateDpAsState(targetValue = if (isFocused) 0.dp else 50.dp, animationSpec = spring(), label = "corner")
    val horizontalPadding by animateDpAsState(targetValue = if (isFocused) 0.dp else 16.dp, animationSpec = spring(), label = "hPad")
    val topMargin by animateDpAsState(targetValue = if (isFocused) 0.dp else safeTopMargin + 8.dp, animationSpec = spring(), label = "tMargin")
    val contentTopPadding by animateDpAsState(targetValue = if (isFocused) safeTopMargin + 24.dp else 16.dp, animationSpec = spring(), label = "cTopPad")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = topMargin, bottom = 8.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surface) 
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = contentTopPadding, bottom = 16.dp, start = 8.dp, end = 20.dp)
                .defaultMinSize(minHeight = 48.dp) 
        ) {
            AnimatedVisibility(visible = !isFocused, enter = expandHorizontally(animationSpec = spring()), exit = shrinkHorizontally(animationSpec = spring())) {
                IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "Menu", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(modifier = Modifier.width(if (isFocused) 12.dp else 4.dp))
            Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) { Text("Search notes...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                BasicTextField(
                    value = query, onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), singleLine = true,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) }
                )
            }
            if (isFocused || query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onQueryChange(""); onClearFocus() }
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
    selectedLabels: Set<String>,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onLabelClick: (String) -> Unit
) {
    val minHeight = remember(note.id) { kotlin.random.Random(note.id).nextInt(140, 221).dp }
    val formatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val displayText = if (!note.summary.isNullOrEmpty()) note.summary else if (note.rawText.isNotBlank()) note.rawText else "⏳ Waiting for AI transcription..."

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
            if (!note.label.isNullOrBlank()) {
                val labels = note.label.split("|").map { it.trim() }.filter { it.isNotBlank() }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    labels.forEach { label ->
                        val isLabelActive = label in selectedLabels
                        Box(
                            modifier = Modifier
                                .background(if (isLabelActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f), RoundedCornerShape(50))
                                .clickable { onLabelClick(label) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Label, null, tint = if (isLabelActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(label, style = MaterialTheme.typography.labelSmall, color = if (isLabelActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            Text(displayText, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.weight(1f))
            Text(formatter.format(Date(note.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp))
        }
    }
}
