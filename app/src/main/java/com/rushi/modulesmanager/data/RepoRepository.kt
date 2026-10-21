package com.rushi.modulesmanager.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class RepoRepository(
    private val context: Context,
    private val api: RepoApi,
) {
    private val _modules = MutableStateFlow<List<RepoModule>>(emptyList())
    val modules: StateFlow<List<RepoModule>> = _modules.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    val lastUpdated: StateFlow<Long?> = _lastUpdated.asStateFlow()

    private val details = ConcurrentHashMap<String, RepoModule>()
    private var activeSourceId: String? = null

    suspend fun load(source: RepoSource) {
        val switching = activeSourceId != source.id
        if (switching) {
            activeSourceId = source.id
            details.clear()
            val cached = readCache(source)
            _modules.value = cached?.modules.orEmpty()
            _lastUpdated.value = cached?.savedAt
            _error.value = null
        }

        _loading.value = _modules.value.isEmpty()
        _refreshing.value = true
        try {
            val fetched = api.fetchModules(source)
            coroutineContext.ensureActive()
            if (activeSourceId != source.id) return
            _modules.value = fetched.modules.filterNot { it.hide }
            _lastUpdated.value = System.currentTimeMillis()
            _error.value = null
            withContext(Dispatchers.IO) { writeCache(source, fetched.rawJson) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (activeSourceId != source.id) return
            if (_modules.value.isEmpty()) {
                val cached = readCache(source)
                if (cached != null) {
                    _modules.value = cached.modules
                    _lastUpdated.value = cached.savedAt
                    _error.value = "Showing cached data. ${e.userMessage()}"
                } else {
                    _error.value = e.userMessage()
                }
            } else {
                _error.value = e.userMessage()
            }
        } finally {
            _loading.value = false
            _refreshing.value = false
        }
    }

    /** Loads the full module document (readme, all releases) and merges it into the list. */
    suspend fun loadModule(source: RepoSource, packageName: String): RepoModule? {
        details[packageName]?.let { return it }
        return try {
            val module = api.fetchModule(source, packageName)
            coroutineContext.ensureActive()
            details[packageName] = module
            _modules.value = _modules.value.map { if (it.packageName == packageName) module else it }
            module
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    fun cachedModule(packageName: String): RepoModule? =
        details[packageName] ?: _modules.value.firstOrNull { it.packageName == packageName }

    fun clearError() {
        _error.value = null
    }

    private fun cacheFile(source: RepoSource): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.id.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .substring(0, 16)
        return File(context.filesDir, "repo-cache").apply { mkdirs() }.resolve("$digest.json")
    }

    private fun readCache(source: RepoSource): CachedRepo? {
        val file = cacheFile(source)
        if (!file.isFile) return null
        return runCatching {
            CachedRepo(RepoJson.parseModules(file.readText()).filterNot { it.hide }, file.lastModified())
        }.getOrNull()
    }

    private fun writeCache(source: RepoSource, rawJson: String) {
        runCatching { cacheFile(source).writeText(rawJson) }
    }

    private data class CachedRepo(val modules: List<RepoModule>, val savedAt: Long)
}
