package com.mainlert.data.models

import com.google.firebase.firestore.PropertyName

/**
 * Service data model for MainLert app.
 * Represents a vehicle service with readings and status tracking.
 */
data class Service(
    var id: String = "",
    var vehicleIds: List<String> = emptyList(),
    var variantId: String = "",
    var variantName: String = "",
    var serviceType: String = "",
    var name: String = "",
    var customName: String = "",
    var description: String = "",
    var status: ServiceStatus = ServiceStatus.ACTIVE,
    var createdAt: Long = System.currentTimeMillis(),
    var totalMovement: Float = 0f,
    @get:PropertyName("isMonitoring")
    @set:PropertyName("isMonitoring")
    var isMonitoring: Boolean = false,
    var lastReadingTime: Long = 0L,
    var userId: String = "",
    // Default deadlock limit (total movement threshold)
    var deadlockLimit: Float = 1000f,
) {
    enum class ServiceStatus {
        ACTIVE,
        COMPLETED,
        CANCELLED,
    }

    /**
     * Returns the first vehicle ID for backward compatibility
     */
    val vehicleId: String
        get() = vehicleIds.firstOrNull() ?: ""

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "vehicleIds" to vehicleIds,
            "variantId" to variantId,
            "variantName" to variantName,
            "serviceType" to serviceType,
            "name" to name,
            "customName" to customName,
            "description" to description,
            "status" to status.name,
            "createdAt" to createdAt,
            "totalMovement" to totalMovement,
            "isMonitoring" to isMonitoring,
            "lastReadingTime" to lastReadingTime,
            "userId" to userId,
            "deadlockLimit" to deadlockLimit,
        )
    }
}
