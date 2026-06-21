package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
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
    val loadingMessage by viewModel.loadingMessage.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val allLabels by viewModel.allLabels.collectAsState()

    var showLanguageMenu by remember { mutableStateOf(false) }
    var showSidePanel by remember { mutableStateOf(false) }
    var showNewLabelDialog by remember { mutableStateOf(false) }
    
    var showEditSheet by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    var newLabelInput by remember { mutableStateOf("") }
    
    var searchHighlightQuery by remember { mutableStateOf("") }
    var temporaryHighlight by remember { mutableStateOf("") }
    var selectedFont by remember { mutableStateOf(FontFamily.SansSerif) }

    // States for Highlights & AI Explain
    var showHighlightDialog by remember { mutableStateOf(false) }
    var currentHighlightWord by remember { mutableStateOf("") }
    var highlightNoteInput by remember { mutableStateOf("") }

    var showAiExplainSheet by remember { mutableStateOf(false) }
    var aiExplainTargetWord by remember { mutableStateOf("") }

    // States for Custom Selection Toolbar
    var selectionRect by remember { mutableStateOf(Rect.Zero) }
    var showCustomMenu by remember { mutableStateOf(false) }
    var copyAction by remember { mutableStateOf<() -> Unit>({}) }
    var selectAllAction by remember { mutableStateOf<() -> Unit>({}) }

    val listState = rememberLazyListState()
    val rawTextScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val deviceLanguage = java.util.Locale.getDefault().displayLanguage
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

    val editSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { dismissValue ->
            if (dismissValue == SheetValue.Hidden && hasUnsavedChanges) {
                showCancelConfirmDialog = true
                false 
            } else {
                true
            }
        }
    )

    BackHandler(enabled = true) {
        if (showCustomMenu) {
            showCustomMenu = false
        } else if (showCancelConfirmDialog) {
            showCancelConfirmDialog = false
        } else if (showSidePanel) {
            showSidePanel = false
        } else if (showEditSheet) {
            if (hasUnsavedChanges) {
                showCancelConfirmDialog = true
            } else {
                coroutineScope.launch { editSheetState.hide(); showEditSheet = false }
            }
        } else if (isTitleFocused) {
            focusManager.clearFocus()
        } else {
            closeNote()
        }
    }

    // Custom Toolbar Logic Override
    val customTextToolbar = remember {
        object : TextToolbar {
            override var status: TextToolbarStatus = TextToolbarStatus.Hidden

            override fun hide() {
                status = TextToolbarStatus.Hidden
                showCustomMenu = false
            }

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                selectionRect = rect
                copyAction = onCopyRequested ?: {}
                selectAllAction = onSelectAllRequested ?: {}
                status = TextToolbarStatus.Shown
                showCustomMenu = true
            }
        }
    }

    // Clipboard Injection Hack (to securely steal the selected text without overriding user's clipboard permanently)
    fun extractSelectedTextAndExecute(action: (String) -> Unit) {
        val oldClip = clipboardManager.getText()
        copyAction() 
        val newClip = clipboardManager.getText()?.text ?: ""
        if (oldClip != null) {
            clipboardManager.setText(oldClip)
        } else {
            clipboardManager.setText(AnnotatedString(""))
        }
        showCustomMenu = false
        action(newClip.trim())
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
                        if (showContent && note?.rawText != "Pending Transcription") {
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
                if (note != null && note!!.summary.isNullOrEmpty() && !isLoading && showContent && note!!.rawText != "Pending Transcription") {
                    val isFabExpanded by remember { derivedStateOf { rawTextScrollState.value == 0 } }
                    with(sharedTransitionScope) {
                        ExtendedFloatingActionButton(
                            onClick = { showEditSheet = true },
                            expanded = isFabExpanded,
                            icon = { Icon(Icons.Default.Edit, "Edit") },
                            text = { Text("Edit") },
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
        ) { paddingValues ->
            if (note == null || !showContent) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    AiThinkingAnimation(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                CompositionLocalProvider(LocalTextToolbar provides customTextToolbar) {
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
                                            text = loadingMessage.ifBlank { "Processing..." },
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
                                    listState = listState,
                                    highlightsInfo = note!!.highlightsInfo,
                                    onSavedHighlightClick = { word, noteText ->
                                        currentHighlightWord = word
                                        highlightNoteInput = noteText
                                        showHighlightDialog = true
                                    },
                                    highlightQuery = temporaryHighlight,
                                    fontFamily = selectedFont,
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                )
                            } else if (!isLoading && note!!.rawText != "Pending Transcription") {
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
    }

    // --- MODAL DIALOG HIGHLIGHT NOTES ---
    if (showHighlightDialog) {
        AlertDialog(
            onDismissRequest = { showHighlightDialog = false },
            title = { Text("Highlight Note") },
            text = {
                Column {
                    Text("\"$currentHighlightWord\"", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = highlightNoteInput,
                        onValueChange = { highlightNoteInput = it },
                        label = { Text("Your Note") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveHighlightNote(currentHighlightWord, highlightNoteInput)
                    showHighlightDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.removeHighlight(currentHighlightWord)
                            showHighlightDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showHighlightDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // --- BOTTOM SHEET AI EXPLAIN ---
    if (showAiExplainSheet) {
        val explainResult by viewModel.explainResult.collectAsState()
        val isExplaining by viewModel.isExplaining.collectAsState()

        ModalBottomSheet(
            onDismissRequest = { 
                showAiExplainSheet = false
                viewModel.clearExplainResult()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("AI Explain", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text("\"$aiExplainTargetWord\"", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                
                if (isExplaining) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        AiThinkingAnimation(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Text(
                        text = explainResult ?: "No explanation available.",
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
                    )
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    // --- CUSTOM BUBBLE MENU ---
    if (showCustomMenu) {
        val density = LocalDensity.current
        Popup(
            popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    var x = selectionRect.left.toInt() - (popupContentSize.width / 2) + (selectionRect.width.toInt() / 2)
                    var y = selectionRect.top.toInt() - popupContentSize.height - with(density) { 8.dp.roundToPx() }
                    
                    if (x < 16) x = 16
                    if (x + popupContentSize.width > windowSize.width - 16) {
                        x = windowSize.width - popupContentSize.width - 16
                    }
                    if (y < 16) {
                        y = selectionRect.bottom.toInt() + with(density) { 8.dp.roundToPx() }
                    }
                    return IntOffset(x, y)
                }
            },
            onDismissRequest = { showCustomMenu = false }
        ) {
            Card(
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { 
                        extractSelectedTextAndExecute { text ->
                            if (text.isNotBlank()) {
                                currentHighlightWord = text
                                highlightNoteInput = ""
                                showHighlightDialog = true
                            }
                        }
                    }) {
                        Icon(Icons.Default.Brush, contentDescription = "Highlight", tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    IconButton(onClick = { 
                        copyAction()
                        showCustomMenu = false
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    IconButton(onClick = { 
                        selectAllAction()
                    }) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    IconButton(onClick = { 
                        extractSelectedTextAndExecute { text ->
                            if (text.isNotBlank()) {
                                aiExplainTargetWord = text
                                viewModel.explainText(text, deviceLanguage)
                                showAiExplainSheet = true
                            }
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "AI Explain", tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
            }
        }
    }

    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = { Text("Cancel editing?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirmDialog = false
                        hasUnsavedChanges = false 
                        coroutineScope.launch { 
                            editSheetState.hide()
                            showEditSheet = false 
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("Keep editing")
                }
            }
        )
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

    if (showEditSheet && note != null && note!!.rawText != "Pending Transcription") {
        var textValue by remember(note!!.id) { mutableStateOf(TextFieldValue(note!!.rawText)) }
        val undoStack = remember { mutableStateListOf<TextFieldValue>() }
        val redoStack = remember { mutableStateListOf<TextFieldValue>() }

        LaunchedEffect(textValue.text) {
            hasUnsavedChanges = textValue.text != note!!.rawText
        }

        ModalBottomSheet(
            onDismissRequest = {
                if (hasUnsavedChanges) showCancelConfirmDialog = true
                else showEditSheet = false
            },
            sheetState = editSheetState
        ) {
            val scrollWall = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        return available 
                    }
                    override suspend fun onPostFling(
                        consumed: Velocity,
                        available: Velocity
                    ): Velocity {
                        return available 
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .nestedScroll(scrollWall) 
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { } 
                    )
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Edit Transcript", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BouncyCapsule(
                            onClick = {
                                if (undoStack.isNotEmpty()) {
                                    redoStack.add(textValue)
                                    textValue = undoStack.removeLast()
                                }
                            },
                            containerColor = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        BouncyCapsule(
                            onClick = {
                                if (redoStack.isNotEmpty()) {
                                    undoStack.add(textValue)
                                    textValue = redoStack.removeLast()
                                }
                            },
                            containerColor = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                    BasicTextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            if (newValue.text != textValue.text) {
                                undoStack.add(textValue)
                                redoStack.clear()
                            }
                            textValue = newValue
                        },
                        modifier = Modifier.fillMaxSize(), 
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = selectedFont
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .imePadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BouncyCapsule(
                        onClick = { 
                            if (hasUnsavedChanges) showCancelConfirmDialog = true
                            else {
                                coroutineScope.launch { editSheetState.hide(); showEditSheet = false }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    }
                    BouncyCapsule(
                        onClick = {
                            viewModel.updateRawText(textValue.text)
                            hasUnsavedChanges = false 
                            coroutineScope.launch { 
                                editSheetState.hide()
                                showEditSheet = false 
                                snackbarHostState.showSnackbar("Text saved!") 
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
                                text = "Audio unavailable. This note was created without saving audio or the file has been moved.",
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
                                    coroutineScope.launch { snackbarHostState.showSnackbar("AI summary removed!") }
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

                    if (note!!.originalRawText != null && note!!.originalRawText != note!!.rawText && note!!.summary == null) {
                        item {
                            BouncyCapsule(
                                onClick = {
                                    viewModel.restoreOriginalRawText { previousNote ->
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Original raw text restored!",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.undoRestoreRawText(previousNote)
                                            }
                                        }
                                    }
                                    showSidePanel = false
                                },
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore Raw", tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore Original Text", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
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

