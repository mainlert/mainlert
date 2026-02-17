package com.mainlert.data.models

import com.google.firebase.firestore.PropertyName

/**
 * User data model for MainLert app.
 * Represents a user with role-based access control.
 */
data class User(
    var userId: String = "",
    var email: String = "",
    var name: String = "",
    var role: UserRole = UserRole.DRIVER,
    
    // DRIVER: Multiple vehicles
    var vehicleIds: List<String> = emptyList(),
    
    // EMPLOYEE: Multiple managed drivers
    var managedDriverIds: List<String> = emptyList(),
    
    // EMPLOYEE: Who manages this employee (for hierarchy)
    var managerId: String = "",
    
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,
    var createdAt: Long = System.currentTimeMillis(),
    var lastLoginAt: Long = 0L,
) {
    enum class UserRole {
        DRIVER,
        EMPLOYEE,
        ADMIN,
    }

    fun toMap(): Map<String, Any?> {
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
}
