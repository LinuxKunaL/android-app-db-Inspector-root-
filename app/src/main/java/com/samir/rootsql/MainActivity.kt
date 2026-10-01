package com.samir.rootsql

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.samir.rootsql.ui.MainViewModel
import com.samir.rootsql.ui.screens.AppListScreen
import com.samir.rootsql.ui.screens.DatabaseListScreen
import com.samir.rootsql.ui.screens.PrefScreen
import com.samir.rootsql.ui.screens.SqlConsoleScreen
import com.samir.rootsql.ui.screens.TableDataScreen
import com.samir.rootsql.ui.screens.TableListScreen
import com.samir.rootsql.ui.theme.RootDBInspectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RootDBInspectorTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

object Routes {
    const val APPS = "apps"
    const val DATABASES = "databases"
    const val TABLES = "tables"
    const val TABLE_DATA = "table_data"
    const val CONSOLE = "console"
    const val PREFS = "prefs"
}

@Composable
private fun App(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    // One snackbar host shared by every screen (each screen hosts it in its own Scaffold).
    val snackbar = remember { SnackbarHostState() }

    // Kick off the root check once.
    LaunchedEffect(Unit) { vm.checkRoot() }

    // Surface any transient message as a snackbar.
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    NavHost(navController = nav, startDestination = Routes.APPS) {
        composable(Routes.APPS) {
            AppListScreen(
                state = state,
                vm = vm,
                snackbar = snackbar,
                onAppClick = { app ->
                    vm.selectApp(app)
                    nav.navigate(Routes.DATABASES)
                },
            )
        }
        composable(Routes.DATABASES) {
            DatabaseListScreen(
                state = state,
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
                onDbClick = { db ->
                    vm.openDatabase(db) { nav.navigate(Routes.TABLES) }
                },
                onPrefClick = { pref ->
                    vm.openPref(pref) { nav.navigate(Routes.PREFS) }
                },
            )
        }
        composable(Routes.PREFS) {
            PrefScreen(
                state = state,
                vm = vm,
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.TABLES) {
            TableListScreen(
                state = state,
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
                onTableClick = { table ->
                    vm.selectTable(table) { nav.navigate(Routes.TABLE_DATA) }
                },
                onConsole = { nav.navigate(Routes.CONSOLE) },
                onSave = { vm.save() },
            )
        }
        composable(Routes.TABLE_DATA) {
            TableDataScreen(
                state = state,
                vm = vm,
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.CONSOLE) {
            SqlConsoleScreen(
                vm = vm,
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
