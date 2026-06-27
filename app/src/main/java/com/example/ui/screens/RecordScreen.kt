package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.components.AudioWaveform
import com.example.viewmodel.RecordViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    userName: String,
    recordMode: Int,
    snackbarHostState: SnackbarHostState,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current // Injeksi Haptic Engine
    
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val recordingSeconds by viewModel.recordingSeconds.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    // State untuk kontrol morphing
    var isTappedExpanded by remember { mutableStateOf(false) }
    var isPressExpanded by remember { mutableStateOf(false) }
    val isExpanded = isTappedExpanded || isPressExpanded

    // Easter egg state
    var greetingTapCount by remember { mutableStateOf(0) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var easterEggAnswer by remember { mutableStateOf("") }
    var showLovePopup by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // Smart & Dynamic Greeting
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greetings = remember(hour) {
        when (hour) {
            in 5..11 -> listOf("Good morning,", "Rise and shine,", "A fresh start,", "Morning inspiration,", "Start your day right,")
            in 12..16 -> listOf("Good afternoon,", "Midday thoughts,", "Keep it going,", "Stay productive,", "Afternoon check-in,")
            in 17..20 -> listOf("Good evening,", "Winding down,", "Evening reflection,", "Time to relax,", "Sunset thoughts,")
            else -> listOf("Late night thoughts,", "Midnight notes,", "Still awake?,", "Quiet hours,", "Rest well,")
        }
    }
    val randomGreeting = remember(hour) { greetings.random() }

    val greetingText = buildAnnotatedString {
        append("$randomGreeting\n")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(if (userName.isNotBlank()) userName else "Guest")
        }
        append(".")
    }

    val minutes = (recordingSeconds / 60).toString().padStart(2, '0')
    val seconds = (recordingSeconds % 60).toString().padStart(2, '0')
    val timeString = "$minutes:$seconds"

    val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

    val displayLiveText = if (recordMode == 1) {
        "Direct recording mode is active.\nLive transcription is disabled."
    } else {
        if (recognizedText.isEmpty()) "Waiting for voice input..." else recognizedText
    }

    val scrollState = rememberScrollState()

    with(animatedVisibilityScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            // Layer 0: Material 3 Expressive Background
            M3ExpressiveBackground(isRecording = isRecording, isExpanded = isExpanded)

            // Layer 1: Main Content UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    // Tap luar untuk tutup
                    .pointerInput(isExpanded) {
                        if (isExpanded) {
                            detectTapGestures(
                                onTap = {
                                    isTappedExpanded = false
                                    isPressExpanded = false
                                }
                            )
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(safeTopMargin + 24.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val availableHeight = maxHeight
                    
                    // FISIKA PEGAS: Damping 0.9f biar ga mental (wobble), Stiffness medium biar berat dan solid.
                    val stiffSpring = spring<Dp>(dampingRatio = 0.9f, stiffness = 400f)
                    
                    val boxHeight by animateDpAsState(
                        targetValue = if (isExpanded) availableHeight else 160.dp,
                        animationSpec = stiffSpring,
                        label = "boxHeight"
                    )
                    val topAlpha by animateFloatAsState(
                        targetValue = if (isExpanded) 0f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "topAlpha"
                    )
                    val cornerRadius by animateDpAsState(
                        targetValue = if (isExpanded) 40.dp else 32.dp,
                        animationSpec = stiffSpring,
                        label = "cornerRadius"
                    )
                    val containerColor by animateColorAsState(
                        targetValue = if (isExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "containerColor"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "contentColor"
                    )
                    
                    // Elevasi Haptic & Layering
                    val boxElevation by animateDpAsState(
                        targetValue = if (isExpanded) 12.dp else if (isPressExpanded) 4.dp else 2.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "boxElevation"
                    )
                    val boxScale by animateFloatAsState(
                        targetValue = if (isPressExpanded) 0.97f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        label = "boxScale"
                    )

                    // Area Atas (Sapaan & Waveform)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 176.dp)
                            .alpha(topAlpha)
                            .animateEnterExit(enter = slideInVertically { -50 } + fadeIn()),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        greetingTapCount++
                                        if (greetingTapCount > 4) {
                                            greetingTapCount = 0
                                            showEasterEggDialog = true
                                            easterEggAnswer = ""
                                        }
                                    }
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                                isRecording -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            AnimatedContent(targetState = timeString, label = "timeAnimation") { time ->
                                Text(
                                    text = time,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        isPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                                        isRecording -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        AudioWaveform(
                            amplitude = amplitude,
                            modifier = Modifier
                                .padding(vertical = 16.dp)
                                .animateEnterExit(enter = fadeIn())
                        )
                    }

                    // Morphing Box UTAMA (Engine Gestur Presisi)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(boxHeight)
                            .scale(boxScale)
                            .shadow(
                                elevation = boxElevation,
                                shape = RoundedCornerShape(cornerRadius),
                                spotColor = MaterialTheme.colorScheme.primary,
                                ambientColor = MaterialTheme.colorScheme.primary
                            )
                            .clip(RoundedCornerShape(cornerRadius))
                            .background(containerColor)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (!isExpanded && dragAmount < -5) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isTappedExpanded = true
                                    }
                                }
                            }
                            .pointerInput("tap", isTappedExpanded) {
                                if (!isTappedExpanded) {
                                    detectTapGestures(
                                        onTap = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isTappedExpanded = true
                                        }
                                    )
                                }
                            }
                            .pointerInput("hold", isTappedExpanded) {
                                if (!isTappedExpanded) {
                                    detectTapGestures(
                                        onPress = {
                                            isPressExpanded = true
                                            tryAwaitRelease()
                                            isPressExpanded = false
                                        }
                                    )
                                }
                            }
                            .padding(top = 8.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
                    ) {
                        LaunchedEffect(recognizedText, isExpanded) {
                            if (recognizedText.isNotEmpty()) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // DRAG HANDLE
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable(
                                        enabled = isExpanded,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isTappedExpanded = false
                                        isPressExpanded = false
                                    }
                                    .pointerInput(isExpanded) {
                                        if (isExpanded) {
                                            detectVerticalDragGestures { _, dragAmount ->
                                                if (dragAmount > 5) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    isTappedExpanded = false
                                                    isPressExpanded = false
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(contentColor.copy(alpha = 0.3f))
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column {
                                    Text(
                                        text = "Live Transcription",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            // Teks dengan transisi Crossfade
                            AnimatedContent(
                                targetState = displayLiveText,
                                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                                label = "TranscriptionFade"
                            ) { text ->
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = contentColor,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState, enabled = isExpanded) 
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Area Tombol Perekaman dengan injeksi haptic dan micro-interaction
                val isSplit = isRecording || isPaused
                val totalAreaWidth = 280.dp

                Box(
                    modifier = Modifier
                        .widthIn(min = totalAreaWidth)
                        .height(80.dp)
                        .animateEnterExit(enter = scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))),
                    contentAlignment = Alignment.Center
                ) {
                    var isLeftPressed by remember { mutableStateOf(false) }
                    var isStopPressed by remember { mutableStateOf(false) }

                    val leftTargetWidth = when {
                        isStopPressed && isSplit -> 88.dp 
                        isLeftPressed && isSplit -> 152.dp  
                        isLeftPressed            -> totalAreaWidth + 24.dp // Mengurangi melar biar lebih padat/stiff
                        isSplit                  -> 120.dp
                        else                     -> totalAreaWidth
                    }
                    val rightTargetWidth = when {
                        !isSplit                  -> 0.dp
                        isStopPressed              -> 152.dp 
                        isLeftPressed               -> 88.dp  
                        else                        -> 120.dp
                    }
                    val gapTarget = if (isSplit) 16.dp else 0.dp

                    val buttonSpring = spring<Dp>(dampingRatio = 0.8f, stiffness = 400f)

                    val leftButtonWidth by animateDpAsState(targetValue = leftTargetWidth, animationSpec = buttonSpring, label = "leftWidth")
                    val rightButtonWidth by animateDpAsState(targetValue = rightTargetWidth, animationSpec = buttonSpring, label = "rightWidth")
                    val rightButtonAlpha by animateFloatAsState(targetValue = if (isSplit) 1f else 0f, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "rightAlpha")
                    val gapWidth by animateDpAsState(targetValue = gapTarget, animationSpec = buttonSpring, label = "gap")
                    
                    // Micro-interaction Scale
                    val leftScale by animateFloatAsState(targetValue = if (isLeftPressed) 0.92f else 1f, animationSpec = spring(stiffness = Spring.StiffnessHigh), label = "leftScale")
                    val rightScale by animateFloatAsState(targetValue = if (isStopPressed) 0.92f else 1f, animationSpec = spring(stiffness = Spring.StiffnessHigh), label = "rightScale")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gapWidth),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        // Tombol Play/Pause/Record
                        Box(
                            modifier = Modifier
                                .width(leftButtonWidth)
                                .height(80.dp)
                                .scale(leftScale)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSplit && !isPaused -> MaterialTheme.colorScheme.secondaryContainer
                                        isSplit && isPaused  -> MaterialTheme.colorScheme.primaryContainer
                                        else                 -> MaterialTheme.colorScheme.primary
                                    }
                                )
                                .pointerInput(isSplit, isPaused) {
                                    detectTapGestures(
                                        onPress = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isLeftPressed = true
                                            tryAwaitRelease()
                                            isLeftPressed = false
                                            when {
                                                !isSplit -> {
                                                    if (!hasPermission) {
                                                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                                                    } else {
                                                        val isEmulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")
                                                        viewModel.toggleRecording(isEmulator, recordMode)
                                                    }
                                                }
                                                isPaused -> viewModel.resumeRecording()
                                                else     -> viewModel.pauseRecording()
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedContent(
                                    targetState = isSplit to isPaused,
                                    label = "iconAnim"
                                ) { (split, paused) ->
                                    Icon(
                                        imageVector = when {
                                            split && paused -> Icons.Default.PlayArrow
                                            split            -> Icons.Default.Pause
                                            else               -> Icons.Default.Mic
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            split && !paused -> MaterialTheme.colorScheme.onSecondaryContainer
                                            split && paused  -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else                 -> MaterialTheme.colorScheme.onPrimary
                                        },
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                if (!isSplit) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Record",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }

                        // Tombol Stop
                        if (rightButtonWidth > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .width(rightButtonWidth)
                                    .height(80.dp)
                                    .scale(rightScale)
                                    .alpha(rightButtonAlpha)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                isStopPressed = true
                                                tryAwaitRelease()
                                                isStopPressed = false

                                                viewModel.stopRecordingInstant()
                                                coroutineScope.launch {
                                                    val saved = viewModel.saveNote(recordMode)
                                                    snackbarHostState.showSnackbar(
                                                        message = if (saved) "Note saved" else "No text to save",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isStopPressed && recordMode == 1) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Easter Egg: Question Dialog (Unchanged)
    if (showEasterEggDialog) {
        AlertDialog(
            onDismissRequest = {
                showEasterEggDialog = false
                easterEggAnswer = ""
            },
            title = {
                Text(
                    text = "🔐 Secret Question",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Who is the developer's sweetheart?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = easterEggAnswer,
                        onValueChange = { if (it.length <= 5) easterEggAnswer = it },
                        singleLine = true,
                        placeholder = { Text("Your answer...") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (easterEggAnswer.trim().equals("dinda", ignoreCase = true)) {
                            showEasterEggDialog = false
                            showLovePopup = true
                        }
                        easterEggAnswer = ""
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEasterEggDialog = false
                    easterEggAnswer = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Easter Egg: Love Popup (Unchanged)
    if (showLovePopup) {
        AlertDialog(
            onDismissRequest = { showLovePopup = false },
            title = null,
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(text = "💖", style = MaterialTheme.typography.displayMedium)
                    Text(
                        text = "For Dinda",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "In every line of code I write,\nin every bug I fix at night —\nit's always you I'm thinking of.\nYou are my favorite feature,\nmy most beautiful exception.\n\nForever yours,\nThe Developer 🌸",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                    Text(
                        text = "✨ With all the love in the codebase ✨",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = { showLovePopup = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🌹 Close")
                }
            }
        )
    }
}

// ==========================================
// BACKGROUND RENDER ENGINE (MATHEMATIKA MURNI)
// ==========================================
@Composable
private fun M3ExpressiveBackground(isRecording: Boolean, isExpanded: Boolean) {
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    val secondaryColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)

    // Animasi rotasi amoeba sangat pelan (20 detik per putaran), jalan waktu rekaman aja
    val infiniteTransition = rememberInfiniteTransition(label = "amoebaRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isRecording) 360f else 0f, // Muter kalau record
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Parallax effect buat kapsul memanjang waktu box ditarik
    val pillParallaxOffset by animateFloatAsState(
        targetValue = if (isExpanded) 150f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "parallax"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Ambient Glow (Gradiasi cahaya di latar murni)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor, Color.Transparent),
                center = Offset(w * 0.5f, h * 0.2f),
                radius = w * 0.8f
            ),
            center = Offset(w * 0.5f, h * 0.2f),
            radius = w * 0.8f
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryColor, Color.Transparent),
                center = Offset(w * 0.2f, h * 0.7f),
                radius = w * 0.7f
            ),
            center = Offset(w * 0.2f, h * 0.7f),
            radius = w * 0.7f
        )

        // 2. The Elongated Pill (Kiri Bawah, Parallax)
        translate(left = -w * 0.1f, top = h * 0.6f + pillParallaxOffset) {
            rotate(degrees = 35f, pivot = Offset(0f, 0f)) {
                drawRoundRect(
                    color = primaryContainer,
                    topLeft = Offset(0f, 0f),
                    size = Size(w * 0.8f, h * 0.18f),
                    cornerRadius = CornerRadius(h * 0.09f, h * 0.09f)
                )
            }
        }

        // 3. The Amoeba (Kanan Atas, muter pelan, Bezier murni)
        val path = Path().apply {
            val scale = w * 0.45f
            moveTo(scale, 0f)
            cubicTo(scale * 1.5f, 0f, scale * 2f, scale * 0.5f, scale * 1.8f, scale)
            cubicTo(scale * 1.5f, scale * 1.5f, scale * 0.5f, scale * 1.8f, 0f, scale * 1.2f)
            cubicTo(-scale * 0.5f, scale * 0.5f, 0f, 0f, scale, 0f)
            close()
        }

        translate(left = w * 0.7f, top = -h * 0.05f) {
            rotate(degrees = rotation, pivot = Offset(w * 0.3f, w * 0.3f)) {
                drawPath(
                    path = path,
                    color = tertiaryContainer
                )
            }
        }
    }
}
