package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.ui.components.MarkdownText
import com.example.viewmodel.ResultViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    noteId: Int, 
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val note by viewModel.note.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val allLabels by viewModel.allLabels.collectAsState()

    var showLanguageMenu by remember { mutableStateOf(false) }
    var showSidePanel by remember { mutableStateOf(false) }
    var showNewLabelDialog by remember { mutableStateOf(false) }
    var newLabelInput by remember { mutableStateOf("") }
    
    var searchHighlightQuery by remember { mutableStateOf("") }
    var temporaryHighlight by remember { mutableStateOf("") }
    var selectedFont by remember { mutableStateOf(FontFamily.SansSerif) }

    val listState = rememberLazyListState()
    val rawTextScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var showEditSheet by remember { mutableStateOf(false) }
    var isEditFabExpanded by remember { mutableStateOf(true) }
    var previousRawScrollOffset by remember { mutableStateOf(0) }

    // Tracks scroll direction on the raw text to drive the FAB capsule -> empty animation.
    // Scrolling down closes the capsule, scrolling up (or reaching the top) reopens it.
    LaunchedEffect(rawTextScrollState.value) {
        val current = rawTextScrollState.value
        val delta = current - previousRawScrollOffset
        when {
            current <= 0 -> isEditFabExpanded = true
            delta > 4 -> isEditFabExpanded = false
            delta < -4 -> isEditFabExpanded = true
        }
        previousRawScrollOffset = current
    }
    val snackbarHostState = remember { SnackbarHostState() }

    val focusManager = LocalFocusManager.current
    var isTitleFocused by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val exportAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mp4")
    ) { uri ->
        uri?.let {
            viewModel.exportAudio(context, it) { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        showContent = true
    }

    val closeNote: () -> Unit = {
        showContent = false
        onNavigateBack()
    }

    BackHandler(enabled = true) {
        if (showSidePanel) {
            showSidePanel = false
        } else if (isTitleFocused) {
            focusManager.clearFocus()
        } else {
            closeNote()
        }
    }

    with(sharedTransitionScope) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.surface, 
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "note-$noteId"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                )
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = { 
                        if (note != null && showContent) {
                            BasicTextField(
                                value = note!!.title,
                                onValueChange = { viewModel.updateTitle(it) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isTitleFocused = it.isFocused }
                            )
                        } else if (note != null) {
                            Text(
                                text = note!!.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = closeNote) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (showContent) {
                            Box {
                                IconButton(onClick = { showLanguageMenu = true }) {
                                    Icon(imageVector = Icons.Default.Translate, contentDescription = "Process Text")
                                }
                                DropdownMenu(
                                    expanded = showLanguageMenu,
                                    onDismissRequest = { showLanguageMenu = false }
                                ) {
                                    val languages = listOf("Indonesia", "English", "Spanish", "Chinese", "Japanese")
                                    languages.forEachIndexed { index, lang ->
                                        Text(
                                            text = lang,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        DropdownMenuItem(text = { Text("Tidy Up") }, onClick = { viewModel.processText(lang, "tidy"); showLanguageMenu = false })
                                        DropdownMenuItem(text = { Text("Summarize") }, onClick = { viewModel.processText(lang, "summarize"); showLanguageMenu = false })
                                        if (index < languages.size - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                            IconButton(onClick = { showSidePanel = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                if (showContent) {
                    // FAB only exists for raw, unprocessed text. Once AI has produced a summary
                    // (or while it's processing) it disappears; restoring raw text brings it back.
                    val isRawTextMode = note != null && note!!.summary.isNullOrEmpty() && !isLoading
                    EditFab(
                        visible = isRawTextMode,
                        expanded = isEditFabExpanded,
                        onClick = { showEditSheet = true }
                    )
                }
            }
        ) { paddingValues ->
            if (note == null || !showContent) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    AiThinkingAnimation(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                SelectionContainer(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isLoading) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AiThinkingAnimation(color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "AI is processing your text...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (error != null) {
                            Text(
                                text = error!!,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        if (!note!!.summary.isNullOrEmpty()) {
                            MarkdownText(
                                text = note!!.summary!!, 
                                highlightQuery = temporaryHighlight,
                                fontFamily = selectedFont,
                                listState = listState,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                            )
                        } else if (!isLoading) {
                            Text(
                                text = buildHighlightedString(
                                    text = "Raw Transcript:\n\n${note!!.rawText}", 
                                    query = temporaryHighlight,
                                    highlightColor = Color.Yellow.copy(alpha = 0.5f),
                                    textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                ),
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = selectedFont),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .verticalScroll(rawTextScrollState)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewLabelDialog) {
        AlertDialog(
            onDismissRequest = { showNewLabelDialog = false },
            title = { Text("New Label") },
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
                        viewModel.toggleLabel(newLabelInput.trim())
                        showNewLabelDialog = false
                        newLabelInput = ""
                    }
                }) { Text("Create & Assign") }
            },
            dismissButton = { TextButton(onClick = { showNewLabelDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSidePanel && note != null) {
        ModalBottomSheet(
            onDismissRequest = { showSidePanel = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text("Labels", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allLabels.filter { it.isNotBlank() }) { label ->
                        val activeLabels = note!!.label?.split("|")?.map { it.trim() } ?: emptyList()
                        val isSelected = activeLabels.contains(label)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { viewModel.toggleLabel(label) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Label, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(label, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                .clickable { showNewLabelDialog = true }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Label", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text("Find & Format", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchHighlightQuery,
                    onValueChange = { searchHighlightQuery = it },
                    label = { Text("Find word in note...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier.fillMaxWidth()
                )

                val textToSearch = note!!.summary ?: note!!.rawText
                val lines = textToSearch.split("\n")
                val searchResults = lines.mapIndexedNotNull { index, line ->
                    if (searchHighlightQuery.isNotBlank() && line.contains(searchHighlightQuery, ignoreCase = true)) {
                        index to line.trim()
                    } else null
                }

                if (searchHighlightQuery.isNotBlank() && searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                        items(searchResults) { (index, line) ->
                            Text(
                                text = line, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    coroutineScope.launch { 
                                        temporaryHighlight = searchHighlightQuery
                                        if (note!!.summary != null) {
                                            listState.animateScrollToItem(index)
                                        } else {
                                            rawTextScrollState.animateScrollTo(index * 60)
                                        }
                                        showSidePanel = false
                                        delay(4000)
                                        temporaryHighlight = "" 
                                    }
                                }.padding(vertical = 12.dp, horizontal = 8.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Reading Font", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        onClick = { selectedFont = FontFamily.SansSerif },
                        selected = selectedFont == FontFamily.SansSerif
                    ) { Text("Sans") }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        onClick = { selectedFont = FontFamily.Serif },
                        selected = selectedFont == FontFamily.Serif
                    ) { Text("Serif") }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        onClick = { selectedFont = FontFamily.Monospace },
                        selected = selectedFont == FontFamily.Monospace
                    ) { Text("Mono") }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text("Export & Media", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))

                if (note!!.audioPath == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Audio unavailable. This note was created via Live Dictation mode which processes speech in real-time without saving audio files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (note!!.summary != null) {
                        item {
                            BouncyCapsule(
                                onClick = {
                                    viewModel.restoreRawText()
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Original raw text restored!") }
                                    showSidePanel = false
                                },
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore", tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore Original", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (note!!.audioPath != null) {
                        item {
                            val playInteractionSource = remember { MutableInteractionSource() }
                            val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                            val playWidth by animateDpAsState(
                                targetValue = if (isPlayPressed) 130.dp else if (isPlaying) 120.dp else 110.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "playWidth"
                            )
                            Box(
                                modifier = Modifier
                                    .width(playWidth).height(48.dp).clip(CircleShape)
                                    .background(if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable(
                                        interactionSource = playInteractionSource,
                                        indication = null,
                                        onClick = { viewModel.toggleAudio() }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isPlaying) "Pause" else "Play", 
                                        color = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    if (note!!.audioPath != null) {
                        item {
                            BouncyCapsule(
                                onClick = { exportAudioLauncher.launch("Binot_Audio_${note!!.id}.mp4") },
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Save MP3", tint = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Audio", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    item {
                        BouncyCapsule(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Binot Note", note!!.summary ?: note!!.rawText))
                                coroutineScope.launch { snackbarHostState.showSnackbar("Text Copied!") }
                                showSidePanel = false
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    item {
                        BouncyCapsule(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${note!!.title}\n\n${note!!.summary ?: note!!.rawText}")
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share note via"))
                                showSidePanel = false
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                if (note!!.audioPath != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = playbackProgress,
                        onValueChange = { viewModel.seekAudio(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    if (showEditSheet && note != null) {
        EditRawTextSheet(
            initialText = note!!.rawText,
            onDismiss = { showEditSheet = false },
            onSave = { newText ->
                viewModel.updateRawText(newText)
                showEditSheet = false
                coroutineScope.launch { snackbarHostState.showSnackbar("Raw text updated!") }
            }
        )
    }
}

fun buildHighlightedString(text: String, query: String, highlightColor: Color, textColor: Color) = buildAnnotatedString {
    if (query.isBlank()) {
        withStyle(SpanStyle(color = textColor)) { append(text) }
        return@buildAnnotatedString
    }
    
    var startIndex = 0
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    
    while (startIndex < text.length) {
        val index = lowerText.indexOf(lowerQuery, startIndex)
        if (index == -1) {
            withStyle(SpanStyle(color = textColor)) { append(text.substring(startIndex)) }
            break
        }
        withStyle(SpanStyle(color = textColor)) { append(text.substring(startIndex, index)) }
        withStyle(SpanStyle(background = highlightColor, color = Color.Black)) {
            append(text.substring(index, index + query.length))
        }
        startIndex = index + query.length
    }
}

@Composable
private fun BouncyCapsule(
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "capsuleScale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .height(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun AiThinkingAnimation(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_thinking")
    val heights = List(4) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400, delayMillis = index * 100, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "height_$index"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(32.dp)
    ) {
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight(height.value)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * The "Edit" FAB. Visually it's the same capsule language as [BouncyCapsule] (pill shape,
 * icon + label, bouncy press scale) but its open/close animation is different from the
 * History screen's "import audio" FAB: instead of morphing into a circle on scroll, it
 * shrinks horizontally down to nothing and disappears, then expands back into the full
 * capsule. The same animation also plays when [visible] toggles (raw text shown/hidden).
 */
@Composable
private fun EditFab(
    visible: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible && expanded,
        enter = expandHorizontally(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            expandFrom = Alignment.Start
        ) + fadeIn(),
        exit = shrinkHorizontally(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            shrinkTowards = Alignment.Start
        ) + fadeOut()
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.90f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "editFabScale"
        )
        Box(
            modifier = Modifier
                .scale(scale)
                .height(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CompactCapsule(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "compactCapsuleScale"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .height(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (enabled) 1f else 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (enabled) 1f else 0.6f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Bottom sheet for editing raw text, visually consistent with the side panel sheet
 * (same ModalBottomSheet style). Its size is fixed to a proportion of the screen and
 * never grows with the amount of text typed — the editable text sits in its own
 * scrollable card inside that fixed area. Has its own local undo/redo history,
 * independent from the "Restore Original" (AI restore) feature elsewhere in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRawTextSheet(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var editText by remember { mutableStateOf(initialText) }
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    var lastSnapshot by remember { mutableStateOf(initialText) }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }

    // Debounced snapshot: ~600ms after the user stops typing, push the previous state
    // onto the undo stack. Keeps the history from growing one entry per keystroke.
    LaunchedEffect(editText) {
        delay(600)
        if (editText != lastSnapshot) {
            undoStack.add(lastSnapshot)
            if (undoStack.size > 50) undoStack.removeAt(0)
            lastSnapshot = editText
            redoStack.clear()
        }
    }

    // Keeps the cursor visible above the keyboard as the user types, instead of making
    // them scroll manually to find it.
    LaunchedEffect(editText) {
        bringIntoViewRequester.bringIntoView()
    }

    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    "Edit Raw Text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactCapsule(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                redoStack.add(editText)
                                val previous = undoStack.removeAt(undoStack.size - 1)
                                editText = previous
                                lastSnapshot = previous
                            }
                        },
                        enabled = undoStack.isNotEmpty(),
                        icon = Icons.AutoMirrored.Filled.Undo,
                        label = "Undo"
                    )
                    CompactCapsule(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                undoStack.add(editText)
                                val next = redoStack.removeAt(redoStack.size - 1)
                                editText = next
                                lastSnapshot = next
                            }
                        },
                        enabled = redoStack.isNotEmpty(),
                        icon = Icons.AutoMirrored.Filled.Redo,
                        label = "Redo"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fixed-size card; this is what scrolls internally as text grows, the
                // sheet itself never resizes.
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val cardScrollState = rememberScrollState()
                    BasicTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(cardScrollState)
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .focusRequester(focusRequester)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BouncyCapsule(
                        onClick = onDismiss,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    }
                    BouncyCapsule(
                        onClick = {
                            coroutineScope.launch { onSave(editText) }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}



