# Root DB Inspector

A **rooted-Android** developer tool for inspecting and editing the private SQLite
databases of installed apps. Built with **Kotlin + Jetpack Compose (Material 3)**
and [libsu](https://github.com/topjohnwu/libsu) for root access.

> ⚠️ Requires a **rooted device** (Magisk / KernelSU / a working `su`). It reads
> other apps' private data under `/data/data/…`, which is only possible with root.
> Use it on devices and apps you own or are authorized to inspect.

## What it does

1. **App list** — every installed app, with icon + package name, searchable, with a
   toggle to include system apps.
2. **Database list** — tap an app and it uses root to `find` all `*.db` / `*.sqlite`
   files inside that app's data directory, showing size, permissions and path.
3. **Table view** — opens the database and lists every table/view with live row counts.
4. **Spreadsheet-style data grid** — horizontally + vertically scrollable, primary-key
   columns flagged, paginated (50 rows/page).
5. **Full CRUD**
   - **Tap a cell** → edit its value (with an explicit *Set NULL* option).
   - **`+ Add row`** → insert a new row; blank fields become `NULL`/default.
   - **Long-press a row** → delete it.
   - **SQL console** → run arbitrary `SELECT` / `PRAGMA` / `INSERT` / `UPDATE` / … .
6. **Save back to device** — the app edits a *working copy* pulled into its own cache,
   then the **Save** button writes it back to the original protected location and
   restores the exact **owner / permissions / SELinux context** so the target app
   keeps working.

## How the root data flow works

- On open, `cp` pulls the `.db` (plus `-wal` / `-shm`) into `cacheDir/work/` and
  `chown`s it to our own uid so the standard `SQLiteDatabase` API can read/write it.
- All CRUD runs locally against that copy (fast, and nothing is touched on the real
  app until you choose to save).
- On **Save**, a `PRAGMA wal_checkpoint(TRUNCATE)` folds the WAL in, the file is
  `cp`'d back over the original, stale `-wal`/`-shm` are removed, and
  `chown` / `chmod` / `chcon` restore the original metadata.

See `data/SqliteRepository.kt` for the whole pipeline and `root/RootManager.kt`
for the libsu wrapper.

## Project layout

```
app/src/main/java/com/samir/rootsql/
├── MainActivity.kt            # Compose nav host + root gate
├── root/RootManager.kt        # libsu shell wrapper
├── data/
│   ├── model/Models.kt        # AppInfo, DbFile, DbTable, TablePage, …
│   └── SqliteRepository.kt    # discovery + copy-out + CRUD + save-back
└── ui/
    ├── MainViewModel.kt       # all UI state (StateFlow)
    ├── theme/                 # Material 3 theme (dynamic color on Android 12+)
    └── screens/               # AppList / DatabaseList / TableList / TableData / SqlConsole
```

## Building

Open the `RootDBInspector` folder in **Android Studio** (Koala or newer) and hit Run.
Android Studio will download the Gradle wrapper and dependencies automatically.

From the command line (if you have Gradle installed), first generate the wrapper:

```bash
cd RootDBInspector
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

Requirements: **JDK 17**, **Android SDK 34**, `minSdk 26` (Android 8.0+).

`libsu` is pulled from JitPack (already configured in `settings.gradle.kts`).

## Notes & caveats

- Databases in **WAL mode** are handled (siblings copied + checkpointed on save).
- **Views** and **`WITHOUT ROWID`** tables open as read-only (no stable rowid to target).
- **BLOB** cells are shown as `«BLOB n bytes»` and are read-only in the grid.
- Editing a live database while the owning app is running can race with that app;
  for safest results, force-stop the target app before saving changes back.
```
