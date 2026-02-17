package com.mainlert.data.repositories

import com.mainlert.data.models.Result
import com.mainlert.data.models.Service
import com.mainlert.data.models.ServiceReading
import com.mainlert.data.models.ServiceStatusSummary
import kotlinx.coroutines.flow.Flow

/**
 * Service repository interface for MainLert app.
 * Handles service and service reading operations.
 *
 * Usage examples:
 * ```kotlin
 * // Get all services
 * val result = serviceRepository.getServices()
 *
 * // Create a new service
 * val newService = Service(/* ... */)
 * val createResult = serviceRepository.createService(newService)
 * ```
 */
interface ServiceRepository {
    /**
     * Get all services for current user.
     * Usage:
     * ```kotlin
     * val result = serviceRepository.getServices()
     * ```
     */
    suspend fun getServices(): Result<List<Service>>

    /**
     * Get service by ID.
     * Usage:
     * ```kotlin
     * val result = serviceRepository.getServiceById("serviceId")
     * ```
     */
    suspend fun getServiceById(serviceId: String): Result<Service>

    /**
     * Create new service.
     * Usage:
     * ```kotlin
     * val newService = Service(/* ... */)
     * val result = serviceRepository.createService(newService)
     * ```
     */
    suspend fun createService(service: Service): Result<Service>

    /**
     * Update service.
     * Usage:
     * ```kotlin
     * val updatedService = service.copy(/* ... */)
     * val result = serviceRepository.updateService(updatedService)
     * ```
     */
    suspend fun updateService(service: Service): Result<Service>

    /**
     * Delete service.
     * Usage:
     * ```kotlin
     * val result = serviceRepository.deleteService("serviceId")
     * ```
     */
    suspend fun deleteService(serviceId: String): Result<Unit>

    /**
     * Get service readings for a service
     */
    suspend fun getServiceReadings(serviceId: String): Result<List<ServiceReading>>

    /**
     * Add service reading
     */
    suspend fun addServiceReading(reading: ServiceReading): Result<ServiceReading>

    /**
     * Update service reading
     */
    suspend fun updateServiceReading(reading: ServiceReading): Result<ServiceReading>

    /**
     * Reset service readings
     */
    suspend fun resetServiceReadings(serviceId: String): Result<Unit>

    /**
     * Observe service readings in real-time
     */
    fun observeServiceReadings(serviceId: String): Flow<List<ServiceReading>>

    /**
     * Check if service has reached mileage limit
     */
    suspend fun checkMileageStatus(serviceId: String): Result<Boolean>

    /**
     * Get current active service for user (legacy method)
     */
    suspend fun getCurrentActiveService(): Result<Service?>

    /**
     * Get current active service for a specific driver
     */
    suspend fun getCurrentActiveServiceForDriver(driverId: String): Result<Service?>

    /**
     * Get services for a specific vehicle
     */
    suspend fun getServicesForVehicle(vehicleId: String): Result<List<Service>>

    /**
     * Get services for multiple vehicles
     */
    suspend fun getServicesForVehicles(vehicleIds: List<String>): Result<List<Service>>

    /**
     * Start service monitoring
     */
    suspend fun startServiceMonitoring(serviceId: String): Result<Unit>

    /**
     * Stop service monitoring
     */
    suspend fun stopServiceMonitoring(serviceId: String): Result<Unit>

/**
     * Get service status summary
     */
    suspend fun getServiceStatusSummary(serviceId: String): Result<ServiceStatusSummary>

/**
     * Get service analytics data
     */
    suspend fun getServiceAnalytics(serviceId: String): Result<Map<String, Any>>

/**
     * Export service data
     */
    suspend fun exportServiceData(serviceId: String): Result<String>
}
