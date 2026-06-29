package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.UpdateState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "buttonBounce"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale),
        content = content
    )
}

@Composable
private fun BouncyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "outlinedButtonBounce"
    )
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isRecording: Boolean = false,
    onDiscardRecording: () -> Unit = {}
) {
    val context = LocalContext.current
    val userName by viewModel.userName.collectAsState()
    val geminiApiKey by viewModel.apiKey.collectAsState()
    val groqApiKey by viewModel.groqApiKey.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val recordMode by viewModel.recordMode.collectAsState() 
    val aiProvider by viewModel.aiProvider.collectAsState() 
    
    // AI Preferences State (Actual Saved Data)
    val aiLanguage by viewModel.aiLanguage.collectAsState()
    val aiTask by viewModel.aiTask.collectAsState()
    val aiFormat by viewModel.aiFormat.collectAsState()

    // Temporary State (Prevents auto-save if user navigates away)
    var tempAiLanguage by remember(aiLanguage) { mutableStateOf(aiLanguage) }
    var tempAiTask by remember(aiTask) { mutableStateOf(aiTask) }
    var tempAiFormat by remember(aiFormat) { mutableStateOf(aiFormat) }
    
    // FIX: State temporary untuk AI Provider supaya nggak auto-save
    var tempAiProvider by remember(aiProvider) { mutableStateOf(aiProvider) }

    val updateState by viewModel.updateState.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val latestVersionStr by viewModel.latestVersionStr.collectAsState()

    // Input States
    var nameInput by remember(userName) { mutableStateOf(userName) }
    var geminiKeyInput by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
    var groqKeyInput by remember(groqApiKey) { mutableStateOf(groqApiKey) }

    // Dirty Flags (Buat deteksi apakah ada pergerakan ketikan di kolom input)
    var isNameDirty by remember { mutableStateOf(false) }
    var isGeminiKeyDirty by remember { mutableStateOf(false) }
    var isGroqKeyDirty by remember { mutableStateOf(false) }

    var showInfoDialog by remember { mutableStateOf(false) }
    var showAiInfoDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var pendingModeSelection by remember { mutableStateOf(-1) }
    
    // Dialog untuk Save Apply to All
    var showApplyAllDialog by remember { mutableStateOf(false) }

    // Bottom Sheet for Language Selection
    var showLanguageSheet by remember { mutableStateOf(false) }
    var languageSearchQuery by remember { mutableStateOf("") }
    
    val supportedLanguages = listOf(
        "English", "Indonesia", "Spanish", "French", "German", "Chinese (Simplified)", 
        "Chinese (Traditional)", "Japanese", "Korean", "Arabic", "Russian", "Portuguese", 
        "Italian", "Hindi", "Bengali", "Urdu", "Turkish", "Vietnamese", "Thai", 
        "Dutch", "Polish", "Swedish", "Malay"
    ).sorted()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    val currentVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0" } catch (e: Exception) { "1.0.0" }
    }
    
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportBackup(context, it) { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } } }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackup(context, it) { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } } }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Recording Modes") },
            text = { Text("Fast:\nFaster, battery efficient, moderate accuracy. Real-time transcription. Original audio is NOT SAVED on your device.\n\nAccurate:\nHigher accuracy, requires internet. Transcription processes later when you open the note. Live transcription is disabled. Original audio is SAVED on your device.") },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it") } }
        )
    }

    if (showAiInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAiInfoDialog = false },
            title = { Text("AI Providers") },
            text = { Text("Google Gemini:\nBest for complex content such as math, chemistry, and Mermaid diagrams. Supports long audio files.\n\nGroq AI (Recommended):\nBlazing fast and ideal for daily use. Audio uploads are limited to 25 MB.") },
            confirmButton = { TextButton(onClick = { showAiInfoDialog = false }) { Text("Got it") } }
        )
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false; pendingModeSelection = -1 },
            title = { Text("Warning") },
            text = { Text("Recording will be stopped and discarded. Continue?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onDiscardRecording()
                        if (pendingModeSelection != -1) {
                            viewModel.saveRecordMode(pendingModeSelection)
                        }
                        showWarningDialog = false
                        pendingModeSelection = -1
                    }
                ) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showWarningDialog = false; pendingModeSelection = -1 }) { Text("Cancel") } }
        )
    }
    
    if (showApplyAllDialog) {
        AlertDialog(
            onDismissRequest = { showApplyAllDialog = false },
            title = { Text("Save & Apply to All Notes?") },
            text = { Text("This will save your new preferences and reset the AI-generated results for all previous notes. They will be re-processed using your new preferences the next time you open them. Your original raw transcripts are completely safe.\n\nContinue?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    onClick = {
                        showApplyAllDialog = false
                        viewModel.saveAiLanguage(tempAiLanguage)
                        viewModel.saveAiTask(tempAiTask)
                        viewModel.saveAiFormat(tempAiFormat)
                        viewModel.applyAiPreferencesToAllNotes { msg ->
                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                ) { Text("Save & Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showApplyAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLanguageSheet) {
        ModalBottomSheet(
             onDismissRequest = { showLanguageSheet = false; languageSearchQuery = "" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).padding(horizontal = 16.dp)) {
                Text(
                    text = "Select Language",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = languageSearchQuery,
                    onValueChange = { languageSearchQuery = it },
                    label = { Text("Search Language...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
                
                val filteredLanguages = supportedLanguages.filter { 
                    it.contains(languageSearchQuery, ignoreCase = true) 
                }
                
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filteredLanguages) { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    tempAiLanguage = lang
                                    showLanguageSheet = false
                                    languageSearchQuery = ""
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = lang, style = MaterialTheme.typography.bodyLarge)
                            if (lang == tempAiLanguage) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        
        // FIX: Menggunakan displayCutout untuk margin aman bagian atas
        val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

        with(animatedVisibilityScope) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .animateEnterExit(enter = slideInVertically(initialOffsetY = { 100 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(safeTopMargin + 4.dp))

                Text(
                    text = "Settings", 
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Personalization", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = nameInput, 
                            onValueChange = { 
                                nameInput = it
                                isNameDirty = true 
                            }, 
                            label = { Text("Your Name") }, 
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        BouncyButton(
                            onClick = { 
                                viewModel.saveUserName(nameInput)
                                isNameDirty = false 
                                coroutineScope.launch { snackbarHostState.showSnackbar("Name saved successfully!") } 
                            }, 
                             enabled = isNameDirty, 
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Save Name")
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Global AI Preferences", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Notes will be automatically processed using these settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                        
                        // Output Language Selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showLanguageSheet = true }
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, contentDescription = "Language", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Output Language", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(tempAiLanguage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                             Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Select", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // AI Task
                        Text("Processing Task", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                onClick = { tempAiTask = 0 },
                                selected = tempAiTask == 0
                            ) { Text("Tidy Up", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                onClick = { tempAiTask = 1 },
                                selected = tempAiTask == 1
                             ) { Text("Summary", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                onClick = { tempAiTask = 2 },
                                selected = tempAiTask == 2
                            ) { Text("Analyze", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // AI Format
                        Text("Output Format", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                onClick = { tempAiFormat = 0 },
                                selected = tempAiFormat == 0
                            ) { Text("Paragraphs") }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                onClick = { tempAiFormat = 1 },
                                selected = tempAiFormat == 1
                            ) { Text("Bullets") }
                         }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        val isChanged = tempAiLanguage != aiLanguage || tempAiTask != aiTask || tempAiFormat != aiFormat
                        
                        BouncyButton(
                            onClick = { showApplyAllDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isChanged 
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save & Apply to All Notes")
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                 ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                             modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("AI Configuration", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { showAiInfoDialog = true }) {
                                Icon(Icons.Default.Info, contentDescription = "AI Info", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // FIX: Ubah state ke tempAiProvider supaya nggak auto-save
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                onClick = { tempAiProvider = 0 },
                                selected = tempAiProvider == 0
                            ) { Text("Gemini") }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                onClick = { tempAiProvider = 1 },
                                selected = tempAiProvider == 1
                            ) { Text("Groq") }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // FIX: Target state baca dari tempAiProvider
                        AnimatedContent(targetState = tempAiProvider, label = "ApiKeyInput") { provider ->
                            if (provider == 0) {
                                Column {
                                    OutlinedTextField(
                                        value = geminiKeyInput, 
                                        onValueChange = { 
                                            geminiKeyInput = it
                                            isGeminiKeyDirty = true 
                                        }, 
                                        label = { Text("Gemini API Key") }, 
                                        visualTransformation = PasswordVisualTransformation(), 
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Click here to get the API Key", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))) })
                                    Spacer(modifier = Modifier.height(12.dp))
                                    BouncyButton(
                                        onClick = { 
                                            viewModel.saveApiKey(geminiKeyInput)
                                            viewModel.saveAiProvider(tempAiProvider) // Save sekaligus
                                            isGeminiKeyDirty = false
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Gemini Configuration saved!") } 
                                        }, 
                                        // FIX: Enable kalau ada text beda ATAU provider beda
                                        enabled = isGeminiKeyDirty || tempAiProvider != aiProvider,
                                        modifier = Modifier.align(Alignment.End)
                                    ) { 
                                        Text("Save Key") 
                                    }
                                }
                             } else {
                                Column {
                                    OutlinedTextField(
                                        value = groqKeyInput, 
                                        onValueChange = { 
                                            groqKeyInput = it
                                            isGroqKeyDirty = true 
                                        }, 
                                        label = { Text("Groq API Key") }, 
                                        visualTransformation = PasswordVisualTransformation(), 
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Click here to get the API Key", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) })
                                    Spacer(modifier = Modifier.height(12.dp))
                                    BouncyButton(
                                        onClick = { 
                                            viewModel.saveGroqApiKey(groqKeyInput)
                                            viewModel.saveAiProvider(tempAiProvider) // Save sekaligus
                                            isGroqKeyDirty = false
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Groq Configuration saved!") } 
                                        }, 
                                        // FIX: Enable kalau ada text beda ATAU provider beda
                                        enabled = isGroqKeyDirty || tempAiProvider != aiProvider,
                                        modifier = Modifier.align(Alignment.End)
                                    ) { 
                                        Text("Save Key") 
                                    }
                                }
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Recording Mode", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { showInfoDialog = true }) {
                                Icon(Icons.Default.Info, contentDescription = "Mode Info", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                onClick = { 
                                    if (recordMode != 0) {
                                        if (isRecording) {
                                            pendingModeSelection = 0
                                            showWarningDialog = true
                                        } else {
                                            viewModel.saveRecordMode(0) 
                                        }
                                    }
                                },
                                selected = recordMode == 0
                            ) { Text("Fast") }
                             SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                onClick = { 
                                    if (recordMode != 1) {
                                        if (isRecording) {
                                            pendingModeSelection = 1
                                            showWarningDialog = true
                                        } else {
                                            viewModel.saveRecordMode(1) 
                                        }
                                    }
                                },
                                selected = recordMode == 1
                            ) { Text("Accurate") }
                        }
                    }
                }

                Card(
                     colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Appearance", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4), onClick = { viewModel.saveThemeMode(0) }, selected = themeMode == 0) { Text("Auto") }
                            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4), onClick = { viewModel.saveThemeMode(1) }, selected = themeMode == 1) { Text("Light") }
                            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4), onClick = { viewModel.saveThemeMode(2) }, selected = themeMode == 2) { Text("Dark") }
                            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4), onClick = { viewModel.saveThemeMode(3) }, selected = themeMode == 3) { Text("Amoled", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) }
                        }
                     }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                 ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Data & System", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Notes Backup", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.End) {
                                BouncyOutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.padding(end = 8.dp)) { Text("Restore") }
                                BouncyButton(onClick = { exportLauncher.launch("Binot_Backup_${formatter.format(Date())}.binotbak") }) { Text("Backup") }
                             }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("App Version", style = MaterialTheme.typography.bodyLarge)
                                Text("v$currentVersion", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                 
                                if (updateState == UpdateState.Downloading) {
                                    val animatedProgress by animateFloatAsState(targetValue = downloadProgress / 100f, label = "progress")
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small), color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Downloading... $downloadProgress%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                } else if (updateState == UpdateState.Available) {
                                    Text("New version ready: $latestVersionStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                } else if (updateState == UpdateState.Error) {
                                    Text("Failed: $latestVersionStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                } else if (updateState == UpdateState.Idle && latestVersionStr.isNotBlank()) {
                                    Text("App is up to date.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            AnimatedContent(targetState = updateState, label = "update_btn") { state ->
                                when (state) {
                                    UpdateState.Idle -> BouncyButton(onClick = { viewModel.checkForUpdate(currentVersion) }) { Text("Check Update") }
                                    UpdateState.Checking -> Button(onClick = {}, enabled = false) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                                    UpdateState.Available -> BouncyButton(onClick = { viewModel.startDownload(context) }) { Text("Update App") }
                                    UpdateState.Downloading -> OutlinedButton(onClick = {}) { Text("Downloading") }
                                    UpdateState.Downloaded -> BouncyButton(onClick = { viewModel.promptInstall(context) }) { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("Install") }
                                    UpdateState.Error -> BouncyOutlinedButton(onClick = { viewModel.checkForUpdate(currentVersion) }) { Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("Retry") }
                                }
                            }
                        }
                    }
                }
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/densl"))
                        context.startActivity(intent)
                    }
                 ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalCafe, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Support Development", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Donate via Ko-fi", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://saweria.co/Densl"))
                        context.startActivity(intent)
                    }
                 ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Support Development", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Donate via Saweria", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DENSLnetion/Binot"))
                        context.startActivity(intent)
                    }
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("GitHub Repository", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Star the repo or report an issue", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
