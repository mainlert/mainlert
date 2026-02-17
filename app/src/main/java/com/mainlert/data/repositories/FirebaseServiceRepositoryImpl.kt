package com.mainlert.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.mainlert.data.models.Result
import com.mainlert.data.models.Service
import com.mainlert.data.models.ServiceReading
import com.mainlert.data.models.ServiceStatusSummary
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firebase-based implementation of ServiceRepository for MainLert app.
 * Provides service and service reading operations with Firestore.
 */
class FirebaseServiceRepositoryImpl
    @Inject
    constructor() : ServiceRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val servicesCollection = firestore.collection("services")
        private val readingsCollection = firestore.collection("service_readings")

        override suspend fun getServices(): Result<List<Service>> {
            return try {
                val querySnapshot =
                    servicesCollection
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .get()
                        .await()

                val services =
                    querySnapshot.mapNotNull { document ->
                        document.toObject(Service::class.java).apply {
                            id = document.id
                        }
                    }

                Result.Success(services)
            } catch (e: FirebaseFirestoreException) {
                Result.Failure(e.message ?: "Failed to fetch services")
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to fetch services")
            }
        }

        override suspend fun getServiceById(serviceId: String): Result<Service> {
            return try {
                val document = servicesCollection.document(serviceId).get().await()

                if (document.exists()) {
                    val service = document.toObject(Service::class.java) ?: throw Exception("Service data not found")
                    Result.Success(service.copy(id = serviceId))
                } else {
                    Result.Failure("Service not found")
                }
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to fetch service")
            }
        }

        override suspend fun createService(service: Service): Result<Service> {
            return try {
                val serviceData = service.copy(id = "").toMap()
                val documentRef = servicesCollection.add(serviceData).await()
                val newService = service.copy(id = documentRef.id)
                Result.Success(newService)
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                Result.Failure("Firestore error: ${e.message}")
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to create service")
            }
        }

        override suspend fun updateService(service: Service): Result<Service> {
            return try {
                if (service.id.isEmpty()) {
                    return Result.Failure("Service ID is required")
                }
                val serviceData = service.toMap()
                servicesCollection.document(service.id).set(serviceData).await()
                Result.Success(service)
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                Result.Failure("Firestore error: ${e.message}")
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to update service")
            }
        }

        override suspend fun deleteService(serviceId: String): Result<Unit> {
            return try {
                // Delete associated readings first
                val readingsQuery = readingsCollection.whereEqualTo("serviceId", serviceId)
                val readingsSnapshot = readingsQuery.get().await()
                readingsSnapshot.documents.forEach { document ->
                    document.reference.delete().await()
                }
                // Delete the service
                servicesCollection.document(serviceId).delete().await()
                Result.Success(Unit)
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                Result.Failure("Firestore error: ${e.message}")
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to delete service")
            }
        }

        override suspend fun getServiceReadings(serviceId: String): Result<List<ServiceReading>> {
            return try {
                val querySnapshot =
                    readingsCollection
                        .whereEqualTo("serviceId", serviceId)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .get()
                        .await()

                val readings =
                    querySnapshot.mapNotNull { document ->
                        document.toObject(ServiceReading::class.java).apply {
                            id = document.id
                        }
                    }

                Result.Success(readings)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to fetch service readings")
            }
        }

        override suspend fun addServiceReading(reading: ServiceReading): Result<ServiceReading> {
            return try {
                val readingData = reading.copy(id = "").toMap()
                val documentRef = readingsCollection.add(readingData).await()
                val newReading = reading.copy(id = documentRef.id)

                // Update service total movement
                updateServiceTotalMovement(reading.serviceId, reading.totalMovement)

                Result.Success(newReading)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to add service reading")
            }
        }

        override suspend fun updateServiceReading(reading: ServiceReading): Result<ServiceReading> {
            return try {
                if (reading.id.isEmpty()) {
                    return Result.Failure("Reading ID is required")
                }

                val readingData = reading.toMap()
                readingsCollection.document(reading.id).set(readingData).await()

                Result.Success(reading)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to update service reading")
            }
        }

        override suspend fun resetServiceReadings(serviceId: String): Result<Unit> {
            return try {
                // Delete all readings for this service
                val readingsQuery = readingsCollection.whereEqualTo("serviceId", serviceId)
                val readingsSnapshot = readingsQuery.get().await()

                readingsSnapshot.documents.forEach { document ->
                    document.reference.delete().await()
                }

                // Reset service total movement and status
                servicesCollection.document(serviceId).update(
                    mapOf(
                        "totalMovement" to 0f,
                        "status" to Service.ServiceStatus.ACTIVE.name,
                        "isMonitoring" to false,
                    ),
                ).await()

                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to reset service readings")
            }
        }

        override fun observeServiceReadings(serviceId: String): Flow<List<ServiceReading>> {
            return callbackFlow {
                val query =
                    readingsCollection
                        .whereEqualTo("serviceId", serviceId)
                        .orderBy("timestamp", Query.Direction.DESCENDING)

                val listener =
                    query.addSnapshotListener { querySnapshot, exception ->
                        if (exception != null) {
                            channel.close(exception)
                            return@addSnapshotListener
                        }

                        val readings =
                            querySnapshot?.mapNotNull { document ->
                                document.toObject(ServiceReading::class.java).apply {
                                    id = document.id
                                }
                            } ?: emptyList()

                        channel.trySend(readings)
                    }

                awaitClose {
                    listener.remove()
                }
            }
        }

        override suspend fun checkMileageStatus(serviceId: String): Result<Boolean> {
            return try {
                val serviceResult = getServiceById(serviceId)
                val readingsResult = getServiceReadings(serviceId)

                if (serviceResult is Result.Success && readingsResult is Result.Success) {
                    val totalMovement = readingsResult.data?.sumOf { it.totalMovement.toDouble() } ?: 0.0

                    // Check if total movement exceeds service's mileage limit
                    val isMileageExceeded = totalMovement >= serviceResult.data?.mileageLimit?.toDouble() ?: 0.0

                    Result.Success(isMileageExceeded)
                } else {
                    Result.Failure("Failed to check mileage status")
                }
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to check mileage status")
            }
        }

    override suspend fun getCurrentActiveService(): Result<Service?> {
        return try {
            val querySnapshot =
                servicesCollection
                    .whereEqualTo("isMonitoring", true)
                    .limit(1)
                    .get()
                    .await()

            if (!querySnapshot.isEmpty) {
                val document = querySnapshot.documents.first()
                val service = document.toObject(Service::class.java)
                if (service != null) {
                    Result.Success(service.copy(id = document.id))
                } else {
                    Result.Success(null)
                }
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get current active service")
        }
    }

    override suspend fun getCurrentActiveServiceForDriver(driverId: String): Result<Service?> {
        return try {
            // First, get all services where userId matches
            val querySnapshot =
                servicesCollection
                    .whereEqualTo("userId", driverId)
                    .whereEqualTo("status", Service.ServiceStatus.ACTIVE.name)
                    .limit(1)
                    .get()
                    .await()

            if (!querySnapshot.isEmpty) {
                val document = querySnapshot.documents.first()
                val service = document.toObject(Service::class.java)
                if (service != null) {
                    Result.Success(service.copy(id = document.id))
                } else {
                    Result.Success(null)
                }
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get current active service for driver")
        }
    }

    override suspend fun getServicesForVehicle(vehicleId: String): Result<List<Service>> {
        return try {
            android.util.Log.d("FirebaseServiceRepo", "Fetching services for vehicleId: $vehicleId")
            
            val querySnapshot =
                servicesCollection
                    .whereArrayContains("vehicleIds", vehicleId)
                    .get()
                    .await()

            val services =
                querySnapshot.mapNotNull { document ->
                    document.toObject(Service::class.java).apply {
                        id = document.id
                    }
                }

            android.util.Log.d("FirebaseServiceRepo", "Found ${services.size} services for vehicle")
            Result.Success(services)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceRepo", "Error fetching services for vehicle", e)
            Result.Failure(e.message ?: "Failed to fetch services for vehicle")
        }
    }

    override suspend fun getServicesForVehicles(vehicleIds: List<String>): Result<List<Service>> {
        return try {
            if (vehicleIds.isEmpty()) {
                return Result.Success(emptyList())
            }

            android.util.Log.d("FirebaseServiceRepo", "Fetching services for vehicleIds: $vehicleIds")
            
            // For multiple vehicle IDs, we need to use array-contains-any (Firestore limitation: max 10)
            val batches = vehicleIds.chunked(10)
            val allServices = mutableListOf<Service>()

            for (batch in batches) {
                @Suppress("UNCHECKED_CAST")
                val querySnapshot =
                    servicesCollection
                        .whereArrayContainsAny("vehicleIds", batch as List<Any?>)
                        .get()
                        .await()

                val services =
                    querySnapshot.mapNotNull { document ->
                        document.toObject(Service::class.java).apply {
                            id = document.id
                        }
                    }
                allServices.addAll(services)
            }

            android.util.Log.d("FirebaseServiceRepo", "Found ${allServices.size} services for vehicles")
            // Remove duplicates (same service may appear in multiple batches)
            Result.Success(allServices.distinctBy { it.id })
        } catch (e: Exception) {
            android.util.Log.e("FirebaseServiceRepo", "Error fetching services for vehicles", e)
            Result.Failure(e.message ?: "Failed to fetch services for vehicles")
        }
    }

        override suspend fun startServiceMonitoring(serviceId: String): Result<Unit> {
            return try {
                servicesCollection.document(serviceId).update(
                    mapOf(
                        "isMonitoring" to true,
                        "status" to Service.ServiceStatus.ACTIVE.name,
                        "lastReadingTime" to System.currentTimeMillis(),
                    ),
                ).await()

                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to start service monitoring")
            }
        }

        override suspend fun stopServiceMonitoring(serviceId: String): Result<Unit> {
            return try {
                servicesCollection.document(serviceId).update(
                    mapOf(
                        "isMonitoring" to false,
                        "status" to Service.ServiceStatus.COMPLETED.name,
                    ),
                ).await()

                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to stop service monitoring")
            }
        }

        override suspend fun getServiceStatusSummary(serviceId: String): Result<ServiceStatusSummary> {
            return try {
                val serviceResult = getServiceById(serviceId)
                val readingsResult = getServiceReadings(serviceId)

                if (serviceResult is Result.Success && readingsResult is Result.Success) {
                    val service = serviceResult.data!!
                    val readings = readingsResult.data!!

                    val totalReadings = readings.size
                    val totalMovement = readings.sumOf { it.totalMovement.toDouble() }
                    val averageMovement = if (totalReadings > 0) totalMovement / totalReadings else 0.0
                    val lastReadingTime = readings.maxOfOrNull { it.timestamp } ?: 0L
                    val isMileageExceeded = totalMovement >= service.mileageLimit.toDouble()

                    val summary =
                        ServiceStatusSummary(
                            serviceId = serviceId,
                            totalReadings = totalReadings,
                            totalMovement = totalMovement.toFloat(),
                            averageMovement = averageMovement.toFloat(),
                            isMonitoring = service.isMonitoring,
                            lastReadingTime = lastReadingTime,
                            isMileageExceeded = isMileageExceeded,
                        )

                    Result.Success(summary)
                } else {
                    Result.Failure("Failed to get service status summary")
                }
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to get service status summary")
            }
        }

        override suspend fun getServiceAnalytics(serviceId: String): Result<Map<String, Any>> {
            return try {
                val readingsResult = getServiceReadings(serviceId)

                if (readingsResult is Result.Success) {
                    val readings = readingsResult.data!!

                    val analytics =
                        mapOf(
                            "totalReadings" to readings.size,
                            "totalMovement" to readings.sumOf { it.totalMovement.toDouble() },
                            "averageMovement" to readings.map { it.totalMovement }.average().toFloat(),
                            "maxMovement" to (readings.maxOfOrNull { it.totalMovement } ?: 0f),
                            "minMovement" to (readings.minOfOrNull { it.totalMovement } ?: 0f),
                            "duration" to
                                if (readings.isNotEmpty()) {
                                    readings.maxOf { it.timestamp } - readings.minOf { it.timestamp }
                                } else {
                                    0L
                                },
                            "readingsPerHour" to calculateReadingsPerHour(readings),
                            "mileageRisk" to calculateMileageRisk(readings),
                        )

                    Result.Success(analytics)
                } else {
                    Result.Failure("Failed to get service analytics")
                }
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to get service analytics")
            }
        }

        override suspend fun exportServiceData(serviceId: String): Result<String> {
            return try {
                val serviceResult = getServiceById(serviceId)
                val readingsResult = getServiceReadings(serviceId)

                if (serviceResult is Result.Success && readingsResult is Result.Success) {
                    val service = serviceResult.data!!
                    val readings = readingsResult.data!!

                    val exportData =
                        mapOf<String, Any>(
                            "service" to service,
                            "readings" to readings,
                            "exportDate" to System.currentTimeMillis(),
                            "totalReadings" to readings.size,
                        )

                    // Convert to JSON string (simplified implementation)
                    val jsonString = exportData.toString()

                    Result.Success(jsonString)
                } else {
                    Result.Failure("Failed to export service data")
                }
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to export service data")
            }
        }

        private fun calculateReadingsPerHour(readings: List<ServiceReading>): Double {
            if (readings.size < 2) return 0.0

            val durationHours = (readings.maxOf { it.timestamp } - readings.minOf { it.timestamp }) / (1000.0 * 60 * 60)
            return if (durationHours > 0) readings.size.toDouble() / durationHours else 0.0
        }

        private fun calculateMileageRisk(readings: List<ServiceReading>): String {
            val totalMovement = readings.sumOf { it.totalMovement.toDouble() }
            val averageMovement = readings.map { it.totalMovement }.average()

            return when {
                totalMovement > 15000 -> "HIGH"
                totalMovement > 10000 -> "MEDIUM"
                averageMovement > 100 -> "LOW"
                else -> "MINIMAL"
            }
        }

        private fun updateServiceTotalMovement(
            serviceId: String,
            movement: Float,
        ) {
            try {
                servicesCollection.document(serviceId).get().addOnSuccessListener { serviceDoc ->
                    if (serviceDoc.exists()) {
                        val currentMovement = serviceDoc.getDouble("totalMovement")?.toFloat() ?: 0f
                        val newTotalMovement = currentMovement + movement

                        servicesCollection.document(serviceId).update(
                            mapOf("totalMovement" to newTotalMovement),
                        )
                    }
                }
            } catch (e: Exception) {
                // Log error but don't fail the operation
            }
        }
    }
