package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.RecordScreen
import com.example.ui.screens.ResultScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BinotTheme
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.RecordViewModel
import com.example.viewmodel.ResultViewModel
import com.example.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as BinotApplication).container

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(
                    appContainer.settingsRepository, 
                    appContainer.noteRepository
                )
            )
            
            val themeMode by settingsViewModel.themeMode.collectAsState()

            BinotTheme(themeMode = themeMode) {
                BinotApp(appContainer, settingsViewModel)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BinotApp(appContainer: AppContainer, settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val userName by settingsViewModel.userName.collectAsState()

    var isReady by remember { mutableStateOf(false) }
    LaunchedEffect(userName) {
        delay(100) 
        isReady = true
    }

    if (!isReady) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val startDestination = if (userName.isBlank()) "onboarding" else "record"

    // SnackbarHostState dipindah ke level Scaffold TERLUAR (yang punya bottomBar).
    // Sebelumnya RecordScreen punya Scaffold + SnackbarHost sendiri yang
    // bersarang di dalam Scaffold ini — snackbar dari Scaffold dalam berisiko
    // ke-render di area yang tertutup/terpotong oleh NavigationBar milik
    // Scaffold luar, sehingga tidak pernah kelihatan sama sekali.
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
        SharedTransitionLayout {
            NavHost(
                navController = navController, 
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding),
                // Transisi cross-fade NavHost DIMATIKAN total (bukan cuma durasi 0).
                // Sebelumnya pakai fadeIn/fadeOut(tween(0)) — meski durasinya 0ms,
                // Compose Navigation tetap menjalankan crossfade antara screen lama
                // & baru selama proses sharedBounds morphing masih berjalan (yang
                // punya durasi sendiri), sehingga kedua layer numpuk dan keduanya
                // berubah alpha bareng -> kelihatan transparan/burem pas buka-tutup
                // catatan. Dengan None, cuma sharedBounds yang mengatur transisi visual,
                // hasilnya solid tanpa fade.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        onComplete = { name, key ->
                            settingsViewModel.saveUserName(name)
                            settingsViewModel.saveApiKey(key)
                            navController.navigate("record") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    )
                }
                composable("record") {
                    val apiKey by settingsViewModel.apiKey.collectAsState()
                    val recordViewModel: RecordViewModel = viewModel(
                        factory = RecordViewModel.provideFactory(
                            appContainer.audioRecorderManager,
                            appContainer.noteRepository,
                            apiKey
                        )
                    )
                    RecordScreen(
                        viewModel = recordViewModel,
                        userName = userName,
                        snackbarHostState = snackbarHostState
                    )
                }
                composable("history") {
                    val historyViewModel: HistoryViewModel = viewModel(
                        factory = HistoryViewModel.provideFactory(appContainer.noteRepository)
                    )
                    HistoryScreen(
                        viewModel = historyViewModel,
                        animatedVisibilityScope = this@composable,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        onNoteClick = { id -> navController.navigate("result/$id") }
                    )
                }
                composable("settings") {
                    SettingsScreen(viewModel = settingsViewModel)
                }
                composable("result/{noteId}") { backStackEntry ->
                    val noteId = backStackEntry.arguments?.getString("noteId")?.toIntOrNull() ?: return@composable
                    val apiKey by settingsViewModel.apiKey.collectAsState()
                    
                    val resultViewModel: ResultViewModel = viewModel(
                        factory = ResultViewModel.provideFactory(noteId, appContainer.noteRepository, apiKey)
                    )
                    ResultScreen(
                        viewModel = resultViewModel,
                        noteId = noteId,
                        animatedVisibilityScope = this@composable,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

