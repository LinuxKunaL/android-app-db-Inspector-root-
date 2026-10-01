package com.samir.rootsql.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.samir.rootsql.ui.theme.MonoTextStyle
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

// Syntax-highlight accents (kept readable in both light and dark).
private val JsonStringColor = Color(0xFF66BB6A)   // green
private val JsonNumberColor = Color(0xFFE0913C)   // orange

/** A quick, cheap check before we try to parse. */
fun looksLikeJson(s: String?): Boolean {
    val t = s?.trim() ?: return false
    return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))
}

private fun parseJson(s: String): Any? = runCatching { JSONTokener(s.trim()).nextValue() }
    .getOrNull()
    ?.takeIf { it is JSONObject || it is JSONArray }

private fun prettyJson(s: String): String = when (val v = parseJson(s)) {
    is JSONObject -> v.toString(2)
    is JSONArray -> v.toString(2)
    else -> s
}

private enum class CellMode { TREE, RAW, EDIT }

/**
 * One dialog to view / format / edit any cell value. JSON objects & arrays get a
 * collapsible tree and a pretty-printed raw view; everything else is plain text.
 */
@Composable
fun CellDialog(
    column: String,
    value: String?,
    editable: Boolean,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    val parsed = remember(value) { if (value != null) parseJson(value) else null }
    val isJson = parsed != null

    var mode by remember {
        mutableStateOf(
            when {
                isJson -> CellMode.TREE
                editable -> CellMode.EDIT
                else -> CellMode.RAW
            }
        )
    }
    var editText by remember { mutableStateOf(value ?: "") }
    var isNull by remember { mutableStateOf(value == null) }
    val clipboard = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 640.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            column,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (isJson) "JSON" else "text",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(value ?: ""))
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy value")
                    }
                }

                Spacer(Modifier.size(10.dp))

                // Mode switcher
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isJson) {
                        FilterChip(mode == CellMode.TREE, { mode = CellMode.TREE }, { Text("Tree") })
                    }
                    FilterChip(
                        mode == CellMode.RAW,
                        { mode = CellMode.RAW },
                        { Text(if (isJson) "Raw" else "Text") },
                    )
                    if (editable) {
                        FilterChip(mode == CellMode.EDIT, { mode = CellMode.EDIT }, { Text("Edit") })
                    }
                }

                Spacer(Modifier.size(10.dp))
                HorizontalDivider()

                // Body
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp)) {
                    when (mode) {
                        CellMode.TREE -> {
                            val v = rememberScrollState()
                            val h = rememberScrollState()
                            Column(Modifier.verticalScroll(v).horizontalScroll(h)) {
                                JsonNode(key = null, value = parsed, depth = 0)
                            }
                        }
                        CellMode.RAW -> {
                            val v = rememberScrollState()
                            val h = rememberScrollState()
                            Column(Modifier.verticalScroll(v).horizontalScroll(h)) {
                                Text(
                                    if (isJson) prettyJson(value ?: "") else (value ?: "NULL"),
                                    style = MonoTextStyle,
                                    color = if (value == null)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        CellMode.EDIT -> {
                            val scroll = rememberScrollState()
                            Column(Modifier.verticalScroll(scroll)) {
                                OutlinedTextField(
                                    value = if (isNull) "" else editText,
                                    onValueChange = { editText = it; isNull = false },
                                    label = { Text("Value") },
                                    enabled = !isNull,
                                    textStyle = MonoTextStyle,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                                )
                                Spacer(Modifier.size(8.dp))
                                OutlinedButton(onClick = { isNull = !isNull }) {
                                    Text(if (isNull) "Value is NULL — enter text" else "Set to NULL")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.size(8.dp))
                HorizontalDivider()
                Spacer(Modifier.size(8.dp))

                // Footer
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    if (editable && mode == CellMode.EDIT) {
                        if (!isNull && looksLikeJson(editText) && parseJson(editText) != null) {
                            TextButton(onClick = { editText = prettyJson(editText) }) {
                                Text("Format")
                            }
                        }
                        TextButton(onClick = { onSave(if (isNull) null else editText) }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Collapsible JSON tree
// ---------------------------------------------------------------------

@Composable
private fun JsonNode(key: String?, value: Any?, depth: Int) {
    when (value) {
        is JSONObject, is JSONArray -> BranchNode(key, value, depth)
        else -> LeafRow(key, value, depth)
    }
}

@Composable
private fun BranchNode(key: String?, node: Any, depth: Int) {
    val isArray = node is JSONArray
    val count = if (isArray) (node as JSONArray).length() else (node as JSONObject).length()
    val open = if (isArray) "[" else "{"
    val close = if (isArray) "]" else "}"
    // Auto-expand the first couple of levels; deeper stays collapsed.
    var expanded by remember { mutableStateOf(depth < 2) }
    val indent = (depth * 16).dp

    Column {
        Row(
            Modifier
                .clickable { expanded = !expanded }
                .padding(start = indent, top = 3.dp, bottom = 3.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (key != null) {
                Text(
                    "\"$key\"",
                    style = MonoTextStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(": ", style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (expanded) open else "$open … $close",
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    if (isArray) "$count items" else "$count keys",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (expanded) {
            if (isArray) {
                val arr = node as JSONArray
                for (i in 0 until arr.length()) JsonNode(i.toString(), arr.get(i), depth + 1)
            } else {
                val obj = node as JSONObject
                obj.keys().forEach { k -> JsonNode(k, obj.get(k), depth + 1) }
            }
            Text(
                close,
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = indent + 18.dp, bottom = 3.dp),
            )
        }
    }
}

@Composable
private fun LeafRow(key: String?, value: Any?, depth: Int) {
    val nullColor = MaterialTheme.colorScheme.onSurfaceVariant
    val boolColor = MaterialTheme.colorScheme.tertiary
    val isNullish = value == null || value == JSONObject.NULL

    val display: String
    val color: Color
    val italic: Boolean
    when {
        isNullish -> { display = "null"; color = nullColor; italic = true }
        value is String -> { display = "\"$value\""; color = JsonStringColor; italic = false }
        value is Boolean -> { display = value.toString(); color = boolColor; italic = false }
        else -> { display = value.toString(); color = JsonNumberColor; italic = false }
    }

    Row(
        Modifier.padding(start = (depth * 16).dp + 18.dp, top = 3.dp, bottom = 3.dp, end = 8.dp),
    ) {
        if (key != null) {
            Text(
                "\"$key\"",
                style = MonoTextStyle,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(": ", style = MonoTextStyle, color = nullColor)
        }
        Text(
            display,
            style = MonoTextStyle,
            color = color,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        )
    }
}
