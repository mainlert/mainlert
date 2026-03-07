package com.mainlert.data.local.dao

import androidx.room.*
import com.mainlert.data.local.entities.VehicleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Vehicle entities.
 * Provides database operations for vehicle data management.
 */
@Dao
interface VehicleDao {
    
    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicle(id: String): VehicleEntity?
    
    @Query("SELECT * FROM vehicles WHERE userId = :userId")
    fun getVehiclesByUser(userId: String): Flow<List<VehicleEntity>>
    
    @Query("SELECT * FROM vehicles")
    fun getAllVehicles(): Flow<List<VehicleEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity)
    
    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)
    
    @Delete
    suspend fun deleteVehicle(vehicle: VehicleEntity)
    
    @Query("SELECT * FROM vehicles WHERE lastSyncTime < :threshold")
    suspend fun getVehiclesNeedingSync(threshold: Long): List<VehicleEntity>
    
    @Query("UPDATE vehicles SET lastSyncTime = :syncTime, isSynced = :synced WHERE id = :vehicleId")
    suspend fun updateSyncStatus(vehicleId: String, syncTime: Long, synced: Boolean)
    
    @Query("DELETE FROM vehicles WHERE id = :vehicleId")
    suspend fun deleteVehicleById(vehicleId: String)
}