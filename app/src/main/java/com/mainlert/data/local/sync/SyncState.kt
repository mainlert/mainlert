package com.mainlert.data.local.sync

/**
 * Sync state for UI updates.
 */
sealed class SyncState {
    object Idle : SyncState()
    object Offline : SyncState()
    object SyncingStructure : SyncState()
    object StructureSynced : SyncState()
    object SyncingContinuous : SyncState()
    object ContinuousSynced : SyncState()
    data class Error(val message: String) : SyncState()
}