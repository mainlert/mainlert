package com.mainlert.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mainlert.ui.viewmodels.AuthViewModel
import com.mainlert.ui.viewmodels.DashboardViewModel

/**
 * Service Details screen for MainLert app.
 * Displays detailed service information and controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun serviceDetailsScreen(
    navController: NavController,
    serviceId: String = "",
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
) {
    var isMonitoring by remember { mutableStateOf(false) }
    var currentReadings by remember { mutableStateOf(0) }
    var mileageThreshold by remember { mutableStateOf(20000) }

    // Observe authentication state
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val successMessage by authViewModel.successMessage.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val currentUserRole by authViewModel.currentUserRole.collectAsState()

    // Observe dashboard state
    val serviceReadings by dashboardViewModel.serviceReadings.collectAsState()
    val isServiceActive by dashboardViewModel.isServiceActive.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Service Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
            // Error message
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp),
                )
            }
            // Success message
            if (successMessage.isNotEmpty()) {
                Text(
                    text = successMessage,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp),
                )
            }

            // Service Info Card
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Service Information",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Monitoring Status", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (isServiceActive) "Active" else "Inactive",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isServiceActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Current Readings", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "$serviceReadings",
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (serviceReadings >= mileageThreshold) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Mileage Threshold", style = MaterialTheme.typography.bodyMedium)
                        Text("$mileageThreshold", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(
                    onClick = {
                        if (isServiceActive) {
                            dashboardViewModel.stopMonitoringService()
                        } else {
                            dashboardViewModel.startMonitoringService()
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isServiceActive) "Stop Monitoring" else "Start Monitoring")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        dashboardViewModel.resetServiceData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Reset Service")
                }
            }
        }
    }
}
