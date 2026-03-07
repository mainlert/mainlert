# Monitoring Consolidation Implementation Summary

## Changes Implemented

### 1. DashboardViewModel.kt

**Deprecated `startMonitoringForService()`:**
- Replaced implementation with a warning and delegation to `startMonitoringForVehicle()`
- Ensures backward compatibility while enforcing vehicle-centric monitoring

**Removed `currentServiceId` tracking:**
- Removed the `currentServiceId` variable (no longer needed for vehicle-centric monitoring)
- Updated `resetServiceData()` to reset ALL services for the monitored vehicle
- Added `resetAllMappingsForVehicle()` helper method

**Key changes:**
- Line 109: Removed `currentServiceId` variable
- Line 314: Removed auto-selection of first service
- Line 511-512: Removed clearing of `currentServiceId` on stop
- Line 537-556: Rewrote `resetServiceData()` to reset all vehicle services
- Line 1144-1148: Deprecated `startMonitoringForService()` with warning and delegation

### 2. DashboardScreen.kt

**Added service selection state:**
- Line 105: Added `selectedServiceId` to track which service is displayed

**Updated service row click behavior:**
- Line 381: Added `isSelected` flag for visual feedback
- Line 392-397: Changed onClick to:
  - If monitoring: select the service for display
  - If not monitoring: start monitoring via main button

**Updated ServiceRowCard:**
- Line 989: Added `isSelected` parameter
- Line 1009: Show selection indicator (checkmark) when selected
- Line 1010: Updated card colors to show selection state

**Updated Service Readings section:**
- Line 418-445: Changed to use selected service or auto-rotate
- Shows "Selected Service" label when a service is manually selected
- Falls back to auto-rotation when no service is selected

### 3. ServiceDetailsScreen.kt

**Removed duplicate monitoring controls:**
- Lines 172-203 replaced with informational card
- New UI explains vehicle-level monitoring concept
- Added "Go to Dashboard" button for navigation

## Architecture Alignment

The changes enforce **vehicle-centric monitoring**:

✅ **Single entry point**: `startMonitoringForVehicle()` is now the only way to start monitoring
✅ **All services monitored**: When a vehicle is monitored, ALL its services accumulate readings
✅ **Consistent state**: `_isMonitoring` and `monitoredVehicleId` are the only monitoring state
✅ **Clear UX**: Users understand that monitoring is per-vehicle, not per-service
✅ **No duplicate mappings**: `ensureAllMappingsExist()` guarantees all services have mappings

## Migration Path

- `startMonitoringForService()` still exists but logs a deprecation warning
- It delegates to `startMonitoringForVehicle()` for backward compatibility
- UI no longer calls this method directly
- Can be removed entirely in a future version

## Testing Checklist

- [ ] START button shows vehicle selection (or auto-starts for single vehicle)
- [ ] Selecting a vehicle starts monitoring for ALL its services
- [ ] Service row clicks do NOT start/stop monitoring when monitoring is active
- [ ] Service row clicks SELECT a service to display in the readings section
- [ ] Selected service shows checkmark and highlighted card
- [ ] Service Readings section shows the selected service or auto-rotates
- [ ] STOP button stops monitoring for the entire vehicle
- [ ] Reset Service button resets ALL services for the vehicle
- [ ] ServiceDetailsScreen shows informational message and dashboard button
- [ ] No direct references to `startMonitoringForService()` in UI code

## Benefits

1. **Architectural consistency** with VehicleServiceMapping design
2. **Simpler mental model** for users: "monitor a vehicle" not "monitor a service"
3. **Reduced code complexity** - one monitoring flow instead of three
4. **Prevents state bugs** from inconsistent monitoring states
5. **Better UX** - clear relationship between vehicle and its services

## Next Steps (Optional)

- Remove `startMonitoringForService()` entirely after testing
- Consider removing `currentServiceId` from AccelerometerService if not needed (it IS needed for processing)
- Add analytics to track how users interact with the new service selection feature
- Update user documentation to reflect vehicle-centric monitoring