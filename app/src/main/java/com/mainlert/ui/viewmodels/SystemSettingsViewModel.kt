package com.mainlert.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mainlert.data.models.Result
import com.mainlert.data.repositories.RemoteConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for System Settings screen.
 * Manages app-wide settings including sensor thresholds.
 */
@HiltViewModel
class SystemSettingsViewModel
    @Inject
    constructor(
        private val remoteConfigRepository: RemoteConfigRepository,
    ) : ViewModel() {

    // Sensor thresholds state
    private val _minThreshold = MutableStateFlow(DEFAULT_MIN_THRESHOLD)
    val minThreshold: StateFlow<Float> = _minThreshold.asStateFlow()

    private val _crashThreshold = MutableStateFlow(DEFAULT_CRASH_THRESHOLD)
    val crashThreshold: StateFlow<Float> = _crashThreshold.asStateFlow()

    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _successMessage = MutableStateFlow("")
    val successMessage: StateFlow<String> = _successMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    init {
        loadCurrentSettings()
    }

    /**
     * Loads current settings from RemoteConfig.
     */
    private fun loadCurrentSettings() {
        _minThreshold.value = remoteConfigRepository.getMinThreshold()
        _crashThreshold.value = remoteConfigRepository.getCrashThreshold()
    }

    /**
     * Updates the minimum movement threshold.
     * Values below this will be ignored as movement.
     */
    fun updateMinThreshold(value: Float) {
        _minThreshold.value = value.coerceIn(MIN_THRESHOLD_RANGE.start, MIN_THRESHOLD_RANGE.endInclusive)
    }

    /**
     * Updates the crash/vehicle detection threshold.
     * Average movement above this is considered vehicle movement.
     */
    fun updateCrashThreshold(value: Float) {
        _crashThreshold.value = value.coerceIn(CRASH_THRESHOLD_RANGE.start, CRASH_THRESHOLD_RANGE.endInclusive)
    }

    /**
     * Saves current threshold settings to Firebase Remote Config.
     * Note: In production, this would require admin privileges and
     * Firebase Remote Config's server-side update mechanism.
     */
    fun saveSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            _successMessage.value = ""

            try {
                // Fetch and activate to get latest values
                val success = remoteConfigRepository.fetchAndActivate()
                if (success) {
                    // Reload after fetch
                    loadCurrentSettings()
                    _successMessage.value = "Settings refreshed successfully"
                } else {
                    _errorMessage.value = "Failed to refresh settings from server"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            }

            _isLoading.value = false
        }
    }

    /**
     * Resets thresholds to default values.
     */
    fun resetToDefaults() {
        _minThreshold.value = DEFAULT_MIN_THRESHOLD
        _crashThreshold.value = DEFAULT_CRASH_THRESHOLD
        _successMessage.value = "Reset to defaults: min=${DEFAULT_MIN_THRESHOLD}g, crash=${DEFAULT_CRASH_THRESHOLD}g"
    }

    /**
     * Clears success/error messages.
     */
    fun clearMessages() {
        _successMessage.value = ""
        _errorMessage.value = ""
    }

    companion object {
        // Default values (in gravity units G)
        const val DEFAULT_MIN_THRESHOLD = 0.5f
        const val DEFAULT_CRASH_THRESHOLD = 3.0f

        // Allowed ranges for thresholds
        val MIN_THRESHOLD_RANGE = 0.1f..2.0f
        val CRASH_THRESHOLD_RANGE = 1.0f..10.0f
    }
}
