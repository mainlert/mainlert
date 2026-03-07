package com.mainlert.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentSnapshot
import com.mainlert.data.models.VehicleServiceMapping
import com.mainlert.data.models.VehicleServiceMapping.MappingStatus

/**
 * Room entity for VehicleServiceMapping data model.
 * Represents the mapping between a vehicle and a service with monitoring data.
 */
@Entity(tableName = "vehicle_service_mappings")
data class VehicleServiceMappingEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val serviceId: String,
    val serviceName: String,
    val variantId: String,
    val variantName: String,
    val status: String, // MappingStatus enum as String
    val totalMovement: Float,
    val isMonitoring: Boolean,
    val lastReadingTime: Long,
    val mileageLimit: Float,
    val userId: String,
    val createdAt: Long,
    var localLastUpdated: Long,
    var firebaseLastUpdated: Long
) {
}

/**
 * Extension function to convert DocumentSnapshot to VehicleServiceMappingEntity
 */
fun DocumentSnapshot.toVehicleServiceMappingEntity(): VehicleServiceMappingEntity? {
    return if (exists()) {
        val data = data!!
        VehicleServiceMappingEntity(
            id = id,
            vehicleId = data["vehicleId"] as? String ?: "",
            serviceId = data["serviceId"] as? String ?: "",
            serviceName = data["serviceName"] as? String ?: "",
            variantId = data["variantId"] as? String ?: "",
            variantName = data["variantName"] as? String ?: "",
            status = data["status"] as? String ?: "ACTIVE",
            totalMovement = (data["totalMovement"] as? Double)?.toFloat() ?: 0f,
            isMonitoring = data["isMonitoring"] as? Boolean ?: false,
            lastReadingTime = data["lastReadingTime"] as? Long ?: 0L,
            mileageLimit = (data["mileageLimit"] as? Number)?.toFloat() ?: 0f,
            userId = data["userId"] as? String ?: "",
            createdAt = data["createdAt"] as? Long ?: 0L,
            localLastUpdated = data["localLastUpdated"] as? Long ?: 0L,
            firebaseLastUpdated = data["firebaseLastUpdated"] as? Long ?: 0L
        )
    } else {
        null
    }
}

/**
 * Extension function to convert VehicleServiceMappingEntity to Firebase map
 */
fun VehicleServiceMappingEntity.toMappingFirebaseMap(): Map<String, Any?> {
    return mapOf(
        "vehicleId" to vehicleId,
        "serviceId" to serviceId,
        "serviceName" to serviceName,
        "variantId" to variantId,
        "variantName" to variantName,
        "status" to status,
        "totalMovement" to totalMovement,
        "isMonitoring" to isMonitoring,
        "lastReadingTime" to lastReadingTime,
        "mileageLimit" to mileageLimit,
        "userId" to userId,
        "createdAt" to createdAt,
        "localLastUpdated" to localLastUpdated,
        "firebaseLastUpdated" to firebaseLastUpdated
    )
}

/**
  * Extension function to convert VehicleServiceMappingEntity to VehicleServiceMapping (domain)
  */
fun VehicleServiceMappingEntity.toDomain(): VehicleServiceMapping {
    return VehicleServiceMapping(
        id = id,
        vehicleId = vehicleId,
        serviceId = serviceId,
        serviceName = serviceName,
        variantId = variantId,
        variantName = variantName,
        status = MappingStatus.valueOf(status),
        totalMovement = totalMovement,
        isMonitoring = isMonitoring,
        lastReadingTime = lastReadingTime,
        mileageLimit = mileageLimit,
        userId = userId,
        createdAt = createdAt,
        lastUpdated = localLastUpdated,
        firebaseLastUpdated = firebaseLastUpdated,
        localLastUpdated = localLastUpdated
    )
}