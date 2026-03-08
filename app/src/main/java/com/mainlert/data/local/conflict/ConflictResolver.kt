package com.mainlert.data.local.conflict

import com.mainlert.data.local.entities.ServiceEntity
import com.mainlert.data.local.entities.ServiceVariantEntity
import com.mainlert.data.local.entities.VehicleEntity
import com.mainlert.data.local.entities.VehicleServiceMappingEntity
import com.mainlert.data.local.entities.toVehicleServiceMappingEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Conflict resolver for hierarchical sync with dual-field comparison.
 * Uses both timestamps and totalMovement to determine which data is newer.
 */
@Singleton
class ConflictResolver @Inject constructor() {
    
    private val _conflictMetrics = MutableStateFlow(ConflictMetrics())
    val conflictMetrics: StateFlow<ConflictMetrics> = _conflictMetrics
    
   /**
    * Resolve conflicts for VehicleEntity using timestamp comparison.
    */
   fun resolveConflict(
       deviceData: VehicleEntity,
       firebaseData: VehicleEntity
   ): VehicleEntity {
       // For vehicles, use lastSyncTime to determine winner
       return if (deviceData.lastSyncTime >= firebaseData.lastSyncTime) {
           deviceData.copy(lastSyncTime = System.currentTimeMillis())
       } else {
           firebaseData.copy(lastSyncTime = System.currentTimeMillis())
       }
   }
   
   /**
    * Resolve conflicts for ServiceEntity using timestamp comparison.
    */
   fun resolveConflict(
       deviceData: ServiceEntity,
       firebaseData: ServiceEntity
   ): ServiceEntity {
       // For services, use lastSyncTime to determine winner
       return if (deviceData.lastSyncTime >= firebaseData.lastSyncTime) {
           deviceData.copy(lastSyncTime = System.currentTimeMillis())
       } else {
           firebaseData.copy(lastSyncTime = System.currentTimeMillis())
       }
   }
   
   /**
    * Resolve conflicts for ServiceVariantEntity using timestamp comparison.
    */
   fun resolveConflict(
       deviceData: ServiceVariantEntity,
       firebaseData: ServiceVariantEntity
   ): ServiceVariantEntity {
       // For service variants, use lastSyncTime to determine winner
       return if (deviceData.lastSyncTime >= firebaseData.lastSyncTime) {
           deviceData.copy(lastSyncTime = System.currentTimeMillis())
       } else {
           firebaseData.copy(lastSyncTime = System.currentTimeMillis())
       }
   }
   
   /**
    * Resolve conflicts between device and Firebase data using dual-field comparison.
    *
    * @param deviceData Data from local device
    * @param firebaseData Data from Firebase
    * @return Resolved data with updated timestamps
    */
   fun resolveConflict(
       deviceData: VehicleServiceMappingEntity,
       firebaseData: VehicleServiceMappingEntity
   ): VehicleServiceMappingEntity {
        val startTime = System.currentTimeMillis()
        
        val resolvedData = when {
            // Case 1: Device has significantly newer timestamp (more than 5 minutes)
            deviceData.localLastUpdated > firebaseData.firebaseLastUpdated + TimeUnit.MINUTES.toMillis(5) -> {
                if (deviceData.totalMovement >= firebaseData.totalMovement) {
                    // Device is newer and has more or equal movement - device wins
                    deviceData.copy(firebaseLastUpdated = System.currentTimeMillis())
                } else {
                    // Edge case: newer timestamp but lower movement
                    handleEdgeCase(deviceData, firebaseData)
                }
            }
            
            // Case 2: Firebase has significantly newer timestamp
            firebaseData.firebaseLastUpdated > deviceData.localLastUpdated + TimeUnit.MINUTES.toMillis(5) -> {
                if (firebaseData.totalMovement >= deviceData.totalMovement) {
                    // Firebase is newer and has more or equal movement - Firebase wins
                    firebaseData.copy(localLastUpdated = System.currentTimeMillis())
                } else {
                    // Edge case: newer timestamp but lower movement
                    handleEdgeCase(firebaseData, deviceData)
                }
            }
            
            // Case 3: Similar timestamps, compare totalMovement    
            kotlin.math.abs(deviceData.localLastUpdated - firebaseData.firebaseLastUpdated) <= TimeUnit.MINUTES.toMillis(5) -> {
                if (deviceData.totalMovement > firebaseData.totalMovement) {
                    // Device has more movement - device wins
                    deviceData.copy(firebaseLastUpdated = System.currentTimeMillis())
                } else {
                    // Firebase has more or equal movement - Firebase wins
                    firebaseData.copy(localLastUpdated = System.currentTimeMillis())
                }
            }
            
            // Case 4: Edge case - handle based on business logic
            else -> {
                handleEdgeCase(deviceData, firebaseData)
            }
        }
        
        // Update metrics
        updateMetrics(startTime)
        
        return resolvedData
    }
    
    /**
     * Handle edge cases in conflict resolution.
     * 
     * @param primaryData Primary data (usually the one with newer timestamp)
     * @param secondaryData Secondary data
     * @param reason Reason for edge case handling
     * @return Resolved data
     */
    private fun handleEdgeCase(
        primaryData: VehicleServiceMappingEntity,
        secondaryData: VehicleServiceMappingEntity
    ): VehicleServiceMappingEntity {
        // Business logic for edge cases:
        // If timestamps are close but movement doesn't align, prefer higher movement
        return if (primaryData.totalMovement >= secondaryData.totalMovement) {
            primaryData.copy(firebaseLastUpdated = System.currentTimeMillis())
        } else {
            secondaryData.copy(localLastUpdated = System.currentTimeMillis())
        }
    }
    
    /**
     * Update conflict resolution metrics.
     */
    private fun updateMetrics(startTime: Long) {
        val endTime = System.currentTimeMillis()
        val operationTime = endTime - startTime
        
        val currentMetrics = _conflictMetrics.value
        val updatedMetrics = currentMetrics.copy(
            totalConflictsResolved = currentMetrics.totalConflictsResolved + 1,
            totalResolutionTime = currentMetrics.totalResolutionTime + operationTime,
            lastResolutionTime = operationTime,
            lastResolutionTimestamp = endTime
        )
        
        _conflictMetrics.value = updatedMetrics
    }
    
    /**
     * Get current conflict resolution metrics.
     */
    fun getMetrics(): ConflictMetrics {
        return _conflictMetrics.value
    }
    
    /**
     * Reset conflict metrics.
     */
    fun resetMetrics() {
        _conflictMetrics.value = ConflictMetrics()
    }
}

/**
 * Metrics for conflict resolution operations.
 */
data class ConflictMetrics(
    val totalConflictsResolved: Int = 0,
    val totalResolutionTime: Long = 0L,
    val lastResolutionTime: Long = 0L,
    val lastResolutionTimestamp: Long = 0L
) {
    val averageResolutionTime: Long
        get() = if (totalConflictsResolved > 0) totalResolutionTime / totalConflictsResolved else 0L
}