package com.mainlert.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mainlert.ui.viewmodels.SystemSettingsViewModel
import kotlin.math.roundToInt

/**
 * System Settings screen for MainLert app.
 * Allows admins to configure system settings including sensor thresholds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    viewModel: SystemSettingsViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect state from ViewModel
    val minThreshold by viewModel.minThreshold.collectAsState()
    val crashThreshold by viewModel.crashThreshold.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Show snackbar messages
    LaunchedEffect(successMessage, errorMessage) {
        if (successMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(successMessage)
            viewModel.clearMessages()
        }
        if (errorMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("System Settings") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sensor Settings Section
            SettingsSection(title = "Sensor Settings") {
                // Minimum Threshold Slider
                ThresholdSlider(
                    label = "Minimum Movement Threshold",
                    value = minThreshold,
                    valueRange = SystemSettingsViewModel.MIN_THRESHOLD_RANGE.start..SystemSettingsViewModel.MIN_THRESHOLD_RANGE.endInclusive,
                    description = "Ignore movements below this value. Higher = less sensitive to vibration.",
                    unit = "g",
                    onValueChange = { viewModel.updateMinThreshold(it) },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Crash/Vehicle Threshold Slider
                ThresholdSlider(
                    label = "Vehicle Detection Threshold",
                    value = crashThreshold,
                    valueRange = SystemSettingsViewModel.CRASH_THRESHOLD_RANGE.start..SystemSettingsViewModel.CRASH_THRESHOLD_RANGE.endInclusive,
                    description = "Average movement above this is considered vehicle movement.",
                    unit = "g",
                    onValueChange = { viewModel.updateCrashThreshold(it) },
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.resetToDefaults() },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                ) {
                    Text("Reset Defaults")
                }

                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                ) {
                    Text("Refresh from Server")
                }
            }

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "About Thresholds",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Minimum Threshold: Filters out small vibrations (walking, phone moving in pocket)\n" +
                            "• Vehicle Threshold: Distinguishes between human movement and vehicle movement\n" +
                            "• Changes take effect when monitoring is next started",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Current Values Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryItem(
                    label = "Min Threshold",
                    value = "%.1fg".format(minThreshold),
                )
                SummaryItem(
                    label = "Vehicle Threshold",
                    value = "%.1fg".format(crashThreshold),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    description: String,
    unit: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "%.2f%s".format(value, unit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = ((valueRange.endInclusive - valueRange.start) / 0.1f).roundToInt() - 1,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
