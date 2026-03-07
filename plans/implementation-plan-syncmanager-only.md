# Implementation Plan: SyncManager-Only Firebase Writes

## Goal
Ensure that **ONLY** the SyncManager writes to Firebase Firestore for core application data. All other components write exclusively to the local Room database, and SyncManager orchestrates synchronization with Firebase.

### Scope: What Must Go Through SyncManager
- **Vehicles** (vehicles collection)
- **Services** (services collection)
- **Service Variants** (service_variants collection)
- **VehicleServiceMappings** (vehicle_service_mappings collection)

### Allowed Exceptions (Can Bypass SyncManager)
- **User Data** (users collection) - FirebaseAuthRepositoryImpl can write directly
- **Admin Initialization** - AdminInitializer can write directly (one-time setup)
- **Firebase Authentication** - Auth operations (login, register, logout) are separate from data sync


---

## Phase 1: Prepare Local Repository Implementations

### Step 1.1: Create LocalVehicleRepositoryImpl

**File**: `app/src/main/java/com/mainlert/data/repositories/LocalVehicleRepositoryImpl.kt`

Key responsibilities:
- Implement `VehicleRepository` interface
- Use `LocalDatabase.vehicleDao()` for all operations
- After write operations (create, update, delete), mark entity as needing sync:
  ```kotlin
  vehicleDao().updateSyncStatus(vehicleId, System.currentTimeMillis(), false)
  ```
- Trigger SyncManager for appropriate sync type:
  - Structure changes (create/update/delete) → `syncManager.syncOnMonitoringStart()` or `syncManager.syncVehicle(vehicleId)`
  - Lifetime mileage updates → `syncManager.syncContinuousData()` (since it's continuous data)

Methods to implement:
- `getAllVehicles()` - read from DAO
- `getVehiclesForUser(userId)` - read from DAO
- `getVehiclesForEmployee(employeeId)` - read from DAO
- `getVehicleById(vehicleId)` - read from DAO
- `createVehicle(vehicle)` - insert to DAO, mark needsSync=true, trigger sync
- `updateVehicle(vehicle)` - update DAO, mark needsSync=true, trigger sync
- `deleteVehicle(vehicleId)` - delete from DAO, mark needsSync=true, trigger sync
- `assignVehicleToDriver(vehicleId, driverId)` - update DAO, mark needsSync=true, trigger sync
- `removeVehicleFromDriver(vehicleId)` - update DAO, mark needsSync=true, trigger sync
- `getUnassignedVehicles()` - read from DAO
- `createVehicleForDriver(...)` - insert vehicle, update user, mark needsSync, trigger sync
- `updateVehicleLifetimeMileage(vehicleId, mileage)` - update DAO, trigger continuous sync
- `observeVehiclesForUser(userId)` - return Flow from DAO

---

### Step 1.2: Create LocalServiceRepositoryImpl

**File**: `app/src/main/java/com/mainlert/data/repositories/LocalServiceRepositoryImpl.kt`

Implementation similar to LocalVehicleRepositoryImpl:
- Use `LocalDatabase.serviceDao()`
- After CRUD operations, mark entity as needing sync: `serviceDao().updateSyncStatus(serviceId, System.currentTimeMillis(), false)`
- Trigger sync: `syncManager.syncOnMonitoringStart()` or `syncManager.syncService(serviceId)`

---

### Step 1.3: Create LocalServiceVariantRepositoryImpl

**File**: `app/src/main/java/com/mainlert/data/repositories/LocalServiceVariantRepositoryImpl.kt`

**Note**: Service variants don't have an entity yet. Need to create `ServiceVariantEntity` in `LocalDatabase`.

Actions:
1. Create `ServiceVariantEntity` data class with fields:
   ```kotlin
   @Entity(tableName = "service_variants")
   data class ServiceVariantEntity(
       @PrimaryKey val id: String,
       val name: String,
       val description: String,
       val mileageLimit: Float,
       val createdBy: String,
       val isActive: Boolean,
       val createdAt: Long,
       val localLastUpdated: Long,
       val firebaseLastUpdated: Long?,
       val needsSync: Boolean
   )
   ```
2. Add `ServiceVariantEntity` to `LocalDatabase.entities`
3. Create `ServiceVariantDao` with CRUD operations and sync status fields
4. Implement repository using DAO, marking needsSync after writes
5. Trigger sync via SyncManager

---

### Step 1.4: User Data Exception

**Note**: User profile data (users collection) is allowed to bypass SyncManager per architectural decision.

**Action**: Keep `FirebaseAuthRepositoryImpl` as-is for all user operations. No local user repository needed.

**Rationale**:
- User data is administrative and changes are infrequent
- Real-time listeners already provide updates from Firebase
- Offline support for user data is not critical
- Simplifies architecture by not requiring UserEntity and UserDao

**However**: If offline user data becomes necessary later, we can implement LocalAuthRepositoryImpl with SyncManager sync.

---

### Step 1.5: Verify LocalVehicleServiceMappingRepositoryImpl

**File**: Already exists and is correctly implemented:
- Uses local DAO for all operations
- Calls `syncManager.syncContinuousData()` after write operations
- ✅ No changes needed

---

## Phase 2: Enhance SyncManager

**File**: `app/src/main/java/com/mainlert/data/local/sync/SyncManager.kt`

### Step 2.1: Add DAO Dependencies

Add to SyncManager constructor:
```kotlin
private val vehicleDao: VehicleDao,
private val serviceDao: ServiceDao,
private val serviceVariantDao: ServiceVariantDao, // new
private val userDao: UserDao, // new
```

### Step 2.2: Add Individual Sync Methods

```kotlin
// Vehicle sync
suspend fun syncVehicle(vehicleId: String) {
    val threshold = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
    val vehicle = vehicleDao.getVehicle(vehicleId) // need method in DAO
    
    if (vehicle != null && (vehicle.needsSync || vehicle.firebaseLastUpdated == null)) {
        try {
            firebaseFirestore.collection("vehicles")
                .document(vehicle.id)
                .set(vehicle.toVehicleFirebaseMap())
                .await()
            
            vehicleDao.updateSyncStatus(vehicleId, System.currentTimeMillis(), true)
        } catch (e: Exception) {
            handleSyncFailure(e)
        }
    }
}

// Service sync
suspend fun syncService(serviceId: String) { /* similar */ }

// ServiceVariant sync
suspend fun syncServiceVariant(variantId: String) { /* similar */ }

// User sync
suspend fun syncUser(userId: String) { /* similar */ }

// Enhanced continuous sync for mappings (already exists)
```

### Step 2.3: Add Batch Sync Methods

For efficiency, add batch sync that processes all entities needing sync:

```kotlin
suspend fun syncAllStructureData() {
    // Sync all vehicles needing sync
    val vehiclesNeedingSync = vehicleDao.getVehiclesNeedingSync(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24))
    vehiclesNeedingSync.forEach { syncVehicle(it.id) }
    
    // Sync all services needing sync
    val servicesNeedingSync = serviceDao.getServicesNeedingSync(...)
    servicesNeedingSync.forEach { syncService(it.id) }
    
    // Sync all variants needing sync
    // ...
}
```

### Step 2.4: Expose Sync Trigger Methods

Add public methods that repositories can call:
```kotlin
fun triggerVehicleSync(vehicleId: String) {
    coroutineScope.launch { syncVehicle(vehicleId) }
}

fun triggerServiceSync(serviceId: String) {
    coroutineScope.launch { syncService(serviceId) }
}

// ... similar for other types
```

---

## Phase 3: Update Dependency Injection

**File**: `app/src/main/java/com/mainlert/di/FirebaseModule.kt`

### Step 3.1: Remove Firebase Repository Bindings

Remove these methods:
- `bindFirebaseVehicleRepository()`
- `bindFirebaseServiceRepository()`
- `bindFirebaseServiceVariantRepository()`
- `bindFirebaseVehicleServiceMappingRepository()` (optional - keep local)
- `bindFirebaseAuthRepository()`

### Step 3.2: Add Local Repository Bindings

Add new bindings for repositories that MUST use SyncManager:

```kotlin
@Singleton
abstract fun bindLocalVehicleRepository(
    localVehicleRepositoryImpl: LocalVehicleRepositoryImpl
): VehicleRepository

@Singleton
abstract fun bindLocalServiceRepository(
    localServiceRepositoryImpl: LocalServiceRepositoryImpl
): ServiceRepository

@Singleton
abstract fun bindLocalServiceVariantRepository(
    localServiceVariantRepositoryImpl: LocalServiceVariantRepositoryImpl
): ServiceVariantRepository

@Singleton
abstract fun bindLocalVehicleServiceMappingRepository(
    localVehicleServiceMappingRepositoryImpl: LocalVehicleServiceMappingRepositoryImpl
): VehicleServiceMappingRepository

// NOTE: Do NOT bind AuthRepository to a local implementation.
// FirebaseAuthRepositoryImpl remains as the implementation for AuthRepository
// because user data is allowed to bypass SyncManager.

### Step 3.3: Provide SyncManager Dependencies

Ensure SyncManager is provided with all DAOs:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SyncManagerModule {
    
    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        localDatabase: LocalDatabase,
        conflictResolver: ConflictResolver,
        networkMonitor: NetworkMonitor,
        firebaseFirestore: FirebaseFirestore,
        coroutineScope: CoroutineScope
    ): SyncManager {
        return SyncManager(
            context = context,
            localDatabase = localDatabase,
            conflictResolver = conflictResolver,
            networkMonitor = networkMonitor,
            firebaseFirestore = firebaseFirestore,
            coroutineScope = coroutineScope,
            vehicleDao = localDatabase.vehicleDao(),
            serviceDao = localDatabase.serviceDao(),
            serviceVariantDao = localDatabase.serviceVariantDao(),
            userDao = localDatabase.userDao()
        )
    }
}
```

---

## Phase 4: Update UI Layer (ViewModels)

Verify that ViewModels don't directly use Firebase repositories. They should only use repository interfaces.

**Files to check**:
- `DashboardViewModel.kt` - ✅ Already uses repository interfaces
- `AuthViewModel.kt` - ✅ Already uses repository interfaces
- `SystemSettingsViewModel.kt` (if exists)
- Any other ViewModels

**Action**: No changes needed if ViewModels only depend on interfaces. The DI switch will automatically inject local implementations.

---

## Phase 5: Update AccelerometerService

**File**: `app/src/main/java/com/mainlert/services/AccelerometerService.kt`

Current state:
- Uses `VehicleServiceMappingRepository` (should be local)
- Uses `VehicleRepository` for vehicle data
- Uses `ServiceRepository` for service data

**Action**: Verify these are injected as local implementations via DI. No code changes needed if DI is configured correctly.

---

## Phase 6: Remove Firebase Repository Implementations

After thorough testing and confirmation that local repositories work correctly:

### Step 6.1: Delete Files
- `FirebaseVehicleRepositoryImpl.kt`
- `FirebaseServiceRepositoryImpl.kt`
- `FirebaseServiceVariantRepositoryImpl.kt`
- `FirebaseVehicleServiceMappingRepositoryImpl.kt` (optional - can keep as reference or delete)
- `FirebaseAuthRepositoryImpl.kt` (keep if we need Firebase Auth operations, but rename or refactor to separate concerns)

### Step 6.2: Clean Up Imports
Remove any remaining imports of Firebase repository classes, EXCEPT:
- FirebaseAuthRepositoryImpl (kept for user data)
- Any Firebase Auth related imports (these are allowed)

---

## Phase 7: Testing Strategy

### Unit Tests
1. Test LocalVehicleRepositoryImpl - verify it writes to Room, not Firebase
2. Test SyncManager sync methods - verify they correctly read from Room and write to Firebase
3. Test conflict resolution in SyncManager

### Integration Tests
1. Test full flow: UI operation → local DB write → SyncManager sync → Firebase update
2. Test offline mode: local writes, then online → automatic sync
3. Test conflict scenarios: simultaneous updates from multiple devices

### Manual Testing Checklist
- [ ] Create vehicle in UI → appears in Firebase after sync
- [ ] Update service → appears in Firebase after sync
- [ ] Create service variant → appears in Firebase after sync
- [ ] Register new user → appears in Firebase after sync
- [ ] Start monitoring → mapping updates flow through SyncManager to Firebase
- [ ] Stop monitoring → final reading saved via SyncManager
- [ ] Offline → all operations work locally
- [ ] Online → sync automatically triggers and updates Firebase
- [ ] Verify no Firebase writes from non-SyncManager components (use Firebase console logs or network inspector)

---

## Timeline Estimate

| Phase | Estimated Effort | Dependencies |
|-------|------------------|--------------|
| Phase 1: Local Repositories | 2-3 days | DAO layer complete (ServiceVariantDao, UserDao need creation) |
| Phase 2: SyncManager Enhancement | 1-2 days | Local repositories ready |
| Phase 3: DI Updates | 0.5 day | SyncManager enhanced, local repositories ready |
| Phase 4: ViewModel Verification | 0.5 day | DI updated |
| Phase 5: AccelerometerService Check | 0.5 day | DI updated |
| Phase 6: Remove Firebase Repos | 0.5 day | All testing complete |
| Phase 7: Testing | 1-2 days | All phases complete |
| **Total** | **6-9 days** | - |

---

## Risk Mitigation

1. **Data Loss**: Keep Firebase repositories as backup until local repos are fully tested
2. **Sync Failures**: Implement robust error handling and retry logic in SyncManager
3. **Performance**: Batch sync operations, use Firebase transactions for conflict resolution
4. **Offline Support**: Ensure all repository operations work offline (local DB only)
5. **Real-time Updates**: Keep Firebase real-time listeners in place to update local DB from external changes

---

## Success Criteria

✅ All repository implementations write ONLY to Room database
✅ SyncManager is the ONLY component that writes to Firebase
✅ All CRUD operations from UI successfully sync to Firebase
✅ Conflict resolution works correctly
✅ Offline-first architecture fully implemented
✅ No Firebase writes detected outside SyncManager (verified via logs/network inspection)
✅ All existing functionality preserved and tested

---

## Next Immediate Actions

1. Create `ServiceVariantEntity` and `UserEntity` data classes
2. Create corresponding DAOs (`ServiceVariantDao`, `UserDao`)
3. Add these entities and DAOs to `LocalDatabase`
4. Implement `LocalServiceVariantRepositoryImpl`
5. Implement `LocalAuthRepositoryImpl`
6. Implement `LocalVehicleRepositoryImpl`
7. Implement `LocalServiceRepositoryImpl`
8. Update `SyncManager` with new DAO dependencies and sync methods
9. Update `FirebaseModule.kt` with new bindings
10. Test thoroughly
11. Remove Firebase repository implementations

---

## Notes

- The `FirebaseAuthRepositoryImpl` can be split: keep Firebase Auth operations (login, register, logout) in a separate `FirebaseAuthManager` class, but move user data operations to `LocalAuthRepositoryImpl`
- Real-time Firebase listeners should remain in place to keep local DB updated with changes from other devices
- The `SyncManager` should be the **only** component with `FirebaseFirestore` write operations
