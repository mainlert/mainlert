package com.mainlert

import android.app.Application
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.mainlert.data.local.sync.SyncManager
import com.mainlert.services.CheckpointSyncWorker
import com.mainlert.services.SyncManagerHolder
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Main Application class for MainLert app.
 * This class is annotated with @HiltAndroidApp to enable dependency injection.
 * Initializes global holders for WorkManager access and schedules periodic checkpoint sync.
 */
@HiltAndroidApp
class MainLertApplication : Application() {
    @Inject
    lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase explicitly to ensure it's ready before any Firebase API usage
        FirebaseApp.initializeApp(this)
        
        // Initialize global holders for WorkManager
        SyncManagerHolder.syncManager = syncManager
        
        // Schedule periodic checkpoint sync work
        scheduleCheckpointSyncWork()
    }
    
    private fun scheduleCheckpointSyncWork() {
        val constraints = Constraints.Builder()
            // WorkManager runs offline; it will execute when network is available
            // No network constraints needed - let it run and sync when possible
            .build()
        
        val checkpointWorkRequest = PeriodicWorkRequestBuilder<CheckpointSyncWorker>(
            15, TimeUnit.MINUTES  // Minimum interval for PeriodicWorkRequest
        )
            .setConstraints(constraints)
            .addTag(CheckpointSyncWorker.TAG)
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CheckpointSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,  // Don't replace if already scheduled
            checkpointWorkRequest
        )
        
        android.util.Log.i("MainLertApplication", "Checkpoint sync work scheduled every 15 minutes")
    }
}
