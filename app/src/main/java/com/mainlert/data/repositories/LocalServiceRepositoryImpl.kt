package com.mainlert.data.repositories

import com.mainlert.data.local.LocalDatabase
import com.mainlert.data.local.entities.ServiceEntity
import com.mainlert.data.local.entities.toDomain
import com.mainlert.data.local.entities.toServiceEntity
import com.mainlert.data.local.sync.SyncManager
import com.mainlert.data.models.Result
import com.mainlert.data.models.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Local implementation of ServiceRepository.
 * All writes go to local Room database only, then trigger sync via SyncManager.
 * Reads come from local database only.
 */
class LocalServiceRepositoryImpl @Inject constructor(
    private val localDatabase: LocalDatabase,
    private val syncManager: SyncManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ServiceRepository {
    
    override suspend fun getServices(): Result<List<Service>> {
        return try {
            val entitiesFlow = localDatabase.serviceDao().getAllServices()
            val entities = entitiesFlow.first()
            val services = entities.map { it.toDomain() }
            Result.Success(services)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get services")
        }
    }
    
    override suspend fun getServiceById(serviceId: String): Result<Service> {
        return try {
            val entity = localDatabase.serviceDao().getService(serviceId)
            if (entity != null) {
                Result.Success(entity.toDomain())
            } else {
                Result.Failure("Service not found")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get service")
        }
    }
    
    override suspend fun createService(service: Service): Result<Service> {
        return try {
            val entity = service.toServiceEntity()
            localDatabase.serviceDao().insertService(entity)
            
            // Sync to Firebase
            coroutineScope.launch {
                syncManager.syncOnMonitoringStart()
            }
            
            Result.Success(service)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to create service")
        }
    }
    
    override suspend fun updateService(service: Service): Result<Service> {
        return try {
            val entity = service.toServiceEntity()
            localDatabase.serviceDao().updateService(entity)
            
            // Sync to Firebase
            coroutineScope.launch {
                syncManager.syncOnMonitoringStart()
            }
            
            Result.Success(service)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to update service")
        }
    }
    
    override suspend fun deleteService(serviceId: String): Result<Unit> {
        return try {
            localDatabase.serviceDao().deleteServiceById(serviceId)
            
            // Sync to Firebase
            coroutineScope.launch {
                syncManager.syncOnMonitoringStart()
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to delete service")
        }
    }
}
