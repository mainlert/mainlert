package com.mainlert.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mainlert.data.models.Result
import com.mainlert.data.models.Vehicle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firebase-based implementation of VehicleRepository.
 */
class FirebaseVehicleRepositoryImpl
    @Inject
    constructor() : VehicleRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val vehiclesCollection = firestore.collection("vehicles")

    override suspend fun getAllVehicles(): Result<List<Vehicle>> {
        return try {
            val querySnapshot =
                vehiclesCollection
                    .whereEqualTo("status", Vehicle.VehicleStatus.ACTIVE.name)
                    .get()
                    .await()

            val vehicles =
                querySnapshot.mapNotNull { document ->
                    document.toObject(Vehicle::class.java).apply {
                        id = document.id
                    }
                }

            Result.Success(vehicles)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure(e.message ?: "Failed to fetch vehicles")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch vehicles")
        }
    }

    override suspend fun getVehiclesForUser(userId: String): Result<List<Vehicle>> {
        return try {
            val querySnapshot =
                vehiclesCollection
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", Vehicle.VehicleStatus.ACTIVE.name)
                    .get()
                    .await()

            val vehicles =
                querySnapshot.mapNotNull { document ->
                    document.toObject(Vehicle::class.java).apply {
                        id = document.id
                    }
                }

            Result.Success(vehicles)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure(e.message ?: "Failed to fetch vehicles")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch vehicles")
        }
    }

    override suspend fun getVehiclesForEmployee(employeeId: String): Result<List<Vehicle>> {
        return try {
            val querySnapshot =
                vehiclesCollection
                    .whereEqualTo("employeeId", employeeId)
                    .whereEqualTo("status", Vehicle.VehicleStatus.ACTIVE.name)
                    .get()
                    .await()

            val vehicles =
                querySnapshot.mapNotNull { document ->
                    document.toObject(Vehicle::class.java).apply {
                        id = document.id
                    }
                }

            Result.Success(vehicles)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure(e.message ?: "Failed to fetch vehicles")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch vehicles")
        }
    }

    override suspend fun getVehicleById(vehicleId: String): Result<Vehicle> {
        return try {
            val document = vehiclesCollection.document(vehicleId).get().await()

            if (document.exists()) {
                val vehicle = document.toObject(Vehicle::class.java) ?: throw Exception("Vehicle data not found")
                Result.Success(vehicle.copy(id = vehicleId))
            } else {
                Result.Failure("Vehicle not found")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch vehicle")
        }
    }

    override suspend fun createVehicle(vehicle: Vehicle): Result<Vehicle> {
        return try {
            val vehicleData = vehicle.copy(id = "").toMap()
            val documentRef = vehiclesCollection.add(vehicleData).await()
            val newVehicle = vehicle.copy(id = documentRef.id)
            Result.Success(newVehicle)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to create vehicle")
        }
    }

    override suspend fun updateVehicle(vehicle: Vehicle): Result<Vehicle> {
        return try {
            if (vehicle.id.isEmpty()) {
                return Result.Failure("Vehicle ID is required")
            }
            val vehicleData = vehicle.toMap()
            vehiclesCollection.document(vehicle.id).set(vehicleData).await()
            Result.Success(vehicle)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to update vehicle")
        }
    }

    override suspend fun deleteVehicle(vehicleId: String): Result<Unit> {
        return try {
            vehiclesCollection.document(vehicleId).delete().await()
            Result.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to delete vehicle")
        }
    }

    override suspend fun assignVehicleToDriver(vehicleId: String, driverId: String): Result<Unit> {
        return try {
            vehiclesCollection.document(vehicleId).update(
                mapOf("userId" to driverId),
            ).await()
            Result.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to assign vehicle to driver")
        }
    }

    override suspend fun removeVehicleFromDriver(vehicleId: String): Result<Unit> {
        return try {
            vehiclesCollection.document(vehicleId).update(
                mapOf("userId" to ""),
            ).await()
            Result.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to remove vehicle assignment")
        }
    }

    override suspend fun getUnassignedVehicles(): Result<List<Vehicle>> {
        return try {
            val querySnapshot =
                vehiclesCollection
                    .whereEqualTo("userId", "")
                    .whereEqualTo("status", Vehicle.VehicleStatus.ACTIVE.name)
                    .get()
                    .await()

            val vehicles =
                querySnapshot.mapNotNull { document ->
                    document.toObject(Vehicle::class.java).apply {
                        id = document.id
                    }
                }

            Result.Success(vehicles)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure(e.message ?: "Failed to fetch unassigned vehicles")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch unassigned vehicles")
        }
    }

    override suspend fun createVehicleForDriver(
        vehicleName: String,
        plateNumber: String,
        driverId: String,
        employeeId: String,
    ): Result<Vehicle> {
        return try {
            // Create vehicle first
            val vehicle =
                Vehicle(
                    name = vehicleName,
                    plateNumber = plateNumber,
                    userId = driverId,
                    employeeId = employeeId,
                )

            val vehicleData = vehicle.copy(id = "").toMap()
            val documentRef = vehiclesCollection.add(vehicleData).await()
            val vehicleId = documentRef.id

            // Get the driver document to update their vehicleIds
            val usersCollection = firestore.collection("users")
            val driverDoc = usersCollection.document(driverId).get().await()

            if (driverDoc.exists()) {
                val driverData = driverDoc.data
                @Suppress("UNCHECKED_CAST")
                val currentVehicleIds = driverData?.get("vehicleIds") as? List<String> ?: emptyList()
                val updatedVehicleIds = currentVehicleIds + vehicleId

                // Update driver's vehicleIds
                usersCollection.document(driverId).update(
                    mapOf("vehicleIds" to updatedVehicleIds)
                ).await()
            }

            Result.Success(vehicle.copy(id = vehicleId))
        } catch (e: FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to create vehicle for driver")
        }
    }

    override fun observeVehiclesForUser(userId: String): Flow<List<Vehicle>> {
        return callbackFlow {
            val query = vehiclesCollection.whereEqualTo("userId", userId)

            val listener =
                query.addSnapshotListener { querySnapshot, exception ->
                    if (exception != null) {
                        channel.close(exception)
                        return@addSnapshotListener
                    }

                    val vehicles =
                        querySnapshot?.mapNotNull { document ->
                            document.toObject(Vehicle::class.java).apply {
                                id = document.id
                            }
                        } ?: emptyList()

                    channel.trySend(vehicles)
                }

            awaitClose {
                listener.remove()
            }
        }
    }
}
