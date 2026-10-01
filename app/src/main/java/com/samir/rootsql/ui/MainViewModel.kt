package com.samir.rootsql.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samir.rootsql.data.SqliteRepository
import com.samir.rootsql.data.model.AppInfo
import com.samir.rootsql.data.model.DbFile
import com.samir.rootsql.data.model.DbTable
import com.samir.rootsql.data.model.PrefEntry
import com.samir.rootsql.data.model.PrefFile
import com.samir.rootsql.data.model.TablePage
import com.samir.rootsql.root.RootManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val PAGE_SIZE = 50

data class UiState(
    val rootChecked: Boolean = false,
    val rootAvailable: Boolean = false,

    val appsLoading: Boolean = false,
    val apps: List<AppInfo> = emptyList(),
    val query: String = "",
    val showSystem: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,

    val selectedApp: AppInfo? = null,
    val dbsLoading: Boolean = false,
    val databases: List<DbFile> = emptyList(),
    val prefs: List<PrefFile> = emptyList(),

    val openedPref: PrefFile? = null,
    val prefLoading: Boolean = false,
    val prefEntries: List<PrefEntry> = emptyList(),
    val prefDirty: Boolean = false,
    val prefSaving: Boolean = false,

    val openedDb: DbFile? = null,
    val tablesLoading: Boolean = false,
    val tables: List<DbTable> = emptyList(),

    val selectedTable: DbTable? = null,
    val pageLoading: Boolean = false,
    val page: TablePage? = null,
    val offset: Int = 0,

    val dirty: Boolean = false,           // unsaved edits in the working copy
    val saving: Boolean = false,
    val message: String? = null,          // transient snackbar text
) {
    val filteredApps: List<AppInfo>
        get() = apps
            .filter { showSystem || !it.isSystem }
            .filter { !favoritesOnly || it.packageName in favorites }
            .filter {
                query.isBlank() ||
                    it.label.contains(query, true) ||
                    it.packageName.contains(query, true)
            }
            // Favourited apps float to the top, then alphabetical.
            .sortedWith(
                compareByDescending<AppInfo> { it.packageName in favorites }
                    .thenBy { it.label.lowercase() }
            )
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SqliteRepository(app.applicationContext)
    private val favPrefs = app.getSharedPreferences("rootdb_favorites", Application.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(favorites = favPrefs.getStringSet("pkgs", emptySet())?.toSet() ?: emptySet())
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private fun update(block: (UiState) -> UiState) = _state.update(block)

    // ---- Favourites ---------------------------------------------------
    fun toggleFavorite(packageName: String) = update {
        val next = if (packageName in it.favorites) it.favorites - packageName
        else it.favorites + packageName
        favPrefs.edit().putStringSet("pkgs", next).apply()
        it.copy(favorites = next)
    }

    fun toggleFavoritesOnly() = update { it.copy(favoritesOnly = !it.favoritesOnly) }

    // ---- Root ---------------------------------------------------------
    fun checkRoot() = viewModelScope.launch {
        val ok = RootManager.isRootAvailable()
        update { it.copy(rootChecked = true, rootAvailable = ok) }
        if (ok) loadApps()
    }

    // ---- Apps ---------------------------------------------------------
    fun loadApps() = viewModelScope.launch {
        update { it.copy(appsLoading = true) }
        val apps = repo.getInstalledApps()
        update { it.copy(apps = apps, appsLoading = false) }
    }

    fun setQuery(q: String) = update { it.copy(query = q) }
    fun toggleShowSystem() = update { it.copy(showSystem = !it.showSystem) }

    // ---- Databases & preferences --------------------------------------
    fun selectApp(app: AppInfo) = viewModelScope.launch {
        update {
            it.copy(
                selectedApp = app,
                dbsLoading = true,
                databases = emptyList(),
                prefs = emptyList(),
            )
        }
        val dbs = repo.findDatabases(app.packageName)
        val prefs = repo.findPrefs(app.packageName)
        update { it.copy(databases = dbs, prefs = prefs, dbsLoading = false) }
    }

    // ---- Open DB ------------------------------------------------------
    fun openDatabase(dbFile: DbFile, onOpened: () -> Unit) = viewModelScope.launch {
        update { it.copy(tablesLoading = true, openedDb = dbFile, dirty = false) }
        val res = repo.open(dbFile)
        if (res.isFailure) {
            update { it.copy(tablesLoading = false, message = "Open failed: ${res.exceptionOrNull()?.message}") }
            return@launch
        }
        val tables = repo.listTables()
        update { it.copy(tables = tables, tablesLoading = false) }
        onOpened()
    }

    // ---- Table viewer -------------------------------------------------
    fun selectTable(table: DbTable, onReady: () -> Unit) = viewModelScope.launch {
        update { it.copy(selectedTable = table, offset = 0) }
        loadPage(0)
        onReady()
    }

    fun loadPage(offset: Int) = viewModelScope.launch {
        val table = _state.value.selectedTable ?: return@launch
        update { it.copy(pageLoading = true) }
        val page = repo.page(table.name, PAGE_SIZE, offset)
        update { it.copy(page = page, offset = offset, pageLoading = false) }
    }

    fun nextPage() {
        val s = _state.value
        val total = s.page?.totalRows ?: return
        if (s.offset + PAGE_SIZE < total) loadPage(s.offset + PAGE_SIZE)
    }

    fun prevPage() {
        val s = _state.value
        if (s.offset - PAGE_SIZE >= 0) loadPage(s.offset - PAGE_SIZE)
    }

    // ---- CRUD ---------------------------------------------------------
    fun editCell(rowId: Long, column: String, newValue: String?) = viewModelScope.launch {
        val table = _state.value.selectedTable ?: return@launch
        val res = repo.updateCell(table.name, rowId, column, newValue)
        afterMutation(res.map { }, "Cell updated")
    }

    fun insertRow(values: Map<String, String?>) = viewModelScope.launch {
        val table = _state.value.selectedTable ?: return@launch
        val res = repo.insertRow(table.name, values)
        afterMutation(res.map { }, "Row inserted")
    }

    fun deleteRow(rowId: Long) = viewModelScope.launch {
        val table = _state.value.selectedTable ?: return@launch
        val res = repo.deleteRow(table.name, rowId)
        afterMutation(res.map { }, "Row deleted")
    }

    private suspend fun afterMutation(res: Result<Unit>, okMsg: String) {
        if (res.isSuccess) {
            update { it.copy(dirty = true, message = okMsg) }
            // Refresh current page + row counts.
            _state.value.selectedTable?.let {
                val page = repo.page(it.name, PAGE_SIZE, _state.value.offset)
                val tables = repo.listTables()
                update { s -> s.copy(page = page, tables = tables) }
            }
        } else {
            update { it.copy(message = "Error: ${res.exceptionOrNull()?.message}") }
        }
    }

    // ---- Save to device ----------------------------------------------
    fun save() = viewModelScope.launch {
        update { it.copy(saving = true) }
        val res = repo.saveBack()
        if (res.isSuccess) {
            update { it.copy(saving = false, dirty = false, message = "Saved to device ✓") }
        } else {
            update { it.copy(saving = false, message = "Save failed: ${res.exceptionOrNull()?.message}") }
        }
    }

    // ---- SharedPreferences --------------------------------------------
    fun openPref(pref: PrefFile, onOpened: () -> Unit) = viewModelScope.launch {
        update { it.copy(prefLoading = true, openedPref = pref, prefDirty = false, prefEntries = emptyList()) }
        val res = repo.openPref(pref)
        if (res.isSuccess) {
            update { it.copy(prefEntries = res.getOrDefault(emptyList()), prefLoading = false) }
            onOpened()
        } else {
            update { it.copy(prefLoading = false, message = "Open failed: ${res.exceptionOrNull()?.message}") }
        }
    }

    fun editPref(key: String, newValue: String?) {
        val entries = repo.setPref(key, newValue)
        update { it.copy(prefEntries = entries, prefDirty = true, message = "Value updated") }
    }

    fun addPref(entry: PrefEntry) {
        val entries = repo.addPref(entry)
        update { it.copy(prefEntries = entries, prefDirty = true, message = "Entry added") }
    }

    fun deletePref(key: String) {
        val entries = repo.deletePref(key)
        update { it.copy(prefEntries = entries, prefDirty = true, message = "Entry deleted") }
    }

    fun savePref() = viewModelScope.launch {
        update { it.copy(prefSaving = true) }
        val res = repo.savePref()
        if (res.isSuccess) {
            update { it.copy(prefSaving = false, prefDirty = false, message = "Saved to device ✓") }
        } else {
            update { it.copy(prefSaving = false, message = "Save failed: ${res.exceptionOrNull()?.message}") }
        }
    }

    // ---- SQL console --------------------------------------------------
    private val _consoleResult = MutableStateFlow<TablePage?>(null)
    val consoleResult: StateFlow<TablePage?> = _consoleResult.asStateFlow()

    fun runSql(sql: String) = viewModelScope.launch {
        val res = repo.executeRaw(sql)
        if (res.isSuccess) {
            _consoleResult.value = res.getOrNull()
            val isMutation = res.getOrNull() == null
            update {
                it.copy(
                    dirty = it.dirty || isMutation,
                    message = if (isMutation) "Statement executed" else "Query OK",
                )
            }
            if (isMutation) update { it.copy(tables = emptyList()) }.also { loadTablesQuiet() }
        } else {
            update { it.copy(message = "SQL error: ${res.exceptionOrNull()?.message}") }
        }
    }

    private fun loadTablesQuiet() = viewModelScope.launch {
        val tables = repo.listTables()
        update { it.copy(tables = tables) }
    }

    fun consumeMessage() = update { it.copy(message = null) }

    override fun onCleared() {
        repo.close()
        super.onCleared()
    }
}

// Small helper mirroring StateFlow.update (avoids extra dependency).
private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    value = block(value)
}
