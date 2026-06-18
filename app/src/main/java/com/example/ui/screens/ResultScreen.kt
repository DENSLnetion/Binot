package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.ui.components.MarkdownText
import com.example.viewmodel.ResultViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    onNavigateBack: () -> Unit
) {
    val note by viewModel.note.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var showLanguageMenu by remember { mutableStateOf(false) }
    var showSidePanel by remember { mutableStateOf(false) }
    
    // Panel States
    var searchHighlightQuery by remember { mutableStateOf("") }
    var selectedFont by remember { mutableStateOf(FontFamily.SansSerif) }

    val scrollState = rememberScrollState()

    LaunchedEffect(isLoading) {
        if (isLoading) {
            scrollState.animateScrollTo(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Judul pindah ke TopBar biar bersih
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
                            Icon(imageVector = Icons.Default.Translate, contentDescription = "Translate")
                        }
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false }
                        ) {
                            // Bahasa dirombak sesuai request
                            val languages = listOf("Indonesia", "English", "Spanish", "Chinese", "Japanese")
                            languages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text("Summarize in $lang") },
                                    onClick = {
                                        viewModel.summarizeText(lang)
                                        showLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }
                    // Tombol Titik Tiga (Side Panel)
                    IconButton(onClick = { showSidePanel = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (note == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
            ) {
                if (isLoading) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "AI is organizing your notes...",
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
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else if (!isLoading) {
                    Text(
                        text = "Raw Transcript:\n\n${note!!.rawText}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Modal Bottom Sheet sebagai Side Panel
    if (showSidePanel) {
        ModalBottomSheet(
            onDismissRequest = { showSidePanel = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text("Find & Format", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = searchHighlightQuery,
                    onValueChange = { searchHighlightQuery = it },
                    label = { Text("Find word in note...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier.fillMaxWidth()
                )
                
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

