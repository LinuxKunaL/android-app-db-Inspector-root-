package com.samir.rootsql.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samir.rootsql.ui.MainViewModel
import com.samir.rootsql.ui.theme.MonoTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqlConsoleScreen(
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    var sql by remember { mutableStateOf("SELECT name FROM sqlite_master WHERE type='table';") }
    val result by vm.consoleResult.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SQL Console") },
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = sql,
                onValueChange = { sql = it },
                label = { Text("SQL statement") },
                textStyle = MonoTextStyle,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            )
            Button(
                onClick = { vm.runSql(sql) },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Text("  Run", fontWeight = FontWeight.SemiBold)
            }

            Text(
                "SELECT / PRAGMA return rows below. INSERT / UPDATE / DELETE / CREATE " +
                    "run against the working copy — remember to Save afterwards.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            HorizontalDivider()

            val res = result
            if (res == null) {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Run a query to see results",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val h = rememberScrollState()
                val v = rememberScrollState()
                val border = MaterialTheme.colorScheme.outlineVariant
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .horizontalScroll(h)
                        .verticalScroll(v),
                ) {
                    Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                        res.columns.forEach {
                            ConsoleCell(it.name, border, header = true)
                        }
                    }
                    res.rows.forEach { row ->
                        Row {
                            row.values.forEach { value ->
                                ConsoleCell(value ?: "NULL", border, isNull = value == null)
                            }
                        }
                    }
                    if (res.rows.isEmpty()) {
                        Text(
                            "0 rows",
                            Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleCell(
    text: String,
    border: androidx.compose.ui.graphics.Color,
    header: Boolean = false,
    isNull: Boolean = false,
) {
    Box(
        Modifier
            .width(150.dp)
            .heightIn(min = 40.dp)
            .border(0.5.dp, border)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = if (header) MaterialTheme.typography.labelLarge else MonoTextStyle,
            fontWeight = if (header) FontWeight.SemiBold else null,
            fontStyle = if (isNull) FontStyle.Italic else null,
            color = if (isNull) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
