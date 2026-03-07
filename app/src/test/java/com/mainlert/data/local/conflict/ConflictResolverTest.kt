package com.mainlert.data.local.conflict

import com.mainlert.data.local.entities.VehicleServiceMappingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Test class for ConflictResolver with dual-field conflict resolution.
 * Tests the hierarchical sync conflict resolution logic.
 */
class ConflictResolverTest {
    
    private lateinit var conflictResolver: ConflictResolver
    
    @Before
    fun setup() {
        conflictResolver = ConflictResolver()
    }
    
    @Test
    fun testDeviceWinsWithNewerTimestampAndHigherMovement() {
        val deviceData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis(),
            firebaseLastUpdated = System.currentTimeMillis() - 10000,
            totalMovement = 200f
        )
        
        val firebaseData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis() - 10000,
            firebaseLastUpdated = System.currentTimeMillis() - 15000,
            totalMovement = 150f
        )
        
        val resolvedData = conflictResolver.resolveConflict(deviceData, firebaseData)
        
        // Device should win - newer timestamp and higher movement
        assertEquals(deviceData.localLastUpdated, resolvedData.localLastUpdated)
        assertEquals(200f, resolvedData.totalMovement)
        assertTrue(resolvedData.firebaseLastUpdated > 0)
    }
    
    @Test
    fun testFirebaseWinsWithNewerTimestampAndHigherMovement() {
        val deviceData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis() - 15000,
            firebaseLastUpdated = System.currentTimeMillis() - 10000,
            totalMovement = 150f
        )
        
        val firebaseData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis() - 10000,
            firebaseLastUpdated = System.currentTimeMillis(),
            totalMovement = 200f
        )
        
        val resolvedData = conflictResolver.resolveConflict(deviceData, firebaseData)
        
        // Firebase should win - newer timestamp and higher movement
        assertEquals(firebaseData.firebaseLastUpdated, resolvedData.firebaseLastUpdated)
        assertEquals(200f, resolvedData.totalMovement)
        assertTrue(resolvedData.localLastUpdated > 0)
    }
    
    @Test
    fun testDeviceWinsWithSimilarTimestampsAndHigherMovement() {
        val currentTime = System.currentTimeMillis()
        
        val deviceData = createMappingEntity(
            localLastUpdated = currentTime,
            firebaseLastUpdated = currentTime - 1000,
            totalMovement = 200f
        )
        
        val firebaseData = createMappingEntity(
            localLastUpdated = currentTime - 1000,
            firebaseLastUpdated = currentTime,
            totalMovement = 150f
        )
        
        val resolvedData = conflictResolver.resolveConflict(deviceData, firebaseData)
        
        // Device should win - higher movement with similar timestamps
        assertEquals(deviceData.localLastUpdated, resolvedData.localLastUpdated)
        assertEquals(200f, resolvedData.totalMovement)
    }
    
    @Test
    fun testFirebaseWinsWithSimilarTimestampsAndHigherMovement() {
        val currentTime = System.currentTimeMillis()
        
        val deviceData = createMappingEntity(
            localLastUpdated = currentTime,
            firebaseLastUpdated = currentTime - 1000,
            totalMovement = 150f
        )
        
        val firebaseData = createMappingEntity(
            localLastUpdated = currentTime - 1000,
            firebaseLastUpdated = currentTime,
            totalMovement = 200f
        )
        
        val resolvedData = conflictResolver.resolveConflict(deviceData, firebaseData)
        
        // Firebase should win - higher movement with similar timestamps
        assertEquals(firebaseData.firebaseLastUpdated, resolvedData.firebaseLastUpdated)
        assertEquals(200f, resolvedData.totalMovement)
    }
    
    @Test
    fun testEdgeCaseDeviceNewerButLowerMovement() {
        val deviceData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis(),
            firebaseLastUpdated = System.currentTimeMillis() - 10000,
            totalMovement = 100f // Lower movement
        )
        
        val firebaseData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis() - 10000,
            firebaseLastUpdated = System.currentTimeMillis() - 15000,
            totalMovement = 200f // Higher movement
        )
        
        val resolvedData = conflictResolver.resolveConflict(deviceData, firebaseData)
        
        // Should prefer higher movement in edge case
        assertEquals(200f, resolvedData.totalMovement)
    }
    
    @Test
    fun testEdgeCaseFirebaseNewerButLowerMovement() {
        val deviceData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis() - 15000,
            firebaseLastUpdated = System.currentTimeMillis() - 10000,
            totalMovement = 200f // Higher movement
        )
        
        val firebaseData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis() - 10000,
            firebaseLastUpdated = System.currentTimeMillis(),
            totalMovement = 100f // Lower movement
        )
        
        val resolvedData = conflictResolver.resolveConflict(deviceData, firebaseData)
        
        // Should prefer higher movement in edge case
        assertEquals(200f, resolvedData.totalMovement)
    }
    
    @Test
    fun testMetricsTracking() = runBlocking {
        val deviceData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis(),
            firebaseLastUpdated = System.currentTimeMillis() - 10000,
            totalMovement = 200f
        )
        
        val firebaseData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis() - 10000,
            firebaseLastUpdated = System.currentTimeMillis() - 15000,
            totalMovement = 150f
        )
        
        // Resolve conflict
        conflictResolver.resolveConflict(deviceData, firebaseData)
        
        // Check metrics
        val metrics = conflictResolver.getMetrics()
        assertEquals(1, metrics.totalConflictsResolved)
        assertTrue(metrics.lastResolutionTime >= 0)
        assertTrue(metrics.lastResolutionTimestamp > 0)
        assertTrue(metrics.averageResolutionTime >= 0)
    }
    
    @Test
    fun testResetMetrics() = runBlocking {
        val deviceData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis(),
            firebaseLastUpdated = System.currentTimeMillis() - 10000,
            totalMovement = 200f
        )
        
        val firebaseData = createMappingEntity(
            localLastUpdated = System.currentTimeMillis() - 10000,
            firebaseLastUpdated = System.currentTimeMillis() - 15000,
            totalMovement = 150f
        )
        
        // Resolve conflict
        conflictResolver.resolveConflict(deviceData, firebaseData)
        
        // Reset metrics
        conflictResolver.resetMetrics()
        
        // Check metrics are reset
        val metrics = conflictResolver.getMetrics()
        assertEquals(0, metrics.totalConflictsResolved)
        assertEquals(0L, metrics.totalResolutionTime)
        assertEquals(0L, metrics.lastResolutionTime)
        assertEquals(0L, metrics.lastResolutionTimestamp)
    }
    
    @Test
    fun testMultipleConflictsAverageTime() = runBlocking {
        val deviceData1 = createMappingEntity(
            localLastUpdated = System.currentTimeMillis(),
            firebaseLastUpdated = System.currentTimeMillis() - 10000,
            totalMovement = 200f
        )
        
        val firebaseData1 = createMappingEntity(
            localLastUpdated = System.currentTimeMillis() - 10000,
            firebaseLastUpdated = System.currentTimeMillis() - 15000,
            totalMovement = 150f
        )
        
        val deviceData2 = createMappingEntity(
            localLastUpdated = System.currentTimeMillis(),
            firebaseLastUpdated = System.currentTimeMillis() - 10000,
            totalMovement = 300f
        )
        
        val firebaseData2 = createMappingEntity(
            localLastUpdated = System.currentTimeMillis() - 10000,
            firebaseLastUpdated = System.currentTimeMillis() - 15000,
            totalMovement = 250f
        )
        
        // Resolve multiple conflicts
        conflictResolver.resolveConflict(deviceData1, firebaseData1)
        conflictResolver.resolveConflict(deviceData2, firebaseData2)
        
        // Check average time
        val metrics = conflictResolver.getMetrics()
        assertEquals(2, metrics.totalConflictsResolved)
        assertTrue(metrics.averageResolutionTime >= 0)
    }
    
    /**
     * Helper method to create a VehicleServiceMappingEntity for testing.
     */
    private fun createMappingEntity(
        localLastUpdated: Long = System.currentTimeMillis(),
        firebaseLastUpdated: Long = System.currentTimeMillis(),
        totalMovement: Float = 100f
    ): VehicleServiceMappingEntity {
        return VehicleServiceMappingEntity(
            id = "test_mapping",
            vehicleId = "test_vehicle",
            serviceId = "test_service",
            serviceName = "Test Service",
            variantId = "test_variant",
            variantName = "Test Variant",
            status = "ACTIVE",
            totalMovement = totalMovement,
            isMonitoring = false,
            lastReadingTime = System.currentTimeMillis(),
            mileageLimit = 5000f,
            userId = "test_user",
            createdAt = System.currentTimeMillis(),
            localLastUpdated = localLastUpdated,
            firebaseLastUpdated = firebaseLastUpdated
        )
    }
}