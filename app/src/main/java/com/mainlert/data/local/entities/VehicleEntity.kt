package com.mainlert.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentSnapshot
import com.mainlert.data.models.Vehicle

/**
 * Room entity for Vehicle data model.
 * Represents a vehicle owned/assigned to a driver.
 */
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val employeeId: String,
    val name: String,
    val model: String,
    val year: Int,
    val plateNumber: String,
    val status: String, // VehicleStatus enum as String
    val createdAt: Long,
    val lifetimeMileage: Float,
    var lastSyncTime: Long,
    var isSynced: Boolean
)

/**
 * Extension function to convert VehicleEntity to Vehicle domain model
 */
fun VehicleEntity.toDomain(): Vehicle {
    return Vehicle(
        id = id,
        userId = userId,
        employeeId = employeeId,
        name = name,
        model = model,
        year = year,
        plateNumber = plateNumber,
        status = Vehicle.VehicleStatus.valueOf(status),
        createdAt = createdAt,
        lifetimeMileage = lifetimeMileage
    )
}

/**
 * Extension function to convert Vehicle domain model to VehicleEntity
 */
fun Vehicle.toVehicleEntity(): VehicleEntity {
    return VehicleEntity(
        id = id,
        userId = userId,
        employeeId = employeeId,
        name = name,
        model = model,
        year = year,
        plateNumber = plateNumber,
        status = status.name,
        createdAt = createdAt,
        lifetimeMileage = lifetimeMileage,
        lastSyncTime = 0L,
        isSynced = false
    )
}

/**
 * Extension function to convert VehicleEntity to Firebase map
 */
fun VehicleEntity.toVehicleFirebaseMap(): Map<String, Any?> {
    return mapOf(
        "userId" to userId,
        "employeeId" to employeeId,
        "name" to name,
        "model" to model,
        "year" to year,
        "plateNumber" to plateNumber,
        "status" to status,
        "createdAt" to createdAt,
        "lifetimeMileage" to lifetimeMileage,
        "lastSyncTime" to lastSyncTime,
        "isSynced" to isSynced
    )
}

/**
 * Extension function to convert DocumentSnapshot to VehicleEntity
 */
fun DocumentSnapshot.toVehicleEntity(): VehicleEntity? {
    return if (exists()) {
        val data = data!!
        VehicleEntity(
            id = id,
            userId = data["userId"] as? String ?: "",
            employeeId = data["employeeId"] as? String ?: "",
            name = data["name"] as? String ?: "",
            model = data["model"] as? String ?: "",
            year = data["year"] as? Int ?: 0,
            plateNumber = data["plateNumber"] as? String ?: "",
            status = data["status"] as? String ?: "ACTIVE",
            createdAt = data["createdAt"] as? Long ?: 0L,
            lifetimeMileage = (data["lifetimeMileage"] as? Double)?.toFloat() ?: 0f,
            lastSyncTime = data["lastSyncTime"] as? Long ?: 0L,
            isSynced = data["isSynced"] as? Boolean ?: false
        )
    } else {
        null
    }
}