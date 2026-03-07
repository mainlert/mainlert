package com.mainlert.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentSnapshot
import com.mainlert.data.models.ServiceVariant

/**
 * Room entity for ServiceVariant data model.
 * Represents a custom variant of a service with specific settings.
 */
@Entity(tableName = "service_variants")
data class ServiceVariantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val mileageLimit: Float,
    val createdBy: String,
    val createdAt: Long,
    val isActive: Boolean,
    var lastSyncTime: Long,
    var isSynced: Boolean
)

/**
 * Extension function to convert ServiceVariantEntity to ServiceVariant domain model
 */
fun ServiceVariantEntity.toDomain(): ServiceVariant {
    return ServiceVariant(
        id = id,
        name = name,
        description = description,
        mileageLimit = mileageLimit,
        createdBy = createdBy,
        createdAt = createdAt,
        isActive = isActive
    )
}

/**
 * Extension function to convert ServiceVariant domain model to ServiceVariantEntity
 */
fun ServiceVariant.toServiceVariantEntity(): ServiceVariantEntity {
    return ServiceVariantEntity(
        id = id,
        name = name,
        description = description,
        mileageLimit = mileageLimit,
        createdBy = createdBy,
        createdAt = createdAt,
        isActive = isActive,
        lastSyncTime = 0L,
        isSynced = false
    )
}

/**
 * Extension function to convert ServiceVariantEntity to Firebase map
 */
fun ServiceVariantEntity.toVariantFirebaseMap(): Map<String, Any?> {
    return mapOf(
        "name" to name,
        "description" to description,
        "mileageLimit" to mileageLimit,
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "isActive" to isActive,
        "lastSyncTime" to lastSyncTime,
        "isSynced" to isSynced
    )
}

/**
 * Extension function to convert DocumentSnapshot to ServiceVariantEntity
 */
fun DocumentSnapshot.toServiceVariantEntity(): ServiceVariantEntity? {
    return if (exists()) {
        val data = data!!
        ServiceVariantEntity(
            id = id,
            name = data["name"] as? String ?: "",
            description = data["description"] as? String ?: "",
            mileageLimit = (data["mileageLimit"] as? Number)?.toFloat() ?: 1000f,
            createdBy = data["createdBy"] as? String ?: "",
            createdAt = data["createdAt"] as? Long ?: System.currentTimeMillis(),
            isActive = data["isActive"] as? Boolean ?: true,
            lastSyncTime = data["lastSyncTime"] as? Long ?: 0L,
            isSynced = data["isSynced"] as? Boolean ?: false
        )
    } else {
        null
    }
}
