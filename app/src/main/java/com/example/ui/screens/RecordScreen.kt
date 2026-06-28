package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.viewmodel.RecordViewModel
import com.example.ui.components.AudioWaveform
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    userName: String,
    recordMode: Int,
    snackbarHostState: SnackbarHostState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onNoteClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current 
    
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val recordingSeconds by viewModel.recordingSeconds.collectAsState()
    val recentNotes by viewModel.recentNotes.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    // State untuk kontrol morphing transkripsi
    var isTappedExpanded by remember { mutableStateOf(false) }
    var isPressExpanded by remember { mutableStateOf(false) }
    val isExpanded = isTappedExpanded || isPressExpanded

    // Easter egg state
    var greetingTapCount by remember { mutableStateOf(0) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var easterEggAnswer by remember { mutableStateOf("") }
    var showLovePopup by remember { mutableStateOf(false) }

    // State untuk Morphing Peeking Note
    var peekedNoteId by remember { mutableStateOf<Int?>(null) }
    
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
        "Live transcription is disabled."
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
            // Layer 0: Material 3 Expressive Background (Glow Only)
            M3ExpressiveBackground()

            // Layer 1: Main Content UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                    
                    val boxScale by animateFloatAsState(
                        targetValue = if (isPressExpanded) 0.97f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        label = "boxScale"
                    )

                    // Area Atas (Sapaan, Waktu, Dinamis: Waveform vs Recent Notes)
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
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .pointerInput(Unit) {
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
                                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
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

                        // Dynamic Area: 16 Terbaru VS Amplitudo
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isRecording || isPaused,
                                enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.8f)),
                                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f)
                            ) {
                                AudioWaveform(
                                    amplitude = amplitude,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isRecording && !isPaused && recentNotes.isNotEmpty(),
                                enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 50 }),
                                exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { 50 })
                            ) {
                                LazyHorizontalStaggeredGrid(
                                    rows = StaggeredGridCells.Fixed(2),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalItemSpacing = 12.dp,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp) 
                                ) {
                                    items(recentNotes, key = { it.id }) { note ->
                                        val displayTitle = if (note.title.isBlank()) "No title" else note.title
                                        val randomPadding = remember(note.id) { (note.id * 23 % 40).dp }
                                        val isPeeked = peekedNoteId == note.id

                                        // Animasi smooth yang santai, bebas efek mantul pegas
                                        val smoothAnimSpec = tween<Dp>(durationMillis = 400, easing = FastOutSlowInEasing)

                                        val animatedCornerRadius by animateDpAsState(
                                            targetValue = if (isPeeked) 16.dp else 32.dp,
                                            animationSpec = smoothAnimSpec,
                                            label = "corner"
                                        )

                                        with(sharedTransitionScope) {
                                            // OUTER BOX: Mesin Penembus Limit Grid (Z-Index Paling Atas & Unbounded)
                                            Box(
                                                modifier = Modifier
                                                    .zIndex(if (isPeeked) 10f else 0f) // Selalu paling atas pas peeking
                                                    .wrapContentSize(align = Alignment.Center, unbounded = true) // Biar bisa bebas nembus ke atas/bawah baris
                                            ) {
                                                // INNER BOX: Mesin Morphing Asli
                                                Box(
                                                    modifier = Modifier
                                                        .sharedBounds(
                                                            sharedContentState = rememberSharedContentState("record_note-${note.id}"),
                                                            animatedVisibilityScope = animatedVisibilityScope,
                                                            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
                                                            boundsTransform = { _, _ -> tween(400, easing = FastOutSlowInEasing) }
                                                        )
                                                        .clip(RoundedCornerShape(animatedCornerRadius))
                                                        .background(MaterialTheme.colorScheme.surface) 
                                                        .pointerInput(note.id) {
                                                            detectTapGestures(
                                                                onPress = {
                                                                    tryAwaitRelease()
                                                                    if (peekedNoteId == note.id) {
                                                                        peekedNoteId = null
                                                                    }
                                                                },
                                                                onLongPress = {
                                                                    peekedNoteId = note.id
                                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                },
                                                                onTap = {
                                                                    onNoteClick(note.id)
                                                                }
                                                            )
                                                        }
                                                        .animateContentSize(animationSpec = tween(400, easing = FastOutSlowInEasing)) // Animasi smooth lambat
                                                        // Trik Morphing Size
                                                        .then(
                                                            if (isPeeked) {
                                                                // Ukuran fix kotak yang presisi (ga bablas panjang)
                                                                Modifier.size(width = 260.dp, height = 140.dp) 
                                                            } else {
                                                                // Ukuran kapsul normal dinamis
                                                                Modifier.height(64.dp)
                                                            }
                                                        )
                                                        .padding(
                                                            horizontal = if (isPeeked) 20.dp else (32.dp + randomPadding),
                                                            // Kasih padding vertikal cuma pas peeking (karena kotak)
                                                            vertical = if (isPeeked) 16.dp else 0.dp 
                                                        ),
                                                    contentAlignment = if (isPeeked) Alignment.TopStart else Alignment.Center
                                                ) {
                                                    if (isPeeked) {
                                                        Column(modifier = Modifier.fillMaxWidth()) {
                                                            Text(
                                                                text = displayTitle,
                                                                style = MaterialTheme.typography.titleMedium,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            
                                                            val contentText = note.summary?.takeIf { it.isNotBlank() }?.replace(Regex("<!--BINOT_META:.*?-->"), "")?.trim() ?: note.rawText
                                                            val displayContent = if (contentText.isBlank() || contentText == "Pending Transcription") "Sedang memproses..." else contentText
                                                            
                                                            Text(
                                                                text = displayContent,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 3, 
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    } else {
                                                        Text(
                                                            text = displayTitle,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = MaterialTheme.colorScheme.secondaryContainer, 
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(0.5f)) 
                    }

                    // Morphing Box UTAMA (Engine Gestur Presisi)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(boxHeight)
                            .scale(boxScale)
                            .padding(horizontal = 24.dp) 
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

                // Area Tombol Perekaman
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
                        isLeftPressed            -> totalAreaWidth + 56.dp 
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

                    val leftButtonWidth by animateDpAsState(targetValue = leftTargetWidth, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "leftWidth")
                    val rightButtonWidth by animateDpAsState(targetValue = rightTargetWidth, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "rightWidth")
                    val rightButtonAlpha by animateFloatAsState(targetValue = if (isSplit) 1f else 0f, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "rightAlpha")
                    val gapWidth by animateDpAsState(targetValue = gapTarget, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "gap")
                    val leftIconScale by animateFloatAsState(targetValue = if (isLeftPressed && !isSplit) 1.12f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "leftIconScale")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gapWidth),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(leftButtonWidth)
                                .height(80.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSplit && !isPaused -> MaterialTheme.colorScheme.secondaryContainer
                                        isSplit && isPaused  -> MaterialTheme.colorScheme.primaryContainer
                                        else                 -> MaterialTheme.colorScheme.primary // Base Color
                                    }
                                )
                                .pointerInput(isSplit, isPaused) {
                                    detectTapGestures(
                                        onPress = {
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
                                Icon(
                                    imageVector = when {
                                        isSplit && isPaused -> Icons.Default.PlayArrow
                                        isSplit            -> Icons.Default.Pause
                                        else               -> Icons.Default.Mic
                                    },
                                    contentDescription = when {
                                        isSplit && isPaused -> "Resume"
                                        isSplit            -> "Pause"
                                        else               -> "Record"
                                    },
                                    tint = when {
                                        isSplit && !isPaused -> MaterialTheme.colorScheme.onSecondaryContainer
                                        isSplit && isPaused  -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else                 -> MaterialTheme.colorScheme.onPrimary
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .scale(leftIconScale)
                                )
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

                        if (rightButtonWidth > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .width(rightButtonWidth)
                                    .height(80.dp)
                                    .alpha(rightButtonAlpha)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
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

    // Easter Egg: Question Dialog
    if (showEasterEggDialog) {
        AlertDialog(
            onDismissRequest = {
                showEasterEggDialog = false
                easterEggAnswer = ""
            },
            title = {
                Text(
                    text = "✨ Secret Question",
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

    // Easter Egg: Love Popup
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
                            text = "In every line of code I write,\nin every bug I fix at night —\nit's always you I'm thinking of.\nYou are my favorite feature,\nmy most beautiful exception.\n\nForever yours,\nThe Developer 👨‍💻",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                    Text(
                        text = "💻 With all the love in the codebase 💻",
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
                    Text("✨ Close")
                }
            }
        )
    }
}

// ==========================================
// BACKGROUND RENDER ENGINE (MINIMALIST GLOW)
// ==========================================
@Composable
private fun M3ExpressiveBackground() {
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    val secondaryColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)

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
    }
}
