package com.mainlert.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mainlert.data.local.dao.VehicleDao
import com.mainlert.data.local.dao.ServiceDao
import com.mainlert.data.local.dao.VehicleServiceMappingDao
import com.mainlert.data.local.entities.VehicleEntity
import com.mainlert.data.local.entities.ServiceEntity
import com.mainlert.data.local.entities.VehicleServiceMappingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Test class for LocalDatabase and DAO operations.
 * Tests the hierarchical sync system components.
 */
@RunWith(AndroidJUnit4::class)
class LocalDatabaseTest {
    
    private lateinit var database: LocalDatabase
    private lateinit var vehicleDao: VehicleDao
    private lateinit var serviceDao: ServiceDao
    private lateinit var mappingDao: VehicleServiceMappingDao
    
    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LocalDatabase::class.java
        ).build()
        
        vehicleDao = database.vehicleDao()
        serviceDao = database.serviceDao()
        mappingDao = database.mappingDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun testVehicleOperations() = runBlocking {
        // Test insert
        val vehicle = VehicleEntity(
            id = "vehicle1",
            userId = "user1",
            employeeId = "emp1",
            name = "Test Vehicle",
            plateNumber = "ABC123",
            status = "ACTIVE",
            createdAt = System.currentTimeMillis(),
            lifetimeMileage = 1000f
        )
        
        vehicleDao.insertVehicle(vehicle)
        
        // Test get
        val retrievedVehicle = vehicleDao.getVehicle("vehicle1")
        assertNotNull(retrievedVehicle)
        assertEquals("Test Vehicle", retrievedVehicle?.name)
        
        // Test update
        val updatedVehicle = vehicle.copy(name = "Updated Vehicle")
        vehicleDao.updateVehicle(updatedVehicle)
        
        val updatedRetrieved = vehicleDao.getVehicle("vehicle1")
        assertEquals("Updated Vehicle", updatedRetrieved?.name)
    }
    
    @Test
    fun testServiceOperations() = runBlocking {
        // Test insert
        val service = ServiceEntity(
            id = "service1",
            variantId = "variant1",
            variantName = "Test Variant",
            serviceType = "OIL_CHANGE",
            name = "Test Service",
            customName = "",
            description = "Test Description",
            status = "ACTIVE",
            createdAt = System.currentTimeMillis(),
            userId = "user1",
            mileageLimit = 5000f
        )
        
        serviceDao.insertService(service)
        
        // Test get
        val retrievedService = serviceDao.getService("service1")
        assertNotNull(retrievedService)
        assertEquals("Test Service", retrievedService?.name)
        
        // Test update
        val updatedService = service.copy(name = "Updated Service")
        serviceDao.updateService(updatedService)
        
        val updatedRetrieved = serviceDao.getService("service1")
        assertEquals("Updated Service", updatedRetrieved?.name)
    }
    
    @Test
    fun testMappingOperations() = runBlocking {
        // Test insert
        val mapping = VehicleServiceMappingEntity(
            id = "mapping1",
            vehicleId = "vehicle1",
            serviceId = "service1",
            serviceName = "Test Service",
            variantId = "variant1",
            variantName = "Test Variant",
            status = "ACTIVE",
            totalMovement = 100f,
            isMonitoring = false,
            lastReadingTime = System.currentTimeMillis(),
            mileageLimit = 5000f,
            userId = "user1",
            createdAt = System.currentTimeMillis(),
            localLastUpdated = System.currentTimeMillis(),
            firebaseLastUpdated = 0L
        )
        
        mappingDao.insertMapping(mapping)
        
        // Test get
        val retrievedMapping = mappingDao.getMapping("mapping1")
        assertNotNull(retrievedMapping)
        assertEquals(100f, retrievedMapping?.totalMovement)
        
        // Test update movement
        val newMovement = 200f
        val timestamp = System.currentTimeMillis()
        mappingDao.updateMovement("mapping1", newMovement, timestamp)
        
        val updatedMapping = mappingDao.getMapping("mapping1")
        assertEquals(newMovement, updatedMapping?.totalMovement)
        assertEquals(timestamp, updatedMapping?.localLastUpdated)
    }
    
    @Test
    fun testConflictResolutionFields() = runBlocking {
        val mapping = VehicleServiceMappingEntity(
            id = "mapping1",
            vehicleId = "vehicle1",
            serviceId = "service1",
            serviceName = "Test Service",
            variantId = "variant1",
            variantName = "Test Variant",
            status = "ACTIVE",
            totalMovement = 100f,
            isMonitoring = false,
            lastReadingTime = System.currentTimeMillis(),
            mileageLimit = 5000f,
            userId = "user1",
            createdAt = System.currentTimeMillis(),
            localLastUpdated = System.currentTimeMillis() - 1000, // Older local update
            firebaseLastUpdated = System.currentTimeMillis() // Newer Firebase update
        )
        
        mappingDao.insertMapping(mapping)
        
        // Test conflict resolution logic
        val retrievedMapping = mappingDao.getMapping("mapping1")
        assertNotNull(retrievedMapping)
        
        // Firebase should win due to newer timestamp
        assertTrue(retrievedMapping?.firebaseLastUpdated ?: 0L > retrievedMapping?.localLastUpdated ?: 0L)
    }
    
    @Test
    fun testSyncStatusTracking() = runBlocking {
        val vehicle = VehicleEntity(
            id = "vehicle1",
            userId = "user1",
            employeeId = "emp1",
            name = "Test Vehicle",
            plateNumber = "ABC123",
            status = "ACTIVE",
            createdAt = System.currentTimeMillis(),
            lifetimeMileage = 1000f,
            lastSyncTime = 0L,
            isSynced = false
        )
        
        vehicleDao.insertVehicle(vehicle)
        
        // Test sync status update
        val syncTime = System.currentTimeMillis()
        vehicleDao.updateSyncStatus("vehicle1", syncTime, true)
        
        val updatedVehicle = vehicleDao.getVehicle("vehicle1")
        assertEquals(syncTime, updatedVehicle?.lastSyncTime)
        assertTrue(updatedVehicle?.isSynced ?: false)
    }
    
    @Test
    fun testMappingSyncDetection() = runBlocking {
        // Create mapping that needs sync (local is newer)
        val mapping1 = VehicleServiceMappingEntity(
            id = "mapping1",
            vehicleId = "vehicle1",
            serviceId = "service1",
            serviceName = "Test Service",
            variantId = "variant1",
            variantName = "Test Variant",
            status = "ACTIVE",
            totalMovement = 100f,
            isMonitoring = false,
            lastReadingTime = System.currentTimeMillis(),
            mileageLimit = 5000f,
            userId = "user1",
            createdAt = System.currentTimeMillis(),
            localLastUpdated = System.currentTimeMillis(),
            firebaseLastUpdated = System.currentTimeMillis() - 10000 // Older Firebase
        )
        
        // Create mapping that doesn't need sync (Firebase is newer)
        val mapping2 = VehicleServiceMappingEntity(
            id = "mapping2",
            vehicleId = "vehicle2",
            serviceId = "service2",
            serviceName = "Test Service 2",
            variantId = "variant2",
            variantName = "Test Variant 2",
            status = "ACTIVE",
            totalMovement = 200f,
            isMonitoring = false,
            lastReadingTime = System.currentTimeMillis(),
            mileageLimit = 6000f,
            userId = "user1",
            createdAt = System.currentTimeMillis(),
            localLastUpdated = System.currentTimeMillis() - 10000, // Older local
            firebaseLastUpdated = System.currentTimeMillis() // Newer Firebase
        )
        
        mappingDao.insertMapping(mapping1)
        mappingDao.insertMapping(mapping2)
        
        // Test get mappings needing sync
        val mappingsNeedingSync = mappingDao.getMappingsNeedingSync()
        
        // Only mapping1 should need sync (local is newer)
        assertEquals(1, mappingsNeedingSync.size)
        assertEquals("mapping1", mappingsNeedingSync.first().id)
    }
}