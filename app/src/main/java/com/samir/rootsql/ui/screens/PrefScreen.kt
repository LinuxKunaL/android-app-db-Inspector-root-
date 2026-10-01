package com.samir.rootsql.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samir.rootsql.data.model.PrefEntry
import com.samir.rootsql.data.model.PrefType
import com.samir.rootsql.ui.MainViewModel
import com.samir.rootsql.ui.UiState
import com.samir.rootsql.ui.theme.MonoTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrefScreen(
    state: UiState,
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    var editTarget by remember { mutableStateOf<PrefEntry?>(null) }
    var deleteKey by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.openedPref?.name ?: "Preferences",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${state.prefEntries.size} entries",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.prefSaving) {
                        CircularProgressIndicator(Modifier.size(22.dp).padding(end = 8.dp))
                    } else {
                        IconButton(onClick = { vm.savePref() }, enabled = state.prefDirty) {
                            Icon(
                                Icons.Default.Save,
                                "Save to device",
                                tint = if (state.prefDirty) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add entry") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.prefDirty) UnsavedPrefBanner()

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.prefLoading -> LoadingBox("Reading preferences…")
                    state.prefEntries.isEmpty() -> EmptyState(
                        Icons.Outlined.Inbox,
                        "No entries",
                        "This preferences file is empty. Use “Add entry” to create one.",
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        items(state.prefEntries, key = { it.key }) { entry ->
                            PrefEntryCard(
                                entry = entry,
                                onClick = { editTarget = entry },
                                onLongClick = { deleteKey = entry.key },
                            )
                            Spacer(Modifier.size(10.dp))
                        }
                    }
                }
            }
        }
    }

    // --- Edit ---
    editTarget?.let { entry ->
        EditPrefDialog(
            entry = entry,
            onDismiss = { editTarget = null },
            onSave = { newValue ->
                vm.editPref(entry.key, newValue)
                editTarget = null
            },
        )
    }

    // --- Delete confirm ---
    deleteKey?.let { key ->
        AlertDialog(
            onDismissRequest = { deleteKey = null },
            title = { Text("Delete entry?") },
            text = { Text("Remove “$key”? Save afterwards to write it back to the app.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePref(key)
                    deleteKey = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteKey = null }) { Text("Cancel") }
            },
        )
    }

    // --- Add ---
    if (showAdd) {
        AddPrefDialog(
            existingKeys = state.prefEntries.map { it.key }.toSet(),
            onDismiss = { showAdd = false },
            onAdd = { entry ->
                vm.addPref(entry)
                showAdd = false
            },
        )
    }
}

@Composable
private fun UnsavedPrefBanner() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        AssistChip(
            onClick = {},
            label = { Text("Unsaved changes — tap Save to write back to the app") },
            leadingIcon = { Icon(Icons.Default.Save, null, Modifier.size(18.dp)) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrefEntryCard(
    entry: PrefEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.key,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                TypeTag(entry.type)
            }
            Spacer(Modifier.size(6.dp))
            if (entry.value == null) {
                Text(
                    "null",
                    style = MonoTextStyle,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    entry.value.ifEmpty { "(empty)" },
                    style = MonoTextStyle,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun TypeTag(type: PrefType) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            type.label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun PrefType.label(): String = when (this) {
    PrefType.STRING -> "string"
    PrefType.INT -> "int"
    PrefType.LONG -> "long"
    PrefType.FLOAT -> "float"
    PrefType.BOOLEAN -> "boolean"
    PrefType.STRING_SET -> "string set"
}

// ---------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------

@Composable
private fun EditPrefDialog(
    entry: PrefEntry,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit “${entry.key}”", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            when (entry.type) {
                PrefType.BOOLEAN -> BooleanEditor(entry.value, onSave)
                else -> ValueEditor(entry.type, entry.value, onSave)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Boolean picker: two buttons that commit immediately. */
@Composable
private fun BooleanEditor(current: String?, onSave: (String?) -> Unit) {
    Column {
        Text(
            "Current: ${current ?: "null"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Row {
            OutlinedButton(onClick = { onSave("true") }) { Text("true") }
            Spacer(Modifier.size(12.dp))
            OutlinedButton(onClick = { onSave("false") }) { Text("false") }
        }
    }
}

@Composable
private fun ValueEditor(type: PrefType, current: String?, onSave: (String?) -> Unit) {
    var text by remember { mutableStateOf(current ?: "") }
    val isSet = type == PrefType.STRING_SET
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(if (isSet) "Members (one per line)" else "Value") },
            textStyle = MonoTextStyle,
            singleLine = !isSet,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!isSet && (type == PrefType.INT || type == PrefType.LONG || type == PrefType.FLOAT)) {
            Text(
                "Numeric (${type.label()}) — enter digits only.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            TextButton(onClick = { onSave(text) }) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPrefDialog(
    existingKeys: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (PrefEntry) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PrefType.STRING) }
    val scroll = rememberScrollState()
    val dup = key.isNotBlank() && key in existingKeys

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add entry") },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Key") },
                    isError = dup,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dup) {
                    Text(
                        "Key already exists — it will be overwritten.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text("Type", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(scroll).padding(vertical = 4.dp)) {
                    PrefType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label()) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                if (type == PrefType.BOOLEAN) {
                    Text("Value", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.padding(top = 4.dp)) {
                        FilterChip(
                            selected = value == "true",
                            onClick = { value = "true" },
                            label = { Text("true") },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        FilterChip(
                            selected = value == "false",
                            onClick = { value = "false" },
                            label = { Text("false") },
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = {
                            Text(
                                if (type == PrefType.STRING_SET) "Members (one per line)"
                                else "Value"
                            )
                        },
                        textStyle = MonoTextStyle,
                        singleLine = type != PrefType.STRING_SET,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = key.isNotBlank() &&
                    (type != PrefType.BOOLEAN || value.isNotBlank()),
                onClick = {
                    val v = if (type == PrefType.BOOLEAN && value.isBlank()) "false" else value
                    onAdd(PrefEntry(key.trim(), v, type))
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
