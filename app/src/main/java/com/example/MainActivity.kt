package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.RecordScreen
import com.example.ui.screens.ResultScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BinotTheme
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.RecordViewModel
import com.example.viewmodel.ResultViewModel
import com.example.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as BinotApplication).container

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(appContainer.settingsRepository)
            )
            
            val themeMode by settingsViewModel.themeMode.collectAsState()

            BinotTheme(themeMode = themeMode) {
                BinotApp(appContainer, settingsViewModel)
            }
        }
    }
}

@Composable
fun BinotApp(appContainer: AppContainer, settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in listOf("record", "history", "settings")) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "record",
                        onClick = { navController.navigate("record") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }},
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Rekam") },
                        label = { Text("Rekam") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "history",
                        onClick = { navController.navigate("history") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }},
                        icon = { Icon(Icons.Default.History, contentDescription = "Riwayat") },
                        label = { Text("Riwayat") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "settings",
                        onClick = { navController.navigate("settings") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }},
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                        label = { Text("Pengaturan") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController, 
            startDestination = "record",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("record") {
                val recordViewModel: RecordViewModel = viewModel(
                    factory = RecordViewModel.provideFactory(
                        appContainer.audioRecorderManager,
                        appContainer.noteRepository
                    )
                )
                RecordScreen(
                    viewModel = recordViewModel,
                    onNavigateToResult = { id -> navController.navigate("result/$id") }
                )
            }
            composable("history") {
                val historyViewModel: HistoryViewModel = viewModel(
                    factory = HistoryViewModel.provideFactory(appContainer.noteRepository)
                )
                HistoryScreen(
                    viewModel = historyViewModel,
                    onNoteClick = { id -> navController.navigate("result/$id") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = settingsViewModel
                )
            }
            composable("result/{noteId}") { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId")?.toIntOrNull() ?: return@composable
                val apiKey by settingsViewModel.apiKey.collectAsState()
                
                val resultViewModel: ResultViewModel = viewModel(
                    factory = ResultViewModel.provideFactory(
                        noteId,
                        appContainer.noteRepository,
                        apiKey
                    )
                )
                ResultScreen(
                    viewModel = resultViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

