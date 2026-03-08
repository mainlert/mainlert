package com.mainlert.services

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches RemoteConfig thresholds locally using SharedPreferences.
 * Allows the accelerometer service to start monitoring without internet connection
 * by using the last-known-good threshold values.
 */
@Singleton
class ThresholdCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "mainlert_thresholds"
        private const val KEY_CRASH_THRESHOLD = "crash_threshold"
        private const val KEY_MIN_THRESHOLD = "min_threshold"
        private const val KEY_UPDATE_INTERVAL = "update_interval"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"

        // Default values matching RemoteConfig defaults
        private const val DEFAULT_CRASH_THRESHOLD = 3.0f
        private const val DEFAULT_MIN_THRESHOLD = 0.5f
        private const val DEFAULT_UPDATE_INTERVAL = 1000L
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Saves threshold values to local cache.
     */
    fun saveThresholds(
        crashThreshold: Float,
        minThreshold: Float,
        updateInterval: Long
    ) {
        sharedPreferences.edit()
            .putFloat(KEY_CRASH_THRESHOLD, crashThreshold)
            .putFloat(KEY_MIN_THRESHOLD, minThreshold)
            .putLong(KEY_UPDATE_INTERVAL, updateInterval)
            .putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
            .apply()
    }

    /**
     * Retrieves cached threshold values.
     * Returns default values if cache is empty (first run).
     */
    fun getCrashThreshold(): Float =
        sharedPreferences.getFloat(KEY_CRASH_THRESHOLD, DEFAULT_CRASH_THRESHOLD)

    fun getMinThreshold(): Float =
        sharedPreferences.getFloat(KEY_MIN_THRESHOLD, DEFAULT_MIN_THRESHOLD)

    fun getUpdateInterval(): Long =
        sharedPreferences.getLong(KEY_UPDATE_INTERVAL, DEFAULT_UPDATE_INTERVAL)

    /**
     * Checks if valid thresholds exist in cache.
     */
    fun hasCachedThresholds(): Boolean {
        return sharedPreferences.contains(KEY_CRASH_THRESHOLD) &&
                sharedPreferences.contains(KEY_MIN_THRESHOLD)
    }

    /**
     * Clears all cached thresholds (for testing/debugging).
     */
    fun clearCache() {
        sharedPreferences.edit().clear().apply()
    }

    /**
     * Gets the timestamp of the last cache update.
     */
    fun getLastUpdateTime(): Long =
        sharedPreferences.getLong(KEY_LAST_UPDATE_TIME, 0L)
}
