package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.MarkdownText
import com.example.viewmodel.ResultViewModel
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
    
    // Audio States
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()

    var showLanguageMenu by remember { mutableStateOf(false) }
    var showSidePanel by remember { mutableStateOf(false) }
    var searchHighlightQuery by remember { mutableStateOf("") }
    var selectedFont by remember { mutableStateOf(FontFamily.SansSerif) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // FITUR 1: ZEN MODE (Sembunyiin Header pas Scroll Bawah)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // FITUR 3: Save MP3
    val exportAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mp4")
    ) { uri ->
        uri?.let {
            viewModel.exportAudio(context, it) { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    with(sharedTransitionScope) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "note-$noteId"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                )
                .nestedScroll(scrollBehavior.nestedScrollConnection), // Hook buat Zen Mode
            topBar = {
                TopAppBar(
                    title = { 
                        if (note != null) {
                            BasicTextField(
                                value = note!!.title,
                                onValueChange = { viewModel.updateTitle(it) },
                                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
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
                    },
                    scrollBehavior = scrollBehavior // Aktifin animasi nyelip ke atas
                )
            }
        ) { paddingValues ->
            if (note == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "AI is processing your text...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
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
                                highlightQuery = searchHighlightQuery, 
                                fontFamily = selectedFont,
                                listState = listState,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                            )
                        } else if (!isLoading) {
                            Text(
                                text = "Raw Transcript:\n\n${note!!.rawText}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = selectedFont),
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }

    // BOTTOM SHEET PANEL
    if (showSidePanel && note != null) {
        ModalBottomSheet(
            onDismissRequest = { showSidePanel = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            ) {
                
                // FITUR 2 & 3: Smart Chips + Audio Export (Material 3 Expressive Morphing Pill)
                Text("Export & Media", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Smart Chip: Play Audio Asli (Morphing Capsule)
                    if (note!!.audioPath != null) {
                        var isPlayPressed by remember { mutableStateOf(false) }
                        val playWidth by animateDpAsState(
                            targetValue = if (isPlayPressed) 130.dp else if (isPlaying) 120.dp else 110.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "playWidth"
                        )
                        Box(
                            modifier = Modifier
                                .width(playWidth).height(48.dp).clip(CircleShape)
                                .background(if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer)
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = {
                                        isPlayPressed = true; tryAwaitRelease(); isPlayPressed = false
                                        viewModel.toggleAudio()
                                    })
                                },
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

                    // Smart Chip: Save MP3
                    if (note!!.audioPath != null) {
                        Box(
                            modifier = Modifier.height(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                                exportAudioLauncher.launch("Binot_Audio_${note!!.id}.mp4")
                            }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, contentDescription = "Save MP3", tint = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Audio", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    // Smart Chip: Copy
                    Box(
                        modifier = Modifier.height(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Binot Note", note!!.summary ?: note!!.rawText))
                            coroutineScope.launch { snackbarHostState.showSnackbar("Text Copied!") }
                            showSidePanel = false
                        }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Smart Chip: Share
                    Box(
                        modifier = Modifier.height(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${note!!.title}\n\n${note!!.summary ?: note!!.rawText}")
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share note via"))
                            showSidePanel = false
                        }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // Slider Audio Progress (Hanya Muncul Kalo Ada Audio)
                if (note!!.audioPath != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = playbackProgress,
                        onValueChange = { viewModel.seekAudio(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                // FORMAT & SEARCH SECTION
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
                                    coroutineScope.launch { listState.animateScrollToItem(index) }
                                    showSidePanel = false
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
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

