package com.mainlert.data.local.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.mainlert.data.local.entities.VehicleEntity
import com.mainlert.data.local.entities.VehicleServiceMappingEntity
import com.mainlert.data.local.entities.ServiceEntity
import com.mainlert.data.models.Result
import com.mainlert.data.local.entities.toVehicleFirebaseMap
import com.mainlert.data.local.entities.toServiceFirebaseMap
import com.mainlert.data.local.entities.toMappingFirebaseMap
import com.mainlert.data.local.entities.toVehicleEntity
import com.mainlert.data.local.entities.toServiceEntity
import com.mainlert.data.local.entities.toVehicleServiceMappingEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firebase sync service for hierarchical sync system.
 * Handles all Firebase database operations for sync operations.
 */
class FirebaseSyncService @Inject constructor(
    private val firebaseFirestore: FirebaseFirestore
) {
    
    /**
     * Sync vehicle data to Firebase.
     */
    suspend fun syncVehicle(vehicle: VehicleEntity): Result<Unit> {
        return try {
            firebaseFirestore.collection("vehicles")
                .document(vehicle.id)
                .set(vehicle.toVehicleFirebaseMap())
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message)
        }
    }
    
    /**
     * Sync service data to Firebase.
     */
    suspend fun syncService(service: ServiceEntity): Result<Unit> {
        return try {
            firebaseFirestore.collection("services")
                .document(service.id)
                .set(service.toServiceFirebaseMap())
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message)
        }
    }
    
    /**
     * Sync VehicleServiceMapping data to Firebase.
     */
    suspend fun syncMapping(mapping: VehicleServiceMappingEntity): Result<Unit> {
        return try {
            firebaseFirestore.collection("vehicle_service_mappings")
                .document(mapping.id)
                .set(mapping.toMappingFirebaseMap())
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message)
        }
    }
    
    /**
     * Update VehicleServiceMapping movement data in Firebase.
     */
    suspend fun updateMappingMovement(
        mappingId: String,
        movement: Float,
        timestamp: Long
    ): Result<Unit> {
        return try {
            firebaseFirestore.collection("vehicle_service_mappings")
                .document(mappingId)
                .update(
                    "totalMovement", movement,
                    "firebaseLastUpdated", timestamp
                )
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message)
        }
    }
    
    /**
     * Get VehicleServiceMapping from Firebase.
     */
    suspend fun getMapping(id: String): VehicleServiceMappingEntity? {
        return try {
            val document = firebaseFirestore.collection("vehicle_service_mappings")
                .document(id)
                .get()
                .await()
            
            if (document.exists()) {
                // Convert Firebase document to entity
                document.toVehicleServiceMappingEntity()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Create new VehicleServiceMapping in Firebase.
     */
    suspend fun createMapping(mapping: VehicleServiceMappingEntity): Result<Unit> {
        return try {
            firebaseFirestore.collection("vehicle_service_mappings")
                .document(mapping.id)
                .set(mapping.toMappingFirebaseMap())
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message)
        }
    }
    
    /**
     * Update existing VehicleServiceMapping in Firebase.
     */
    suspend fun updateMapping(mapping: VehicleServiceMappingEntity): Result<Unit> {
        return try {
            firebaseFirestore.collection("vehicle_service_mappings")
                .document(mapping.id)
                .update(mapping.toMappingFirebaseMap())
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message)
        }
    }
    
    /**
     * Get all VehicleServiceMappings for a vehicle from Firebase.
     */
    suspend fun getMappingsForVehicle(vehicleId: String): List<VehicleServiceMappingEntity> {
        return try {
            val query = firebaseFirestore.collection("vehicle_service_mappings")
                .whereEqualTo("vehicleId", vehicleId)
                .get()
                .await()
            
            query.documents.mapNotNull { document ->
                document.toVehicleServiceMappingEntity()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get all VehicleServiceMappings for a user from Firebase.
     */
    suspend fun getMappingsForUser(userId: String): List<VehicleServiceMappingEntity> {
        return try {
            val query = firebaseFirestore.collection("vehicle_service_mappings")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            query.documents.mapNotNull { document ->
                document.toVehicleServiceMappingEntity()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get all vehicles for a user from Firebase.
     */
    suspend fun getVehiclesForUser(userId: String): List<VehicleEntity> {
        return try {
            val query = firebaseFirestore.collection("vehicles")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            query.documents.mapNotNull { document ->
                document.toVehicleEntity()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get all services for a user from Firebase.
     */
    suspend fun getServicesForUser(userId: String): List<ServiceEntity> {
        return try {
            val query = firebaseFirestore.collection("services")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            query.documents.mapNotNull { document ->
                document.toServiceEntity()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Convert Firebase document to VehicleServiceMappingEntity.
     */
    private fun convertDocumentToMappingEntity(document: com.google.firebase.firestore.DocumentSnapshot): VehicleServiceMappingEntity? {
        return document.toVehicleServiceMappingEntity()
    }
    
    /**
     * Convert Firebase document to VehicleEntity.
     */
    private fun convertDocumentToVehicleEntity(document: com.google.firebase.firestore.DocumentSnapshot): VehicleEntity? {
        return document.toVehicleEntity()
    }
    
    /**
     * Convert Firebase document to ServiceEntity.
     */
    private fun convertDocumentToServiceEntity(document: com.google.firebase.firestore.DocumentSnapshot): ServiceEntity? {
        return document.toServiceEntity()
    }
}