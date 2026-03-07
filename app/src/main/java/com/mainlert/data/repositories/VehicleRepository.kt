package com.mainlert.data.repositories

import com.mainlert.data.models.Result
import com.mainlert.data.models.Vehicle
import kotlinx.coroutines.flow.Flow

/**
 * Vehicle repository interface for MainLert app.
 * Handles vehicle operations.
 */
interface VehicleRepository {
    /**
     * Get all vehicles in the system (Admin only).
     */
    suspend fun getAllVehicles(): Result<List<Vehicle>>

    /**
     * Get all vehicles for a user.
     */
    suspend fun getVehiclesForUser(userId: String): Result<List<Vehicle>>

    /**
     * Get all vehicles managed by an employee.
     */
    suspend fun getVehiclesForEmployee(employeeId: String): Result<List<Vehicle>>

    /**
     * Get vehicle by ID.
     */
    suspend fun getVehicleById(vehicleId: String): Result<Vehicle>

    /**
     * Create new vehicle.
     */
    suspend fun createVehicle(vehicle: Vehicle): Result<Vehicle>

    /**
     * Update vehicle.
     */
    suspend fun updateVehicle(vehicle: Vehicle): Result<Vehicle>

    /**
     * Delete vehicle.
     */
    suspend fun deleteVehicle(vehicleId: String): Result<Unit>

    /**
     * Assign vehicle to driver.
     */
    suspend fun assignVehicleToDriver(vehicleId: String, driverId: String): Result<Unit>

    /**
     * Remove vehicle assignment from driver.
     */
    suspend fun removeVehicleFromDriver(vehicleId: String): Result<Unit>

    /**
     * Get all unassigned vehicles (userId is empty).
     */
    suspend fun getUnassignedVehicles(): Result<List<Vehicle>>

    /**
     * Creates a new vehicle and assigns it to a driver atomically.
     * Updates both vehicle's userId and driver's vehicleIds list.
     */
    suspend fun createVehicleForDriver(
        vehicleName: String,
        plateNumber: String,
        driverId: String,
        employeeId: String = "",
    ): Result<Vehicle>

    /**
     * Observe vehicles for a user in real-time.
     */
    fun observeVehiclesForUser(userId: String): Flow<List<Vehicle>>
}
