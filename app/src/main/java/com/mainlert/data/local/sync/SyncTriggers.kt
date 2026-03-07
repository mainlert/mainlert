package com.mainlert.data.local.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mainlert.data.local.LocalDatabase
import com.mainlert.data.local.entities.VehicleServiceMappingEntity
import com.mainlert.data.local.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Sync triggers and callbacks for hierarchical sync system.
 * Handles automatic sync operations based on app lifecycle and data changes.
 */
class SyncTriggers @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val syncManager: SyncManager,
    private val localDatabase: LocalDatabase,
    private val networkMonitor: NetworkMonitor,
    private val coroutineScope: CoroutineScope
) : LifecycleObserver {
    
    private var isRegistered = false
    
    /**
     * Register sync triggers with lifecycle owner.
     */
    fun registerWithLifecycle(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
    }
    
    /**
     * Called when the lifecycle owner is created.
     * Sets up initial sync triggers.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        setupBroadcastReceivers()
        setupNetworkMonitoring()
    }
    
    /**
     * Called when the lifecycle owner is started.
     * Triggers initial sync if needed.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        triggerInitialSync()
    }
    
    /**
     * Called when the lifecycle owner is resumed.
     * Ensures sync is active.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        ensureSyncActive()
    }
    
    /**
     * Called when the lifecycle owner is paused.
     * Pauses non-critical sync operations.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        pauseNonCriticalSync()
    }
    
    /**
     * Called when the lifecycle owner is stopped.
     * Stops sync operations.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        stopSyncOperations()
    }
    
    /**
     * Called when the lifecycle owner is destroyed.
     * Cleans up resources.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        cleanup()
    }
    
    /**
     * Setup broadcast receivers for various sync triggers.
     */
    private fun setupBroadcastReceivers() {
        if (isRegistered) return
        
        // App state change receiver
        val appStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> onScreenOn()
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_BOOT_COMPLETED -> onBootCompleted()
                }
            }
        }
        
        // Register receivers
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BOOT_COMPLETED)
        }
        
        LocalBroadcastManager.getInstance(context).registerReceiver(appStateReceiver, intentFilter)
        isRegistered = true
    }
    
    /**
     * Setup network monitoring for automatic sync.
     */
    private fun setupNetworkMonitoring() {
        coroutineScope.launch {
            networkMonitor.observeNetworkState().collect { isOnline ->
                if (isOnline) {
                    triggerContinuousSync()
                } else {
                    pauseSyncUntilOnline()
                }
            }
        }
    }
    
    /**
     * Trigger initial sync when app starts.
     */
    private fun triggerInitialSync() {
        coroutineScope.launch {
            // Check if we need initial sync (e.g., if data is stale)
            val lastSyncTime = getLastSyncTime()
            val threshold = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
            
            if (lastSyncTime < threshold) {
                syncManager.syncOnMonitoringStart()
            }
        }
    }
    
    /**
     * Ensure sync is active when app is in foreground.
     */
    private fun ensureSyncActive() {
        coroutineScope.launch {
            // Check for any pending sync operations
            val pendingMappings = localDatabase.mappingDao().getMappingsNeedingSync()
            if (pendingMappings.isNotEmpty()) {
                syncManager.syncContinuousData()
            }
        }
    }
    
    /**
     * Pause non-critical sync operations.
     */
    private fun pauseNonCriticalSync() {
        // Pause continuous sync but keep structure sync available
        // This helps save battery when app is in background
    }
    
    /**
     * Stop all sync operations.
     */
    private fun stopSyncOperations() {
        // Stop any ongoing sync operations
    }
    
    /**
     * Cleanup resources.
     */
    private fun cleanup() {
        if (isRegistered) {
            // Unregister receivers
            isRegistered = false
        }
    }
    
    /**
     * Trigger continuous sync when network becomes available.
     */
    private fun triggerContinuousSync() {
        coroutineScope.launch {
            syncManager.syncContinuousData()
        }
    }
    
    /**
     * Pause sync until network is available again.
     */
    private fun pauseSyncUntilOnline() {
        // Mark sync as paused, will resume when network is back
    }
    
    /**
     * Handle screen on event.
     */
    private fun onScreenOn() {
        // Trigger sync when user unlocks device
        coroutineScope.launch {
            syncManager.syncContinuousData()
        }
    }
    
    /**
     * Handle screen off event.
     */
    private fun onScreenOff() {
        // Pause sync operations to save battery
    }
    
    /**
     * Handle boot completed event.
     */
    private fun onBootCompleted() {
        // Perform initial sync after boot
        coroutineScope.launch {
            syncManager.syncOnMonitoringStart()
        }
    }
    
    /**
     * Get last sync time from database.
     */
    private suspend fun getLastSyncTime(): Long = withContext(Dispatchers.IO) {
        try {
            val mappings = localDatabase.mappingDao().getMappingsNeedingSync()
            mappings.maxOfOrNull { it.firebaseLastUpdated } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}