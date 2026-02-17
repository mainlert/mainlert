package com.mainlert.data.models

/**
 * ServiceVariant data model for MainLert app.
 * Represents a custom variant of a service with specific settings.
 */
data class ServiceVariant(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var mileageLimit: Float = 1000f,
    var createdBy: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var isActive: Boolean = true,
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "description" to description,
            "mileageLimit" to mileageLimit,
            "createdBy" to createdBy,
            "createdAt" to createdAt,
            "isActive" to isActive,
        )
    }
}
