# Fix for AccelerometerService Stop Monitoring Bug

## Problem Statement

When the user stops monitoring from the Dashboard, the `AccelerometerService` continues to broadcast accelerometer data. The logs show:

```
[2026-03-05 17:22:57] >>> onStartCommand called with action: com.mainlert.mainlertapp.STOP_MONITORING
[2026-03-05 17:22:57] Active mapping restored (currentMappingId=0Nmv15FEHmm9oFQHsO9I), skipping intent processing
```

The STOP_MONITORING intent is being ignored because of an early return condition that checks `currentMappingId != null` before processing the intent action.

## Root Cause

In `AccelerometerService.kt:454-506`, the `onStartCommand` method has this flow:

1. Check for active mapping synchronously (lines 462-467)
2. If `currentMappingId != null`, return early (lines 471-474) - **THIS SKIPS THE INTENT PROCESSING**
3. Only then does the `when (intent?.action)` dispatch happen (lines 477-504)

This means when a STOP_MONITORING intent arrives and `currentMappingId` is set (which it always is during monitoring), the method returns before ever reaching the `ACTION_STOP_MONITORING` case.

## Solution

Reorder the logic to:

1. **Process STOP_MONITORING and RETRY_LOADING immediately** - these actions must always be processed regardless of mapping state
2. For START_MONITORING, allow the restoration check to run (to handle app restarts)
3. Only skip START_MONITORING if an active mapping was found during restoration

### Detailed Changes Required

Replace the entire `onStartCommand` method (lines 454-506) with the following:

```kotlin
override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
): Int {
    android.util.Log.i("AccelerometerService", ">>> onStartCommand called with action: ${intent?.action}")
    
    // Process intent FIRST based on action type
    when (intent?.action) {
        ACTION_STOP_MONITORING -> {
            android.util.Log.i("AccelerometerService", "STOP_MONITORING received, stopping monitoring immediately")
            stopMonitoring()
            // After stopping, clear the active mapping state
            // to prevent automatic restoration on next start
            currentMappingId = null
            currentServiceId = null
            currentVehicleId = null
            return START_STICKY
        }
        ACTION_RETRY_LOADING -> {
            android.util.Log.i("AccelerometerService", "RETRY_LOADING received")
            retryCount = 0
            serviceState = ServiceState.LOADING_FIREBASE_DATA
            loadFirebaseData()
            return START_STICKY
        }
        // For START_MONITORING, continue with normal flow (including restoration check)
        ACTION_START_MONITORING -> {
            // Only set serviceId/vehicleId from intent if they are provided (not blank)
            val serviceIdExtra = intent.getStringExtra(EXTRA_SERVICE_ID)
            val vehicleIdExtra = intent.getStringExtra(EXTRA_VEHICLE_ID)
            
            if (!serviceIdExtra.isNullOrBlank()) {
                currentServiceId = serviceIdExtra
            }
            if (!vehicleIdExtra.isNullOrBlank()) {
                currentVehicleId = vehicleIdExtra
            }
            
            android.util.Log.i("AccelerometerService", "START_MONITORING received, serviceId: $currentServiceId, vehicleId: $currentVehicleId")
        }
        else -> {
            android.util.Log.d("AccelerometerService", "Unknown or null action: ${intent?.action}")
        }
    }
    
    // Check for active mapping synchronously if we don't already have one
    // This allows the service to restore state after a reboot or app restart
    if (currentMappingId == null) {
        android.util.Log.d("AccelerometerService", "No current mapping, performing synchronous restoration check...")
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            checkForActiveMappingAndRestore()
        }
    }
    
    // If restoration found an active mapping, monitoring is already started/loading
    // Skip further processing to avoid overwriting restored state
    if (currentMappingId != null) {
        android.util.Log.i("AccelerometerService", "Active mapping restored (currentMappingId=$currentMappingId), monitoring is active")
        return START_STICKY
    }
    
    // If we get here with a START_MONITORING action but no active mapping,
    // we need to start the Firebase data loading phase
    if (intent?.action == ACTION_START_MONITORING) {
        serviceState = ServiceState.LOADING_FIREBASE_DATA
        loadFirebaseData()
    }
    
    return START_STICKY
}
```

## Key Changes

1. **Early return for STOP_MONITORING**: The STOP_MONITORING case now processes immediately and returns, ensuring `stopMonitoring()` is always called.

2. **Clear mapping state on stop**: After stopping, we clear `currentMappingId`, `currentServiceId`, and `currentVehicleId` to ensure the service starts fresh next time.

3. **Preserve restoration logic**: The active mapping restoration still runs for START_MONITORING (or any other action) when `currentMappingId == null`, preserving the auto-restart functionality after device reboot.

4. **Avoid duplicate processing**: The restoration check only runs when needed, and we only call `loadFirebaseData()` if we have a START_MONITORING intent with no active mapping.

## Expected Behavior After Fix

- **Stop monitoring**: When user taps stop, the service immediately:
  - Calls `stopMonitoring()`
  - Unregisters sensor listener
  - Stops foreground service
  - Clears mapping state
  - Broadcasts stop (with `isMonitoring=false`)
  - Dashboard stops receiving/processing broadcasts

- **Start monitoring**: Works as before, with auto-restore from Firebase if there was an active mapping.

- **App restart**: If the service was monitoring when the app closed, it will restore from Firebase and continue monitoring.

## Testing Checklist

- [ ] Start monitoring and verify accelerometer data appears in dashboard
- [ ] Stop monitoring and verify broadcasts stop (check log for "Stopping monitoring" and "Monitoring stopped")
- [ ] Verify sensor listener is unregistered (no more sensor events processed)
- [ ] Verify foreground service is stopped (notification removed)
- [ ] Start monitoring again and verify it works correctly
- [ ] Test app restart scenario: start monitoring, kill app, verify service restores and continues
- [ ] Test that STOP_MONITORING works even when `currentMappingId` is set

## Additional Notes

The `stopMonitoring()` method already has proper cleanup:
- Sets `isMonitoring = false`
- Unregisters sensor listener
- Stops foreground service
- Cancels coroutines
- Saves final reading to Firebase
- Updates vehicle lifetime mileage

The only issue was that it was never being called due to the early return. This fix ensures it gets called when needed.
