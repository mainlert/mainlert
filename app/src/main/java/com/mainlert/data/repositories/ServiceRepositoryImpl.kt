package com.mainlert.data.repositories

import com.mainlert.data.models.Result
import com.mainlert.data.models.Service
import com.mainlert.data.models.ServiceReading
import com.mainlert.data.models.ServiceStatusSummary
import kotlinx.coroutines.flow.Flow

/**
 * ServiceRepositoryImpl delegates all calls to FirebaseServiceRepositoryImpl for Firestore-backed operations.
 */
class ServiceRepositoryImpl : ServiceRepository {
    private val delegate = FirebaseServiceRepositoryImpl()

    override suspend fun getServices(): Result<List<Service>> = delegate.getServices()

    override suspend fun getServiceById(serviceId: String): Result<Service> = delegate.getServiceById(serviceId)

    override suspend fun createService(service: Service): Result<Service> = delegate.createService(service)

    override suspend fun updateService(service: Service): Result<Service> = delegate.updateService(service)

    override suspend fun deleteService(serviceId: String): Result<Unit> = delegate.deleteService(serviceId)

    override suspend fun getServiceReadings(serviceId: String): Result<List<ServiceReading>> = delegate.getServiceReadings(serviceId)

    override suspend fun addServiceReading(reading: ServiceReading): Result<ServiceReading> = delegate.addServiceReading(reading)

    override suspend fun updateServiceReading(reading: ServiceReading): Result<ServiceReading> = delegate.updateServiceReading(reading)

    override suspend fun resetServiceReadings(serviceId: String): Result<Unit> = delegate.resetServiceReadings(serviceId)

    override fun observeServiceReadings(serviceId: String): Flow<List<ServiceReading>> = delegate.observeServiceReadings(serviceId)

    override suspend fun checkDeadlockStatus(serviceId: String): Result<Boolean> = delegate.checkDeadlockStatus(serviceId)

    override suspend fun getCurrentActiveService(): Result<Service?> = delegate.getCurrentActiveService()

    override suspend fun getCurrentActiveServiceForDriver(driverId: String): Result<Service?> =
        delegate.getCurrentActiveServiceForDriver(driverId)

    override suspend fun getServicesForVehicle(vehicleId: String): Result<List<Service>> =
        delegate.getServicesForVehicle(vehicleId)

    override suspend fun getServicesForVehicles(vehicleIds: List<String>): Result<List<Service>> =
        delegate.getServicesForVehicles(vehicleIds)

    override suspend fun startServiceMonitoring(serviceId: String): Result<Unit> = delegate.startServiceMonitoring(serviceId)

    override suspend fun stopServiceMonitoring(serviceId: String): Result<Unit> = delegate.stopServiceMonitoring(serviceId)

    override suspend fun getServiceStatusSummary(serviceId: String): Result<ServiceStatusSummary> =
        delegate.getServiceStatusSummary(
            serviceId,
        )

    override suspend fun getServiceAnalytics(serviceId: String): Result<Map<String, Any>> = delegate.getServiceAnalytics(serviceId)

    override suspend fun exportServiceData(serviceId: String): Result<String> = delegate.exportServiceData(serviceId)
}
