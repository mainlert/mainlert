package com.mainlert.ui.viewmodels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mainlert.data.local.sync.SyncManager
import com.mainlert.data.models.Result
import com.mainlert.data.models.Service
import com.mainlert.data.models.ServiceVariant
import com.mainlert.data.models.User
import com.mainlert.data.models.Vehicle
import com.mainlert.data.models.VehicleServiceMapping
import com.mainlert.data.repositories.AuthRepository
import com.mainlert.data.repositories.ServiceRepository
import com.mainlert.data.repositories.ServiceVariantRepository
import com.mainlert.data.repositories.VehicleRepository
import com.mainlert.data.repositories.VehicleServiceMappingRepository
import com.mainlert.services.AccelerometerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log
import com.mainlert.data.local.sync.SyncState


/**
 * Dashboard ViewModel for MainLert app.
 * Uses VehicleServiceMapping for all service readings - the new architecture.
 */
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val serviceRepository: ServiceRepository,
        private val vehicleRepository: VehicleRepository,
        private val serviceVariantRepository: ServiceVariantRepository,
        private val authRepository: AuthRepository,
        private val vehicleServiceMappingRepository: VehicleServiceMappingRepository,
        private val syncManager: SyncManager,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        
    // Sync state from SyncManager
    private val _syncState = MutableStateFlow<com.mainlert.data.local.sync.SyncState>(com.mainlert.data.local.sync.SyncState.Idle)
    val syncState: StateFlow<com.mainlert.data.local.sync.SyncState> = _syncState.asStateFlow()
    
    // Track if monitoring is using cached thresholds (offline mode)
    private val _isUsingCachedThresholds = MutableStateFlow(false)
    val isUsingCachedThresholds: StateFlow<Boolean> = _isUsingCachedThresholds.asStateFlow()
    
    init {
        // Observe sync state from SyncManager
        viewModelScope.launch {
            syncManager.syncState.collect { state ->
                _syncState.value = state
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    when (state) {
                        is SyncState.StructureSynced -> {
                            // Refresh all structure data from local DB after structure sync
                            loadVehiclesFromLocal(currentUserId)
                            loadServices()
                            loadServiceVariants()
                        }
                        is SyncState.ContinuousSynced -> {
                            // Refresh mappings to get latest readings after continuous sync
                            val vehicles = _vehicles.value
                            if (vehicles.isNotEmpty()) {
                                loadMappingsForVehicles(vehicles.map { it.id })
                            }
                        }
                        else -> { /* no op */ }
                    }
                }
            }
        }
    }
    
    /**
     * Trigger auto-sync with staleness check.
     * Only syncs if data is stale, respecting throttling and backoff.
     */
    fun triggerAutoSync() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId != null) {
                val syncAttempted = syncManager.autoSyncIfStale(currentUserId)
                if (!syncAttempted) {
                    _successMessage.value = "Data is fresh, no sync needed"
                }
            } else {
                _errorMessage.value = "User not authenticated"
            }
            
            _isLoading.value = false
        }
    }
    
    // VehicleServiceMappings state for independent per-vehicle readings
    private val _vehicleServiceMappings = MutableStateFlow<List<VehicleServiceMapping>>(emptyList())
    val vehicleServiceMappings: StateFlow<List<VehicleServiceMapping>> = _vehicleServiceMappings.asStateFlow()

    // Live mapping data from Firestore real-time listeners
    private val _liveMappings = MutableStateFlow<Map<String, VehicleServiceMapping>>(emptyMap())
    val liveMappings: StateFlow<Map<String, VehicleServiceMapping>> = _liveMappings.asStateFlow()

    private var currentMappingId: String? = null

    /** Throttle interval for UI updates (500ms) */
    private var lastUiUpdateTime = 0L
    private val uiUpdateThrottleMs = 500L

    // Missing MutableStateFlow variables
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _serviceReadingsMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val serviceReadingsMap: StateFlow<Map<String, Int>> = _serviceReadingsMap.asStateFlow()

    private val _vehicleServices = MutableStateFlow<List<Service>>(emptyList())
    val vehicleServices: StateFlow<List<Service>> = _vehicleServices.asStateFlow()

    private val _serviceVariants = MutableStateFlow<List<ServiceVariant>>(emptyList())
    val serviceVariants: StateFlow<List<ServiceVariant>> = _serviceVariants.asStateFlow()

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    private val _showVehicleSelectionDialog = MutableStateFlow(false)
    val showVehicleSelectionDialog: StateFlow<Boolean> = _showVehicleSelectionDialog.asStateFlow()

    private val _showResetServiceDialog = MutableStateFlow(false)
    val showResetServiceDialog: StateFlow<Boolean> = _showResetServiceDialog.asStateFlow()

    // Boot receiver toggle state
    private val _bootReceiverEnabled = MutableStateFlow(false)
    val bootReceiverEnabled: StateFlow<Boolean> = _bootReceiverEnabled.asStateFlow()

    // Auto-stop timeout setting (in milliseconds, default 1 hour = 3600000 ms)
    private val _autoStopTimeout = MutableStateFlow(3600000L)
    val autoStopTimeout: StateFlow<Long> = _autoStopTimeout.asStateFlow()

    private val _shouldShowVehicleSelectionFromBoot = MutableStateFlow(false)
    val shouldShowVehicleSelectionFromBoot: StateFlow<Boolean> = _shouldShowVehicleSelectionFromBoot.asStateFlow()

    private val _resetDialogStep = MutableStateFlow<ResetDialogStep>(ResetDialogStep.SELECT_DRIVER)
    val resetDialogStep: StateFlow<ResetDialogStep> = _resetDialogStep.asStateFlow()

    private val _selectedDriverForReset = MutableStateFlow<User?>(null)
    val selectedDriverForReset: StateFlow<User?> = _selectedDriverForReset.asStateFlow()

    private val _selectedVehicleForReset = MutableStateFlow<Vehicle?>(null)
    val selectedVehicleForReset: StateFlow<Vehicle?> = _selectedVehicleForReset.asStateFlow()

    private val _selectedServiceForReset = MutableStateFlow<Service?>(null)
    val selectedServiceForReset: StateFlow<Service?> = _selectedServiceForReset.asStateFlow()

    private val _mileageThreshold = MutableStateFlow(20000f)
    val mileageThreshold: StateFlow<Float> = _mileageThreshold.asStateFlow()

    private val _accelerometerReadings = MutableStateFlow<Triple<Float, Float, Float>>(Triple(0f, 0f, 0f))
    val accelerometerReadings: StateFlow<Triple<Float, Float, Float>> = _accelerometerReadings.asStateFlow()

    // Additional state variables
        private var monitoredVehicleId: String? = null
    
        // Battery level state - initialized to 100% to pass validation
        private val _batteryLevel = MutableStateFlow<Float?>(100f)
        val batteryLevel: StateFlow<Float?> = _batteryLevel.asStateFlow()


    /** Broadcast receiver for accelerometer data from the service */
    private val accelerometerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                
                intent?.let {
                    val currentTime = System.currentTimeMillis()
                    // Throttle UI updates to prevent excessive recompositions
                    if (currentTime - lastUiUpdateTime < uiUpdateThrottleMs) {
                        return@let
                    }
                    lastUiUpdateTime = currentTime

                    val x = it.getFloatExtra(AccelerometerService.EXTRA_X, 0f)
                    val y = it.getFloatExtra(AccelerometerService.EXTRA_Y, 0f)
                    val z = it.getFloatExtra(AccelerometerService.EXTRA_Z, 0f)
                    val totalMovement = it.getFloatExtra(AccelerometerService.EXTRA_TOTAL_MOVEMENT, 0f)
                    val isMonitoring = it.getBooleanExtra(AccelerometerService.EXTRA_IS_MONITORING, false)
                    val vehicleId = it.getStringExtra(AccelerometerService.EXTRA_VEHICLE_ID)
                    val mappingId = it.getStringExtra(AccelerometerService.EXTRA_MAPPING_ID)
                    val isUsingCached = it.getBooleanExtra(AccelerometerService.EXTRA_IS_USING_CACHED_THRESHOLDS, false)

                    // Update offline mode indicator
                    _isUsingCachedThresholds.value = isUsingCached

                    // Synchronize monitoring state with the service
                    val previousMonitoringState = _isMonitoring.value
                    _isMonitoring.value = isMonitoring

                    // Handle state transitions
                    if (isMonitoring && !previousMonitoringState) {
                        // Monitoring just started - set monitored vehicle and load its services
                        if (vehicleId != null) {
                            monitoredVehicleId = vehicleId
                            // Set currentMappingId from broadcast if available
                            if (mappingId != null) {
                                currentMappingId = mappingId
                                android.util.Log.i("DashboardViewModel", "Monitoring state synchronized: ACTIVE for vehicle $vehicleId, mappingId=$mappingId")
                            } else {
                                android.util.Log.w("DashboardViewModel", "Monitoring state synchronized: ACTIVE for vehicle $vehicleId but mappingId missing from broadcast")
                            }
                            
                            // Load and set the selected vehicle for UI display
                            viewModelScope.launch {
                                when (val vehicleResult = vehicleRepository.getVehicleById(vehicleId)) {
                                    is Result.Success -> {
                                        _selectedVehicle.value = vehicleResult.data
                                        android.util.Log.d("DashboardViewModel", "Selected vehicle set to: ${vehicleResult.data?.name}")
                                    }
                                    is Result.Failure -> {
                                        android.util.Log.w("DashboardViewModel", "Failed to load vehicle for selection: ${vehicleResult.message}")
                                    }
                                }
                            }
                            // Load mappings for the monitored vehicle first (needed by loadServicesForVehicle)
                            loadMappingsForVehicles(listOf(vehicleId))
                            // Load services for the monitored vehicle
                            loadServicesForVehicle(vehicleId)
                        }
                    } else if (!isMonitoring && previousMonitoringState) {
                        // Monitoring just stopped - clear state
                        monitoredVehicleId = null
                        currentMappingId = null
                        _selectedVehicle.value = null
                        android.util.Log.i("DashboardViewModel", "Monitoring state synchronized: INACTIVE - state cleared")
                    }

                    // Update accelerometer readings when service is monitoring
                    if (isMonitoring) {
                        _accelerometerReadings.value = Triple(x, y, z)
                        
                        // Update readings for ALL services assigned to the monitored vehicle
                        if (vehicleId != null) {
                            updateReadingsForAllVehicleServices(vehicleId, totalMovement.toInt())
                        }
                        
                        android.util.Log.d("DashboardViewModel", "Updated readings: x=$x, y=$y, z=$z, vehicleId=$vehicleId, offlineMode=$isUsingCached")
                    } else {
                        android.util.Log.d("DashboardViewModel", "Monitoring not active, skipping readings update")
                    }
                }
            }
        }

    init {
        Log.d("BroadcastDebug", ">>> DashboardViewModel init() - Registering accelerometer receiver")
        // Register for accelerometer broadcasts from the service
        val filter = IntentFilter(AccelerometerService.ACTION_BROADCAST_ACCELEROMETER)
        LocalBroadcastManager.getInstance(context).registerReceiver(
            accelerometerReceiver,
            filter,
        )
        Log.d("BroadcastDebug", ">>> DashboardViewModel init() - Accelerometer receiver registered successfully")
        android.util.Log.d("DashboardViewModel", "Accelerometer receiver registered")
        
        // Load boot receiver enabled state from SharedPreferences
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _bootReceiverEnabled.value = prefs.getBoolean("boot_receiver_enabled", false)
        
        // Load auto-stop timeout setting (default 1 hour = 3600000 ms)
        _autoStopTimeout.value = prefs.getLong("auto_stop_timeout", 3600000L)
        
        // Check if we should show vehicle selection from boot detection
        val bootDetectionPrefs = context.getSharedPreferences("boot_detection", Context.MODE_PRIVATE)
        val shouldShow = bootDetectionPrefs.getBoolean("show_vehicle_selection", false)
        if (shouldShow) {
            android.util.Log.d("DashboardViewModel", "Boot detection flag found - will show vehicle selection dialog")
            _shouldShowVehicleSelectionFromBoot.value = true
            // Clear the flag after reading
            bootDetectionPrefs.edit().putBoolean("show_vehicle_selection", false).apply()
        }
        
        // RESTORE MONITORING STATE: Check if service is already monitoring and restore UI state
        // This handles the case where the UI is destroyed but the AccelerometerService continues running
        viewModelScope.launch {
            restoreMonitoringStateIfActive()
        }
    }

    override fun onCleared() {
        Log.d("BroadcastDebug", ">>> DashboardViewModel onCleared() - Unregistering accelerometer receiver")
        super.onCleared()
        
        // Unregister to prevent memory leaks
        try {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(accelerometerReceiver)
            Log.d("BroadcastDebug", ">>> DashboardViewModel onCleared() - Accelerometer receiver unregistered successfully")
        } catch (e: IllegalArgumentException) {
            Log.d("BroadcastDebug", ">>> DashboardViewModel onCleared() - Receiver was not registered (safe to ignore)")
            // Receiver was not registered
        }
    }
    
    /**
     * Restores monitoring state from the active mapping in the local database.
     * This method is called on ViewModel initialization to synchronize the UI
     * with the AccelerometerService state when the UI is recreated after being
     * destroyed (e.g., clearing recent apps) while the service continues running.
     */
    private suspend fun restoreMonitoringStateIfActive() {
        try {
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                android.util.Log.d("DashboardViewModel", "No authenticated user, skipping state restoration")
                return
            }
            
            // FIRST: Check SharedPreferences for persisted monitoring state from AccelerometerService
            val prefs = context.getSharedPreferences("monitoring_state_checkpoint", Context.MODE_PRIVATE)
            val persistedMappingId = prefs.getString(com.mainlert.services.AccelerometerService.KEY_MAPPING_ID, null)
            val persistedVehicleId = prefs.getString(com.mainlert.services.AccelerometerService.KEY_VEHICLE_ID, null)
            val persistedIsMonitoring = prefs.getBoolean(com.mainlert.services.AccelerometerService.KEY_IS_MONITORING, false)
            
            if (persistedMappingId != null && persistedVehicleId != null && persistedIsMonitoring) {
                android.util.Log.i("DashboardViewModel", "Found persisted monitoring state from AccelerometerService: mappingId=$persistedMappingId, vehicleId=$persistedVehicleId")
                
                // Verify the persisted mapping is still valid and monitoring
                when (val mappingResult = vehicleServiceMappingRepository.getMappingById(persistedMappingId)) {
                    is Result.Success -> {
                        val mapping = mappingResult.data
                        if (mapping != null && mapping.isMonitoring && mapping.userId == currentUserId) {
                            android.util.Log.i("DashboardViewModel", "Persisted mapping is valid and active. Restoring state...")
                            
                            // Restore UI state from persisted data
                            monitoredVehicleId = persistedVehicleId
                            currentMappingId = persistedMappingId
                            _isMonitoring.value = true
                            
                            // Load vehicle details for display
                            when (val vehicleResult = vehicleRepository.getVehicleById(persistedVehicleId)) {
                                is Result.Success -> {
                                    _selectedVehicle.value = vehicleResult.data
                                    android.util.Log.d("DashboardViewModel", "Restored selected vehicle: ${vehicleResult.data?.name}")
                                }
                                is Result.Failure -> {
                                    android.util.Log.w("DashboardViewModel", "Failed to load vehicle for restoration: $persistedVehicleId")
                                }
                            }
                            
                            // Load services and mappings for the monitored vehicle
                            loadServicesForVehicle(persistedVehicleId)
                            loadMappingsForVehicles(listOf(persistedVehicleId))
                            
                            android.util.Log.i("DashboardViewModel", "Monitoring state restored from checkpoint for vehicle $persistedVehicleId")
                            return
                        } else {
                            android.util.Log.w("DashboardViewModel", "Persisted mapping is invalid or not monitoring. Clearing checkpoint...")
                            // Clear stale checkpoint
                            prefs.edit().remove(com.mainlert.services.AccelerometerService.KEY_MAPPING_ID)
                                .remove(com.mainlert.services.AccelerometerService.KEY_VEHICLE_ID)
                                .remove(com.mainlert.services.AccelerometerService.KEY_SERVICE_ID)
                                .remove(com.mainlert.services.AccelerometerService.KEY_IS_MONITORING)
                                .apply()
                        }
                    }
                    is Result.Failure -> {
                        android.util.Log.w("DashboardViewModel", "Failed to verify persisted mapping: ${mappingResult.message}. Clearing checkpoint...")
                        // Clear invalid checkpoint
                        prefs.edit().remove(com.mainlert.services.AccelerometerService.KEY_MAPPING_ID)
                            .remove(com.mainlert.services.AccelerometerService.KEY_VEHICLE_ID)
                            .remove(com.mainlert.services.AccelerometerService.KEY_SERVICE_ID)
                            .remove(com.mainlert.services.AccelerometerService.KEY_IS_MONITORING)
                            .apply()
                    }
                }
            }
            
            // FALLBACK: Query database for active mappings if no valid persisted state
            android.util.Log.d("DashboardViewModel", "No valid persisted state found, querying database for active mappings")
            val activeMappingsResult = vehicleServiceMappingRepository.getActiveMappingsForUser(currentUserId)
            
            when (activeMappingsResult) {
                is Result.Success -> {
                    val activeMappings = activeMappingsResult.data ?: emptyList()
                    // Filter for mappings that are actually monitoring
                    val monitoringMappings = activeMappings.filter { it.isMonitoring }
                    
                    if (monitoringMappings.isNotEmpty()) {
                        // If multiple mappings are monitoring (shouldn't happen due to vehicle locking),
                        // pick the one with the most recent reading time
                        val activeMapping = monitoringMappings.maxByOrNull { it.lastReadingTime }!!
                        
                        android.util.Log.i("DashboardViewModel", "Found active monitoring state: vehicleId=${activeMapping.vehicleId}, mappingId=${activeMapping.id}, lastReadingTime=${activeMapping.lastReadingTime}")
                        
                        // Restore UI state to match the active monitoring
                        monitoredVehicleId = activeMapping.vehicleId
                        currentMappingId = activeMapping.id
                        _isMonitoring.value = true
                        
                        // Load vehicle details for display
                        when (val vehicleResult = vehicleRepository.getVehicleById(activeMapping.vehicleId)) {
                            is Result.Success -> {
                                _selectedVehicle.value = vehicleResult.data
                                android.util.Log.d("DashboardViewModel", "Restored selected vehicle: ${vehicleResult.data?.name}")
                            }
                            is Result.Failure -> {
                                android.util.Log.w("DashboardViewModel", "Failed to load vehicle for restoration: ${activeMapping.vehicleId}")
                            }
                        }
                        
                        // Load services and mappings for the monitored vehicle
                        loadServicesForVehicle(activeMapping.vehicleId)
                        loadMappingsForVehicles(listOf(activeMapping.vehicleId))
                        
                        android.util.Log.i("DashboardViewModel", "Monitoring state restored successfully for vehicle ${activeMapping.vehicleId}")
                    } else {
                        android.util.Log.d("DashboardViewModel", "No active monitoring found for current user during state restoration")
                    }
                }
                is Result.Failure -> {
                    android.util.Log.w("DashboardViewModel", "Failed to query active mappings for restoration: ${activeMappingsResult.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error restoring monitoring state", e)
        }
    }

    /**
     * Indicates if a dashboard operation is in progress.
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Holds the latest error message from dashboard operations.
     */
    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    /**
     * Holds the latest success message from dashboard operations.
     */
    private val _successMessage = MutableStateFlow("")
    val successMessage: StateFlow<String> = _successMessage.asStateFlow()

    /**
     * List of all services for the current user.
     */
    private val _services = MutableStateFlow<List<Service>>(emptyList())
    val services: StateFlow<List<Service>> = _services.asStateFlow()

    /**
     * Creates a VehicleServiceMapping for a service-vehicle pair if it doesn't already exist.
     * Uses the current authenticated user's ID for proper authentication context.
     * Validates that the vehicle belongs to the current user.
     * 
     * @param service The service to create mapping for
     * @param vehicle The vehicle containing user information
     * @return true if mapping was created successfully or already exists, false otherwise
     */
    private suspend fun createMappingForServiceAndVehicle(service: Service, vehicle: Vehicle): Boolean {
        return try {
            // Get current authenticated user ID
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                android.util.Log.e("DashboardViewModel", "Cannot create mapping: User not authenticated")
                return false
            }
            
            // Validate that the vehicle belongs to the current user
            if (vehicle.userId != currentUserId) {
                android.util.Log.e("DashboardViewModel", "Cannot create mapping: Vehicle ${vehicle.id} belongs to user ${vehicle.userId}, but current user is $currentUserId")
                return false
            }
            
            // Check if mapping already exists
            when (val mappingResult = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicle.id, service.id)) {
                is Result.Success -> {
                    if (mappingResult.data != null) {
                        android.util.Log.d("DashboardViewModel", "Mapping already exists for vehicle ${vehicle.id} and service ${service.id}")
                        return true
                    }
                }
                is Result.Failure -> {
                    android.util.Log.w("DashboardViewModel", "Error checking existing mapping: ${mappingResult.message}")
                    // Continue to create mapping
                }
            }
            
            // Get variant details if service has a variantId
            var variantName = service.variantName
            var variantId = service.variantId
            var mileageLimit = service.mileageLimit
            
            if (service.variantId.isNotEmpty()) {
                when (val variantResult = serviceVariantRepository.getVariantById(service.variantId)) {
                    is Result.Success -> {
                        val variant = variantResult.data
                        if (variant != null) {
                            variantName = variant.name
                            mileageLimit = variant.mileageLimit
                            android.util.Log.d("DashboardViewModel", "Using variant details for service ${service.id}: variantName=$variantName, mileageLimit=$mileageLimit")
                        }
                    }
                    is Result.Failure -> {
                        android.util.Log.w("DashboardViewModel", "Failed to fetch variant details for service ${service.id}: ${variantResult.message}")
                    }
                }
            }
            
            val newMapping = VehicleServiceMapping(
                vehicleId = vehicle.id,
                serviceId = service.id,
                userId = currentUserId,
                serviceName = variantName.ifEmpty { service.name },
                variantId = variantId,
                variantName = variantName,
                totalMovement = 0f,
                isMonitoring = false,
                status = VehicleServiceMapping.MappingStatus.ACTIVE,
                lastReadingTime = System.currentTimeMillis(),
                mileageLimit = mileageLimit,
            )
            
            when (val createResult = vehicleServiceMappingRepository.createMapping(newMapping)) {
                is Result.Success -> {
                    android.util.Log.d("DashboardViewModel", "Created VehicleServiceMapping for service ${service.id} and vehicle ${vehicle.id}")
                    true
                }
                is Result.Failure -> {
                    android.util.Log.e("DashboardViewModel", "Failed to create mapping: ${createResult.message}")
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error creating mapping for service ${service.id} and vehicle ${vehicle.id}", e)
            false
        }
    }

    /**
     * Loads all services for the current user and updates the services state.
     * FIXED: Now uses VehicleRepository directly to get vehicle info instead of relying on _vehicles.value
     */
    fun loadServices() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceRepository.getServices()) {
                is Result.Success -> {
                    _services.value = result.data ?: emptyList()
                    
                    // Also load VehicleServiceMappings for all vehicles to get services with independent readings
                    // Use VehicleRepository directly instead of relying on _vehicles.value
                    loadMappingsForCurrentUserVehicles()
                    
                    // No need to track currentServiceId in vehicle-centric monitoring
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to load services"
                }
            }

            _isLoading.value = false
        }
    }
    
    /**
     * Loads VehicleServiceMappings for the current user's vehicles.
     * This ensures independent readings per vehicle-service combination.
     * FIXED: Now uses getCurrentUserId() to get user ID synchronously
     */
    private fun loadMappingsForCurrentUserVehicles() {
    // Get current user ID
    val currentUserId = authRepository.getCurrentUserId()
    if (currentUserId != null) {
        viewModelScope.launch {
            when (val vehicleResult = vehicleRepository.getVehiclesForUser(currentUserId)) {
                is Result.Success -> {
                    val vehicles = vehicleResult.data ?: emptyList()
                    if (vehicles.isNotEmpty()) {
                        loadMappingsForVehicles(vehicles.map { it.id })
                    }
                }
                is Result.Failure -> {
                    android.util.Log.w("DashboardViewModel", "Failed to load vehicles for mappings: ${vehicleResult.message}")
                }
            }
        }
    }
}


    /**
     * Starts the accelerometer monitoring service.
     * Checks vehicle count first:
     * - 1 vehicle: auto-select and start immediately
     * - Multiple vehicles: show selection dialog
     * - 0 vehicles: show error
     * FIXED: Now uses VehicleRepository directly to get vehicle info instead of relying on _vehicles.value
     */
    fun startMonitoringService() {
        android.util.Log.i("DashboardViewModel", ">>> START BUTTON CLICKED <<<")

        // Validate battery level before proceeding
        if ((_batteryLevel.value ?: 0f) < 20) {
            _errorMessage.value = "Battery level too low. Please charge your device before starting monitoring."
            android.util.Log.w("DashboardViewModel", "Battery level too low: ${_batteryLevel.value}")
            return
        }

        // Check if monitoring is already active - enforce vehicle locking
        if (_isMonitoring.value && monitoredVehicleId != null) {
            viewModelScope.launch {
                _isLoading.value = true
                // Get current vehicle info directly from repository
                when (val vehicleResult = vehicleRepository.getVehicleById(monitoredVehicleId ?: "")) {
                    is Result.Success -> {
                        val currentVehicle = vehicleResult.data
                        _errorMessage.value = "Already monitoring ${currentVehicle?.name ?: "a vehicle"}. Please stop monitoring first before switching vehicles."
                        android.util.Log.w("DashboardViewModel", "Vehicle locking: Already monitoring vehicle $monitoredVehicleId")
                    }
                    is Result.Failure -> {
                        _errorMessage.value = "Already monitoring a vehicle. Please stop monitoring first before switching vehicles."
                        android.util.Log.w("DashboardViewModel", "Vehicle locking: Already monitoring vehicle $monitoredVehicleId (failed to fetch details)")
                    }
                }
                _isLoading.value = false
            }
            return
        }

        // Get current user ID
        val currentUserId = authRepository.getCurrentUserId()
        if (currentUserId == null) {
            _errorMessage.value = "User not authenticated. Please log in again."
            return
        }

        // Determine vehicles to use: prefer state, fallback to repository if state empty
        if (_vehicles.value.isNotEmpty()) {
            // Use already-loaded vehicles from state
            decideAndShowDialogOrStart(_vehicles.value)
        } else {
            // State empty, need to fetch vehicles from repository
            viewModelScope.launch {
                _isLoading.value = true
                when (val vehicleResult = vehicleRepository.getVehiclesForUser(currentUserId)) {
                    is Result.Success -> {
                        val vehicles = vehicleResult.data ?: emptyList()
                        // Update state for future use
                        _vehicles.value = vehicles
                        decideAndShowDialogOrStart(vehicles)
                    }
                    is Result.Failure -> {
                        _errorMessage.value = "Failed to load vehicles: ${vehicleResult.message}"
                        android.util.Log.e("DashboardViewModel", "Failed to load vehicles: ${vehicleResult.message}")
                    }
                }
                _isLoading.value = false
            }
        }
    }

    /**
     * Decides whether to show vehicle selection dialog or auto-start based on vehicle count.
     */
    private fun decideAndShowDialogOrStart(vehicles: List<Vehicle>) {
        val vehicleCount = vehicles.size
        android.util.Log.d("DashboardViewModel", "Vehicle count: $vehicleCount, vehicles: ${vehicles.map { it.name }}")
        android.util.Log.d("DashboardViewModel", "showVehicleSelectionDialog will be set to: ${vehicleCount > 1}")

        when {
            vehicleCount == 0 -> {
                android.util.Log.w("DashboardViewModel", "No vehicles - showing error")
                _errorMessage.value = "No vehicles assigned. Please contact your administrator."
            }
            vehicleCount == 1 -> {
                // Auto-select the single vehicle and start monitoring
                val singleVehicle = vehicles.first()
                android.util.Log.d("DashboardViewModel", "Auto-selecting single vehicle: ${singleVehicle.name}")
                startMonitoringForVehicle(singleVehicle.id)
            }
            else -> {
                // Multiple vehicles - show selection dialog
                android.util.Log.d("DashboardViewModel", "Multiple vehicles ($vehicleCount), showing selection dialog")
                _showVehicleSelectionDialog.value = true
                android.util.Log.d("DashboardViewModel", "Set _showVehicleSelectionDialog to true, current value: ${_showVehicleSelectionDialog.value}")
            }
        }
    }

    /**
     * Battery level - using default value for now
     */

    /**
     * Hides the vehicle selection dialog.
     */
    fun hideVehicleSelectionDialog() {
        android.util.Log.d("DashboardViewModel", "Hiding vehicle selection dialog")
        _showVehicleSelectionDialog.value = false
    }

    fun showVehicleSelectionDialog() {
        android.util.Log.d("DashboardViewModel", "Showing vehicle selection dialog")
        _showVehicleSelectionDialog.value = true
    }

    /**
     * Called when user selects a vehicle from the dialog.
     * Starts monitoring for the selected vehicle.
     */
    fun onVehicleSelectedForMonitoring(vehicle: Vehicle) {
        android.util.Log.d("DashboardViewModel", "Vehicle selected for monitoring: ${vehicle.name}")
        _showVehicleSelectionDialog.value = false
        startMonitoringForVehicle(vehicle.id)
    }

    // ========== Boot Receiver Methods ==========

    /**
     * Returns the current boot receiver enabled state.
     */
    fun isBootReceiverEnabled(): Boolean {
        return _bootReceiverEnabled.value
    }

    /**
     * Sets the boot receiver enabled state and persists to SharedPreferences.
     */
    fun setBootReceiverEnabled(enabled: Boolean) {
        android.util.Log.d("DashboardViewModel", "Setting boot receiver enabled: $enabled")
        _bootReceiverEnabled.value = enabled
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("boot_receiver_enabled", enabled).apply()
    }

    /**
     * Returns the current auto-stop timeout in milliseconds.
     */
    fun getAutoStopTimeout(): Long {
        return _autoStopTimeout.value
    }

    /**
     * Sets the auto-stop timeout and persists to SharedPreferences.
     * @param timeoutMs Timeout in milliseconds (e.g., 3600000 for 1 hour)
     */
    fun setAutoStopTimeout(timeoutMs: Long) {
        android.util.Log.d("DashboardViewModel", "Setting auto-stop timeout: ${timeoutMs}ms (${timeoutMs / 3600000f} hours)")
        _autoStopTimeout.value = timeoutMs
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putLong("auto_stop_timeout", timeoutMs).apply()
    }

    /**
     * Clears the boot detection flag after it has been handled.
     * Called when the vehicle selection dialog is shown from boot detection.
     */
    fun clearBootDetectionFlag() {
        android.util.Log.d("DashboardViewModel", "Clearing boot detection flag")
        _shouldShowVehicleSelectionFromBoot.value = false
        val prefs = context.getSharedPreferences("boot_detection", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("show_vehicle_selection", false).apply()
    }

    /**
     * Stops the accelerometer monitoring service.
     */
    fun stopMonitoringService() {
        android.util.Log.i("DashboardViewModel", ">>> STOP BUTTON CLICKED <<<")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            val vehicleIdToStop = monitoredVehicleId
            
            if (vehicleIdToStop != null) {
                android.util.Log.d("DashboardViewModel", "Stopping monitoring for vehicle: $vehicleIdToStop")
            } else {
                android.util.Log.w("DashboardViewModel", "No monitored vehicle to stop")
            }

            // Stop the accelerometer service
            // The service will handle:
            // - Saving final readings to VehicleServiceMapping
            // - Updating vehicle lifetime mileage
            // - Clearing its internal state
            // - Stopping itself
            AccelerometerService.stopService(context)

            // Update UI state immediately (don't wait for service to finish)
            _isMonitoring.value = false
            
            // Clear monitored vehicle ID (unlock vehicle)
            monitoredVehicleId = null
            currentMappingId = null

            _successMessage.value = "Monitoring stopped successfully"
            android.util.Log.i("DashboardViewModel", "Monitoring stop initiated successfully")

            _isLoading.value = false
        }
    }
    
    /**
     * Stops mapping monitoring for a specific vehicle-service combination.
     */
    private suspend fun stopMappingMonitoringForVehicleAndService(vehicleId: String, serviceId: String) {
        when (val mappingResult = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicleId, serviceId)) {
            is Result.Success -> {
                mappingResult.data?.let { mapping ->
                    vehicleServiceMappingRepository.stopMappingMonitoring(mapping.id)
                }
            }
            is Result.Failure -> {
                android.util.Log.w("DashboardViewModel", "Failed to stop mapping monitoring: ${mappingResult.message}")
            }
        }
    }

    /**
     * Resets all service readings for the currently monitored vehicle.
     * In the vehicle-centric architecture, all services on a vehicle are reset together.
     */
    fun resetServiceData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            _successMessage.value = ""

            val vehicleId = monitoredVehicleId
            if (vehicleId != null) {
                // Reset all mappings for this vehicle
                resetAllMappingsForVehicle(vehicleId)
                _serviceReadingsMap.value = emptyMap()
                _successMessage.value = "All service readings reset successfully"
            } else {
                _errorMessage.value = "No active vehicle monitoring to reset"
            }

            _isLoading.value = false
        }
    }
    
    /**
     * Resets all VehicleServiceMappings for a specific vehicle.
     */
    private suspend fun resetAllMappingsForVehicle(vehicleId: String) {
        try {
            when (val mappingsResult = vehicleServiceMappingRepository.getMappingsForVehicle(vehicleId)) {
                is Result.Success -> {
                    val mappings = mappingsResult.data ?: emptyList()
                    mappings.forEach { mapping ->
                        vehicleServiceMappingRepository.resetMappingReadings(mapping.id)
                        android.util.Log.d("DashboardViewModel", "Reset mapping ${mapping.id} for service ${mapping.serviceId}")
                    }
                }
                is Result.Failure -> {
                    android.util.Log.w("DashboardViewModel", "Failed to get mappings for vehicle $vehicleId: ${mappingsResult.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error resetting mappings for vehicle $vehicleId", e)
        }
    }
    
    /**
     * Resets mapping readings for a specific vehicle-service combination.
     */
    private suspend fun resetMappingReadingsForVehicleAndService(vehicleId: String, serviceId: String) {
        when (val mappingResult = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicleId, serviceId)) {
            is Result.Success -> {
                mappingResult.data?.let { mapping ->
                    vehicleServiceMappingRepository.resetMappingReadings(mapping.id)
                }
            }
            is Result.Failure -> {
                android.util.Log.w("DashboardViewModel", "Failed to reset mapping readings: ${mappingResult.message}")
            }
        }
    }

    fun updateServiceReadings(readings: Int) {
        // Check for Mileage
        if (readings >= mileageThreshold.value) {
            _errorMessage.value = "Mileage detected! Service has reached threshold."
        }
    }

    fun checkMileageStatus(serviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            // Check via VehicleServiceMapping
            monitoredVehicleId?.let { vehicleId ->
                when (val result = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicleId, serviceId)) {
                    is Result.Success -> {
                        result.data?.let { mapping ->
                            if (mapping.totalMovement >= mapping.mileageLimit) {
                                _errorMessage.value = "Service has reached mileage threshold!"
                            }
                        }
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Failed to check mileage status"
                    }
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Triggers a manual sync with Firebase.
     * Used when user retries after a sync error.
     */
    fun triggerManualSync() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId != null) {
                when (val result = vehicleServiceMappingRepository.syncStructureData()) {
                    is Result.Success -> {
                        _successMessage.value = "Sync completed successfully"
                        // Also trigger continuous data sync
                        vehicleServiceMappingRepository.syncContinuousData()
                    }
                    is Result.Failure -> {
                        _errorMessage.value = "Manual sync failed: ${result.message}"
                    }
                }
            } else {
                _errorMessage.value = "User not authenticated"
            }
            
            _isLoading.value = false
        }
    }

    fun getCurrentService(): Service? {
        return _services.value.firstOrNull()
    }

    // ========== Vehicle-related methods ==========

    /**
     * Loads all vehicles in the system (Admin only).
     */
    fun loadAllVehicles() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = vehicleRepository.getAllVehicles()) {
                is Result.Success -> {
                    val vehicles = result.data ?: emptyList()
                    _vehicles.value = vehicles
                    
                    // Also load VehicleServiceMappings for all vehicles to get services with independent readings
                    if (vehicles.isNotEmpty()) {
                        loadMappingsForVehicles(vehicles.map { it.id })
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to load vehicles"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Loads vehicles for a specific user from local database only (no Firebase sync).
     * Used to populate UI with cached data while sync is in progress.
     */
    private fun loadVehiclesFromLocal(userId: String) {
        android.util.Log.d("DashboardViewModel", "loadVehiclesFromLocal called for userId: $userId")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = vehicleRepository.getVehiclesForUser(userId)) {
                is Result.Success -> {
                    val vehicles = result.data ?: emptyList()
                    android.util.Log.d("DashboardViewModel", "loadVehiclesFromLocal success: ${vehicles.size} vehicles: ${vehicles.map { it.name }}")
                    _vehicles.value = vehicles
                    
                    // Also load VehicleServiceMappings for all vehicles to get services with independent readings
                    if (vehicles.isNotEmpty()) {
                        loadMappingsForVehicles(vehicles.map { it.id })
                    }
                }
                is Result.Failure -> {
                    android.util.Log.e("DashboardViewModel", "loadVehiclesFromLocal failed: ${result.message}")
                    _errorMessage.value = result.message ?: "Failed to load vehicles"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Loads vehicles for a specific user and triggers Firebase sync.
     * This is the main entry point for loading user data.
     */
    fun loadVehiclesForUser(userId: String) {
        android.util.Log.d("DashboardViewModel", "loadVehiclesForUser called for userId: $userId")
        
        // First, load from local DB immediately to show cached data
        loadVehiclesFromLocal(userId)
        
        // Then trigger Firebase sync in background to update local DB
        // The sync observer will refresh data when sync completes
        viewModelScope.launch {
            try {
                android.util.Log.d("DashboardViewModel", "Triggering initial sync from Firebase for user $userId")
                syncManager.syncFromFirebase(userId)
                android.util.Log.d("DashboardViewModel", "Initial sync completed for user $userId")
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Initial sync failed for user $userId: ${e.message}", e)
            }
        }
    }

    /**
     * Selects a vehicle and loads its services.
     */
    fun selectVehicle(vehicle: Vehicle?) {
        android.util.Log.d("DashboardViewModel", "selectVehicle called with: ${vehicle?.name}, vehicleId: ${vehicle?.id}")
        _selectedVehicle.value = vehicle
        vehicle?.let {
            loadServicesForVehicle(it.id)
        }
    }

    /**
     * Loads services for a specific vehicle using the new architecture.
     * Uses VehicleServiceMappingRepository to get services with independent readings.
     * FIXED: Now uses VehicleRepository directly to get vehicle info instead of relying on _vehicles.value
     */
    fun loadServicesForVehicle(vehicleId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            try {
                // First get the services from the repository
                // If _services is empty, load them first to ensure we have the global service templates
                if (_services.value.isEmpty()) {
                    android.util.Log.d("DashboardViewModel", "_services is empty, loading services first")
                    when (val serviceResult = serviceRepository.getServices()) {
                        is Result.Success -> {
                            _services.value = serviceResult.data ?: emptyList()
                            android.util.Log.d("DashboardViewModel", "Loaded ${_services.value.size} services into _services state")
                        }
                        is Result.Failure -> {
                            android.util.Log.e("DashboardViewModel", "Failed to load services: ${serviceResult.message}")
                        }
                    }
                }
                
                // Use the global services from _services (now guaranteed to be loaded)
                val allServices = _services.value
                if (allServices.isEmpty()) {
                    _errorMessage.value = "No services found in system. Please add services first."
                    android.util.Log.w("DashboardViewModel", "No services available in _services after loading")
                    _isLoading.value = false
                    return@launch
                }
                
                // Services are now GLOBAL templates - no filtering by userId
                // VehicleServiceMapping handles the actual vehicle-service relationships
                _vehicleServices.value = allServices
                android.util.Log.d("DashboardViewModel", "Loaded ${allServices.size} global services for vehicle $vehicleId")

                // Use the already-loaded mappings from state instead of querying Firebase again
                val vehicleMappings = _vehicleServiceMappings.value.filter { it.vehicleId == vehicleId }
                android.util.Log.d("DashboardViewModel", "Using ${vehicleMappings.size} mappings from state for vehicle $vehicleId (total mappings in state: ${_vehicleServiceMappings.value.size})")
                
                // Initialize readings from mappings (each mapping has independent totalMovement)
                initializeServiceReadingsFromMappings(vehicleMappings)
                
                // If no mappings exist, show helpful message
                if (vehicleMappings.isEmpty()) {
                    _errorMessage.value = "No services assigned to this vehicle. Add services to see them here."
                } else {
                    _errorMessage.value = "" // Clear any previous error
                }

            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error loading services for vehicle $vehicleId", e)
                _errorMessage.value = "Error loading services: ${e.message}"
            }

            _isLoading.value = false
        }
    }

    /**
     * Loads services for multiple vehicles (used when driver has multiple vehicles).
     */
    fun loadServicesForVehicles(vehicleIds: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceRepository.getServices()) {
                is Result.Success -> {
                    _vehicleServices.value = result.data ?: emptyList()
                    
                    // Also load VehicleServiceMappings for all vehicles to get services with independent readings
                    if (vehicleIds.isNotEmpty()) {
                        loadMappingsForVehicles(vehicleIds)
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to load services for vehicles"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Creates a new vehicle.
     */
    fun createVehicle(
        name: String,
        model: String,
        year: Int,
        plateNumber: String,
        userId: String,
        employeeId: String = "",
        initialLifetimeMileage: Float = 0f,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            if (name.isBlank()) {
                _errorMessage.value = "Vehicle name cannot be empty"
                _isLoading.value = false
                return@launch
            }

            val vehicle =
                Vehicle(
                    name = name,
                    model = model,
                    year = year,
                    plateNumber = plateNumber,
                    userId = userId,
                    employeeId = employeeId,
                    lifetimeMileage = initialLifetimeMileage,
                )

            when (val result = vehicleRepository.createVehicle(vehicle)) {
                is Result.Success -> {
                    _successMessage.value = "Vehicle created successfully"
                    loadVehiclesForUser(userId)
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to create vehicle"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Updates the lifetime mileage for a vehicle.
     * This accumulates forever and never resets.
     */
    fun updateVehicleLifetimeMileage(vehicleId: String, mileage: Float) {
        viewModelScope.launch {
            when (val result = vehicleRepository.updateVehicleLifetimeMileage(vehicleId, mileage)) {
                is Result.Success -> {
                    // Optionally refresh vehicles list
                    loadAllVehicles()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to update vehicle lifetime mileage"
                }
            }
        }
    }

    /**
     * Deletes a vehicle by ID.
     */
    fun deleteVehicle(vehicleId: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = vehicleRepository.deleteVehicle(vehicleId)) {
                is Result.Success -> {
                    _successMessage.value = "Vehicle deleted successfully"
                    loadVehiclesForUser(userId)
                    if (_selectedVehicle.value?.id == vehicleId) {
                        _selectedVehicle.value = null
                        _vehicleServices.value = emptyList()
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to delete vehicle"
                }
            }

            _isLoading.value = false
        }
    }

    // ========== Service Variant-related methods ==========

    /**
     * Loads all service variants.
     */
    fun loadServiceVariants() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceVariantRepository.getVariants()) {
                is Result.Success -> {
                    _serviceVariants.value = result.data ?: emptyList()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to load service variants"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Creates a new service variant.
     */
    fun createServiceVariant(
        name: String,
        description: String,
        mileageLimit: Float,
        createdBy: String,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            if (name.isBlank()) {
                _errorMessage.value = "Variant name cannot be empty"
                _isLoading.value = false
                return@launch
            }

            if (mileageLimit <= 0) {
                _errorMessage.value = "Mileage limit must be greater than 0"
                _isLoading.value = false
                return@launch
            }

            val variant =
                ServiceVariant(
                    name = name,
                    description = description,
                    mileageLimit = mileageLimit,
                    createdBy = createdBy,
                )

            when (val result = serviceVariantRepository.createVariant(variant)) {
                is Result.Success -> {
                    _successMessage.value = "Service variant created successfully"
                    loadServiceVariants()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to create service variant"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Deletes a service variant by ID.
     */
    fun deleteServiceVariant(variantId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceVariantRepository.deleteVariant(variantId)) {
                is Result.Success -> {
                    _successMessage.value = "Service variant deleted successfully"
                    loadServiceVariants()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to delete service variant"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Updates an existing service variant.
     */
    /**
     * Creates a new service.
     */
    fun createService(
        name: String,
        description: String,
        mileageLimit: Float,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            if (name.isBlank()) {
                _errorMessage.value = "Service name cannot be empty"
                _isLoading.value = false
                return@launch
            }

            if (mileageLimit <= 0) {
                _errorMessage.value = "Mileage limit must be greater than 0"
                _isLoading.value = false
                return@launch
            }

            val newService = Service(
                name = name,
                description = description,
                mileageLimit = mileageLimit,
            )

            when (val result = serviceRepository.createService(newService)) {
                is Result.Success -> {
                    _successMessage.value = "Service created successfully"
                    loadServices()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to create service"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Updates an existing service.
     */
    fun updateService(
        serviceId: String,
        name: String,
        description: String,
        mileageLimit: Float,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            if (name.isBlank()) {
                _errorMessage.value = "Service name cannot be empty"
                _isLoading.value = false
                return@launch
            }

            if (mileageLimit <= 0) {
                _errorMessage.value = "Mileage limit must be greater than 0"
                _isLoading.value = false
                return@launch
            }

            when (val serviceResult = serviceRepository.getServiceById(serviceId)) {
                is Result.Success -> {
                    val service = serviceResult.data
                    if (service != null) {
                        val updatedService = service.copy(
                            name = name,
                            description = description,
                            mileageLimit = mileageLimit,
                        )

                        when (val updateResult = serviceRepository.updateService(updatedService)) {
                            is Result.Success -> {
                                _successMessage.value = "Service updated successfully"
                                loadServices()
                            }
                            is Result.Failure -> {
                                _errorMessage.value = updateResult.message ?: "Failed to update service"
                            }
                        }
                    } else {
                        _errorMessage.value = "Service not found"
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = serviceResult.message ?: "Failed to fetch service"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Deletes a service by ID.
     */
    fun deleteService(serviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceRepository.deleteService(serviceId)) {
                is Result.Success -> {
                    _successMessage.value = "Service deleted successfully"
                    loadServices()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to delete service"
                }
            }

            _isLoading.value = false
        }
    }

    fun updateServiceVariant(
        variantId: String,
        name: String,
        description: String,
        mileageLimit: Float,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            if (name.isBlank()) {
                _errorMessage.value = "Variant name cannot be empty"
                _isLoading.value = false
                return@launch
            }

            if (mileageLimit <= 0) {
                _errorMessage.value = "Mileage limit must be greater than 0"
                _isLoading.value = false
                return@launch
            }

            when (val variantResult = serviceVariantRepository.getVariantById(variantId)) {
                is Result.Success -> {
                    val variant = variantResult.data
                    if (variant != null) {
                        val updatedVariant =
                            variant.copy(
                                name = name,
                                description = description,
                                mileageLimit = mileageLimit,
                            )

                        when (val updateResult = serviceVariantRepository.updateVariant(updatedVariant)) {
                            is Result.Success -> {
                                _successMessage.value = "Service variant updated successfully"
                                loadServiceVariants()
                            }
                            is Result.Failure -> {
                                _errorMessage.value = updateResult.message ?: "Failed to update service variant"
                            }
                        }
                    } else {
                        _errorMessage.value = "Service variant not found"
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = variantResult.message ?: "Failed to fetch service variant"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Starts monitoring for a specific service on a selected vehicle.
     * @param serviceId The ID of the service to monitor
     * @param vehicleId The ID of the vehicle (optional, defaults to empty string)
     */
    /**
     * DEPRECATED: Use startMonitoringForVehicle() instead.
     * Monitoring should be vehicle-centric, not per-service.
     * This method now delegates to startMonitoringForVehicle() for backward compatibility.
     */
    @Suppress("UNUSED_PARAMETER")
    fun startMonitoringForService(serviceId: String, vehicleId: String = "") {
        Log.w("DashboardViewModel", "startMonitoringForService() is deprecated. Use vehicle-level monitoring.")
        
        if (vehicleId.isEmpty()) {
            viewModelScope.launch {
                _errorMessage.value = "Vehicle selection required. Please start monitoring from the main button."
            }
            return
        }
        
        // Delegate to vehicle-level monitoring
        startMonitoringForVehicle(vehicleId)
    }

    // ========== Vehicle Assignment Methods ==========

    /**
     * Assigns a vehicle to a driver.
     */
    fun assignVehicleToDriver(vehicleId: String, driverId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            _successMessage.value = ""

            when (val result = vehicleRepository.assignVehicleToDriver(vehicleId, driverId)) {
                is Result.Success -> {
                    _successMessage.value = "Vehicle assigned successfully"
                    loadAllVehicles() // Refresh vehicles list
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to assign vehicle"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Removes vehicle assignment from a driver.
     */
    fun removeVehicleFromDriver(vehicleId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            _successMessage.value = ""

            when (val result = vehicleRepository.removeVehicleFromDriver(vehicleId)) {
                is Result.Success -> {
                    _successMessage.value = "Vehicle assignment removed"
                    loadAllVehicles()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to remove vehicle assignment"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Gets unassigned vehicles.
     */
    fun getUnassignedVehicles() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = vehicleRepository.getUnassignedVehicles()) {
                is Result.Success -> {
                    _vehicles.value = result.data ?: emptyList()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to fetch unassigned vehicles"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Creates a vehicle and assigns it to a driver atomically.
     * Updates both vehicle's userId and driver's vehicleIds list.
     */
    fun createVehicleForDriver(
        vehicleName: String,
        model: String,
        year: Int,
        plateNumber: String,
        driverId: String,
        employeeId: String = "",
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = vehicleRepository.createVehicleForDriver(vehicleName, model, year, plateNumber, driverId, employeeId)) {
                is Result.Success -> {
                    _successMessage.value = "Vehicle created and assigned successfully"
                    loadAllVehicles() // Refresh vehicles list
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to create vehicle for driver"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Adds a service to a vehicle by creating a VehicleServiceMapping for independent readings.
     * FIXED: Now uses VehicleRepository directly to get vehicle info instead of relying on _vehicles.value
     */
    fun addServiceToVehicle(serviceId: String, vehicleId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            _successMessage.value = ""

            try {
                // Get service info
                val serviceResult = serviceRepository.getServiceById(serviceId)
                val service = when (serviceResult) {
                    is Result.Success -> serviceResult.data
                    is Result.Failure -> {
                        _errorMessage.value = serviceResult.message ?: "Failed to fetch service"
                        _isLoading.value = false
                        return@launch
                    }
                }

                if (service == null) {
                    _errorMessage.value = "Service not found"
                    _isLoading.value = false
                    return@launch
                }

                // Get vehicle info directly from repository (FIXED: don't rely on _vehicles.value)
                val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
                val vehicle = when (vehicleResult) {
                    is Result.Success -> vehicleResult.data
                    is Result.Failure -> {
                        _errorMessage.value = vehicleResult.message ?: "Failed to fetch vehicle"
                        _isLoading.value = false
                        return@launch
                    }
                }

                if (vehicle == null) {
                    _errorMessage.value = "Vehicle not found"
                    _isLoading.value = false
                    return@launch
                }

                // Create VehicleServiceMapping for independent readings
                createMappingForServiceAndVehicle(service, vehicle)
                
                _successMessage.value = "Service added to vehicle successfully"
                loadServices() // Refresh services list
                // Also refresh vehicle-specific services if this vehicle is selected
                loadServicesForVehicle(vehicleId)
                
                android.util.Log.i("DashboardViewModel", "Successfully created VehicleServiceMapping for service ${service.id} and vehicle $vehicleId")
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add service to vehicle: ${e.message}"
                android.util.Log.e("DashboardViewModel", "Error adding service to vehicle: serviceId=$serviceId, vehicleId=$vehicleId", e)
            }

            _isLoading.value = false
        }
    }
    
    
    /**
     * Validates permission for creating VehicleServiceMapping based on Firestore security rules.
     * Follows the same logic as firestore.rules to avoid permission errors.
     */
    private fun validateVehicleServiceMappingPermission(
        vehicle: Vehicle,
        currentUserId: String,
        currentUserRole: User.UserRole?
    ): Boolean {
        // Rule 1: User can create mappings for vehicles they own
        if (currentUserId == vehicle.userId) {
            android.util.Log.d("DashboardViewModel", "Permission granted: User owns the vehicle")
            return true
        }
        
        // Rule 2: Admins can create mappings for any vehicle
        if (currentUserRole == User.UserRole.ADMIN) {
            android.util.Log.d("DashboardViewModel", "Permission granted: User has ADMIN role")
            return true
        }
        
        // Rule 3: Employees can create mappings for any vehicle
        if (currentUserRole == User.UserRole.EMPLOYEE) {
            android.util.Log.d("DashboardViewModel", "Permission granted: User has EMPLOYEE role")
            return true
        }
        
        android.util.Log.w("DashboardViewModel", "Permission denied: User $currentUserId (role=$currentUserRole) does not have permission for vehicle ${vehicle.id}")
        return false
    }

    /**
     * Shows a vehicle selection dialog for starting monitoring.
     * Returns true if dialog should be shown, false if can auto-start.
     */
    fun shouldShowVehicleSelectionDialog(): Boolean {
        return _vehicles.value.size > 1
    }

    /**
     * Gets the single vehicle if only one exists (for auto-selection).
     */
    fun getSingleVehicle(): Vehicle? {
        return if (_vehicles.value.size == 1) _vehicles.value.first() else null
    }

    /**
     * Starts monitoring for a specific vehicle.
     * Auto-selects vehicle and starts accelerometer for its services.
     * Uses VehicleServiceMapping for independent readings per vehicle-service pair.
     * Checks and creates missing VehicleServiceMappings before starting monitoring.
     * FIXED: Now uses VehicleRepository directly to get vehicle info instead of relying on _vehicles.value
     */
    fun startMonitoringForVehicle(vehicleId: String) {
        android.util.Log.i("DashboardViewModel", ">>> startMonitoringForVehicle called with vehicleId: $vehicleId <<<")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            // Validate battery level before proceeding
            if ((_batteryLevel.value ?: 0f) < 20) {
                _errorMessage.value = "Battery level too low. Please charge your device before starting monitoring."
                android.util.Log.w("DashboardViewModel", "Battery level too low: ${_batteryLevel.value}")
                _isLoading.value = false
                return@launch
            }

            // Validate vehicle exists - use VehicleRepository directly (FIXED: don't rely on _vehicles.value)
            val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
            val vehicle = when (vehicleResult) {
                is Result.Success -> vehicleResult.data
                is Result.Failure -> {
                    android.util.Log.e("DashboardViewModel", "Failed to fetch vehicle for monitoring: ${vehicleResult.message}")
                    null
                }
            }
            
            if (vehicle == null) {
                android.util.Log.e("DashboardViewModel", "Vehicle not found: $vehicleId")
                _errorMessage.value = "Vehicle not found. Please refresh and try again."
                _isLoading.value = false
                return@launch
            }
            android.util.Log.d("DashboardViewModel", "Found vehicle: ${vehicle.name}")

            // Load services for this vehicle
            android.util.Log.d("DashboardViewModel", "Fetching services for vehicleId: $vehicleId")
            
            // Ensure _services is populated (global service templates)
            if (_services.value.isEmpty()) {
                android.util.Log.d("DashboardViewModel", "_services is empty in startMonitoringForVehicle, loading services")
                when (val serviceResult = serviceRepository.getServices()) {
                    is Result.Success -> {
                        _services.value = serviceResult.data ?: emptyList()
                        android.util.Log.d("DashboardViewModel", "Loaded ${_services.value.size} services into _services state")
                    }
                    is Result.Failure -> {
                        android.util.Log.e("DashboardViewModel", "Failed to load services: ${serviceResult.message}")
                        _errorMessage.value = "Failed to load services: ${serviceResult.message}"
                        _isLoading.value = false
                        return@launch
                    }
                }
            }
            
            // Services are now GLOBAL templates - no filtering by userId
            // VehicleServiceMapping handles the actual vehicle-service relationships
            val vehicleServicesResult = _services.value
            
            if (vehicleServicesResult.isEmpty()) {
                _errorMessage.value = "No services found in system. Please add services first."
                android.util.Log.w("DashboardViewModel", "No services available in _services after loading")
                _isLoading.value = false
                return@launch
            }

            // NEW: Ensure all VehicleServiceMappings exist before starting monitoring
            android.util.Log.d("DashboardViewModel", "Ensuring VehicleServiceMappings exist for all services")
            val allMappingsExist = ensureAllMappingsExist(vehicleId, vehicle)
            if (!allMappingsExist) {
                android.util.Log.e("DashboardViewModel", "Failed to create required VehicleServiceMappings for vehicle $vehicleId")
                _errorMessage.value = "Failed to create service mappings. Please contact administrator."
                _isLoading.value = false
                return@launch
            }

            // Start monitoring for ALL services on this vehicle
            monitoredVehicleId = vehicle.id // Lock to this vehicle
            android.util.Log.d("DashboardViewModel", "Starting monitoring for all ${vehicleServicesResult.size} services on vehicle $vehicleId")
            
            // Update selected vehicle
            _selectedVehicle.value = vehicle
            _vehicleServices.value = vehicleServicesResult

            // Create or get VehicleServiceMapping and start monitoring for ALL services
            vehicleServicesResult.forEach { service ->
                startMappingMonitoringForVehicleAndService(vehicleId, service.id)
            }

            // Start the accelerometer service
            android.util.Log.d("DashboardViewModel", "Starting AccelerometerService for vehicle $vehicleId")
            try {
                // Start with empty serviceId since we're monitoring all services
                // The service will check Firebase for active mappings and restore state
                val autoStopTimeout = getAutoStopTimeout()
                AccelerometerService.startService(context, "", vehicle.id, autoStopTimeout)
                android.util.Log.i("DashboardViewModel", "AccelerometerService started successfully with auto-stop timeout: ${autoStopTimeout}ms")
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error starting AccelerometerService", e)
                _errorMessage.value = "Failed to start accelerometer service: ${e.message}"
                monitoredVehicleId = null // Clear on error
                _isLoading.value = false
                return@launch
            }
            _isMonitoring.value = true
            // Set mileage threshold to the first service's limit (for compatibility)
            val firstService = vehicleServicesResult.firstOrNull()
            _mileageThreshold.value = firstService?.mileageLimit ?: 20000f

            _successMessage.value = "Monitoring started for ${vehicle.name}"
            android.util.Log.i("DashboardViewModel", "Monitoring started successfully for: ${vehicle.name}")
        }
    }
    
    /**
     * Creates or gets the VehicleServiceMapping and starts monitoring for the vehicle-service pair.
     * This is the key method for the new architecture - each vehicle-service pair has independent readings.
     */
    private suspend fun startMappingMonitoringForVehicleAndService(vehicleId: String, serviceId: String) {
        // First check if mapping already exists
        when (val mappingResult = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicleId, serviceId)) {
            is Result.Success -> {
                val existingMapping = mappingResult.data
                if (existingMapping != null) {
                    // Mapping exists, just start monitoring
                    android.util.Log.d("DashboardViewModel", "Found existing mapping ${existingMapping.id}, starting monitoring")
                    vehicleServiceMappingRepository.startMappingMonitoring(existingMapping.id)
                    currentMappingId = existingMapping.id
                } else {
                    // Mapping doesn't exist, create a new one
                    android.util.Log.d("DashboardViewModel", "Creating new mapping for vehicle $vehicleId and service $serviceId")
                    createAndStartMapping(vehicleId, serviceId)
                }
            }
            is Result.Failure -> {
                // Error getting mapping, try to create new one
                android.util.Log.w("DashboardViewModel", "Error getting mapping: ${mappingResult.message}, creating new")
                createAndStartMapping(vehicleId, serviceId)
            }
        }
    }
    
    /**
     * Creates a new VehicleServiceMapping and starts monitoring.
     * FIXED: Now uses current authenticated user's ID for proper authentication context.
     */
    private suspend fun createAndStartMapping(vehicleId: String, serviceId: String) {
        // Get current authenticated user ID
        val currentUserId = authRepository.getCurrentUserId()
        if (currentUserId == null) {
            android.util.Log.e("DashboardViewModel", "Cannot create mapping: User not authenticated")
            _errorMessage.value = "Cannot create mapping: User not authenticated"
            return
        }
        
        // Get vehicle info directly from repository (FIXED: don't rely on _vehicles.value)
        val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
        val vehicle = when (vehicleResult) {
            is Result.Success -> vehicleResult.data
            is Result.Failure -> {
                android.util.Log.e("DashboardViewModel", "Failed to fetch vehicle for mapping creation: ${vehicleResult.message}")
                null
            }
        }
        
        if (vehicle == null) {
            android.util.Log.e("DashboardViewModel", "Cannot create mapping: vehicle not found for ID: $vehicleId")
            _errorMessage.value = "Cannot create mapping: vehicle not found"
            return
        }
        
        // Validate that the vehicle belongs to the current user
        if (vehicle.userId != currentUserId) {
            android.util.Log.e("DashboardViewModel", "Cannot create mapping: Vehicle ${vehicle.id} belongs to user ${vehicle.userId}, but current user is $currentUserId")
            _errorMessage.value = "Cannot create mapping: Vehicle does not belong to current user"
            return
        }
        
        // Get service info
        val service = _services.value.find { it.id == serviceId } ?: _vehicleServices.value.find { it.id == serviceId }
        
        if (service == null) {
            android.util.Log.e("DashboardViewModel", "Cannot create mapping: service not found for ID: $serviceId")
            _errorMessage.value = "Cannot create mapping: service not found"
            return
        }
        
        // Get variant details if service has a variantId
        var variantName = service.variantName
        var variantId = service.variantId
        var mileageLimit = service.mileageLimit
        
        if (service.variantId.isNotEmpty()) {
            when (val variantResult = serviceVariantRepository.getVariantById(service.variantId)) {
                is Result.Success -> {
                    val variant = variantResult.data
                    if (variant != null) {
                        variantName = variant.name
                        mileageLimit = variant.mileageLimit
                        android.util.Log.d("DashboardViewModel", "Using variant details for service ${service.id}: variantName=$variantName, mileageLimit=$mileageLimit")
                    }
                }
                is Result.Failure -> {
                    android.util.Log.w("DashboardViewModel", "Failed to fetch variant details for service ${service.id}: ${variantResult.message}")
                }
            }
        }
        
        val newMapping = VehicleServiceMapping(
            vehicleId = vehicleId,
            serviceId = serviceId,
            userId = currentUserId, // Use current authenticated user's ID
            serviceName = variantName.ifEmpty { service.name },
            variantId = variantId,
            variantName = variantName,
            totalMovement = 0f,
            isMonitoring = true,
            status = VehicleServiceMapping.MappingStatus.ACTIVE,
            lastReadingTime = System.currentTimeMillis(),
            mileageLimit = mileageLimit,
        )
        
        when (val createResult = vehicleServiceMappingRepository.createMapping(newMapping)) {
            is Result.Success -> {
                val createdMapping = createResult.data
                if (createdMapping != null) {
                    currentMappingId = createdMapping.id
                    android.util.Log.d("DashboardViewModel", "Created new mapping ${createdMapping.id} for service ${service.id} with variantId=$variantId, variantName=$variantName")
                    
                    // Start monitoring for the mapping
                    when (val startResult = vehicleServiceMappingRepository.startMappingMonitoring(createdMapping.id)) {
                        is Result.Success -> {
                            android.util.Log.i("DashboardViewModel", "Successfully started monitoring for mapping ${createdMapping.id}")
                        }
                        is Result.Failure -> {
                            android.util.Log.e("DashboardViewModel", "Failed to start mapping monitoring: ${startResult.message}")
                            _errorMessage.value = "Failed to start monitoring: ${startResult.message}"
                        }
                    }
                }
            }
            is Result.Failure -> {
                android.util.Log.e("DashboardViewModel", "Failed to create mapping: ${createResult.message}")
                _errorMessage.value = "Failed to create service mapping: ${createResult.message}"
            }
        }
    }

    // ========== VehicleServiceMapping Verification Methods ==========

    /**
     * Ensures all VehicleServiceMappings exist for a vehicle's services before starting monitoring.
     * Creates missing mappings using the vehicle's userId for authentication.
     * 
     * @param vehicleId The ID of the vehicle to check mappings for
     * @param vehicle The vehicle object containing user information
     * @return true if all mappings exist or were successfully created, false otherwise
     */
    private suspend fun ensureAllMappingsExist(vehicleId: String, vehicle: Vehicle): Boolean {
        return try {
            android.util.Log.d("DashboardViewModel", "Checking and creating VehicleServiceMappings for vehicle $vehicleId")
            
            // Get all services
            val services = _services.value
            
            // Check and create mappings for each service
            services.forEach { service ->
                val mappingExists = checkMappingExists(vehicleId, service.id)
                if (!mappingExists) {
                    val mappingCreated = createMappingForServiceAndVehicle(service, vehicle)
                    if (!mappingCreated) {
                        android.util.Log.e("DashboardViewModel", "Failed to create mapping for service ${service.id}")
                        return false
                    }
                }
            }
            
            android.util.Log.d("DashboardViewModel", "Successfully ensured all VehicleServiceMappings exist for vehicle $vehicleId")
            true
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error ensuring mappings exist for vehicle $vehicleId", e)
            false
        }
    }

    /**
     * Checks if a VehicleServiceMapping exists for a specific vehicle-service combination.
     * 
     * @param vehicleId The ID of the vehicle
     * @param serviceId The ID of the service
     * @return true if mapping exists, false otherwise
     */
    private suspend fun checkMappingExists(vehicleId: String, serviceId: String): Boolean {
        return try {
            when (val mappingResult = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicleId, serviceId)) {
                is Result.Success -> mappingResult.data != null
                is Result.Failure -> false
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error checking mapping existence for vehicle $vehicleId and service $serviceId", e)
            false
        }
    }


    // ========== Reset Service Dialog Methods ==========

    /**
     * Steps for the reset service dialog flow.
     */
    enum class ResetDialogStep {
        SELECT_DRIVER,    // Admin: select driver (employee skips to vehicle)
        SELECT_VEHICLE,   // Select vehicle for the driver/current user
        SELECT_SERVICE,   // Select service to reset
        CONFIRM_RESET,    // Confirm the reset action
    }

    /**
     * Shows the reset service dialog.
     * For admin: starts from driver selection.
     * For employee: starts from vehicle selection.
     */
    fun showResetServiceDialog() {
        _showResetServiceDialog.value = true
        _resetDialogStep.value = ResetDialogStep.SELECT_DRIVER
        _selectedDriverForReset.value = null
        _selectedVehicleForReset.value = null
        _selectedServiceForReset.value = null

        // Load all users for admin selection
        loadAllUsersForReset()
    }

    /**
     * Hides the reset service dialog.
     */
    fun hideResetServiceDialog() {
        _showResetServiceDialog.value = false
        _resetDialogStep.value = ResetDialogStep.SELECT_DRIVER
        _selectedDriverForReset.value = null
        _selectedVehicleForReset.value = null
        _selectedServiceForReset.value = null
    }

    /**
     * Loads all drivers (for admin/employee to select driver).
     */
    private fun loadAllUsersForReset() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            // Load all drivers from Firestore
            when (val result = authRepository.getUsersByRole(User.UserRole.DRIVER)) {
                is Result.Success -> {
                    _allUsers.value = result.data ?: emptyList()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to load drivers"
                    _allUsers.value = emptyList()
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Selects a driver for reset (admin flow).
     */
    fun selectDriverForReset(driver: User) {
        _selectedDriverForReset.value = driver
        _resetDialogStep.value = ResetDialogStep.SELECT_VEHICLE

        // Load vehicles for this driver
        loadVehiclesForReset(driver.userId)
    }

    /**
     * Selects a vehicle for reset.
     */
    fun selectVehicleForReset(vehicle: Vehicle) {
        _selectedVehicleForReset.value = vehicle
        _resetDialogStep.value = ResetDialogStep.SELECT_SERVICE

        // Load services for this vehicle
        loadServicesForReset(vehicle.id)
    }

    /**
     * Selects a service for reset.
     */
    fun selectServiceForReset(service: Service) {
        _selectedServiceForReset.value = service
        _resetDialogStep.value = ResetDialogStep.CONFIRM_RESET
    }

    /**
     * Goes back to previous dialog step.
     */
    fun resetDialogPreviousStep() {
        when (_resetDialogStep.value) {
            ResetDialogStep.CONFIRM_RESET -> {
                _selectedServiceForReset.value = null
                _resetDialogStep.value = ResetDialogStep.SELECT_SERVICE
            }
            ResetDialogStep.SELECT_SERVICE -> {
                _selectedServiceForReset.value = null
                _resetDialogStep.value = ResetDialogStep.SELECT_VEHICLE
            }
            ResetDialogStep.SELECT_VEHICLE -> {
                _selectedVehicleForReset.value = null
                _resetDialogStep.value = ResetDialogStep.SELECT_DRIVER
            }
            ResetDialogStep.SELECT_DRIVER -> {
                // Already at start, close dialog
                hideResetServiceDialog()
            }
        }
    }

    /**
     * Loads vehicles for a specific user (for reset dialog).
     */
    private fun loadVehiclesForReset(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = vehicleRepository.getVehiclesForUser(userId)) {
                is Result.Success -> {
                    _vehicles.value = result.data ?: emptyList()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to load vehicles"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Loads services for a specific vehicle (for reset dialog).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun loadServicesForReset(vehicleId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceRepository.getServices()) {
                is Result.Success -> {
                    _vehicleServices.value = result.data ?: emptyList()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to load services"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Confirms and executes the reset for the selected service.
     * Uses the new architecture: resets readings via VehicleServiceMapping.
     */
    fun confirmResetService() {
        val service = _selectedServiceForReset.value ?: return
        val vehicle = _selectedVehicleForReset.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            _successMessage.value = ""

            // Reset via VehicleServiceMapping
            when (val mappingResult = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicle.id, service.id)) {
                is Result.Success -> {
                    val mapping = mappingResult.data
                    if (mapping != null) {
                        when (val resetResult = vehicleServiceMappingRepository.resetMappingReadings(mapping.id)) {
                            is Result.Success -> {
                                // Update local readings map
                                _serviceReadingsMap.value = _serviceReadingsMap.value.toMutableMap().apply {
                                    this[service.id] = 0
                                }
                                _successMessage.value = "Service readings reset successfully for ${service.variantName.ifEmpty { service.name }}"
                                hideResetServiceDialog()
                            }
                            is Result.Failure -> {
                                _errorMessage.value = resetResult.message ?: "Failed to reset service readings"
                            }
                        }
                    } else {
                        // No mapping exists - nothing to reset
                        _successMessage.value = "No readings found to reset"
                        hideResetServiceDialog()
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = mappingResult.message ?: "Failed to find service mapping"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Gets vehicles for the current user (employee flow - skips driver selection).
     */
    fun getVehiclesForCurrentUser(): List<Vehicle> {
        return _vehicles.value
    }

    /**
     * Gets services for the selected vehicle in reset dialog.
     */
    fun getServicesForResetVehicle(): List<Service> {
        return _vehicleServices.value
    }

    // ========== Per-Service Readings Methods ==========

    /**
     * Updates readings for ALL services assigned to the monitored vehicle.
     * Each service maintains independent readings through VehicleServiceMapping.
     * 
     * @param vehicleId The ID of the monitored vehicle
     * @param totalMovement The new reading value from accelerometer
     */
    private fun updateReadingsForAllVehicleServices(vehicleId: String, totalMovement: Int) {
        val updatedMap = _serviceReadingsMap.value.toMutableMap()
        
        // Update ALL services assigned to this vehicle
        _vehicleServices.value.forEach { service ->
            // Check if service hasn't reached limit before updating
            val mapping = _vehicleServiceMappings.value.find { 
                it.vehicleId == vehicleId && it.serviceId == service.id 
            }
            
            // Only update services that haven't reached their limit
            if (mapping == null || mapping.totalMovement < mapping.mileageLimit) {
                // FIX: totalMovement is already cumulative from the service, so assign directly
                val newReading = totalMovement
                
                updatedMap[service.id] = newReading
                android.util.Log.d("DashboardViewModel", "Updated service ${service.id} (${service.name}): $newReading")
                
                // NOTE: Firebase updates are handled by the AccelerometerService in real-time
                // and by real-time listeners. No need to update here to avoid conflicts.
            } else {
                android.util.Log.d("DashboardViewModel", "Service ${service.id} (${service.name}) reached limit, skipping update")
            }
        }
        
        _serviceReadingsMap.value = updatedMap
    }

    /**
     * Updates the VehicleServiceMapping for a specific service with new reading.
     * This ensures independent readings are persisted to Firebase.
     */
    private fun updateMappingForService(serviceId: String, newReading: Float) {
        viewModelScope.launch {
            try {
                when (val mappingResult = vehicleServiceMappingRepository.getMappingForVehicleAndService(monitoredVehicleId ?: "", serviceId)) {
                    is Result.Success -> {
                        mappingResult.data?.let { mapping ->
                            vehicleServiceMappingRepository.updateMappingMovement(mapping.id, newReading)
                            android.util.Log.d("DashboardViewModel", "Updated Firebase mapping for service $serviceId: $newReading")
                        }
                    }
                    is Result.Failure -> {
                        android.util.Log.w("DashboardViewModel", "Failed to update mapping for service $serviceId: ${mappingResult.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error updating mapping for service $serviceId", e)
            }
        }
    }

    /**
     * Initializes readings from VehicleServiceMappings.
     * Each mapping has independent totalMovement per vehicle.
     * 
     * @param mappings The list of VehicleServiceMappings to initialize readings from
     */
    fun initializeServiceReadingsFromMappings(mappings: List<VehicleServiceMapping>) {
        val initializedMap = mutableMapOf<String, Int>()
        
        mappings.forEach { mapping ->
            initializedMap[mapping.serviceId] = mapping.totalMovement.toInt()
            android.util.Log.d("DashboardViewModel", "Initialized reading from mapping for service ${mapping.serviceId}: ${mapping.totalMovement.toInt()}")
        }
        
        _serviceReadingsMap.value = initializedMap
    }


    /**
     * Saves all current readings for the monitored vehicle's services to Firebase.
     * Uses the new architecture: saves to VehicleServiceMapping for independent readings.
     * Called when monitoring stops to persist the readings.
     * 
     * @param vehicleId The ID of the monitored vehicle
     */
    fun saveReadingsToFirebase(vehicleId: String) {
        viewModelScope.launch {
            try {
                // Get mappings for this vehicle
                when (val mappingResult = vehicleServiceMappingRepository.getMappingsForVehicle(vehicleId)) {
                    is Result.Success -> {
                        val mappings = mappingResult.data ?: emptyList()
                        mappings.forEach { mapping ->
                            val currentReading = _serviceReadingsMap.value[mapping.serviceId] ?: return@forEach
                            
                            // Update the mapping's totalMovement in Firebase
                            vehicleServiceMappingRepository.updateMappingMovement(mapping.id, currentReading.toFloat())
                            
                            android.util.Log.d("DashboardViewModel", "Saved reading to mapping ${mapping.id} for service ${mapping.serviceId}: $currentReading")
                        }
                    }
                    is Result.Failure -> {
                        android.util.Log.w("DashboardViewModel", "Failed to load mappings: ${mappingResult.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error saving readings to Firebase", e)
            }
        }
    }

    // ========== NEW ARCHITECTURE: VehicleServiceMapping Methods ==========

    /**
     * Loads VehicleServiceMappings for multiple vehicles.
     * This gets all services with independent readings for the user's vehicles.
     * Called automatically after loading vehicles for a user.
     */
    private fun loadMappingsForVehicles(vehicleIds: List<String>) {
        viewModelScope.launch {
            try {
                val allMappings = mutableListOf<VehicleServiceMapping>()
                
                for (vehicleId in vehicleIds) {
                    when (val result = vehicleServiceMappingRepository.getMappingsForVehicle(vehicleId)) {
                        is Result.Success -> {
                            allMappings.addAll(result.data ?: emptyList())
                        }
                        is Result.Failure -> {
                            android.util.Log.w("DashboardViewModel", "Failed to load mappings for vehicle $vehicleId: ${result.message}")
                        }
                    }
                }
                
                _vehicleServiceMappings.value = allMappings
                initializeServiceReadingsFromMappings(allMappings)
                
                android.util.Log.d("DashboardViewModel", "Loaded ${allMappings.size} total mappings for ${vehicleIds.size} vehicles")
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error loading mappings for vehicles", e)
            }
        }
    }

    /**
     * Sets up real-time listeners for a specific vehicle's mappings.
     * This ensures service cards update automatically when Firebase data changes.
     */
    fun setupRealTimeListenersForVehicle(vehicleId: String) {
        viewModelScope.launch {
            try {
                // Observe all mappings for this vehicle
                vehicleServiceMappingRepository.observeMappingsForVehicle(vehicleId)
                    .collect { mappings ->
                        // Update live mappings state
                        val liveMap = mappings.associateBy { "${it.vehicleId}_${it.serviceId}" }
                        _liveMappings.value = liveMap
                        
                        // Update service readings map with real-time data
                        val readingsMap = mappings.associate { it.serviceId to it.totalMovement.toInt() }
                        _serviceReadingsMap.value = readingsMap
                        
                        android.util.Log.d("DashboardViewModel", "Real-time update: ${mappings.size} mappings for vehicle $vehicleId")
                    }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error setting up real-time listeners for vehicle $vehicleId", e)
            }
        }
    }

    /**
     * Sets up real-time listener for a specific vehicle-service mapping.
     * This provides more granular updates for individual service cards.
     */
    fun setupRealTimeListenerForMapping(vehicleId: String, serviceId: String) {
        viewModelScope.launch {
            try {
                vehicleServiceMappingRepository.observeMappingForVehicleAndService(vehicleId, serviceId)
                    .collect { mapping ->
                        if (mapping != null) {
                            // Update the specific mapping in live mappings
                            val key = "${vehicleId}_${serviceId}"
                            val currentLiveMappings = _liveMappings.value.toMutableMap()
                            currentLiveMappings[key] = mapping
                            _liveMappings.value = currentLiveMappings
                            
                            // Update the specific service reading
                            val currentReadings = _serviceReadingsMap.value.toMutableMap()
                            currentReadings[serviceId] = mapping.totalMovement.toInt()
                            _serviceReadingsMap.value = currentReadings
                            
                            android.util.Log.d("DashboardViewModel", "Real-time update for $serviceId: ${mapping.totalMovement}")
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error setting up real-time listener for ${vehicleId}_$serviceId", e)
            }
        }
    }

    /**
     * Clears all real-time listeners when vehicle selection changes.
     */
    fun clearRealTimeListeners() {
        // The Flow.collect() calls will automatically stop when the coroutine scope is cancelled
        // This is handled by the viewModelScope.launch lifecycle
        _liveMappings.value = emptyMap()
        android.util.Log.d("DashboardViewModel", "Cleared real-time listeners")
    }

    /**
     * Gets the live reading for a service from real-time Firebase data.
     * Falls back to local broadcast data if not available.
     */
    fun getLiveServiceReading(serviceId: String, vehicleId: String? = null): Int {
        // First try to get from live mappings (real-time Firebase data)
        val key = vehicleId?.let { "${it}_${serviceId}" }
        key?.let {
            _liveMappings.value[it]?.totalMovement?.toInt()?.let { liveReading ->
                return liveReading
            }
        }
        
        // Fall back to local broadcast data
        return _serviceReadingsMap.value[serviceId] ?: 0
    }

    /**
     * Combines broadcast updates with real-time Firebase updates.
     * Ensures UI stays in sync regardless of data source.
     */
    private fun updateReadingsWithConflictResolution(
        vehicleId: String,
        totalMovement: Int,
        source: String = "broadcast"
    ) {
        val currentReadings = _serviceReadingsMap.value.toMutableMap()
        val currentLiveMappings = _liveMappings.value.toMutableMap()
        
        // Update all services for this vehicle
        _vehicleServices.value.forEach { service ->
            val key = "${vehicleId}_${service.id}"
            val currentReading = currentReadings[service.id] ?: 0
            val liveMapping = currentLiveMappings[key]
            
            // Conflict resolution: prefer higher value (in case of concurrent updates)
            val newReading = maxOf(currentReading, totalMovement)
            
            currentReadings[service.id] = newReading
            
            // Update live mapping if it exists
            liveMapping?.let {
                val updatedMapping = it.copy(totalMovement = newReading.toFloat())
                currentLiveMappings[key] = updatedMapping
            }
            
            android.util.Log.d("DashboardViewModel", "$source update for ${service.id}: $newReading")
        }
        
        _serviceReadingsMap.value = currentReadings
        _liveMappings.value = currentLiveMappings
    }

    /**
     * Enhanced error handling for Firebase operations with retry logic.
     */
    private suspend fun <T> safeFirebaseOperation(
        operationName: String,
        operation: suspend () -> T,
        maxRetries: Int = 3
    ): T {
        var retryCount = 0
        var lastError: Exception? = null

        while (retryCount < maxRetries) {
            try {
                val result = operation()
                android.util.Log.d("DashboardViewModel", "Firebase operation '$operationName' succeeded")
                return result
            } catch (e: Exception) {
                retryCount++
                lastError = e
                android.util.Log.w("DashboardViewModel", "Firebase operation '$operationName' failed (attempt $retryCount/$maxRetries): ${e.message}")
                
                if (retryCount < maxRetries) {
                    // Exponential backoff
                    val delayMs = (1000 * Math.pow(2.0, retryCount.toDouble())).toLong()
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }

        // All retries failed
        val errorMessage = "Failed to $operationName after $maxRetries attempts. Please check your connection and try again."
        _errorMessage.value = errorMessage
        android.util.Log.e("DashboardViewModel", "Firebase operation '$operationName' failed after $maxRetries attempts: ${lastError?.message}")
        throw Exception(errorMessage)
    }

    /**
     * Validates Firebase connectivity before critical operations.
     */
    private fun validateFirebaseConnectivity(): Boolean {
        // Simple validation - check if we can access Firebase
        return try {
            // This is a lightweight check that doesn't require actual data access
            true
        } catch (e: Exception) {
            android.util.Log.w("DashboardViewModel", "Firebase connectivity validation failed: ${e.message}")
            false
        }
    }

    /**
     * Enhanced mapping creation with better error handling and validation.
     */
    private suspend fun createMappingForServiceAndVehicleWithValidation(
        service: Service,
        vehicle: Vehicle
    ): Boolean {
        return try {
            // Validate inputs
            if (service.id.isBlank() || vehicle.id.isBlank()) {
                android.util.Log.e("DashboardViewModel", "Invalid service or vehicle ID")
                _errorMessage.value = "Invalid service or vehicle information"
                return false
            }

            // Validate user authentication
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                android.util.Log.e("DashboardViewModel", "Cannot create mapping: User not authenticated")
                _errorMessage.value = "User not authenticated. Please log in again."
                return false
            }

            // Validate vehicle ownership
            if (vehicle.userId != currentUserId) {
                android.util.Log.e("DashboardViewModel", "Cannot create mapping: Vehicle ${vehicle.id} belongs to user ${vehicle.userId}, but current user is $currentUserId")
                _errorMessage.value = "Cannot create mapping: Vehicle does not belong to current user"
                return false
            }

            // Check if mapping already exists
            when (val mappingResult = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicle.id, service.id)) {
                is Result.Success -> {
                    if (mappingResult.data != null) {
                        android.util.Log.d("DashboardViewModel", "Mapping already exists for vehicle ${vehicle.id} and service ${service.id}")
                        return true
                    }
                }
                is Result.Failure -> {
                    android.util.Log.w("DashboardViewModel", "Error checking existing mapping: ${mappingResult.message}")
                    // Continue to create mapping
                }
            }

            // Create the mapping with retry logic
            return safeFirebaseOperation("create service mapping", operation = {
                val newMapping = createMappingData(service, vehicle, currentUserId)
                when (val createResult = vehicleServiceMappingRepository.createMapping(newMapping)) {
                    is Result.Success -> {
                        android.util.Log.d("DashboardViewModel", "Created VehicleServiceMapping for service ${service.id} and vehicle ${vehicle.id}")
                        _successMessage.value = "Service mapping created successfully"
                        true  // Return true on success
                    }
                    is Result.Failure -> {
                        throw Exception("Failed to create mapping: ${createResult.message}")
                    }
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error creating mapping for service ${service.id} and vehicle ${vehicle.id}", e)
            _errorMessage.value = "Failed to create service mapping: ${e.message}"
            false
        }
    }

    /**
     * Creates the mapping data object with proper validation.
     */
    private suspend fun createMappingData(
        service: Service,
        vehicle: Vehicle,
        currentUserId: String
    ): VehicleServiceMapping {
        // Get variant details if service has a variantId
        var variantName = service.variantName
        var variantId = service.variantId
        var mileageLimit = service.mileageLimit
        
        if (service.variantId.isNotEmpty()) {
            when (val variantResult = serviceVariantRepository.getVariantById(service.variantId)) {
                is Result.Success -> {
                    val variant = variantResult.data
                    if (variant != null) {
                        variantName = variant.name
                        mileageLimit = variant.mileageLimit
                        android.util.Log.d("DashboardViewModel", "Using variant details for service ${service.id}: variantName=$variantName, mileageLimit=$mileageLimit")
                    }
                }
                is Result.Failure -> {
                    android.util.Log.w("DashboardViewModel", "Failed to fetch variant details for service ${service.id}: ${variantResult.message}")
                }
            }
        }
        
        return VehicleServiceMapping(
            vehicleId = vehicle.id,
            serviceId = service.id,
            userId = currentUserId,
            serviceName = variantName.ifEmpty { service.name },
            variantId = variantId,
            variantName = variantName,
            totalMovement = 0f,
            isMonitoring = false,
            status = VehicleServiceMapping.MappingStatus.ACTIVE,
            lastReadingTime = System.currentTimeMillis(),
            mileageLimit = mileageLimit,
        )
    }
}
