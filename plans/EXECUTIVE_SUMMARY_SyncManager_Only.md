# Executive Summary: SyncManager-Only Firebase Writes

## Problem Statement

The MainLert app currently has **multiple components writing directly to Firebase Firestore**, bypassing the SyncManager. This violates the offline-first, hierarchical sync architecture and causes:

- Inconsistent sync state
- No conflict resolution for most data types
- Unpredictable data flow
- Difficulty debugging and testing

---

## Current Violations

### Must Be Refactored (Write Through SyncManager)
1. ❌ **FirebaseVehicleRepositoryImpl** - Vehicles CRUD
2. ❌ **FirebaseServiceRepositoryImpl** - Services CRUD
3. ❌ **FirebaseServiceVariantRepositoryImpl** - Service Variants CRUD
4. ❌ **FirebaseVehicleServiceMappingRepositoryImpl** - Mappings CRUD & movement updates

### Allowed Exceptions (Can Bypass SyncManager)
1. ✅ **FirebaseAuthRepositoryImpl** - User data (users collection)
2. ✅ **AdminInitializer** - Admin setup (users collection)
3. ✅ **Firebase Auth** - Authentication operations (separate from data sync)

---

## Proposed Architecture

```
┌─────────────────┐
│   UI Layer      │  ViewModels
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────┐
│   Repository Layer (Local Implementations)  │
│  • LocalVehicleRepositoryImpl               │
│  • LocalServiceRepositoryImpl               │
│  • LocalServiceVariantRepositoryImpl        │
│  • LocalVehicleServiceMappingRepositoryImpl │
│  • FirebaseAuthRepositoryImpl (exception)   │
└─────────────┬───────────────────────────────┘
              │ writes only to Room
              ▼
┌─────────────────────────────────────────────┐
│         Local Database (Room)               │
│  • VehicleEntity                            │
│  • ServiceEntity                            │
│  • ServiceVariantEntity (to be created)     │
│  • VehicleServiceMappingEntity              │
│  • UserEntity (optional - not needed)       │
└─────────────┬───────────────────────────────┘
              │ Sync triggered after writes
              ▼
┌─────────────────────────────────────────────┐
│         SyncManager (Singleton)             │
│  • Syncs vehicles to Firebase               │
│  • Syncs services to Firebase               │
│  • Syncs variants to Firebase               │
│  • Syncs mappings to Firebase               │
│  • Conflict resolution (dual-field timestamps)│
│  • Network awareness & retry logic          │
└─────────────┬───────────────────────────────┘
              │ ONLY component that writes
              ▼
┌─────────────────────────────────────────────┐
│      Firebase Firestore                     │
│  • vehicles                                 │
│  • services                                 │
│  • service_variants                         │
│  • vehicle_service_mappings                 │
│  • users (AuthRepository writes here)       │
└─────────────────────────────────────────────┘
```

---

## Implementation Roadmap

### Phase 1: Prepare Database Schema (Day 1)
- [ ] Create `ServiceVariantEntity` (if not exists)
- [ ] Create `ServiceVariantDao` with sync status fields
- [ ] Update `LocalDatabase` to include variant entity
- [ ] Add `needsSync`, `localLastUpdated`, `firebaseLastUpdated` fields to existing entities if missing

### Phase 2: Implement Local Repositories (Days 2-3)
- [ ] `LocalVehicleRepositoryImpl` - VehicleDao + SyncManager triggers
- [ ] `LocalServiceRepositoryImpl` - ServiceDao + SyncManager triggers
- [ ] `LocalServiceVariantRepositoryImpl` - ServiceVariantDao + SyncManager triggers
- [ ] Verify `LocalVehicleServiceMappingRepositoryImpl` is correct (already exists)

### Phase 3: Enhance SyncManager (Day 4)
- [ ] Add DAO dependencies (VehicleDao, ServiceDao, ServiceVariantDao)
- [ ] Implement `syncVehicle(vehicleId: String)`
- [ ] Implement `syncService(serviceId: String)`
- [ ] Implement `syncServiceVariant(variantId: String)`
- [ ] Add trigger methods: `triggerVehicleSync()`, `triggerServiceSync()`, `triggerVariantSync()`
- [ ] Ensure conflict resolution works for all entity types

### Phase 4: Update Dependency Injection (Day 5)
- [ ] Remove Firebase repository bindings for Vehicle, Service, ServiceVariant, VehicleServiceMapping
- [ ] Add local repository bindings
- [ ] Update SyncManager provider to inject all DAOs
- [ ] Keep FirebaseAuthRepositoryImpl binding (exception)

### Phase 5: Testing & Validation (Days 6-7)
- [ ] Unit tests for local repositories (verify no Firebase writes)
- [ ] Unit tests for SyncManager sync methods
- [ ] Integration tests: full flow from UI to Firebase
- [ ] Manual testing: create/update/delete operations
- [ ] Offline mode testing
- [ ] Conflict resolution testing
- [ ] Verify Firebase console logs show only SyncManager writes

### Phase 6: Cleanup (Day 8)
- [ ] Delete Firebase repository implementation files
- [ ] Remove unused imports
- [ ] Update any remaining direct Firebase references
- [ ] Final code review

---

## Key Changes by File

### New Files to Create
- `LocalVehicleRepositoryImpl.kt`
- `LocalServiceRepositoryImpl.kt`
- `LocalServiceVariantRepositoryImpl.kt`
- `ServiceVariantEntity.kt` (entity)
- `ServiceVariantDao.kt` (DAO interface + @Dao methods)
- (Optional) `UserEntity.kt` and `UserDao` - NOT needed since user data bypasses SyncManager

### Modified Files
- `LocalDatabase.kt` - add ServiceVariantEntity, add ServiceVariantDao abstract method
- `SyncManager.kt` - add DAO parameters, add individual sync methods
- `FirebaseModule.kt` - update all bindings
- `LocalVehicleServiceMappingRepositoryImpl.kt` - verify correct usage (likely no changes)

### Files to Remove
- `FirebaseVehicleRepositoryImpl.kt`
- `FirebaseServiceRepositoryImpl.kt`
- `FirebaseServiceVariantRepositoryImpl.kt`
- `FirebaseVehicleServiceMappingRepositoryImpl.kt` (optional - can keep for reference)

### Files to Keep Unchanged
- `FirebaseAuthRepositoryImpl.kt` (allowed exception)
- `AdminInitializer.kt` (allowed exception)
- All ViewModels (they depend on interfaces, DI will inject correct implementations)
- `AccelerometerService.kt` (uses VehicleServiceMappingRepository, will get local impl via DI)

---

## Testing Checklist

### Functional Testing
- [ ] Create vehicle → appears in local DB → syncs to Firebase
- [ ] Update vehicle → syncs to Firebase with conflict resolution
- [ ] Delete vehicle → removed from Firebase
- [ ] Create/update/delete service → syncs to Firebase
- [ ] Create/update/delete service variant → syncs to Firebase
- [ ] Start monitoring → mapping created and updates flow through SyncManager
- [ ] Stop monitoring → final reading saved via SyncManager
- [ ] User operations (register, login, profile update) → still work via AuthRepository

### Offline Testing
- [ ] Disable network → create/update operations work locally
- [ ] Re-enable network → automatic sync triggers
- [ ] Verify no crashes or data loss

### Conflict Testing
- [ ] Modify same vehicle on two devices → conflict resolution merges correctly
- [ ] Verify `localLastUpdated` vs `firebaseLastUpdated` timestamp comparison works

### Security Rules Verification
- [ ] Firebase security rules allow SyncManager service account to write
- [ ] Client-side writes are blocked (defense in depth)
- [ ] User writes to users collection still allowed

---

## Success Metrics

✅ **Zero** direct Firebase writes from Vehicle/Service/Variant/Mapping repositories
✅ **100%** of core data writes flow through SyncManager
✅ **All** existing UI functionality preserved
✅ **Offline-first** architecture fully functional
✅ **Conflict resolution** working for all entity types
✅ **No regressions** in user authentication/management flow

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| SyncManager bugs cause data loss | High | Comprehensive testing, keep Firebase repos as backup during transition |
| Performance degradation | Medium | Batch sync operations, optimize Firebase queries |
| Breaking existing features | High | Incremental implementation, thorough QA at each phase |
| Incomplete migration | Medium | Code review checklist, automated tests to detect direct Firebase writes |

---

## Timeline

**Total Estimated Effort**: 6-8 days

| Phase | Days | Dependencies |
|-------|------|--------------|
| 1. Database Schema | 1 | None |
| 2. Local Repositories | 2-3 | Phase 1 complete |
| 3. SyncManager Enhancement | 1 | Phase 2 complete |
| 4. DI Updates | 1 | Phase 3 complete |
| 5. Testing | 2 | Phase 4 complete |
| 6. Cleanup | 1 | Phase 5 complete |

---

## Decision Required

**Approve this architecture approach?**

Once approved, I can begin implementation in the order specified above. Each phase will be completed and verified before moving to the next.

---

## Questions?

The plan is documented in detail in:
- `plans/syncmanager-only-firebase-writes.md` - Full investigation report
- `plans/implementation-plan-syncmanager-only.md` - Step-by-step implementation guide
- `plans/rule-definition-syncmanager-only.md` - Rule definition and scope

All three documents are consistent and ready for execution.
