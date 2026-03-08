package com.mainlert.data.models

import com.google.firebase.firestore.PropertyName
import com.mainlert.data.local.entities.VehicleEntity

/**
 * Vehicle data model for MainLert app.
 * Represents a vehicle owned/assigned to a driver.
 */
data class Vehicle(
    var id: String = "",
    var userId: String = "",
    var employeeId: String = "",
    var name: String = "",
    var model: String = "",
    var year: Int = 0,
    var plateNumber: String = "",
    var status: VehicleStatus = VehicleStatus.ACTIVE,
    var createdAt: Long = System.currentTimeMillis(),
    // Lifetime mileage - accumulates forever and never resets
    var lifetimeMileage: Float = 0f,
) {
    enum class VehicleStatus {
        ACTIVE,
        INACTIVE,
        SOLD,
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "userId" to userId,
            "employeeId" to employeeId,
            "name" to name,
            "model" to model,
            "year" to year,
            "plateNumber" to plateNumber,
            "status" to status.name,
            "createdAt" to createdAt,
            "lifetimeMileage" to lifetimeMileage,
        )
    }

    fun toVehicleEntity(): VehicleEntity {
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
}
