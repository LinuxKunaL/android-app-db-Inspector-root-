package com.samir.rootsql.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samir.rootsql.data.model.ColumnInfo
import com.samir.rootsql.data.model.TablePage
import com.samir.rootsql.data.model.TableRow
import com.samir.rootsql.ui.MainViewModel
import com.samir.rootsql.ui.PAGE_SIZE
import com.samir.rootsql.ui.UiState
import com.samir.rootsql.ui.theme.MonoTextStyle

private val CELL_WIDTH = 160.dp
private val GUTTER_WIDTH = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableDataScreen(
    state: UiState,
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val page = state.page
    val table = state.selectedTable

    // Dialog state
    var cellTarget by remember { mutableStateOf<CellTarget?>(null) }
    var deleteRowId by remember { mutableStateOf<Long?>(null) }
    var showAddRow by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(table?.name ?: "Table", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        page?.let {
                            Text(
                                "${it.totalRows} rows",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        floatingActionButton = {
            if (page != null && page.hasRowId) {
                ExtendedFloatingActionButton(
                    onClick = { showAddRow = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add row") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.pageLoading && page == null -> LoadingBox("Reading rows…")
                page == null -> EmptyState(Icons.Outlined.Info, "No data")
                else -> {
                    if (!page.hasRowId) ReadOnlyNote()
                    DataGrid(
                        page = page,
                        onCellClick = { row, colIndex ->
                            cellTarget = CellTarget(
                                rowId = row.rowId,
                                column = page.columns[colIndex].name,
                                value = row.values[colIndex],
                                editable = page.hasRowId && row.rowId != null,
                            )
                        },
                        onRowLongPress = { row ->
                            if (page.hasRowId) row.rowId?.let { deleteRowId = it }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PaginationBar(state = state, page = page, vm = vm)
                }
            }
        }
    }

    // --- Cell view / JSON tree / edit dialog ---
    cellTarget?.let { target ->
        CellDialog(
            column = target.column,
            value = target.value,
            editable = target.editable,
            onDismiss = { cellTarget = null },
            onSave = { newValue ->
                target.rowId?.let { vm.editCell(it, target.column, newValue) }
                cellTarget = null
            },
        )
    }

    // --- Delete confirm ---
    deleteRowId?.let { rid ->
        AlertDialog(
            onDismissRequest = { deleteRowId = null },
            title = { Text("Delete row?") },
            text = { Text("This removes rowid $rid from the working copy. Save afterwards to write it back to the app.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRow(rid)
                    deleteRowId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteRowId = null }) { Text("Cancel") }
            },
        )
    }

    // --- Add row dialog ---
    if (showAddRow && page != null) {
        AddRowDialog(
            columns = page.columns,
            onDismiss = { showAddRow = false },
            onInsert = { values ->
                vm.insertRow(values)
                showAddRow = false
            },
        )
    }
}

private data class CellTarget(
    val rowId: Long?,
    val column: String,
    val value: String?,
    val editable: Boolean,
)

// ---------------------------------------------------------------------
// The scrollable grid
// ---------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DataGrid(
    page: TablePage,
    onCellClick: (TableRow, Int) -> Unit,
    onRowLongPress: (TableRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val border = MaterialTheme.colorScheme.outlineVariant
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val rowBg = MaterialTheme.colorScheme.surface
    val rowAltBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Column(modifier.horizontalScroll(hScroll)) {
        // Header (stays put vertically; scrolls horizontally with the body)
        Row(Modifier.background(headerBg)) {
            HeaderCell("#", GUTTER_WIDTH, border)
            page.columns.forEach { col ->
                HeaderCell(col.name, CELL_WIDTH, border, col.primaryKey)
            }
        }

        // Body
        Column(Modifier.verticalScroll(vScroll)) {
            page.rows.forEachIndexed { rowIndex, row ->
                Row(Modifier.background(if (rowIndex % 2 == 0) rowBg else rowAltBg)) {
                    // Gutter: rowid / index (long-press deletes the row)
                    Box(
                        Modifier
                            .width(GUTTER_WIDTH)
                            .heightIn(min = 48.dp)
                            .border(0.5.dp, border)
                            .combinedClickable(
                                onClick = { onCellClick(row, 0) },
                                onLongClick = { onRowLongPress(row) },
                            )
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            row.rowId?.toString() ?: "${rowIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Cells: tap to view/edit, long-press to delete the row
                    row.values.forEachIndexed { colIndex, value ->
                        DataCell(
                            value = value,
                            border = border,
                            onClick = { onCellClick(row, colIndex) },
                            onLongClick = { onRowLongPress(row) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    border: androidx.compose.ui.graphics.Color,
    isPk: Boolean = false,
) {
    Row(
        Modifier
            .width(width)
            .heightIn(min = 48.dp)
            .border(0.5.dp, border)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPk) {
            Icon(
                Icons.Default.Key, null,
                Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DataCell(
    value: String?,
    border: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .width(CELL_WIDTH)
            .heightIn(min = 48.dp)
            .border(0.5.dp, border)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            value == null -> Text(
                "NULL",
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
            looksLikeJson(value) -> Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        if (value.trim().startsWith("[")) "[ ]" else "{ }",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    value.trim(),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            else -> Text(
                value,
                style = MonoTextStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------
// Pagination
// ---------------------------------------------------------------------

@Composable
private fun PaginationBar(state: UiState, page: TablePage, vm: MainViewModel) {
    val start = if (page.totalRows == 0L) 0 else state.offset + 1
    val end = minOf(state.offset + PAGE_SIZE, page.totalRows.toInt())
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = { vm.prevPage() },
                enabled = state.offset > 0 && !state.pageLoading,
            ) { Icon(Icons.Default.ChevronLeft, "Previous page") }

            Text(
                "$start–$end of ${page.totalRows}",
                style = MaterialTheme.typography.labelLarge,
            )

            IconButton(
                onClick = { vm.nextPage() },
                enabled = state.offset + PAGE_SIZE < page.totalRows && !state.pageLoading,
            ) { Icon(Icons.Default.ChevronRight, "Next page") }
        }
    }
}

@Composable
private fun ReadOnlyNote() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                "Read-only: this is a view or a WITHOUT ROWID table, so cells can't be edited here.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ---------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------

@Composable
private fun AddRowDialog(
    columns: List<ColumnInfo>,
    onDismiss: () -> Unit,
    onInsert: (Map<String, String?>) -> Unit,
) {
    // Per-column entry: text + explicit-null flag.
    val texts = remember { mutableStateListOf(*Array(columns.size) { "" }) }
    val nulls = remember { mutableStateListOf(*Array(columns.size) { true }) }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert new row") },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                columns.forEachIndexed { i, col ->
                    val label = buildString {
                        append(col.name)
                        if (col.type.isNotBlank()) append("  (${col.type})")
                        if (col.primaryKey) append("  • PK")
                    }
                    OutlinedTextField(
                        value = texts[i],
                        onValueChange = { texts[i] = it; nulls[i] = false },
                        label = { Text(label) },
                        placeholder = { Text(if (nulls[i]) "NULL / default" else "") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    "Empty fields are inserted as NULL (or the column default). " +
                        "Type to set a value.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val values = buildMap {
                    columns.forEachIndexed { i, col ->
                        // Only include columns the user actually typed into; leave the
                        // rest out so SQLite applies defaults / autoincrement.
                        if (!nulls[i] && texts[i].isNotEmpty()) put(col.name, texts[i])
                    }
                }
                onInsert(values)
            }) { Text("Insert") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
