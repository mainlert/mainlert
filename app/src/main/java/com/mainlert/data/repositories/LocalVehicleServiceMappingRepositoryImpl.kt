package com.mainlert.data.repositories

import com.mainlert.data.local.entities.VehicleServiceMappingEntity
import com.mainlert.data.local.entities.toDomain
import com.mainlert.data.models.VehicleServiceMapping.MappingStatus
import com.mainlert.data.utils.toVehicleServiceMapping

import com.mainlert.data.local.LocalDatabase
import com.mainlert.data.local.network.NetworkMonitor
import com.mainlert.data.local.sync.SyncManager
import com.mainlert.data.local.sync.SyncMetrics
import com.mainlert.data.local.sync.SyncState
import com.mainlert.data.models.Result
import com.mainlert.data.models.VehicleServiceMapping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject


class LocalVehicleServiceMappingRepositoryImpl @Inject constructor(
    private val localDatabase: LocalDatabase,
    private val syncManager: SyncManager,
    private val networkMonitor: NetworkMonitor,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : VehicleServiceMappingRepository {
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)

    override suspend fun getMappings(): Result<List<VehicleServiceMapping>> {
            return try {
                val entitiesFlow = localDatabase.mappingDao().getAllMappings()
                val entities = entitiesFlow.first() // Collect the Flow
                val mappings = entities.map { it.toDomain() }
                Result.Success(mappings)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to get mappings")
            }
        }
    
    override suspend fun getMappingById(mappingId: String): Result<VehicleServiceMapping> {
        return try {
            val entity = localDatabase.mappingDao().getMapping(mappingId)
            if (entity != null) {
                Result.Success(
                                    VehicleServiceMapping(
                                        id = entity.id,
                                        vehicleId = entity.vehicleId,
                                        serviceId = entity.serviceId,
                                        serviceName = entity.serviceName,
                                        variantId = entity.variantId,
                                        variantName = entity.variantName,
                                        status = MappingStatus.valueOf(entity.status),
                                        totalMovement = entity.totalMovement,
                                        isMonitoring = entity.isMonitoring,
                                        lastReadingTime = entity.lastReadingTime,
                                        mileageLimit = entity.mileageLimit,
                                        userId = entity.userId,
                                        createdAt = entity.createdAt,
                                        lastUpdated = entity.localLastUpdated,
                                        firebaseLastUpdated = entity.firebaseLastUpdated,
                                        localLastUpdated = entity.localLastUpdated
                                    )
                                )
            } else {
                Result.Failure("Mapping not found")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get mapping")
        }
    }
    
    override suspend fun getMappingsForVehicle(vehicleId: String): Result<List<VehicleServiceMapping>> {
        return try {
            val entitiesFlow = localDatabase.mappingDao().getMappingsByVehicle(vehicleId)
            val entities = entitiesFlow.first() // Collect the Flow
            val mappings = entities.map { it.toDomain() }
            Result.Success(mappings)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get mappings for vehicle")
        }
    }
    
    override suspend fun getMappingsForService(serviceId: String): Result<List<VehicleServiceMapping>> {
        return try {
            val entitiesFlow = localDatabase.mappingDao().getMappingsByService(serviceId)
            val entities = entitiesFlow.first() // Collect the Flow
            val mappings = entities.map { it.toDomain() }
            Result.Success(mappings)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get mappings for service")
        }
    }
    
    override suspend fun getMappingForVehicleAndService(
        vehicleId: String,
        serviceId: String
    ): Result<VehicleServiceMapping?> {
        return try {
            // Query local database only - SyncManager handles Firebase sync
            val entitiesFlow = localDatabase.mappingDao().getMappingsByVehicle(vehicleId)
            val entities = entitiesFlow.first() // Collect the Flow
            val mapping = entities.find { it.serviceId == serviceId }
            
            Result.Success(mapping?.toDomain())
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get mapping for vehicle and service")
        }
    }
    
    override suspend fun getMappingsForUser(userId: String): Result<List<VehicleServiceMapping>> {
        return try {
            val entitiesFlow = localDatabase.mappingDao().getMappingsByUser(userId)
            val entities = entitiesFlow.first() // Collect the Flow
            val mappings = entities.map { it.toDomain() }
            Result.Success(mappings)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get mappings for user")
        }
    }
    
    override suspend fun createMapping(mapping: VehicleServiceMapping): Result<VehicleServiceMapping> {
            return try {
                val entity = mapping.toEntity()
                localDatabase.mappingDao().insertMapping(entity)
                
                // Sync to Firebase
                syncManager.syncContinuousData()
                
                Result.Success(mapping)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to create mapping")
            }
        }
    
    override suspend fun updateMapping(mapping: VehicleServiceMapping): Result<VehicleServiceMapping> {
            return try {
                val entity = mapping.toEntity()
                localDatabase.mappingDao().updateMapping(entity)
                
                // Sync to Firebase
                syncManager.syncContinuousData()
                
                Result.Success(mapping)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to update mapping")
            }
        }
    
    override suspend fun deleteMapping(mappingId: String): Result<Unit> {
        return try {
            localDatabase.mappingDao().deleteMappingById(mappingId)
            
            // Sync to Firebase
            syncManager.syncContinuousData()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to delete mapping")
        }
    }
    
    override suspend fun updateMappingMovement(
        mappingId: String,
        totalMovement: Float
    ): Result<Unit> {
        return try {
            val timestamp = System.currentTimeMillis()
            localDatabase.mappingDao().updateMovement(mappingId, totalMovement, timestamp)
            
            // Sync to Firebase
            syncManager.syncContinuousData()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to update mapping movement")
        }
    }
    
    override suspend fun startMappingMonitoring(mappingId: String): Result<Unit> {
        return try {
            val timestamp = System.currentTimeMillis()
            localDatabase.mappingDao().updateMonitoringStatus(mappingId, true, timestamp)
            
            // Sync to Firebase
            syncManager.syncContinuousData()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to start mapping monitoring")
        }
    }
    
    override suspend fun stopMappingMonitoring(mappingId: String): Result<Unit> {
        return try {
            val timestamp = System.currentTimeMillis()
            localDatabase.mappingDao().updateMonitoringStatus(mappingId, false, timestamp)
            
            // Sync to Firebase
            syncManager.syncContinuousData()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to stop mapping monitoring")
        }
    }
    
    override suspend fun resetMappingReadings(mappingId: String): Result<Unit> {
        return try {
            val timestamp = System.currentTimeMillis()
            localDatabase.mappingDao().updateMovement(mappingId, 0f, timestamp)
            
            // Sync to Firebase
            syncManager.syncContinuousData()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to reset mapping readings")
        }
    }
    
    override suspend fun getActiveMapping(): Result<VehicleServiceMapping?> {
        return try {
            val entitiesFlow = localDatabase.mappingDao().getAllMappings()
            val entities = entitiesFlow.first() // Collect the Flow
            val activeMapping = entities.find { it.isMonitoring }
            
            if (activeMapping != null) {
                Result.Success(activeMapping.toDomain())
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get active mapping")
        }
    }
    
    override suspend fun getActiveMappingsForUser(userId: String): Result<List<VehicleServiceMapping>> {
        return try {
            val entitiesFlow = localDatabase.mappingDao().getMappingsByUser(userId)
            val entities = entitiesFlow.first() // Collect the Flow
            val activeMappings = entities.filter { it.isMonitoring }
            val mappings = activeMappings.map { it.toDomain() }
            Result.Success(mappings)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get active mappings for user")
        }
    }
    
    override fun observeMappingForVehicleAndService(
            vehicleId: String,
            serviceId: String
        ): Flow<VehicleServiceMapping?> {
            return localDatabase.mappingDao().getMappingsByVehicle(vehicleId)
                .map { entities ->
                    entities.find { it.serviceId == serviceId }?.let { entity ->
                        entity.toDomain()
                    }
                }
        }
    
    override fun observeMappingsForVehicle(vehicleId: String): Flow<List<VehicleServiceMapping>> {
            return localDatabase.mappingDao().getMappingsByVehicle(vehicleId)
                .map { entities ->
                    entities.map { entity ->
                        entity.toDomain()
                    }
                }
        }
    
    override fun observeMappingsForUser(userId: String): Flow<List<VehicleServiceMapping>> {
            return localDatabase.mappingDao().getMappingsByUser(userId)
                .map { entities ->
                    entities.map { entity ->
                        entity.toDomain()
                    }
                }
        }
    
    // Sync capabilities
    override suspend fun syncStructureData(): Result<Unit> {
        return try {
            syncManager.syncOnMonitoringStart()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to sync structure data")
        }
    }
    
    override suspend fun syncContinuousData(): Result<Unit> {
        return try {
            syncManager.syncContinuousData()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to sync continuous data")
        }
    }
    
    override fun observeSyncState(): Flow<SyncState> {
        return _syncState.asStateFlow()
    }
    
    override fun getSyncMetrics(): SyncMetrics {
        return syncManager.getSyncMetrics()
    }
    
    override fun resetSyncMetrics() {
        syncManager.resetMetrics()
    }
    
}
