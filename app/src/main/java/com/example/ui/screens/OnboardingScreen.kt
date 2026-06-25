package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Content
import com.example.data.GenerateContentRequest
import com.example.data.GroqChatRequest
import com.example.data.GroqMessage
import com.example.data.Part
import com.example.data.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.UnknownHostException
import java.net.ConnectException
import java.net.SocketTimeoutException

enum class KeyVerificationState {
    IDLE, LOADING, SUCCESS, ERROR
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: (String, Int, String, Int, Int) -> Unit // name, aiProvider, apiKey, aiTask, aiFormat
) {
    // 5 pages: Intro, Name, Preferences, API Key Input, Verification Result Screen
    val pagerState = rememberPagerState(pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var nameInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var aiProvider by remember { mutableStateOf(1) } // Default to Groq (1)
    
    // AI Preferences State
    var aiTask by remember { mutableStateOf(0) } // 0: Tidy Up, 1: Summary, 2: Analyze
    var aiFormat by remember { mutableStateOf(0) } // 0: Paragraphs, 1: Bullets

    // Real-time API Key Verification State
    var keyState by remember { mutableStateOf(KeyVerificationState.IDLE) }
    var keyErrorMessage by remember { mutableStateOf("") }

    val isFinalPage = pagerState.currentPage == 4

    Scaffold(
        bottomBar = {
            // Hide standard bottom bar on the final verification screen
            AnimatedVisibility(
                visible = !isFinalPage,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dot Indicators (Only show 4 dots representing steps before result)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) { index ->
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            )
                        }
                    }

                    // Next / Verify Button
                    Button(
                        onClick = {
                            if (pagerState.currentPage < 3) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else if (pagerState.currentPage == 3) {
                                if (apiKeyInput.isBlank()) return@Button
                                
                                coroutineScope.launch {
                                    // Move to Verification Screen first, then load
                                    pagerState.animateScrollToPage(4)
                                    keyState = KeyVerificationState.LOADING
                                    
                                    try {
                                        if (aiProvider == 0) {
                                            val req = GenerateContentRequest(
                                                contents = listOf(Content(parts = listOf(Part(text = "hi"))))
                                            )
                                            RetrofitClient.service.generateContent(apiKeyInput.trim(), req)
                                        } else {
                                            val req = GroqChatRequest(
                                                model = "llama3-8b-8192",
                                                messages = listOf(GroqMessage(role = "user", content = "hi"))
                                            )
                                            RetrofitClient.groqService.generateContent("Bearer ${apiKeyInput.trim()}", req)
                                        }
                                        
                                        // Wait a moment for UX smoothness before showing success
                                        delay(800)
                                        keyState = KeyVerificationState.SUCCESS
                                        
                                    } catch (e: Exception) {
                                        delay(800)
                                        keyState = KeyVerificationState.ERROR
                                        keyErrorMessage = when (e) {
                                            is HttpException -> when (e.code()) {
                                                401 -> "The key you entered is invalid. Please ensure there are no missing characters or accidental spaces."
                                                403 -> "Access denied. Your key might be restricted, or you need to accept the provider's terms of service."
                                                429 -> "Rate limit exceeded. The provider's server is busy or you have reached your usage quota."
                                                else -> "Server rejected the connection (Code: ${e.code()}). Please check your key and try again."
                                            }
                                            is UnknownHostException, is ConnectException, is SocketTimeoutException -> 
                                                "We couldn't reach the server. Please check your internet connection and try again."
                                            else -> "An unexpected error occurred. Please verify your API key and try again."
                                        }
                                    }
                                }
                            }
                        },
                        enabled = when(pagerState.currentPage) {
                            1 -> nameInput.isNotBlank()
                            3 -> apiKeyInput.isNotBlank()
                            else -> true
                        },
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 3) "Verify Key" else "Next",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets
        
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false, // Force users to interact with buttons, no manual swiping allowed
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = safeTopMargin)
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (page) {
                    0 -> {
                        Text(
                            text = "Welcome to Binot.",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Your intelligent voice workspace. Speak naturally, and let advanced AI structure your thoughts perfectly.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                    1 -> {
                        Text(
                            text = "Let's get acquainted.",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "What should we call you?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Enter your name") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    2 -> {
                        Text(
                            text = "Tailor your experience.",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "How would you like Binot to process your voice by default? You can change this later.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Text(
                            text = "Processing Task",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                onClick = { aiTask = 0 },
                                selected = aiTask == 0
                            ) { Text("Tidy Up", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                onClick = { aiTask = 1 },
                                selected = aiTask == 1
                             ) { Text("Summary", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                onClick = { aiTask = 2 },
                                selected = aiTask == 2
                            ) { Text("Analyze", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Output Format",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                onClick = { aiFormat = 0 },
                                selected = aiFormat == 0
                            ) { Text("Paragraphs") }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                onClick = { aiFormat = 1 },
                                selected = aiFormat == 1
                            ) { Text("Bullets") }
                         }
                    }
                    3 -> {
                        Text(
                            text = "Connect the brain.",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Binot needs an AI engine to operate. Choose a provider and claim your free access key.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                onClick = { aiProvider = 0 },
                                selected = aiProvider == 0
                            ) { Text("Google Gemini") }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                onClick = { aiProvider = 1 },
                                selected = aiProvider == 1
                            ) { Text("Groq AI") }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        AnimatedContent(targetState = aiProvider, label = "provider_info") { provider ->
                            if (provider == 1) {
                                Text(
                                    text = "Groq is highly recommended for its blazing fast speed.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "Gemini is great for processing very long audio recordings.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = {
                                val url = if (aiProvider == 0) "https://aistudio.google.com/app/apikey" else "https://console.groq.com/keys"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Get Free API Key",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("Paste your API Key here") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    4 -> {
                        // The Dedicated Verification Screen
                        AnimatedContent(
                            targetState = keyState, 
                            label = "verification_state",
                            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }
                        ) { state ->
                            when (state) {
                                KeyVerificationState.IDLE, KeyVerificationState.LOADING -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(72.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 6.dp
                                        )
                                        Spacer(modifier = Modifier.height(32.dp))
                                        Text(
                                            text = "Connecting to ${if (aiProvider == 0) "Gemini" else "Groq"}...",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Verifying your access key. This will only take a moment.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                KeyVerificationState.SUCCESS -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(96.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Success",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(32.dp))
                                        Text(
                                            text = "Connection Established",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Your API key is active. The AI engine is fully configured and ready to organize your thoughts.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(48.dp))
                                        
                                        // Capsule Button
                                        Button(
                                            onClick = { onComplete(nameInput.trim(), aiProvider, apiKeyInput.trim(), aiTask, aiFormat) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(64.dp),
                                            shape = CircleShape,
                                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                                        ) {
                                            Text(
                                                text = "Start Workspace",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                KeyVerificationState.ERROR -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(96.dp)
                                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Error,
                                                contentDescription = "Error",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(32.dp))
                                        Text(
                                            text = "Connection Failed",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = keyErrorMessage,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(48.dp))
                                        
                                        // Back Button
                                        OutlinedButton(
                                            onClick = { 
                                                coroutineScope.launch { 
                                                    pagerState.animateScrollToPage(3) 
                                                    keyState = KeyVerificationState.IDLE
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = "Review API Key",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
