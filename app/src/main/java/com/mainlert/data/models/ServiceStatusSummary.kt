package com.mainlert.data.models

/**
 * Service status summary data class for MainLert app.
 * Provides a comprehensive overview of service status and statistics.
 */
data class ServiceStatusSummary(
    val serviceId: String,
    val totalReadings: Int,
    val totalMovement: Float,
    val averageMovement: Float,
    val isMonitoring: Boolean,
    val lastReadingTime: Long,
    val isDeadlockDetected: Boolean,
)
