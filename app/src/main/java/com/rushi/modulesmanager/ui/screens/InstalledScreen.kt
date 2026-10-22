package com.rushi.modulesmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.ModuleUpdate
import com.rushi.modulesmanager.data.InstalledModule
import com.rushi.modulesmanager.data.RepoModule
import com.rushi.modulesmanager.ui.components.CountedFilterChip
import com.rushi.modulesmanager.ui.components.EmptyState
import com.rushi.modulesmanager.ui.components.HeaderDivider
import com.rushi.modulesmanager.ui.components.InstalledAvatar
import com.rushi.modulesmanager.ui.components.PackageLabel
import com.rushi.modulesmanager.ui.components.StatusPill
import com.rushi.modulesmanager.ui.components.VerticalScrollbar
import kotlinx.coroutines.launch

@Composable
fun InstalledScreen(
    viewModel: AppViewModel,
    onOpenModule: (String) -> Unit,
) {
    val installed by viewModel.allInstalled.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val modules by viewModel.modules.collectAsStateWithLifecycle()

    var updatesOnly by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val modulesByPackage = remember(modules) { modules.associateBy { it.packageName } }
    val updatesByPackage = remember(updates) { updates.associateBy { it.installed.packageName } }
    val visible = remember(installed, updatesByPackage, updatesOnly) {
        installed.filter { !updatesOnly || updatesByPackage.containsKey(it.packageName) }
            .sortedWith(
                compareBy<InstalledModule> { if (updatesByPackage.containsKey(it.packageName)) 0 else 1 }
                    .thenBy { it.label.lowercase() },
            )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Installed") },
                    actions = {
                        IconButton(onClick = { viewModel.rescan(showMessage = true) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Rescan installed modules")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                HeaderDivider()
            }
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
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CountedFilterChip(
                    selected = !updatesOnly,
                    onClick = { updatesOnly = false },
                    label = "All",
                    count = installed.size,
                )
                CountedFilterChip(
                    selected = updatesOnly,
                    onClick = { updatesOnly = true },
                    label = "Updates",
                    count = updates.size,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (visible.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                icon = Icons.Outlined.Apps,
                                title = if (updatesOnly) "Nothing to update" else "No installed modules found",
                                body = if (updatesOnly) {
                                    "All installed modules are on their latest version."
                                } else {
                                    "Install modules from the Modules tab, then enable them in LSPosed."
                                },
                            )
                        }
                    } else {
                        items(visible, key = { it.packageName }) { module ->
                            InstalledCard(
                                module = module,
                                repoModule = modulesByPackage[module.packageName],
                                update = updatesByPackage[module.packageName],
                                onOpenRepository = { onOpenModule(module.packageName) },
                                onOpenApp = { viewModel.openInstalledModule(module.packageName) },
                                onUpdate = { update -> viewModel.installLatest(update.module) },
                            )
                        }
                    }
                }

                VerticalScrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(top = 4.dp, bottom = 80.dp),
                )

                val showScrollToTop by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 0 }
                }
                if (showScrollToTop) {
                    FloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Scroll to top")
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledCard(
    module: InstalledModule,
    repoModule: RepoModule?,
    update: ModuleUpdate?,
    onOpenRepository: () -> Unit,
    onOpenApp: () -> Unit,
    onUpdate: (ModuleUpdate) -> Unit,
) {
    Card(
        onClick = {
            if (module.hasLauncher) onOpenApp()
            else if (repoModule != null) onOpenRepository()
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstalledAvatar(module, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = module.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (update != null) {
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { onUpdate(update) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Update", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(1.dp))
                PackageLabel(module.packageName)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString {
                            append("v${module.versionName ?: module.versionCode}")
                            if (module.scope.isNotEmpty()) append(" - ${module.scope.size} targets")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    when {
                        update != null -> StatusPill(
                            text = "Latest ${update.version.name}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        repoModule != null -> StatusPill(
                            text = "Up to date",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        else -> StatusPill(text = "Not in repository")
                    }
                }
            }
        }
    }
}
