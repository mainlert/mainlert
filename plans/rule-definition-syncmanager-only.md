# Rule Definition: "SyncManager-Only" Firebase Writes

## Rule Statement

**All writes to Firebase Firestore for core application data MUST go through the SyncManager.**

The SyncManager is the **exclusive** component responsible for synchronizing local Room database changes with Firebase Firestore.

---

## Scope: What Must Go Through SyncManager

These Firebase collections must only be written by SyncManager:

1. **vehicles** - Vehicle structure data
2. **services** - Service template data
3. **service_variants** - Service variant data
4. **vehicle_service_mappings** - Vehicle-service mapping data (including movement readings, monitoring status)

---

## Allowed Exceptions (Can Bypass SyncManager)

The following components CAN write directly to Firebase without going through SyncManager:

### 1. FirebaseAuthRepositoryImpl
- **Collection**: `users`
- **Operations**: User profile CRUD, role assignments, driver-employee relationships
- **Rationale**: User management is administrative, infrequent, and requires immediate consistency. Real-time listeners already handle updates.

### 2. AdminInitializer
- **Collection**: `users`
- **Operations**: One-time admin user creation/update during app setup
- **Rationale**: Setup/initialization utility, not part of regular application data flow.

### 3. Firebase Authentication
- **Service**: Firebase Auth (not Firestore)
- **Operations**: User authentication, token management, password reset
- **Rationale**: Separate concern from data synchronization.

---

## Prohibited Direct Firebase Writes

The following components currently violate the rule and **MUST be refactored**:

❌ `FirebaseVehicleRepositoryImpl` - all write operations
❌ `FirebaseServiceRepositoryImpl` - all write operations
❌ `FirebaseServiceVariantRepositoryImpl` - all write operations
❌ `FirebaseVehicleServiceMappingRepositoryImpl` - all write operations
❌ Any other repository that writes to vehicles/services/variants/mappings collections

---

## Implementation Strategy

### 1. Create Local Repository Implementations
- `LocalVehicleRepositoryImpl` - writes to VehicleEntity via VehicleDao
- `LocalServiceRepositoryImpl` - writes to ServiceEntity via ServiceDao
- `LocalServiceVariantRepositoryImpl` - writes to ServiceVariantEntity via ServiceVariantDao
- `LocalVehicleServiceMappingRepositoryImpl` - already exists ✅

### 2. Enhance SyncManager
- Add DAO dependencies (VehicleDao, ServiceDao, ServiceVariantDao)
- Add individual sync methods: `syncVehicle()`, `syncService()`, `syncServiceVariant()`
- Add batch sync method: `syncAllStructureData()`
- Add trigger methods: `triggerVehicleSync()`, `triggerServiceSync()`, etc.

### 3. Update Dependency Injection
- Bind local repository implementations to repository interfaces
- Keep `FirebaseAuthRepositoryImpl` bound to `AuthRepository` (exception)
- Remove Firebase repository bindings for Vehicle, Service, ServiceVariant, VehicleServiceMapping

### 4. Mark Entities for Sync
Local entities must have sync tracking fields:
```kotlin
val localLastUpdated: Long
val firebaseLastUpdated: Long?
val needsSync: Boolean
```

After local write operations, set `needsSync = true` and call appropriate SyncManager trigger.

---

## Verification Strategy

### Code Review Checklist
- [ ] No `FirebaseFirestore` or `CollectionReference` imports in repository implementations (except AuthRepository)
- [ ] All repository write operations target Room database only
- [ ] SyncManager is the only class with `.set()`, `.update()`, `.delete()` on vehicles/services/variants/mappings collections
- [ ] Local repositories call SyncManager after local writes

### Runtime Verification
- Enable Firebase Firestore logging to monitor writes
- Perform UI operations and verify Firebase writes originate from SyncManager only
- Check that no direct Firebase writes appear from other components

---

## Benefits

1. **Single Source of Truth**: Local database is authoritative
2. **Offline-First**: All operations work offline, sync when online
3. **Conflict Resolution**: Centralized in SyncManager with dual-field timestamps
4. **Consistency**: Predictable data flow, easier debugging
5. **Testability**: Local repositories can be tested without Firebase

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Data loss if SyncManager fails | Implement robust error handling and retry in SyncManager |
| Sync conflicts | Use dual-field timestamp comparison for conflict resolution |
| Performance issues | Batch sync operations, use Firebase transactions |
| Breaking existing functionality | Comprehensive testing, keep Firebase repos as backup during transition |

---

## Success Criteria

✅ All Vehicle/Service/Variant/Mapping writes flow through SyncManager
✅ Local repositories only write to Room database
✅ FirebaseAuthRepositoryImpl remains unchanged (allowed exception)
✅ All existing functionality preserved
✅ Offline operations work correctly
✅ Sync conflicts resolved correctly
✅ No direct Firebase writes detected outside SyncManager

---

## Next Steps

1. Review and approve this rule definition
2. Implement local repository implementations
3. Enhance SyncManager with individual sync methods
4. Update DI bindings
5. Test thoroughly
6. Remove Firebase repository implementations
