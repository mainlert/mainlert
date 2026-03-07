## Complete Detailed Implementation Plan: Hierarchical Sync with Dual-Field Conflict Resolution

### **Phase 1: Architecture Foundation**

**1.1 System Architecture Overview**
```
┌─────────────────────────────────────────────────────────────┐
│                        Device Layer                         │
├─────────────────────────────────────────────────────────────┤
│  UI Layer  │  ViewModel  │  Repository  │  Local DB (Room)  │
├─────────────────────────────────────────────────────────────┤
│                    Sync Manager Layer                       │
├─────────────────────────────────────────────────────────────┤
│  Conflict  │  Structure  │  Network    │  Sync State        │
│  Resolver  │  Sync       │  Monitor    │  Tracker            │
├─────────────────────────────────────────────────────────────┤
│                      Firebase Layer                         │
├─────────────────────────────────────────────────────────────┤
│  Real-time  │  Structure  │  Timestamps  │  Authentication   │
│  Database   │  Sync       │              │                  │
└─────────────────────────────────────────────────────────────┘
```

**1.2 Data Flow Architecture**
```
Device Operations:
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Local DB      │◄──►│   Sync Manager   │◄──►│   Firebase      │
│   (Room)        │    │                  │    │   (Firestore)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Business      │    │   Conflict       │    │   Real-time     │
│   Logic         │    │   Resolution     │    │   Sync          │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### **Phase 2: Core Components Implementation**

**2.1 Local Database Layer (Room)**

**Entities:**
```kotlin
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val plateNumber: String,
    val model: String,
    val year: Int,
    val lastSyncTime: Long = 0L,
    val isSynced: Boolean = false
)

@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val variantId: String?,
    val vehicleId: String,
    val mileageLimit: Int,
    val lastSyncTime: Long = 0L,
    val isSynced: Boolean = false
)

@Entity(tableName = "vehicle_service_mappings")
data class VehicleServiceMappingEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val serviceId: String,
    val userId: String,
    val serviceName: String,
    val variantId: String?,
    val variantName: String,
    val totalMovement: Float = 0f,
    val isMonitoring: Boolean = false,
    val status: String, // ACTIVE, INACTIVE, COMPLETED
    val lastReadingTime: Long = 0L,
    val mileageLimit: Int,
    val localLastUpdated: Long = 0L,
    val firebaseLastUpdated: Long = 0L
)
```

**DAO Interfaces:**
```kotlin
@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicle(id: String): VehicleEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity)
    
    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)
    
    @Query("SELECT * FROM vehicles WHERE lastSyncTime < :threshold")
    suspend fun getVehiclesNeedingSync(threshold: Long): List<VehicleEntity>
}

@Dao
interface ServiceDao {
    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun getService(id: String): ServiceEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: ServiceEntity)
    
    @Update
    suspend fun updateService(service: ServiceEntity)
    
    @Query("SELECT * FROM services WHERE lastSyncTime < :threshold")
    suspend fun getServicesNeedingSync(threshold: Long): List<ServiceEntity>
}

@Dao
interface VehicleServiceMappingDao {
    @Query("SELECT * FROM vehicle_service_mappings WHERE id = :id")
    suspend fun getMapping(id: String): VehicleServiceMappingEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: VehicleServiceMappingEntity)
    
    @Update
    suspend fun updateMapping(mapping: VehicleServiceMappingEntity)
    
    @Query("UPDATE vehicle_service_mappings SET totalMovement = :movement, localLastUpdated = :timestamp WHERE id = :mappingId")
    suspend fun updateMovement(mappingId: String, movement: Float, timestamp: Long)
    
    @Query("SELECT * FROM vehicle_service_mappings WHERE localLastUpdated > firebaseLastUpdated")
    suspend fun getMappingsNeedingSync(): List<VehicleServiceMappingEntity>
}
```

**2.2 Sync Manager Service**

**Core Sync Manager:**
```kotlin
class SyncManager @Inject constructor(
    private val firebaseService: FirebaseSyncService,
    private val conflictResolver: ConflictResolver,
    private val networkMonitor: NetworkMonitor,
    private val localDatabase: LocalDatabase
) {
    
    suspend fun syncOnMonitoringStart() {
        // 1. Sync vehicle/service structure
        syncStructureData()
        
        // 2. Sync any pending movement data
        syncPendingMovements()
    }
    
    suspend fun syncContinuousData() {
        if (!networkMonitor.isOnline()) return
        
        val mappings = localDatabase.mappingDao().getMappingsNeedingSync()
        mappings.forEach { mapping ->
            syncMappingData(mapping)
        }
    }
    
    private suspend fun syncStructureData() {
        // Sync vehicles
        val vehicles = localDatabase.vehicleDao().getVehiclesNeedingSync(System.currentTimeMillis() - 24.hours.inWholeMilliseconds)
        vehicles.forEach { vehicle ->
            firebaseService.syncVehicle(vehicle)
        }
        
        // Sync services
        val services = localDatabase.serviceDao().getServicesNeedingSync(System.currentTimeMillis() - 24.hours.inWholeMilliseconds)
        services.forEach { service ->
            firebaseService.syncService(service)
        }
    }
    
    private suspend fun syncMappingData(mapping: VehicleServiceMappingEntity) {
        try {
            val firebaseData = firebaseService.getMapping(mapping.id)
            if (firebaseData != null) {
                val resolvedData = conflictResolver.resolveConflict(
                    deviceData = mapping,
                    firebaseData = firebaseData
                )
                firebaseService.updateMapping(resolvedData)
                localDatabase.mappingDao().updateMapping(resolvedData)
            } else {
                firebaseService.createMapping(mapping)
                localDatabase.mappingDao().updateMapping(mapping.copy(firebaseLastUpdated = System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            // Handle sync failure
            handleSyncFailure(mapping, e)
        }
    }
}
```

**2.3 Conflict Resolution Engine**

**Dual-Field Conflict Resolver:**
```kotlin
class ConflictResolver {
    
    fun resolveConflict(deviceData: VehicleServiceMappingEntity, firebaseData: VehicleServiceMappingEntity): VehicleServiceMappingEntity {
        // Priority 1: Timestamp comparison with tolerance
        val timeDiff = abs(deviceData.localLastUpdated - firebaseData.firebaseLastUpdated)
        
        return when {
            // Case 1: Device has significantly newer timestamp (more than 5 minutes)
            deviceData.localLastUpdated > firebaseData.firebaseLastUpdated + 5.minutes.inWholeMilliseconds -> {
                if (deviceData.totalMovement >= firebaseData.totalMovement) {
                    deviceData.copy(firebaseLastUpdated = System.currentTimeMillis())
                } else {
                    handleEdgeCase(deviceData, firebaseData)
                }
            }
            
            // Case 2: Firebase has significantly newer timestamp
            firebaseData.firebaseLastUpdated > deviceData.localLastUpdated + 5.minutes.inWholeMilliseconds -> {
                if (firebaseData.totalMovement >= deviceData.totalMovement) {
                    firebaseData.copy(localLastUpdated = System.currentTimeMillis())
                } else {
                    handleEdgeCase(firebaseData, deviceData)
                }
            }
            
            // Case 3: Similar timestamps, compare totalMovement
            timeDiff <= 5.minutes.inWholeMilliseconds -> {
                if (deviceData.totalMovement > firebaseData.totalMovement) {
                    deviceData.copy(firebaseLastUpdated = System.currentTimeMillis())
                } else {
                    firebaseData.copy(localLastUpdated = System.currentTimeMillis())
                }
            }
            
            // Case 4: Edge case - handle based on business logic
            else -> {
                handleEdgeCase(deviceData, firebaseData)
            }
        }
    }
    
    private fun handleEdgeCase(deviceData: VehicleServiceMappingEntity, firebaseData: VehicleServiceMappingEntity): VehicleServiceMappingEntity {
        // Business logic for edge cases
        return if (deviceData.totalMovement >= firebaseData.totalMovement) {
            deviceData.copy(firebaseLastUpdated = System.currentTimeMillis())
        } else {
            firebaseData.copy(localLastUpdated = System.currentTimeMillis())
        }
    }
}
```

**2.4 Network Monitor Service**

**Network State Management:**
```kotlin
class NetworkMonitor @Inject constructor(
    private val context: Context
) {
    
    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    
    fun observeNetworkState(): Flow<Boolean> {
        return callbackFlow {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(true)
                }
                
                override fun onLost(network: Network) {
                    trySend(false)
                }
            }
            
            connectivityManager.registerDefaultNetworkCallback(callback)
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }
}
```

### **Phase 3: Repository Layer Updates**

**3.1 Enhanced VehicleServiceMappingRepository**

**Repository Interface:**
```kotlin
interface VehicleServiceMappingRepository {
    suspend fun getMapping(id: String): VehicleServiceMapping?
    suspend fun createMapping(mapping: VehicleServiceMapping): String
    suspend fun updateMapping(mapping: VehicleServiceMapping)
    suspend fun updateMovement(mappingId: String, movement: Float)
    suspend fun getMappingsForVehicle(vehicleId: String): List<VehicleServiceMapping>
    suspend fun syncStructureData()
    suspend fun syncContinuousData()
}
```

**Repository Implementation:**
```kotlin
class VehicleServiceMappingRepositoryImpl @Inject constructor(
    private val localDatabase: LocalDatabase,
    private val firebaseService: FirebaseSyncService,
    private val syncManager: SyncManager
) : VehicleServiceMappingRepository {
    
    override suspend fun getMapping(id: String): VehicleServiceMapping? {
        return localDatabase.mappingDao().getMapping(id)?.toDomain()
    }
    
    override suspend fun createMapping(mapping: VehicleServiceMapping): String {
        val entity = mapping.toEntity()
        localDatabase.mappingDao().insertMapping(entity)
        syncManager.syncContinuousData()
        return entity.id
    }
    
    override suspend fun updateMovement(mappingId: String, movement: Float) {
        val timestamp = System.currentTimeMillis()
        localDatabase.mappingDao().updateMovement(mappingId, movement, timestamp)
        syncManager.syncContinuousData()
    }
    
    override suspend fun syncStructureData() {
        syncManager.syncOnMonitoringStart()
    }
    
    override suspend fun syncContinuousData() {
        syncManager.syncContinuousData()
    }
}
```

### **Phase 4: ViewModel Layer Updates**

**4.1 Enhanced DashboardViewModel**

**Updated ViewModel:**
```kotlin
class DashboardViewModel @ViewModelInject constructor(
    private val vehicleServiceMappingRepository: VehicleServiceMappingRepository,
    private val syncManager: SyncManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    
    private val _syncState = MutableLiveData<SyncState>()
    val syncState: LiveData<SyncState> = _syncState
    
    init {
        observeNetworkChanges()
    }
    
    private fun observeNetworkChanges() {
        viewModelScope.launch {
            networkMonitor.observeNetworkState().collect { isOnline ->
                if (isOnline) {
                    _syncState.value = SyncState.Syncing
                    vehicleServiceMappingRepository.syncContinuousData()
                    _syncState.value = SyncState.Synced
                } else {
                    _syncState.value = SyncState.Offline
                }
            }
        }
    }
    
    fun startMonitoring() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            vehicleServiceMappingRepository.syncStructureData()
            _syncState.value = SyncState.Synced
        }
    }
    
    fun updateServiceMovement(serviceId: String, movement: Float) {
        viewModelScope.launch {
            vehicleServiceMappingRepository.updateMovement(serviceId, movement)
        }
    }
}
```

**4.2 Sync State Management**

**Sync State Enum:**
```kotlin
sealed class SyncState {
    object Offline : SyncState()
    object Syncing : SyncState()
    object Synced : SyncState()
    data class Error(val message: String) : SyncState()
}
```

### **Phase 5: Firebase Integration**

**5.1 Firebase Sync Service**

**Firebase Operations:**
```kotlin
class FirebaseSyncService @Inject constructor(
    private val firebaseFirestore: FirebaseFirestore
) {
    
    suspend fun syncVehicle(vehicle: VehicleEntity) {
        firebaseFirestore.collection("vehicles")
            .document(vehicle.id)
            .set(vehicle.toFirebaseMap())
            .await()
    }
    
    suspend fun syncService(service: ServiceEntity) {
        firebaseFirestore.collection("services")
            .document(service.id)
            .set(service.toFirebaseMap())
            .await()
    }
    
    suspend fun getMapping(id: String): VehicleServiceMappingEntity? {
        val document = firebaseFirestore.collection("vehicle_service_mappings")
            .document(id)
            .get()
            .await()
        
        return if (document.exists()) {
            document.toObject(VehicleServiceMappingEntity::class.java)
        } else {
            null
        }
    }
    
    suspend fun updateMapping(mapping: VehicleServiceMappingEntity) {
        firebaseFirestore.collection("vehicle_service_mappings")
            .document(mapping.id)
            .update(
                "totalMovement", mapping.totalMovement,
                "firebaseLastUpdated", System.currentTimeMillis()
            )
            .await()
    }
    
    suspend fun createMapping(mapping: VehicleServiceMappingEntity) {
        firebaseFirestore.collection("vehicle_service_mappings")
            .document(mapping.id)
            .set(mapping.toFirebaseMap())
            .await()
    }
}
```

### **Phase 6: Implementation Timeline**

**Week 1: Foundation**
- Day 1-2: Set up Room database entities and DAOs
- Day 3-4: Implement core Sync Manager and Conflict Resolver
- Day 5: Create Network Monitor service

**Week 2: Repository & ViewModel**
- Day 1-2: Update repository layer with sync capabilities
- Day 3-4: Enhance ViewModel with sync state management
- Day 5: Implement sync triggers and callbacks

**Week 3: Firebase Integration**
- Day 1-2: Implement Firebase sync service
- Day 3-4: Add real-time listeners and conflict detection
- Day 5: Test sync operations and error handling

**Week 4: Testing & Optimization**
- Day 1-2: Unit tests for conflict resolution
- Day 3-4: Integration tests for multi-device scenarios
- Day 5: Performance optimization and monitoring

### **Phase 7: Error Handling & Edge Cases**

**7.1 Sync Failure Handling**
```kotlin
private suspend fun handleSyncFailure(mapping: VehicleServiceMappingEntity, error: Exception) {
    // Log error
    Timber.e(error, "Sync failed for mapping: ${mapping.id}")
    
    // Queue for retry
    syncQueue.add(mapping)
    
    // Notify UI
    _syncState.value = SyncState.Error("Sync failed: ${error.message}")
    
    // Retry after delay
    delay(5000)
    if (networkMonitor.isOnline()) {
        syncMappingData(mapping)
    }
}
```

**7.2 Data Validation**
```kotlin
private fun validateMappingData(mapping: VehicleServiceMappingEntity): Boolean {
    return mapping.totalMovement >= 0f &&
           mapping.mileageLimit > 0 &&
           mapping.serviceName.isNotBlank() &&
           mapping.vehicleId.isNotBlank()
}
```

### **Phase 8: Monitoring & Analytics**

**8.1 Sync Metrics**
```kotlin
data class SyncMetrics(
    val syncStartTime: Long,
    val syncEndTime: Long,
    val itemsSynced: Int,
    val conflictsResolved: Int,
    val errors: List<String>
)
```

**8.2 Performance Monitoring**
- Sync operation timing
- Conflict resolution frequency
- Network usage optimization
- Database query performance

### **Phase 9: Testing Strategy**

**9.1 Unit Tests**
- Conflict resolution algorithm tests
- Database operations tests
- Network state handling tests

**9.2 Integration Tests**
- Multi-device sync scenarios
- Offline/online transition tests
- Structure vs movement sync tests

**9.3 Performance Tests**
- Large dataset sync performance
- Concurrent sync operation handling
- Memory usage optimization

### **Phase 10: Deployment & Rollout**

**10.1 Gradual Rollout**
- Phase 1: Internal testing with limited users
- Phase 2: Beta testing with select users
- Phase 3: Full production rollout

**10.2 Monitoring & Feedback**
- Real-time sync success/failure monitoring
- User feedback collection
- Performance metric analysis

**Benefits of This Implementation:**
- ✅ **Offline-first**: Complete functionality without internet
- ✅ **Intelligent sync**: Smart conflict resolution with dual-field comparison
- ✅ **Hierarchical sync**: Efficient structure vs movement data handling
- ✅ **Multi-device**: Seamless synchronization across all devices
- ✅ **Data integrity**: No lost readings or conflicts
- ✅ **Performance**: Optimized for minimal network usage

Ready to implement this comprehensive hierarchical sync system with dual-field conflict resolution?

Please **toggle to Act mode** so we can start building this architecture.