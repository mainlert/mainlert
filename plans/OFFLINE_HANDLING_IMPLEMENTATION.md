# Offline Handling Implementation - MainLert

## Overview

This document describes the implementation of offline-first capabilities for the MainLert app's monitoring and syncing system. The primary goal is to allow the accelerometer monitoring service to start and operate without an internet connection, using locally cached configuration data.

## Problem Statement

**Original Issue**: [`AccelerometerService`](app/src/main/java/com/mainlert/services/AccelerometerService.kt) refused to start without internet connection because it required fetching RemoteConfig thresholds and Firebase mapping data at startup.

**Impact**: Core monitoring functionality was unavailable when offline, violating the offline-first architecture principle.

## Solution Architecture

### 1. Local Threshold Caching

**New Component**: [`ThresholdCache`](app/src/main/java/com/mainlert/services/ThresholdCache.kt)

- Uses Android SharedPreferences for persistent storage
- Caches three key values:
  - `crashThreshold` (Float): Threshold for detecting crashes
  - `minThreshold` (Float): Minimum movement threshold
  - `updateInterval` (Long): Sensor update interval
- Provides fallback to safe defaults if cache is empty
- Methods:
  - `saveThresholds()`: Store thresholds when online
  - `getCrashThreshold()`, `getMinThreshold()`, `getUpdateInterval()`: Retrieve cached values
  - `hasCachedThresholds()`: Check if cache exists
  - `clearCache()`: For testing/debugging

**Default Values** (safe for all vehicles):
- `crashThreshold = 3.0f` (G-force)
- `minThreshold = 0.5f` (G-force)
- `updateInterval = 1000L` (milliseconds)

### 2. AccelerometerService Changes

**Modified Method**: [`loadFirebaseData()`](app/src/main/java/com/mainlert/services/AccelerometerService.kt:334-443)

**Key Changes**:
- Added `isUsingCachedThresholds` flag to track offline mode
- Split logic into online and offline branches:
  - **Offline**: Load thresholds from `ThresholdCache` immediately, continue to mapping loading
  - **Online**: Fetch from RemoteConfig, save to cache, then continue
- Mapping loading logic executes in **both** cases (critical for offline operation)
- Removed early return when offline - service now proceeds with monitoring setup

**New State**: `ServiceState` enum unchanged - monitoring can now reach `MONITORING` state even when offline

**Injection**: Added `ThresholdCache` dependency via Hilt

### 3. Broadcast Extensions

**New Intent Extra**: `EXTRA_IS_USING_CACHED_THRESHOLDS`

- Added to [`AccelerometerService`](app/src/main/java/com/mainlert/services/AccelerometerService.kt:73) companion object
- Included in [`broadcastAccelerometerData()`](app/src/main/java/com/mainlert/services/AccelerometerService.kt:988) intent
- Allows UI to know if monitoring is using cached (offline) thresholds

### 4. DashboardViewModel Updates

**New State Flow**: `isUsingCachedThresholds: StateFlow<Boolean>`

- Collects the offline mode flag from accelerometer broadcasts
- Exposes to UI for real-time indicator

**Modified Receiver**: [`accelerometerReceiver`](app/src/main/java/com/mainlert/ui/viewmodels/DashboardViewModel.kt:182-203)
- Now reads `EXTRA_IS_USING_CACHED_THRESHOLDS` from broadcast
- Updates `_isUsingCachedThresholds` state

### 5. DashboardScreen UI Indicator

**New UI Component**: Offline Mode Badge

- Shows when `isUsingCachedThresholds == true`
- Appears in header next to sync status
- Design:
  - Tertiary color background (subtle)
  - WifiOff icon + "Offline" text
  - Only visible when monitoring with cached thresholds

**State Collection**: Added `val isUsingCachedThresholds by dashboardViewModel.isUsingCachedThresholds.collectAsState()`

## Behavior Changes

### Before Implementation

| Scenario | Monitoring Start | Thresholds Source | Mapping Source |
|----------|-----------------|-------------------|----------------|
| Online | ✅ Success | RemoteConfig | Firebase |
| Offline | ❌ Failed | N/A | N/A |

### After Implementation

| Scenario | Monitoring Start | Thresholds Source | Mapping Source |
|----------|-----------------|-------------------|----------------|
| Online (first time) | ✅ Success | RemoteConfig | Firebase + Local DB |
| Online (subsequent) | ✅ Success | RemoteConfig (cached) | Firebase + Local DB |
| Offline (with cache) | ✅ Success | ThresholdCache | Local DB only |
| Offline (no cache) | ✅ Success* | Default values | Local DB only |

*First boot without ever being online will use safe default thresholds.

## Data Flow

### Online Flow
```
1. AccelerometerService.start()
2. loadFirebaseData() - isOnline = true
3. Fetch thresholds from RemoteConfig
4. Save thresholds to ThresholdCache
5. Load mapping from local DB (or create if needed)
6. Start monitoring
7. Broadcast includes: isUsingCachedThresholds = false
```

### Offline Flow
```
1. AccelerometerService.start()
2. loadFirebaseData() - isOnline = false
3. Load thresholds from ThresholdCache (or use defaults)
4. Load mapping from local DB (must exist from previous online sync)
5. Start monitoring
6. Broadcast includes: isUsingCachedThresholds = true
7. UI shows "Offline" badge
```

## Real-time Firebase Listeners (Already Implemented)

The DashboardViewModel already includes real-time listener infrastructure:

- `setupRealTimeListenersForVehicle(vehicleId)`: Observes all mappings for a vehicle
- `setupRealTimeListenerForMapping(vehicleId, serviceId)`: Observes specific mapping
- `observeMappingsForVehicle()`: Flow from repository using Firestore snapshot listeners
- `clearRealTimeListeners()`: Cleanup when vehicle changes

**Note**: These listeners update UI state but do not write to local database. They provide real-time updates when online but don't affect offline operation.

## Testing Checklist

### Unit Tests
- [ ] `ThresholdCache` save/retrieve operations
- [ ] `ThresholdCache` default value fallback
- [ ] `ThresholdCache` clear functionality

### Integration Tests
- [ ] Service starts offline with cached thresholds
- [ ] Service starts offline with no cache (uses defaults)
- [ ] Thresholds are saved to cache when online
- [ ] Mapping loads from local DB when offline
- [ ] Broadcast includes correct `isUsingCachedThresholds` flag
- [ ] UI shows offline badge when flag is true
- [ ] UI hides offline badge when flag is false

### Manual Testing
1. **First Boot (Online)**:
   - Start app with internet
   - Verify monitoring starts
   - Check logs: "Loading RemoteConfig thresholds"
   - Check logs: "Thresholds saved to local cache"

2. **Second Boot (Offline)**:
   - Stop app and disable internet
   - Start monitoring
   - Verify monitoring starts successfully
   - Check logs: "No internet connection - loading thresholds from local cache"
   - Check logs: "Using cached thresholds"
   - Verify Dashboard shows "Offline" badge

3. **Threshold Persistence**:
   - While online, note threshold values in logs
   - Stop app, clear app data (simulate fresh install)
   - Start app offline (no internet)
   - Verify defaults are used (3.0, 0.5)
   - Start app online
   - Verify new thresholds are fetched and cached
   - Stop app, go offline
   - Start app again
   - Verify cached thresholds are loaded

4. **Mapping Offline Operation**:
   - While online, create vehicle-service mapping
   - Stop app, go offline
   - Start monitoring
   - Verify mapping is loaded from local DB
   - Verify monitoring starts successfully

## Migration Notes

### No Database Schema Changes
This implementation does not require any database migrations. All changes are additive.

### SharedPreferences
- Created new preference file: `mainlert_thresholds`
- Keys: `crash_threshold`, `min_threshold`, `update_interval`, `last_update_time`

### Backward Compatibility
- Old app versions will not have cached thresholds → uses safe defaults
- No breaking changes to existing APIs
- Broadcast receivers should handle missing extra gracefully (defaults to false)

## Performance Considerations

- **Storage**: SharedPreferences uses ~20 bytes per install (negligible)
- **Startup Time**: Offline startup is faster (no network calls)
- **Memory**: Added one boolean flag (`isUsingCachedThresholds`) - negligible
- **Battery**: No impact - same sensor usage patterns

## Security Considerations

- Thresholds are not sensitive data (safe to store locally)
- No encryption needed for SharedPreferences
- Default values are safe for all vehicle types

## Known Limitations

1. **First Boot Offline**: If user never connects to internet, monitoring uses default thresholds (3.0g crash, 0.5g min). This is safe but may not be optimal for their specific vehicle.

2. **Threshold Staleness**: Cached thresholds may become outdated if RemoteConfig changes while user is offline. They'll get updated on next online connection.

3. **Mapping Requirement**: Offline monitoring requires that a vehicle-service mapping already exists in local DB from a previous online session. If no mapping exists, monitoring will fail (but not due to internet dependency).

4. **No Offline Firebase Writes**: While monitoring works offline, readings are still only synced to Firebase when online. This is by design.

## Future Enhancements

1. **Threshold Cache Invalidation**: Add TTL to cached thresholds (e.g., 30 days) to force refresh periodically
2. **User-Configurable Thresholds**: Allow users to override cached thresholds in app settings
3. **Offline Mapping Creation**: Allow creating new mappings while offline (queue for sync)
4. **Cache Diagnostics**: Show cache age and last sync time in UI (debug mode)
5. **Graceful Degradation**: If cached thresholds are very old, show warning to user

## Related Documents

- [`offline-handling-analysis.md`](offline-handling-analysis.md) - Original investigation and analysis
- [`implementation-plan.md`](implementation-plan.md) - Overall implementation roadmap
- [`sync-issue-analysis.md`](sync-issue-analysis.md) - Previous sync system analysis

## Implementation Summary

**Files Modified**:
1. `app/src/main/java/com/mainlert/services/ThresholdCache.kt` (NEW)
2. `app/src/main/java/com/mainlert/services/AccelerometerService.kt`
3. `app/src/main/java/com/mainlert/ui/viewmodels/DashboardViewModel.kt`
4. `app/src/main/java/com/mainlert/ui/screens/DashboardScreen.kt`

**Lines Added**: ~150
**Lines Modified**: ~50
**Complexity**: Low-Medium
**Risk**: Low (backward compatible, safe defaults)

## Verification Steps

1. Build and run the app
2. Connect to internet, start monitoring, verify logs show RemoteConfig fetch
3. Disable internet, restart monitoring, verify logs show cache usage
4. Check Dashboard for "Offline" badge when using cached thresholds
5. Re-enable internet, verify thresholds are refreshed and cache updated

---

**Last Updated**: 2025-03-07
**Status**: ✅ Implementation Complete
