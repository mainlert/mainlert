package com.mainlert.data.repositories

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing Firebase Remote Config values.
 * Allows adjusting app behavior without app updates.
 */
@Singleton
class RemoteConfigRepository @Inject constructor() {

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    // Default values for accelerometer thresholds (gravity units)
    private val defaultCrashThreshold = 3.0f
    private val defaultMinThreshold = 0.5f
    private val defaultSamplingInterval = 100L

    init {
        val configSettings: FirebaseRemoteConfigSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // 1 hour minimum fetch interval
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
    }

    /**
     * Fetches and activates remote config values.
     */
    suspend fun fetchAndActivate(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the crash detection threshold for accelerometer.
     * Value represents gravity units (G) - typical crash is > 2-3G
     */
    fun getCrashThreshold(): Float {
        return remoteConfig.getDouble("crash_threshold").toFloat().takeIf { it > 0 }
            ?: defaultCrashThreshold
    }

    /**
     * Gets the minimum threshold to ignore minor vibrations.
     * Filters out small movements like walking with phone.
     */
    fun getMinThreshold(): Float {
        return remoteConfig.getDouble("min_threshold").toFloat().takeIf { it > 0 }
            ?: defaultMinThreshold
    }

    /**
     * Gets the sampling interval in milliseconds.
     * Lower = more accurate but higher battery consumption.
     */
    fun getSamplingInterval(): Long {
        return remoteConfig.getLong("sampling_interval").takeIf { it > 0 }
            ?: defaultSamplingInterval
    }

    /**
     * Checks if analytics is enabled remotely.
     */
    fun isAnalyticsEnabled(): Boolean {
        return remoteConfig.getBoolean("analytics_enabled")
    }

    /**
     * Gets the current app version requirement.
     * If installed version is below this, show update prompt.
     */
    fun getMinAppVersion(): Int {
        return remoteConfig.getLong("min_app_version").toInt()
    }

    companion object {
        // Remote config parameter keys
        const val KEY_CRASH_THRESHOLD = "crash_threshold"
        const val KEY_MIN_THRESHOLD = "min_threshold"
        const val KEY_SAMPLING_INTERVAL = "sampling_interval"
        const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
        const val KEY_MIN_APP_VERSION = "min_app_version"
    }
}
