package com.mainlert.data.local.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.firebase.firestore.FirebaseFirestore
import com.mainlert.data.local.LocalDatabase
import com.mainlert.data.local.conflict.ConflictResolver
import com.mainlert.data.local.entities.VehicleServiceMappingEntity
import com.mainlert.data.local.entities.VehicleEntity
import com.mainlert.data.local.entities.ServiceEntity
import com.mainlert.data.local.entities.ServiceVariantEntity
import com.mainlert.data.local.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.pow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.mainlert.data.local.entities.toVehicleFirebaseMap
import com.mainlert.data.local.entities.toServiceFirebaseMap
import com.mainlert.data.local.entities.toVariantFirebaseMap
import com.mainlert.data.local.entities.toMappingFirebaseMap
import com.mainlert.data.local.entities.toVehicleEntity
import com.mainlert.data.local.entities.toServiceEntity
import com.mainlert.data.local.entities.toServiceVariantEntity
import com.mainlert.data.local.entities.toVehicleServiceMappingEntity

/**
 * Sync manager for hierarchical sync with dual-field conflict resolution.
 * Orchestrates sync operations between local database and Firebase.
 */
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localDatabase: LocalDatabase,
    private val conflictResolver: ConflictResolver,
    private val networkMonitor: NetworkMonitor,
    private val firebaseFirestore: FirebaseFirestore,
    private val coroutineScope: CoroutineScope
) {
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState
    
    private val _syncMetrics = MutableStateFlow(SyncMetrics())
    val syncMetrics: StateFlow<SyncMetrics> = _syncMetrics
    
    // Throttling for continuous sync to avoid Firebase abuse
    private var lastContinuousSyncTime = 0L
    private val continuousSyncThrottleMs = TimeUnit.SECONDS.toMillis(30) // 30 seconds
    
    // Exponential backoff for failed syncs
    private var consecutiveFailures = 0
    private val maxBackoffDelay = TimeUnit.SECONDS.toMillis(300) // 5 minutes max
    private val baseBackoffDelay = TimeUnit.SECONDS.toMillis(5) // 5 seconds base
    
    // Track last successful sync times for staleness detection
    private var lastSuccessfulStructureSync = 0L
    private var lastSuccessfulContinuousSync = 0L
    private val staleDataThresholdMs = TimeUnit.MINUTES.toMillis(5) // 5 minutes
    
    init {
        // Observe network changes and trigger sync when online
        coroutineScope.launch {
            networkMonitor.observeNetworkState().collect { isOnline ->
                if (isOnline) {
                    triggerContinuousSync()
                }
            }
        }
    }
    
    /**
     * Sync on monitoring start - syncs structure data (vehicles, services, and variants).
     */
    suspend fun syncOnMonitoringStart() {
        updateSyncState(SyncState.SyncingStructure)
        
        try {
            val startTime = System.currentTimeMillis()
            
            // Sync vehicle structure
            syncVehicleStructure()
            
            // Sync service structure
            syncServiceStructure()
            
            // Sync service variant structure
            syncServiceVariantStructure()
            
            val endTime = System.currentTimeMillis()
            updateSyncMetrics(startTime, endTime, 0, 0)
            updateSyncState(SyncState.StructureSynced)
            
            // Record successful structure sync
            lastSuccessfulStructureSync = System.currentTimeMillis()
            
        } catch (e: Exception) {
            updateSyncState(SyncState.Error("Structure sync failed: ${e.message}"))
            handleSyncFailure(e)
        }
    }
    
    /**
     * Sync continuous data - syncs movement data for VehicleServiceMappings.
     */
    suspend fun syncContinuousData() {
        if (!networkMonitor.isOnline()) {
            updateSyncState(SyncState.Offline)
            return
        }
        
        // Throttle: prevent too frequent syncs (max once per 30 seconds)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastContinuousSyncTime < continuousSyncThrottleMs) {
            android.util.Log.d("SyncManager", "Sync throttled: last sync was ${currentTime - lastContinuousSyncTime}ms ago, minimum interval is ${continuousSyncThrottleMs}ms")
            return
        }
        
        lastContinuousSyncTime = currentTime
        
        // Check if we should backoff due to recent failures
        if (consecutiveFailures > 0) {
            val backoffDelay = calculateBackoffDelay()
            if (currentTime - lastContinuousSyncTime < backoffDelay) {
                android.util.Log.d("SyncManager", "Sync in backoff period: ${currentTime - lastContinuousSyncTime}ms < $backoffDelay")
                updateSyncState(SyncState.Error("Sync in backoff period due to recent failures"))
                return
            }
            // Reset failures after backoff period has passed
            consecutiveFailures = 0
        }
        
        updateSyncState(SyncState.SyncingContinuous)
        
        try {
            val startTime = System.currentTimeMillis()
            val mappings = localDatabase.mappingDao().getMappingsNeedingSync()
            var itemsSynced = 0
            var conflictsResolved = 0
            
            for (mapping in mappings) {
                val result = syncMappingData(mapping)
                if (result.success) {
                    itemsSynced++
                    if (result.conflictResolved) {
                        conflictsResolved++
                    }
                }
            }
            
            val endTime = System.currentTimeMillis()
            updateSyncMetrics(startTime, endTime, itemsSynced, conflictsResolved)
            updateSyncState(SyncState.ContinuousSynced)
            
            // Reset failure count on success
            consecutiveFailures = 0
            
            // Record successful continuous sync
            lastSuccessfulContinuousSync = System.currentTimeMillis()
            
        } catch (e: Exception) {
            consecutiveFailures++
            updateSyncState(SyncState.Error("Continuous sync failed: ${e.message}"))
            handleSyncFailure(e)
        }
    }
    
    /**
     * Sync data from Firebase to local database.
     * Used for initial data population and manual refresh.
     */
    suspend fun syncFromFirebase(userId: String) {
        if (!networkMonitor.isOnline()) {
            updateSyncState(SyncState.Offline)
            return
        }
        
        updateSyncState(SyncState.SyncingStructure)
        
        try {
            val startTime = System.currentTimeMillis()
            var itemsSynced = 0
            var conflictsResolved = 0
            
            // Step 1: Sync vehicles for user - collect Flow from DAO
            val existingVehicles = localDatabase.vehicleDao().getVehiclesByUser(userId).first()
            val firebaseVehicles = fetchVehiclesFromFirebase(userId)
            
            for (firebaseVehicle in firebaseVehicles) {
                val existingVehicle = existingVehicles.find { it.id == firebaseVehicle.id }
                if (existingVehicle != null) {
                    // Conflict resolution
                    val resolvedVehicle = conflictResolver.resolveConflict(existingVehicle, firebaseVehicle)
                    localDatabase.vehicleDao().insertVehicle(resolvedVehicle)
                    conflictsResolved++
                } else {
                    // New vehicle from Firebase
                    localDatabase.vehicleDao().insertVehicle(firebaseVehicle)
                }
                itemsSynced++
            }
            
            // Step 2: Sync mappings for user - collect Flow from DAO
            // This must be done before syncing services to determine which services are actually used
            val firebaseMappings = fetchMappingsFromFirebase(userId)
            val existingMappings = localDatabase.mappingDao().getAllMappings().first()
            
            for (firebaseMapping in firebaseMappings) {
                val existingMapping = existingMappings.find { it.id == firebaseMapping.id }
                if (existingMapping != null) {
                    val resolvedMapping = conflictResolver.resolveConflict(existingMapping, firebaseMapping)
                    localDatabase.mappingDao().insertMapping(resolvedMapping)
                    conflictsResolved++
                } else {
                    localDatabase.mappingDao().insertMapping(firebaseMapping)
                }
                itemsSynced++
            }
            
            // Step 3: Extract unique service IDs from the mappings
            // Services are templates, so we only sync those that are actually assigned to user's vehicles
            val serviceIdsToSync = firebaseMappings.map { it.serviceId }.distinct()
            
            // Step 4: Sync only the services that are referenced in the mappings
            if (serviceIdsToSync.isNotEmpty()) {
                val firebaseServices = fetchServicesByIdsFromFirebase(serviceIdsToSync)
                val existingServices = localDatabase.serviceDao().getAllServices().first()
                
                for (firebaseService in firebaseServices) {
                    val existingService = existingServices.find { it.id == firebaseService.id }
                    if (existingService != null) {
                        val resolvedService = conflictResolver.resolveConflict(existingService, firebaseService)
                        localDatabase.serviceDao().insertService(resolvedService)
                        conflictsResolved++
                    } else {
                        localDatabase.serviceDao().insertService(firebaseService)
                    }
                    itemsSynced++
                }
            }
            
            // Step 5: Sync service variants - collect Flow from DAO (global reference data)
            val firebaseVariants = fetchServiceVariantsFromFirebase()
            val existingVariants = localDatabase.serviceVariantDao().getAllVariants().first()
            
            for (firebaseVariant in firebaseVariants) {
                val existingVariant = existingVariants.find { it.id == firebaseVariant.id }
                if (existingVariant != null) {
                    val resolvedVariant = conflictResolver.resolveConflict(existingVariant, firebaseVariant)
                    localDatabase.serviceVariantDao().insertVariant(resolvedVariant)
                    conflictsResolved++
                } else {
                    localDatabase.serviceVariantDao().insertVariant(firebaseVariant)
                }
                itemsSynced++
            }
            
            val endTime = System.currentTimeMillis()
            updateSyncMetrics(startTime, endTime, itemsSynced, conflictsResolved)
            updateSyncState(SyncState.StructureSynced)
            
        } catch (e: Exception) {
            updateSyncState(SyncState.Error("Firebase sync failed: ${e.message}"))
            handleSyncFailure(e)
        }
    }
    
    /**
     * Fetch vehicles from Firebase for a specific user.
     */
    private suspend fun fetchVehiclesFromFirebase(userId: String): List<VehicleEntity> {
        return try {
            val query = firebaseFirestore.collection("vehicles")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            query.documents.mapNotNull { document ->
                document.toVehicleEntity()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Fetch services from Firebase for a specific user.
     */
    private suspend fun fetchServicesFromFirebase(userId: String): List<ServiceEntity> {
        return try {
            val query = firebaseFirestore.collection("services")
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .await()
            
            query.documents.mapNotNull { document ->
                document.toServiceEntity()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Fetch specific services from Firebase by their IDs.
     * Used to sync only services that are actually assigned to user's vehicles.
     */
    private suspend fun fetchServicesByIdsFromFirebase(serviceIds: List<String>): List<ServiceEntity> {
        if (serviceIds.isEmpty()) return emptyList()
        
        return try {
            // Fetch each service by its document ID
            val services = mutableListOf<ServiceEntity>()
            
            for (serviceId in serviceIds) {
                val document = firebaseFirestore.collection("services")
                    .document(serviceId)
                    .get()
                    .await()
                
                if (document.exists()) {
                    document.toServiceEntity()?.let { services.add(it) }
                }
            }
            
            services
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Fetch service variants from Firebase.
     */
    private suspend fun fetchServiceVariantsFromFirebase(): List<ServiceVariantEntity> {
        return try {
            val query = firebaseFirestore.collection("service_variants")
                .get()
                .await()
            
            query.documents.mapNotNull { document ->
                document.toServiceVariantEntity()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Fetch mappings from Firebase for a specific user.
     */
    private suspend fun fetchMappingsFromFirebase(userId: String): List<VehicleServiceMappingEntity> {
        return try {
            val query = firebaseFirestore.collection("vehicle_service_mappings")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            query.documents.mapNotNull { document ->
                document.toVehicleServiceMappingEntity()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Trigger continuous sync when network becomes available.
     */
    private fun triggerContinuousSync() {
        coroutineScope.launch(Dispatchers.IO) {
            syncContinuousData()
        }
    }
    
    /**
     * Sync vehicle structure data.
     */
    private suspend fun syncVehicleStructure() {
        val threshold = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        val vehicles = localDatabase.vehicleDao().getVehiclesNeedingSync(threshold)
        
        for (vehicle in vehicles) {
            try {
                firebaseFirestore.collection("vehicles")
                    .document(vehicle.id)
                    .set(vehicle.toVehicleFirebaseMap())
                    .await()
                
                localDatabase.vehicleDao().updateSyncStatus(
                    vehicle.id, 
                    System.currentTimeMillis(), 
                    true
                )
            } catch (e: Exception) {
                handleSyncFailure(e)
            }
        }
    }
    
    /**
     * Sync service structure data.
     */
    private suspend fun syncServiceStructure() {
        val threshold = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        val services = localDatabase.serviceDao().getServicesNeedingSync(threshold)
        
        for (service in services) {
            try {
                firebaseFirestore.collection("services")
                    .document(service.id)
                    .set(service.toServiceFirebaseMap())
                    .await()
                
                localDatabase.serviceDao().updateSyncStatus(
                    service.id,
                    System.currentTimeMillis(),
                    true
                )
            } catch (e: Exception) {
                handleSyncFailure(e)
            }
        }
    }
    
    /**
     * Sync service variant structure data.
     */
    private suspend fun syncServiceVariantStructure() {
        val threshold = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        val variants = localDatabase.serviceVariantDao().getVariantsNeedingSync(threshold)
        
        for (variant in variants) {
            try {
                firebaseFirestore.collection("service_variants")
                    .document(variant.id)
                    .set(variant.toVariantFirebaseMap())
                    .await()
                
                localDatabase.serviceVariantDao().updateSyncStatus(
                    variant.id,
                    System.currentTimeMillis(),
                    true
                )
            } catch (e: Exception) {
                handleSyncFailure(e)
            }
        }
    }
    
    /**
     * Sync individual mapping data with conflict resolution.
     */
    private suspend fun syncMappingData(mapping: VehicleServiceMappingEntity): SyncResult {
        return try {
            val firebaseData = getMappingFromFirebase(mapping.id)
            
            if (firebaseData != null) {
                // Conflict resolution needed
                val resolvedData = conflictResolver.resolveConflict(mapping, firebaseData)
                
                // Update Firebase with resolved data
                firebaseFirestore.collection("vehicle_service_mappings")
                    .document(mapping.id)
                    .update(resolvedData.toMappingFirebaseMap())
                    .await()
                
                // Update local database
                localDatabase.mappingDao().insertMapping(resolvedData)
                localDatabase.mappingDao().markAsSynced(mapping.id, System.currentTimeMillis())
                
                SyncResult(success = true, conflictResolved = true)
                
            } else {
                // No Firebase data, create new
                firebaseFirestore.collection("vehicle_service_mappings")
                    .document(mapping.id)
                    .set(mapping.toMappingFirebaseMap())
                    .await()
                
                localDatabase.mappingDao().markAsSynced(mapping.id, System.currentTimeMillis())
                
                SyncResult(success = true, conflictResolved = false)
            }
        } catch (e: Exception) {
            handleSyncFailure(e)
            SyncResult(success = false, conflictResolved = false)
        }
    }
    
    /**
     * Get mapping data from Firebase.
     */
    private suspend fun getMappingFromFirebase(id: String): VehicleServiceMappingEntity? {
        return try {
            val document = firebaseFirestore.collection("vehicle_service_mappings")
                .document(id)
                .get()
                .await()

            if (document.exists()) {
                document.toVehicleServiceMappingEntity()
            } else {
                null
            }
        } catch (e: Exception) {
            handleSyncFailure(e)
            null
        }
    }
    
    /**
     * Update sync state.
     */
    private fun updateSyncState(state: SyncState) {
        _syncState.value = state
    }
    
    /**
     * Update sync metrics.
     */
    private fun updateSyncMetrics(startTime: Long, endTime: Long, itemsSynced: Int, conflictsResolved: Int) {
        val currentMetrics = _syncMetrics.value
        val updatedMetrics = currentMetrics.copy(
            syncStartTime = startTime,
            syncEndTime = endTime,
            itemsSynced = currentMetrics.itemsSynced + itemsSynced,
            conflictsResolved = currentMetrics.conflictsResolved + conflictsResolved
        )
        _syncMetrics.value = updatedMetrics
    }
    
    /**
     * Handle sync failures.
     */
    /**
     * Checks if structure data is stale (older than threshold).
     */
    fun isStructureDataStale(): Boolean {
        if (lastSuccessfulStructureSync == 0L) return true // Never synced
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastSuccessfulStructureSync) > staleDataThresholdMs
    }
    
    /**
     * Checks if continuous data is stale (older than threshold).
     */
    fun isContinuousDataStale(): Boolean {
        if (lastSuccessfulContinuousSync == 0L) return true // Never synced
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastSuccessfulContinuousSync) > staleDataThresholdMs
    }
    
    private fun handleSyncFailure(error: Exception) {
        android.util.Log.e("SyncManager", "Sync failed: ${error.message}", error)
        // Additional retry logic could be added here
        // For now, exponential backoff is handled in syncContinuousData()
    }
    
    /**
     * Calculate exponential backoff delay based on consecutive failures.
     * Uses exponential backoff with jitter to avoid thundering herd.
     */
    private fun calculateBackoffDelay(): Long {
        val exponentialDelay = baseBackoffDelay * (2.0.pow(consecutiveFailures - 1)).toLong()
        val jitter = (0..1000).random().toLong()
        val delay = (exponentialDelay + jitter).coerceAtMost(maxBackoffDelay)
        android.util.Log.d("SyncManager", "Calculated backoff delay: ${delay}ms (consecutiveFailures=$consecutiveFailures)")
        return delay
    }
    
    /**
     * Get current sync metrics.
     */
    fun getSyncMetrics(): SyncMetrics {
        return _syncMetrics.value
    }
    
    /**
     * Reset sync metrics.
     */
    fun resetMetrics() {
        _syncMetrics.value = SyncMetrics()
    }
    
    /**
     * Get consecutive failure count (for debugging/monitoring).
     */
    fun getConsecutiveFailures(): Int {
        return consecutiveFailures
    }
    
    /**
     * Perform auto-sync with staleness checks.
     * Only syncs if data is stale, respecting throttling and backoff.
     * Returns true if sync was attempted, false if skipped due to staleness check.
     */
    suspend fun autoSyncIfStale(userId: String): Boolean {
        // Check if we should sync structure data
        val structureStale = isStructureDataStale()
        val continuousStale = isContinuousDataStale()
        
        if (!structureStale && !continuousStale) {
            android.util.Log.d("SyncManager", "Auto-sync skipped: data is fresh (structure: ${lastSuccessfulStructureSync}ms ago, continuous: ${lastSuccessfulContinuousSync}ms ago)")
            return false
        }
        
        android.util.Log.d("SyncManager", "Auto-sync triggered: structureStale=$structureStale, continuousStale=$continuousStale")
        
        // Sync structure if stale
        if (structureStale) {
            try {
                syncFromFirebase(userId)
            } catch (e: Exception) {
                android.util.Log.e("SyncManager", "Auto-sync structure failed: ${e.message}")
                // Continue to try continuous sync even if structure fails
            }
        }
        
        // Sync continuous data if stale (with delay to avoid overwhelming Firebase)
        if (continuousStale) {
            try {
                // Small delay between structure and continuous sync
                kotlinx.coroutines.delay(1000)
                syncContinuousData()
            } catch (e: Exception) {
                android.util.Log.e("SyncManager", "Auto-sync continuous failed: ${e.message}")
            }
        }
        
        return true
    }
}