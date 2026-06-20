package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.UpdateState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Wrapper tipis di atas Button/OutlinedButton M3 bawaan: nambahin animasi "mengkerut"
// pas ditekan, pakai spring bouncy yang sama persis kayak tombol Record/capsule lain
// di RecordScreen & ResultScreen — biar seluruh tombol capsule di app berasa konsisten.
// Tetap pakai Button/OutlinedButton asli (bukan Box custom) jadi ripple, enabled-state,
// dan shape default M3 gak berubah — cuma nambah scale animation di atasnya.
@Composable
private fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "buttonBounce"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale),
        content = content
    )
}

@Composable
private fun BouncyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "outlinedButtonBounce"
    )
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val userName by viewModel.userName.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    
    val updateState by viewModel.updateState.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val latestVersionStr by viewModel.latestVersionStr.collectAsState()

    var nameInput by remember(userName) { mutableStateOf(userName) }
    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    val currentVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0" } catch (e: Exception) { "1.0.0" }
    }
    
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportBackup(context, it) { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } } }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackup(context, it) { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } } }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        val topInsets = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(topInsets + 4.dp))

            Text(
                text = "Settings", 
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Personalization", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Your Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    BouncyButton(onClick = { viewModel.saveUserName(nameInput); coroutineScope.launch { snackbarHostState.showSnackbar("Name saved successfully!") } }, modifier = Modifier.align(Alignment.End)) {
                        Text("Save Name")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Gemini API Key", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = keyInput, onValueChange = { keyInput = it }, label = { Text("Enter API Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    BouncyButton(onClick = { viewModel.saveApiKey(keyInput); coroutineScope.launch { snackbarHostState.showSnackbar("API Key saved securely!") } }, modifier = Modifier.align(Alignment.End)) {
                        Text("Save Key")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4), onClick = { viewModel.saveThemeMode(0) }, selected = themeMode == 0) { Text("Auto") }
                        SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4), onClick = { viewModel.saveThemeMode(1) }, selected = themeMode == 1) { Text("Light") }
                        SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4), onClick = { viewModel.saveThemeMode(2) }, selected = themeMode == 2) { Text("Dark") }
                        SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4), onClick = { viewModel.saveThemeMode(3) }, selected = themeMode == 3) { Text("Amoled", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Data & System", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Notes Backup", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.End) {
                            BouncyOutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.padding(end = 8.dp)) { Text("Restore") }
                            BouncyButton(onClick = { exportLauncher.launch("Binot_Backup_${formatter.format(Date())}.json") }) { Text("Backup") }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("App Version", style = MaterialTheme.typography.bodyLarge)
                            Text("v$currentVersion", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            
                            if (updateState == UpdateState.Downloading) {
                                val animatedProgress by animateFloatAsState(targetValue = downloadProgress / 100f, label = "progress")
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small), color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Text("Downloading... $downloadProgress%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else if (updateState == UpdateState.Available) {
                                Text("New version ready: $latestVersionStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else if (updateState == UpdateState.Error) {
                                // FIX: Nampilin pesan error yang sesungguhnya (Biar gampang di debug)
                                Text("Failed: $latestVersionStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            } else if (updateState == UpdateState.Idle && latestVersionStr.isNotBlank()) {
                                Text("App is up to date.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        AnimatedContent(targetState = updateState, label = "update_btn") { state ->
                            when (state) {
                                UpdateState.Idle -> BouncyButton(onClick = { viewModel.checkForUpdate(currentVersion) }) { Text("Check Update") }
                                UpdateState.Checking -> Button(onClick = {}, enabled = false) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                                UpdateState.Available -> BouncyButton(onClick = { viewModel.startDownload(context) }) { Text("Update App") }
                                UpdateState.Downloading -> OutlinedButton(onClick = {}) { Text("Downloading") }
                                UpdateState.Downloaded -> BouncyButton(onClick = { viewModel.promptInstall(context) }) { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("Install") }
                                UpdateState.Error -> BouncyOutlinedButton(onClick = { viewModel.checkForUpdate(currentVersion) }) { Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("Retry") }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
