package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    // PERBAIKAN: Bungkus seluruh layar pakai verticalScroll biar UI ga gepeng di layar HP kecil
    val mainScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(mainScrollState) 
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

        // Pakai Spacer minimum biar Waveform ga nempel banget kalau teks di atasnya panjang
        Spacer(modifier = Modifier.heightIn(min = 32.dp))

        AudioWaveform(
            amplitude = amplitude,
            modifier = Modifier.padding(vertical = 32.dp)
        )

        val textScrollState = rememberScrollState()
        LaunchedEffect(recognizedText) {
            textScrollState.animateScrollTo(textScrollState.maxValue)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(24.dp)
        ) {
            Text(
                text = if (recognizedText.isEmpty()) "Waiting for voice..." else recognizedText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start, 
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(textScrollState)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showLiveTextSheet = true
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Live View",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            var isPressed by remember { mutableStateOf(false) }
            val width by animateDpAsState(
                targetValue = if (isPressed) 200.dp else if (isRecording) 180.dp else 140.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "buttonWidth"
            )

            val buttonColor = if (isRecording) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            }
            
            val iconColor = if (isRecording) {
                MaterialTheme.colorScheme.onTertiary
            } else {
                MaterialTheme.colorScheme.onPrimary
            }

            Box(
                modifier = Modifier
                    .width(width)
                    .height(80.dp)
                    .clip(CircleShape) 
                    .background(buttonColor)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                                
                                if (!hasPermission) {
                                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    val isEmulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")
                                    if (isRecording) {
                                        viewModel.toggleRecording(isEmulator)
                                        viewModel.saveNote { expectedId ->
                                            onNavigateToResult(expectedId)
                                        }
                                    } else {
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
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Record",
                        tint = iconColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isRecording) "Stop" else "Record",
                        color = iconColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
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


