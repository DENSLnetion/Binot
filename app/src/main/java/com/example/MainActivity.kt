package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.example.ui.screens.TrashScreen
import com.example.ui.theme.BinotTheme
import com.example.utils.ImportExportHelper
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.RecordViewModel
import com.example.viewmodel.ResultViewModel
import com.example.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    // Penampung reaktif URI yang datang dari luar (WhatsApp / File Manager)
    val incomingIntentUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

        val appContainer = (application as BinotApplication).container

        // Tangkap intent saat app baru dinyalakan
        handleIntent(intent)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(
                    appContainer.settingsRepository, 
                    appContainer.noteRepository
                )
            )
            val themeMode by settingsViewModel.themeMode.collectAsState()

            BinotTheme(themeMode = themeMode) {
                BinotApp(appContainer, settingsViewModel, this)
            }
        }
    }

    // Tangkap intent saat app udah jalan (SingleTask)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            incomingIntentUri.value = intent.data
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BinotApp(appContainer: AppContainer, settingsViewModel: SettingsViewModel, mainActivity: MainActivity) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val userName by settingsViewModel.userName.collectAsState()

    val isDataLoaded by settingsViewModel.isDataLoaded.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // State Tracker untuk "Open With" dari WA
    val incomingUri by mainActivity.incomingIntentUri.collectAsState()
    var isImportingFromExternal by remember { mutableStateOf(false) }

    if (!isDataLoaded) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
        return
    }

    val startDestination = if (userName.isBlank()) "onboarding" else "record"
    val snackbarHostState = remember { SnackbarHostState() }
    val isRecordingGlobal by appContainer.audioRecorderManager.isRecording.collectAsState()

    // Eksekutor "Open With"
    LaunchedEffect(incomingUri) {
        incomingUri?.let { uri ->
            isImportingFromExternal = true
            val newId = ImportExportHelper.importFile(context, uri, appContainer.noteRepository)
            mainActivity.incomingIntentUri.value = null // Reset biar ga loop
            isImportingFromExternal = false
            
            if (newId != null) {
                navController.navigate("result/$newId")
            } else {
                snackbarHostState.showSnackbar("Failed to open or import note.")
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                enterTransition = {
                    if (targetState.destination.route?.startsWith("result") == true || initialState.destination.route?.startsWith("result") == true) fadeIn(tween(0)) else fadeIn(tween(300))
                },
                exitTransition = {
                    if (targetState.destination.route?.startsWith("result") == true || initialState.destination.route?.startsWith("result") == true) fadeOut(tween(0)) else fadeOut(tween(300))
                },
                popEnterTransition = {
                    if (targetState.destination.route?.startsWith("result") == true || initialState.destination.route?.startsWith("result") == true) fadeIn(tween(0)) else fadeIn(tween(300))
                },
                popExitTransition = {
                    if (targetState.destination.route?.startsWith("result") == true || initialState.destination.route?.startsWith("result") == true) fadeOut(tween(0)) else fadeOut(tween(300))
                }
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        onComplete = { name, provider, key, task, format ->
                            settingsViewModel.saveUserName(name)
                            settingsViewModel.saveAiProvider(provider)
                            if (provider == 0) settingsViewModel.saveApiKey(key) else settingsViewModel.saveGroqApiKey(key)
                            settingsViewModel.saveAiTask(task)
                            settingsViewModel.saveAiFormat(format)
                            navController.navigate("record") { popUpTo("onboarding") { inclusive = true } }
                        }
                    )
                }
                composable("record") {
                    val apiKey by settingsViewModel.apiKey.collectAsState()
                    val recordMode by settingsViewModel.recordMode.collectAsState()
                    
                    val recordViewModel: RecordViewModel = viewModel(
                        factory = RecordViewModel.provideFactory(appContainer.audioRecorderManager, appContainer.noteRepository, apiKey)
                    )
                    RecordScreen(
                        viewModel = recordViewModel,
                        userName = userName,
                        recordMode = recordMode, 
                        snackbarHostState = snackbarHostState,
                        animatedVisibilityScope = this@composable,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        onNoteClick = { id -> navController.navigate("result/$id") }
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
                        onNoteClick = { id -> navController.navigate("result/$id") },
                        onTrashClick = { navController.navigate("trash") },
                        onImportFile = { uri -> ImportExportHelper.importFile(context, uri, appContainer.noteRepository) }
                    )
                }
                composable("trash") {
                    val historyViewModel: HistoryViewModel = viewModel(
                        factory = HistoryViewModel.provideFactory(appContainer.noteRepository)
                    )
                    TrashScreen(
                        viewModel = historyViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        animatedVisibilityScope = this@composable,
                        isRecording = isRecordingGlobal,
                        onDiscardRecording = { 
                            val path = appContainer.audioRecorderManager.stopRecording()
                            if (path != null) try { java.io.File(path).delete() } catch (e: Exception) {}
                        }
                    )
                }
                composable("result/{noteId}") { backStackEntry ->
                    val noteId = backStackEntry.arguments?.getString("noteId")?.toIntOrNull() ?: return@composable
                    
                    val resultViewModel: ResultViewModel = viewModel(
                        factory = ResultViewModel.provideFactory(
                            noteId = noteId, 
                            repository = appContainer.noteRepository, 
                            settingsRepository = appContainer.settingsRepository
                        )
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
            
            // Screen Pelindung Loading saat nge-import dari WhatsApp
            if (isImportingFromExternal) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .clickable(enabled = false) {}, // Blok klik
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Unpacking Binot...", 
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
