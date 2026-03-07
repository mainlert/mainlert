package com.mainlert.data.local.sync

import com.mainlert.data.local.entities.VehicleServiceMappingEntity
import com.mainlert.data.utils.toVehicleServiceMappingEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Conflict resolution engine for hierarchical sync system.
 * Implements dual-field conflict resolution with timestamp and movement comparison.
 */
class ConflictResolver @Inject constructor() {
    
    companion object {
        private const val CONFLICT_RESOLUTION_TOLERANCE_MS = 5 * 60 * 1000L // 5 minutes
    }
    
    /**
     * Resolve conflict between device and Firebase data using dual-field comparison.
     */
    fun resolveConflict(deviceData: VehicleServiceMappingEntity, firebaseData: VehicleServiceMappingEntity): VehicleServiceMappingEntity {
        // Calculate time difference between device and Firebase timestamps
        val timeDiff = kotlin.math.abs(deviceData.localLastUpdated - firebaseData.firebaseLastUpdated)
        
        return when {
            // Case 1: Device has significantly newer timestamp (more than 5 minutes)
            deviceData.localLastUpdated > firebaseData.firebaseLastUpdated + CONFLICT_RESOLUTION_TOLERANCE_MS -> {
                if (deviceData.totalMovement >= firebaseData.totalMovement) {
                    deviceData.copy(firebaseLastUpdated = System.currentTimeMillis())
                } else {
                    handleEdgeCase(deviceData, firebaseData)
                }
            }
            
            // Case 2: Firebase has significantly newer timestamp
            firebaseData.firebaseLastUpdated > deviceData.localLastUpdated + CONFLICT_RESOLUTION_TOLERANCE_MS -> {
                if (firebaseData.totalMovement >= deviceData.totalMovement) {
                    firebaseData.copy(localLastUpdated = System.currentTimeMillis())
                } else {
                    handleEdgeCase(firebaseData, deviceData)
                }
            }
            
            // Case 3: Similar timestamps, compare totalMovement
            timeDiff <= CONFLICT_RESOLUTION_TOLERANCE_MS -> {
                if (deviceData.totalMovement > firebaseData.totalMovement) {
                    deviceData.copy(firebaseLastUpdated = System.currentTimeMillis())
                } else {
                    firebaseData.copy(localLastUpdated = System.currentTimeMillis())
                }
            }
            
            // Case 4: Edge case - handle based on business logic
            else -> {
                handleEdgeCase(deviceData, firebaseData)
            }
        }
    }
    
    /**
     * Handle edge cases in conflict resolution.
     */
    private fun handleEdgeCase(deviceData: VehicleServiceMappingEntity, firebaseData: VehicleServiceMappingEntity): VehicleServiceMappingEntity {
        // Business logic for edge cases: prefer device data if movement is equal or greater
        return if (deviceData.totalMovement >= firebaseData.totalMovement) {
            deviceData.copy(firebaseLastUpdated = System.currentTimeMillis())
        } else {
            firebaseData.copy(localLastUpdated = System.currentTimeMillis())
        }
    }
    
    /**
     * Validate mapping data before sync.
     */
    fun validateMappingData(mapping: VehicleServiceMappingEntity): Boolean {
        return mapping.totalMovement >= 0f &&
               mapping.mileageLimit > 0 &&
               mapping.serviceName.isNotBlank() &&
               mapping.vehicleId.isNotBlank()
    }
}