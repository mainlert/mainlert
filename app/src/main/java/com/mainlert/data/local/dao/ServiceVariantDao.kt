package com.mainlert.data.local.dao

import androidx.room.*
import com.mainlert.data.local.entities.ServiceVariantEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ServiceVariant entities.
 * Provides database operations for service variant data management.
 */
@Dao
interface ServiceVariantDao {
    
    @Query("SELECT * FROM service_variants WHERE id = :id")
    suspend fun getVariant(id: String): ServiceVariantEntity?
    
    @Query("SELECT * FROM service_variants WHERE createdBy = :userId")
    fun getVariantsByUser(userId: String): Flow<List<ServiceVariantEntity>>
    
    @Query("SELECT * FROM service_variants")
    fun getAllVariants(): Flow<List<ServiceVariantEntity>>
    
    @Query("SELECT * FROM service_variants WHERE isActive = 1")
    fun getActiveVariants(): Flow<List<ServiceVariantEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: ServiceVariantEntity)
    
    @Update
    suspend fun updateVariant(variant: ServiceVariantEntity)
    
    @Delete
    suspend fun deleteVariant(variant: ServiceVariantEntity)
    
    @Query("SELECT * FROM service_variants WHERE lastSyncTime < :threshold")
    suspend fun getVariantsNeedingSync(threshold: Long): List<ServiceVariantEntity>
    
    @Query("UPDATE service_variants SET lastSyncTime = :syncTime, isSynced = :synced WHERE id = :variantId")
    suspend fun updateSyncStatus(variantId: String, syncTime: Long, synced: Boolean)
    
    @Query("DELETE FROM service_variants WHERE id = :variantId")
    suspend fun deleteVariantById(variantId: String)
}
