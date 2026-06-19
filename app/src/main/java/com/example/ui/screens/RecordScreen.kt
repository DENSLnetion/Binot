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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.components.AudioWaveform
import com.example.viewmodel.RecordViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    userName: String,
    onNavigateToResult: (Int) -> Unit
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val recordingSeconds by viewModel.recordingSeconds.collectAsState()

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
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Surface(
                shape = CircleShape,
                color = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isRecording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
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

        // Split morphing: saat recording → membelah jadi Pause + Stop
        // Saat idle → satu tombol Record panjang di tengah
        val isSplit = isRecording

        // Lebar total area tombol
        val totalAreaWidth = 280.dp

        // Animasi lebar masing-masing tombol
        val recordButtonWidth by animateDpAsState(
            targetValue = if (isSplit) 120.dp else totalAreaWidth,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "recordWidth"
        )
        val secondButtonWidth by animateDpAsState(
            targetValue = if (isSplit) 120.dp else 0.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "secondWidth"
        )
        val secondButtonAlpha by animateFloatAsState(
            targetValue = if (isSplit) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "secondAlpha"
        )

        Box(
            modifier = Modifier
                .width(totalAreaWidth)
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isSplit) 16.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                // Tombol kiri: saat idle = Record (full width), saat recording = Pause
                var isPausePressed by remember { mutableStateOf(false) }
                val pauseWidthPress by animateDpAsState(
                    targetValue = if (isPausePressed) recordButtonWidth + 8.dp else recordButtonWidth,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "pausePress"
                )

                Box(
                    modifier = Modifier
                        .width(pauseWidthPress)
                        .height(80.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSplit) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.primary
                        )
                        .pointerInput(isSplit) {
                            detectTapGestures(
                                onPress = {
                                    isPausePressed = true
                                    tryAwaitRelease()
                                    isPausePressed = false
                                    if (isSplit) {
                                        // Pause: belum ada fungsi pause, bisa di-extend nanti
                                        // Untuk sekarang pause = stop tapi tanpa save (stub)
                                    } else {
                                        // Tombol Record diklik saat idle → mulai recording
                                        if (!hasPermission) {
                                            launcher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            val isEmulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")
                                            viewModel.toggleRecording(isEmulator)
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isSplit) Icons.Default.Pause else Icons.Default.Mic,
                            contentDescription = if (isSplit) "Pause" else "Record",
                            tint = if (isSplit) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
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

                // Tombol kanan: Stop (hanya muncul saat recording)
                if (secondButtonWidth > 0.dp) {
                    var isStopPressed by remember { mutableStateOf(false) }
                    val stopWidthPress by animateDpAsState(
                        targetValue = if (isStopPressed) secondButtonWidth + 8.dp else secondButtonWidth,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "stopPress"
                    )

                    Box(
                        modifier = Modifier
                            .width(stopWidthPress)
                            .height(80.dp)
                            .alpha(secondButtonAlpha)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isStopPressed = true
                                        tryAwaitRelease()
                                        isStopPressed = false
                                        val isEmulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")
                                        viewModel.toggleRecording(isEmulator)
                                        viewModel.saveNote { expectedId ->
                                            onNavigateToResult(expectedId)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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

