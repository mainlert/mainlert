package com.mainlert.data.utils

import com.google.firebase.firestore.DocumentSnapshot
import com.mainlert.data.models.*
import com.mainlert.data.models.Service.ServiceStatus
import com.mainlert.data.models.User.UserRole
import com.mainlert.data.models.Vehicle.VehicleStatus
import com.mainlert.data.models.VehicleServiceMapping.MappingStatus
import com.mainlert.data.local.entities.VehicleServiceMappingEntity
import com.mainlert.data.local.entities.ServiceEntity
import com.mainlert.data.local.entities.VehicleEntity
import com.mainlert.data.local.entities.toVehicleEntity
import com.mainlert.data.local.entities.toServiceEntity

/**
 * Data mapping extension functions for converting between different data layers.
 * Provides toFirebaseMap(), toDomain(), toEntity() and toVehicleEntity/toServiceEntity extensions.
 */

// VehicleServiceMapping extensions
fun VehicleServiceMapping.toFirebaseMap(): Map<String, Any?> {
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

fun VehicleServiceMapping.toMappingFirebaseMap(): Map<String, Any?> {
    return toFirebaseMap()
}

// Vehicle extensions
fun Vehicle.toFirebaseMap(): Map<String, Any?> {
    return mapOf(
        "userId" to userId,
        "employeeId" to employeeId,
        "name" to name,
        "plateNumber" to plateNumber,
        "status" to status.name,
        "createdAt" to createdAt,
        "lifetimeMileage" to lifetimeMileage,
    )
}

fun Vehicle.toVehicleFirebaseMap(): Map<String, Any?> {
    return toFirebaseMap()
}

fun Vehicle.toDomain(): Vehicle {
    return this
}

fun DocumentSnapshot.toVehicle(): Vehicle {
    val vehicle = Vehicle()
    vehicle.id = this.id
    vehicle.userId = this.getString("userId") ?: ""
    vehicle.employeeId = this.getString("employeeId") ?: ""
    vehicle.name = this.getString("name") ?: ""
    vehicle.plateNumber = this.getString("plateNumber") ?: ""
    vehicle.status = VehicleStatus.valueOf(this.getString("status") ?: "ACTIVE")
    vehicle.createdAt = this.getLong("createdAt") ?: 0L
    vehicle.lifetimeMileage = this.getDouble("lifetimeMileage")?.toFloat() ?: 0f
    return vehicle
}

// Service extensions
fun Service.toFirebaseMap(): Map<String, Any?> {
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

fun Service.toServiceFirebaseMap(): Map<String, Any?> {
    return toFirebaseMap()
}

fun Service.toDomain(): Service {
    return this
}

fun DocumentSnapshot.toService(): Service {
    val service = Service()
    service.id = this.id
    service.variantId = this.getString("variantId") ?: ""
    service.variantName = this.getString("variantName") ?: ""
    service.serviceType = this.getString("serviceType") ?: ""
    service.name = this.getString("name") ?: ""
    service.customName = this.getString("customName") ?: ""
    service.description = this.getString("description") ?: ""
    service.status = ServiceStatus.valueOf(this.getString("status") ?: "ACTIVE")
    service.createdAt = this.getLong("createdAt") ?: System.currentTimeMillis()
    service.userId = this.getString("userId") ?: ""
    service.mileageLimit = this.getDouble("mileageLimit")?.toFloat() ?: 1000f
    return service
}


fun DocumentSnapshot.toVehicleServiceMapping(): VehicleServiceMapping {
    val mapping = VehicleServiceMapping()
    mapping.id = this.id
    mapping.vehicleId = this.getString("vehicleId") ?: ""
    mapping.serviceId = this.getString("serviceId") ?: ""
    mapping.serviceName = this.getString("serviceName") ?: ""
    mapping.variantId = this.getString("variantId") ?: ""
    mapping.variantName = this.getString("variantName") ?: ""
    mapping.status = MappingStatus.valueOf(this.getString("status") ?: MappingStatus.ACTIVE.name)
    mapping.totalMovement = this.getDouble("totalMovement")?.toFloat() ?: 0f
    mapping.isMonitoring = this.getBoolean("isMonitoring") ?: false
    mapping.lastReadingTime = this.getLong("lastReadingTime") ?: 0L
    mapping.mileageLimit = this.getDouble("mileageLimit")?.toFloat() ?: 1000f
    mapping.userId = this.getString("userId") ?: ""
    mapping.createdAt = this.getLong("createdAt") ?: System.currentTimeMillis()
    return mapping
}

// User extensions
fun User.toFirebaseMap(): Map<String, Any?> {
    return mapOf(
        "userId" to userId,
        "email" to email,
        "name" to name,
        "role" to role.name,
        "vehicleIds" to vehicleIds,
        "managedDriverIds" to managedDriverIds,
        "managerId" to managerId,
        "isActive" to isActive,
        "createdAt" to createdAt,
        "lastLoginAt" to lastLoginAt,
    )
}

fun User.toDomain(): User {
    return this
}

fun DocumentSnapshot.toUser(): User {
    val user = User()
    user.userId = this.getString("userId") ?: ""
    user.email = this.getString("email") ?: ""
    user.name = this.getString("name") ?: ""
    user.role = UserRole.valueOf(this.getString("role") ?: UserRole.DRIVER.name)
    user.vehicleIds = (this.get("vehicleIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    user.managedDriverIds = (this.get("managedDriverIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    user.managerId = this.getString("managerId") ?: ""
    user.isActive = this.getBoolean("isActive") ?: true
    user.createdAt = this.getLong("createdAt") ?: System.currentTimeMillis()
    user.lastLoginAt = this.getLong("lastLoginAt") ?: 0L
    return user
}

// ServiceVariant extensions
fun ServiceVariant.toFirebaseMap(): Map<String, Any?> {
    return mapOf(
        "name" to name,
        "description" to description,
        "mileageLimit" to mileageLimit,
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "isActive" to isActive,
    )
}

fun ServiceVariant.toDomain(): ServiceVariant {
    return this
}

fun DocumentSnapshot.toServiceVariant(): ServiceVariant {
    val variant = ServiceVariant()
    variant.id = this.id
    variant.name = this.getString("name") ?: ""
    variant.description = this.getString("description") ?: ""
    variant.mileageLimit = this.getDouble("mileageLimit")?.toFloat() ?: 1000f
    variant.createdBy = this.getString("createdBy") ?: ""
    variant.createdAt = this.getLong("createdAt") ?: System.currentTimeMillis()
    variant.isActive = this.getBoolean("isActive") ?: true
    return variant
}
