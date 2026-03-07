# Fix for Duplicate Mappings Issue

## Problem Statement

The AccelerometerService is creating duplicate VehicleServiceMappings when restarting with an already active mapping. This causes the reading to reset to 0 instead of continuing from where it stopped.

## Root Cause Analysis

From the log analysis:

1. **Service restart** triggers `checkForActiveMappingAndRestore()`
2. It finds an active mapping: `vehicleId=eBp61Fv9J62XIEXAREYK, serviceId=elZnaf9TyxhelTgFOWjj, mappingId=0Nmv15FEHmm9oFQHsO9I`
3. It sets: `currentVehicleId`, `currentServiceId`, `currentMappingId`, `totalMovement`
4. `onStartCommand()` is called with intent extras: `serviceId=""` (empty), `vehicleId="eBp61Fv9J62XIEXAREYK"`
5. **BUG**: `currentServiceId` is overwritten to empty string, but `currentMappingId` remains set
6. In `loadFirebaseData()`, the code checks `if (currentServiceId != null && currentVehicleId != null)` - this passes because empty string != null
7. Since `currentMappingId` is set, it goes into that branch and loads the mapping correctly
8. **However**, later logic still treats `currentServiceId` as empty, causing `createNewMappingAndStartMonitoring()` to be called
9. This creates duplicate mappings with new IDs, and `currentMappingId` gets overwritten
10. The reading resets to 0 instead of continuing from the previous `totalMovement`

## Solution

Add logic in `loadFirebaseData()` to restore `currentServiceId` from the mapping data when:
- `currentServiceId` is blank/empty
- `currentMappingId` is already set (from active mapping restore)

This should happen BEFORE the mapping loading logic, so that the rest of the code works with the correct serviceId.

## Implementation Details

Location: `AccelerometerService.kt`, inside `loadFirebaseData()` function, around line 310-360

Add this code block after line 313 (before the existing `if (currentMappingId != null)` check):

```kotlin
// FIX: If currentServiceId is blank but we have currentMappingId from active mapping restore,
// restore the serviceId from the mapping to avoid creating duplicate mappings
if ((currentServiceId.isNullOrBlank()) && currentMappingId != null) {
    android.util.Log.d("AccelerometerService", "currentServiceId is blank but currentMappingId is set - restoring serviceId from mapping")
    try {
        val mappingResult = vehicleServiceMappingRepository.getMappingById(currentMappingId!!)
        when (mappingResult) {
            is com.mainlert.data.models.Result.Success -> {
                val mapping = mappingResult.data
                if (mapping != null) {
                    currentServiceId = mapping.serviceId
                    android.util.Log.i("AccelerometerService", "Restored serviceId from mapping: $currentServiceId")
                } else {
                    android.util.Log.e("AccelerometerService", "Mapping with ID $currentMappingId returned null - cannot restore serviceId")
                }
            }
            is com.mainlert.data.models.Result.Failure -> {
                android.util.Log.e("AccelerometerService", "Failed to load mapping for serviceId restore: ${mappingResult.message}")
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("AccelerometerService", "Exception while loading mapping for serviceId restore", e)
    }
}
```

## Expected Behavior After Fix

1. Service restarts, finds active mapping with `mappingId=0Nmv15FEHmm9oFQHsO9I`
2. `onStartCommand()` overwrites `currentServiceId` to empty
3. In `loadFirebaseData()`, the fix detects blank serviceId + set mappingId
4. It loads the mapping by ID and restores `currentServiceId = "elZnaf9TyxhelTgFOWjj"`
5. The existing mapping is used, `totalMovement` continues from previous value (36.83829)
6. No duplicate mappings are created
7. Readings continue seamlessly from where they stopped

## Testing

1. Start monitoring a vehicle/service
2. Let it accumulate some movement (e.g., 100.0)
3. Stop monitoring
4. Start monitoring again (service should restart automatically or manually)
5. Check logcat for:
   - "currentServiceId is blank but currentMappingId is set - restoring serviceId from mapping"
   - "Restored serviceId from mapping: <correct-service-id>"
   - Should NOT see "No mapping found for vehicle X and service  - will create new mapping"
6. Verify `totalMovement` continues from previous value, not reset to 0
7. Verify only ONE mapping exists for the vehicle/service combination in Firebase

## Additional Considerations

- The fix is defensive: it only restores when serviceId is blank and mappingId is set
- It preserves the existing logic flow, just adds a restoration step
- It includes comprehensive logging for debugging
- It handles error cases gracefully (if mapping load fails, it continues with existing logic which will create new mapping as fallback)
