package com.mainlert.data.repositories

import com.mainlert.data.models.Result
import com.mainlert.data.models.VehicleServiceMapping
import com.mainlert.data.local.sync.SyncState
import com.mainlert.data.local.sync.SyncMetrics
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for VehicleServiceMapping operations.
 * Manages the relationship between vehicles and services with independent readings.
 * Enhanced with hierarchical sync capabilities and dual-field conflict resolution.
 */
interface VehicleServiceMappingRepository {
    /**
     * Gets all vehicle-service mappings.
     */
    suspend fun getMappings(): Result<List<VehicleServiceMapping>>

    /**
     * Gets a mapping by its ID.
     */
    suspend fun getMappingById(mappingId: String): Result<VehicleServiceMapping>

    /**
     * Gets all mappings for a specific vehicle.
     */
    suspend fun getMappingsForVehicle(vehicleId: String): Result<List<VehicleServiceMapping>>

    /**
     * Gets all mappings for a specific service.
     */
    suspend fun getMappingsForService(serviceId: String): Result<List<VehicleServiceMapping>>

    /**
     * Gets the mapping for a specific vehicle and service combination.
     * This is the key method for getting independent readings.
     */
    suspend fun getMappingForVehicleAndService(
        vehicleId: String,
        serviceId: String,
    ): Result<VehicleServiceMapping?>

    /**
     * Gets all mappings for a specific user.
     */
    suspend fun getMappingsForUser(userId: String): Result<List<VehicleServiceMapping>>

    /**
     * Creates a new vehicle-service mapping.
     */
    suspend fun createMapping(mapping: VehicleServiceMapping): Result<VehicleServiceMapping>

    /**
     * Updates an existing mapping.
     */
    suspend fun updateMapping(mapping: VehicleServiceMapping): Result<VehicleServiceMapping>

    /**
     * Deletes a mapping by ID.
     */
    suspend fun deleteMapping(mappingId: String): Result<Unit>

    /**
     * Updates the total movement (reading) for a specific mapping.
     */
    suspend fun updateMappingMovement(
        mappingId: String,
        totalMovement: Float,
    ): Result<Unit>

    /**
     * Saves a movement checkpoint without triggering immediate sync.
     * Used for periodic crash recovery state preservation.
     */
    suspend fun saveMovementCheckpoint(
        mappingId: String,
        totalMovement: Float,
    ): Result<Unit>

    /**
     * Starts monitoring for a specific mapping.
     */
    suspend fun startMappingMonitoring(mappingId: String): Result<Unit>

    /**
     * Stops monitoring for a specific mapping.
     */
    suspend fun stopMappingMonitoring(mappingId: String): Result<Unit>

    /**
     * Resets the readings for a specific mapping.
     */
    suspend fun resetMappingReadings(mappingId: String): Result<Unit>

    /**
     * Gets the currently active mapping for monitoring.
     */
    suspend fun getActiveMapping(): Result<VehicleServiceMapping?>

    /**
     * Gets active mappings for a user.
     */
    suspend fun getActiveMappingsForUser(userId: String): Result<List<VehicleServiceMapping>>

    /**
     * Observes real-time changes to a specific vehicle-service mapping.
     * Returns a Flow that emits updates whenever the mapping changes in Firebase.
     */
    fun observeMappingForVehicleAndService(
        vehicleId: String,
        serviceId: String,
    ): Flow<VehicleServiceMapping?>

    /**
     * Observes real-time changes to all mappings for a specific vehicle.
     * Returns a Flow that emits updates whenever any mapping for the vehicle changes.
     */
    fun observeMappingsForVehicle(vehicleId: String): Flow<List<VehicleServiceMapping>>

    /**
     * Observes real-time changes to all mappings for a specific user.
     * Returns a Flow that emits updates whenever any mapping for the user changes.
     */
    fun observeMappingsForUser(userId: String): Flow<List<VehicleServiceMapping>>

    // Sync capabilities
    /**
     * Syncs structure data (vehicles and services) when starting monitoring.
     */
    suspend fun syncStructureData(): Result<Unit>

    /**
     * Syncs continuous data (movement readings) for all mappings.
     */
    suspend fun syncContinuousData(): Result<Unit>

    /**
     * Observes sync state changes.
     */
    fun observeSyncState(): Flow<SyncState>

    /**
     * Gets current sync metrics.
     */
    fun getSyncMetrics(): SyncMetrics

    /**
     * Resets sync metrics.
     */
    fun resetSyncMetrics()
}
