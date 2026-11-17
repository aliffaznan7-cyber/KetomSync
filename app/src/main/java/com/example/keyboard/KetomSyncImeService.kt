package com.example.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.local.ClipboardSyncCoordinator
import com.example.data.local.KetomDatabase
import com.example.data.local.KetomSettings
import com.example.data.local.SettingsDataStore
import com.example.data.model.KeyboardLayoutMode
import com.example.data.remote.SyncRepository
import com.example.data.util.HapticFeedbackHelper
import com.example.ui.theme.KetomSyncTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class KetomSyncImeService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var syncRepository: SyncRepository
    private lateinit var coordinator: ClipboardSyncCoordinator
    private lateinit var haptics: HapticFeedbackHelper
    private lateinit var keyboardController: KetomKeyboardController

    private val currentSettings = MutableStateFlow(KetomSettings())
    private var cloudSyncJob: Job? = null
    private var lastCapturedClip: String? = null

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = coordinator.readSystemClipboardText() ?: return@OnPrimaryClipChangedListener
        if (text == lastCapturedClip) return@OnPrimaryClipChangedListener
        lastCapturedClip = text
        val cfg = currentSettings.value
        serviceScope.launch {
            coordinator.recordCapturedText(
                rawText = text,
                retentionPolicy = cfg.retentionPolicy,
                isIncognito = cfg.incognitoMode,
                syncToCloud = cfg.crossDeviceSyncEnabled
            )
        }
    }

    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        if (user != null) {
            startCloudObservers()
        } else {
            cloudSyncJob?.cancel()
            cloudSyncJob = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        settingsDataStore = SettingsDataStore(applicationContext)
        syncRepository = SyncRepository(applicationContext)
        coordinator = ClipboardSyncCoordinator(
            context = applicationContext,
            database = KetomDatabase.getInstance(applicationContext),
            syncRepository = syncRepository
        )
        haptics = HapticFeedbackHelper(applicationContext)

        val bridge = AndroidInputConnectionBridge(
            inputConnectionProvider = { currentInputConnection },
            editorInfoProvider = { currentInputEditorInfo }
        )
        keyboardController = KetomKeyboardController(
            bridge = bridge,
            scope = serviceScope,
            onHapticTick = { mult ->
                val cfg = currentSettings.value
                haptics.pulse(cfg.hapticEnabled, cfg.hapticStrength, cfg.hapticDurationMs, mult)
            }
        )

        serviceScope.launch {
            coordinator.ensureStarterSnippetsSeeded()
        }
        serviceScope.launch {
            settingsDataStore.settingsFlow.collectLatest { settings ->
                currentSettings.value = settings
            }
        }

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.addPrimaryClipChangedListener(clipboardListener)
        Firebase.auth.addAuthStateListener(authListener)
    }

    private fun startCloudObservers() {
        cloudSyncJob?.cancel()
        if (Firebase.auth.currentUser == null) return
        cloudSyncJob = serviceScope.launch {
            launch {
                try {
                    syncRepository.observeClipboardEntries().collectLatest { remote ->
                        if (currentSettings.value.crossDeviceSyncEnabled && !currentSettings.value.incognitoMode) {
                            coordinator.mergeRemoteClips(remote)
                        }
                    }
                } catch (_: Exception) {
                    // Handled in repository & logged via handleFirestoreError
                }
            }
            launch {
                try {
                    syncRepository.observeSnippets().collectLatest { remote ->
                        coordinator.mergeRemoteSnippets(remote)
                    }
                } catch (_: Exception) {
                    // Handled in repository
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }

        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val settings by settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = KetomSettings())
                val clips by coordinator.observeEffectiveClips(
                    kotlinx.coroutines.flow.flowOf(settings.incognitoMode)
                ).collectAsStateWithLifecycle(initialValue = emptyList())
                val snippets by coordinator.observeLocalSnippets()
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                KetomSyncTheme(
                    themeMode = settings.themeMode,
                    dynamicColor = settings.dynamicColorEnabled
                ) {
                    KetomKeyboardDeck(
                        controller = keyboardController,
                        settings = settings,
                        recentClips = clips,
                        snippets = snippets,
                        onToggleIncognito = {
                            val next = !settings.incognitoMode
                            serviceScope.launch {
                                settingsDataStore.setIncognitoMode(next)
                                if (!next) coordinator.clearIncognitoSession()
                            }
                        },
                        onCycleLayoutMode = {
                            val next = when (settings.keyboardLayoutMode) {
                                KeyboardLayoutMode.STANDARD -> KeyboardLayoutMode.ONE_HANDED_RIGHT
                                KeyboardLayoutMode.ONE_HANDED_RIGHT -> KeyboardLayoutMode.ONE_HANDED_LEFT
                                KeyboardLayoutMode.ONE_HANDED_LEFT -> KeyboardLayoutMode.FLOATING
                                KeyboardLayoutMode.FLOATING -> KeyboardLayoutMode.STANDARD
                            }
                            serviceScope.launch { settingsDataStore.setKeyboardLayoutMode(next) }
                        },
                        onFlipOneHandedSide = {
                            val next = if (settings.keyboardLayoutMode == KeyboardLayoutMode.ONE_HANDED_RIGHT) {
                                KeyboardLayoutMode.ONE_HANDED_LEFT
                            } else {
                                KeyboardLayoutMode.ONE_HANDED_RIGHT
                            }
                            serviceScope.launch { settingsDataStore.setKeyboardLayoutMode(next) }
                        },
                        onUpdateFloatingSize = { w, h ->
                            serviceScope.launch { settingsDataStore.setFloatingDimensions(w, h) }
                        },
                        onUpdateFloatingOffset = { x, y ->
                            serviceScope.launch { settingsDataStore.setFloatingOffset(x, y) }
                        },
                        applyNavigationInsets = true
                    )
                }
            }
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        keyboardController.syncAutoCapitalization(currentSettings.value.autoCapitalize)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        if (currentSettings.value.incognitoMode) {
            coordinator.clearIncognitoSession()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.removePrimaryClipChangedListener(clipboardListener)
        Firebase.auth.removeAuthStateListener(authListener)
        cloudSyncJob?.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        serviceScope.cancel()
    }
}
