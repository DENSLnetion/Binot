package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
    val userName by settingsViewModel.userName.collectAsState()

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
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Record") },
                        label = { Text("Record") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "history",
                        onClick = { navController.navigate("history") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }},
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "settings",
                        onClick = { navController.navigate("settings") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }},
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController, 
            startDestination = "record",
            modifier = Modifier.padding(innerPadding),
            // Transisi antar tab instan (0 ms) menghilangkan efek ghosting
            enterTransition = { fadeIn(animationSpec = tween(0)) },
            exitTransition = { fadeOut(animationSpec = tween(0)) },
            popEnterTransition = { fadeIn(animationSpec = tween(0)) },
            popExitTransition = { fadeOut(animationSpec = tween(0)) }
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
                    userName = userName,
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
                SettingsScreen(viewModel = settingsViewModel)
            }
            // Animasi Morphing (Scale) khusus untuk buka Catatan
            composable(
                "result/{noteId}",
                enterTransition = { scaleIn(initialScale = 0.85f, animationSpec = tween(300)) + fadeIn(tween(300)) },
                exitTransition = { scaleOut(targetScale = 0.85f, animationSpec = tween(300)) + fadeOut(tween(300)) }
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId")?.toIntOrNull() ?: return@composable
                val apiKey by settingsViewModel.apiKey.collectAsState()
                
                val resultViewModel: ResultViewModel = viewModel(
                    factory = ResultViewModel.provideFactory(noteId, appContainer.noteRepository, apiKey)
                )
                ResultScreen(
                    viewModel = resultViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

