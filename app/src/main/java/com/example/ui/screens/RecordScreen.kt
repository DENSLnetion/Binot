package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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

    // Smart & Dynamic Greeting dengan Bold Username
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greetings = remember(hour) {
        when (hour) {
            in 5..11 -> listOf("Ready to focus,", "Start strong,", "Morning logic,")
            in 12..16 -> listOf("Keep the momentum,", "Afternoon grind,", "Stay sharp,")
            in 17..20 -> listOf("Wrap up the day,", "Evening reflection,", "Clear your mind,")
            else -> listOf("Midnight thoughts,", "Still going,", "Nightcap,")
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
        if (recognizedText.isEmpty()) "Waiting for voice..." else recognizedText
    }

    with(animatedVisibilityScope) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(safeTopMargin + 24.dp))

            // Menggunakan Box agar elemen atas dan bawah saling tumpuk (overlap)
            // Ini mencegah layout atas dipaksa menjadi 0 height yang menyebabkan Force Close.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Fills remaining space
            ) {
                val availableHeight = maxHeight
                
                // State Animasi Morphing
                val boxHeight by animateDpAsState(
                    targetValue = if (isExpanded) availableHeight else 160.dp,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                    label = "boxHeight"
                )
                val topAlpha by animateFloatAsState(
                    targetValue = if (isExpanded) 0f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "topAlpha"
                )
                val cornerRadius by animateDpAsState(
                    targetValue = if (isExpanded) 40.dp else 32.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "cornerRadius"
                )
                val containerColor by animateColorAsState(
                    targetValue = if (isExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "containerColor"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "contentColor"
                )

                // 1. Area Atas: Selalu dibatasi ukurannya agar AudioWaveform tidak pernah tergencet layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 176.dp) // Sisakan ruang pasti (160dp box + 16dp spacing)
                        .alpha(topAlpha)
                        .animateEnterExit(enter = slideInVertically { -50 } + fadeIn()),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = greetingText,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                            isRecording -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                isPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                                isRecording -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    AudioWaveform(
                        amplitude = amplitude,
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .animateEnterExit(enter = fadeIn())
                    )
                }

                // 2. Morphing Box: Memanjang dari dasar Box ke atas menutupi layout belakang
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(boxHeight)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(containerColor)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    val startTime = System.currentTimeMillis()
                                    isPressExpanded = true
                                    
                                    val released = tryAwaitRelease()
                                    isPressExpanded = false
                                    
                                    if (released) {
                                        // Cek apakah ketukan ini cepat (tap) atau lama (hold)
                                        if (System.currentTimeMillis() - startTime < 300) {
                                            isTappedExpanded = !isTappedExpanded
                                        } else {
                                            isTappedExpanded = false 
                                        }
                                    }
                                }
                            )
                        }
                        .padding(24.dp)
                ) {
                    val scrollState = rememberScrollState()
                    
                    LaunchedEffect(recognizedText, isExpanded) {
                        // Scroll otomatis berjalan saat expanded untuk mencegah UI lompat-lompat
                        if (isExpanded && recognizedText.isNotEmpty()) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
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

                        Text(
                            text = displayLiveText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxSize()
                        )
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
                                    else                 -> MaterialTheme.colorScheme.primary
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
