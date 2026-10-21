package com.rushi.modulesmanager

import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rushi.modulesmanager.data.ApkInspector
import com.rushi.modulesmanager.data.ApkInstaller
import com.rushi.modulesmanager.data.DefaultSources
import com.rushi.modulesmanager.data.DownloadCenter
import com.rushi.modulesmanager.data.InstalledModule
import com.rushi.modulesmanager.data.InstalledPackageVersion
import com.rushi.modulesmanager.data.ModuleVersion
import com.rushi.modulesmanager.data.RepoAsset
import com.rushi.modulesmanager.data.RepoModule
import com.rushi.modulesmanager.data.RepoSource
import com.rushi.modulesmanager.data.SortMode
import com.rushi.modulesmanager.data.UpdateChannel
import com.rushi.modulesmanager.data.normalizeRepoBase
import com.rushi.modulesmanager.data.sourceFromBase
import com.rushi.modulesmanager.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class ModuleUpdate(
    val installed: InstalledModule,
    val module: RepoModule,
    val version: ModuleVersion,
)

data class AddSourceState(
    val inProgress: Boolean = false,
    val error: String? = null,
    val added: RepoSource? = null,
)

class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settings
    private val loadMutex = Mutex()

    val channel: StateFlow<UpdateChannel> = settings.channel
    val sortMode: StateFlow<SortMode> = settings.sortMode
    val updatesFirst: StateFlow<Boolean> = settings.updatesFirst

    val modules: StateFlow<List<RepoModule>> = container.repo.modules
    val loading: StateFlow<Boolean> = container.repo.loading
    val refreshing: StateFlow<Boolean> = container.repo.refreshing
    val error: StateFlow<String?> = container.repo.error
    val lastUpdated: StateFlow<Long?> = container.repo.lastUpdated
    val downloads: StateFlow<DownloadCenter.State?> = container.downloads.state

    val sources: StateFlow<List<RepoSource>> = settings.customSources
        .map { listOf(DefaultSources.official) + it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(DefaultSources.official))

    val source: StateFlow<RepoSource> = combine(sources, settings.sourceId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.first()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DefaultSources.official)

    private val _repoInstalled = MutableStateFlow<List<InstalledModule>>(emptyList())
    val repoInstalled: StateFlow<List<InstalledModule>> = _repoInstalled.asStateFlow()

    private val _installedVersions = MutableStateFlow<Map<String, InstalledPackageVersion>>(emptyMap())
    val installedVersions: StateFlow<Map<String, InstalledPackageVersion>> = _installedVersions.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _addSource = MutableStateFlow(AddSourceState())
    val addSourceState: StateFlow<AddSourceState> = _addSource.asStateFlow()

    private val _detail = MutableStateFlow<RepoModule?>(null)
    val detail: StateFlow<RepoModule?> = _detail.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    private var detailJob: Job? = null
    private var downloadJob: Job? = null
    private var installedRebuildJob: Job? = null
    private var detectedModules: List<InstalledModule> = emptyList()
    private var installedVersionMap: Map<String, InstalledPackageVersion> = emptyMap()

    val updates: StateFlow<List<ModuleUpdate>> = combine(modules, repoInstalled, channel) { repoModules, installedModules, selectedChannel ->
        val byPackage = repoModules.associateBy { it.packageName }
        installedModules.mapNotNull { installedModule ->
            val module = byPackage[installedModule.packageName] ?: return@mapNotNull null
            val latest = module.latest(selectedChannel) ?: return@mapNotNull null
            if (latest.isNewerThan(installedModule.versionCode, installedModule.versionName)) {
                ModuleUpdate(installedModule, module, latest)
            } else {
                null
            }
        }.sortedBy { it.module.displayName.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        rescan()
        viewModelScope.launch {
            source.collectLatest { repoSource ->
                loadMutex.withLock { container.repo.load(repoSource) }
            }
        }
        viewModelScope.launch {
            modules.map { list -> list.map { it.packageName } }
                .distinctUntilChanged()
                .collectLatest { rebuildRepoInstalled() }
        }
    }

    fun rescan(showMessage: Boolean = false) {
        viewModelScope.launch {
            val scanned = withContext(Dispatchers.IO) { container.scanner.scan() }
            detectedModules = scanned.modules
            installedVersionMap = scanned.versions
            _installedVersions.value = scanned.versions
            rebuildRepoInstalled()
            if (showMessage) message("Rescanned installed modules")
        }
    }

    /** Combines Xposed-detected modules with repository packages installed without metadata. */
    private fun rebuildRepoInstalled() {
        installedRebuildJob?.cancel()
        val repoPackages = modules.value.map { it.packageName }
        val versions = installedVersionMap
        if (repoPackages.isEmpty() || versions.isEmpty()) {
            _repoInstalled.value = emptyList()
            return
        }
        val detected = detectedModules.associateBy { it.packageName }
        installedRebuildJob = viewModelScope.launch {
            val full = withContext(Dispatchers.IO) {
                repoPackages.asSequence()
                    .filter { versions.containsKey(it) }
                    .mapNotNull { detected[it] ?: container.scanner.installedModule(it) }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }
            _repoInstalled.value = full
        }
    }

    fun refresh() {
        rescan()
        viewModelScope.launch {
            val repoSource = source.value
            loadMutex.withLock { container.repo.load(repoSource) }
        }
    }

    fun selectSource(id: String) {
        settings.setSourceId(id)
    }

    fun setChannel(channel: UpdateChannel) {
        settings.setChannel(channel)
    }

    fun setSortMode(mode: SortMode) {
        settings.setSortMode(mode)
    }

    fun setUpdatesFirst(enabled: Boolean) {
        settings.setUpdatesFirst(enabled)
    }

    fun addSource(input: String) {
        val base = normalizeRepoBase(input)
        if (base == null) {
            _addSource.value = AddSourceState(error = "Enter a valid repository URL")
            return
        }
        viewModelScope.launch {
            _addSource.value = AddSourceState(inProgress = true)
            try {
                container.api.fetchModules(sourceFromBase(base))
                val added = settings.addCustomSource(base) ?: error("Could not add this repository")
                settings.setSourceId(added.id)
                _addSource.value = AddSourceState(added = added)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _addSource.value = AddSourceState(error = e.userMessage())
            }
        }
    }

    fun clearAddSourceState() {
        _addSource.value = AddSourceState()
    }

    fun removeSource(id: String) {
        settings.removeCustomSource(id)
    }

    fun openModule(packageName: String) {
        detailJob?.cancel()
        _detail.value = container.repo.cachedModule(packageName)
        _detailError.value = null
        _detailLoading.value = true
        detailJob = viewModelScope.launch {
            val loaded = container.repo.loadModule(source.value, packageName)
            if (loaded != null) {
                _detail.value = loaded
            } else if (_detail.value == null) {
                _detailError.value = "This module is not available in the selected repository."
            }
            _detailLoading.value = false
        }
    }

    fun closeModule() {
        detailJob?.cancel()
        _detail.value = null
        _detailError.value = null
        _detailLoading.value = false
    }

    fun latestVersion(module: RepoModule): ModuleVersion? = module.latest(channel.value)

    fun moduleFor(packageName: String): RepoModule? = container.repo.cachedModule(packageName)

    fun updateFor(packageName: String): ModuleUpdate? = updates.value.firstOrNull { it.installed.packageName == packageName }

    fun install(module: RepoModule, asset: RepoAsset) {
        if (downloads.value != null || downloadJob?.isActive == true) {
            message("Wait for the current download to finish")
            return
        }
        val appContext = container.appContext
        if (!ApkInstaller.canInstall(appContext)) {
            message("Allow \"Install unknown apps\" for Modules Manager, then try again")
            ApkInstaller.openInstallPermissionSettings(appContext)
            return
        }

        val destination = File(appContext.cacheDir, "apk")
            .apply { mkdirs() }
            .resolve(asset.name.sanitizeFileName())

        downloadJob = viewModelScope.launch {
            try {
                container.downloads.update(
                    DownloadCenter.State(
                        packageName = module.packageName,
                        label = module.displayName,
                        fileName = asset.name,
                        downloaded = 0,
                        total = asset.size,
                        phase = DownloadCenter.Phase.DOWNLOADING,
                    ),
                )
                val file = container.api.download(
                    urls = listOfNotNull(asset.downloadUrl, asset.originalDownloadUrl),
                    destination = destination,
                ) { done, total ->
                    container.downloads.progress(done, total)
                }
                container.downloads.phase(DownloadCenter.Phase.VERIFYING)

                val info = withContext(Dispatchers.IO) { ApkInspector.inspect(appContext, file) }
                    ?: throw IllegalStateException("The downloaded file is not a valid APK")
                if (info.packageName != module.packageName) {
                    throw IllegalStateException("This release contains ${info.packageName}, not ${module.packageName}")
                }
                val installedPackage = _installedVersions.value[module.packageName]
                if (installedPackage != null) {
                    if (info.versionCode < installedPackage.versionCode) {
                        throw IllegalStateException("This release is older than the installed version")
                    }
                    val installedSignature = withContext(Dispatchers.IO) {
                        ApkInspector.installedSignatureDigest(appContext, module.packageName)
                    }
                    if (installedSignature != null && info.signatureDigest != null &&
                        installedSignature != info.signatureDigest
                    ) {
                        throw IllegalStateException(
                            "This APK is signed with a different key, so Android cannot update the installed app",
                        )
                    }
                }

                container.downloads.phase(DownloadCenter.Phase.INSTALLING)
                ApkInstaller.install(appContext, file) { status, reason ->
                    if (status == PackageInstaller.STATUS_SUCCESS) {
                        message("${module.displayName} installed")
                        rescan()
                    } else {
                        message(installFailureMessage(status, reason))
                    }
                    container.downloads.update(null)
                    file.delete()
                }
            } catch (e: CancellationException) {
                container.downloads.update(null)
                destination.delete()
                File(destination.path + ".part").delete()
                throw e
            } catch (e: Exception) {
                container.downloads.update(null)
                destination.delete()
                File(destination.path + ".part").delete()
                message(e.userMessage())
            }
        }
    }

    /** Resolves the newest APK for an installed module and starts installing it. */
    fun installLatest(module: RepoModule) {
        viewModelScope.launch {
            val detailed = container.repo.loadModule(source.value, module.packageName) ?: module
            val asset = detailed.releaseFor(channel.value)?.apkAssets?.firstOrNull()
            if (asset == null) {
                message("No APK is available for ${module.displayName}")
                return@launch
            }
            install(detailed, asset)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        container.downloads.update(null)
    }

    fun openInstalledModule(packageName: String) {
        if (!container.scanner.openModule(packageName)) {
            message("This module has no settings screen")
        }
    }

    fun openLink(url: String?) {
        if (url.isNullOrBlank()) {
            message("No link available")
            return
        }
        runCatching {
            container.appContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { message("No app can open this link") }
    }

    private fun installFailureMessage(status: Int, reason: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled"
        PackageInstaller.STATUS_FAILURE_BLOCKED ->
            "The system blocked the install. Allow unknown apps for Modules Manager and try again"
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "The installed app conflicts with this APK, usually because the signing keys differ"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This APK is not compatible with this device"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install this APK"
        else -> reason?.takeIf { it.isNotBlank() }?.let { "Install failed: $it" } ?: "Install failed"
    }

    private fun message(text: String) {
        _messages.tryEmit(text)
    }

    private fun String.sanitizeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifBlank { "module.apk" }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }
}
