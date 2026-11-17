package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ClipboardEntity
import com.example.data.local.ClipboardSyncCoordinator
import com.example.data.local.KetomSettings
import com.example.data.local.SettingsDataStore
import com.example.data.local.SnippetEntity
import com.example.data.model.ClipboardCategory
import com.example.data.model.FirestoreDevice
import com.example.data.model.KeyboardLayoutMode
import com.example.data.model.RetentionPolicy
import com.example.data.model.SnippetCategory
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.remote.SyncRepository
import com.example.data.util.LocalDeviceProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class KetomViewModel(
    private val currentUserId: String,
    private val syncRepository: SyncRepository,
    private val coordinator: ClipboardSyncCoordinator,
    private val settingsDataStore: SettingsDataStore,
    private val deviceProfile: LocalDeviceProfile
) : ViewModel() {

    val settings: StateFlow<KetomSettings> = settingsDataStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = KetomSettings()
        )

    private val _selectedCategoryFilter = MutableStateFlow<ClipboardCategory?>(null)
    val selectedCategoryFilter: StateFlow<ClipboardCategory?> = _selectedCategoryFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _pinnedOnly = MutableStateFlow(false)
    val pinnedOnly: StateFlow<Boolean> = _pinnedOnly.asStateFlow()

    private val _syncBannerMessage = MutableStateFlow<String?>(null)
    val syncBannerMessage: StateFlow<String?> = _syncBannerMessage.asStateFlow()

    val clips: StateFlow<List<ClipboardEntity>> = coordinator.observeEffectiveClips(
        settings.map { it.incognitoMode }.distinctUntilChanged()
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val filteredClips: StateFlow<List<ClipboardEntity>> = combine(
        clips,
        _selectedCategoryFilter,
        _searchQuery,
        _pinnedOnly
    ) { allClips, catFilter, query, onlyPinned ->
        val q = query.trim().lowercase()
        allClips.filter { item ->
            val matchesCat = catFilter == null || item.category == catFilter.wireValue
            val matchesPin = !onlyPinned || item.isPinned
            val matchesQuery = q.isEmpty() ||
                item.content.lowercase().contains(q) ||
                item.deviceName.lowercase().contains(q)
            matchesCat && matchesPin && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val snippets: StateFlow<List<SnippetEntity>> = coordinator.observeLocalSnippets()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val devicesState: StateFlow<UiState<List<FirestoreDevice>>> = syncRepository.observeDevices()
        .map<List<FirestoreDevice>, UiState<List<FirestoreDevice>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing devices for $currentUserId", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to sync devices"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            coordinator.ensureStarterSnippetsSeeded()
        }
        viewModelScope.launch {
            syncRepository.registerOrHeartbeatDevice(
                deviceId = deviceProfile.deviceId,
                deviceName = deviceProfile.deviceName,
                deviceType = deviceProfile.deviceType,
                modelName = deviceProfile.modelName,
                syncEnabled = true
            )
        }
        viewModelScope.launch {
            syncRepository.observeClipboardEntries()
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Clipboard cloud sync stream error", firestoreError ?: error)
                }
                .collectLatest { remote ->
                    val cfg = settings.value
                    if (cfg.crossDeviceSyncEnabled && !cfg.incognitoMode) {
                        coordinator.mergeRemoteClips(remote)
                    }
                }
        }
        viewModelScope.launch {
            syncRepository.observeSnippets()
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Snippets cloud sync stream error", firestoreError ?: error)
                }
                .collectLatest { remote ->
                    coordinator.mergeRemoteSnippets(remote)
                }
        }
    }

    fun setCategoryFilter(category: ClipboardCategory?) {
        _selectedCategoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePinnedOnly() {
        _pinnedOnly.value = !_pinnedOnly.value
    }

    fun addManualClip(text: String) {
        val cfg = settings.value
        viewModelScope.launch {
            coordinator.recordCapturedText(
                rawText = text,
                retentionPolicy = cfg.retentionPolicy,
                isIncognito = cfg.incognitoMode,
                syncToCloud = cfg.crossDeviceSyncEnabled
            )
            _syncBannerMessage.value = if (cfg.incognitoMode) {
                "Added to ephemeral incognito session"
            } else if (cfg.crossDeviceSyncEnabled) {
                "Synced across your devices"
            } else {
                "Saved locally"
            }
        }
    }

    fun captureFromSystemClipboard() {
        val clip = coordinator.readSystemClipboardText()
        if (clip.isNullOrBlank()) {
            _syncBannerMessage.value = "System clipboard is empty"
            return
        }
        addManualClip(clip)
    }

    fun copyToSystemClipboard(text: String) {
        coordinator.copyToSystemClipboard(text)
        _syncBannerMessage.value = "Copied to device clipboard"
    }

    fun togglePin(entry: ClipboardEntity) {
        val cfg = settings.value
        viewModelScope.launch {
            coordinator.togglePin(entry, syncToCloud = cfg.crossDeviceSyncEnabled && !cfg.incognitoMode)
        }
    }

    fun deleteEntry(entry: ClipboardEntity) {
        val cfg = settings.value
        viewModelScope.launch {
            coordinator.deleteEntry(entry, syncToCloud = cfg.crossDeviceSyncEnabled)
        }
    }

    fun clearUnpinned() {
        viewModelScope.launch {
            coordinator.clearUnpinnedHistory()
            _syncBannerMessage.value = "Cleared unpinned clipboard entries"
        }
    }

    fun saveSnippet(
        existingId: Long? = null,
        existingRemoteId: String? = null,
        title: String,
        shortcut: String,
        body: String,
        category: SnippetCategory
    ) {
        val cfg = settings.value
        viewModelScope.launch {
            coordinator.saveSnippet(
                existingId = existingId,
                existingRemoteId = existingRemoteId,
                title = title,
                shortcut = shortcut,
                body = body,
                category = category,
                syncToCloud = cfg.crossDeviceSyncEnabled && !cfg.incognitoMode
            )
        }
    }

    fun deleteSnippet(snippet: SnippetEntity) {
        val cfg = settings.value
        viewModelScope.launch {
            coordinator.deleteSnippet(snippet, syncToCloud = cfg.crossDeviceSyncEnabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDynamicColorEnabled(enabled) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setHapticEnabled(enabled) }
    }

    fun setHapticStrength(strength: Float) {
        viewModelScope.launch { settingsDataStore.setHapticStrength(strength) }
    }

    fun setHapticDurationMs(durationMs: Int) {
        viewModelScope.launch { settingsDataStore.setHapticDurationMs(durationMs) }
    }

    fun setKeyboardLayoutMode(mode: KeyboardLayoutMode) {
        viewModelScope.launch { settingsDataStore.setKeyboardLayoutMode(mode) }
    }

    fun cycleKeyboardLayoutMode() {
        val next = when (settings.value.keyboardLayoutMode) {
            KeyboardLayoutMode.STANDARD -> KeyboardLayoutMode.ONE_HANDED_RIGHT
            KeyboardLayoutMode.ONE_HANDED_RIGHT -> KeyboardLayoutMode.ONE_HANDED_LEFT
            KeyboardLayoutMode.ONE_HANDED_LEFT -> KeyboardLayoutMode.FLOATING
            KeyboardLayoutMode.FLOATING -> KeyboardLayoutMode.STANDARD
        }
        setKeyboardLayoutMode(next)
    }

    fun flipOneHandedSide() {
        val next = if (settings.value.keyboardLayoutMode == KeyboardLayoutMode.ONE_HANDED_RIGHT) {
            KeyboardLayoutMode.ONE_HANDED_LEFT
        } else {
            KeyboardLayoutMode.ONE_HANDED_RIGHT
        }
        setKeyboardLayoutMode(next)
    }

    fun setFloatingDimensions(widthDp: Float, heightDp: Float) {
        viewModelScope.launch { settingsDataStore.setFloatingDimensions(widthDp, heightDp) }
    }

    fun setFloatingOffset(offsetX: Float, offsetY: Float) {
        viewModelScope.launch { settingsDataStore.setFloatingOffset(offsetX, offsetY) }
    }

    fun setRetentionPolicy(policy: RetentionPolicy) {
        viewModelScope.launch {
            settingsDataStore.setRetentionPolicy(policy)
            coordinator.updateRetentionForAllUnpinned(policy)
        }
    }

    fun setAutoCapitalize(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoCapitalize(enabled) }
    }

    fun setIncognitoMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setIncognitoMode(enabled)
            if (!enabled) coordinator.clearIncognitoSession()
        }
    }

    fun setCrossDeviceSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setCrossDeviceSyncEnabled(enabled)
            syncRepository.registerOrHeartbeatDevice(
                deviceId = deviceProfile.deviceId,
                deviceName = deviceProfile.deviceName,
                deviceType = deviceProfile.deviceType,
                modelName = deviceProfile.modelName,
                syncEnabled = enabled
            )
        }
    }

    fun clearBanner() {
        _syncBannerMessage.value = null
    }

    private companion object {
        const val TAG = "KetomViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
