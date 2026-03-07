package com.mainlert.data.local.dao

import androidx.room.*
import com.mainlert.data.local.entities.VehicleServiceMappingEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for VehicleServiceMapping entities.
 * Provides database operations for vehicle-service relationship data management.
 */
@Dao
interface VehicleServiceMappingDao {
    
    @Query("SELECT * FROM vehicle_service_mappings WHERE id = :id")
    suspend fun getMapping(id: String): VehicleServiceMappingEntity?
    
    @Query("SELECT * FROM vehicle_service_mappings WHERE vehicleId = :vehicleId")
    fun getMappingsByVehicle(vehicleId: String): Flow<List<VehicleServiceMappingEntity>>
    
    @Query("SELECT * FROM vehicle_service_mappings WHERE serviceId = :serviceId")
    fun getMappingsByService(serviceId: String): Flow<List<VehicleServiceMappingEntity>>
    
    @Query("SELECT * FROM vehicle_service_mappings WHERE userId = :userId")
    fun getMappingsByUser(userId: String): Flow<List<VehicleServiceMappingEntity>>
    
    @Query("SELECT * FROM vehicle_service_mappings")
    fun getAllMappings(): Flow<List<VehicleServiceMappingEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: VehicleServiceMappingEntity)
    
    @Update
    suspend fun updateMapping(mapping: VehicleServiceMappingEntity)
    
    @Delete
    suspend fun deleteMapping(mapping: VehicleServiceMappingEntity)
    
    @Query("UPDATE vehicle_service_mappings SET totalMovement = :movement, localLastUpdated = :timestamp WHERE id = :mappingId")
    suspend fun updateMovement(mappingId: String, movement: Float, timestamp: Long)
    
    @Query("UPDATE vehicle_service_mappings SET isMonitoring = :isMonitoring, lastReadingTime = :timestamp WHERE id = :mappingId")
    suspend fun updateMonitoringStatus(mappingId: String, isMonitoring: Boolean, timestamp: Long)
    
    @Query("UPDATE vehicle_service_mappings SET status = :status, localLastUpdated = :timestamp WHERE id = :mappingId")
    suspend fun updateMappingStatus(mappingId: String, status: String, timestamp: Long)
    
    @Query("SELECT * FROM vehicle_service_mappings WHERE localLastUpdated > firebaseLastUpdated")
    suspend fun getMappingsNeedingSync(): List<VehicleServiceMappingEntity>
    
    @Query("UPDATE vehicle_service_mappings SET firebaseLastUpdated = :syncTime WHERE id = :mappingId")
    suspend fun markAsSynced(mappingId: String, syncTime: Long)
    
    @Query("DELETE FROM vehicle_service_mappings WHERE id = :mappingId")
    suspend fun deleteMappingById(mappingId: String)
    
    @Query("DELETE FROM vehicle_service_mappings WHERE vehicleId = :vehicleId")
    suspend fun deleteMappingsByVehicle(vehicleId: String)
    
    @Query("DELETE FROM vehicle_service_mappings WHERE serviceId = :serviceId")
    suspend fun deleteMappingsByService(serviceId: String)
}