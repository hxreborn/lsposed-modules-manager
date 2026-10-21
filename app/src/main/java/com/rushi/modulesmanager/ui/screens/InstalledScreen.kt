package com.rushi.modulesmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.rushi.modulesmanager.ui.components.InstalledAvatar
import com.rushi.modulesmanager.ui.components.PackageLabel
import com.rushi.modulesmanager.ui.components.StatusPill

@Composable
fun InstalledScreen(
    viewModel: AppViewModel,
    onOpenModule: (String) -> Unit,
) {
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val modules by viewModel.modules.collectAsStateWithLifecycle()

    var updatesOnly by rememberSaveable { mutableStateOf(false) }

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
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "filter") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            }

            if (visible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.Apps,
                        title = if (updatesOnly) "Nothing to update" else "No Xposed modules found",
                        body = if (updatesOnly) {
                            "All installed modules are on their latest version in this repository."
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
        Row(modifier = Modifier.padding(14.dp)) {
            InstalledAvatar(module, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(2.dp))
                PackageLabel(module.packageName)
                Spacer(Modifier.size(4.dp))
                Text(
                    text = buildString {
                        append("Version ${module.versionName ?: module.versionCode}")
                        if (module.scope.isNotEmpty()) append(" - ${module.scope.size} target apps")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.weight(1f))
                    if (update != null) {
                        FilledTonalButton(onClick = { onUpdate(update) }) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Update")
                        }
                    }
                }
            }
        }
    }
}

