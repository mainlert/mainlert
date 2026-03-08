# Service Persistence Enhancement Plan

## Problem Statement

The current AccelerometerService loses monitoring state when the app exits, closes, or crashes. Specifically:

1. **totalMovement resets to 0** on service restart, even if Firebase has a more recent value
2. **No periodic checkpointing** - if the app crashes, all movement since the last Firebase update is lost
3. **onDestroy() is incomplete** - doesn't reliably save final state before service termination
4. **No background backup** - if the app is killed before onDestroy() runs, state is lost
5. **State restoration incomplete** - service restores mapping metadata but not the actual reading

## Current Persistence Mechanisms

### What Works:
- **Firebase real-time sync**: VehicleServiceMapping.totalMovement is updated continuously via `updateFirebaseMappingForSelectedService()`
- **Active mapping restoration**: Service checks Firebase for `isMonitoring=true` mappings on startup and restores metadata
- **BootReceiver**: Automatically restarts service after device reboot
- **Local database**: VehicleServiceMappingEntity stores `totalMovement` but is not used for state restoration

### What Doesn't Work:
- **Memory-only state**: `totalMovement`, `currentMappingId`, `isMonitoring` exist only in memory
- **No local checkpoint**: Service never reads `totalMovement` from local database on startup
- **Race conditions**: Multiple service instances can overwrite each other's readings
- **onDestroy() unreliability**: System may kill service without calling onDestroy()

## Root Cause Analysis

### Issue 1: totalMovement Reset on Restart
```kotlin
// AccelerometerService.kt: startMonitoring()
totalMovement = 0f  // ALWAYS resets to 0
```

Even though Firebase has the correct value, the service never loads it on startup. It only loads the mapping metadata.

### Issue 2: No Periodic Checkpoint
The service updates Firebase continuously but:
- If Firebase write fails, data is lost
- If app crashes before Firebase write, data is lost
- No local backup of recent readings

### Issue 3: onDestroy() May Not Execute
Android can kill a foreground service without calling onDestroy() in extreme scenarios (system memory pressure, force stop, crash).

### Issue 4: State Not Persisted Locally
The local database has `VehicleServiceMappingEntity.totalMovement` but it's only updated via sync operations, not by the service itself.

## Proposed Solution: Multi-Layer Persistence Strategy

### Layer 1: Local Database Checkpointing (Primary)
**Frequency**: Every 30 seconds during monitoring
**What**: Save current `totalMovement` to local Room database
**Why**: Provides recent state even if Firebase is unavailable

### Layer 2: Enhanced Firebase Sync (Continuous)
**Frequency**: Every 500ms when vehicle movement detected (already exists)
**Improvement**: Add local database update after successful Firebase write
**Why**: Keep local database in sync with Firebase

### Layer 3: State Restoration on Startup (Robust)
**Order**:
1. Check local database for most recent `totalMovement` for the active mapping
2. If found, use that as the starting value
3. Sync with Firebase to get the authoritative value
4. Use the maximum of (local, Firebase) to prevent data loss

### Layer 4: WorkManager Backup (Crash Recovery)
**Trigger**: When service is stopped normally or crashes
**What**: Schedule a one-time WorkManager job to ensure final state is saved
**Why**: Even if onDestroy() doesn't complete, WorkManager can finish the save

### Layer 5: Improved onDestroy() (Graceful Shutdown)
**Add**: Synchronous save with timeout before stopping
**Why**: Best effort to save state before service terminates

## Detailed Implementation Plan

### Phase 1: Local Database Checkpointing

#### 1.1 Add DAO Method for Checkpoint Update
**File**: `app/src/main/java/com/mainlert/data/local/dao/VehicleServiceMappingDao.kt`

```kotlin
@Query("UPDATE vehicle_service_mappings SET totalMovement = :movement, lastReadingTime = :timestamp, localLastUpdated = :timestamp WHERE id = :mappingId")
suspend fun updateMovementCheckpoint(mappingId: String, movement: Float, timestamp: Long)
```

#### 1.2 Add Repository Method
**File**: `app/src/main/java/com/mainlert/data/repositories/VehicleServiceMappingRepository.kt`

```kotlin
/**
 * Saves a checkpoint of the current movement reading to local database.
 * Used for crash recovery and state persistence.
 */
suspend fun saveMovementCheckpoint(mappingId: String, totalMovement: Float): Result<Unit>
```

#### 1.3 Implement in AccelerometerService
**File**: `app/src/main/java/com/mainlert/services/AccelerometerService.kt`

Add:
- Checkpoint interval constant (e.g., 30 seconds)
- Last checkpoint timestamp tracking
- Periodic checkpoint coroutine

```kotlin
private val CHECKPOINT_INTERVAL_MS = 30000L // 30 seconds
private var lastCheckpointTime = 0L

private fun maybePerformCheckpoint() {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastCheckpointTime >= CHECKPOINT_INTERVAL_MS) {
        lastCheckpointTime = currentTime
        performCheckpoint()
    }
}

private fun performCheckpoint() {
    if (currentMappingId != null && isMonitoring) {
        serviceScope.launch {
            try {
                vehicleServiceMappingRepository.saveMovementCheckpoint(
                    currentMappingId!!,
                    totalMovement
                )
                android.util.Log.d("AccelerometerService", "Checkpoint saved: mappingId=$currentMappingId, totalMovement=$totalMovement")
            } catch (e: Exception) {
                android.util.Log.e("AccelerometerService", "Failed to save checkpoint", e)
            }
        }
    }
}
```

Call `maybePerformCheckpoint()` in `processMovementData()` after updating `totalMovement`.

### Phase 2: Enhanced State Restoration

#### 2.1 Load Local Checkpoint on Startup
Modify `AccelerometerService.kt: startMonitoringWithMapping()`:

```kotlin
private suspend fun startMonitoringWithMapping(mapping: VehicleServiceMapping) {
    currentMappingId = mapping.id
    currentServiceMileageLimit = mapping.mileageLimit

    // NEW: Load local checkpoint first
    val localCheckpoint = loadLocalCheckpoint(currentMappingId!!)
    if (localCheckpoint != null) {
        android.util.Log.i(TAG, "Found local checkpoint: totalMovement=${localCheckpoint.value}")
        totalMovement = localCheckpoint.value
    } else {
        totalMovement = 0f
    }

    // Continue with existing logic...
}
```

#### 2.2 Add Checkpoint Loading Method
```kotlin
private suspend fun loadLocalCheckpoint(mappingId: String): VehicleServiceMappingEntity? {
    return try {
        val entity = localDatabase.mappingDao().getMapping(mappingId)
        entity?.takeIf { it.localLastUpdated > 0 }
    } catch (e: Exception) {
        android.util.Log.e(TAG, "Failed to load checkpoint", e)
        null
    }
}
```

**Note**: Need to inject `LocalDatabase` into AccelerometerService.

### Phase 3: WorkManager Backup

#### 3.1 Add Dependency
**File**: `app/build.gradle`

```gradle
dependencies {
    implementation "androidx.work:work-runtime-ktx:2.9.0"
}
```

#### 3.2 Create SaveStateWorker
**File**: `app/src/main/java/com/mainlert/services/SaveStateWorker.kt`

```kotlin
package com.mainlert.services

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import com.mainlert.data.local.LocalDatabase
import com.mainlert.data.repositories.VehicleServiceMappingRepository
import com.mainlert.data.repositories.VehicleRepository
import android.util.Log

class SaveStateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result = runBlocking {
        try {
            // Get Hilt dependencies
            val appContext = applicationContext
            val localDatabase = EntryPointAccessors.fromApplication(
                appContext,
                SaveStateWorkerEntryPoint::class.java
            ).localDatabase()

            val vehicleServiceMappingRepository = EntryPointAccessors.fromApplication(
                appContext,
                SaveStateWorkerEntryPoint::class.java
            ).vehicleServiceMappingRepository()

            val vehicleRepository = EntryPointAccessors.fromApplication(
                appContext,
                SaveStateWorkerEntryPoint::class.java
            ).vehicleRepository()

            // Find any active mappings and save their state
            val activeMappings = localDatabase.mappingDao().getMappingsNeedingSync()
            
            for (mapping in activeMappings) {
                if (mapping.isMonitoring) {
                    // Save checkpoint
                    localDatabase.mappingDao().updateMovementCheckpoint(
                        mapping.id,
                        mapping.totalMovement,
                        System.currentTimeMillis()
                    )
                    
                    // Also try to sync to Firebase
                    try {
                        vehicleServiceMappingRepository.updateMappingMovement(
                            mapping.id,
                            mapping.totalMovement
                        )
                    } catch (e: Exception) {
                        Log.w("SaveStateWorker", "Failed to sync mapping ${mapping.id} to Firebase: ${e.message}")
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SaveStateWorker", "Failed to save state", e)
            Result.retry()
        }
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface SaveStateWorkerEntryPoint {
        fun localDatabase(): LocalDatabase
        fun vehicleServiceMappingRepository(): VehicleServiceMappingRepository
        fun vehicleRepository(): VehicleRepository
    }
}
```

#### 3.3 Schedule WorkManager Job in onDestroy()
```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    if (isMonitoring && currentMappingId != null) {
        // Schedule WorkManager job to save state
        val saveStateWorkRequest = androidx.work.OneTimeWorkRequestBuilder<SaveStateWorker>()
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
        
        androidx.work.WorkManager.getInstance(this).enqueue(saveStateWorkRequest)
        android.util.Log.d("AccelerometerService", "Scheduled WorkManager job to save state")
    }
    
    // Existing cleanup...
}
```

### Phase 4: Improved onDestroy() with Timeout

#### 4.1 Add Synchronous Save with Timeout
```kotlin
override fun onDestroy() {
    val startTime = System.currentTimeMillis()
    val TIMEOUT_MS = 5000L // 5 seconds max wait
    
    // Best effort synchronous save before destruction
    if (isMonitoring && currentMappingId != null) {
        try {
            // Use runBlocking with timeout
            kotlinx.coroutines.withTimeout(TIMEOUT_MS) {
                runBlocking {
                    // Save checkpoint to local database (fast, synchronous)
                    localDatabase.mappingDao().updateMovementCheckpoint(
                        currentMappingId!!,
                        totalMovement,
                        System.currentTimeMillis()
                    )
                    
                    // Try to save to Firebase (may fail if offline)
                    vehicleServiceMappingRepository.updateMappingMovement(
                        currentMappingId!!,
                        totalMovement
                    )
                }
            }
            android.util.Log.i("AccelerometerService", "State saved successfully in onDestroy()")
        } catch (e: Exception) {
            android.util.Log.w("AccelerometerService", "Failed to save state in onDestroy(): ${e.message}")
            // WorkManager job will handle it asynchronously
        }
    }
    
    // Continue with existing cleanup...
    // (rest of onDestroy code)
}
```

### Phase 5: Prevent Duplicate Service Instances

#### 5.1 Add Service-Level Lock
To prevent multiple service instances from overwriting each other:

```kotlin
companion object {
    private var serviceInstanceCount = 0
    private val serviceLock = Any()
}

override fun onCreate() {
    super.onCreate()
    synchronized(serviceLock) {
        serviceInstanceCount++
        android.util.Log.d(TAG, "Service instance count: $serviceInstanceCount")
    }
    // existing code...
}

override fun onDestroy() {
    synchronized(serviceLock) {
        serviceInstanceCount--
        android.util.Log.d(TAG, "Service instance count after destroy: $serviceInstanceCount")
    }
    // existing cleanup...
}
```

### Phase 6: Configuration and Testing

#### 6.1 Add Hilt Module for LocalDatabase Injection
Ensure `LocalDatabase` is available in AccelerometerService:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    @Provides
    fun provideLocalDatabase(@ApplicationContext context: Context): LocalDatabase {
        return Room.databaseBuilder(
            context,
            LocalDatabase::class.java,
            "mainlert_database"
        ).build()
    }
}
```

#### 6.2 Update AccelerometerService to Inject LocalDatabase
```kotlin
@AndroidEntryPoint
class AccelerometerService : Service(), SensorEventListener {
    
    @Inject
    lateinit var localDatabase: LocalDatabase
    
    // existing code...
}
```

## Testing Strategy

### Test 1: Normal Stop
1. Start monitoring
2. Let it accumulate some movement
3. Tap Stop button
4. Verify:
   - Final reading saved to Firebase
   - Local checkpoint cleared or marked as inactive
   - Service stops cleanly

### Test 2: App Crash (Force Stop)
1. Start monitoring
2. Accumulate movement
3. Force stop the app via Settings or `adb shell am force-stop`
4. Restart app
5. Verify:
   - Service restores from local checkpoint
   - totalMovement continues from last checkpoint
   - No data loss

### Test 3: Service Kill (Low Memory)
1. Start monitoring
2. Simulate low memory kill via `adb shell am kill`
3. Verify service restarts automatically (START_STICKY)
4. Verify state restored from checkpoint

### Test 4: Device Reboot
1. Start monitoring
2. Reboot device
3. Verify:
   - BootReceiver starts service
   - Service loads checkpoint and restores state
   - Monitoring continues

### Test 5: Firebase Outage
1. Start monitoring offline
2. Accumulate movement
3. Verify checkpoints saved to local database
4. Restore internet
5. Verify all checkpoint data syncs to Firebase

### Test 6: Multiple Service Instances
1. Rapidly start/stop monitoring multiple times
2. Verify only one instance runs at a time
3. Verify no race conditions in checkpoint writes

## Rollout Plan

### Week 1: Core Checkpointing
- Day 1-2: Implement DAO method and repository interface
- Day 3-4: Add checkpoint logic to AccelerometerService
- Day 5: Test and debug

### Week 2: State Restoration & WorkManager
- Day 1-2: Implement state restoration from checkpoint
- Day 3-4: Add WorkManager backup
- Day 5: Integration testing

### Week 3: Refinement & Testing
- Day 1-2: Add service instance locking
- Day 3-4: Comprehensive testing across scenarios
- Day 5: Bug fixes and optimization

## Success Criteria

✅ `totalMovement` persists across app crashes and restarts  
✅ Maximum data loss is 30 seconds (checkpoint interval)  
✅ Service automatically restarts after system kill  
✅ No duplicate service instances  
✅ WorkManager ensures final state is saved even if onDestroy() fails  
✅ All tests pass consistently  

## Risks and Mitigations

### Risk 1: Database Contention
**Mitigation**: Use transactions and proper Room threading. Checkpoint writes are infrequent (30s) and fast.

### Risk 2: Battery Impact
**Mitigation**: Checkpoint interval is 30 seconds, which is acceptable for a foreground service. Can be made configurable if needed.

### Risk 3: Data Inconsistency
**Mitigation**: Use maximum of (local, Firebase) on restore. Conflict resolution handles divergent values.

### Risk 4: WorkManager Not Executing
**Mitigation**: Use `setBackoffCriteria()` and `Result.retry()` for transient failures. Monitor WorkManager logs.

## Monitoring and Observability

Add logs at key points:
- Checkpoint save/load
- State restoration decisions
- WorkManager job execution
- Service instance count

Consider adding a debug screen to show:
- Current checkpoint timestamp
- Last Firebase sync time
- Service instance count

## Conclusion

This multi-layer persistence strategy ensures monitoring continues seamlessly across app lifecycle events with minimal data loss. The combination of local checkpointing, robust restoration, and WorkManager backup provides resilience against crashes, kills, and network outages.
