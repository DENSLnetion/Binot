package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val recordingSeconds by viewModel.recordingSeconds.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    var showLiveTextSheet by remember { mutableStateOf(false) }

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

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greetingTime = when (hour) {
        in 0..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val greeting = if (userName.isNotBlank()) "$greetingTime,\n$userName." else "$greetingTime!"

    val minutes = (recordingSeconds / 60).toString().padStart(2, '0')
    val seconds = (recordingSeconds % 60).toString().padStart(2, '0')
    val timeString = "$minutes:$seconds"

    val topInsets = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(topInsets + 40.dp))

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(
                text = greeting,
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
        }

        Spacer(modifier = Modifier.weight(1f))

        AudioWaveform(
            amplitude = amplitude,
            modifier = Modifier.padding(vertical = 32.dp)
        )

        val scrollState = rememberScrollState()
        LaunchedEffect(recognizedText) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { showLiveTextSheet = true }
                .padding(24.dp)
        ) {
            Text(
                text = if (recognizedText.isEmpty()) "Waiting for voice..." else recognizedText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // isSplit 
        val isSplit = isRecording || isPaused

        val totalAreaWidth = 280.dp

        Box(
            modifier = Modifier
                .widthIn(min = totalAreaWidth)
                .height(80.dp),
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

            val leftButtonWidth by animateDpAsState(
                targetValue = leftTargetWidth,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "leftWidth"
            )
            val rightButtonWidth by animateDpAsState(
                targetValue = rightTargetWidth,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "rightWidth"
            )
            val rightButtonAlpha by animateFloatAsState(
                targetValue = if (isSplit) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "rightAlpha"
            )
            val gapWidth by animateDpAsState(
                targetValue = gapTarget,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "gap"
            )
            val leftIconScale by animateFloatAsState(
                targetValue = if (isLeftPressed && !isSplit) 1.12f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "leftIconScale"
            )

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
                                                viewModel.toggleRecording(isEmulator)
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
                                            val saved = viewModel.saveNote()
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

        Spacer(modifier = Modifier.height(32.dp))
    } // end Column

    if (showLiveTextSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLiveTextSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Live Transcription",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (recognizedText.isEmpty()) "No words detected yet..." else recognizedText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

