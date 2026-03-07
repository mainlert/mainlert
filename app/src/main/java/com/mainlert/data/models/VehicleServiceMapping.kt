package com.mainlert.data.models

import com.google.firebase.firestore.PropertyName
import com.mainlert.data.local.entities.VehicleServiceMappingEntity

/**
 * VehicleServiceMapping data model for MainLert app.
 * Represents the relationship between a vehicle and a service, storing independent readings.
 * This ensures each vehicle has its own independent reading for a shared service.
 * 
 * Example: If Economy Oil Change is assigned to Vehicle A and Vehicle B,
 * each will have their own VehicleServiceMapping with independent totalMovement values.
 */
data class VehicleServiceMapping(
    var id: String = "",
    var vehicleId: String = "",
    var serviceId: String = "",
    var serviceName: String = "",
    var variantId: String = "",
    var variantName: String = "",
    var status: MappingStatus = MappingStatus.ACTIVE,
    var totalMovement: Float = 0f,
    @get:PropertyName("isMonitoring")
    @set:PropertyName("isMonitoring")
    var isMonitoring: Boolean = false,
    var lastReadingTime: Long = 0L,
    var mileageLimit: Float = 1000f,
    var userId: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var lastUpdated: Long = System.currentTimeMillis(),
    var firebaseLastUpdated: Long = System.currentTimeMillis(),
    var localLastUpdated: Long = System.currentTimeMillis(),
) {
    enum class MappingStatus {
        ACTIVE,
        COMPLETED,
        CANCELLED,
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "vehicleId" to vehicleId,
            "serviceId" to serviceId,
            "serviceName" to serviceName,
            "variantId" to variantId,
            "variantName" to variantName,
            "status" to status.name,
            "totalMovement" to totalMovement,
            "isMonitoring" to isMonitoring,
            "lastReadingTime" to lastReadingTime,
            "mileageLimit" to mileageLimit,
            "userId" to userId,
            "createdAt" to createdAt,
            "lastUpdated" to lastUpdated,
            "firebaseLastUpdated" to firebaseLastUpdated,
            "localLastUpdated" to localLastUpdated,
        )
    }

    fun toEntity(): VehicleServiceMappingEntity {
        return VehicleServiceMappingEntity(
            id = id,
            vehicleId = vehicleId,
            serviceId = serviceId,
            serviceName = serviceName,
            variantId = variantId,
            variantName = variantName,
            status = status.name, // Convert enum to String
            totalMovement = totalMovement,
            isMonitoring = isMonitoring,
            lastReadingTime = lastReadingTime,
            mileageLimit = mileageLimit,
            userId = userId,
            createdAt = createdAt,
            localLastUpdated = lastUpdated,
            firebaseLastUpdated = firebaseLastUpdated
        )
    }
}
