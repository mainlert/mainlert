package com.mainlert.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mainlert.data.local.sync.SyncManager
import kotlinx.coroutines.withTimeout

/**
 * WorkManager worker that syncs checkpoint data to Firebase.
 * Runs offline (no network constraints) and syncs when network is available.
 * Used for crash recovery and app exit scenarios.
 */
class CheckpointSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val syncManager: SyncManager? = SyncManagerHolder.syncManager

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.d("CheckpointSyncWorker", "Starting checkpoint sync work")
            
            // Timeout after 30 seconds to avoid long-running work
            val result = withTimeout(30000L) {
                syncManager?.syncContinuousData()
            }
            
            when (result) {
                null -> {
                    android.util.Log.w("CheckpointSyncWorker", "SyncManager not available")
                    Result.failure()
                }
                else -> {
                    android.util.Log.i("CheckpointSyncWorker", "Checkpoint sync completed successfully")
                    Result.success()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CheckpointSyncWorker", "Checkpoint sync failed", e)
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "CheckpointSyncWork"
        const val TAG = "CheckpointSyncWorker"
    }
}
