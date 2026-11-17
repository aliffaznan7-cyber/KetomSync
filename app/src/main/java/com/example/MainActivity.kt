package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SnippetFolder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SnippetFolder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.ClipboardSyncCoordinator
import com.example.data.local.KetomDatabase
import com.example.data.local.KetomSettings
import com.example.data.local.SettingsDataStore
import com.example.data.remote.SyncRepository
import com.example.data.util.DeviceInfoProvider
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ClipboardSyncScreen
import com.example.ui.screens.KeyboardSandboxScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SnippetsScreen
import com.example.ui.theme.KetomSyncTheme
import com.example.ui.theme.LocalKetomTokens
import com.example.ui.viewmodel.KetomViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object KetomRoutes {
    const val SYNC = "sync"
    const val KEYBOARD = "keyboard"
    const val SNIPPETS = "snippets"
    const val SETTINGS = "settings"
}

private data class KetomNavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val ketomTabs = listOf(
    KetomNavTab(KetomRoutes.SYNC, R.string.nav_sync, Icons.Filled.Sync, Icons.Outlined.Sync),
    KetomNavTab(KetomRoutes.KEYBOARD, R.string.nav_sandbox, Icons.Filled.Keyboard, Icons.Outlined.Keyboard),
    KetomNavTab(KetomRoutes.SNIPPETS, R.string.nav_snippets, Icons.Filled.SnippetFolder, Icons.Outlined.SnippetFolder),
    KetomNavTab(KetomRoutes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsDataStore = remember(context) { SettingsDataStore(context.applicationContext) }
            val settings by settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = KetomSettings())

            KetomSyncTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColorEnabled
            ) {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            MainKetomApp(
                currentUserId = user.uid,
                onSignedOut = { currentUser = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainKetomApp(
    currentUserId: String,
    onSignedOut: () -> Unit
) {
    val viewModel: KetomViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val syncRepository = SyncRepository(db, Firebase.auth)
                val database = KetomDatabase.getInstance(app)
                val coordinator = ClipboardSyncCoordinator(app, database, syncRepository)
                val settingsStore = SettingsDataStore(app)
                val deviceProfile = DeviceInfoProvider.getProfile(app)
                KetomViewModel(
                    currentUserId = currentUserId,
                    syncRepository = syncRepository,
                    coordinator = coordinator,
                    settingsDataStore = settingsStore,
                    deviceProfile = deviceProfile
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: KetomRoutes.SYNC
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.syncBannerMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val tokens = LocalKetomTokens.current

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    val navigateTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (settings.incognitoMode) "KetomSync • Incognito" else "KetomSync",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (settings.incognitoMode) tokens.incognitoAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        ketomTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NavigationRail(modifier = Modifier.padding(top = 8.dp)) {
                            ketomTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                val label = stringResource(tab.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = KetomRoutes.SYNC,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(KetomRoutes.SYNC) {
                        ClipboardSyncScreen(
                            viewModel = viewModel,
                            onNavigateToSandbox = { navigateTab(KetomRoutes.KEYBOARD) }
                        )
                    }
                    composable(KetomRoutes.KEYBOARD) {
                        KeyboardSandboxScreen(viewModel = viewModel)
                    }
                    composable(KetomRoutes.SNIPPETS) {
                        SnippetsScreen(viewModel = viewModel)
                    }
                    composable(KetomRoutes.SETTINGS) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onSignedOut = onSignedOut
                        )
                    }
                }
            }
        }
    }
}
