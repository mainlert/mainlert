package com.mainlert.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentSnapshot
import com.mainlert.data.models.Service

/**
 * Room entity for Service data model.
 * This is a SERVICE TEMPLATE - it defines the service type but does NOT store readings.
 * Readings are stored in VehicleServiceMapping for each vehicle independently.
 */
@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey val id: String,
    val variantId: String,
    val variantName: String,
    val serviceType: String,
    val name: String,
    val customName: String,
    val description: String,
    val status: String, // ServiceStatus enum as String
    val createdAt: Long,
    val userId: String,
    val mileageLimit: Float,
    var lastSyncTime: Long,
    var isSynced: Boolean
)

/**
 * Extension function to convert ServiceEntity to Service domain model
 */
fun ServiceEntity.toDomain(): Service {
    return Service(
        id = id,
        variantId = variantId,
        variantName = variantName,
        serviceType = serviceType,
        name = name,
        customName = customName,
        description = description,
        status = Service.ServiceStatus.valueOf(status),
        createdAt = createdAt,
        userId = userId,
        mileageLimit = mileageLimit
    )
}

/**
 * Extension function to convert Service domain model to ServiceEntity
 */
fun Service.toServiceEntity(): ServiceEntity {
    return ServiceEntity(
        id = id,
        variantId = variantId,
        variantName = variantName,
        serviceType = serviceType,
        name = name,
        customName = customName,
        description = description,
        status = status.name,
        createdAt = createdAt,
        userId = userId,
        mileageLimit = mileageLimit,
        lastSyncTime = 0L,
        isSynced = false
    )
}

/**
 * Extension function to convert ServiceEntity to Firebase map
 */
fun ServiceEntity.toServiceFirebaseMap(): Map<String, Any?> {
    return mapOf(
        "variantId" to variantId,
        "variantName" to variantName,
        "serviceType" to serviceType,
        "name" to name,
        "customName" to customName,
        "description" to description,
        "status" to status,
        "createdAt" to createdAt,
        "userId" to userId,
        "mileageLimit" to mileageLimit,
        "lastSyncTime" to lastSyncTime,
        "isSynced" to isSynced
    )
}

/**
 * Extension function to convert DocumentSnapshot to ServiceEntity
 */
fun DocumentSnapshot.toServiceEntity(): ServiceEntity? {
    return if (exists()) {
        val data = data!!
        ServiceEntity(
            id = id,
            variantId = data["variantId"] as? String ?: "",
            variantName = data["variantName"] as? String ?: "",
            serviceType = data["serviceType"] as? String ?: "",
            name = data["name"] as? String ?: "",
            customName = data["customName"] as? String ?: "",
            description = data["description"] as? String ?: "",
            status = data["status"] as? String ?: "ACTIVE",
            createdAt = data["createdAt"] as? Long ?: System.currentTimeMillis(),
            userId = data["userId"] as? String ?: "",
            mileageLimit = (data["mileageLimit"] as? Number)?.toFloat() ?: 1000f,
            lastSyncTime = data["lastSyncTime"] as? Long ?: 0L,
            isSynced = data["isSynced"] as? Boolean ?: false
        )
    } else {
        null
    }
}