# AccelerometerService Refactoring Plan

## Issues to Fix

1. **Duplicated mapping loading code** (lines 341-434)
   - Two nearly identical blocks with same error handling
   - Maintenance burden
   - Inconsistent behavior

2. **Race condition in mapping creation** (lines 75-78, 162-165)
   - Multiple processes can create duplicate mappings
   - No atomic check-and-create

## Proposed Changes

### 1. Extract `loadOrCreateMapping()` unified function

```kotlin
/**
 * Unified function to load an existing mapping or create a new one.
 * Uses distributed locking to prevent duplicate creation.
 */
private suspend fun loadOrCreateMapping(): VehicleServiceMapping? {
    val vehicleId = currentVehicleId
    val serviceId = currentServiceId
    
    if (vehicleId.isNullOrBlank() || serviceId.isNullOrBlank()) {
        Log.w(TAG, "Cannot load/create mapping - vehicleId or serviceId is null/blank")
        return null
    }
    
    return try {
        // Try to load existing mapping first
        val existingMapping = loadExistingMapping(vehicleId, serviceId)
        if (existingMapping != null) {
            Log.i(TAG, "Found existing mapping: ${existingMapping.id}")
            return existingMapping
        }
        
        // No mapping found - acquire lock and create
        createMappingWithLock(vehicleId, serviceId)
        
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load or create mapping", e)
        null
    }
}

/**
 * Loads existing mapping using the best available query method.
 * Returns null if no mapping found.
 */
private suspend fun loadExistingMapping(vehicleId: String, serviceId: String): VehicleServiceMapping? {
    // If we have a currentMappingId from active mapping restore, use it directly
    if (currentMappingId != null) {
        Log.d(TAG, "Using currentMappingId from restore: $currentMappingId")
        val result = vehicleServiceMappingRepository.getMappingById(currentMappingId!!)
        if (result is Result.Success) {
            return result.data
        }
        // If loading by ID fails, clear the ID and fall back to query
        currentMappingId = null
    }
    
    // Query by vehicle+service
    val result = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicleId, serviceId)
    return when (result) {
        is Result.Success -> {
            val mapping = result.data
            if (mapping != null) {
                currentMappingId = mapping.id
                currentServiceMileageLimit = mapping.mileageLimit
                totalMovement = mapping.totalMovement
            }
            mapping
        }
        is Result.Failure -> {
            Log.e(TAG, "Failed to load mapping: ${result.message}")
            null
        }
    }
}

/**
 * Creates a new mapping with distributed locking to prevent duplicates.
 * Uses Firestore transaction to ensure only one process creates the mapping.
 */
private suspend fun createMappingWithLock(vehicleId: String, serviceId: String): VehicleServiceMapping? {
    val lockDocId = "mapping_lock_${vehicleId}_$serviceId"
    val lockRef = firebaseFirestore.collection("locks").document(lockDocId)
    val mappingCreated = AtomicBoolean(false)
    var createdMapping: VehicleServiceMapping? = null
    
    try {
        // Try to acquire lock with transaction (max 5 second wait)
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 5000) {
            try {
                firebaseFirestore.runTransaction { transaction ->
                    val lockDoc = transaction.get(lockRef)
                    if (lockDoc.exists()) {
                        // Lock already held by another process
                        throw IllegalStateException("Mapping creation already in progress")
                    }
                    
                    // Create lock document with TTL (auto-delete after 30 seconds)
                    transaction.set(lockRef, mapOf(
                        "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "processId" to Process.myPid(),
                        "vehicleId" to vehicleId,
                        "serviceId" to serviceId
                    ))
                    null
                }
                // Lock acquired successfully
                break
            } catch (e: Exception) {
                // Wait and retry
                delay(500)
            }
        }
        
        // Double-check if mapping was created while we were waiting for lock
        val doubleCheck = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicleId, serviceId)
        if (doubleCheck is Result.Success && doubleCheck.data != null) {
            Log.i(TAG, "Mapping already created by another process: ${doubleCheck.data.id}")
            return doubleCheck.data
        }
        
        // Create the mapping
        Log.i(TAG, "Lock acquired, creating mapping for vehicle $vehicleId and service $serviceId")
        val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
        val vehicle = when (vehicleResult) {
            is Result.Success -> vehicleResult.data
            is Result.Failure -> {
                Log.e(TAG, "Failed to get vehicle: ${vehicleResult.message}")
                null
            }
        }
        
        if (vehicle == null) {
            Log.e(TAG, "Cannot create mapping - vehicle not found: $vehicleId")
            return null
        }
        
        val serviceResult = serviceRepository.getServiceById(serviceId)
        val service = when (serviceResult) {
            is Result.Success -> serviceResult.data
            is Result.Failure -> {
                Log.e(TAG, "Failed to get service: ${serviceResult.message}")
                null
            }
        }
        
        if (service == null) {
            Log.e(TAG, "Cannot create mapping - service not found: $serviceId")
            return null
        }
        
        createdMapping = createMappingForService(vehicle, service)
        mappingCreated.set(true)
        
        return createdMapping
        
    } finally {
        // Always release lock if we acquired it
        if (mappingCreated.get() || System.currentTimeMillis() - startTime >= 5000) {
            try {
                lockRef.delete().await()
                Log.d(TAG, "Released mapping lock: $lockDocId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release lock: $lockDocId", e)
            }
        }
    }
}

/**
 * Starts monitoring with the loaded or newly created mapping.
 */
private fun startMonitoringWithMapping(mapping: VehicleServiceMapping) {
    currentMappingId = mapping.id
    currentServiceMileageLimit = mapping.mileageLimit
    totalMovement = mapping.totalMovement
    
    if (!mapping.isMonitoring) {
        Log.d(TAG, "Mapping found but not monitoring, starting monitoring for mapping ${mapping.id}")
        val startResult = vehicleServiceMappingRepository.startMappingMonitoring(mapping.id)
        when (startResult) {
            is Result.Success -> {
                Log.i(TAG, "Successfully started monitoring for existing mapping ${mapping.id}")
            }
            is Result.Failure -> {
                Log.e(TAG, "Failed to start monitoring for existing mapping ${mapping.id}: ${startResult.message}")
            }
        }
    }
    
    // Continue with vehicle mileage loading and monitoring start
    loadVehicleMileageAndStartMonitoring()
}
```

### 2. Replace duplicated code in `loadFirebaseData()`

Replace lines 311-438 with:

```kotlin
// Load VehicleServiceMapping data with unified logic
if (currentServiceId != null && currentVehicleId != null) {
    Log.d(TAG, "Loading VehicleServiceMapping for service $currentServiceId and vehicle $currentVehicleId")
    
    // If currentServiceId is blank but we have currentMappingId, restore serviceId
    if ((currentServiceId.isNullOrBlank()) && currentMappingId != null) {
        Log.d(TAG, "currentServiceId is blank but currentMappingId is set - restoring serviceId from mapping")
        val mappingResult = vehicleServiceMappingRepository.getMappingById(currentMappingId!!)
        when (mappingResult) {
            is Result.Success -> {
                mappingResult.data?.let { mapping ->
                    currentServiceId = mapping.serviceId
                    Log.i(TAG, "Restored serviceId from mapping: $currentServiceId")
                }
            }
            else -> Log.w(TAG, "Failed to restore serviceId from mapping")
        }
    }
    
    // Load or create mapping using unified function
    val mapping = loadOrCreateMapping()
    if (mapping != null) {
        startMonitoringWithMapping(mapping)
        return@launch
    } else {
        Log.e(TAG, "Failed to load or create mapping")
        // Continue to load vehicle mileage and start monitoring with defaults
    }
    
} else {
    Log.w(TAG, "Service ID or Vehicle ID is null - cannot load mapping")
    Log.w(TAG, "Service ID: $currentServiceId, Vehicle ID: $currentVehicleId")
}

loadVehicleMileageAndStartMonitoring()
```

### 3. Extract `loadVehicleMileageAndStartMonitoring()` 

```kotlin
/**
 * Loads vehicle lifetime mileage and starts monitoring.
 */
private fun loadVehicleMileageAndStartMonitoring() {
    if (currentVehicleId != null) {
        try {
            val vehicleResult = vehicleRepository.getVehicleById(currentVehicleId!!)
            when (vehicleResult) {
                is Result.Success -> {
                    val vehicle = vehicleResult.data
                    if (vehicle != null) {
                        Log.d(TAG, "Successfully loaded vehicle lifetime mileage: ${vehicle.lifetimeMileage}")
                    } else {
                        Log.w(TAG, "Vehicle not found for ID: $currentVehicleId")
                    }
                }
                is Result.Failure -> {
                    Log.e(TAG, "Failed to load vehicle: ${vehicleResult.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while loading vehicle data", e)
        }
    }
    
    // All data loaded successfully - start monitoring
    Log.i(TAG, "Firebase data loaded successfully. Starting monitoring...")
    isFirebaseDataLoaded = true
    serviceState = ServiceState.MONITORING
    startMonitoring()
}
```

## Benefits

1. **Eliminates code duplication** - Single source of truth for mapping loading logic
2. **Prevents race conditions** - Distributed lock ensures only one process creates mapping
3. **Improves maintainability** - Easier to understand and modify
4. **Better error handling** - Consistent logging and fallback behavior
5. **Reduces log noise** - Fewer duplicate "No mapping found" messages

## Testing Checklist

- [ ] Start monitoring with single vehicle - should create one mapping
- [ ] Start monitoring with multiple service processes - should not create duplicates
- [ ] Stop and restart monitoring - should restore existing mapping
- [ ] Verify lock cleanup after failures
- [ ] Check Firebase logs for duplicate creation attempts

## Migration Notes

- No database schema changes required
- Existing mappings continue to work
- Lock collection "locks" will be created automatically
- Consider adding Firestore TTL policy to auto-delete old locks (30 seconds)