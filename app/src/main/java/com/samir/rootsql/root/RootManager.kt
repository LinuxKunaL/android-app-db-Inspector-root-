package com.samir.rootsql.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around libsu's [Shell]. All root work funnels through here so the
 * rest of the app never touches shell APIs directly.
 */
object RootManager {

    init {
        // Configure the global shell before it is created for the first time.
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(20)
        )
    }

    /** True if we successfully obtained a root shell (triggers the superuser prompt). */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        Shell.getShell().isRoot
    }

    /** Run a command as root and return its stdout lines (empty on failure). */
    suspend fun exec(cmd: String): Shell.Result = withContext(Dispatchers.IO) {
        Shell.cmd(cmd).exec()
    }

    /** Convenience: run a command, returning stdout joined by newlines. */
    suspend fun execOut(cmd: String): List<String> = exec(cmd).out
}
