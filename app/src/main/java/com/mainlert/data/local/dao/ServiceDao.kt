package com.mainlert.data.local.dao

import androidx.room.*
import com.mainlert.data.local.entities.ServiceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Service entities.
 * Provides database operations for service data management.
 */
@Dao
interface ServiceDao {
    
    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun getService(id: String): ServiceEntity?
    
    @Query("SELECT * FROM services WHERE userId = :userId")
    fun getServicesByUser(userId: String): Flow<List<ServiceEntity>>
    
    @Query("SELECT * FROM services")
    fun getAllServices(): Flow<List<ServiceEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: ServiceEntity)
    
    @Update
    suspend fun updateService(service: ServiceEntity)
    
    @Delete
    suspend fun deleteService(service: ServiceEntity)
    
    @Query("SELECT * FROM services WHERE lastSyncTime < :threshold")
    suspend fun getServicesNeedingSync(threshold: Long): List<ServiceEntity>
    
    @Query("UPDATE services SET lastSyncTime = :syncTime, isSynced = :synced WHERE id = :serviceId")
    suspend fun updateSyncStatus(serviceId: String, syncTime: Long, synced: Boolean)
    
    @Query("DELETE FROM services WHERE id = :serviceId")
    suspend fun deleteServiceById(serviceId: String)
}