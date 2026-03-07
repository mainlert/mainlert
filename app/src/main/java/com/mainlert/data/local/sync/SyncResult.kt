package com.mainlert.data.local.sync

/**
 * Sync result for individual mapping operations.
 */
data class SyncResult(
    val success: Boolean,
    val conflictResolved: Boolean
)