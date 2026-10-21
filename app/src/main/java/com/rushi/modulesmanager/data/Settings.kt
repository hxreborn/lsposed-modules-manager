package com.rushi.modulesmanager.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("modules_manager", Context.MODE_PRIVATE)

    private val _channel = MutableStateFlow(UpdateChannel.fromKey(prefs.getString(KEY_CHANNEL, null)))
    val channel: StateFlow<UpdateChannel> = _channel.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.fromKey(prefs.getString(KEY_SORT, null)))
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _updatesFirst = MutableStateFlow(prefs.getBoolean(KEY_UPDATES_FIRST, true))
    val updatesFirst: StateFlow<Boolean> = _updatesFirst.asStateFlow()

    fun setChannel(channel: UpdateChannel) {
        prefs.edit().putString(KEY_CHANNEL, channel.key).apply()
        _channel.value = channel
    }

    fun setSortMode(mode: SortMode) {
        prefs.edit().putString(KEY_SORT, mode.key).apply()
        _sortMode.value = mode
    }

    fun setUpdatesFirst(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATES_FIRST, enabled).apply()
        _updatesFirst.value = enabled
    }

    fun lastLiveCheck(): Long = prefs.getLong(KEY_LAST_LIVE_CHECK, 0L)

    fun setLastLiveCheck(time: Long) {
        prefs.edit().putLong(KEY_LAST_LIVE_CHECK, time).apply()
    }

    private companion object {
        const val KEY_CHANNEL = "update_channel"
        const val KEY_SORT = "sort_mode"
        const val KEY_UPDATES_FIRST = "updates_first"
        const val KEY_LAST_LIVE_CHECK = "last_live_check"
    }
}
