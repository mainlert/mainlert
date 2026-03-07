package com.mainlert.data.models

import com.google.firebase.firestore.PropertyName

/**
 * Vehicle data model for MainLert app.
 * Represents a vehicle owned/assigned to a driver.
 */
data class Vehicle(
    var id: String = "",
    var userId: String = "",
    var employeeId: String = "",
    var name: String = "",
    var plateNumber: String = "",
    var status: VehicleStatus = VehicleStatus.ACTIVE,
    var createdAt: Long = System.currentTimeMillis(),
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
            "plateNumber" to plateNumber,
            "status" to status.name,
            "createdAt" to createdAt,
        )
    }
}
