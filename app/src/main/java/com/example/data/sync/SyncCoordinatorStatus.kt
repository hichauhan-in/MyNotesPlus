package com.example.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide, observable sync state so any screen (e.g. the animated "+" on Home) can reflect
 * whether a sync is currently running, regardless of which view model kicked it off.
 */
object SyncStatus {
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    internal fun setSyncing(value: Boolean) {
        _syncing.value = value
    }
}
