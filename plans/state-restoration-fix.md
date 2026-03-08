# State Restoration Fix: UI Loses Monitored Vehicle After App Destroy

## Problem Statement

When the app UI is destroyed (e.g., by clearing recent apps), the accelerometer service continues running in the background, but when the UI is relaunched, it loses the previous monitoring vehicle state. The UI shows no active monitoring even though the service is still collecting data.

## Root Cause Analysis

### Current State Management Flow

1. **AccelerometerService** runs as a foreground service and maintains its own state:
   - `currentVehicleId`, `currentServiceId`, `currentMappingId`
   - `isMonitoring` flag
   - `totalMovement` reading
   - Broadcasts state updates via `LocalBroadcastManager`

2. **DashboardViewModel**:
   - Registers a `BroadcastReceiver` to listen for accelerometer updates
   - Maintains UI state: `_isMonitoring`, `monitoredVehicleId`, `_selectedVehicle`, etc.
   - When a broadcast arrives with `isMonitoring=true`, it sets `monitoredVehicleId` and loads vehicle data
   - When a broadcast arrives with `isMonitoring=false`, it clears the state

3. **DashboardScreen**:
   - Collects state from DashboardViewModel
   - Displays monitoring status and vehicle information

### The Gap

**When the UI is destroyed and recreated:**

1. The `AccelerometerService` continues running (it's a foreground service)
2. A new `DashboardViewModel` is created with a fresh state:
   - `_isMonitoring = false`
   - `monitoredVehicleId = null`
   - `_selectedVehicle = null`
3. The new ViewModel registers its `BroadcastReceiver` in `init{}`
4. **BUT**: The service is already monitoring and may not send an immediate broadcast
5. The UI appears with no active monitoring shown, even though monitoring is actually active

### Why No Immediate Broadcast?

Looking at `AccelerometerService.broadcastAccelerometerData()`:
- It broadcasts on every sensor event (throttled to 500ms)
- If the service is monitoring and sensor events are occurring, broadcasts will happen
- However, there's no guarantee of an immediate broadcast after the receiver registers
- The UI could be out of sync for an indeterminate time

## Solution Approach

### Option 1: Proactive State Query on ViewModel Init (CHOSEN)

When the DashboardViewModel starts up, it should:
1. Query the `VehicleServiceMappingRepository` for an active mapping (`getActiveMapping()`)
2. If an active mapping exists with `isMonitoring=true`:
   - Set `_isMonitoring = true`
   - Set `monitoredVehicleId` from the mapping
   - Load the vehicle details into `_selectedVehicle`
   - Load the services and mappings for that vehicle
3. This ensures the UI state is immediately synchronized with the actual service state

**Advantages:**
- Immediate state synchronization on UI launch
- No dependency on broadcast timing
- Works even if service hasn't sent a recent broadcast
- Simple and reliable

**Disadvantages:**
- Requires an extra repository call on startup (negligible overhead)

### Option 2: Service Broadcasts State on Demand

Add a method to `AccelerometerService` to immediately broadcast its current state when a new UI connects. The ViewModel could send an intent to the service to trigger this broadcast.

**Advantages:**
- Keeps repository query out of ViewModel
- Service remains in control of its state

**Disadvantages:**
- More complex: requires intent-based communication from ViewModel to Service
- Timing issues: service might not process the intent immediately
- Less direct than querying repository

### Option 3: SharedPreferences for Monitoring State

Store the current monitoring state (vehicleId, mappingId, isMonitoring) in SharedPreferences that both service and UI can read.

**Advantages:**
- Simple key-value store
- No repository calls needed

**Disadvantages:**
- Duplication of state (already in Firebase and local DB)
- Risk of inconsistency
- Not needed given existing Firebase repository

## Implementation Plan

### 1. Add `restoreMonitoringStateIfActive()` method to DashboardViewModel

```kotlin
private fun restoreMonitoringStateIfActive() {
    viewModelScope.launch {
        try {
            val activeMappingResult = vehicleServiceMappingRepository.getActiveMapping()
            when (activeMappingResult) {
                is Result.Success -> {
                    val activeMapping = activeMappingResult.data
                    if (activeMapping != null && activeMapping.isMonitoring) {
                        android.util.Log.i("DashboardViewModel", "Found active monitoring state: vehicleId=${activeMapping.vehicleId}, mappingId=${activeMapping.id}")
                        
                        // Restore UI state
                        monitoredVehicleId = activeMapping.vehicleId
                        currentMappingId = activeMapping.id
                        _isMonitoring.value = true
                        
                        // Load vehicle details
                        when (val vehicleResult = vehicleRepository.getVehicleById(activeMapping.vehicleId)) {
                            is Result.Success -> {
                                _selectedVehicle.value = vehicleResult.data
                                android.util.Log.d("DashboardViewModel", "Restored selected vehicle: ${vehicleResult.data?.name}")
                            }
                            is Result.Failure -> {
                                android.util.Log.w("DashboardViewModel", "Failed to load vehicle for restoration: ${activeMapping.vehicleId}")
                            }
                        }
                        
                        // Load services and mappings for this vehicle
                        loadServicesForVehicle(activeMapping.vehicleId)
                        loadMappingsForVehicles(listOf(activeMapping.vehicleId))
                        
                        android.util.Log.i("DashboardViewModel", "Monitoring state restored successfully")
                    } else {
                        android.util.Log.d("DashboardViewModel", "No active monitoring found")
                    }
                }
                is Result.Failure -> {
                    android.util.Log.w("DashboardViewModel", "Failed to query active mapping: ${activeMappingResult.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error restoring monitoring state", e)
        }
    }
}
```

### 2. Call this method in the ViewModel's `init` block

Add after the existing initialization code:
```kotlin
viewModelScope.launch {
    restoreMonitoringStateIfActive()
}
```

### 3. Ensure `getActiveMapping()` queries the local database

The repository's `getActiveMapping()` should query the local database (which is kept in sync with Firebase). This is already implemented in `LocalVehicleServiceMappingRepositoryImpl.getActiveMapping()`:
```kotlin
override suspend fun getActiveMapping(): Result<VehicleServiceMapping?> {
    return try {
        val entitiesFlow = localDatabase.mappingDao().getAllMappings()
        val entities = entitiesFlow.first() // Collect the Flow
        val activeMapping = entities.find { it.isMonitoring }
        
        if (activeMapping != null) {
            Result.Success(activeMapping.toDomain())
        } else {
            Result.Success(null)
        }
    } catch (e: Exception) {
        Result.Failure(e.message ?: "Failed to get active mapping")
    }
}
```

This queries the local Room database, which is fast and works offline.

## Testing Strategy

1. **Start monitoring** on a vehicle
2. **Clear recent apps** (destroy UI)
3. **Relaunch the app**
4. **Expected**: UI immediately shows:
   - Monitoring is active
   - The previously monitored vehicle is selected
   - Service readings are displayed with accumulated values
5. **Verify** that the accelerometer data continues to update in real-time

## Edge Cases Handled

- **No active mapping**: UI shows normal state (no monitoring)
- **Multiple active mappings**: `getActiveMapping()` returns the first one (should be only one due to vehicle locking)
- **Repository error**: Logged but doesn't crash; UI remains in default state
- **Vehicle not found**: Logged as warning; monitoring state still shown but vehicle details may be missing

## Files to Modify

1. `app/src/main/java/com/mainlert/ui/viewmodels/DashboardViewModel.kt`
   - Add `restoreMonitoringStateIfActive()` method
   - Call it in `init` block

## Additional Considerations

- The existing broadcast receiver will continue to work and keep the UI synchronized with real-time updates
- This fix is idempotent: if the service sends a broadcast before or after restoration, the state will be consistent
- No changes needed to `AccelerometerService` or repository interfaces

## Implementation Summary

This is a **single-file change** to `DashboardViewModel.kt` that adds proactive state restoration on startup. The fix is minimal, focused, and solves the exact problem described.
