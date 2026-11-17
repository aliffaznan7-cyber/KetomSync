package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ClipboardEntity
import com.example.data.model.ClipboardCategory
import com.example.data.model.DeviceType
import com.example.data.model.FirestoreDevice
import com.example.data.model.RetentionPolicy
import com.example.data.model.UiState
import com.example.data.util.DeviceInfoProvider
import com.example.ui.theme.LocalKetomTokens
import com.example.ui.viewmodel.KetomViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClipboardSyncScreen(
    viewModel: KetomViewModel,
    onNavigateToSandbox: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val filteredClips by viewModel.filteredClips.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val pinnedOnly by viewModel.pinnedOnly.collectAsStateWithLifecycle()
    val devicesState by viewModel.devicesState.collectAsStateWithLifecycle()

    var isImeEnabled by remember { mutableStateOf(checkImeEnabled(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedClip by remember { mutableStateOf<ClipboardEntity?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isImeEnabled = checkImeEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedLayout = maxWidth >= 680.dp

            if (isExpandedLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SyncHeaderControls(
                            isImeEnabled = isImeEnabled,
                            isIncognito = settings.incognitoMode,
                            retentionPolicy = settings.retentionPolicy,
                            searchQuery = searchQuery,
                            categoryFilter = categoryFilter,
                            pinnedOnly = pinnedOnly,
                            onSearchChange = viewModel::setSearchQuery,
                            onCategorySelect = viewModel::setCategoryFilter,
                            onTogglePinnedOnly = viewModel::togglePinnedOnly,
                            onCaptureSystemClipboard = viewModel::captureFromSystemClipboard,
                            onClearUnpinned = viewModel::clearUnpinned,
                            onOpenImeSettings = {
                                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            },
                            onShowImePicker = {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showInputMethodPicker()
                            },
                            onTestSandbox = onNavigateToSandbox
                        )
                        ClipboardEntriesList(
                            clips = filteredClips,
                            selectedClipId = selectedClip?.id,
                            onSelect = { selectedClip = it },
                            onCopy = { viewModel.copyToSystemClipboard(it.content) },
                            onTogglePin = viewModel::togglePin,
                            onDelete = {
                                if (selectedClip?.id == it.id) selectedClip = null
                                viewModel.deleteEntry(it)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SyncedDevicesPanel(devicesState = devicesState)
                        ClipDetailPane(
                            clip = selectedClip ?: filteredClips.firstOrNull(),
                            onCopy = { viewModel.copyToSystemClipboard(it.content) },
                            onTogglePin = viewModel::togglePin,
                            onDelete = {
                                if (selectedClip?.id == it.id) selectedClip = null
                                viewModel.deleteEntry(it)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SyncHeaderControls(
                            isImeEnabled = isImeEnabled,
                            isIncognito = settings.incognitoMode,
                            retentionPolicy = settings.retentionPolicy,
                            searchQuery = searchQuery,
                            categoryFilter = categoryFilter,
                            pinnedOnly = pinnedOnly,
                            onSearchChange = viewModel::setSearchQuery,
                            onCategorySelect = viewModel::setCategoryFilter,
                            onTogglePinnedOnly = viewModel::togglePinnedOnly,
                            onCaptureSystemClipboard = viewModel::captureFromSystemClipboard,
                            onClearUnpinned = viewModel::clearUnpinned,
                            onOpenImeSettings = {
                                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            },
                            onShowImePicker = {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showInputMethodPicker()
                            },
                            onTestSandbox = onNavigateToSandbox
                        )
                    }

                    item {
                        SyncedDevicesPanel(devicesState = devicesState)
                    }

                    if (filteredClips.isEmpty()) {
                        item {
                            EmptyClipboardCard(
                                isIncognito = settings.incognitoMode,
                                onAddClip = { showAddDialog = true }
                            )
                        }
                    } else {
                        items(filteredClips, key = { "${it.id}_${it.updatedAtMillis}" }) { clip ->
                            ClipboardCardItem(
                                clip = clip,
                                isSelected = false,
                                onClick = { viewModel.copyToSystemClipboard(clip.content) },
                                onCopy = { viewModel.copyToSystemClipboard(clip.content) },
                                onTogglePin = { viewModel.togglePin(clip) },
                                onDelete = { viewModel.deleteEntry(clip) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .testTag("add_clip_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add clipboard entry"
            )
        }
    }

    if (showAddDialog) {
        AddClipboardDialog(
            onDismiss = { showAddDialog = false },
            onSave = { content ->
                viewModel.addManualClip(content)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun SyncHeaderControls(
    isImeEnabled: Boolean,
    isIncognito: Boolean,
    retentionPolicy: RetentionPolicy,
    searchQuery: String,
    categoryFilter: ClipboardCategory?,
    pinnedOnly: Boolean,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (ClipboardCategory?) -> Unit,
    onTogglePinnedOnly: () -> Unit,
    onCaptureSystemClipboard: () -> Unit,
    onClearUnpinned: () -> Unit,
    onOpenImeSettings: () -> Unit,
    onShowImePicker: () -> Unit,
    onTestSandbox: () -> Unit
) {
    val tokens = LocalKetomTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // IME Status & Quick Setup Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isIncognito) {
                tokens.incognitoAccent.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            },
            border = BorderStroke(
                1.dp,
                if (isIncognito) tokens.incognitoAccent.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isIncognito) Icons.Default.VisibilityOff else Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = if (isIncognito) tokens.incognitoAccent else MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = if (isIncognito) "Incognito Mode Active" else if (isImeEnabled) "KetomSync IME Ready" else "Enable KetomSync Keyboard",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isIncognito) {
                                    "Session-only clipboard • cloud sync paused"
                                } else {
                                    "Retention: ${retentionPolicy.label} • Max 100 local entries"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (isImeEnabled && !isIncognito) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Keyboard enabled",
                            tint = tokens.syncAccent
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isImeEnabled) {
                        Button(
                            onClick = onOpenImeSettings,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("enable_ime_button")
                        ) {
                            Text("Enable in Settings")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onShowImePicker,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("switch_ime_button")
                        ) {
                            Text("Switch IME")
                        }
                    }
                    Button(
                        onClick = onTestSandbox,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_sandbox_button")
                    ) {
                        Text("Live Keyboard")
                    }
                }
            }
        }

        // Search & System Capture Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search clips or device...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("clipboard_search_field")
            )
            Surface(
                onClick = onCaptureSystemClipboard,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .size(54.dp)
                    .testTag("capture_system_clipboard_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Capture from system clipboard",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Surface(
                onClick = onClearUnpinned,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(54.dp)
                    .testTag("clear_unpinned_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = "Clear unpinned clipboard history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Category Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = categoryFilter == null && !pinnedOnly,
                    onClick = {
                        onCategorySelect(null)
                        if (pinnedOnly) onTogglePinnedOnly()
                    },
                    label = { Text("All") }
                )
            }
            item {
                FilterChip(
                    selected = pinnedOnly,
                    onClick = onTogglePinnedOnly,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Pinned") }
                )
            }
            items(ClipboardCategory.entries) { cat ->
                FilterChip(
                    selected = categoryFilter == cat,
                    onClick = {
                        onCategorySelect(if (categoryFilter == cat) null else cat)
                    },
                    label = { Text(cat.label) }
                )
            }
        }
    }
}

@Composable
private fun SyncedDevicesPanel(devicesState: UiState<List<FirestoreDevice>>) {
    val context = LocalContext.current
    val localProfile = remember(context) { DeviceInfoProvider.getProfile(context) }
    val tokens = LocalKetomTokens.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = tokens.syncAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Synced Ecosystem Devices",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            when (devicesState) {
                is UiState.Loading -> {
                    Text(
                        text = "Connecting ${localProfile.deviceName}...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UiState.Error -> {
                    Text(
                        text = "This device: ${localProfile.deviceName} (${localProfile.deviceType.label})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UiState.Success -> {
                    val list = devicesState.data
                    if (list.isEmpty()) {
                        Text(
                            text = "${localProfile.deviceName} active. Sign in on your Pixel or Xiaomi Pad to link.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(list, key = { it.id }) { dev ->
                                val isTablet = dev.deviceType == DeviceType.TABLET.wireValue
                                AssistChip(
                                    onClick = {},
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isTablet) Icons.Default.TabletAndroid else Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = if (dev.syncEnabled) tokens.syncAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = if (dev.id == localProfile.deviceId) "${dev.deviceName} (This)" else dev.deviceName,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardEntriesList(
    clips: List<ClipboardEntity>,
    selectedClipId: Long?,
    onSelect: (ClipboardEntity) -> Unit,
    onCopy: (ClipboardEntity) -> Unit,
    onTogglePin: (ClipboardEntity) -> Unit,
    onDelete: (ClipboardEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (clips.isEmpty()) {
        EmptyClipboardCard(isIncognito = false, onAddClip = {})
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(clips, key = { "${it.id}_${it.updatedAtMillis}" }) { clip ->
            ClipboardCardItem(
                clip = clip,
                isSelected = clip.id == selectedClipId,
                onClick = { onSelect(clip) },
                onCopy = { onCopy(clip) },
                onTogglePin = { onTogglePin(clip) },
                onDelete = { onDelete(clip) }
            )
        }
    }
}

@Composable
private fun ClipboardCardItem(
    clip: ClipboardEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val category = ClipboardCategory.fromWire(clip.category)
    val isTablet = clip.deviceType == DeviceType.TABLET.wireValue
    val tokens = LocalKetomTokens.current
    val timeLabel = remember(clip.updatedAtMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(clip.updatedAtMillis))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected || clip.isPinned) 1.5.dp else 1.dp,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                clip.isPinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("clip_card_${clip.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = when (category) {
                                    ClipboardCategory.LINK -> Icons.Default.Link
                                    ClipboardCategory.CODE -> Icons.Default.Code
                                    ClipboardCategory.ADDRESS -> Icons.Default.LocationOn
                                    ClipboardCategory.TEXT -> Icons.Default.Notes
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (isTablet) Icons.Default.TabletAndroid else Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = tokens.syncAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${clip.deviceName} • $timeLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (clip.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (clip.isPinned) "Unpin clip" else "Pin clip",
                            tint = if (clip.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy clip",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete clip",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = clip.content,
                style = if (category == ClipboardCategory.CODE) {
                    MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ClipDetailPane(
    clip: ClipboardEntity?,
    onCopy: (ClipboardEntity) -> Unit,
    onTogglePin: (ClipboardEntity) -> Unit,
    onDelete: (ClipboardEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        if (clip == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a clipboard item to inspect its full payload and device metadata.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val cat = ClipboardCategory.fromWire(clip.category)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cat.label,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = clip.deviceName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        Text(
                            text = clip.content,
                            style = if (cat == ClipboardCategory.CODE) {
                                MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onTogglePin(clip) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (clip.isPinned) "Unpin" else "Pin")
                    }
                    Button(
                        onClick = { onCopy(clip) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy")
                    }
                    IconButton(onClick = { onDelete(clip) }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete clip",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyClipboardCard(
    isIncognito: Boolean,
    onAddClip: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isIncognito) Icons.Default.VisibilityOff else Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                text = if (isIncognito) "Incognito Clipboard Empty" else "No Clipboard Clips Yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Copy text on your Pixel 9 Pro or Xiaomi Pad 7, or tap + to add a synced clip right away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onAddClip) {
                Text("Add Clip")
            }
        }
    }
}

@Composable
private fun AddClipboardDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Synced Clip") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text, URL, Code, or Address") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_clip_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onSave(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("save_clip_button")
            ) {
                Text("Save & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun checkImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}
