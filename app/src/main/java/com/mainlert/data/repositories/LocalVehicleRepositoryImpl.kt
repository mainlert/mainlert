package com.mainlert.data.repositories

import com.mainlert.data.local.LocalDatabase
import com.mainlert.data.local.entities.VehicleEntity
import com.mainlert.data.local.entities.toDomain
import com.mainlert.data.local.entities.toVehicleEntity
import com.mainlert.data.local.sync.SyncManager
import com.mainlert.data.models.Result
import com.mainlert.data.models.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Local implementation of VehicleRepository.
 * All writes go to local Room database only, then trigger sync via SyncManager.
 * Reads come from local database only.
 */
class LocalVehicleRepositoryImpl @Inject constructor(
    private val localDatabase: LocalDatabase,
    private val syncManager: SyncManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : VehicleRepository {
    
    override suspend fun getAllVehicles(): Result<List<Vehicle>> {
        return try {
            val entitiesFlow = localDatabase.vehicleDao().getAllVehicles()
            val entities = entitiesFlow.first()
            val vehicles = entities.map { it.toDomain() }
            Result.Success(vehicles)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get vehicles")
        }
    }
    
    override suspend fun getVehiclesForUser(userId: String): Result<List<Vehicle>> {
        return try {
            val entitiesFlow = localDatabase.vehicleDao().getVehiclesByUser(userId)
            val entities = entitiesFlow.first()
            val vehicles = entities.map { it.toDomain() }
            Result.Success(vehicles)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get vehicles for user")
        }
    }
    
    override suspend fun getVehiclesForEmployee(employeeId: String): Result<List<Vehicle>> {
        return try {
            val entities = localDatabase.vehicleDao().getAllVehicles().first()
                .filter { it.employeeId == employeeId }
            val vehicles = entities.map { it.toDomain() }
            Result.Success(vehicles)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get vehicles for employee")
        }
    }
    
    override suspend fun getVehicleById(vehicleId: String): Result<Vehicle> {
        return try {
            val entity = localDatabase.vehicleDao().getVehicle(vehicleId)
            if (entity != null) {
                Result.Success(entity.toDomain())
            } else {
                Result.Failure("Vehicle not found")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get vehicle")
        }
    }
    
    override suspend fun createVehicle(vehicle: Vehicle): Result<Vehicle> {
        return try {
            val entity = vehicle.toVehicleEntity()
            localDatabase.vehicleDao().insertVehicle(entity)
            
            // Sync to Firebase
            coroutineScope.launch {
                syncManager.syncOnMonitoringStart()
            }
            
            Result.Success(vehicle)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to create vehicle")
        }
    }
    
    override suspend fun updateVehicle(vehicle: Vehicle): Result<Vehicle> {
        return try {
            val entity = vehicle.toVehicleEntity()
            localDatabase.vehicleDao().updateVehicle(entity)
            
            // Sync to Firebase
            coroutineScope.launch {
                syncManager.syncOnMonitoringStart()
            }
            
            Result.Success(vehicle)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to update vehicle")
        }
    }
    
    override suspend fun deleteVehicle(vehicleId: String): Result<Unit> {
        return try {
            localDatabase.vehicleDao().deleteVehicleById(vehicleId)
            
            // Sync to Firebase
            coroutineScope.launch {
                syncManager.syncOnMonitoringStart()
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to delete vehicle")
        }
    }
    
    override suspend fun assignVehicleToDriver(vehicleId: String, driverId: String): Result<Unit> {
        return try {
            val entity = localDatabase.vehicleDao().getVehicle(vehicleId)
            if (entity != null) {
                val updatedEntity = entity.copy(userId = driverId)
                localDatabase.vehicleDao().updateVehicle(updatedEntity)
                
                // Sync to Firebase
                coroutineScope.launch {
                    syncManager.syncOnMonitoringStart()
                }
                
                Result.Success(Unit)
            } else {
                Result.Failure("Vehicle not found")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to assign vehicle to driver")
        }
    }
    
    override suspend fun removeVehicleFromDriver(vehicleId: String): Result<Unit> {
        return try {
            val entity = localDatabase.vehicleDao().getVehicle(vehicleId)
            if (entity != null) {
                val updatedEntity = entity.copy(userId = "")
                localDatabase.vehicleDao().updateVehicle(updatedEntity)
                
                // Sync to Firebase
                coroutineScope.launch {
                    syncManager.syncOnMonitoringStart()
                }
                
                Result.Success(Unit)
            } else {
                Result.Failure("Vehicle not found")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to remove vehicle from driver")
        }
    }
    
    override suspend fun getUnassignedVehicles(): Result<List<Vehicle>> {
        return try {
            val entitiesFlow = localDatabase.vehicleDao().getAllVehicles()
            val entities = entitiesFlow.first()
                .filter { it.userId.isEmpty() }
            val vehicles = entities.map { it.toDomain() }
            Result.Success(vehicles)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get unassigned vehicles")
        }
    }
    
    override suspend fun createVehicleForDriver(
        vehicleName: String,
        model: String,
        year: Int,
        plateNumber: String,
        driverId: String,
        employeeId: String
    ): Result<Vehicle> {
        return try {
            val vehicle = Vehicle(
                id = "",
                userId = driverId,
                employeeId = employeeId,
                name = vehicleName,
                model = model,
                year = year,
                plateNumber = plateNumber,
                status = Vehicle.VehicleStatus.ACTIVE,
                createdAt = System.currentTimeMillis(),
                lifetimeMileage = 0f
            )
            
            val entity = vehicle.toVehicleEntity()
            localDatabase.vehicleDao().insertVehicle(entity)
            
            // Sync to Firebase
            coroutineScope.launch {
                syncManager.syncOnMonitoringStart()
            }
            
            Result.Success(vehicle)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to create vehicle for driver")
        }
    }
    
    override fun observeVehiclesForUser(userId: String): Flow<List<Vehicle>> {
        return localDatabase.vehicleDao().getVehiclesByUser(userId)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override suspend fun updateVehicleLifetimeMileage(vehicleId: String, mileage: Float): Result<Vehicle> {
        return try {
            val entity = localDatabase.vehicleDao().getVehicle(vehicleId)
            if (entity != null) {
                val updatedEntity = entity.copy(lifetimeMileage = mileage)
                localDatabase.vehicleDao().updateVehicle(updatedEntity)
                
                // Sync to Firebase
                coroutineScope.launch {
                    syncManager.syncContinuousData()
                }
                
                Result.Success(updatedEntity.toDomain())
            } else {
                Result.Failure("Vehicle not found")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to update vehicle lifetime mileage")
        }
    }
}
