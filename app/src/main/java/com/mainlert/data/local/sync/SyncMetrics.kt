package com.mainlert.data.local.sync

/**
 * Sync metrics for monitoring and debugging.
 */
data class SyncMetrics(
    val syncStartTime: Long = 0L,
    val syncEndTime: Long = 0L,
    val itemsSynced: Int = 0,
    val conflictsResolved: Int = 0,
    val errors: List<String> = emptyList()
)