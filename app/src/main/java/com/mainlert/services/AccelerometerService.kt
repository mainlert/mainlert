package com.mainlert.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.auth.FirebaseAuth
import com.mainlert.data.models.ServiceReading
import com.mainlert.data.repositories.ServiceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.sqrt

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
    @Inject
    lateinit var serviceRepository: ServiceRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var remoteConfigRepository: com.mainlert.data.repositories.RemoteConfigRepository

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

    // Movement detection thresholds (loaded from RemoteConfig at startup)
    private var crashThreshold: Float = 3.0f
    private var minThreshold: Float = 0.5f

    // Service constants
    private val notificationChannelId = "accelerometer_channel"
    private val mileageNotificationChannelId = "mileage_notifications_channel"
    private val notificationId = 1
    private val mileageNotificationId = 2

    // Duplicate notification prevention
    private var lastMileageNotificationTime = 0L
    private val notificationCooldown = TimeUnit.MINUTES.toMillis(30) // 30 minutes cooldown

    // Movement tracking variables
    private var isVehicleMovement = false
    private var movementBuffer = mutableListOf<Float>()
    private var bufferMaxSize = 100
    private var currentServiceId: String? = null
    private var currentVehicleId: String? = null
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

    override fun onCreate() {
        super.onCreate()
        // Initialize sensors and notification channel for foreground service
        initSensors()
        createNotificationChannel()
        checkBatteryOptimization()
    }

    /**
     * Checks if battery optimization is enabled and notifies user if so.
     */
    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val packageName = packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // TODO: Show notification or dialog to user to disable battery optimization for best results
            }
        }
    }

    /**
     * Fallback for long-running tasks using WorkManager (stub for future implementation).
     */
    private fun scheduleWorkManagerFallback() {
        // TODO: Implement WorkManager fallback for background monitoring if service is killed
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        android.util.Log.i("AccelerometerService", ">>> onStartCommand called with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                currentServiceId = intent.getStringExtra(EXTRA_SERVICE_ID)
                currentVehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID)
                android.util.Log.i("AccelerometerService", "START_MONITORING received, serviceId: $currentServiceId, vehicleId: $currentVehicleId")

                // Start monitoring IMMEDIATELY - don't wait for Firebase
                startMonitoring()

                // Load existing service data asynchronously (updates totalMovement while monitoring runs)
                serviceScope.launch {
                    currentServiceId?.let { serviceId ->
                        val serviceResult = serviceRepository.getServiceById(serviceId)
                        if (serviceResult is com.mainlert.data.models.Result.Success) {
                            val service = serviceResult.data
                            currentServiceMileageLimit = service?.mileageLimit ?: 1000f
                            // Load existing totalMovement so we continue from where we left off
                            // This ensures readings don't wait for local to catch up to Firebase value
                            totalMovement = service?.totalMovement ?: 0f
                            android.util.Log.d("AccelerometerService", "Mileage limit set to: $currentServiceMileageLimit, totalMovement loaded: $totalMovement")
                        }
                    }
                }
            }
            ACTION_STOP_MONITORING -> stopMonitoring()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
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
        if (isMonitoring) {
            android.util.Log.w("AccelerometerService", "Already monitoring, returning early")
            return
        }

        // Load thresholds from RemoteConfig
        crashThreshold = remoteConfigRepository.getCrashThreshold()
        minThreshold = remoteConfigRepository.getMinThreshold()
        android.util.Log.d("AccelerometerService", "Loaded thresholds from RemoteConfig: crashThreshold=$crashThreshold, minThreshold=$minThreshold")

        isMonitoring = true
        isServiceActive = true
        readingStartTime = System.currentTimeMillis()
        // Don't reset totalMovement to 0 - it will be loaded from Firebase asynchronously
        // This ensures readings continue from where they left off when accelerometer restarts
        movementBuffer.clear()
        lastBroadcastTime = 0L

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
    }

    private fun stopMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false
        isServiceActive = false
        sensorManager.unregisterListener(this)
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Cancel all coroutines when stopping
        serviceScope.cancel("Monitoring stopped")

        // Save final service reading to Firebase
        if (currentServiceId != null && totalMovement > 0) {
            val userId = firebaseAuth.currentUser?.uid ?: ""
            val reading =
                ServiceReading(
                    id = "",
                    serviceId = currentServiceId!!,
                    userId = userId,
                    totalMovement = totalMovement,
                    duration = System.currentTimeMillis() - readingStartTime,
                    isVehicleMovement = isVehicleMovement,
                    isCompleted = true,
                    timestamp = System.currentTimeMillis(),
                )

            serviceScope.launch {
                serviceRepository.addServiceReading(reading)
                android.util.Log.d("AccelerometerService", "Final reading saved to Firebase: totalMovement=$totalMovement")
            }
        }

        android.util.Log.d("AccelerometerService", "Monitoring stopped")
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
                    // Skip if not monitoring to prevent crashes
                    if (!isMonitoring) return@let

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
                    val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

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

            // Determine if this is vehicle or human movement
            val avgMovement = movementBuffer.average().toFloat()
            isVehicleMovement = avgMovement > crashThreshold

            // Check for mileage limit condition
            checkForMileageLimit()
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
        }
        val sent = LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        android.util.Log.d("AccelerometerService", "Broadcast sent: $sent, totalMovement=$totalMovement, magnitude=$magnitude, vehicleId=$currentVehicleId")
    }

    /**
     * Checks if mileage limit reached and handles it.
     */
    private fun checkForMileageLimit() {
        // Check if total movement has reached the mileage limit while in vehicle movement
        if (isVehicleMovement && totalMovement >= currentServiceMileageLimit) {
            android.util.Log.i("AccelerometerService", "MILEAGE LIMIT REACHED! totalMovement=$totalMovement, limit=$currentServiceMileageLimit")

            // Send final reading to Firebase
            currentServiceId?.let { serviceId ->
                val userId = firebaseAuth.currentUser?.uid ?: ""
                val reading =
                    ServiceReading(
                        id = "",
                        serviceId = serviceId,
                        userId = userId,
                        totalMovement = totalMovement,
                        duration = System.currentTimeMillis() - readingStartTime,
                        isVehicleMovement = isVehicleMovement,
                        isCompleted = true,
                        timestamp = System.currentTimeMillis(),
                    )

                serviceScope.launch {
                    serviceRepository.addServiceReading(reading)
                    android.util.Log.d("AccelerometerService", "Mileage limit reading saved to Firebase")
                }
            }

            // Show notification
            showMileageNotification()

            // Stop monitoring
            stopMonitoring()
        }
    }

    companion object {
        const val ACTION_START_MONITORING = "com.mainlert.mainlertapp.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.mainlert.mainlertapp.STOP_MONITORING"
        const val ACTION_BROADCAST_ACCELEROMETER = "com.mainlert.mainlertapp.BROADCAST_ACCELEROMETER"
        const val EXTRA_SERVICE_ID = "service_id"
        const val EXTRA_VEHICLE_ID = "vehicle_id"
        const val EXTRA_X = "extra_x"
        const val EXTRA_Y = "extra_y"
        const val EXTRA_Z = "extra_z"
        const val EXTRA_MAGNITUDE = "extra_magnitude"
        const val EXTRA_TOTAL_MOVEMENT = "extra_total_movement"
        const val EXTRA_IS_VEHICLE_MOVEMENT = "extra_is_vehicle_movement"
        const val EXTRA_IS_MONITORING = "extra_is_monitoring"

        fun startService(
            context: Context,
            serviceId: String,
            vehicleId: String,
        ) {
            val intent =
                Intent(context, AccelerometerService::class.java).apply {
                    action = ACTION_START_MONITORING
                    putExtra(EXTRA_SERVICE_ID, serviceId)
                    putExtra(EXTRA_VEHICLE_ID, vehicleId)
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
}
