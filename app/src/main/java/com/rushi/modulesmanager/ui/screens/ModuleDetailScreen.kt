package com.rushi.modulesmanager.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rushi.modulesmanager.AppViewModel
import com.rushi.modulesmanager.ModuleUpdate
import com.rushi.modulesmanager.data.InstalledModule
import com.rushi.modulesmanager.data.ModuleVersion
import com.rushi.modulesmanager.data.RepoAsset
import com.rushi.modulesmanager.data.RepoModule
import com.rushi.modulesmanager.data.RepoRelease
import com.rushi.modulesmanager.data.UpdateChannel
import com.rushi.modulesmanager.data.formatBytes
import com.rushi.modulesmanager.data.formatDate
import com.rushi.modulesmanager.data.stripHtml
import com.rushi.modulesmanager.ui.components.EmptyState
import com.rushi.modulesmanager.ui.components.LoadingState
import com.rushi.modulesmanager.ui.components.ModuleAvatar
import com.rushi.modulesmanager.ui.components.PackageLabel
import com.rushi.modulesmanager.ui.components.StatusPill

private val tabs = listOf("Overview", "Releases", "Information")

@Composable
fun ModuleDetailScreen(
    viewModel: AppViewModel,
    packageName: String,
    onBack: () -> Unit,
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val loading by viewModel.detailLoading.collectAsStateWithLifecycle()
    val detailError by viewModel.detailError.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var pendingRelease by remember { mutableStateOf<RepoRelease?>(null) }

    val context = LocalContext.current
    val module = detail
    val installedModule = installed.firstOrNull { it.packageName == packageName }
    val update = updates.firstOrNull { it.installed.packageName == packageName }
    val release = module?.releaseFor(channel)
    val primaryAsset = release?.apkAssets?.firstOrNull()
    val latest = module?.latest(channel)

    LaunchedEffect(packageName) { viewModel.openModule(packageName) }
    DisposableEffect(packageName) {
        onDispose { viewModel.closeModule() }
    }

    val topBarTitle = module?.displayName ?: packageName

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(topBarTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            module?.homepageUrl?.let { url ->
                                DropdownMenuItem(
                                    text = { Text("Open homepage") },
                                    leadingIcon = { Icon(Icons.Outlined.Language, null) },
                                    onClick = {
                                        viewModel.openLink(url)
                                        showMenu = false
                                    },
                                )
                            }
                            module?.sourceUrl?.let { url ->
                                DropdownMenuItem(
                                    text = { Text("Open source code") },
                                    leadingIcon = { Icon(Icons.Outlined.Code, null) },
                                    onClick = {
                                        viewModel.openLink(url)
                                        showMenu = false
                                    },
                                )
                            }
                            module?.url?.let { url ->
                                DropdownMenuItem(
                                    text = { Text("Open release page") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                                    onClick = {
                                        viewModel.openLink(url)
                                        showMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Copy package name") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = {
                                    copyToClipboard(context, packageName)
                                    showMenu = false
                                },
                            )
                        }
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
                .fillMaxSize(),
        ) {
            if (module == null) {
                if (loading) {
                    LoadingState("Loading module details...")
                } else {
                    EmptyState(
                        icon = Icons.Outlined.Info,
                        title = "Module not found",
                        body = detailError ?: "This module is not part of the selected repository.",
                    )
                }
                return@Column
            }

            DetailHeader(
                module = module,
                installed = installedModule,
                update = update,
                latest = latest,
                channel = channel,
                primaryAsset = primaryAsset,
                onInstall = {
                    if (release != null) {
                        if (release.apkAssets.size > 1) {
                            pendingRelease = release
                        } else if (primaryAsset != null) {
                            viewModel.install(module, primaryAsset)
                        }
                    }
                },
                onOpenModule = { viewModel.openInstalledModule(packageName) },
                onOpenLsposed = viewModel::openManager,
            )

            PrimaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (tab) {
                    0 -> OverviewTab(
                        module = module,
                        installing = loading,
                        onOpenLink = viewModel::openLink,
                    )
                    1 -> ReleasesTab(
                        module = module,
                        channel = channel,
                        loading = loading,
                        onInstall = { asset ->
                            viewModel.install(module, asset)
                        },
                        onOpenRelease = viewModel::openLink,
                    )
                    else -> InformationTab(
                        module = module,
                        sourceName = source.name,
                        onOpenLink = viewModel::openLink,
                    )
                }
            }
        }
    }

    if (pendingRelease != null) {
        val target = pendingRelease
        if (target != null && module != null) {
            ModalBottomSheet(
                onDismissRequest = { pendingRelease = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                ) {
                    Text("Choose an APK", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = target.name ?: target.tagName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    target.apkAssets.forEach { asset ->
                        AssetRow(asset = asset) {
                            pendingRelease = null
                            viewModel.install(module, asset)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    module: RepoModule,
    installed: InstalledModule?,
    update: ModuleUpdate?,
    latest: ModuleVersion?,
    channel: UpdateChannel,
    primaryAsset: RepoAsset?,
    onInstall: () -> Unit,
    onOpenModule: () -> Unit,
    onOpenLsposed: () -> Unit,
) {
    val updateVersion = update?.version
    val hasUpdate = updateVersion != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        Row {
            ModuleAvatar(module, size = 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                PackageLabel(module.packageName)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        hasUpdate -> StatusPill(
                            text = "Update ${updateVersion.name}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        installed != null -> StatusPill(
                            text = "Installed ${installed.versionName.orEmpty()}".trim(),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        latest != null -> StatusPill(text = "Latest ${latest.name}")
                    }
                    if (installed != null && hasUpdate) {
                        StatusPill(text = "Installed ${installed.versionName.orEmpty()}".trim())
                    }
                }
            }
        }

        module.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val latestDate = formatDate(module.latestTime(channel))
        if (latest != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Latest ${latest.name}" + (latestDate?.let { " - $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val primaryLabel = when {
                hasUpdate -> "Update to ${updateVersion.name}"
                installed != null && installed.hasLauncher -> "Open"
                installed != null -> "Open in LSPosed"
                primaryAsset != null && latest != null -> "Install ${latest.name}"
                primaryAsset != null -> "Install"
                else -> "No APK available"
            }
            Button(
                onClick = {
                    when {
                        hasUpdate || installed == null -> onInstall()
                        installed.hasLauncher -> onOpenModule()
                        else -> onOpenLsposed()
                    }
                },
                enabled = hasUpdate || installed != null || primaryAsset != null,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (installed != null && !hasUpdate) {
                        Icons.Outlined.PlayArrow
                    } else {
                        Icons.Outlined.Download
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = onOpenLsposed) {
                Text("LSPosed", maxLines = 1)
            }
        }
    }
}

@Composable
private fun OverviewTab(
    module: RepoModule,
    installing: Boolean,
    onOpenLink: (String) -> Unit,
) {
    val readme = module.readmeHtml
    if (readme.isNullOrBlank()) {
        val fallback = module.readme ?: module.summary
        if (fallback.isNullOrBlank()) {
            EmptyState(
                icon = Icons.Outlined.Extension,
                title = "No description",
                body = "This module has not published a README.",
            )
        } else {
            Text(
                text = fallback,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            )
        }
        return
    }
    ReadmeWebView(
        html = readme,
        onOpenLink = onOpenLink,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ReadmeWebView(
    html: String,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewInstance = this
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.loadsImagesAutomatically = true
                settings.textZoom = 100
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        onOpenLink(request.url.toString())
                        return true
                    }
                }
                loadDataWithBaseURL("https://github.com", wrapReadme(html, dark), "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://github.com", wrapReadme(html, dark), "text/html", "utf-8", null)
        },
    )
}

private fun wrapReadme(html: String, dark: Boolean): String {
    val colors = if (dark) {
        "body{color:#DDE4E4;background:transparent}a{color:#80D4DA}code,pre{background:rgba(255,255,255,.08);color:#DDE4E4}hr{border-color:rgba(255,255,255,.15)}td,th{border-color:rgba(255,255,255,.2)}blockquote{color:#BEC8C9;border-left:3px solid rgba(255,255,255,.2)}"
    } else {
        "body{color:#161D1E;background:transparent}a{color:#00696E}code,pre{background:rgba(0,0,0,.05);color:#161D1E}hr{border-color:rgba(0,0,0,.12)}td,th{border-color:rgba(0,0,0,.15)}blockquote{color:#3F4849;border-left:3px solid rgba(0,0,0,.15)}"
    }
    return """
        <!doctype html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
        body{font-family:Roboto,sans-serif;font-size:15px;line-height:1.55;margin:16px;}
        img{max-width:100%;height:auto;}
        pre{overflow:auto;padding:10px;border-radius:8px;}
        code{font-family:monospace;font-size:13px;padding:1px 4px;border-radius:4px;}
        pre code{padding:0;}
        table{border-collapse:collapse;}
        td,th{border:1px solid;padding:5px 8px;}
        blockquote{margin:8px 0;padding-left:10px;}
        h1,h2,h3{line-height:1.25;}
        $colors
        </style></head>
        <body>$html</body></html>
    """.trimIndent()
}

@Composable
private fun ReleasesTab(
    module: RepoModule,
    channel: UpdateChannel,
    loading: Boolean,
    onInstall: (RepoAsset) -> Unit,
    onOpenRelease: (String?) -> Unit,
) {
    val releases = module.releasesFor(channel)
    if (releases.isEmpty()) {
        if (loading) {
            LoadingState("Loading releases...")
        } else {
            EmptyState(
                icon = Icons.Outlined.Info,
                title = "No releases",
                body = "No release of this module is available for the ${channel.label} channel.",
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(releases, key = { it.url ?: it.tagName ?: it.toString() }) { item ->
            ReleaseCard(
                release = item,
                onInstall = onInstall,
                onOpenRelease = { onOpenRelease(item.url) },
            )
        }
        item {
            Text(
                text = "Channel: ${channel.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ReleaseCard(
    release: RepoRelease,
    onInstall: (RepoAsset) -> Unit,
    onOpenRelease: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.name ?: release.tagName ?: "Release",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatDate(release.publishedAt) ?: "Unknown date",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (release.isPrerelease) {
                    StatusPill(text = "Pre-release")
                    Spacer(Modifier.width(4.dp))
                }
                if (release.url != null) {
                    IconButton(onClick = onOpenRelease) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open release page")
                    }
                }
            }
            val notes = stripHtml(release.descriptionHtml)
            if (!notes.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (release.apkAssets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                release.apkAssets.forEach { asset ->
                    AssetRow(asset = asset, onInstall = { onInstall(asset) })
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "No APK attached to this release",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AssetRow(asset: RepoAsset, onInstall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatBytes(asset.size)} - ${asset.downloadCount} downloads",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onInstall) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Install")
        }
    }
}

@Composable
private fun InformationTab(
    module: RepoModule,
    sourceName: String,
    onOpenLink: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        InfoRow("Repository", sourceName)
        module.homepageUrl?.let { InfoRow("Homepage", it, onClick = { onOpenLink(it) }) }
        module.sourceUrl?.let { InfoRow("Source code", it, onClick = { onOpenLink(it) }) }
        module.url?.let { InfoRow("Releases", it, onClick = { onOpenLink(it) }) }
        if (module.collaborators.isNotEmpty()) {
            InfoRow(
                title = "Maintainers",
                value = module.collaborators.joinToString(", ") { it.name ?: it.login },
            )
        }
        if (module.stargazerCount > 0) {
            InfoRow("Stars", module.stargazerCount.toString())
        }
        if (module.scope.isNotEmpty()) {
            InfoRow(
                title = "Target apps",
                value = module.scope.joinToString("\n"),
            )
        }
        HorizontalDivider()
        Text(
            text = "Version strings in this repository look like versionCode-versionName. Updates are matched by package name and version code.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("package name", text))
    Toast.makeText(context, "Package name copied", Toast.LENGTH_SHORT).show()
}
