package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val userName by viewModel.userName.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var nameInput by remember(userName) { mutableStateOf(userName) }
    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // --- LOGIKA BACKUP & RESTORE ---
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    // Launcher buat Create File (Export JSON)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { 
            viewModel.exportBackup(context, it) { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    // Launcher buat Open File (Import JSON)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importBackup(context, it) { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()), // Anti-gepeng
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Personalization",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Your Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { 
                            viewModel.saveUserName(nameInput) 
                            coroutineScope.launch { snackbarHostState.showSnackbar("Name saved successfully!") }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Name")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Gemini API Key",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Enter API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { 
                            viewModel.saveApiKey(keyInput) 
                            coroutineScope.launch { snackbarHostState.showSnackbar("API Key saved securely!") }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Key")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                            onClick = { viewModel.saveThemeMode(0) },
                            selected = themeMode == 0
                        ) { Text("Auto") }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                            onClick = { viewModel.saveThemeMode(1) },
                            selected = themeMode == 1
                        ) { Text("Light") }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                            onClick = { viewModel.saveThemeMode(2) },
                            selected = themeMode == 2
                        ) { Text("Dark") }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                            onClick = { viewModel.saveThemeMode(3) },
                            selected = themeMode == 3
                        ) {
                            Text(
                                text = "Amoled",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp 
                            )
                        }
                    }
                }
            }

            // KARTU BARU: Data & System (Backup & Update)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Data & System",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Backup & Restore
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notes Backup", style = MaterialTheme.typography.bodyLarge)
                        Row {
                            OutlinedButton(
                                onClick = { 
                                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) 
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            ) { Text("Restore") }
                            Button(onClick = { 
                                val fileName = "Binot_Backup_${formatter.format(Date())}.json"
                                exportLauncher.launch(fileName)
                            }) { Text("Backup") }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Version & Update
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("App Version", style = MaterialTheme.typography.bodyLarge)
                            Text("v1.0.0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Button(onClick = { 
                            // Lempar user ke halaman releases GitHub repo lu
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aistudio-id/Binot/releases"))
                            context.startActivity(intent)
                        }) { Text("Check Update") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

