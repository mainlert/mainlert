package com.mainlert.data.models

import com.google.firebase.firestore.PropertyName

/**
 * Service data model for MainLert app.
 * This is a SERVICE TEMPLATE - it defines the service type but does NOT store readings.
 * Readings are stored in VehicleServiceMapping for each vehicle independently.
 * 
 * Example: "Economy Oil Change" is a template that can be assigned to multiple vehicles.
 * Each vehicle will have their own VehicleServiceMapping with independent readings.
 */
data class Service(
    var id: String = "",
    var variantId: String = "",
    var variantName: String = "",
    var serviceType: String = "",
    var name: String = "",
    var customName: String = "",
    var description: String = "",
    var status: ServiceStatus = ServiceStatus.ACTIVE,
    var createdAt: Long = System.currentTimeMillis(),
    var userId: String = "",
    // Default mileage limit - this becomes the default for new vehicle mappings
    var mileageLimit: Float = 1000f,
) {
    enum class ServiceStatus {
        ACTIVE,
        COMPLETED,
        CANCELLED,
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "variantId" to variantId,
            "variantName" to variantName,
            "serviceType" to serviceType,
            "name" to name,
            "customName" to customName,
            "description" to description,
            "status" to status.name,
            "createdAt" to createdAt,
            "userId" to userId,
            "mileageLimit" to mileageLimit,
        )
    }
}
