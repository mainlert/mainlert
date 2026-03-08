package com.mainlert.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mainlert.data.models.VehicleServiceMapping
import com.mainlert.data.repositories.ServiceRepository
import com.mainlert.data.repositories.VehicleRepository
import com.mainlert.data.repositories.VehicleServiceMappingRepository
import dagger.hilt.android.AndroidEntryPoint
import com.mainlert.services.BootReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.sqrt
import android.util.Log

/**
 * Foreground service for monitoring accelerometer data with sensor fusion.
 * Uses gyroscope and rotation vector to remove gravity and isolate vehicle movement.
 *
 * Usage:
 * ```kotlin
 * val intent = Intent(context, AccelerometerService::class.java)
 * context.startForegroundService(intent)
 * ```
 */
@AndroidEntryPoint
class AccelerometerService : Service(), SensorEventListener {
    companion object {
           private const val TAG = "AccelerometerService"
           
           // Intent actions
           const val ACTION_START_MONITORING = "com.mainlert.mainlertapp.START_MONITORING"
           const val ACTION_STOP_MONITORING = "com.mainlert.mainlertapp.STOP_MONITORING"
           const val ACTION_RETRY_LOADING = "com.mainlert.mainlertapp.RETRY_LOADING"
           const val ACTION_BROADCAST_ACCELEROMETER = "com.mainlert.mainlertapp.BROADCAST_ACCELEROMETER"
           
           // Intent extras
           const val EXTRA_SERVICE_ID = "service_id"
           const val EXTRA_VEHICLE_ID = "vehicle_id"
           const val EXTRA_MAPPING_ID = "mapping_id"
           const val EXTRA_X = "extra_x"
           const val EXTRA_Y = "extra_y"
           const val EXTRA_Z = "extra_z"
           const val EXTRA_MAGNITUDE = "extra_magnitude"
           const val EXTRA_TOTAL_MOVEMENT = "extra_total_movement"
           const val EXTRA_IS_VEHICLE_MOVEMENT = "extra_is_vehicle_movement"
           const val EXTRA_IS_MONITORING = "extra_is_monitoring"
           const val EXTRA_DETECTION_MODE = "extra_detection_mode"
           const val EXTRA_IS_USING_CACHED_THRESHOLDS = "extra_is_using_cached_thresholds"
           const val EXTRA_AUTO_STOP_TIMEOUT = "extra_auto_stop_timeout"
           
           // Service constants
           const val STOP_FOREGROUND_REMOVE = 0
           
           // Monitoring state checkpoint preferences
           private const val PREFS_NAME = "monitoring_state_checkpoint"
           const val KEY_MAPPING_ID = "mapping_id"
           const val KEY_VEHICLE_ID = "vehicle_id"
           const val KEY_SERVICE_ID = "service_id"
           const val KEY_IS_MONITORING = "is_monitoring"
        
        fun startService(
            context: Context,
            serviceId: String,
            vehicleId: String,
            autoStopTimeout: Long = 3600000L, // Default 1 hour
        ) {
            val intent =
                Intent(context, AccelerometerService::class.java).apply {
                    action = ACTION_START_MONITORING
                    putExtra(EXTRA_SERVICE_ID, serviceId)
                    putExtra(EXTRA_VEHICLE_ID, vehicleId)
                    putExtra(EXTRA_AUTO_STOP_TIMEOUT, autoStopTimeout)
                }
            // Use startForegroundService for Android O+ (required for foreground services)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, AccelerometerService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
    }
    
    @Inject
    lateinit var remoteConfigRepository: com.mainlert.data.repositories.RemoteConfigRepository

    @Inject
    lateinit var thresholdCache: ThresholdCache

    @Inject
    lateinit var vehicleRepository: com.mainlert.data.repositories.VehicleRepository

    @Inject
    lateinit var vehicleServiceMappingRepository: VehicleServiceMappingRepository

    @Inject
    lateinit var serviceRepository: com.mainlert.data.repositories.ServiceRepository

    @Inject
    lateinit var serviceVariantRepository: com.mainlert.data.repositories.ServiceVariantRepository

    @Inject
    lateinit var lockRepository: com.mainlert.data.repositories.LockRepository

    @Inject
    lateinit var localDatabase: com.mainlert.data.local.LocalDatabase

    /** Sensor manager for accessing device sensors. */
    private lateinit var sensorManager: SensorManager

    /** Accelerometer sensor instance. */
    private var accelerometer: Sensor? = null

    /** Rotation vector sensor for orientation/sensor fusion. */
    private var rotationVector: Sensor? = null

    /** Gyroscope sensor for additional rotation data. */
    private var gyroscope: Sensor? = null

    /** Coroutine scope for Firebase writes only. */
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Indicates if monitoring is currently active. */
    private var isMonitoring = false

    // Detection mode flag (for boot detection)
    private var isDetectionMode = false
    private var detectionStartTime = 0L
    private val detectionTimeout = 30000L // 30 seconds max detection
    
    // Auto-stop functionality
    private var lastVehicleMovementTime = 0L
    private var autoStopTimeout = 3600000L // Default 1 hour (will be updated from DashboardViewModel)
    private var isAutoStopped = false

    // Movement detection thresholds (loaded from RemoteConfig at startup)
    private var crashThreshold: Float = 3.0f
    private var minThreshold: Float = 0.5f

    // Service constants
    private val notificationChannelId = "accelerometer_channel"
    private val mileageNotificationChannelId = "mileage_notifications_channel"
    private val notificationId = 1
    private val mileageNotificationId = 2

    // Checkpoint interval for crash recovery (30 seconds)
    private val CHECKPOINT_INTERVAL_MS = 30000L
    private var lastCheckpointTime = 0L

    // Duplicate notification prevention
    private var lastMileageNotificationTime = 0L
    private val notificationCooldown = TimeUnit.MINUTES.toMillis(30) // 30 minutes cooldown

    // Movement tracking variables
    private var isVehicleMovement = false
    private var movementBuffer = mutableListOf<Float>()
    private var bufferMaxSize = 100
    private var currentServiceId: String? = null
    private var currentVehicleId: String? = null
    private var currentMappingId: String? = null
    private var currentServiceMileageLimit: Float = 1000f // Default mileage limit

    // Service reading calculation - using gravity-compensated movement
    private var totalMovement = 0f
    private var readingStartTime = 0L
    private var isServiceActive = false

    // Broadcast throttling
    private var lastBroadcastTime = 0L
    private val broadcastIntervalMs = 500L

    // Gravity estimation for high-pass filter (complementary filter approach)
    private val alpha = 0.8f // Smoothing factor for gravity estimation
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f

    // Rotation matrix and orientation for sensor fusion
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Simplified state management - use Firebase as single source of truth
    private var isFirebaseDataLoaded = false
    private var isMonitoringActive = false
    private var isUsingCachedThresholds = false

    // Retry constants
    private val RETRY_DELAY_MS = 5000L
    private var retryCount = 0
    private val MAX_RETRY_ATTEMPTS = 3

    // Service state enum for better state management
    private var serviceState = ServiceState.IDLE
    enum class ServiceState {
        IDLE,
        LOADING_FIREBASE_DATA,
        MONITORING,
        ERROR_NO_INTERNET,
        ERROR_FIREBASE_FAILED
    }

    // Connectivity variables
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    override fun onCreate() {
        super.onCreate()
        Log.d("ServiceDebug", ">>> onCreate() called")
        
        // FIXED: Initialize service state with proper synchronization
        synchronized(this) {
            serviceState = ServiceState.IDLE
            Log.d("ServiceDebug", ">>> Service state initialized to IDLE")
        }
        
        // Initialize sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        // Initialize connectivity manager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Initialize notification channel
        createNotificationChannel()
        
        // Start foreground service with initial notification
        startForeground(1, createNotification())
        
        // Setup network monitoring for automatic retry (modern approach)
        setupNetworkMonitoring()
        
        Log.d("ServiceDebug", ">>> onCreate() completed")
    }

    /**
     * Checks if internet connection is available.
     */
    private fun hasInternetConnection(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /**
     * Setup network monitoring using modern NetworkCallback API.
     * Replaces deprecated CONNECTIVITY_ACTION broadcast receiver.
     */
    private fun setupNetworkMonitoring() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                android.util.Log.i("AccelerometerService", "Internet connection available, retrying Firebase loading...")
                retryCount = 0
                loadFirebaseData()
            }
            
            override fun onLost(network: Network) {
                super.onLost(network)
                android.util.Log.w("AccelerometerService", "Internet connection lost")
            }
        }
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }
    
    /**
     * Saves the current monitoring state to SharedPreferences checkpoint.
     * This allows the UI to restore state after being destroyed.
     */
    private fun saveMonitoringStateToPrefs() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString(KEY_MAPPING_ID, currentMappingId)
            editor.putString(KEY_VEHICLE_ID, currentVehicleId)
            editor.putString(KEY_SERVICE_ID, currentServiceId)
            editor.putBoolean(KEY_IS_MONITORING, isMonitoring)
            editor.apply()
            android.util.Log.d(TAG, "Monitoring state saved to SharedPreferences: mappingId=$currentMappingId, vehicleId=$currentVehicleId, isMonitoring=$isMonitoring")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save monitoring state to SharedPreferences", e)
        }
    }
    
    /**
     * Clears the monitoring state from SharedPreferences checkpoint.
     * Called when monitoring stops to prevent stale state.
     */
    private fun clearMonitoringStateFromPrefs() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.remove(KEY_MAPPING_ID)
            editor.remove(KEY_VEHICLE_ID)
            editor.remove(KEY_SERVICE_ID)
            editor.remove(KEY_IS_MONITORING)
            editor.apply()
            android.util.Log.d(TAG, "Monitoring state cleared from SharedPreferences")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to clear monitoring state from SharedPreferences", e)
        }
    }
    
    /**
     * Checks for active mapping in Firebase and restores service state on restart.
     * This enables the service to automatically resume monitoring after app restart.
     */
    private suspend fun checkForActiveMappingAndRestore(): Boolean {
        android.util.Log.d("AccelerometerService", "Checking for active mapping in Firebase...")
        
        return try {
            val activeMappingResult = vehicleServiceMappingRepository.getActiveMapping()
            
            when (activeMappingResult) {
                is com.mainlert.data.models.Result.Success -> {
                    val activeMapping = activeMappingResult.data
                    if (activeMapping != null && activeMapping.isMonitoring) {
                        android.util.Log.i("AccelerometerService", "Found active mapping: vehicleId=${activeMapping.vehicleId}, serviceId=${activeMapping.serviceId}")
                        
                        // Restore service state from active mapping (but NOT totalMovement - start fresh)
                        currentVehicleId = activeMapping.vehicleId
                        currentServiceId = activeMapping.serviceId
                        currentMappingId = activeMapping.id
                        currentServiceMileageLimit = activeMapping.mileageLimit
                        
                        android.util.Log.i("AccelerometerService", "Restored service state from Firebase. Starting monitoring...")
                        
                        // Start Firebase data loading phase with restored state
                        loadFirebaseData()
                        true
                    } else {
                        android.util.Log.d("AccelerometerService", "No active mapping found or mapping not monitoring")
                        false
                    }
                }
                is com.mainlert.data.models.Result.Failure -> {
                    android.util.Log.w("AccelerometerService", "Failed to check active mapping: ${activeMappingResult.message}")
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AccelerometerService", "Error checking for active mapping", e)
            false
        }
    }

    /**
     * Restores totalMovement from local checkpoint.
     * Called during service start to recover from crashes/restarts.
     * Returns the checkpoint value if found, otherwise 0f.
     */
    private fun restoreCheckpoint(): Float {
        if (currentMappingId == null) {
            android.util.Log.d("AccelerometerService", "No mapping ID available for checkpoint restore")
            return 0f
        }
        
        return try {
            // Use runBlocking to call suspend function from non-suspend context
            val entity = runBlocking {
                localDatabase.mappingDao().getMapping(currentMappingId!!)
            }
            if (entity != null) {
                android.util.Log.i("AccelerometerService", "Checkpoint restored: totalMovement=${entity.totalMovement}, localLastUpdated=${entity.localLastUpdated}")
                entity.totalMovement
            } else {
                android.util.Log.d("AccelerometerService", "No checkpoint found in local database for mapping ${currentMappingId!!}")
                0f
            }
        } catch (e: Exception) {
            android.util.Log.e("AccelerometerService", "Failed to restore checkpoint", e)
            0f
        }
    }

    /**
     * Saves checkpoint asynchronously without triggering immediate Firebase sync.
     * Called periodically (every 30 seconds) for crash recovery.
     */
    private fun saveCheckpointAsync(mappingId: String?, totalMovement: Float) {
        if (mappingId == null) {
            return
        }
        
        serviceScope.launch {
            try {
                val result = vehicleServiceMappingRepository.saveMovementCheckpoint(mappingId, totalMovement)
                when (result) {
                    is com.mainlert.data.models.Result.Success -> {
                        android.util.Log.d("AccelerometerService", "Checkpoint saved: mappingId=$mappingId, totalMovement=$totalMovement")
                    }
                    is com.mainlert.data.models.Result.Failure -> {
                        android.util.Log.w("AccelerometerService", "Failed to save checkpoint: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AccelerometerService", "Exception saving checkpoint", e)
            }
        }
    }

    /**
     * Handles retry logic for Firebase loading failures.
     */
    private fun handleRetry() {
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++
            android.util.Log.w("AccelerometerService", "Retrying Firebase loading (attempt $retryCount)")
            
            // Schedule retry after delay
            Handler(Looper.getMainLooper()).postDelayed({
                loadFirebaseData()
            }, RETRY_DELAY_MS * retryCount)
        } else {
            android.util.Log.e("AccelerometerService", "Max retry attempts reached. Service cannot start.")
            showRetryNotification("Max retry attempts reached. Please check your internet connection and try again.")
            
            // Reset service state to IDLE when max retries reached
            synchronized(this) {
                serviceState = ServiceState.IDLE
            }
        }
    }

    /**
     * Loads all required Firebase data before starting monitoring.
     * Handles connectivity checks and retry logic with comprehensive logging.
     */
    private fun loadFirebaseData() {
        Log.d("ServiceDebug", ">>> loadFirebaseData() called, serviceState=$serviceState")
        android.util.Log.d("AccelerometerService", "Starting Firebase data loading...")
        
        val isOnline = hasInternetConnection()
        
        if (!isOnline) {
            Log.d("ServiceDebug", ">>> No internet connection available - using cached thresholds")
            android.util.Log.i("AccelerometerService", "No internet connection - loading thresholds from local cache")
            
            // Load thresholds from cache when offline
            try {
                crashThreshold = thresholdCache.getCrashThreshold()
                minThreshold = thresholdCache.getMinThreshold()
                Log.d("ServiceDebug", ">>> Loaded thresholds from cache: crashThreshold=$crashThreshold, minThreshold=$minThreshold")
                android.util.Log.i("AccelerometerService", "Using cached thresholds: crashThreshold=$crashThreshold, minThreshold=$minThreshold")
                isUsingCachedThresholds = true
            } catch (e: Exception) {
                android.util.Log.e("AccelerometerService", "Failed to load thresholds from cache", e)
                // Use default values if cache fails
                crashThreshold = 3.0f
                minThreshold = 0.5f
                android.util.Log.w("AccelerometerService", "Using default thresholds due to cache failure: crashThreshold=$crashThreshold, minThreshold=$minThreshold")
            }
        } else {
            // Online: fetch from RemoteConfig and save to cache
            Log.d("ServiceDebug", ">>> Internet connection available - loading from RemoteConfig")
            retryCount = 0
        }
        
        // Load mapping data (works both online and offline)
        // Use coroutine to avoid blocking
        serviceScope.launch {
            try {
                // If online, fetch thresholds from RemoteConfig and save to cache
                if (isOnline) {
                    try {
                        crashThreshold = remoteConfigRepository.getCrashThreshold()
                        minThreshold = remoteConfigRepository.getMinThreshold()
                        Log.d("ServiceDebug", ">>> Loaded thresholds from RemoteConfig: crashThreshold=$crashThreshold, minThreshold=$minThreshold")
                        android.util.Log.d("AccelerometerService", "Successfully loaded RemoteConfig thresholds: crashThreshold=$crashThreshold, minThreshold=$minThreshold")
                        
                        // Save thresholds to cache for offline use
                        thresholdCache.saveThresholds(crashThreshold, minThreshold, 1000L)
                        android.util.Log.i("AccelerometerService", "Thresholds saved to local cache")
                    } catch (e: Exception) {
                        android.util.Log.e("AccelerometerService", "Failed to load RemoteConfig thresholds", e)
                        // Use default values if RemoteConfig fails
                        crashThreshold = 3.0f
                        minThreshold = 0.5f
                        android.util.Log.w("AccelerometerService", "Using default thresholds due to RemoteConfig failure: crashThreshold=$crashThreshold, minThreshold=$minThreshold")
                    }
                }
                
                // Load VehicleServiceMapping data with detailed logging
                if (currentServiceId != null && currentVehicleId != null) {
                    android.util.Log.d("AccelerometerService", "Loading VehicleServiceMapping for service $currentServiceId and vehicle $currentVehicleId")
                    
                    // FIX: If currentServiceId is blank but we have currentMappingId from active mapping restore,
                    // restore the serviceId from the mapping to avoid creating duplicate mappings
                    if ((currentServiceId.isNullOrBlank()) && currentMappingId != null) {
                        android.util.Log.d("AccelerometerService", "currentServiceId is blank but currentMappingId is set - restoring serviceId from mapping")
                        try {
                            val mappingResult = vehicleServiceMappingRepository.getMappingById(currentMappingId!!)
                            when (mappingResult) {
                                is com.mainlert.data.models.Result.Success -> {
                                    val mapping = mappingResult.data
                                    if (mapping != null) {
                                        currentServiceId = mapping.serviceId
                                        android.util.Log.i("AccelerometerService", "Restored serviceId from mapping: $currentServiceId")
                                    } else {
                                        android.util.Log.e("AccelerometerService", "Mapping with ID $currentMappingId returned null - cannot restore serviceId")
                                    }
                                }
                                is com.mainlert.data.models.Result.Failure -> {
                                    android.util.Log.e("AccelerometerService", "Failed to load mapping for serviceId restore: ${mappingResult.message}")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AccelerometerService", "Exception while loading mapping for serviceId restore", e)
                        }
                    }
                    
                    // Use unified mapping loading logic with distributed locking
                    val mapping = loadOrCreateMapping()
                    if (mapping != null) {
                        startMonitoringWithMapping(mapping)
                        return@launch
                    } else {
                        android.util.Log.e("AccelerometerService", "Failed to load or create mapping")
                        // Continue to load vehicle mileage and start monitoring with defaults
                    }
                    
                    // Load vehicle lifetime mileage and start monitoring
                    loadVehicleMileageAndStartMonitoring()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AccelerometerService", "Firebase loading failed", e)
                serviceState = ServiceState.ERROR_FIREBASE_FAILED
                showRetryNotification("Failed to load service data: ${e.message}. Tap to retry.")
                handleRetry()
            }
        }
    }

    /**
     * Shows a retry notification when Firebase loading fails.
     */
    private fun showRetryNotification(errorMessage: String) {
        val retryIntent = Intent(this, AccelerometerService::class.java).apply {
            action = ACTION_RETRY_LOADING
        }
        
        val retryPendingIntent = android.app.PendingIntent.getService(
            this,
            0,
            retryIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            NotificationCompat.Builder(this, notificationChannelId)
                .setContentTitle("MainLert")
                .setContentText(errorMessage)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(retryPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_rotate,
                    "Retry",
                    retryPendingIntent
                )
                .setAutoCancel(false)
                .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        android.util.Log.i("AccelerometerService", ">>> onStartCommand called with action: ${intent?.action}")
        
        // Process intent FIRST based on action type
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                android.util.Log.i("AccelerometerService", "STOP_MONITORING received, stopping monitoring immediately")
                stopMonitoring()
                // After stopping, clear the active mapping state
                // to prevent automatic restoration on next start
                currentMappingId = null
                currentServiceId = null
                currentVehicleId = null
                return START_STICKY
            }
            ACTION_RETRY_LOADING -> {
                android.util.Log.i("AccelerometerService", "RETRY_LOADING received")
                retryCount = 0
                serviceState = ServiceState.LOADING_FIREBASE_DATA
                loadFirebaseData()
                return START_STICKY
            }
            // For START_MONITORING, continue with normal flow (including restoration check)
            ACTION_START_MONITORING -> {
                // Only set serviceId/vehicleId from intent if they are provided (not blank)
                val serviceIdExtra = intent.getStringExtra(EXTRA_SERVICE_ID)
                val vehicleIdExtra = intent.getStringExtra(EXTRA_VEHICLE_ID)
                val autoStopTimeoutExtra = intent.getLongExtra(EXTRA_AUTO_STOP_TIMEOUT, -1L)
                
                if (!serviceIdExtra.isNullOrBlank()) {
                    currentServiceId = serviceIdExtra
                }
                if (!vehicleIdExtra.isNullOrBlank()) {
                    currentVehicleId = vehicleIdExtra
                }
                if (autoStopTimeoutExtra != -1L) {
                    autoStopTimeout = autoStopTimeoutExtra
                    android.util.Log.i("AccelerometerService", "Auto-stop timeout set to: ${autoStopTimeout}ms (${autoStopTimeout / 3600000f} hours)")
                }
                
                android.util.Log.i("AccelerometerService", "START_MONITORING received, serviceId: $currentServiceId, vehicleId: $currentVehicleId")
            }
            else -> {
                android.util.Log.d("AccelerometerService", "Unknown or null action: ${intent?.action}")
            }
        }
        
        // Check for active mapping synchronously if we don't already have one
        // This allows the service to restore state after a reboot or app restart
        if (currentMappingId == null) {
            android.util.Log.d("AccelerometerService", "No current mapping, performing synchronous restoration check...")
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                checkForActiveMappingAndRestore()
            }
        }
        
        // If restoration found an active mapping, monitoring is already started/loading
        // Skip further processing to avoid overwriting restored state
        if (currentMappingId != null) {
            android.util.Log.i("AccelerometerService", "Active mapping restored (currentMappingId=$currentMappingId), monitoring is active")
            return START_STICKY
        }
        
        // If we get here with a START_MONITORING action but no active mapping,
        // we need to start the Firebase data loading phase
        if (intent?.action == ACTION_START_MONITORING) {
            serviceState = ServiceState.LOADING_FIREBASE_DATA
            loadFirebaseData()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        
        // If monitoring is active, save final checkpoint before destruction
        // This handles system kills and unexpected terminations
        if (isMonitoring && currentMappingId != null) {
            android.util.Log.w("AccelerometerService", "onDestroy() called while monitoring active - saving final checkpoint")
            
            // Save checkpoint synchronously with timeout to avoid blocking too long
            try {
                val checkpointSaved = saveCheckpointWithTimeout(currentMappingId!!, totalMovement, 2000L)
                if (checkpointSaved) {
                    android.util.Log.i("AccelerometerService", "Final checkpoint saved successfully: mappingId=${currentMappingId!!}, totalMovement=$totalMovement")
                } else {
                    android.util.Log.w("AccelerometerService", "Failed to save final checkpoint within timeout")
                }
            } catch (e: Exception) {
                android.util.Log.e("AccelerometerService", "Exception saving final checkpoint", e)
            }
        }
        
        // Stop sensor monitoring immediately
        isMonitoring = false
        isServiceActive = false
        sensorManager.unregisterListener(this)
        android.util.Log.d("AccelerometerService", "Sensor listeners unregistered in onDestroy()")
        
        // Cancel all coroutines
        serviceScope.cancel("Service destroyed")
        android.util.Log.d("AccelerometerService", "ServiceScope cancelled in onDestroy()")
        
        // Unregister network monitoring
        if (::connectivityManager.isInitialized && ::networkCallback.isInitialized) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            android.util.Log.d("AccelerometerService", "Network callback unregistered in onDestroy()")
        }
        
        // Clear all state
        currentMappingId = null
        currentServiceId = null
        currentVehicleId = null
        currentServiceMileageLimit = 1000f
        totalMovement = 0f
        serviceState = ServiceState.IDLE
        
        android.util.Log.i("AccelerometerService", "onDestroy() completed - service fully cleaned up")
    }
    
    /**
     * Saves checkpoint with a timeout to avoid blocking onDestroy() too long.
     * Returns true if checkpoint was saved successfully within timeout.
     */
    private fun saveCheckpointWithTimeout(mappingId: String, totalMovement: Float, timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        
        // Use runBlocking with timeout for synchronous checkpoint save
        try {
            runBlocking {
                withTimeout(timeoutMs) {
                    val result = vehicleServiceMappingRepository.saveMovementCheckpoint(mappingId, totalMovement)
                    when (result) {
                        is com.mainlert.data.models.Result.Success -> {
                            android.util.Log.d("AccelerometerService", "Checkpoint saved with timeout wrapper: success")
                        }
                        is com.mainlert.data.models.Result.Failure -> {
                            android.util.Log.w("AccelerometerService", "Checkpoint save failed: ${result.message}")
                        }
                    }
                }
            }
            return true
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.e("AccelerometerService", "Checkpoint save timed out after ${timeoutMs}ms")
            return false
        } catch (e: Exception) {
            android.util.Log.e("AccelerometerService", "Exception in saveCheckpointWithTimeout", e)
            return false
        }
    }

    /**
     * Creates a notification for the foreground service.
     */
    private fun createNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("MainLert")
            .setContentText("Accelerometer monitoring active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Starts monitoring accelerometer data.
     * This method is called from onCreate() to initialize the accelerometer monitoring.
     */
    private fun startAccelerometerMonitoring() {
        android.util.Log.d("AccelerometerService", "Starting accelerometer monitoring...")
        
        // Initialize sensors
        initSensors()
        
        // Start monitoring if we have an active mapping
        if (currentMappingId != null) {
            android.util.Log.d("AccelerometerService", "Found active mapping, starting monitoring...")
            startMonitoring()
        } else {
            android.util.Log.d("AccelerometerService", "No active mapping found, waiting for start command...")
        }
    }

    private fun initSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    notificationChannelId,
                    "Accelerometer Service",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows accelerometer monitoring status"
                    enableLights(false)
                    enableVibration(false)
                }

            val mileageChannel =
                NotificationChannel(
                    mileageNotificationChannelId,
                    "Mileage Notifications",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Alerts when a vehicle service reaches mileage limit"
                    enableLights(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 250, 500) // Pattern: 0ms delay, 500ms vibrate, 250ms pause, 500ms vibrate
                    lightColor = android.graphics.Color.RED
                }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(mileageChannel)
        }
    }

    /**
     * Shows a mileage notification to alert the user that the service reading has reached the mileage limit.
     */
    private fun showMileageNotification() {
        val currentTime = System.currentTimeMillis()

        // Prevent duplicate notifications within cooldown period
        if (currentTime - lastMileageNotificationTime < notificationCooldown) {
            return
        }

        lastMileageNotificationTime = currentTime

        val notification =
            NotificationCompat.Builder(this, mileageNotificationChannelId)
                .setContentTitle("Mileage Limit Reached")
                .setContentText("Service reading has reached the mileage limit - your vehicle needs service")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 250, 500))
                .setLights(android.graphics.Color.RED, 3000, 3000)
                .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(mileageNotificationId, notification)
    }

    private fun startMonitoring() {
        android.util.Log.i("AccelerometerService", ">>> startMonitoring() called")
        
        // FIXED: Add state validation before starting monitoring
        synchronized(this) {
            when (serviceState) {
                ServiceState.IDLE -> {
                    android.util.Log.w("AccelerometerService", "Cannot start monitoring from IDLE state - Firebase data not loaded yet")
                    return
                }
                ServiceState.LOADING_FIREBASE_DATA -> {
                    android.util.Log.w("AccelerometerService", "Cannot start monitoring while loading Firebase data")
                    return
                }
                ServiceState.ERROR_NO_INTERNET, ServiceState.ERROR_FIREBASE_FAILED -> {
                    android.util.Log.w("AccelerometerService", "Cannot start monitoring due to error state: $serviceState")
                    return
                }
                ServiceState.MONITORING -> {
                    if (isMonitoring) {
                        android.util.Log.w("AccelerometerService", "Already monitoring, returning early")
                        return
                    }
                }
            }
        }

        // Set service state to monitoring
        serviceState = ServiceState.MONITORING

        // Load thresholds from RemoteConfig
        crashThreshold = remoteConfigRepository.getCrashThreshold()
        minThreshold = remoteConfigRepository.getMinThreshold()
        android.util.Log.d("AccelerometerService", "Loaded thresholds from RemoteConfig: crashThreshold=$crashThreshold, minThreshold=$minThreshold")

        isMonitoring = true
        isServiceActive = true
        readingStartTime = System.currentTimeMillis()
        
        // Restore totalMovement from checkpoint (persistence enhancement)
        // If checkpoint exists, use it; otherwise start from 0
        totalMovement = restoreCheckpoint()
        android.util.Log.i("AccelerometerService", "Restored totalMovement from checkpoint: $totalMovement")
        
        // Initialize auto-stop tracking
        lastVehicleMovementTime = System.currentTimeMillis()
        isAutoStopped = false
        android.util.Log.i("AccelerometerService", "Auto-stop initialized: timeout=${autoStopTimeout}ms (${autoStopTimeout / 3600000f} hours)")
        movementBuffer.clear()
        lastBroadcastTime = 0L
        lastCheckpointTime = System.currentTimeMillis()

        // Reset gravity estimation
        gravityX = 0f
        gravityY = 0f
        gravityZ = 0f

        android.util.Log.d("AccelerometerService", "Creating foreground notification...")
        val notification =
            NotificationCompat.Builder(this, notificationChannelId)
                .setContentTitle("MainLert")
                .setContentText("Accelerometer monitoring active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        android.util.Log.d("AccelerometerService", "Starting foreground service...")
        startForeground(notificationId, notification)
        android.util.Log.i("AccelerometerService", "Foreground notification started")

        // Register sensors
        rotationVector?.let { sensor ->
            android.util.Log.d("AccelerometerService", "Registering rotation vector sensor: ${sensor.name}")
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
            )
        }

        accelerometer?.let { sensor ->
            android.util.Log.d("AccelerometerService", "Registering accelerometer sensor: ${sensor.name}")
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
            )
        } ?: run {
            android.util.Log.e("AccelerometerService", "NO ACCELEROMETER SENSOR FOUND on this device!")
        }
        
        // Log service state for debugging
        android.util.Log.i("AccelerometerService", "Monitoring started: serviceId=$currentServiceId, vehicleId=$currentVehicleId, mappingId=$currentMappingId, totalMovement=$totalMovement")
    }

    private fun stopMonitoring() {
        if (!isMonitoring) {
            android.util.Log.d("AccelerometerService", "stopMonitoring() called but already stopped")
            return
        }

        android.util.Log.i("AccelerometerService", "Stopping monitoring: serviceId=$currentServiceId, vehicleId=$currentVehicleId, mappingId=$currentMappingId, totalMovement=$totalMovement")

        // Clear monitoring state from SharedPreferences (do this before clearing state)
        clearMonitoringStateFromPrefs()
        
        // Mark as not monitoring immediately to prevent further updates
        isMonitoring = false
        isServiceActive = false

        // Broadcast state change to notify UI (especially important for state synchronization)
        broadcastMonitoringState()

        // Unregister sensor listeners
        sensorManager.unregisterListener(this)
        android.util.Log.d("AccelerometerService", "Sensor listeners unregistered")

        // Stop foreground notification
        stopForeground(STOP_FOREGROUND_REMOVE)
        android.util.Log.d("AccelerometerService", "Foreground notification removed")

        // Save final service reading to VehicleServiceMapping and update vehicle mileage
        // Do this BEFORE stopping the service to ensure data is saved
        if (currentMappingId != null && totalMovement > 0) {
            android.util.Log.d("AccelerometerService", "Saving final reading to Firebase: mappingId=$currentMappingId, totalMovement=$totalMovement")
            
            // Launch save operations on serviceScope (which is still active)
            serviceScope.launch {
                try {
                    // Save final reading to mapping
                    val result = vehicleServiceMappingRepository.updateMappingMovement(currentMappingId!!, totalMovement)
                    
                    when (result) {
                        is com.mainlert.data.models.Result.Success -> {
                            android.util.Log.i("AccelerometerService", "Final reading saved to VehicleServiceMapping: mappingId=$currentMappingId, totalMovement=$totalMovement")
                        }
                        is com.mainlert.data.models.Result.Failure -> {
                            android.util.Log.e("AccelerometerService", "Failed to save final reading: mappingId=$currentMappingId, error=${result.message}")
                        }
                    }

                    // Update vehicle lifetime mileage - this accumulates forever and never resets
                    currentVehicleId?.let { vehicleId ->
                        val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
                        if (vehicleResult is com.mainlert.data.models.Result.Success) {
                            val updateResult = vehicleRepository.updateVehicleLifetimeMileage(vehicleId, totalMovement)
                            when (updateResult) {
                                is com.mainlert.data.models.Result.Success -> {
                                    android.util.Log.d("AccelerometerService", "Vehicle lifetime mileage updated: +$totalMovement")
                                }
                                is com.mainlert.data.models.Result.Failure -> {
                                    android.util.Log.e("AccelerometerService", "Failed to update vehicle lifetime mileage: vehicleId=$vehicleId, error=${updateResult.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AccelerometerService", "Exception during final save operations", e)
                } finally {
                    // After saving, clear state and stop the service
                    android.util.Log.d("AccelerometerService", "Clearing service state and stopping service")
                    currentMappingId = null
                    currentServiceId = null
                    currentVehicleId = null
                    currentServiceMileageLimit = 1000f
                    totalMovement = 0f
                    serviceState = ServiceState.IDLE
                    
                    // Actually stop the service
                    stopSelf()
                    android.util.Log.i("AccelerometerService", "Service stopped successfully")
                }
            }
        } else {
            // No final reading to save, just clear state and stop immediately
            android.util.Log.d("AccelerometerService", "No final reading to save, stopping service immediately")
            currentMappingId = null
            currentServiceId = null
            currentVehicleId = null
            currentServiceMileageLimit = 1000f
            totalMovement = 0f
            serviceState = ServiceState.IDLE
            stopSelf()
            android.util.Log.i("AccelerometerService", "Service stopped successfully")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            when (sensorEvent.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    // Update rotation matrix from rotation vector for sensor fusion
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorEvent.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                     // Skip if not monitoring and not in detection mode
                     if (!isMonitoring && !isDetectionMode) return@let

                     val accelX = sensorEvent.values[0]
                     val accelY = sensorEvent.values[1]
                     val accelZ = sensorEvent.values[2]

                    val currentTime = System.currentTimeMillis()

                    // Apply gravity estimation (complementary filter approach)
                    // Estimate gravity using low-pass filter
                    gravityX = alpha * gravityX + (1 - alpha) * accelX
                    gravityY = alpha * gravityY + (1 - alpha) * accelY
                    gravityZ = alpha * gravityZ + (1 - alpha) * accelZ

                    // Calculate linear acceleration (remove gravity)
                    val linearX = accelX - gravityX
                    val linearY = accelY - gravityY
                    val linearZ = accelZ - gravityZ

                    // Calculate magnitude of linear acceleration (true movement)
                    val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ).toFloat()

                    // Process movement data SYNCHRONOUSLY (no coroutine)
                    processMovementData(linearX, linearY, linearZ, magnitude, currentTime)
                }
            }
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {
        // Handle sensor accuracy changes if needed
        android.util.Log.d("AccelerometerService", "Sensor accuracy changed: ${sensor?.name} -> $accuracy")
    }

    /**
     * Processes movement data synchronously.
     * Uses gravity-compensated linear acceleration for accurate movement detection.
     */
    private fun processMovementData(
        linearX: Float,
        linearY: Float,
        linearZ: Float,
        magnitude: Float,
        currentTime: Long,
    ) {
        // Add to movement buffer
        movementBuffer.add(magnitude)
        if (movementBuffer.size > bufferMaxSize) {
            movementBuffer.removeAt(0)
        }

        // Check for movement type using configurable thresholds
        val isMoving = magnitude > minThreshold
        if (isMoving) {
            // Update total movement only when device is actually moving
            totalMovement += magnitude

            // Save checkpoint periodically for crash recovery (every 30 seconds)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCheckpointTime >= CHECKPOINT_INTERVAL_MS) {
                saveCheckpointAsync(currentMappingId, totalMovement)
                lastCheckpointTime = currentTime
            }

            // Determine if this is vehicle or human movement
            val avgMovement = movementBuffer.average().toFloat()
            isVehicleMovement = avgMovement > crashThreshold

            // Handle detection mode
            if (isDetectionMode) {
                // Check if vehicle movement is detected
                if (isVehicleMovement) {
                    android.util.Log.i("AccelerometerService", "Vehicle movement detected in detection mode! Launching app...")
                    
                    // Launch the app to show vehicle selection dialog
                    launchAppFromDetection()
                    
                    // Stop the service after launching app
                    stopSelf()
                    return
                }
                
                // Check if detection timeout has been reached
                if (currentTime - detectionStartTime > detectionTimeout) {
                    android.util.Log.i("AccelerometerService", "Detection timeout reached without vehicle movement. Stopping service.")
                    stopSelf()
                    return
                }
            } else {
                // Normal monitoring mode
                
                // Check for auto-stop: if vehicle movement stopped, track inactivity
                if (isVehicleMovement) {
                    // Vehicle is moving - reset auto-stop timer and restart if previously auto-stopped
                    if (isAutoStopped) {
                        android.util.Log.i("AccelerometerService", "Vehicle movement detected after auto-stop - restarting monitoring")
                        isAutoStopped = false
                        // Reset last movement time to current to give time before next auto-stop
                        lastVehicleMovementTime = currentTime
                    } else {
                        // Normal monitoring - update last movement time
                        lastVehicleMovementTime = currentTime
                    }
                    
                    // Update Firebase and check mileage
                    updateFirebaseMappingForSelectedService(currentMappingId, totalMovement)
                    checkForMileageLimit()
                } else {
                    // No vehicle movement detected - check if auto-stop should trigger
                    if (!isAutoStopped && isMonitoring && currentTime - lastVehicleMovementTime > autoStopTimeout) {
                        android.util.Log.i("AccelerometerService", "Auto-stop triggered: Vehicle stationary for ${(autoStopTimeout / 3600000)} hours. Stopping monitoring.")
                        stopMonitoring()
                        isAutoStopped = true
                    }
                }
            }
        } else {
            // Not moving at all (magnitude below threshold)
            // Still check for auto-stop if we were previously monitoring with vehicle movement
            if (!isDetectionMode && !isAutoStopped && isMonitoring && currentTime - lastVehicleMovementTime > autoStopTimeout) {
                android.util.Log.i("AccelerometerService", "Auto-stop triggered: No movement for ${(autoStopTimeout / 3600000)} hours. Stopping monitoring.")
                stopMonitoring()
                isAutoStopped = true
            }
        }

        // Broadcast accelerometer readings to UI every 500ms (throttled)
        if (currentTime - lastBroadcastTime > broadcastIntervalMs) {
            broadcastAccelerometerData(linearX, linearY, linearZ, magnitude)
            lastBroadcastTime = currentTime
        }
    }

    /**
     * Broadcasts gravity-compensated accelerometer data to the UI layer for real-time display.
     */
    private fun broadcastAccelerometerData(
        linearX: Float,
        linearY: Float,
        linearZ: Float,
        magnitude: Float,
    ) {
        val intent = Intent(ACTION_BROADCAST_ACCELEROMETER).apply {
            putExtra(EXTRA_X, linearX)
            putExtra(EXTRA_Y, linearY)
            putExtra(EXTRA_Z, linearZ)
            putExtra(EXTRA_MAGNITUDE, magnitude)
            putExtra(EXTRA_TOTAL_MOVEMENT, totalMovement)
            putExtra(EXTRA_IS_VEHICLE_MOVEMENT, isVehicleMovement)
            putExtra(EXTRA_IS_MONITORING, isMonitoring)
            putExtra(EXTRA_VEHICLE_ID, currentVehicleId)
            putExtra(EXTRA_MAPPING_ID, currentMappingId)
            putExtra(EXTRA_IS_USING_CACHED_THRESHOLDS, isUsingCachedThresholds)
        }
        val sent = LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        android.util.Log.d("AccelerometerService", "Broadcast sent: $sent, totalMovement=$totalMovement, magnitude=$magnitude, vehicleId=$currentVehicleId, mappingId=$currentMappingId")
    }

    /**
     * Broadcasts monitoring state change (used when stopping).
     */
    private fun broadcastMonitoringState() {
        val intent = Intent(ACTION_BROADCAST_ACCELEROMETER).apply {
            putExtra(EXTRA_IS_MONITORING, isMonitoring)
            putExtra(EXTRA_VEHICLE_ID, currentVehicleId)
            putExtra(EXTRA_MAPPING_ID, currentMappingId)
            putExtra(EXTRA_TOTAL_MOVEMENT, totalMovement)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        android.util.Log.d("AccelerometerService", "State broadcast sent: isMonitoring=$isMonitoring, vehicleId=$currentVehicleId, mappingId=$currentMappingId")
    }

    /**
     * Checks if mileage limit reached and handles it.
     * If serviceId is empty (monitoring all services), checks all services for mileage limits.
     * Uses the service's stored mileage limit directly, eliminating runtime variant lookups.
     */
    private fun checkForMileageLimit() {
        // If we have a specific service, check just that one
        if (currentServiceId != null) {
            // Use the service's stored mileage limit directly
            val actualMileageLimit = currentServiceMileageLimit
            
            // Check if total movement has reached the mileage limit while in vehicle movement
            if (isVehicleMovement && totalMovement >= actualMileageLimit) {
                android.util.Log.i("AccelerometerService", "MILEAGE LIMIT REACHED! totalMovement=$totalMovement, limit=$actualMileageLimit (service limit for service $currentServiceId)")

                // Save reading to VehicleServiceMapping
                if (currentMappingId != null) {
                    serviceScope.launch {
                        vehicleServiceMappingRepository.updateMappingMovement(currentMappingId!!, totalMovement)
                        android.util.Log.d("AccelerometerService", "Mileage limit reading saved to VehicleServiceMapping")
                    }
                }

                // Show notification
                showMileageNotification()

                // Stop monitoring
                stopMonitoring()
            }
        } 
        // If serviceId is empty, we're monitoring all services - check all mappings for mileage limits
        else if (currentVehicleId != null) {
            checkAllServicesForMileageLimit()
        }
    }
    
    /**
     * Checks all services for the vehicle for mileage limits when monitoring all services.
     */
    private fun checkAllServicesForMileageLimit() {
        serviceScope.launch {
            try {
                android.util.Log.d("AccelerometerService", "Checking mileage limits for all services on vehicle $currentVehicleId")
                
                // Get all mappings for this vehicle
                val mappingsResult = vehicleServiceMappingRepository.getMappingsForVehicle(currentVehicleId!!)
                
                when (mappingsResult) {
                    is com.mainlert.data.models.Result.Success -> {
                        val mappings = mappingsResult.data ?: emptyList()
                        
                        if (mappings.isEmpty()) {
                            android.util.Log.w("AccelerometerService", "No mappings found for vehicle $currentVehicleId")
                            return@launch
                        }
                        
                        var anyServiceReachedLimit = false
                        
                        // Check each mapping for mileage limit
                        for (mapping in mappings) {
                            try {
                                // Get the actual variant mileage limit for this service
                                val actualMileageLimit = getActualVariantMileageLimitForService(mapping.serviceId)
                                
                                // Check if this service has reached its limit
                                if (isVehicleMovement && mapping.totalMovement >= actualMileageLimit) {
                                    android.util.Log.i("AccelerometerService", "MILEAGE LIMIT REACHED for service ${mapping.serviceId}! totalMovement=${mapping.totalMovement}, limit=$actualMileageLimit")
                                    anyServiceReachedLimit = true
                                    
                                    // Update this specific mapping
                                    val updateResult = vehicleServiceMappingRepository.updateMappingMovement(mapping.id, mapping.totalMovement)
                                    when (updateResult) {
                                        is com.mainlert.data.models.Result.Success -> {
                                            android.util.Log.d("AccelerometerService", "Mileage limit reading saved for service ${mapping.serviceId}")
                                        }
                                        is com.mainlert.data.models.Result.Failure -> {
                                            android.util.Log.e("AccelerometerService", "Failed to save mileage limit reading for service ${mapping.serviceId}: ${updateResult.message}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("AccelerometerService", "Exception checking mileage limit for service ${mapping.serviceId}: ${e.message}")
                            }
                        }
                        
                        // If any service reached the limit, show notification and stop monitoring
                        if (anyServiceReachedLimit) {
                            showMileageNotification()
                            stopMonitoring()
                        }
                        
                    }
                    is com.mainlert.data.models.Result.Failure -> {
                        android.util.Log.e("AccelerometerService", "Failed to get mappings for vehicle $currentVehicleId: ${mappingsResult.message}")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AccelerometerService", "Exception checking mileage limits for all services on vehicle $currentVehicleId", e)
            }
        }
    }
    
    /**
     * Gets the actual mileage limit from a specific service's assigned variant.
     * Adds defensive checks for empty/invalid variant IDs to prevent Firebase errors.
     */
    private suspend fun getActualVariantMileageLimitForService(serviceId: String): Float {
        return try {
            // Get the service to find its variantId
            val serviceResult = serviceRepository.getServiceById(serviceId)
            
            when (serviceResult) {
                is com.mainlert.data.models.Result.Success -> {
                    val service = serviceResult.data
                    if (service?.variantId != null && service.variantId.isNotEmpty()) {
                        // Defensive check: only call getVariantById if variantId is valid
                        try {
                            // Look up the variant to get its actual mileage limit
                            val variantResult = serviceVariantRepository.getVariantById(service.variantId)
                            
                            when (variantResult) {
                                is com.mainlert.data.models.Result.Success -> {
                                    val variant = variantResult.data
                                    if (variant != null) {
                                        android.util.Log.d("AccelerometerService", "Using variant mileage limit: ${variant.mileageLimit} for service $serviceId")
                                        return variant.mileageLimit
                                    }
                                }
                                is com.mainlert.data.models.Result.Failure -> {
                                    android.util.Log.w("AccelerometerService", "Failed to get variant for service $serviceId: ${variantResult.message}")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AccelerometerService", "Exception during variant lookup for service $serviceId: ${e.message}")
                        }
                    } else {
                        android.util.Log.d("AccelerometerService", "Service $serviceId has empty or null variantId, using service's default mileage limit: ${service?.mileageLimit}")
                    }
                }
                is com.mainlert.data.models.Result.Failure -> {
                    android.util.Log.w("AccelerometerService", "Failed to get service $serviceId: ${serviceResult.message}")
                }
            }
            
            // Fallback to service's default mileage limit or 1000f
            val fallbackLimit = try {
                val serviceResult = serviceRepository.getServiceById(serviceId)
                when (serviceResult) {
                    is com.mainlert.data.models.Result.Success -> serviceResult.data?.mileageLimit ?: 1000f
                    else -> 1000f
                }
            } catch (e: Exception) {
                1000f
            }
            
            android.util.Log.d("AccelerometerService", "Using fallback mileage limit: $fallbackLimit for service $serviceId")
            fallbackLimit
        } catch (e: Exception) {
            android.util.Log.e("AccelerometerService", "Error getting variant mileage limit for service $serviceId", e)
            // Fallback to default limit on exception
            android.util.Log.d("AccelerometerService", "Using fallback mileage limit: 1000f for service $serviceId")
            1000f
        }
    }


    /**
     * Gets the actual mileage limit from the service's assigned variant.
     * Falls back to the mapping's stored limit if variant lookup fails.
     */
    private fun getActualVariantMileageLimit(): Float {
        return try {
            // Get the current service to find its variantId
            if (currentServiceId != null) {
                // Use runBlocking to call suspend function synchronously
                val serviceResult = kotlinx.coroutines.runBlocking {
                    serviceRepository.getServiceById(currentServiceId!!)
                }
                
                when (serviceResult) {
                    is com.mainlert.data.models.Result.Success -> {
                        val service = serviceResult.data
                        if (service?.variantId != null) {
                            // Look up the variant to get its actual mileage limit
                            val variantResult = kotlinx.coroutines.runBlocking {
                                serviceVariantRepository.getVariantById(service.variantId)
                            }
                            
                            when (variantResult) {
                                is com.mainlert.data.models.Result.Success -> {
                                    val variant = variantResult.data
                                    if (variant != null) {
                                        android.util.Log.d("AccelerometerService", "Using variant mileage limit: ${variant.mileageLimit} for service $currentServiceId")
                                        return variant.mileageLimit
                                    }
                                }
                                is com.mainlert.data.models.Result.Failure -> {
                                    android.util.Log.w("AccelerometerService", "Failed to get variant: ${variantResult.message}")
                                }
                            }
                        }
                    }
                    is com.mainlert.data.models.Result.Failure -> {
                        android.util.Log.w("AccelerometerService", "Failed to get service: ${serviceResult.message}")
                    }
                }
            }
            
            // Fallback to the mapping's stored limit
            android.util.Log.d("AccelerometerService", "Using fallback mileage limit: $currentServiceMileageLimit")
            currentServiceMileageLimit
        } catch (e: Exception) {
            android.util.Log.e("AccelerometerService", "Error getting variant mileage limit", e)
            // Fallback to the mapping's stored limit on exception
            android.util.Log.d("AccelerometerService", "Using fallback mileage limit: $currentServiceMileageLimit")
            currentServiceMileageLimit
        }
    }

    /**
     * Updates the Firebase VehicleServiceMapping with the latest totalMovement for the selected vehicle service.
     * If serviceId is empty (monitoring all services), updates all mappings for the vehicle.
     * This ensures service readings are updated in real-time via VehicleServiceMapping.
     */
    private fun updateFirebaseMappingForSelectedService(mappingId: String?, totalMovement: Float) {
        // Only update Firebase when vehicle is actually moving (not human movement)
        if (!isVehicleMovement) {
            android.util.Log.d("AccelerometerService", "Not vehicle movement - skipping Firebase update")
            return
        }
        
        // If we have a specific mapping ID, update just that one
        if (mappingId != null) {
            updateSingleMapping(mappingId, totalMovement)
            return
        }
        
        // If serviceId is empty, we're monitoring all services - update all mappings for the vehicle
        if (currentServiceId.isNullOrEmpty() && currentVehicleId != null) {
            updateAllMappingsForVehicle(currentVehicleId!!, totalMovement)
        } else {
            android.util.Log.d("AccelerometerService", "No mapping ID and no vehicle ID - skipping Firebase update")
        }
    }
    
    /**
     * Updates a single VehicleServiceMapping.
     */
    private fun updateSingleMapping(mappingId: String, totalMovement: Float) {
        serviceScope.launch {
            try {
                android.util.Log.d("AccelerometerService", "Attempting Firebase update: mappingId=$mappingId, totalMovement=$totalMovement")
                val result = vehicleServiceMappingRepository.updateMappingMovement(mappingId, totalMovement)
                
                when (result) {
                    is com.mainlert.data.models.Result.Success -> {
                        android.util.Log.i("AccelerometerService", "Firebase mapping updated successfully: mappingId=$mappingId, totalMovement=$totalMovement")
                    }
                    is com.mainlert.data.models.Result.Failure -> {
                        android.util.Log.e("AccelerometerService", "Firebase update failed: mappingId=$mappingId, error=${result.message}")
                        // Show a notification to alert the user about the Firebase sync issue
                        showFirebaseSyncErrorNotification(result.message ?: "Failed to sync readings to cloud")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AccelerometerService", "Exception during Firebase update: mappingId=$mappingId", e)
                showFirebaseSyncErrorNotification("Network error: ${e.message}")
            }
        }
    }
    
    /**
     * Updates all VehicleServiceMappings for the vehicle when monitoring all services.
     */
    private fun updateAllMappingsForVehicle(vehicleId: String, totalMovement: Float) {
        serviceScope.launch {
            try {
                android.util.Log.d("AccelerometerService", "Attempting Firebase update for all mappings on vehicle $vehicleId, totalMovement=$totalMovement")
                
                // Get all mappings for this vehicle
                val mappingsResult = vehicleServiceMappingRepository.getMappingsForVehicle(vehicleId)
                
                when (mappingsResult) {
                    is com.mainlert.data.models.Result.Success -> {
                        val mappings = mappingsResult.data ?: emptyList()
                        
                        if (mappings.isEmpty()) {
                            android.util.Log.w("AccelerometerService", "No mappings found for vehicle $vehicleId")
                            return@launch
                        }
                        
                        var updatesSuccessful = 0
                        var updatesFailed = 0
                        
                        // Update all mappings for this vehicle
                        for (mapping in mappings) {
                            try {
                                val result = vehicleServiceMappingRepository.updateMappingMovement(mapping.id, totalMovement)
                                
                                when (result) {
                                    is com.mainlert.data.models.Result.Success -> {
                                        updatesSuccessful++
                                        android.util.Log.d("AccelerometerService", "Updated mapping ${mapping.id} for service ${mapping.serviceId}: $totalMovement")
                                    }
                                    is com.mainlert.data.models.Result.Failure -> {
                                        updatesFailed++
                                        android.util.Log.e("AccelerometerService", "Failed to update mapping ${mapping.id} for service ${mapping.serviceId}: ${result.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                updatesFailed++
                                android.util.Log.e("AccelerometerService", "Exception updating mapping ${mapping.id} for service ${mapping.serviceId}: ${e.message}")
                            }
                        }
                        
                        android.util.Log.i("AccelerometerService", "Batch update completed: $updatesSuccessful successful, $updatesFailed failed out of ${mappings.size} mappings")
                        
                        if (updatesFailed > 0) {
                            showFirebaseSyncErrorNotification("Failed to sync some readings to cloud: $updatesFailed/${mappings.size} updates failed")
                        }
                        
                    }
                    is com.mainlert.data.models.Result.Failure -> {
                        android.util.Log.e("AccelerometerService", "Failed to get mappings for vehicle $vehicleId: ${mappingsResult.message}")
                        showFirebaseSyncErrorNotification("Failed to sync readings: ${mappingsResult.message}")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AccelerometerService", "Exception during batch Firebase update for vehicle $vehicleId", e)
                showFirebaseSyncErrorNotification("Network error: ${e.message}")
            }
        }
    }

    // ========== Refactored Mapping Loading Functions ==========
    
    /**
     * Unified function to load an existing mapping or create a new one.
     * Uses distributed locking to prevent duplicate creation.
     */
    private suspend fun loadOrCreateMapping(): VehicleServiceMapping? {
        val vehicleId = currentVehicleId
        val serviceId = currentServiceId
        
        if (vehicleId.isNullOrBlank() || serviceId.isNullOrBlank()) {
            Log.w(TAG, "Cannot load/create mapping - vehicleId or serviceId is null/blank")
            return null
        }
        
        return try {
            // Try to load existing mapping first
            val existingMapping = loadExistingMapping(vehicleId, serviceId)
            if (existingMapping != null) {
                android.util.Log.i(TAG, "Found existing mapping: ${existingMapping.id}")
                return existingMapping
            }
            
            // No mapping found - acquire lock and create
            createMappingWithLock(vehicleId, serviceId)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load or create mapping", e)
            null
        }
    }
    
    /**
     * Loads existing mapping using the best available query method.
     * Returns null if no mapping found.
     */
    private suspend fun loadExistingMapping(vehicleId: String, serviceId: String): VehicleServiceMapping? {
        // If we have a currentMappingId from active mapping restore, use it directly
        if (currentMappingId != null) {
            android.util.Log.d(TAG, "Using currentMappingId from restore: $currentMappingId")
            val result = vehicleServiceMappingRepository.getMappingById(currentMappingId!!)
            if (result is com.mainlert.data.models.Result.Success) {
                return result.data
            }
            // If loading by ID fails, clear the ID and fall back to query
            currentMappingId = null
        }
        
        // Query by vehicle+service
        val result = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicleId, serviceId)
        return when (result) {
            is com.mainlert.data.models.Result.Success -> {
                val mapping = result.data
                if (mapping != null) {
                    currentMappingId = mapping.id
                    currentServiceMileageLimit = mapping.mileageLimit
                    // Do NOT restore totalMovement from mapping - start fresh each session
                }
                mapping
            }
            is com.mainlert.data.models.Result.Failure -> {
                android.util.Log.e(TAG, "Failed to load mapping: ${result.message}")
                null
            }
        }
    }
    
    /**
     * Creates a new mapping with distributed locking to prevent duplicates.
     * Uses Firestore transaction to ensure only one process creates the mapping.
     */
    private suspend fun createMappingWithLock(vehicleId: String, serviceId: String): VehicleServiceMapping? {
        val lockDocId = "mapping_lock_${vehicleId}_$serviceId"
        val mappingCreated = AtomicBoolean(false)
        var createdMapping: VehicleServiceMapping? = null
        var startTime = System.currentTimeMillis()
        
        try {
            // Try to acquire lock (max 5 second wait)
            startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 5000) {
                try {
                    val lockAcquired = lockRepository.acquireLock(lockDocId)
                    if (lockAcquired is com.mainlert.data.models.Result.Success && lockAcquired.data == true) {
                        // Lock acquired successfully
                        break
                    } else {
                        // Lock already held by another process, wait and retry
                        delay(500)
                    }
                } catch (e: Exception) {
                    // Wait and retry
                    delay(500)
                }
            }
            
            // Double-check if mapping was created while we were waiting for lock
            val doubleCheck = vehicleServiceMappingRepository.getMappingForVehicleAndService(vehicleId, serviceId)
            if (doubleCheck is com.mainlert.data.models.Result.Success && doubleCheck.data != null) {
                Log.i(TAG, "Mapping already created by another process: ${doubleCheck.data.id}")
                return doubleCheck.data
            }
            
            // Create the mapping
            android.util.Log.i(TAG, "Lock acquired, creating mapping for vehicle $vehicleId and service $serviceId")
            val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
            val vehicle = when (vehicleResult) {
                is com.mainlert.data.models.Result.Success -> vehicleResult.data
                is com.mainlert.data.models.Result.Failure -> {
                    android.util.Log.e(TAG, "Failed to get vehicle: ${vehicleResult.message}")
                    null
                }
            }
            
            if (vehicle == null) {
                android.util.Log.e(TAG, "Cannot create mapping - vehicle not found: $vehicleId")
                return null
            }
            
            val serviceResult = serviceRepository.getServiceById(serviceId)
            val service = when (serviceResult) {
                is com.mainlert.data.models.Result.Success -> serviceResult.data
                is com.mainlert.data.models.Result.Failure -> {
                    android.util.Log.e(TAG, "Failed to get service: ${serviceResult.message}")
                    null
                }
            }
            
            if (service == null) {
                android.util.Log.e(TAG, "Cannot create mapping - service not found: $serviceId")
                return null
            }
            
            createdMapping = createMappingForService(vehicle, service)
            mappingCreated.set(true)
            
            return createdMapping
            
        } finally {
            // Always release lock if we acquired it
            if (mappingCreated.get() || System.currentTimeMillis() - startTime >= 5000) {
                try {
                    lockRepository.releaseLock(lockDocId)
                    android.util.Log.d(TAG, "Released mapping lock: $lockDocId")
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to release lock: $lockDocId", e)
                }
            }
        }
    }
    
    /**
     * Starts monitoring with the loaded or newly created mapping.
     */
    private suspend fun startMonitoringWithMapping(mapping: VehicleServiceMapping) {
        currentMappingId = mapping.id
        currentServiceMileageLimit = mapping.mileageLimit
        // Do NOT restore totalMovement from mapping - start fresh each session
        
        // Save monitoring state to SharedPreferences for UI restoration
        saveMonitoringStateToPrefs()
        
        if (!mapping.isMonitoring) {
            android.util.Log.d(TAG, "Mapping found but not monitoring, starting monitoring for mapping ${mapping.id}")
            val startResult = vehicleServiceMappingRepository.startMappingMonitoring(mapping.id)
            when (startResult) {
                is com.mainlert.data.models.Result.Success -> {
                    android.util.Log.i(TAG, "Successfully started monitoring for existing mapping ${mapping.id}")
                }
                is com.mainlert.data.models.Result.Failure -> {
                    android.util.Log.e(TAG, "Failed to start monitoring for existing mapping ${mapping.id}: ${startResult.message}")
                }
            }
        }
        
        // Continue with vehicle mileage loading and monitoring start
        loadVehicleMileageAndStartMonitoring()
    }
    
    /**
     * Loads vehicle lifetime mileage and starts monitoring.
     */
    private suspend fun loadVehicleMileageAndStartMonitoring() {
        if (currentVehicleId != null) {
            try {
                val vehicleResult = vehicleRepository.getVehicleById(currentVehicleId!!)
                when (vehicleResult) {
                    is com.mainlert.data.models.Result.Success -> {
                        val vehicle = vehicleResult.data
                        if (vehicle != null) {
                            android.util.Log.d(TAG, "Successfully loaded vehicle lifetime mileage: ${vehicle.lifetimeMileage}")
                        } else {
                            android.util.Log.w(TAG, "Vehicle not found for ID: $currentVehicleId")
                        }
                    }
                    is com.mainlert.data.models.Result.Failure -> {
                        android.util.Log.e(TAG, "Failed to load vehicle: ${vehicleResult.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Exception while loading vehicle data", e)
            }
        }
        
        // All data loaded successfully - start monitoring
        android.util.Log.i(TAG, "Firebase data loaded successfully. Starting monitoring...")
        isFirebaseDataLoaded = true
        serviceState = ServiceState.MONITORING
        startMonitoring()
    }
    
    /**
     * Creates new VehicleServiceMappings when no active mapping is found and starts monitoring.
     * If serviceId is empty, fetches all services for the vehicle and creates mappings for all of them.
     * This ensures the service can always start monitoring by creating the necessary mappings.
     */
    private fun createNewMappingAndStartMonitoring() {
        android.util.Log.i("AccelerometerService", "Creating new mapping for service $currentServiceId and vehicle $currentVehicleId")
        
        serviceScope.launch {
            try {
                // Get vehicle info for userId
                val vehicleResult = vehicleRepository.getVehicleById(currentVehicleId!!)
                val vehicle = when (vehicleResult) {
                    is com.mainlert.data.models.Result.Success -> vehicleResult.data
                    is com.mainlert.data.models.Result.Failure -> {
                        android.util.Log.e("AccelerometerService", "Failed to get vehicle: ${vehicleResult.message}")
                        null
                    }
                }
                
                if (vehicle == null) {
                    android.util.Log.e("AccelerometerService", "Cannot create mapping - vehicle not found: $currentVehicleId")
                    return@launch
                }
                
                // Check if serviceId is empty - if so, fetch all services for the vehicle
                if (currentServiceId.isNullOrEmpty()) {
                    android.util.Log.i("AccelerometerService", "ServiceId is empty, fetching all services for vehicle $currentVehicleId")
                    createMappingsForAllServices(vehicle)
                } else {
                    // Single service mapping creation (existing logic)
                    createMappingForSingleService(vehicle)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AccelerometerService", "Exception while creating new mapping", e)
                
                // If permission denied, halt service startup
                if (e is com.mainlert.data.models.PermissionDeniedException) {
                    android.util.Log.e("AccelerometerService", "PERMISSION DENIED: Cannot start service due to Firebase permission issues")
                    showPermissionDeniedNotification(e.message ?: "Permission denied: Cannot create vehicle service mappings")
                    serviceState = ServiceState.ERROR_FIREBASE_FAILED
                    return@launch
                }
                
                // Fallback to starting monitoring with default values
                android.util.Log.w("AccelerometerService", "Exception during mapping creation, starting with default values")
                currentMappingId = null
                currentServiceMileageLimit = 1000f
                totalMovement = 0f
                startMonitoring()
            }
        }
    }
    
    /**
     * Creates mappings for all services for the vehicle when serviceId is empty.
     * Throws PermissionDeniedException if permission denied to prevent monitoring startup.
     */
    private suspend fun createMappingsForAllServices(vehicle: com.mainlert.data.models.Vehicle) {
        try {
            // Get all services
            val serviceResult = serviceRepository.getServices()
            val services = when (serviceResult) {
                is com.mainlert.data.models.Result.Success -> serviceResult.data ?: emptyList()
                is com.mainlert.data.models.Result.Failure -> {
                    android.util.Log.e("AccelerometerService", "Failed to get services: ${serviceResult.message}")
                    emptyList()
                }
            }
            
            if (services.isEmpty()) {
                android.util.Log.w("AccelerometerService", "No services found for vehicle $currentVehicleId, cannot start monitoring")
                throw com.mainlert.data.models.PermissionDeniedException("Cannot start monitoring - no services available")
            }
            
            android.util.Log.i("AccelerometerService", "Found ${services.size} services for vehicle $currentVehicleId, creating mappings for all")
            
            var mappingsCreated = 0
            var firstMappingId: String? = null
            var firstMileageLimit = 1000f
            
            // Create mappings for all services
            for (service in services) {
                try {
                    val mapping = createMappingForService(vehicle, service)
                    if (mapping != null) {
                        mappingsCreated++
                        if (firstMappingId == null) {
                            firstMappingId = mapping.id
                            firstMileageLimit = mapping.mileageLimit
                        }
                        android.util.Log.d("AccelerometerService", "Created mapping for service ${service.id}: ${service.name}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AccelerometerService", "Failed to create mapping for service ${service.id}: ${e.message}")
                    
                    // Re-throw permission denied exceptions to halt service startup
                    if (e is com.mainlert.data.models.PermissionDeniedException) {
                        throw e
                    }
                }
            }
            
            if (mappingsCreated > 0) {
                android.util.Log.i("AccelerometerService", "Successfully created $mappingsCreated mappings for vehicle $currentVehicleId")
                currentMappingId = firstMappingId
                currentServiceMileageLimit = firstMileageLimit
                totalMovement = 0f
                // startMonitoring() will be called from loadFirebaseData() after all data is loaded
            } else {
                android.util.Log.e("AccelerometerService", "PERMISSION DENIED: Failed to create any mappings for vehicle $currentVehicleId")
                throw com.mainlert.data.models.PermissionDeniedException("Permission denied: Cannot create vehicle service mappings")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AccelerometerService", "Exception while creating mappings for all services", e)
            
            // Re-throw permission denied exceptions
            if (e is com.mainlert.data.models.PermissionDeniedException) {
                throw e
            }
            
            // For other exceptions, still halt service startup to prevent inconsistent state
            throw com.mainlert.data.models.PermissionDeniedException("Failed to create mappings: ${e.message}")
        }
    }
    
    /**
     * Creates a mapping for a single service (existing logic).
     */
    private suspend fun createMappingForSingleService(vehicle: com.mainlert.data.models.Vehicle) {
        // Get service info and variant details
        val serviceResult = serviceRepository.getServiceById(currentServiceId!!)
        val service = when (serviceResult) {
            is com.mainlert.data.models.Result.Success -> serviceResult.data
            is com.mainlert.data.models.Result.Failure -> {
                android.util.Log.e("AccelerometerService", "Failed to get service: ${serviceResult.message}")
                null
            }
        }
        
        if (service == null) {
            android.util.Log.e("AccelerometerService", "Cannot create mapping - service not found: $currentServiceId")
            return
        }
        
        val mapping = createMappingForService(vehicle, service)
        if (mapping != null) {
            currentMappingId = mapping.id
            currentServiceMileageLimit = mapping.mileageLimit
            totalMovement = 0f  // Start fresh, don't persist totalMovement
            
            android.util.Log.i("AccelerometerService", "Successfully created new mapping: id=${mapping.id}, vehicleId=${mapping.vehicleId}, serviceId=${mapping.serviceId}")
            // startMonitoring() will be called from loadFirebaseData() after all data is loaded
        } else {
            android.util.Log.e("AccelerometerService", "Failed to create mapping for service $currentServiceId")
            currentMappingId = null
            currentServiceMileageLimit = 1000f
            totalMovement = 0f
            // startMonitoring() will be called from loadFirebaseData() after all data is loaded
        }
    }
    
    /**
     * Creates a single VehicleServiceMapping for a service-vehicle pair.
     * Uses the service's stored mileage limit directly, eliminating runtime variant lookups.
     */
    private suspend fun createMappingForService(
        vehicle: com.mainlert.data.models.Vehicle,
        service: com.mainlert.data.models.Service
    ): com.mainlert.data.models.VehicleServiceMapping? {
        try {
            // Use service's stored values directly - no runtime variant lookups
            val variantName = service.variantName
            val variantId = service.variantId
            val mileageLimit = service.mileageLimit
            
            // Log the values being used
            android.util.Log.d("AccelerometerService", "Creating mapping for service ${service.id}: variantId=$variantId, variantName=$variantName, mileageLimit=$mileageLimit")
            
            // Create new VehicleServiceMapping using service's stored values
            val newMapping = com.mainlert.data.models.VehicleServiceMapping(
                vehicleId = vehicle.id,
                serviceId = service.id,
                userId = vehicle.userId,
                serviceName = variantName.ifEmpty { service.name },
                variantId = variantId,
                variantName = variantName,
                totalMovement = 0f,
                isMonitoring = true,
                status = com.mainlert.data.models.VehicleServiceMapping.MappingStatus.ACTIVE,
                lastReadingTime = System.currentTimeMillis(),
                mileageLimit = mileageLimit,
            )
            
            // Create the mapping in Firebase
            val createResult = vehicleServiceMappingRepository.createMapping(newMapping)
            when (createResult) {
                is com.mainlert.data.models.Result.Success -> {
                    val createdMapping = createResult.data
                    if (createdMapping != null) {
                        android.util.Log.i("AccelerometerService", "Successfully created mapping for service ${service.id}: id=${createdMapping.id}, variantId=$variantId, variantName=$variantName, mileageLimit=$mileageLimit")
                        return createdMapping
                    } else {
                        android.util.Log.e("AccelerometerService", "Failed to create mapping for service ${service.id} - no data returned")
                        return null
                    }
                }
                is com.mainlert.data.models.Result.Failure -> {
                    android.util.Log.e("AccelerometerService", "Failed to create mapping for service ${service.id}: ${createResult.message}")
                    
                    // Check if this is a permission denied error
                    if (createResult.message?.contains("Permission denied", ignoreCase = true) == true) {
                        android.util.Log.e("AccelerometerService", "PERMISSION DENIED: Cannot create VehicleServiceMapping. Halting service startup.")
                        throw com.mainlert.data.models.PermissionDeniedException("Permission denied: Cannot create vehicle service mapping")
                    }
                    return null
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AccelerometerService", "Exception while creating mapping for service ${service.id}", e)
            
            // Re-throw permission denied exceptions to halt service startup
            if (e is com.mainlert.data.models.PermissionDeniedException) {
                throw e
            }
            return null
        }
    }

    /**
     * Shows a notification when Firebase sync fails.
     */
    private fun showFirebaseSyncErrorNotification(errorMessage: String) {
        val notification =
            NotificationCompat.Builder(this, notificationChannelId)
                .setContentTitle("Sync Error")
                .setContentText(errorMessage)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 250, 500))
                .setLights(android.graphics.Color.RED, 3000, 3000)
                .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId + 1, notification)
    }

    /**
     * Shows a notification when Firebase permission denied errors occur.
     */
    private fun showPermissionDeniedNotification(errorMessage: String) {
        val notification =
            NotificationCompat.Builder(this, notificationChannelId)
                .setContentTitle("Permission Denied")
                .setContentText(errorMessage)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
                .setLights(android.graphics.Color.RED, 3000, 3000)
                .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId + 2, notification)
    }

    /**
     * Launches the app from boot detection mode when vehicle movement is detected.
     * This is called when the service is in detection mode and vehicle movement is confirmed.
     */
    private fun launchAppFromDetection() {
        try {
            // Create a notification to prompt user to select a vehicle
            val notificationIntent = Intent(this, com.mainlert.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(BootReceiver.EXTRA_SHOW_VEHICLE_SELECTION, true)
                putExtra(BootReceiver.EXTRA_FROM_BOOT_DETECTION, true)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(this, mileageNotificationChannelId)
                .setContentTitle("Vehicle Detected")
                .setContentText("Your device appears to be in a moving vehicle. Tap to select a vehicle and start monitoring.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = 1001 // Unique ID for boot detection notification
            notificationManager.notify(notificationId, notification)
            
            android.util.Log.i("AccelerometerService", "Showed vehicle detection notification")
        } catch (e: Exception) {
            android.util.Log.e("AccelerometerService", "Failed to show detection notification", e)
        }
    }
    
    }
