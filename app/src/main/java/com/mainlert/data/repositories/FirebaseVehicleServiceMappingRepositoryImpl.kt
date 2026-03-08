package com.mainlert.data.repositories

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.mainlert.data.models.Result
import com.mainlert.data.models.User
import com.mainlert.data.models.Vehicle
import com.mainlert.data.models.VehicleServiceMapping
import com.mainlert.data.local.sync.SyncState
import com.mainlert.data.local.sync.SyncMetrics
import com.mainlert.data.utils.toFirebaseMap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firebase-based implementation of VehicleServiceMappingRepository.
 * Provides CRUD operations for vehicle-service mappings with independent readings.
 */
class FirebaseVehicleServiceMappingRepositoryImpl
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val vehicleRepository: VehicleRepository,
    ) : VehicleServiceMappingRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val mappingsCollection = firestore.collection("vehicle_service_mappings")

    override suspend fun getMappings(): Result<List<VehicleServiceMapping>> {
        return try {
            val querySnapshot =
                mappingsCollection
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

            val mappings =
                querySnapshot.mapNotNull { document ->
                    document.toObject(VehicleServiceMapping::class.java).apply {
                        id = document.id
                    }
                }

            Result.Success(mappings)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure(e.message ?: "Failed to fetch mappings")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch mappings")
        }
    }

    override suspend fun getMappingById(mappingId: String): Result<VehicleServiceMapping> {
        return try {
            val document = mappingsCollection.document(mappingId).get().await()

            if (document.exists()) {
                val mapping = document.toObject(VehicleServiceMapping::class.java) ?: throw Exception("Mapping data not found")
                Result.Success(mapping.copy(id = mappingId))
            } else {
                Result.Failure("Mapping not found")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch mapping")
        }
    }

    override suspend fun getMappingsForVehicle(vehicleId: String): Result<List<VehicleServiceMapping>> {
        return try {
            val querySnapshot =
                mappingsCollection
                    .whereEqualTo("vehicleId", vehicleId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

            val mappings =
                querySnapshot.mapNotNull { document ->
                    document.toObject(VehicleServiceMapping::class.java).apply {
                        id = document.id
                    }
                }

            Result.Success(mappings)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch mappings for vehicle")
        }
    }

    override suspend fun getMappingsForService(serviceId: String): Result<List<VehicleServiceMapping>> {
        return try {
            val querySnapshot =
                mappingsCollection
                    .whereEqualTo("serviceId", serviceId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

            val mappings =
                querySnapshot.mapNotNull { document ->
                    document.toObject(VehicleServiceMapping::class.java).apply {
                        id = document.id
                    }
                }

            Result.Success(mappings)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch mappings for service")
        }
    }

    override suspend fun getMappingForVehicleAndService(
        vehicleId: String,
        serviceId: String,
    ): Result<VehicleServiceMapping?> {
        return try {
            val querySnapshot =
                mappingsCollection
                    .whereEqualTo("vehicleId", vehicleId)
                    .whereEqualTo("serviceId", serviceId)
                    .limit(1)
                    .get()
                    .await()

            if (!querySnapshot.isEmpty) {
                val document = querySnapshot.documents.first()
                val mapping = document.toObject(VehicleServiceMapping::class.java) ?: throw Exception("Mapping data not found")
                Result.Success(mapping.copy(id = document.id))
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch mapping for vehicle and service")
        }
    }

    override suspend fun getMappingsForUser(userId: String): Result<List<VehicleServiceMapping>> {
        return try {
            val querySnapshot =
                mappingsCollection
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

            val mappings =
                querySnapshot.mapNotNull { document ->
                    document.toObject(VehicleServiceMapping::class.java).apply {
                        id = document.id
                    }
                }

            Result.Success(mappings)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch mappings for user")
        }
    }

    override suspend fun createMapping(mapping: VehicleServiceMapping): Result<VehicleServiceMapping> {
        return try {
            // Log detailed context information before attempting creation
            logMappingCreationAttempt(mapping)
            
            val mappingData = mapping.copy(id = "").toMap()
            val documentRef = mappingsCollection.add(mappingData).await()
            val newMapping = mapping.copy(id = documentRef.id)
            
            Log.i("FirebaseVehicleServiceMappingRepositoryImpl", 
                "✅ SUCCESSFULLY CREATED VehicleServiceMapping: " +
                "id=${documentRef.id}, " +
                "vehicleId=${mapping.vehicleId}, " +
                "serviceId=${mapping.serviceId}, " +
                "userId=${mapping.userId}, " +
                "serviceName=${mapping.serviceName}")
            Result.Success(newMapping)
        } catch (e: FirebaseFirestoreException) {
            // Enhanced error logging with comprehensive permission analysis
            logMappingCreationFailure(mapping, e)
            Result.Failure(detailedPermissionErrorMessage(e, mapping))
        } catch (e: Exception) {
            Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
                "❌ EXCEPTION creating mapping: " +
                "vehicleId=${mapping.vehicleId}, " +
                "serviceId=${mapping.serviceId}, " +
                "userId=${mapping.userId}, " +
                "error=${e.message}", e)
            Result.Failure(e.message ?: "Failed to create mapping")
        }
    }

    override suspend fun updateMapping(mapping: VehicleServiceMapping): Result<VehicleServiceMapping> {
        return try {
            if (mapping.id.isEmpty()) {
                return Result.Failure("Mapping ID is required")
            }
            val mappingData = mapping.toMap()
            mappingsCollection.document(mapping.id).set(mappingData).await()
            Result.Success(mapping)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to update mapping")
        }
    }

    override suspend fun deleteMapping(mappingId: String): Result<Unit> {
        return try {
            mappingsCollection.document(mappingId).delete().await()
            Result.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to delete mapping")
        }
    }

    override suspend fun updateMappingMovement(
        mappingId: String,
        totalMovement: Float,
    ): Result<Unit> {
        return try {
            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", "Updating mapping movement: mappingId=$mappingId, totalMovement=$totalMovement")
            mappingsCollection.document(mappingId).update(
                mapOf(
                    "totalMovement" to totalMovement,
                    "lastReadingTime" to System.currentTimeMillis(),
                ),
            ).await()
            Log.i("FirebaseVehicleServiceMappingRepositoryImpl", "Successfully updated mapping movement: mappingId=$mappingId, totalMovement=$totalMovement")
            Result.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Log.e("FirebaseVehicleServiceMappingRepositoryImpl", "Firestore error updating mapping movement: mappingId=$mappingId, error=${e.message}", e)
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Log.e("FirebaseVehicleServiceMappingRepositoryImpl", "Exception updating mapping movement: mappingId=$mappingId, error=${e.message}", e)
            Result.Failure(e.message ?: "Failed to update mapping movement")
        }
    }

    override suspend fun saveMovementCheckpoint(
        mappingId: String,
        totalMovement: Float,
    ): Result<Unit> {
        return try {
            val timestamp = System.currentTimeMillis()
            mappingsCollection.document(mappingId).update(
                mapOf(
                    "totalMovement" to totalMovement,
                    "lastReadingTime" to timestamp,
                    "checkpointTimestamp" to timestamp,
                ),
            ).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to save movement checkpoint")
        }
    }

    override suspend fun startMappingMonitoring(mappingId: String): Result<Unit> {
        return try {
            mappingsCollection.document(mappingId).update(
                mapOf(
                    "isMonitoring" to true,
                    "status" to VehicleServiceMapping.MappingStatus.ACTIVE.name,
                    "lastReadingTime" to System.currentTimeMillis(),
                ),
            ).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to start mapping monitoring")
        }
    }

    override suspend fun stopMappingMonitoring(mappingId: String): Result<Unit> {
        return try {
            mappingsCollection.document(mappingId).update(
                mapOf(
                    "isMonitoring" to false,
                    "status" to VehicleServiceMapping.MappingStatus.COMPLETED.name,
                ),
            ).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to stop mapping monitoring")
        }
    }

    override suspend fun resetMappingReadings(mappingId: String): Result<Unit> {
        return try {
            mappingsCollection.document(mappingId).update(
                mapOf(
                    "totalMovement" to 0f,
                    "status" to VehicleServiceMapping.MappingStatus.ACTIVE.name,
                    "isMonitoring" to false,
                    "lastReadingTime" to 0L,
                ),
            ).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to reset mapping readings")
        }
    }

    override suspend fun syncStructureData(): Result<Unit> {
        return try {
            // Structure data sync is handled by other repositories (VehicleRepository, ServiceRepository)
            // This repository focuses on VehicleServiceMapping data which is continuous data
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to sync structure data")
        }
    }

    override suspend fun syncContinuousData(): Result<Unit> {
        return try {
            // Sync continuous data (movement readings) for all mappings
            // This would typically sync the totalMovement values from Firebase
            val querySnapshot = mappingsCollection.get().await()
            
            // Process each mapping to ensure continuous data is up to date
            querySnapshot.documents.forEach { document ->
                val mapping = document.toObject(VehicleServiceMapping::class.java)
                if (mapping != null) {
                    // Update the mapping with current data if needed
                    // This is where you would sync any continuous data changes
                }
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to sync continuous data")
        }
    }

    override suspend fun getActiveMapping(): Result<VehicleServiceMapping?> {
        return try {
            val querySnapshot =
                mappingsCollection
                    .whereEqualTo("isMonitoring", true)
                    .limit(1)
                    .get()
                    .await()

            if (!querySnapshot.isEmpty) {
                val document = querySnapshot.documents.first()
                val mapping = document.toObject(VehicleServiceMapping::class.java)
                if (mapping != null) {
                    Result.Success(mapping.copy(id = document.id))
                } else {
                    Result.Success(null)
                }
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get active mapping")
        }
    }

    override suspend fun getActiveMappingsForUser(userId: String): Result<List<VehicleServiceMapping>> {
        return try {
            val querySnapshot =
                mappingsCollection
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("isMonitoring", true)
                    .get()
                    .await()

            val mappings =
                querySnapshot.mapNotNull { document ->
                    document.toObject(VehicleServiceMapping::class.java).apply {
                        id = document.id
                    }
                }

            Result.Success(mappings)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to get active mappings for user")
        }
    }

    override fun observeMappingForVehicleAndService(
        vehicleId: String,
        serviceId: String,
    ): Flow<VehicleServiceMapping?> {
        return callbackFlow {
            val query = mappingsCollection
                .whereEqualTo("vehicleId", vehicleId)
                .whereEqualTo("serviceId", serviceId)
                .limit(1)

            val listener = query.addSnapshotListener { querySnapshot, exception ->
                if (exception != null) {
                    close(exception)
                    return@addSnapshotListener
                }

                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    val document = querySnapshot.documents.first()
                    val mapping = document.toObject(VehicleServiceMapping::class.java)
                    if (mapping != null) {
                        trySend(mapping.copy(id = document.id))
                    } else {
                        trySend(null)
                    }
                } else {
                    trySend(null)
                }
            }

            awaitClose { listener.remove() }
        }
    }

    override fun observeMappingsForVehicle(vehicleId: String): Flow<List<VehicleServiceMapping>> {
        return callbackFlow {
            val query = mappingsCollection
                .whereEqualTo("vehicleId", vehicleId)
                .orderBy("createdAt", Query.Direction.DESCENDING)

            val listener = query.addSnapshotListener { querySnapshot, exception ->
                if (exception != null) {
                    close(exception)
                    return@addSnapshotListener
                }

                val mappings = querySnapshot?.mapNotNull { document ->
                    document.toObject(VehicleServiceMapping::class.java).apply {
                        id = document.id
                    }
                } ?: emptyList()

                trySend(mappings)
            }

            awaitClose { listener.remove() }
        }
    }

    override fun observeMappingsForUser(userId: String): Flow<List<VehicleServiceMapping>> {
        return callbackFlow {
            val query = mappingsCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)

            val listener = query.addSnapshotListener { querySnapshot, exception ->
                if (exception != null) {
                    close(exception)
                    return@addSnapshotListener
                }

                val mappings = querySnapshot?.mapNotNull { document ->
                    document.toObject(VehicleServiceMapping::class.java).apply {
                        id = document.id
                    }
                } ?: emptyList()

                trySend(mappings)
            }

            awaitClose { listener.remove() }
        }
    }

    override fun observeSyncState(): Flow<SyncState> {
        // Firebase implementation doesn't have sync state management
        // Return a flow that always emits idle state
        return kotlinx.coroutines.flow.flowOf(SyncState.Idle)
    }

    override fun getSyncMetrics(): SyncMetrics {
        return SyncMetrics() // temporary stub
    }

    override fun resetSyncMetrics() {
        // Firebase implementation doesn't have sync metrics management
        // No-op for now
    }

    /**
     * Logs detailed context information before attempting to create a VehicleServiceMapping.
     * This includes user authentication status, role, vehicle ownership validation, and permission rule analysis.
     */
    private suspend fun logMappingCreationAttempt(mapping: VehicleServiceMapping) {
        try {
            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                """
                📝 ATTEMPTING TO CREATE VehicleServiceMapping:
                -----------------------------------------
                📋 MAPPING DATA:
                  • vehicleId: ${mapping.vehicleId}
                  • serviceId: ${mapping.serviceId}
                  • userId (in mapping): ${mapping.userId}
                  • serviceName: ${mapping.serviceName}
                  • variantId: ${mapping.variantId}
                  • variantName: ${mapping.variantName}
                
                🔐 USER CONTEXT:
                """.trimIndent())
            
            // Get current user authentication status
            val currentUserId = authRepository.getCurrentUserId()
            val currentUserRole = authRepository.getCurrentUserRole()
            
            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                "  • Current User ID: ${currentUserId ?: "NULL (not authenticated)"}")
            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                "  • Current User Role: ${currentUserRole ?: "NULL (unknown role)"}")
            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                "  • User Authenticated: ${authRepository.isAuthenticated()}")
            
            // Validate vehicle ownership if vehicleId is provided
            if (mapping.vehicleId.isNotEmpty()) {
                Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                    "\n🚗 VEHICLE OWNERSHIP VALIDATION:")
                
                val vehicleResult = vehicleRepository.getVehicleById(mapping.vehicleId)
                when (vehicleResult) {
                    is Result.Success -> {
                        val vehicle = vehicleResult.data
                        if (vehicle != null) {
                            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                                "  • Vehicle Found: ${vehicle.name} (${vehicle.plateNumber})")
                            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                                "  • Vehicle Owner (userId): ${vehicle.userId}")
                            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                                "  • Ownership Match: ${currentUserId == vehicle.userId}")
                            
                            // Check if current user can manage this vehicle
                            val canManageVehicle = currentUserRole == User.UserRole.ADMIN || 
                                                 currentUserRole == User.UserRole.EMPLOYEE ||
                                                 currentUserId == vehicle.userId
                            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                                "  • Can Manage Vehicle: $canManageVehicle")
                        } else {
                            Log.w("FirebaseVehicleServiceMappingRepositoryImpl", 
                                "  • Vehicle NOT FOUND for ID: ${mapping.vehicleId}")
                        }
                    }
                    is Result.Failure -> {
                        Log.w("FirebaseVehicleServiceMappingRepositoryImpl", 
                            "  • Failed to fetch vehicle: ${vehicleResult.message}")
                    }
                }
            }
            
            // Permission rule analysis based on Firebase security rules
            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                "\n🔒 PERMISSION RULE ANALYSIS:")
            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                "  • Firebase Rules for vehicle_service_mappings:")
            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                "  • User can create mappings for themselves: ${currentUserId == mapping.userId}")
            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                "  • Admin/Employee permissions: ${currentUserRole == User.UserRole.ADMIN || currentUserRole == User.UserRole.EMPLOYEE}")
            
            Log.d("FirebaseVehicleServiceMappingRepositoryImpl", 
                "----------------------------------------")
        } catch (e: Exception) {
            Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
                "❌ ERROR during permission analysis: ${e.message}", e)
        }
    }

    /**
     * Logs comprehensive error information when mapping creation fails due to Firebase exceptions.
     */
    private suspend fun logMappingCreationFailure(
        mapping: VehicleServiceMapping, 
        exception: FirebaseFirestoreException
    ) {
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            """
            ❌ FAILED TO CREATE VehicleServiceMapping:
            ---------------------------------------
            📋 MAPPING DATA:
              • vehicleId: ${mapping.vehicleId}
              • serviceId: ${mapping.serviceId}
              • userId (in mapping): ${mapping.userId}
              • serviceName: ${mapping.serviceName}
            
            🔐 USER CONTEXT AT FAILURE:
            """.trimIndent())
        
        val currentUserId = authRepository.getCurrentUserId()
        val currentUserRole = authRepository.getCurrentUserRole()
        
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            "  • Current User ID: ${currentUserId ?: "NULL"}")
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            "  • Current User Role: ${currentUserRole ?: "NULL"}")
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            "  • User Authenticated: ${authRepository.isAuthenticated()}")
        
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            "\n🔥 FIREBASE ERROR DETAILS:")
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            "  • Error Type: ${exception.javaClass.simpleName}")
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            "  • Error Message: ${exception.message}")
        
        // Analyze error code for specific permission issues
        val errorMessage = exception.message ?: "Unknown error"
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            "\n🔍 ERROR ANALYSIS:")
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            "  • Contains PERMISSION_DENIED: ${errorMessage.contains("PERMISSION_DENIED")}")
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            "  • Contains UNAUTHENTICATED: ${errorMessage.contains("UNAUTHENTICATED")}")
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            "  • Contains NOT_FOUND: ${errorMessage.contains("NOT_FOUND")}")
        
        android.util.Log.e("FirebaseVehicleServiceMappingRepositoryImpl", 
            "--------------------------------------")
    }

    /**
     * Provides detailed error messages based on Firebase exception analysis.
     * This helps users understand exactly why their permission was denied.
     */
    private suspend fun detailedPermissionErrorMessage(
        exception: FirebaseFirestoreException, 
        mapping: VehicleServiceMapping
    ): String {
        val errorMessage = exception.message ?: "Unknown Firebase error"
        val currentUserId = authRepository.getCurrentUserId()
        val currentUserRole = authRepository.getCurrentUserRole()
        
        return when {
            errorMessage.contains("PERMISSION_DENIED") -> {
                """
                🔐 PERMISSION DENIED DETAILS:
                
                📋 Attempted Operation: Create VehicleServiceMapping
                🚗 Vehicle: ${mapping.vehicleId}
                🔧 Service: ${mapping.serviceId} (${mapping.serviceName})
                
                📊 PERMISSION ANALYSIS:
                • Current User ID: ${currentUserId ?: "Not authenticated"}
                • Current User Role: ${currentUserRole ?: "Unknown role"}
                • Mapping User ID: ${mapping.userId}
                • User Match: ${currentUserId == mapping.userId}
                • Admin Access: ${currentUserRole == User.UserRole.ADMIN}
                • Employee Access: ${currentUserRole == User.UserRole.EMPLOYEE}
                
                🔒 LIKELY CAUSES:
                • User authentication may have expired
                • Role permissions are insufficient
                • Vehicle ownership mismatch
                • Security rules configuration issue
                
                💡 SUGGESTIONS:
                • Re-authenticate if session expired
                • Contact administrator for role verification
                • Verify vehicle assignment belongs to current user
                """.trimIndent()
            }
            
            errorMessage.contains("UNAUTHENTICATED") -> {
                "Authentication required: Please sign in to create vehicle service mappings. " +
                "Current authentication status: ${if (authRepository.isAuthenticated()) "Authenticated" else "Not authenticated"}"
            }
            
            errorMessage.contains("NOT_FOUND") -> {
                "Resource not found: The vehicle or service may not exist. " +
                "Please verify that vehicle '${mapping.vehicleId}' and service '${mapping.serviceId}' exist in the system."
            }
            
            else -> {
                "Firestore error: ${exception.message}. " +
                "Please check your network connection and try again."
            }
        }
    }
}
