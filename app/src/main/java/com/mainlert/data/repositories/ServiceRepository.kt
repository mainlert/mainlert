package com.mainlert.data.repositories

import com.mainlert.data.models.Result
import com.mainlert.data.models.Service

/**
 * Service repository interface for MainLert app.
 * Handles service template operations only.
 * All readings are managed via VehicleServiceMappingRepository.
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
     */
    suspend fun getServices(): Result<List<Service>>

    /**
     * Get service by ID.
     */
    suspend fun getServiceById(serviceId: String): Result<Service>

    /**
     * Create new service.
     */
    suspend fun createService(service: Service): Result<Service>

    /**
     * Update service.
     */
    suspend fun updateService(service: Service): Result<Service>

    /**
     * Delete service.
     */
    suspend fun deleteService(serviceId: String): Result<Unit>
}
