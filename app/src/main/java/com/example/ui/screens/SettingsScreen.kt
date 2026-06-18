package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val apiKey by viewModel.apiKey.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        CenterAlignedTopAppBar(
            title = { Text("Pengaturan", style = MaterialTheme.typography.headlineMedium) }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Kunci API Gemini",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Masukkan API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.saveApiKey(keyInput) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Simpan Kunci")
                    }
                    Text(
                        "Kunci API tersimpan secara aman di DataStore lokal.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Tema Tampilan",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                            onClick = { viewModel.saveThemeMode(0) },
                            selected = themeMode == 0
                        ) {
                            Text("Auto")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                            onClick = { viewModel.saveThemeMode(1) },
                            selected = themeMode == 1
                        ) {
                            Text("Light")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                            onClick = { viewModel.saveThemeMode(2) },
                            selected = themeMode == 2
                        ) {
                            Text("Dark")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                            onClick = { viewModel.saveThemeMode(3) },
                            selected = themeMode == 3
                        ) {
                            Text("Amoled")
                        }
                    }
                }
            }
        }
    }
}
