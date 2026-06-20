package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
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

    BackHandler(enabled = isTitleFocused || showSidePanel) {
        if (showSidePanel) {
            showSidePanel = false
        } else if (isTitleFocused) {
            focusManager.clearFocus()
        }
    }

    // PENTING (fix lag/stuck saat buka-tutup catatan):
    // sharedBounds() menghitung morph posisi/ukuran SETIAP FRAME selama transisi
    // berjalan. Sebelumnya seluruh isi ResultScreen (TopAppBar dgn BasicTextField,
    // MarkdownText/teks panjang, semua tombol export) langsung di-compose+layout
    // BERSAMAAN dengan proses morphing itu — Compose jadi harus ngerjain dua kerjaan
    // berat sekaligus per frame, makanya kerasa nge-stuck beberapa milidetik.
    //
    // Pola yang dipakai Google Keep: container (shape kosong) morph duluan, konten
    // detail baru muncul SETELAH morph kelar. Di sini ditiru dengan showContent:
    // mulai false (Scaffold cuma nampilin shape+warna kosong via topBar minimal),
    // baru jadi true sesaat setelah delay pendek — pas morph sudah hampir/benar2
    // selesai — baru konten berat di-compose.
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(220)
        showContent = true
    }

    with(sharedTransitionScope) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "note-$noteId"),
                    animatedVisibilityScope = animatedVisibilityScope
                    // resizeMode default (RemeasureToBounds) dipakai, BUKAN ScaleToBounds.
                    // ScaleToBounds menambah transform scale di atas resize biasa — lebih
                    // mahal dihitung tiap frame. RemeasureToBounds cukup untuk morph solid.
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
                            // Placeholder ringan (cuma Text statis, tanpa BasicTextField/focus
                            // listener) selama morph masih berjalan — jauh lebih murah di-layout.
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
                        IconButton(onClick = onNavigateBack) {
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
            }
        ) { paddingValues ->
            if (note == null || !showContent) {
                // Selama morph berjalan (showContent masih false), body cuma nampilin
                // loading indicator ringan — TIDAK compose MarkdownText/raw text yang berat.
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
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            ) {
                // 1. SECTION: LABELS
                Text("Labels", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allLabels.filter { it.isNotBlank() }.forEach { label ->
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
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                // 2. SECTION: FIND & FORMAT (Dipindah ke atas sini)
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

                // 3. SECTION: EXPORT & MEDIA (Dipindah ke bawah sini)
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
                
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (note!!.summary != null) {
                        Box(
                            modifier = Modifier.height(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer).clickable {
                                viewModel.restoreRawText()
                                coroutineScope.launch { snackbarHostState.showSnackbar("Original raw text restored!") }
                                showSidePanel = false
                            }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore", tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore Original", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

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
}

// MESIN HIGHLIGHTER BUAT TEKS MENTAH
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

