package com.mainlert.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.mainlert.data.models.Result
import com.mainlert.data.models.Service
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firebase-based implementation of ServiceRepository for MainLert app.
 * Provides service template operations - readings are managed via VehicleServiceMapping.
 */
class FirebaseServiceRepositoryImpl
    @Inject
    constructor() : ServiceRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val servicesCollection = firestore.collection("services")

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
                val serviceWithId = service.copy(id = serviceId)
                Result.Success(serviceWithId)
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
            // Delete the service
            servicesCollection.document(serviceId).delete().await()
            Result.Success(Unit)
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to delete service")
        }
    }
}
