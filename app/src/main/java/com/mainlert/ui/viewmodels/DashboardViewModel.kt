package com.mainlert.ui.viewmodels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mainlert.data.models.Result
import com.mainlert.data.models.Service
import com.mainlert.data.models.ServiceReading
import com.mainlert.data.models.ServiceStatusSummary
import com.mainlert.data.models.ServiceVariant
import com.mainlert.data.models.User
import com.mainlert.data.models.Vehicle
import com.mainlert.data.repositories.AuthRepository
import com.mainlert.data.repositories.ServiceRepository
import com.mainlert.data.repositories.ServiceVariantRepository
import com.mainlert.data.repositories.VehicleRepository
import com.mainlert.services.AccelerometerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dashboard ViewModel for MainLert app.
 * Handles dashboard state and service operations with real-time accelerometer integration.
 */
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val serviceRepository: ServiceRepository,
        private val vehicleRepository: VehicleRepository,
        private val serviceVariantRepository: ServiceVariantRepository,
        private val authRepository: AuthRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {

    /** Throttle interval for UI updates (500ms) */
    private var lastUiUpdateTime = 0L
    private val uiUpdateThrottleMs = 500L

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

                    _accelerometerReadings.value = Triple(x, y, z)
                    _isMonitoring.value = isMonitoring
                    _serviceReadings.value = totalMovement.toInt()
                    android.util.Log.d("DashboardViewModel", "Updated readings: x=$x, y=$y, z=$z")
                }
            }
        }

    init {
        // Register for accelerometer broadcasts from the service
        val filter = IntentFilter(AccelerometerService.ACTION_BROADCAST_ACCELEROMETER)
        LocalBroadcastManager.getInstance(context).registerReceiver(
            accelerometerReceiver,
            filter,
        )
        android.util.Log.d("DashboardViewModel", "Accelerometer receiver registered")
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister to prevent memory leaks
        try {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(accelerometerReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
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
     * Current service reading, if available.
     */
    private val _currentReadings = MutableStateFlow<ServiceReading?>(null)
    val currentReadings: StateFlow<ServiceReading?> = _currentReadings.asStateFlow()

    /**
     * Indicates if accelerometer monitoring is active.
     */
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    /**
     * Current service readings value.
     */
    private val _serviceReadings = MutableStateFlow(0)
    val serviceReadings: StateFlow<Int> = _serviceReadings.asStateFlow()

    /**
     * Indicates if a service is currently active.
     */
    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    /**
     * Mileage threshold value for service readings.
     */
    private val _mileageThreshold = MutableStateFlow(20000)
    val mileageThreshold: StateFlow<Int> = _mileageThreshold.asStateFlow()

    /**
     * Service status summary with detailed information.
     */
    private val _serviceStatus = MutableStateFlow<ServiceStatusSummary?>(null)
    val serviceStatus: StateFlow<ServiceStatusSummary?> = _serviceStatus.asStateFlow()

    /**
     * List of recent service readings for analytics.
     */
    private val _recentReadings = MutableStateFlow<List<ServiceReading>>(emptyList())
    val recentReadings: StateFlow<List<ServiceReading>> = _recentReadings.asStateFlow()

    /**
     * Battery level for monitoring optimization.
     */
    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    /**
     * Connection status indicator.
     */
    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * Current accelerometer readings (x, y, z values).
     */
    private val _accelerometerReadings = MutableStateFlow(Triple(0f, 0f, 0f))
    val accelerometerReadings: StateFlow<Triple<Float, Float, Float>> = _accelerometerReadings.asStateFlow()

    // Vehicle-related state
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

    private val _vehicleServices = MutableStateFlow<List<Service>>(emptyList())
    val vehicleServices: StateFlow<List<Service>> = _vehicleServices.asStateFlow()

    // Service variant state
    private val _serviceVariants = MutableStateFlow<List<ServiceVariant>>(emptyList())
    val serviceVariants: StateFlow<List<ServiceVariant>> = _serviceVariants.asStateFlow()

    // Vehicle selection dialog state
    private val _showVehicleSelectionDialog = MutableStateFlow(false)
    val showVehicleSelectionDialog: StateFlow<Boolean> = _showVehicleSelectionDialog.asStateFlow()

    // Reset Service dialog state
    private val _showResetServiceDialog = MutableStateFlow(false)
    val showResetServiceDialog: StateFlow<Boolean> = _showResetServiceDialog.asStateFlow()

    private val _resetDialogStep = MutableStateFlow(ResetDialogStep.SELECT_DRIVER)
    val resetDialogStep: StateFlow<ResetDialogStep> = _resetDialogStep.asStateFlow()

    private val _selectedDriverForReset = MutableStateFlow<User?>(null)
    val selectedDriverForReset: StateFlow<User?> = _selectedDriverForReset.asStateFlow()

    private val _selectedVehicleForReset = MutableStateFlow<Vehicle?>(null)
    val selectedVehicleForReset: StateFlow<Vehicle?> = _selectedVehicleForReset.asStateFlow()

    private val _selectedServiceForReset = MutableStateFlow<Service?>(null)
    val selectedServiceForReset: StateFlow<Service?> = _selectedServiceForReset.asStateFlow()

    // For admin: list of all users (drivers)
    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    // For employee: their assigned vehicles (already in _vehicles)
    // For employee: their assigned services for selected vehicle (already in _vehicleServices)

    /**
     * Holds the ID of the current service, if any.
     */
    private var currentServiceId: String? = null

    init {
        // Loads all services for the current user on ViewModel initialization.
        loadServices()
    }

    /**
     * Loads all services for the current user and updates the services state.
     */
    fun loadServices() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceRepository.getServices()) {
                is Result.Success -> {
                    _services.value = result.data ?: emptyList()
                    // Auto-select first service if available
                    if (_services.value.isNotEmpty()) {
                        currentServiceId = _services.value.first().id
                        getServiceStatusSummary(currentServiceId!!)
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to load services"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Starts the accelerometer monitoring service.
     * Checks vehicle count first:
     * - 1 vehicle: auto-select and start immediately
     * - Multiple vehicles: show selection dialog
     * - 0 vehicles: show error
     */
    fun startMonitoringService() {
        android.util.Log.i("DashboardViewModel", ">>> START BUTTON CLICKED <<<")

        // Validate battery level before proceeding
        if (_batteryLevel.value < 20) {
            _errorMessage.value = "Battery level too low. Please charge your device before starting monitoring."
            android.util.Log.w("DashboardViewModel", "Battery level too low: ${_batteryLevel.value}")
            return
        }

        val vehicleCount = _vehicles.value.size
        android.util.Log.d("DashboardViewModel", "Vehicle count: $vehicleCount")

        when {
            vehicleCount == 0 -> {
                _errorMessage.value = "No vehicles assigned. Please contact your administrator."
            }
            vehicleCount == 1 -> {
                // Auto-select the single vehicle and start monitoring
                val singleVehicle = _vehicles.value.first()
                android.util.Log.d("DashboardViewModel", "Auto-selecting single vehicle: ${singleVehicle.name}")
                startMonitoringForVehicle(singleVehicle.id)
            }
            else -> {
                // Multiple vehicles - show selection dialog
                android.util.Log.d("DashboardViewModel", "Multiple vehicles ($vehicleCount), showing selection dialog")
                _showVehicleSelectionDialog.value = true
            }
        }
    }

    /**
     * Hides the vehicle selection dialog.
     */
    fun hideVehicleSelectionDialog() {
        _showVehicleSelectionDialog.value = false
    }

    /**
     * Called when user selects a vehicle from the dialog.
     * Starts monitoring for the selected vehicle.
     */
    fun onVehicleSelectedForMonitoring(vehicle: Vehicle) {
        _showVehicleSelectionDialog.value = false
        startMonitoringForVehicle(vehicle.id)
    }

    /**
     * Stops the accelerometer monitoring service.
     */
    fun stopMonitoringService() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            // Stop the accelerometer service
            AccelerometerService.stopService(context)

            if (currentServiceId != null) {
                // Stop service monitoring in Firebase
                val stopResult = serviceRepository.stopServiceMonitoring(currentServiceId!!)

                when (stopResult) {
                    is Result.Success -> {
                        _isMonitoring.value = false
                        _isServiceActive.value = false

                        // Update service status
                        _serviceStatus.value =
                            _serviceStatus.value?.copy(
                                isMonitoring = false,
                                lastReadingTime = System.currentTimeMillis(),
                            )

                        _successMessage.value = "Monitoring stopped successfully"
                    }
                    is Result.Failure -> {
                        _errorMessage.value = stopResult.message ?: "Failed to stop service monitoring"
                    }
                }
            } else {
                _errorMessage.value = "No active service to stop"
            }

            _isLoading.value = false
        }
    }

    fun resetServiceData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            _successMessage.value = ""

            currentServiceId?.let { serviceId ->
                val resetResult = serviceRepository.resetServiceReadings(serviceId)

                when (resetResult) {
                    is Result.Success -> {
                        _serviceReadings.value = 0
                        _serviceStatus.value =
                            _serviceStatus.value?.copy(
                                totalMovement = 0f,
                                totalReadings = 0,
                            )
                        _successMessage.value = "Service readings reset successfully"
                    }
                    is Result.Failure -> {
                        _errorMessage.value = resetResult.message ?: "Failed to reset service readings"
                    }
                }
            } ?: run {
                _errorMessage.value = "No active service to reset"
            }

            _isLoading.value = false
        }
    }

    fun updateServiceReadings(readings: Int) {
        _serviceReadings.value = readings

        // Update service status
        _serviceStatus.value =
            _serviceStatus.value?.copy(
                totalMovement = readings.toFloat(),
                lastReadingTime = System.currentTimeMillis(),
            )

        // Check for Mileage
        if (readings >= mileageThreshold.value) {
            _errorMessage.value = "Mileage detected! Service has reached threshold."
        }
    }

    fun checkMileageStatus(serviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceRepository.checkMileageStatus(serviceId)) {
                is Result.Success -> {
                    if (result.data == true) {
                        _errorMessage.value = "Service has reached Mileage threshold!"
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to check Mileage status"
                }
            }

            _isLoading.value = false
        }
    }

    fun getCurrentService(): Service? {
        return _services.value.firstOrNull()
    }

    fun observeServiceReadings(serviceId: String) {
        viewModelScope.launch {
            serviceRepository.observeServiceReadings(serviceId).collect { readings ->
                _recentReadings.value = readings

                val totalReadings = readings.sumOf { it.totalMovement.toDouble() }
                _serviceReadings.value = totalReadings.toInt()

                // Update service status with latest data
                _serviceStatus.value =
                    _serviceStatus.value?.copy(
                        totalMovement = totalReadings.toFloat(),
                        totalReadings = readings.size,
                    )

                // Check for Mileage against each vehicle service's individual limit
                checkMileageForAllServices()
            }
        }
    }

    fun getServiceStatusSummary(serviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceRepository.getServiceStatusSummary(serviceId)) {
                is Result.Success -> {
                    val summary = result.data
                    if (summary != null) {
                        _serviceStatus.value = summary
                        _isServiceActive.value = summary.isMonitoring
                        _serviceReadings.value = summary.totalMovement.toInt()

                        // Update Mileage threshold based on service configuration
                        currentServiceId?.let { id ->
                            when (val serviceResult = serviceRepository.getServiceById(id)) {
                                is Result.Success -> {
                                    serviceResult.data?.let { service ->
                                        _mileageThreshold.value = service.mileageLimit.toInt()
                                    }
                                }
                                is Result.Failure -> {
                                    _errorMessage.value = serviceResult.message ?: "Failed to get service configuration"
                                }
                            }
                        }
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to get service status"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Creates a new service with the specified details.
     */
    fun createService(
        name: String,
        description: String,
        mileageLimit: Float,
        vehicleId: String = "",
        variantId: String = "",
        variantName: String = "",
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            // Validate inputs
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

            val newService =
                Service(
                    name = name,
                    description = description,
                    mileageLimit = mileageLimit,
                    vehicleIds = if (vehicleId.isNotEmpty()) listOf(vehicleId) else emptyList(),
                    variantId = variantId,
                    variantName = variantName.ifEmpty { "Standard" },
                )

            when (val result = serviceRepository.createService(newService)) {
                is Result.Success -> {
                    _successMessage.value = "Service created successfully"
                    loadServices() // Refresh services list
                    // Also refresh vehicle-specific services if a vehicle is selected
                    if (vehicleId.isNotEmpty()) {
                        loadServicesForVehicle(vehicleId)
                    }
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
        vehicleId: String = "",
        variantId: String = "",
        variantName: String = "",
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            // Validate inputs
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
                        val updatedService =
                            service.copy(
                                name = name,
                                description = description,
                                mileageLimit = mileageLimit,
                                vehicleIds = if (vehicleId.isEmpty()) service.vehicleIds else listOf(vehicleId),
                                variantId = variantId.ifEmpty { service.variantId },
                                variantName = variantName.ifEmpty { service.variantName.ifEmpty { "Standard" } },
                            )

                        when (val updateResult = serviceRepository.updateService(updatedService)) {
                            is Result.Success -> {
                                _successMessage.value = "Service updated successfully"
                                loadServices() // Refresh services list

                                // Also refresh vehicle-specific services if a vehicle is selected
                                if (vehicleId.isNotEmpty()) {
                                    loadServicesForVehicle(vehicleId)
                                }

                                // Update current service if it's the one being edited
                                if (currentServiceId == serviceId) {
                                    _mileageThreshold.value = mileageLimit.toInt()
                                }
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

            // Check if service is currently active
            if (currentServiceId == serviceId && _isMonitoring.value) {
                _errorMessage.value = "Cannot delete an active service. Please stop monitoring first."
                _isLoading.value = false
                return@launch
            }

            when (val result = serviceRepository.deleteService(serviceId)) {
                is Result.Success -> {
                    _successMessage.value = "Service deleted successfully"
                    loadServices() // Refresh services list

                    // Clear current service if it was deleted
                    if (currentServiceId == serviceId) {
                        currentServiceId = null
                        _serviceStatus.value = null
                        _isServiceActive.value = false
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to delete service"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Gets analytics data for the current service.
     */
    fun getServiceAnalytics(serviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceRepository.getServiceAnalytics(serviceId)) {
                is Result.Success -> {
                    val analytics = result.data
                    if (analytics != null) {
                        // Process analytics data
                        _successMessage.value = "Analytics loaded successfully"
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to load analytics"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Exports service data to a file or backup.
     */
    fun exportServiceData(serviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceRepository.exportServiceData(serviceId)) {
                is Result.Success -> {
                    _successMessage.value = "Service data exported successfully"
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to export service data"
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Updates the current accelerometer readings.
     */
    fun updateAccelerometerReadings(x: Float, y: Float, z: Float) {
        _accelerometerReadings.value = Triple(x, y, z)
    }

    /**
     * Checks Mileage status for all vehicle services.
     * Notification triggers when readings reach any service's Mileage limit.
     */
    fun checkMileageForAllServices() {
        // Check each vehicle service's Mileage limit
        _vehicleServices.value.forEach { service ->
            if (_serviceReadings.value >= service.mileageLimit.toInt()) {
                _errorMessage.value = "Mileage detected! ${service.variantName.ifEmpty { service.name }} has reached threshold."
                android.util.Log.i("DashboardViewModel", "MILEAGE: ${service.name} reached ${service.mileageLimit} (current: ${_serviceReadings.value})")
            }
        }
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
     * Loads vehicles for a specific user.
     */
    fun loadVehiclesForUser(userId: String) {
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
     * Selects a vehicle and loads its services.
     */
    fun selectVehicle(vehicle: Vehicle?) {
        _selectedVehicle.value = vehicle
        vehicle?.let {
            loadServicesForVehicle(it.id)
        }
    }

    /**
     * Loads services for a specific vehicle.
     */
    fun loadServicesForVehicle(vehicleId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceRepository.getServicesForVehicle(vehicleId)) {
                is Result.Success -> {
                    _vehicleServices.value = result.data ?: emptyList()
                }
                is Result.Failure -> {
                    _errorMessage.value = result.message ?: "Failed to load services for vehicle"
                }
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

            when (val result = serviceRepository.getServicesForVehicles(vehicleIds)) {
                is Result.Success -> {
                    _vehicleServices.value = result.data ?: emptyList()
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
        plateNumber: String,
        userId: String,
        employeeId: String = "",
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
                    plateNumber = plateNumber,
                    userId = userId,
                    employeeId = employeeId,
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
     */
    fun startMonitoringForService(serviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            if (_batteryLevel.value < 20) {
                _errorMessage.value = "Battery level too low. Please charge your device before starting monitoring."
                _isLoading.value = false
                return@launch
            }

            val startResult = serviceRepository.startServiceMonitoring(serviceId)

            when (startResult) {
                is Result.Success -> {
                    AccelerometerService.startService(context, serviceId)
                    _isMonitoring.value = true
                    _isServiceActive.value = true
                    currentServiceId = serviceId
                    getServiceStatusSummary(serviceId)
                    observeServiceReadings(serviceId)
                    _successMessage.value = "Monitoring started successfully"
                }
                is Result.Failure -> {
                    _errorMessage.value = startResult.message ?: "Failed to start monitoring"
                }
            }

            _isLoading.value = false
        }
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
                    // Return unassigned vehicles
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
        plateNumber: String,
        driverId: String,
        employeeId: String = "",
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = vehicleRepository.createVehicleForDriver(vehicleName, plateNumber, driverId, employeeId)) {
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
     * Adds a service to a vehicle by adding the vehicleId to the service's vehicleIds list.
     * This allows a service to be shared across multiple vehicles.
     */
    fun addServiceToVehicle(serviceId: String, vehicleId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            _successMessage.value = ""

            when (val serviceResult = serviceRepository.getServiceById(serviceId)) {
                is Result.Success -> {
                    val service = serviceResult.data
                    if (service != null) {
                        // Check if service is already assigned to this vehicle
                        if (service.vehicleIds.contains(vehicleId)) {
                            _errorMessage.value = "Service is already assigned to this vehicle"
                            _isLoading.value = false
                            return@launch
                        }
                        // Add vehicleId to the list
                        val currentVehicleIds = service.vehicleIds.toMutableList()
                        currentVehicleIds.add(vehicleId)
                        val updatedService = service.copy(vehicleIds = currentVehicleIds)
                        when (val updateResult = serviceRepository.updateService(updatedService)) {
                            is Result.Success -> {
                                _successMessage.value = "Service added to vehicle successfully"
                                loadServices() // Refresh services list
                                // Also refresh vehicle-specific services if this vehicle is selected
                                loadServicesForVehicle(vehicleId)
                            }
                            is Result.Failure -> {
                                _errorMessage.value = updateResult.message ?: "Failed to add service to vehicle"
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
     */
    fun startMonitoringForVehicle(vehicleId: String) {
        android.util.Log.i("DashboardViewModel", ">>> startMonitoringForVehicle called with vehicleId: $vehicleId <<<")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            // Validate battery level
            if (_batteryLevel.value < 20) {
                _errorMessage.value = "Battery level too low. Please charge your device before starting monitoring."
                android.util.Log.w("DashboardViewModel", "Battery level too low: ${_batteryLevel.value}")
                _isLoading.value = false
                return@launch
            }

            // Validate vehicle exists
            val vehicle = _vehicles.value.find { it.id == vehicleId }
            if (vehicle == null) {
                android.util.Log.e("DashboardViewModel", "Vehicle not found in list: $vehicleId")
                android.util.Log.d("DashboardViewModel", "Available vehicles: ${_vehicles.value.map { it.id }}")
                _errorMessage.value = "Vehicle not found. Please refresh and try again."
                _isLoading.value = false
                return@launch
            }
            android.util.Log.d("DashboardViewModel", "Found vehicle: ${vehicle.name}")

            // Load services for this vehicle
            android.util.Log.d("DashboardViewModel", "Fetching services for vehicleId: $vehicleId")
            val vehicleServicesResult = serviceRepository.getServicesForVehicle(vehicleId)
            when (vehicleServicesResult) {
                is Result.Success -> {
                    val services = vehicleServicesResult.data ?: emptyList()
                    android.util.Log.d("DashboardViewModel", "Found ${services.size} services for vehicle")
                    if (services.isEmpty()) {
                        _errorMessage.value = "No services found for ${vehicle.name}. Please add a service first."
                        android.util.Log.w("DashboardViewModel", "No services for vehicle: $vehicleId")
                        _isLoading.value = false
                        return@launch
                    }

                    // Select the first service and start monitoring
                    val firstService = services.first()
                    currentServiceId = firstService.id
                    android.util.Log.d("DashboardViewModel", "Using first service: ${firstService.id}, name: ${firstService.name}")
                    
                    // Update selected vehicle
                    _selectedVehicle.value = vehicle
                    _vehicleServices.value = services

                    // Start monitoring
                    android.util.Log.d("DashboardViewModel", "Starting service monitoring for: ${firstService.id}")
                    val startResult = serviceRepository.startServiceMonitoring(firstService.id)
                    when (startResult) {
                        is Result.Success -> {
                            android.util.Log.d("DashboardViewModel", "Firebase monitoring started, calling AccelerometerService.startService")
                            try {
                                AccelerometerService.startService(context, firstService.id)
                                android.util.Log.i("DashboardViewModel", "AccelerometerService started successfully")
                            } catch (e: Exception) {
                                android.util.Log.e("DashboardViewModel", "Error starting AccelerometerService", e)
                                _errorMessage.value = "Failed to start accelerometer service: ${e.message}"
                                _isLoading.value = false
                                return@launch
                            }
                            _isMonitoring.value = true
                            _isServiceActive.value = true
                            _mileageThreshold.value = firstService.mileageLimit.toInt()

                            _serviceStatus.value = ServiceStatusSummary(
                                serviceId = firstService.id,
                                totalReadings = 0,
                                totalMovement = 0f,
                                averageMovement = 0f,
                                isMonitoring = true,
                                lastReadingTime = System.currentTimeMillis(),
                                isMileageExceeded = false,
                            )

                            _successMessage.value = "Monitoring started for ${vehicle.name}"
                            android.util.Log.i("DashboardViewModel", "Monitoring started successfully for: ${vehicle.name}")
                            observeServiceReadings(firstService.id)
                        }
                        is Result.Failure -> {
                            _errorMessage.value = startResult.message ?: "Failed to start monitoring"
                            android.util.Log.e("DashboardViewModel", "Failed to start monitoring: ${startResult.message}")
                        }
                    }
                }
                is Result.Failure -> {
                    _errorMessage.value = vehicleServicesResult.message ?: "Failed to load services for vehicle"
                    android.util.Log.e("DashboardViewModel", "Failed to load services: ${vehicleServicesResult.message}")
                }
            }

            _isLoading.value = false
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
    private fun loadServicesForReset(vehicleId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            when (val result = serviceRepository.getServicesForVehicle(vehicleId)) {
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
     */
    fun confirmResetService() {
        val service = _selectedServiceForReset.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            _successMessage.value = ""

            val resetResult = serviceRepository.resetServiceReadings(service.id)

            when (resetResult) {
                is Result.Success -> {
                    _successMessage.value = "Service readings reset successfully for ${service.variantName.ifEmpty { service.name }}"
                    hideResetServiceDialog()
                }
                is Result.Failure -> {
                    _errorMessage.value = resetResult.message ?: "Failed to reset service readings"
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
}
