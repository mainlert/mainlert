# Plan: Consolidate Monitoring Modes to Vehicle-Centric Approach

## Problem Statement

The DashboardViewModel has three different methods to start monitoring:
1. `startMonitoringService()` - legacy, shows vehicle selection
2. `startMonitoringForService(serviceId, vehicleId)` - per-service start
3. `startMonitoringForVehicle(vehicleId)` - per-vehicle start (correct)

This causes architectural inconsistencies with the VehicleServiceMapping design where each vehicle should have independent readings for all its services.

## Root Issues

1. **Wrong Granularity**: `startMonitoringForService()` starts monitoring for a single service only, violating the vehicle-centric architecture
2. **Incomplete State Setup**: `startMonitoringForService()` doesn't ensure all VehicleServiceMappings exist for the vehicle
3. **State Inconsistency**: Different methods handle `monitoredVehicleId`, `currentServiceId`, and `_isMonitoring` differently
4. **User Confusion**: UI elements behave differently (START button vs service row clicks)

## Solution: Unify on Vehicle-Centric Monitoring

### Guiding Principle
**Monitoring is always for a complete vehicle, not individual services.** When a vehicle is monitored, ALL its services accumulate readings independently through VehicleServiceMapping.

### Changes Required

#### 1. DashboardViewModel Changes

**Remove or deprecate `startMonitoringForService()`:**
- This method should NOT be used to start monitoring
- If kept for backward compatibility, it should delegate to `startMonitoringForVehicle()` instead
- Remove the ability to start monitoring for a single service

**Ensure `startMonitoringService()` always delegates properly:**
- It already delegates to `startMonitoringForVehicle()` via vehicle selection
- This is correct and should be maintained

**Keep `startMonitoringForVehicle()` as the single source of truth:**
- This is the only method that should set `_isMonitoring = true`
- It already ensures all mappings exist and starts monitoring for all services
- No changes needed to its core logic

**Update `stopMonitoringService()`:**
- Already stops monitoring for the entire vehicle
- Should remain as is

#### 2. UI Changes (DashboardScreen.kt)

**Service Row Clicks:**
- **Current behavior**: Clicking a service row calls `startMonitoringForService(service.id)`
- **New behavior**: Should NOT start monitoring. Instead:
  - If not monitoring: Show a prompt "Select a vehicle first" or navigate to vehicle selection
  - If monitoring: Display service details (maybe navigate to service details screen)
  - OR: Clicking a service row could select that service for display in the "Service Readings" section

**START Button (InactivityOverlay):**
- Already calls `startMonitoringService()` which is correct
- No change needed

**Service Row Card Appearance:**
- Currently shows "ACTIVE" badge when monitoring
- Should show which service is currently selected/displayed, not whether it's monitoring
- Monitoring status should be indicated at the vehicle level, not per-service

#### 3. ServiceDetailsScreen Changes

**Remove direct monitoring control:**
- ServiceDetailsScreen currently has its own Start/Stop buttons calling `startMonitoringService()`
- This creates confusion - monitoring should be controlled from the dashboard
- Options:
  - Remove the buttons entirely (read-only view)
  - Or delegate to vehicle-level monitoring (navigate back to dashboard)
  - Or show "This service is monitored as part of vehicle X" with a button to go to dashboard

## Implementation Steps

### Phase 1: Code Changes (DashboardViewModel)

1. **Modify `startMonitoringForService()`** to prevent direct service-level monitoring:
   ```kotlin
   fun startMonitoringForService(serviceId: String, vehicleId: String = "") {
       // DEPRECATED: This method should not be used
       // Log warning and delegate to startMonitoringForVehicle
       Log.w("DashboardViewModel", "startMonitoringForService() is deprecated. Use vehicle-level monitoring.")
       
       if (vehicleId.isNotEmpty()) {
           // Delegate to vehicle-level monitoring
           startMonitoringForVehicle(vehicleId)
       } else {
           // Cannot start without vehicle context
           _errorMessage.value = "Please select a vehicle first"
       }
   }
   ```

2. **Add validation** to prevent inconsistent state:
   - Ensure `monitoredVehicleId` is always set when `_isMonitoring` is true
   - Clear `currentServiceId` when stopping (it's not needed for vehicle-level monitoring)

3. **Optional**: Remove `currentServiceId` entirely if not used elsewhere (check for references)

### Phase 2: UI Changes (DashboardScreen.kt)

1. **ServiceRowCard onClick handler** (line 390-393):
   ```kotlin
   onClick = {
       onDashboardInteraction()
       // OLD: dashboardViewModel.startMonitoringForService(service.id)
       // NEW: If monitoring, just select this service for display
       // If not monitoring, show message or do nothing
       if (isMonitoring) {
           // Select this service to display in readings section
           // (Need to add a method to set currentServiceIndex or selectedService)
       } else {
           // Prompt user to start monitoring from the main button
           _errorMessage.value = "Please start monitoring from the main button first"
       }
   }
   ```

2. **ServiceRowCard visual indicators**:
   - Remove "ACTIVE" badge (line 1034-1040) - monitoring is vehicle-level
   - Show selection indicator if this service is currently displayed
   - Disable clicks when not monitoring (or change behavior as above)

3. **ServiceDetailsScreen**:
   - Remove Start/Stop buttons (lines 177-203)
   - Show message: "Monitoring is controlled from the dashboard"
   - Or make it read-only with navigation back to dashboard

### Phase 3: Testing & Validation

1. **Test scenarios**:
   - Single vehicle: Click START → all services should monitor
   - Multiple vehicles: Select vehicle → all services should monitor
   - Service row clicks: Should not start/stop monitoring
   - Stop monitoring: Should stop all services for the vehicle

2. **Verify state consistency**:
   - `_isMonitoring` reflects vehicle-level monitoring
   - `monitoredVehicleId` is set correctly
   - All VehicleServiceMappings for the vehicle have `isMonitoring = true`
   - Readings update for all services independently

3. **Edge cases**:
   - What happens if user clicks service row before starting? (show prompt)
   - What if vehicle has no services? (show error)
   - What if mappings fail to create? (error handling)

## Benefits

1. **Clear Architecture**: Monitoring is always vehicle-centric, matching the VehicleServiceMapping design
2. **Consistent State**: Single source of truth for monitoring state
3. **Better UX**: Users understand that a vehicle is being monitored, not individual services
4. **Easier Maintenance**: One code path to maintain, not three

## Migration Path

If backward compatibility is needed:

1. Keep `startMonitoringForService()` but make it a thin wrapper:
   ```kotlin
   fun startMonitoringForService(serviceId: String, vehicleId: String = "") {
       if (vehicleId.isEmpty()) {
           _errorMessage.value = "Vehicle required for monitoring"
           return
       }
       startMonitoringForVehicle(vehicleId)
   }
   ```

2. Add deprecation warning in logs

3. Update all UI to use vehicle-level approach

4. Remove the method entirely in a future version

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Breaking existing UI flows | Test all screens thoroughly; provide clear user messaging |
| ServiceDetailsScreen loses functionality | Make it read-only or navigate to dashboard for controls |
| Users expect per-service control | Educate through UI: "All services on a vehicle are monitored together" |
| State not properly cleared on stop | Verify `stopMonitoringService()` clears all state correctly |

## Success Criteria

- Only `startMonitoringForVehicle()` sets `_isMonitoring = true`
- All services on a monitored vehicle have active mappings
- Service row clicks do not start/stop monitoring
- UI clearly indicates which vehicle is being monitored
- No direct references to `startMonitoringForService()` in UI code
