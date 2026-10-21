package com.rushi.modulesmanager.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("modules_manager", Context.MODE_PRIVATE)

    private val _channel = MutableStateFlow(UpdateChannel.fromKey(prefs.getString(KEY_CHANNEL, null)))
    val channel: StateFlow<UpdateChannel> = _channel.asStateFlow()

    private val _sourceId = MutableStateFlow(prefs.getString(KEY_SOURCE, null) ?: DefaultSources.official.id)
    val sourceId: StateFlow<String> = _sourceId.asStateFlow()

    private val _customSources = MutableStateFlow(readCustomSources())
    val customSources: StateFlow<List<RepoSource>> = _customSources.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.fromKey(prefs.getString(KEY_SORT, null)))
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _updatesFirst = MutableStateFlow(prefs.getBoolean(KEY_UPDATES_FIRST, true))
    val updatesFirst: StateFlow<Boolean> = _updatesFirst.asStateFlow()

    fun setChannel(channel: UpdateChannel) {
        prefs.edit().putString(KEY_CHANNEL, channel.key).apply()
        _channel.value = channel
    }

    fun setSourceId(id: String) {
        prefs.edit().putString(KEY_SOURCE, id).apply()
        _sourceId.value = id
    }

    fun setSortMode(mode: SortMode) {
        prefs.edit().putString(KEY_SORT, mode.key).apply()
        _sortMode.value = mode
    }

    fun setUpdatesFirst(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATES_FIRST, enabled).apply()
        _updatesFirst.value = enabled
    }

    fun addCustomSource(base: String): RepoSource? {
        val normalized = normalizeRepoBase(base) ?: return null
        val source = sourceFromBase(normalized)
        val existing = _customSources.value
        if (existing.any { it.bases.first() == normalized } || normalized == DefaultSources.official.bases.first()) {
            return existing.firstOrNull { it.bases.first() == normalized } ?: DefaultSources.official
        }
        val updated = existing + source
        persistCustomSources(updated)
        _customSources.value = updated
        return source
    }

    fun removeCustomSource(id: String) {
        val updated = _customSources.value.filterNot { it.id == id }
        persistCustomSources(updated)
        _customSources.value = updated
        if (_sourceId.value == id) setSourceId(DefaultSources.official.id)
    }

    private fun persistCustomSources(sources: List<RepoSource>) {
        val array = JSONArray()
        sources.forEach { array.put(it.bases.first()) }
        prefs.edit().putString(KEY_CUSTOM_SOURCES, array.toString()).apply()
    }

    private fun readCustomSources(): List<RepoSource> {
        val raw = prefs.getString(KEY_CUSTOM_SOURCES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val base = array.optString(index)
                    normalizeRepoBase(base)?.let { add(sourceFromBase(it)) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY_CHANNEL = "update_channel"
        const val KEY_SOURCE = "source_id"
        const val KEY_CUSTOM_SOURCES = "custom_sources"
        const val KEY_SORT = "sort_mode"
        const val KEY_UPDATES_FIRST = "updates_first"
    }
}
