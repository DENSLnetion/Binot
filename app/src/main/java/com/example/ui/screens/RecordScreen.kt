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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.components.AudioWaveform
import com.example.viewmodel.RecordViewModel

@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    onNavigateToResult: (Int) -> Unit
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Apa yang Anda pikirkan?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        if (isRecording) {
            AudioWaveform(
                amplitude = amplitude,
                modifier = Modifier.padding(vertical = 32.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(164.dp))
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
                text = if (recognizedText.isEmpty()) "Menunggu suara..." else recognizedText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type Note Button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            // Dummy write
                            viewModel.saveNote { expectedId ->
                                onNavigateToResult(expectedId)
                            }
                        }
                    }
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Tulis Teks",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Morphing Record Button
            var isPressed by remember { mutableStateOf(false) }
            val width by animateDpAsState(
                targetValue = if (isPressed) 160.dp else if (isRecording) 120.dp else 80.dp,
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
                        contentDescription = "Rekam",
                        tint = iconColor,
                        modifier = Modifier.size(36.dp)
                    )
                    if (width > 100.dp) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRecording) "Stop" else "Rekam",
                            color = iconColor,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}
