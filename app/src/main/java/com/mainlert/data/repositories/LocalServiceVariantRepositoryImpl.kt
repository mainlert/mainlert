package com.mainlert.data.repositories

import com.mainlert.data.local.LocalDatabase
import com.mainlert.data.local.entities.ServiceVariantEntity
import com.mainlert.data.local.entities.toDomain
import com.mainlert.data.local.entities.toServiceVariantEntity
import com.mainlert.data.local.sync.SyncManager
import com.mainlert.data.models.Result
import com.mainlert.data.models.ServiceVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Local implementation of ServiceVariantRepository.
 * All writes go to local Room database only, then trigger sync via SyncManager.
 * Reads come from local database only.
 */
class LocalServiceVariantRepositoryImpl @Inject constructor(
    private val localDatabase: LocalDatabase,
    private val syncManager: SyncManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ServiceVariantRepository {
    
    override suspend fun getVariants(): Result<List<ServiceVariant>> {
        return try {
            val entitiesFlow = localDatabase.serviceVariantDao().getAllVariants()
            val entities = entitiesFlow.first()
            val variants = entities.map { it.toDomain() }
            Result.Success(variants)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get variants")
        }
    }
    
    override suspend fun getVariantById(variantId: String): Result<ServiceVariant> {
        return try {
            val entity = localDatabase.serviceVariantDao().getVariant(variantId)
            if (entity != null) {
                Result.Success(entity.toDomain())
            } else {
                Result.Failure("Variant not found")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get variant")
        }
    }
    
    override suspend fun createVariant(variant: ServiceVariant): Result<ServiceVariant> {
        return try {
            val entity = variant.toServiceVariantEntity()
            localDatabase.serviceVariantDao().insertVariant(entity)
            
            // Sync to Firebase
            coroutineScope.launch {
                syncManager.syncOnMonitoringStart()
            }
            
            Result.Success(variant)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to create variant")
        }
    }
    
    override suspend fun updateVariant(variant: ServiceVariant): Result<ServiceVariant> {
        return try {
            val entity = variant.toServiceVariantEntity()
            localDatabase.serviceVariantDao().updateVariant(entity)
            
            // Sync to Firebase
            coroutineScope.launch {
                syncManager.syncOnMonitoringStart()
            }
            
            Result.Success(variant)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to update variant")
        }
    }
    
    override suspend fun deleteVariant(variantId: String): Result<Unit> {
        return try {
            localDatabase.serviceVariantDao().deleteVariantById(variantId)
            
            // Sync to Firebase
            coroutineScope.launch {
                syncManager.syncOnMonitoringStart()
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to delete variant")
        }
    }
    
    override fun observeVariants(): Flow<List<ServiceVariant>> {
        return localDatabase.serviceVariantDao().getActiveVariants()
            .map { entities -> entities.map { it.toDomain() } }
    }
}
