package com.samir.rootsql.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.samir.rootsql.data.model.AppInfo
import com.samir.rootsql.ui.MainViewModel
import com.samir.rootsql.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    state: UiState,
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    onAppClick: (AppInfo) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Root DB Inspector")
                        Text(
                            "App data & preferences editor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
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
            when {
                !state.rootChecked -> LoadingBox("Requesting root access…")
                !state.rootAvailable -> RootDenied(onRetry = { vm.checkRoot() })
                else -> AppsContent(state, vm, onAppClick)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.AppsContent(
    state: UiState,
    vm: MainViewModel,
    onAppClick: (AppInfo) -> Unit,
) {
    OutlinedTextField(
        value = state.query,
        onValueChange = vm::setQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
    )

    Row(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.favoritesOnly,
            onClick = { vm.toggleFavoritesOnly() },
            label = { Text("Favorites") },
            leadingIcon = {
                Icon(
                    if (state.favoritesOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    null,
                    Modifier.size(18.dp),
                )
            },
        )
        FilterChip(
            selected = state.showSystem,
            onClick = { vm.toggleShowSystem() },
            label = { Text("System apps") },
        )
    }

    when {
        state.appsLoading -> Box(Modifier.weight(1f)) { LoadingBox("Loading installed apps…") }
        state.filteredApps.isEmpty() ->
            Box(Modifier.weight(1f)) { EmptyState(Icons.Default.Android, "No apps match your filter") }
        else -> LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(state.filteredApps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    isFavorite = app.packageName in state.favorites,
                    onClick = { onAppClick(app) },
                    onToggleFav = { vm.toggleFavorite(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bmp = app.icon?.toBitmap(96, 96)?.asImageBitmap()
        if (bmp != null) {
            Image(bmp, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Android, null) }
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleFav) {
            Icon(
                if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                tint = if (isFavorite) Color(0xFFFFC107)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RootDenied(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Outlined.Lock, null,
                Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text("Root access required", style = MaterialTheme.typography.titleLarge)
            Text(
                "This app needs superuser (root) permission to read other apps' " +
                    "private databases. Grant the request in Magisk / your su manager, " +
                    "then retry.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
