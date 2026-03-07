# AccelerometerService Refactoring - Complete

## Changes Implemented

### 1. Added Distributed Locking to Prevent Duplicate Mappings

**Problem**: Multiple service processes were creating duplicate VehicleServiceMappings for the same vehicle+service pair.

**Solution**: Implemented Firestore-based distributed locking using transactions:
- Lock document: `locks/mapping_lock_{vehicleId}_{serviceId}`
- 5-second timeout with retry every 500ms
- Automatic lock cleanup in `finally` block
- Double-check after lock acquisition to catch races

### 2. Eliminated Duplicated Mapping Loading Code

**Before**: Two nearly identical code blocks (lines 341-385 and 387-434) with same error handling.

**After**: Single unified function `loadOrCreateMapping()` that:
- Tries to load existing mapping via `loadExistingMapping()`
- If not found, acquires lock and creates via `createMappingWithLock()`
- Returns mapping or null

### 3. Extracted Helper Functions

- `loadExistingMapping()`: Uses currentMappingId if available, otherwise queries by vehicle+service
- `createMappingWithLock()`: Atomic check-and-create with distributed lock
- `startMonitoringWithMapping()`: Sets up monitoring state and starts service
- `loadVehicleMileageAndStartMonitoring()`: Loads vehicle data and starts monitoring

### 4. Code Reduction

- **Removed**: ~100 lines of duplicated code
- **Added**: ~150 lines of well-structured, maintainable code
- **Net**: Better readability with improved reliability

## Files Modified

- `app/src/main/java/com/mainlert/services/AccelerometerService.kt`
  - Added imports: `Process`, `FieldValue`, `AtomicBoolean`
  - Added `TAG` constant for logging
  - Inserted 4 new functions (lines 1247-1347)
  - Replaced duplicated code with single call (lines 340-352)

## Testing Checklist

- [ ] Start monitoring with single vehicle - should create one mapping
- [ ] Start monitoring from multiple service processes simultaneously - should not create duplicates
- [ ] Stop and restart monitoring - should restore existing mapping
- [ ] Verify lock documents are created and deleted properly
- [ ] Check that logs show "Lock acquired" only once per mapping creation
- [ ] Test failure scenarios (network loss, permission denied) - locks should be released

## Expected Behavior After Fix

### Before (Race Condition)
```
[Process 531] No mapping found - creating
[Process 531] Created mapping: mMbK7s5w35vJdF5D3rHI
[Process 30563] No mapping found - creating
[Process 30563] Created mapping: PX2sGNYFyogpmMDmfukM  // DUPLICATE!
```

### After (Locking)
```
[Process 531] Acquired lock
[Process 531] Lock acquired, creating mapping
[Process 531] Created mapping: abc123
[Process 30563] Lock exists, waiting...
[Process 30563] Mapping already created by another process: abc123
```

## Additional Benefits

1. **Maintainability**: Single source of truth for mapping loading logic
2. **Observability**: Clear logs showing lock acquisition and release
3. **Reliability**: Automatic lock cleanup even on exceptions
4. **Scalability**: Lock mechanism works across multiple processes/instances

## Migration Notes

- No database schema changes required
- Existing mappings continue to work normally
- Lock collection will be created automatically on first use
- Consider adding Firestore TTL policy to `locks` collection for automatic cleanup (30 seconds)

## Next Steps

1. Deploy to test environment
2. Monitor logs for lock contention and failures
3. Verify no duplicate mappings are created
4. Consider implementing similar locking pattern in other write operations if needed