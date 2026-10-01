package com.samir.rootsql.data.model

import android.graphics.drawable.Drawable

/** An installed application shown in the first screen. */
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystem: Boolean,
)

/** A database file discovered inside an app's private data directory (via root). */
data class DbFile(
    val name: String,           // e.g. "app_database.db"
    val absolutePath: String,   // e.g. "/data/data/<pkg>/databases/app_database.db"
    val sizeBytes: Long,
    val owner: String,          // "uid:gid" of the original file, restored on save
    val mode: String,           // octal perms, e.g. "660"
    val seContext: String,      // SELinux context, restored on save
)

/** One table (or view) inside an opened database. */
data class DbTable(
    val name: String,
    val rowCount: Long,
    val isView: Boolean,
)

/** Column metadata as reported by PRAGMA table_info. */
data class ColumnInfo(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val primaryKey: Boolean,
)

/**
 * A page of rows for the table viewer.
 * [rowId] carries SQLite's implicit rowid so edits/deletes can target a single row.
 */
data class TablePage(
    val columns: List<ColumnInfo>,
    val rows: List<TableRow>,
    val totalRows: Long,
    val hasRowId: Boolean,
)

data class TableRow(
    val rowId: Long?,                    // null for WITHOUT ROWID / views
    val values: List<String?>,           // stringified cell values, aligned with columns
)

// ---------------------------------------------------------------------
// SharedPreferences (shared_prefs/*.xml)
// ---------------------------------------------------------------------

/** A SharedPreferences XML file discovered inside an app's data directory. */
data class PrefFile(
    val name: String,           // e.g. "settings.xml"
    val absolutePath: String,   // e.g. "/data/data/<pkg>/shared_prefs/settings.xml"
    val sizeBytes: Long,
    val owner: String,          // "uid:gid", restored on save
    val mode: String,           // octal perms, restored on save
    val seContext: String,      // SELinux context, restored on save
    val entryCount: Int,        // number of key/value entries (0 = unknown / unread)
)

/** SharedPreferences supports exactly these value types. */
enum class PrefType { STRING, INT, LONG, FLOAT, BOOLEAN, STRING_SET }

/**
 * One key/value entry in a SharedPreferences file.
 * For [PrefType.STRING_SET] the members are stored newline-joined in [value].
 */
data class PrefEntry(
    val key: String,
    val value: String?,          // null only for a null string entry
    val type: PrefType,
)
