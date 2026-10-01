package com.samir.rootsql.data

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Xml
import com.samir.rootsql.data.model.AppInfo
import com.samir.rootsql.data.model.ColumnInfo
import com.samir.rootsql.data.model.DbFile
import com.samir.rootsql.data.model.DbTable
import com.samir.rootsql.data.model.PrefEntry
import com.samir.rootsql.data.model.PrefFile
import com.samir.rootsql.data.model.PrefType
import com.samir.rootsql.data.model.TablePage
import com.samir.rootsql.data.model.TableRow
import com.samir.rootsql.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader

/**
 * Everything the UI needs: enumerate apps, find their SQLite files with root,
 * pull a working copy into our own cache, run CRUD against it, and push changes
 * back to the original protected location (restoring owner / mode / SELinux).
 */
class SqliteRepository(private val appContext: Context) {

    // The currently opened database (a copy living in our cache dir).
    private var db: SQLiteDatabase? = null
    private var openedDbFile: DbFile? = null
    private var localCopyPath: String? = null

    // The currently opened SharedPreferences file, held in memory until saved.
    private var openedPref: PrefFile? = null
    private val prefEntries = mutableListOf<PrefEntry>()

    // ---------------------------------------------------------------------
    // 1. Installed apps
    // ---------------------------------------------------------------------

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { info ->
                AppInfo(
                    label = pm.getApplicationLabel(info).toString(),
                    packageName = info.packageName,
                    icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    // ---------------------------------------------------------------------
    // 2. Discover database files inside an app's data dir (root)
    // ---------------------------------------------------------------------

    suspend fun findDatabases(packageName: String): List<DbFile> = withContext(Dispatchers.IO) {
        val base = "/data/data/$packageName"
        // Detect SQLite files by their content, not by extension. This is the fix
        // that makes React Native / Expo AsyncStorage show up: RN stores its data
        // in a SQLite DB literally named "RKStorage" (no .db suffix), and WebView /
        // other libs use extensionless files too. Every SQLite file starts with the
        // 16-byte magic header "SQLite format 3\0", so we match on the first 15
        // printable bytes. We prune huge/irrelevant dirs (cache, native libs) for
        // speed, and skip -wal/-shm/-journal sidecars (they have their own headers).
        val paths = RootManager.execOut(
            "find $base \\( -name cache -o -name code_cache -o -name lib \\) -prune -o " +
                "-type f -print 2>/dev/null | while IFS= read -r f; do " +
                "case \"\$f\" in *-wal|*-shm|*-journal) continue;; esac; " +
                "[ \"\$(head -c 15 \"\$f\" 2>/dev/null)\" = 'SQLite format 3' ] && echo \"\$f\"; " +
                "done"
        ).filter { it.isNotBlank() }

        paths.mapNotNull { path -> stat(path) }
            .sortedBy { it.name.lowercase() }
    }

    // ---------------------------------------------------------------------
    // 2b. Discover SharedPreferences XML files (root)
    // ---------------------------------------------------------------------

    suspend fun findPrefs(packageName: String): List<PrefFile> = withContext(Dispatchers.IO) {
        val base = "/data/data/$packageName/shared_prefs"
        val paths = RootManager.execOut(
            "find $base -type f -name '*.xml' 2>/dev/null"
        ).filter { it.isNotBlank() }

        paths.mapNotNull { path ->
            val meta = stat(path) ?: return@mapNotNull null
            // Cheap entry count: number of top-level typed elements in the file.
            val count = RootManager.execOut(
                "grep -oE '<(string|int|long|float|boolean|set)[ >]' \"$path\" 2>/dev/null | wc -l"
            ).firstOrNull()?.trim()?.toIntOrNull() ?: 0
            PrefFile(
                name = meta.name,
                absolutePath = meta.absolutePath,
                sizeBytes = meta.sizeBytes,
                owner = meta.owner,
                mode = meta.mode,
                seContext = meta.seContext,
                entryCount = count,
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** Read size / owner / mode / SELinux context for a single file via root. */
    private suspend fun stat(path: String): DbFile? {
        // %s=size %u:%g=owner %a=octal-mode
        val meta = RootManager.execOut("stat -c '%s|%u:%g|%a' \"$path\" 2>/dev/null")
            .firstOrNull()?.split("|") ?: return null
        if (meta.size < 3) return null
        val se = RootManager.execOut(
            "stat -c '%C' \"$path\" 2>/dev/null"
        ).firstOrNull()?.takeIf { it.isNotBlank() && it != "?" } ?: ""
        return DbFile(
            name = path.substringAfterLast('/'),
            absolutePath = path,
            sizeBytes = meta[0].toLongOrNull() ?: 0L,
            owner = meta[1],
            mode = meta[2],
            seContext = se,
        )
    }

    // ---------------------------------------------------------------------
    // 3. Open a database (copy it out of the protected dir, then open r/w)
    // ---------------------------------------------------------------------

    suspend fun open(dbFile: DbFile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            closeInternal()
            val workDir = File(appContext.cacheDir, "work").apply { mkdirs() }
            // Clear old working copies.
            workDir.listFiles()?.forEach { it.delete() }

            val dest = File(workDir, dbFile.name)
            val src = dbFile.absolutePath

            // Copy the db and its WAL/SHM siblings, then make them ours to read/write.
            // restorecon relabels the copies to our app's SELinux domain so the
            // untrusted_app process can actually open them (DAC perms alone aren't enough).
            val res = RootManager.exec(
                "cp -f \"$src\" \"${dest.absolutePath}\"; " +
                    "[ -f \"$src-wal\" ] && cp -f \"$src-wal\" \"${dest.absolutePath}-wal\"; " +
                    "[ -f \"$src-shm\" ] && cp -f \"$src-shm\" \"${dest.absolutePath}-shm\"; " +
                    "chmod 666 \"${dest.absolutePath}\"* ; " +
                    "chown ${androidUidGid()} \"${dest.absolutePath}\"* ; " +
                    "restorecon \"${dest.absolutePath}\"*"
            )
            check(dest.exists()) { "Copy failed: ${res.err.joinToString()}" }

            db = SQLiteDatabase.openDatabase(
                dest.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            )
            openedDbFile = dbFile
            localCopyPath = dest.absolutePath
        }
    }

    /** Our own process uid, so the working copy is owned by us. */
    private fun androidUidGid(): String {
        val uid = android.os.Process.myUid()
        return "$uid:$uid"
    }

    // ---------------------------------------------------------------------
    // 4. Tables & schema
    // ---------------------------------------------------------------------

    suspend fun listTables(): List<DbTable> = withContext(Dispatchers.IO) {
        val d = db ?: return@withContext emptyList()
        val out = mutableListOf<DbTable>()
        d.rawQuery(
            "SELECT name, type FROM sqlite_master " +
                "WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' " +
                "ORDER BY type, name",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)
                val isView = c.getString(1) == "view"
                val count = runCatching {
                    d.rawQuery("SELECT COUNT(*) FROM ${quote(name)}", null).use {
                        if (it.moveToFirst()) it.getLong(0) else 0L
                    }
                }.getOrDefault(0L)
                out += DbTable(name, count, isView)
            }
        }
        out
    }

    suspend fun columnsOf(table: String): List<ColumnInfo> = withContext(Dispatchers.IO) {
        val d = db ?: return@withContext emptyList()
        d.rawQuery("PRAGMA table_info(${quote(table)})", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ColumnInfo(
                            name = c.getString(c.getColumnIndexOrThrow("name")),
                            type = c.getString(c.getColumnIndexOrThrow("type")) ?: "",
                            notNull = c.getInt(c.getColumnIndexOrThrow("notnull")) == 1,
                            primaryKey = c.getInt(c.getColumnIndexOrThrow("pk")) > 0,
                        )
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // 5. Paged table data (READ)
    // ---------------------------------------------------------------------

    suspend fun page(
        table: String,
        limit: Int,
        offset: Int,
    ): TablePage = withContext(Dispatchers.IO) {
        val d = db ?: error("No database open")
        val columns = columnsOf(table)

        val total = d.rawQuery("SELECT COUNT(*) FROM ${quote(table)}", null).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }

        // Prefer rowid so individual rows can be edited/deleted. Fall back for
        // WITHOUT ROWID tables and views, which become read-only. rawQuery is lazy,
        // so we probe with a forced execution to learn if rowid is selectable.
        val hasRowId = runCatching {
            d.rawQuery("SELECT rowid FROM ${quote(table)} LIMIT 0", null)
                .use { it.moveToFirst() }
            true
        }.getOrDefault(false)

        val cursor: Cursor = if (hasRowId) {
            d.rawQuery(
                "SELECT rowid AS __rid, * FROM ${quote(table)} LIMIT ? OFFSET ?",
                arrayOf(limit.toString(), offset.toString())
            )
        } else {
            d.rawQuery(
                "SELECT * FROM ${quote(table)} LIMIT ? OFFSET ?",
                arrayOf(limit.toString(), offset.toString())
            )
        }

        val rows = cursor.use { c ->
            val firstDataCol = if (hasRowId) 1 else 0
            buildList {
                while (c.moveToNext()) {
                    val rid = if (hasRowId) c.getLong(0) else null
                    val values = ArrayList<String?>(columns.size)
                    for (i in columns.indices) {
                        values += cellToString(c, firstDataCol + i)
                    }
                    add(TableRow(rid, values))
                }
            }
        }
        TablePage(columns, rows, total, hasRowId)
    }

    private fun cellToString(c: Cursor, index: Int): String? = when (c.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_BLOB -> "«BLOB ${c.getBlob(index).size} bytes»"
        else -> c.getString(index)
    }

    // ---------------------------------------------------------------------
    // 6. CRUD (against the local working copy)
    // ---------------------------------------------------------------------

    suspend fun updateCell(
        table: String,
        rowId: Long,
        column: String,
        newValue: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val d = db ?: error("No database open")
            val cv = ContentValues().apply {
                if (newValue == null) putNull(column) else put(column, newValue)
            }
            val n = d.update(quote(table), cv, "rowid=?", arrayOf(rowId.toString()))
            check(n == 1) { "Update affected $n rows" }
        }
    }

    suspend fun insertRow(
        table: String,
        values: Map<String, String?>,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val d = db ?: error("No database open")
            if (values.isEmpty()) {
                // No values supplied: let SQLite apply every default.
                d.execSQL("INSERT INTO ${quote(table)} DEFAULT VALUES")
                d.rawQuery("SELECT last_insert_rowid()", null).use {
                    if (it.moveToFirst()) it.getLong(0) else -1L
                }
            } else {
                val cv = ContentValues().apply {
                    values.forEach { (k, v) -> if (v == null) putNull(k) else put(k, v) }
                }
                d.insertOrThrow(quote(table), null, cv)
            }
        }
    }

    suspend fun deleteRow(table: String, rowId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val d = db ?: error("No database open")
                val n = d.delete(quote(table), "rowid=?", arrayOf(rowId.toString()))
                check(n == 1) { "Delete affected $n rows" }
            }
        }

    /** Run an arbitrary SQL statement/query (SQL console). */
    suspend fun executeRaw(sql: String): Result<TablePage?> = withContext(Dispatchers.IO) {
        runCatching {
            val d = db ?: error("No database open")
            val trimmed = sql.trim().trimEnd(';')
            val isQuery = trimmed.substringBefore(' ').lowercase() in
                setOf("select", "pragma", "explain", "with")
            if (isQuery) {
                d.rawQuery(trimmed, null).use { c ->
                    val cols = c.columnNames.map { ColumnInfo(it, "", false, false) }
                    val rows = buildList {
                        while (c.moveToNext()) {
                            add(TableRow(null, (0 until c.columnCount).map { cellToString(c, it) }))
                        }
                    }
                    TablePage(cols, rows, rows.size.toLong(), false)
                }
            } else {
                d.execSQL(trimmed)
                null
            }
        }
    }

    // ---------------------------------------------------------------------
    // 7. Save the working copy back into the protected app dir (root)
    // ---------------------------------------------------------------------

    suspend fun saveBack(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = openedDbFile ?: error("Nothing open")
            val local = localCopyPath ?: error("Nothing open")

            // Fold any WAL back into the main file, then close so it's flushed.
            db?.let {
                runCatching { it.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c -> c.moveToFirst() } }
                it.close()
            }
            db = null

            val dst = target.absolutePath
            val res = RootManager.exec(
                // Overwrite the original data, drop any stale WAL/SHM, then restore
                // the exact ownership / permissions / SELinux label the app expects.
                "cp -f \"$local\" \"$dst\"; " +
                    "rm -f \"$dst-wal\" \"$dst-shm\"; " +
                    "chown ${target.owner} \"$dst\"; " +
                    "chmod ${target.mode} \"$dst\"; " +
                    (if (target.seContext.isNotBlank())
                        "chcon \"${target.seContext}\" \"$dst\"; " else "restorecon \"$dst\"; ")
            )
            check(res.isSuccess) { "Save failed: ${res.err.joinToString()}" }

            // Re-open the (freshly written) copy so editing can continue.
            db = SQLiteDatabase.openDatabase(local, null, SQLiteDatabase.OPEN_READWRITE)
        }
    }

    // ---------------------------------------------------------------------
    // 8. SharedPreferences: read, edit in memory, save back (root)
    // ---------------------------------------------------------------------

    /** Read + parse a prefs file. Result mirrors the in-memory working entries. */
    suspend fun openPref(pref: PrefFile): Result<List<PrefEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val xml = RootManager.execOut("cat \"${pref.absolutePath}\" 2>/dev/null")
                .joinToString("\n")
            val entries = parsePrefsXml(xml)
            openedPref = pref
            prefEntries.clear()
            prefEntries.addAll(entries)
            prefEntries.toList()
        }
    }

    /** Change an existing key's value (type is preserved). */
    fun setPref(key: String, newValue: String?): List<PrefEntry> {
        val i = prefEntries.indexOfFirst { it.key == key }
        if (i >= 0) prefEntries[i] = prefEntries[i].copy(value = newValue)
        return prefEntries.toList()
    }

    /** Add a new entry, or replace one with the same key. */
    fun addPref(entry: PrefEntry): List<PrefEntry> {
        val i = prefEntries.indexOfFirst { it.key == entry.key }
        if (i >= 0) prefEntries[i] = entry else prefEntries.add(entry)
        return prefEntries.toList()
    }

    fun deletePref(key: String): List<PrefEntry> {
        prefEntries.removeAll { it.key == key }
        return prefEntries.toList()
    }

    /** Serialize the working entries and root-write them over the original file. */
    suspend fun savePref(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pref = openedPref ?: error("No preferences file open")
            val xml = serializePrefs(prefEntries)
            // Stage in our cache, then root-copy over the original, restoring the
            // app's exact owner / mode / SELinux label so it keeps reading it.
            val staged = File(File(appContext.cacheDir, "work").apply { mkdirs() }, pref.name)
            staged.writeText(xml)
            val dst = pref.absolutePath
            val res = RootManager.exec(
                "cp -f \"${staged.absolutePath}\" \"$dst\"; " +
                    "chown ${pref.owner} \"$dst\"; " +
                    "chmod ${pref.mode} \"$dst\"; " +
                    (if (pref.seContext.isNotBlank())
                        "chcon \"${pref.seContext}\" \"$dst\"; " else "restorecon \"$dst\"; ")
            )
            check(res.isSuccess) { "Save failed: ${res.err.joinToString()}" }
            staged.delete()
            Unit
        }
    }

    private fun parsePrefsXml(xml: String): List<PrefEntry> {
        if (xml.isBlank()) return emptyList()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val out = mutableListOf<PrefEntry>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (val tag = parser.name) {
                    "string" -> {
                        val name = parser.getAttributeValue(null, "name")
                        val text = parser.nextText()   // consumes up to the END_TAG
                        if (name != null) out += PrefEntry(name, text, PrefType.STRING)
                    }
                    "int", "long", "float", "boolean" -> {
                        val name = parser.getAttributeValue(null, "name")
                        val value = parser.getAttributeValue(null, "value")
                        if (name != null) out += PrefEntry(name, value, prefTypeOf(tag))
                    }
                    "set" -> {
                        val name = parser.getAttributeValue(null, "name")
                        val items = mutableListOf<String>()
                        var e = parser.next()
                        while (!(e == XmlPullParser.END_TAG && parser.name == "set")) {
                            if (e == XmlPullParser.END_DOCUMENT) break
                            if (e == XmlPullParser.START_TAG && parser.name == "string") {
                                items += parser.nextText()
                            }
                            e = parser.next()
                        }
                        if (name != null) {
                            out += PrefEntry(name, items.joinToString("\n"), PrefType.STRING_SET)
                        }
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun prefTypeOf(tag: String): PrefType = when (tag) {
        "int" -> PrefType.INT
        "long" -> PrefType.LONG
        "float" -> PrefType.FLOAT
        "boolean" -> PrefType.BOOLEAN
        else -> PrefType.STRING
    }

    private fun serializePrefs(entries: List<PrefEntry>): String = buildString {
        append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n")
        append("<map>\n")
        for (e in entries) {
            when (e.type) {
                PrefType.STRING ->
                    append("    <string name=\"${xmlEscape(e.key)}\">${xmlEscape(e.value ?: "")}</string>\n")
                PrefType.STRING_SET -> {
                    append("    <set name=\"${xmlEscape(e.key)}\">\n")
                    e.value?.takeIf { it.isNotEmpty() }?.split("\n")?.forEach {
                        append("        <string>${xmlEscape(it)}</string>\n")
                    }
                    append("    </set>\n")
                }
                else -> {
                    val tag = when (e.type) {
                        PrefType.INT -> "int"
                        PrefType.LONG -> "long"
                        PrefType.FLOAT -> "float"
                        else -> "boolean"
                    }
                    append("    <$tag name=\"${xmlEscape(e.key)}\" value=\"${xmlEscape(e.value ?: "")}\" />\n")
                }
            }
        }
        append("</map>\n")
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun close() = closeInternal()

    private fun closeInternal() {
        runCatching { db?.close() }
        db = null
        openedDbFile = null
        localCopyPath = null
        openedPref = null
        prefEntries.clear()
    }

    val currentDbName: String? get() = openedDbFile?.name

    // Quote an identifier for safe interpolation ("my table" -> "\"my table\"").
    private fun quote(id: String) = "\"" + id.replace("\"", "\"\"") + "\""
}
