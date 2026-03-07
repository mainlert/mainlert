# BootReceiver Enhancement Plan

## Current Behavior
- BootReceiver restarts monitoring after device reboot by finding the first active mapping and starting the AccelerometerService with that specific serviceId and vehicleId
- This is a simple restore operation with no user interaction

## New Required Behavior
- BootReceiver should detect if the device is in a moving vehicle after boot
- If vehicle movement is detected, prompt the user to select a vehicle to monitor
- Provide an enable/disable toggle for this auto-start functionality (disabled by default)
- The toggle button should be placed at the bottom of DashboardScreen.kt

## Implementation Approach

### 1. Update BootReceiver.kt

**New Logic:**
1. On BOOT_COMPLETED, start a lightweight detection service/activity
2. The detection mechanism should:
   - Start the AccelerometerService in "detection mode" (not full monitoring)
   - Monitor accelerometer data for a short period (e.g., 10-30 seconds)
   - Use the existing vehicle movement detection logic (thresholds from RemoteConfig)
   - If vehicle movement is detected, show a notification prompting user to select a vehicle
   - If no movement detected after timeout, stop silently

**Key Changes:**
- Remove the old "find active mapping and restart" logic
- Add detection mode that uses AccelerometerService's movement detection without creating mappings
- Create a notification that opens the DashboardScreen when tapped
- The notification should allow the user to select a vehicle and start monitoring

### 2. Add BootReceiver Setting in DashboardViewModel

**New State:**
```kotlin
private val _bootReceiverEnabled = MutableStateFlow(false) // Disabled by default
val bootReceiverEnabled: StateFlow<Boolean> = _bootReceiverEnabled.asStateFlow()
```

**Methods:**
```kotlin
fun setBootReceiverEnabled(enabled: Boolean) {
    // Save to SharedPreferences or DataStore
    // Update BootReceiver's manifest or dynamic registration
}
```

**Persistence:**
- Use SharedPreferences or DataStore to store the setting
- Load on app startup

### 3. Update DashboardScreen.kt

**Add Toggle Button:**
- Location: Bottom of the screen, below the existing "Accelerometer Service" card or in a new card
- Button should show current state (Enabled/Disabled)
- Clicking toggles the state and calls `dashboardViewModel.setBootReceiverEnabled()`
- Visual feedback: Show different colors/text for enabled vs disabled

**UI Example:**
```kotlin
Card(
    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Auto-Start on Boot", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Automatically detect vehicle and start monitoring",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isBootReceiverEnabled,
                onCheckedChange = { dashboardViewModel.setBootReceiverEnabled(it) }
            )
        }
    }
}
```

### 4. Dynamic BootReceiver Registration (Optional)

If we want to completely disable the BootReceiver when the setting is off:
- Instead of manifest-registered receiver, use dynamic registration
- In Application class or MainActivity, register/unregister BootReceiver based on the setting
- Or keep manifest registration but have the receiver check the setting and exit early if disabled

**Simpler approach:** Keep manifest registration, but have BootReceiver check the setting:
```kotlin
val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
val enabled = prefs.getBoolean("boot_receiver_enabled", false)
if (!enabled) return
```

### 5. Detection Service Design

**Option A: Use Existing AccelerometerService**
- Start AccelerometerService with a special "detection mode" flag
- Service runs for 30 seconds, checks for vehicle movement
- If movement detected, show notification and stop
- If no movement, stop silently

**Option B: Create New DetectionService**
- Lightweight service that only checks for movement
- Less code reuse but cleaner separation

**Recommended: Option A** - modify AccelerometerService to support detection mode:
- Add `EXTRA_DETECTION_MODE` to startService intent
- If detection mode:
  - Don't create mappings
  - Don't save to Firebase
  - Just monitor movement for N seconds
  - If vehicle movement detected, show notification: "Vehicle movement detected. Select a vehicle to start monitoring."
  - Notification opens DashboardScreen with vehicle selection

### 6. Notification Flow

**Notification Content:**
- Title: "Vehicle Detected"
- Text: "Your device appears to be in a moving vehicle. Select a vehicle to start monitoring."
- Action: Tap to open app and show vehicle selection dialog
- Auto-cancel after tap

**Implementation:**
- In BootReceiver, after detection, create notification
- Use PendingIntent to open DashboardScreen with a flag to show vehicle selection immediately
- DashboardScreen observes the flag (via ViewModel or Intent extra) and triggers `showVehicleSelectionDialog`

### 7. Settings Persistence

**Using SharedPreferences:**
```kotlin
// In DashboardViewModel
private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

fun setBootReceiverEnabled(enabled: Boolean) {
    prefs.edit().putBoolean("boot_receiver_enabled", enabled).apply()
    _bootReceiverEnabled.value = enabled
}

init {
    _bootReceiverEnabled.value = prefs.getBoolean("boot_receiver_enabled", false)
}
```

**Note:** Need to pass Context to ViewModel (already have @ApplicationContext)

### 8. Testing Considerations

- Test boot scenario with movement detection
- Test notification appearance and tap action
- Test toggle persistence across app restarts
- Test that disabled setting prevents detection
- Test edge cases: no network, no vehicles assigned, multiple vehicles

## Files to Modify

1. `BootReceiver.kt` - Complete rewrite of detection logic
2. `DashboardViewModel.kt` - Add setting state and persistence
3. `DashboardScreen.kt` - Add toggle button UI
4. `AccelerometerService.kt` - Add detection mode support
5. `AndroidManifest.xml` - Possibly change receiver registration (if using dynamic)

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Battery drain from detection mode | Limit detection to 30 seconds max; use efficient sensor sampling |
| False positives (detecting movement when not in vehicle) | Use existing thresholds; user can dismiss notification |
| Complexity in AccelerometerService | Add clear mode flag; early returns for detection path |
| Setting not persisted | Use reliable SharedPreferences; test across app kills |

## Success Criteria

- BootReceiver detects vehicle movement after boot (when enabled)
- User receives notification to select vehicle
- Toggle button in DashboardScreen controls the feature
- Setting persists across app restarts
- No monitoring starts without user explicit selection (except detection-triggered)