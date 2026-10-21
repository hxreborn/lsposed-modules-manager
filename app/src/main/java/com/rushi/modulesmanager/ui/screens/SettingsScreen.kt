package com.rushi.modulesmanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.BuildConfig
import com.rushi.modulesmanager.data.UpdateChannel
import com.rushi.modulesmanager.ui.components.AddRepositoryDialog
import com.rushi.modulesmanager.ui.components.RepositoryPicker

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val addSourceState by viewModel.addSourceState.collectAsStateWithLifecycle()

    var showChannelDialog by remember { mutableStateOf(false) }
    var showRepositorySheet by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(addSourceState.added) {
        if (addSourceState.added != null) {
            showAddDialog = false
            viewModel.clearAddSourceState()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings")
                        Text(
                            text = "Updates, repositories and about",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SectionTitle("Updates")
            SettingsRow(
                icon = Icons.Outlined.Download,
                title = "Update channel",
                subtitle = "${channel.label} - ${channel.description}",
                onClick = { showChannelDialog = true },
            )
            SettingsRow(
                icon = Icons.Outlined.Storage,
                title = "Repository",
                subtitle = source.name,
                onClick = { showRepositorySheet = true },
            )
            SettingsRow(
                icon = Icons.Outlined.Refresh,
                title = "Check for updates",
                subtitle = "Refresh the repository and rescan installed modules",
                onClick = viewModel::refresh,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("Modules")
            SettingsRow(
                icon = Icons.Outlined.Apps,
                title = "Installed modules",
                subtitle = "${installed.size} Xposed modules detected - tap to rescan",
                onClick = { viewModel.rescan(showMessage = true) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("About")
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = "Modules Manager",
                subtitle = "Version ${BuildConfig.VERSION_NAME}",
            )
            SettingsRow(
                icon = Icons.Outlined.Extension,
                title = "LSPosed project",
                subtitle = "github.com/LSPosed/LSPosed",
                onClick = { viewModel.openLink("https://github.com/LSPosed/LSPosed") },
            )

            Text(
                text = "Modules Manager is a repository client. It discovers and installs Xposed modules, " +
                    "but does not hook apps itself. Enabling a module is done in LSPosed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }

    if (showChannelDialog) {
        AlertDialog(
            onDismissRequest = { showChannelDialog = false },
            title = { Text("Update channel") },
            text = {
                Column {
                    UpdateChannel.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setChannel(option)
                                    showChannelDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = channel == option,
                                onClick = {
                                    viewModel.setChannel(option)
                                    showChannelDialog = false
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChannelDialog = false }) { Text("Close") }
            },
        )
    }

    if (showRepositorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showRepositorySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            RepositoryPicker(
                sources = sources,
                selected = source,
                onSelect = { id ->
                    viewModel.selectSource(id)
                    showRepositorySheet = false
                },
                onRemove = viewModel::removeSource,
                onAdd = {
                    showRepositorySheet = false
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

