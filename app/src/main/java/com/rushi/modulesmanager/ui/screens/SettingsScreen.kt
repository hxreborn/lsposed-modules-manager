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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.BuildConfig
import com.rushi.modulesmanager.data.AiProvider
import com.rushi.modulesmanager.data.AppTheme
import com.rushi.modulesmanager.data.InstallerType
import com.rushi.modulesmanager.data.ShizukuInstaller
import com.rushi.modulesmanager.data.TldrApi
import com.rushi.modulesmanager.data.UpdateChannel
import com.rushi.modulesmanager.ui.components.HeaderDivider

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val installer by viewModel.installer.collectAsStateWithLifecycle()
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val amoled by viewModel.amoled.collectAsStateWithLifecycle()
    val systemColors by viewModel.systemColors.collectAsStateWithLifecycle()
    val aiProvider by viewModel.aiProvider.collectAsStateWithLifecycle()
    val aiKeys by viewModel.aiKeys.collectAsStateWithLifecycle()
    val aiKey = aiKeys[aiProvider].orEmpty()
    val tldrPrompt by viewModel.tldrPrompt.collectAsStateWithLifecycle()
    val githubToken by viewModel.githubToken.collectAsStateWithLifecycle()

    var showChannelDialog by remember { mutableStateOf(false) }
    var showInstallerDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showGithubDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showInstallerDialog) {
        if (showInstallerDialog) viewModel.refreshShizukuStatus()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Settings") },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SectionTitle("Appearance")
            SettingsSwitchRow(
                icon = Icons.Outlined.DarkMode,
                title = "AMOLED mode",
                subtitle = "Pitch black backgrounds when the dark theme is active",
                checked = amoled,
                onCheckedChange = viewModel::setAmoled,
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.ColorLens,
                title = "System colors",
                subtitle = "Material You colors on Android 12+",
                checked = systemColors,
                onCheckedChange = viewModel::setSystemColors,
            )
            SettingsRow(
                icon = Icons.Outlined.Palette,
                title = "Theme",
                subtitle = "${theme.label} - ${theme.description}",
                onClick = { showThemeDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("Updates")
            SettingsRow(
                icon = Icons.Outlined.Refresh,
                title = "Check for updates",
                onClick = viewModel::refresh,
            )
            SettingsRow(
                icon = Icons.Outlined.InstallMobile,
                title = "Installer",
                subtitle = installer.label,
                onClick = { showInstallerDialog = true },
            )
            SettingsRow(
                icon = Icons.Outlined.Download,
                title = "Update channel",
                subtitle = channel.label,
                onClick = { showChannelDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("Summaries")
            SettingsRow(
                icon = Icons.Outlined.Cloud,
                title = "AI provider",
                subtitle = "${aiProvider.label} (${aiProvider.model})",
                onClick = { showProviderDialog = true },
            )
            SettingsRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "${aiProvider.label} API key",
                subtitle = if (aiKey.isBlank()) {
                    "Not set. Tap TLDR on a module to summarize it with your own key"
                } else {
                    "Set, ending in ${aiKey.takeLast(4)}"
                },
                onClick = { showKeyDialog = true },
            )
            SettingsRow(
                icon = Icons.Outlined.EditNote,
                title = "Summary prompt",
                subtitle = if (tldrPrompt == TldrApi.DEFAULT_PROMPT) "Default" else "Custom",
                onClick = { showPromptDialog = true },
            )
            SettingsRow(
                icon = Icons.Outlined.Key,
                title = "GitHub token",
                subtitle = if (githubToken.isBlank()) {
                    "Not set. GitHub allows 60 requests an hour without one"
                } else {
                    "Set, ending in ${githubToken.takeLast(4)}"
                },
                onClick = { showGithubDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("About")
            SettingsRow(
                icon = Icons.Outlined.LocalCafe,
                title = "Buy me a coffee",
                subtitle = "buymeacoffee.com/rushiranpise",
                onClick = { viewModel.openLink("https://buymeacoffee.com/rushiranpise") },
            )
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = "Modules Manager",
                subtitle = "Version ${BuildConfig.VERSION_NAME}",
            )
            SettingsRow(
                icon = Icons.Outlined.Code,
                title = "View on GitHub",
                subtitle = "github.com/rushiranpise/lsposed-modules-manager",
                onClick = { viewModel.openLink("https://github.com/rushiranpise/lsposed-modules-manager") },
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

    if (showPromptDialog) {
        var draft by remember { mutableStateOf(tldrPrompt) }
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text("Summary prompt") },
            text = {
                Column {
                    Text(
                        text = "Sent before each module's facts. ${TldrApi.LANGUAGE_TOKEN} becomes the device language. Changing it makes TLDR ask again for modules summarized with the old prompt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = MaterialTheme.typography.bodySmall,
                        minLines = 6,
                        maxLines = 14,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTldrPrompt(draft)
                    showPromptDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { draft = TldrApi.DEFAULT_PROMPT },
                        enabled = draft != TldrApi.DEFAULT_PROMPT,
                    ) { Text("Reset") }
                    TextButton(onClick = { showPromptDialog = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (showKeyDialog) {
        SecretDialog(
            title = "${aiProvider.label} API key",
            description = "Stored only on this device. Summaries are generated one module at a time when you tap TLDR.",
            placeholder = aiProvider.keyHint,
            value = aiKey,
            onSave = { viewModel.setAiKey(aiProvider, it) },
            onDismiss = { showKeyDialog = false },
        )
    }

    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text("AI provider") },
            text = {
                Column {
                    AiProvider.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAiProvider(option)
                                    showProviderDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = aiProvider == option,
                                onClick = {
                                    viewModel.setAiProvider(option)
                                    showProviderDialog = false
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (aiKeys[option].isNullOrBlank()) "${option.model}, no key" else "${option.model}, key set",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderDialog = false }) { Text("Close") }
            },
        )
    }

    if (showGithubDialog) {
        SecretDialog(
            title = "GitHub token",
            description = "Stored only on this device and sent only to api.github.com. A token with no scopes is enough to read public repositories.",
            placeholder = "ghp_... or github_pat_...",
            value = githubToken,
            onSave = viewModel::setGithubToken,
            onDismiss = { showGithubDialog = false },
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    AppTheme.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setTheme(option)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = theme == option,
                                onClick = {
                                    viewModel.setTheme(option)
                                    showThemeDialog = false
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
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            },
        )
    }

    if (showInstallerDialog) {
        AlertDialog(
            onDismissRequest = { showInstallerDialog = false },
            title = { Text("Installer") },
            text = {
                Column {
                    InstallerType.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setInstaller(option)
                                    // Keep the dialog open for Shizuku so its state and the action
                                    // that resolves it stay visible.
                                    if (option != InstallerType.SHIZUKU) showInstallerDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = installer == option,
                                onClick = {
                                    viewModel.setInstaller(option)
                                    if (option != InstallerType.SHIZUKU) showInstallerDialog = false
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
                    if (installer == InstallerType.SHIZUKU) {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (shizukuStatus) {
                                    ShizukuInstaller.Status.READY -> "Shizuku is running and allowed"
                                    ShizukuInstaller.Status.PERMISSION_REQUIRED ->
                                        "Shizuku permission is not granted"
                                    ShizukuInstaller.Status.NOT_RUNNING -> "Shizuku is not running"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (shizukuStatus == ShizukuInstaller.Status.READY) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (shizukuStatus != ShizukuInstaller.Status.READY) {
                                TextButton(
                                    onClick = {
                                        if (shizukuStatus == ShizukuInstaller.Status.NOT_RUNNING) {
                                            viewModel.openShizuku()
                                        } else {
                                            viewModel.requestShizukuPermission()
                                        }
                                    },
                                ) {
                                    Text(
                                        if (shizukuStatus == ShizukuInstaller.Status.NOT_RUNNING) {
                                            "Start"
                                        } else {
                                            "Allow"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInstallerDialog = false }) { Text("Close") }
            },
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
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
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
        Spacer(Modifier.width(20.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
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
            subtitle?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun SecretDialog(
    title: String,
    description: String,
    placeholder: String,
    value: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(draft)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onSave("")
                        onDismiss()
                    },
                    enabled = value.isNotEmpty(),
                ) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
