package com.mainlert.data.models

/**
 * ServiceReading data model for MainLert app.
 * Represents accelerometer-based service readings for a vehicle.
 */
data class ServiceReading(
    var id: String = "",
    var serviceId: String = "",
    var userId: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var totalMovement: Float = 0f,
    var duration: Long = 0L,
    var isVehicleMovement: Boolean = false,
    var isCompleted: Boolean = false,
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "serviceId" to serviceId,
            "userId" to userId,
            "timestamp" to timestamp,
            "totalMovement" to totalMovement,
            "duration" to duration,
            "isVehicleMovement" to isVehicleMovement,
            "isCompleted" to isCompleted,
        )
    }
}
