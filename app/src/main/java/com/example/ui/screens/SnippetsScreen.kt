package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SnippetEntity
import com.example.data.model.SnippetCategory
import com.example.data.util.ClipboardCategorizer
import com.example.ui.viewmodel.KetomViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnippetsScreen(
    viewModel: KetomViewModel,
    modifier: Modifier = Modifier
) {
    val snippets by viewModel.snippets.collectAsStateWithLifecycle()
    var editingSnippet by remember { mutableStateOf<SnippetEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var expandingSnippet by remember { mutableStateOf<SnippetEntity?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Snippets & Variable Macros",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Use {variable} placeholders inside any macro body. KetomSync prompts for values right inside the keyboard drawer before pasting.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(snippets, key = { it.id }) { snippet ->
                val vars = remember(snippet.body) { ClipboardCategorizer.extractTemplateVariables(snippet.body) }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("snippet_card_${snippet.id}")
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = snippet.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = snippet.shortcut,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        if (vars.isEmpty()) {
                                            viewModel.copyToSystemClipboard(snippet.body)
                                        } else {
                                            expandingSnippet = snippet
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (vars.isEmpty()) Icons.Default.ContentCopy else Icons.Default.PlayArrow,
                                        contentDescription = "Expand or copy snippet",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { editingSnippet = snippet },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit snippet"
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteSnippet(snippet) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete snippet",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Text(
                            text = snippet.body,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (vars.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                vars.forEach { variable ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            text = "{$variable}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .testTag("add_snippet_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create snippet")
        }
    }

    if (showCreateDialog || editingSnippet != null) {
        SnippetEditorDialog(
            initial = editingSnippet,
            onDismiss = {
                showCreateDialog = false
                editingSnippet = null
            },
            onSave = { title, shortcut, body, cat ->
                val target = editingSnippet
                viewModel.saveSnippet(
                    existingId = target?.id,
                    existingRemoteId = target?.remoteId,
                    title = title,
                    shortcut = shortcut,
                    body = body,
                    category = cat
                )
                showCreateDialog = false
                editingSnippet = null
            }
        )
    }

    if (expandingSnippet != null) {
        ExpandSnippetTestDialog(
            snippet = expandingSnippet!!,
            onDismiss = { expandingSnippet = null },
            onCopyExpanded = { expanded ->
                viewModel.copyToSystemClipboard(expanded)
                expandingSnippet = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnippetEditorDialog(
    initial: SnippetEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, SnippetCategory) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var shortcut by remember { mutableStateOf(initial?.shortcut ?: "/") }
    var body by remember { mutableStateOf(initial?.body ?: "") }
    var category by remember {
        mutableStateOf(SnippetCategory.fromWire(initial?.category))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Macro / Snippet" else "Edit Snippet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shortcut,
                    onValueChange = { shortcut = it },
                    label = { Text("Shortcut (e.g., /meeting)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Body (use {variable} tokens)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SnippetCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, shortcut, body, category) },
                enabled = title.isNotBlank() && shortcut.isNotBlank() && body.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExpandSnippetTestDialog(
    snippet: SnippetEntity,
    onDismiss: () -> Unit,
    onCopyExpanded: (String) -> Unit
) {
    val vars = remember(snippet.body) { ClipboardCategorizer.extractTemplateVariables(snippet.body) }
    val values = remember(snippet.id) { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Expand ${snippet.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vars.forEach { key ->
                    OutlinedTextField(
                        value = values[key] ?: "",
                        onValueChange = { values[key] = it },
                        label = { Text(key) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ClipboardCategorizer.expandTemplate(snippet.body, values),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCopyExpanded(ClipboardCategorizer.expandTemplate(snippet.body, values))
                }
            ) {
                Text("Copy Expanded")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
