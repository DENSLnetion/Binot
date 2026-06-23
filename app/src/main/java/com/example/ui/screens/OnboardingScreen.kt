package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
private fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "expressive_btn"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale),
        contentPadding = contentPadding,
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: (String, Int, String, String, Int, Int) -> Unit // name, aiProvider, apiKey, lang, task, format
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var nameInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var aiProvider by remember { mutableStateOf(1) } // Default 1 (Groq)
    
    // Preferences Data
    var tempAiLanguage by remember { mutableStateOf("English") }
    var tempAiTask by remember { mutableStateOf(0) }
    var tempAiFormat by remember { mutableStateOf(0) }

    var showLanguageSheet by remember { mutableStateOf(false) }
    var showApiTutorialSheet by remember { mutableStateOf(false) }
    var languageSearchQuery by remember { mutableStateOf("") }
    
    val supportedLanguages = listOf(
        "English", "Indonesia", "Spanish", "French", "German", "Chinese (Simplified)", 
        "Chinese (Traditional)", "Japanese", "Korean", "Arabic", "Russian", "Portuguese", 
        "Italian", "Hindi", "Bengali", "Urdu", "Turkish", "Vietnamese", "Thai", 
        "Dutch", "Polish", "Swedish", "Malay"
    ).sorted()

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 10.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .height(10.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                ExpressiveButton(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onComplete(nameInput.trim(), aiProvider, apiKeyInput.trim(), tempAiLanguage, tempAiTask, tempAiFormat)
                        }
                    },
                    enabled = when(pagerState.currentPage) {
                        1 -> nameInput.isNotBlank()
                        3 -> apiKeyInput.isNotBlank()
                        else -> true
                    },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    AnimatedContent(
                        targetState = pagerState.currentPage == 3,
                        label = "btn_text"
                    ) { isLastPage ->
                        if (isLastPage) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Get Started")
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Next")
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets
        
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false, // Memaksa user pakai tombol supaya flow-nya dijaga
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = safeTopMargin)
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (page) {
                    0 -> {
                        // Page 0: The Hook
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic, 
                                contentDescription = null, 
                                modifier = Modifier.size(64.dp), 
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Meet Binot.",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Your smart voice assistant that perfectly summarizes everything you say using advanced AI.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                    1 -> {
                        // Page 1: Identity
                        Text(
                            text = "What should I call you?",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Enter your name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    2 -> {
                        // Page 2: AI Preferences (The Tailoring)
                        Text(
                            text = "How should I process your notes?",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Language Selection
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
                                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Output Language", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(tempAiLanguage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Task Selection
                        Text("Task", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3), onClick = { tempAiTask = 0 }, selected = tempAiTask == 0) { Text("Tidy Up", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3), onClick = { tempAiTask = 1 }, selected = tempAiTask == 1) { Text("Summary", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3), onClick = { tempAiTask = 2 }, selected = tempAiTask == 2) { Text("Analyze", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Format Selection
                        Text("Format", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2), onClick = { tempAiFormat = 0 }, selected = tempAiFormat == 0) { Text("Paragraphs") }
                            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2), onClick = { tempAiFormat = 1 }, selected = tempAiFormat == 1) { Text("Bullets") }
                        }
                    }
                    3 -> {
                        // Page 3: API Key & Provider
                        Text(
                            text = "Unlock the Brain",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Binot needs a brain to think. Choose your favorite AI engine, 100% Free!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2), onClick = { aiProvider = 0 }, selected = aiProvider == 0) { Text("Gemini") }
                            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2), onClick = { aiProvider = 1 }, selected = aiProvider == 1) { Text("Groq") }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        AnimatedContent(targetState = aiProvider, label = "provider_info") { provider ->
                            if (provider == 1) {
                                Text(
                                    text = "Highly recommended for fast speed!",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "Great for summarizing massive audio files.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text(if (aiProvider == 0) "Paste Gemini API Key" else "Paste Groq API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Handholding Button
                        TextButton(onClick = { showApiTutorialSheet = true }) {
                            Text("How to get a free key?", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Modal untuk pilih bahasa (sama persis logikanya kaya di Settings)
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
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
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
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    // Tutorial Pintar untuk API Key (Handholding)
    if (showApiTutorialSheet) {
        ModalBottomSheet(
            onDismissRequest = { showApiTutorialSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (aiProvider == 0) "How to get Gemini Key" else "How to get Groq Key",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Step 1
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text("1", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Open the Developer Console", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            val url = if (aiProvider == 0) "https://aistudio.google.com/app/apikey" else "https://console.groq.com/keys"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }) {
                            Text(if (aiProvider == 0) "Open Google AI Studio" else "Open Groq Console")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Step 2
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text("2", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sign In", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("Log in using your existing Google account.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Step 3
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text("3", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Create & Copy", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("Find the 'Create API Key' button, copy the long text, and paste it back into this app.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
