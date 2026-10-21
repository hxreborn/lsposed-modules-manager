package com.rushi.modulesmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.ModuleUpdate
import com.rushi.modulesmanager.data.InstalledModule
import com.rushi.modulesmanager.data.ModuleVersion
import com.rushi.modulesmanager.data.RepoModule
import com.rushi.modulesmanager.data.RepoSource
import com.rushi.modulesmanager.data.SortMode
import com.rushi.modulesmanager.data.UpdateChannel
import com.rushi.modulesmanager.data.formatDate
import com.rushi.modulesmanager.data.formatRelativeDate
import com.rushi.modulesmanager.data.parseInstant
import com.rushi.modulesmanager.ui.components.AddRepositoryDialog
import com.rushi.modulesmanager.ui.components.EmptyState
import com.rushi.modulesmanager.ui.components.ErrorBanner
import com.rushi.modulesmanager.ui.components.LoadingState
import com.rushi.modulesmanager.ui.components.ModuleAvatar
import com.rushi.modulesmanager.ui.components.PackageLabel
import com.rushi.modulesmanager.ui.components.RepositoryPicker
import com.rushi.modulesmanager.ui.components.StatusPill
import java.time.Instant

private enum class RepoFilter(val label: String) {
    ALL("All"),
    UPDATES("Updates"),
    INSTALLED("Installed"),
}

@Composable
fun RepoListScreen(
    viewModel: AppViewModel,
    onOpenModule: (String) -> Unit,
) {
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val updatesFirst by viewModel.updatesFirst.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val lastUpdated by viewModel.lastUpdated.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val addSourceState by viewModel.addSourceState.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(RepoFilter.ALL) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    val installedByPackage = remember(installed) { installed.associateBy { it.packageName } }
    val updatesByPackage = remember(updates) { updates.associateBy { it.installed.packageName } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val filtered = remember(modules, query, filter, installedByPackage, updatesByPackage, sortMode, updatesFirst, channel) {
        val needle = query.trim().lowercase()
        val matched = modules.filter { module ->
            val matchesFilter = when (filter) {
                RepoFilter.ALL -> true
                RepoFilter.UPDATES -> updatesByPackage.containsKey(module.packageName)
                RepoFilter.INSTALLED -> installedByPackage.containsKey(module.packageName)
            }
            if (!matchesFilter) return@filter false
            needle.isEmpty() ||
                module.displayName.lowercase().contains(needle) ||
                module.packageName.lowercase().contains(needle) ||
                module.summary?.lowercase()?.contains(needle) == true
        }
        matched.sortedWith(
            compareBy<RepoModule> {
                if (updatesFirst && updatesByPackage.containsKey(it.packageName)) 0 else 1
            }.then(
                if (sortMode == SortMode.NAME) {
                    compareBy { it.displayName.lowercase() }
                } else {
                    compareByDescending { parseInstant(it.latestTime(channel)) ?: Instant.EPOCH }
                },
            ),
        )
    }

    val subtitle = when {
        loading -> "Loading repository..."
        updates.isNotEmpty() -> "${updates.size} update${if (updates.size == 1) "" else "s"} available"
        else -> "${modules.size} modules"
    }

    LaunchedEffect(addSourceState.added) {
        if (addSourceState.added != null) {
            showAddDialog = false
            viewModel.clearAddSourceState()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Modules")
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Sort by name") },
                                leadingIcon = {
                                    if (sortMode == SortMode.NAME) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp))
                                },
                                onClick = {
                                    viewModel.setSortMode(SortMode.NAME)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by last update") },
                                leadingIcon = {
                                    if (sortMode == SortMode.UPDATED) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp))
                                },
                                onClick = {
                                    viewModel.setSortMode(SortMode.UPDATED)
                                    showSortMenu = false
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Updates first") },
                                leadingIcon = {
                                    Checkbox(
                                        checked = updatesFirst,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = { viewModel.setUpdatesFirst(!updatesFirst) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { showSourceSheet = true },
                    label = {
                        Text(
                            text = source.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.widthIn(max = 230.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatRelativeDate(lastUpdated?.let { Instant.ofEpochMilli(it).toString() }) ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search modules") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == RepoFilter.ALL,
                    onClick = { filter = RepoFilter.ALL },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = filter == RepoFilter.UPDATES,
                    onClick = { filter = RepoFilter.UPDATES },
                    label = { Text(if (updates.isEmpty()) "Updates" else "Updates ${updates.size}") },
                )
                FilterChip(
                    selected = filter == RepoFilter.INSTALLED,
                    onClick = { filter = RepoFilter.INSTALLED },
                    label = { Text("Installed") },
                )
            }

            error?.let { message ->
                ErrorBanner(message = message, onRetry = viewModel::refresh)
            }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    loading && modules.isEmpty() -> LoadingState("Loading modules...")
                    filtered.isEmpty() -> {
                        if (modules.isEmpty()) {
                            EmptyState(
                                icon = Icons.Outlined.ErrorOutline,
                                title = "Repository unavailable",
                                body = error ?: "No modules were found at ${source.name}.",
                                actionLabel = "Choose repository",
                                onAction = { showSourceSheet = true },
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Outlined.Extension,
                                title = "Nothing here",
                                body = when (filter) {
                                    RepoFilter.UPDATES -> "Every installed module is up to date."
                                    RepoFilter.INSTALLED -> "None of your installed modules are in this repository."
                                    RepoFilter.ALL -> "No module matches \"$query\"."
                                },
                            )
                        }
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.packageName }) { module ->
                            ModuleCard(
                                module = module,
                                latest = module.latest(channel),
                                installed = installedByPackage[module.packageName],
                                update = updatesByPackage[module.packageName],
                                channel = channel,
                                onClick = { onOpenModule(module.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSourceSheet = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            RepositoryPicker(
                sources = sources,
                selected = source,
                onSelect = { id ->
                    viewModel.selectSource(id)
                    showSourceSheet = false
                },
                onRemove = viewModel::removeSource,
                onAdd = {
                    showSourceSheet = false
                    showAddDialog = true
                },
            )
        }
    }

    if (showAddDialog) {
        AddRepositoryDialog(
            inProgress = addSourceState.inProgress,
            error = addSourceState.error,
            onDismiss = {
                showAddDialog = false
                viewModel.clearAddSourceState()
            },
            onAdd = viewModel::addSource,
        )
    }
}

@Composable
private fun ModuleCard(
    module: RepoModule,
    latest: ModuleVersion?,
    installed: InstalledModule?,
    update: ModuleUpdate?,
    channel: UpdateChannel,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            ModuleAvatar(module, size = 54.dp)
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                PackageLabel(module.packageName)
                module.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        update != null -> StatusPill(
                            text = "Update ${update.version.name}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        installed != null -> StatusPill(
                            text = "Installed ${installed.versionName.orEmpty()}".trim(),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        latest != null -> StatusPill(text = "v${latest.name}")
                    }
                    Spacer(Modifier.weight(1f))
                    val date = formatDate(module.latestTime(channel) ?: module.updatedAt)
                    if (date != null) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
