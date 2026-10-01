package com.samir.rootsql.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samir.rootsql.data.model.DbTable
import com.samir.rootsql.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableListScreen(
    state: UiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onTableClick: (DbTable) -> Unit,
    onConsole: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tables", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.openedDb?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onConsole) {
                        Icon(Icons.Default.Terminal, "SQL console")
                    }
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(22.dp).padding(end = 8.dp))
                    } else {
                        IconButton(onClick = onSave, enabled = state.dirty) {
                            Icon(
                                Icons.Default.Save,
                                "Save to device",
                                tint = if (state.dirty) MaterialTheme.colorScheme.primary
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.dirty) UnsavedBanner()

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.tablesLoading -> LoadingBox("Opening database…")
                    state.tables.isEmpty() -> EmptyState(
                        Icons.Outlined.TableChart,
                        "No tables in this database",
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        items(state.tables, key = { it.name + it.isView }) { table ->
                            TableCard(table) { onTableClick(table) }
                            Spacer(Modifier.size(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnsavedBanner() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AssistChip(
            onClick = {},
            label = { Text("Unsaved changes — tap Save to write back to the app") },
            leadingIcon = { Icon(Icons.Default.Save, null, Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun TableCard(table: DbTable, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (table.isView) Icons.Outlined.Visibility else Icons.Outlined.TableChart,
                null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(table.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (table.isView) "view" else "table",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Badge { Text("${table.rowCount}") }
        }
    }
}
