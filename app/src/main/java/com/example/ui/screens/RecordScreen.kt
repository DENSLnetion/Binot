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

    // Scope di level Composable utama (BUKAN di dalam Box tombol Stop yang kondisional),
    // supaya coroutine showSnackbar tidak ikut ter-cancel ketika tombol Stop collapse
    // (rightButtonWidth -> 0.dp) dan Box-nya hilang dari composition.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

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

        // isSplit = true selama recording ATAU paused (tombol udah membelah, belum balik)
        val isSplit = isRecording || isPaused

        val totalAreaWidth = 280.dp

        val leftButtonWidth by animateDpAsState(
            targetValue = if (isSplit) 120.dp else totalAreaWidth,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "leftWidth"
        )
        val rightButtonWidth by animateDpAsState(
            targetValue = if (isSplit) 120.dp else 0.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "rightWidth"
        )
        val rightButtonAlpha by animateFloatAsState(
            targetValue = if (isSplit) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "rightAlpha"
        )
        val gapWidth by animateDpAsState(
            targetValue = if (isSplit) 16.dp else 0.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "gap"
        )

        Box(
            modifier = Modifier
                .width(totalAreaWidth)
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(gapWidth),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth()
            ) {
                // State tekan untuk kedua tombol dideklarasikan di sini (sebelum dipakai
                // kedua Box) supaya masing-masing bisa menyusut saat tombol sebelahnya
                // sedang ditekan-lama (morph simetris).
                var isLeftPressed by remember { mutableStateOf(false) }
                var isStopPressed by remember { mutableStateOf(false) }

                // Idle: morph lebih panjang & kerasa (efek "tekan/tahan" murni visual, tap biasa tetap jalan).
                // Diperbesar supaya morphing-nya intuitif kayak transisi ke Pause, bukan cuma geser dikit.
                val leftPressExtra by animateDpAsState(
                    targetValue = when {
                        isLeftPressed && isSplit -> 32.dp
                        isLeftPressed            -> 56.dp
                        else                     -> 0.dp
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "leftPress"
                )
                // Icon sedikit membesar pas ditekan, biar morphing kerasa "hidup" bukan cuma lebar Box-nya doang
                val leftIconScale by animateFloatAsState(
                    targetValue = if (isLeftPressed && !isSplit) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "leftIconScale"
                )
                val stopPressExtra by animateDpAsState(
                    targetValue = if (isStopPressed) 32.dp else 0.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "stopPress"
                )

                // Tombol KIRI: idle=Record, recording=Pause, paused=Resume
                Box(
                    modifier = Modifier
                        .width((leftButtonWidth + leftPressExtra - stopPressExtra).coerceAtLeast(64.dp))
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
                                            // Mulai recording
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

                // Tombol KANAN: Stop — hanya tampil saat split
                if (rightButtonWidth > 0.dp) {
                    Box(
                        modifier = Modifier
                            .width((rightButtonWidth - leftPressExtra + stopPressExtra).coerceAtLeast(64.dp))
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

                                        // PENTING: hentikan recording SEKETIKA dulu (tanpa nunggu apa pun)
                                        // supaya isRecording/isSplit langsung berubah dan animasi morph
                                        // tombol mulai jalan mulus, persis di momen jari dilepas.
                                        // saveNote() (yang ada delay(300) + insert DB) dijalankan terpisah
                                        // di coroutine sendiri, gak ngeblock animasi.
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

