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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mainlert.data.models.Service
import com.mainlert.data.models.ServiceVariant
import com.mainlert.ui.viewmodels.AuthViewModel
import com.mainlert.ui.viewmodels.DashboardViewModel

/**
 * Service Management screen for MainLert app.
 * Allows admins to manage services with variant selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun serviceManagementScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
) {
    var isCreatingService by remember { mutableStateOf(false) }
    var editingService by remember { mutableStateOf<Service?>(null) }

    // Service form fields
    var serviceName by remember { mutableStateOf("") }
    var serviceDescription by remember { mutableStateOf("") }
    var selectedVariant by remember { mutableStateOf<ServiceVariant?>(null) }

    // Observe authentication state
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val successMessage by authViewModel.successMessage.collectAsState()

    // Observe dashboard state
    val services by dashboardViewModel.services.collectAsState()
    val serviceVariants by dashboardViewModel.serviceVariants.collectAsState()

    // Load data
    LaunchedEffect(Unit) {
        dashboardViewModel.loadServices()
        dashboardViewModel.loadServiceVariants()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Service Management") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (authViewModel.isAdmin()) {
                        IconButton(onClick = { isCreatingService = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Service",
                            )
                        }
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

            if (authViewModel.isAdmin()) {
                if (isCreatingService || editingService != null) {
                    // Service form
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
                                text = if (editingService != null) "Edit Service" else "Create Service",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )

                            // Service Variant Selection Dropdown
                            ServiceVariantDropdown(
                                variants = serviceVariants,
                                selectedVariant = selectedVariant,
                                onVariantSelected = { selectedVariant = it },
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = serviceName,
                                onValueChange = { serviceName = it },
                                label = { Text("Service Name") },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                            )

                            OutlinedTextField(
                                value = serviceDescription,
                                onValueChange = { serviceDescription = it },
                                label = { Text("Service Description") },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                maxLines = 3,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Button(
                                    onClick = {
                                        val deadlockLimit = selectedVariant?.deadlockLimit ?: 1000f
                                        if (editingService != null) {
                                            dashboardViewModel.updateService(
                                                editingService!!.id,
                                                serviceName,
                                                serviceDescription,
                                                deadlockLimit,
                                                "",
                                                selectedVariant?.id ?: "",
                                                selectedVariant?.name ?: "Standard",
                                            )
                                        } else {
                                            dashboardViewModel.createService(
                                                serviceName,
                                                serviceDescription,
                                                deadlockLimit,
                                                "",
                                                selectedVariant?.id ?: "",
                                                selectedVariant?.name ?: "Standard",
                                            )
                                        }
                                        isCreatingService = false
                                        editingService = null
                                        serviceName = ""
                                        serviceDescription = ""
                                        selectedVariant = null
                                    },
                                    enabled = serviceName.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (editingService != null) "Update Service" else "Create Service")
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        isCreatingService = false
                                        editingService = null
                                        serviceName = ""
                                        serviceDescription = ""
                                        selectedVariant = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }

                // Services list
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = "Services List (${services.size})",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )

                        if (services.isEmpty()) {
                            Text(
                                text = "No services found. Create your first service.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        } else {
                            LazyColumn {
                                items(services) { service ->
                                    SimpleServiceItem(
                                        service = service,
                                        variantName = service.variantName.ifEmpty { "Standard" },
                                        onEdit = {
                                            editingService = service
                                            serviceName = service.name
                                            serviceDescription = service.description
                                            selectedVariant = serviceVariants.find { it.id == service.variantId }
                                        },
                                        onDelete = {
                                            dashboardViewModel.deleteService(service.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // For non-admin users, show limited view
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = "Service Management",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        Text(
                            text = "Only administrators can manage services.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceVariantDropdown(
    variants: List<ServiceVariant>,
    selectedVariant: ServiceVariant?,
    onVariantSelected: (ServiceVariant?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedVariant?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Select Service Variant") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            variants.forEach { variant ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(variant.name)
                            Text(
                                text = "Limit: ${variant.deadlockLimit.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    },
                    onClick = {
                        onVariantSelected(variant)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun SimpleServiceItem(
    service: Service,
    variantName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = service.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = service.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Variant:",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = variantName,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Deadlock Limit:",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = "${service.deadlockLimit.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Status:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = service.status.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        when (service.status) {
                            Service.ServiceStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                            Service.ServiceStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                            Service.ServiceStatus.CANCELLED -> MaterialTheme.colorScheme.error
                        },
                )
            }
        }
    }
}
