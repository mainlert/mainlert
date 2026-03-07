@file:OptIn(ExperimentalMaterial3Api::class)

package com.mainlert.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.util.Log
import com.mainlert.data.models.Service
import com.mainlert.data.models.ServiceVariant
import com.mainlert.data.models.User
import com.mainlert.data.models.Vehicle
import com.mainlert.ui.viewmodels.AuthViewModel
import com.mainlert.ui.viewmodels.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun userManagementScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
) {
    Log.d("UserManagementScreen", ">>> userManagementScreen ENTER")
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Drivers", "Vehicles", "Service Variants")

    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val successMessage by authViewModel.successMessage.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val allDrivers by authViewModel.allDrivers.collectAsState()
    val managedDrivers by authViewModel.managedDrivers.collectAsState()
    val allEmployees by authViewModel.allEmployees.collectAsState()
    val vehicles by dashboardViewModel.vehicles.collectAsState()
    val serviceVariants by dashboardViewModel.serviceVariants.collectAsState()

    Log.d("UserManagementScreen", "currentUser = $currentUser")
    Log.d("UserManagementScreen", "allDrivers = $allDrivers")
    Log.d("UserManagementScreen", "managedDrivers = $managedDrivers")

    // Fetch users when currentUser is loaded
    LaunchedEffect(currentUser) {
        Log.d("UserManagementScreen", ">>> LaunchedEffect triggered, currentUser = $currentUser")
        currentUser?.let { user ->
            Log.d("UserManagementScreen", "User role = ${user.role}")
            if (user.role == User.UserRole.ADMIN) {
                Log.d("UserManagementScreen", "Admin user - loading all drivers and employees")
                authViewModel.getAllDrivers()
                authViewModel.getAllEmployees()
                dashboardViewModel.loadAllVehicles()
            } else if (user.role == User.UserRole.EMPLOYEE) {
                Log.d("UserManagementScreen", "Employee user - loading managed drivers")
                authViewModel.getManagedDrivers()
                dashboardViewModel.loadVehiclesForUser(user.userId)
            }
            dashboardViewModel.loadServiceVariants()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("User Management") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.LocalShipping
                                    1 -> Icons.Default.DirectionsCar
                                    else -> Icons.Default.Settings
                                },
                                contentDescription = title
                            )
                        },
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> DriversTab(
                    isAdmin = currentUser?.role == User.UserRole.ADMIN,
                    allDrivers = allDrivers,
                    managedDrivers = managedDrivers,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    successMessage = successMessage,
                    authViewModel = authViewModel,
                    dashboardViewModel = dashboardViewModel,
                )
                1 -> VehiclesTab(
                    currentUser = currentUser,
                    vehicles = vehicles,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    successMessage = successMessage,
                    dashboardViewModel = dashboardViewModel,
                )
                2 -> ServiceVariantsTab(
                    serviceVariants = serviceVariants,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    successMessage = successMessage,
                    dashboardViewModel = dashboardViewModel,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriversTab(
    isAdmin: Boolean,
    allDrivers: List<User>,
    managedDrivers: List<User>,
    isLoading: Boolean,
    errorMessage: String,
    successMessage: String,
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
) {
    val allEmployees by authViewModel.allEmployees.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    
    var showCreateForm by remember { mutableStateOf(false) }
    var newUserEmail by remember { mutableStateOf("") }
    var newUserPassword by remember { mutableStateOf("") }
    var newUserName by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(User.UserRole.DRIVER) }
    var roleDropdownExpanded by remember { mutableStateOf(false) }
    
    // Vehicle fields for driver creation
    var vehicleName by remember { mutableStateOf("") }
    var vehiclePlateNumber by remember { mutableStateOf("") }
    
    // Assign Employee dialog state
    var showAssignDialog by remember { mutableStateOf(false) }
    var driverToAssign by remember { mutableStateOf<User?>(null) }
    
    // Manage Vehicles dialog state
    var showManageVehiclesDialog by remember { mutableStateOf(false) }
    var driverToManageVehicles by remember { mutableStateOf<User?>(null) }

    val availableRoles = if (isAdmin) User.UserRole.values().toList() else listOf(User.UserRole.DRIVER)
    val isDriverRole = selectedRole == User.UserRole.DRIVER
    val canCreateUser = newUserEmail.isNotBlank() && newUserPassword.isNotBlank() && 
        (!isDriverRole || (vehicleName.isNotBlank() && vehiclePlateNumber.isNotBlank()))

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        if (errorMessage.isNotEmpty()) Text(text = errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        if (successMessage.isNotEmpty()) Text(text = successMessage, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))

        Button(onClick = { showCreateForm = !showCreateForm }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (showCreateForm) "Cancel" else "Add User")
        }

        if (showCreateForm) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Create New User", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))

                    OutlinedTextField(value = newUserName, onValueChange = { newUserName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newUserEmail, onValueChange = { newUserEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newUserPassword, onValueChange = { newUserPassword = it }, label = { Text("Temporary Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isAdmin) {
                        ExposedDropdownMenuBox(expanded = roleDropdownExpanded, onExpandedChange = { roleDropdownExpanded = it }) {
                            OutlinedTextField(value = selectedRole.name, onValueChange = {}, readOnly = true, label = { Text("Role") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleDropdownExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                            ExposedDropdownMenu(expanded = roleDropdownExpanded, onDismissRequest = { roleDropdownExpanded = false }) {
                                availableRoles.forEach { role ->
                                    DropdownMenuItem(text = { Text(role.name) }, onClick = { selectedRole = role; roleDropdownExpanded = false })
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Vehicle section - REQUIRED for DRIVER role
                    if (isDriverRole) {
                        Text("Assign Vehicle (Required)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedTextField(value = vehicleName, onValueChange = { vehicleName = it }, label = { Text("Vehicle Name") }, placeholder = { Text("e.g., Toyota Camry") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = vehiclePlateNumber, onValueChange = { vehiclePlateNumber = it }, label = { Text("Plate Number") }, placeholder = { Text("e.g., ABC-123") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            // Create user first - we'll get the ID from success callback
                            // For now, create driver first without vehicle, then add vehicle
                            authViewModel.createUser(email = newUserEmail, password = newUserPassword, role = selectedRole, name = newUserName)
                            
                            // Reset form
                            showCreateForm = false
                            newUserEmail = ""
                            newUserPassword = ""
                            newUserName = ""
                            selectedRole = User.UserRole.DRIVER
                            vehicleName = ""
                            vehiclePlateNumber = ""
                        },
                        enabled = canCreateUser,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create User")
                    }
                }
            }
        }

        Text(if (isAdmin) "All Users" else "Managed Drivers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))

        val drivers = if (isAdmin) allDrivers else managedDrivers
        if (drivers.isEmpty()) {
            Text("No users found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        } else {
            LazyColumn {
                items(drivers) { driver ->
                    DriverCard(
                        driver = driver,
                        isAdmin = isAdmin,
                        onDelete = { if (isAdmin) authViewModel.deleteUser(driver.userId) },
                        onAssignEmployee = { if (isAdmin) { driverToAssign = driver; showAssignDialog = true } },
                        onManageVehicles = { if (isAdmin) { driverToManageVehicles = driver; showManageVehiclesDialog = true } }
                    )
                }
            }
        }
    }

    // Assign Employee Dialog
    if (showAssignDialog && driverToAssign != null) {
        AssignEmployeeDialog(
            driver = driverToAssign!!,
            allEmployees = allEmployees,
            onDismiss = { showAssignDialog = false; driverToAssign = null },
            onAssign = { employeeId ->
                authViewModel.assignDriverToEmployee(driverToAssign!!.userId, employeeId)
                showAssignDialog = false
                driverToAssign = null
            }
        )
    }

    // Manage Vehicles Dialog
    if (showManageVehiclesDialog && driverToManageVehicles != null) {
        ManageVehiclesDialog(
            driver = driverToManageVehicles!!,
            dashboardViewModel = dashboardViewModel,
            onDismiss = { showManageVehiclesDialog = false; driverToManageVehicles = null }
        )
    }
}

@Composable
fun DriverCard(
    driver: User, 
    isAdmin: Boolean = false, 
    onDelete: () -> Unit = {}, 
    onAssignEmployee: () -> Unit = {},
    onManageVehicles: () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(driver.name, style = MaterialTheme.typography.titleMedium)
                Text(driver.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Role: ${driver.role.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                if (driver.managerId.isNotEmpty()) {
                    Text("Assigned to employee", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (driver.vehicleIds.isNotEmpty()) {
                    Text("Vehicles: ${driver.vehicleIds.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (driver.isActive) "Active" else "Inactive", style = MaterialTheme.typography.labelMedium, color = if (driver.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Row {
                    if (isAdmin) {
                        IconButton(onClick = onManageVehicles) { Icon(Icons.Default.DirectionsCar, "Manage Vehicles", tint = MaterialTheme.colorScheme.tertiary) }
                        IconButton(onClick = onAssignEmployee) { Icon(Icons.Default.Person, "Assign Employee", tint = MaterialTheme.colorScheme.primary) }
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageVehiclesDialog(
    driver: User,
    dashboardViewModel: DashboardViewModel,
    onDismiss: () -> Unit,
) {
    val allVehicles by dashboardViewModel.vehicles.collectAsState()
    var newVehicleName by remember { mutableStateOf("") }
    var newVehiclePlate by remember { mutableStateOf("") }
    var showAddVehicleForm by remember { mutableStateOf(false) }
    var vehicleDropdownExpanded by remember { mutableStateOf(false) }
    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }
    
    // Get driver's current vehicles
    val driverVehicles = allVehicles.filter { driver.vehicleIds.contains(it.id) }
    // Get unassigned vehicles (not assigned to any driver)
    val availableVehicles = allVehicles.filter { it.userId.isEmpty() && !driver.vehicleIds.contains(it.id) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Vehicles: ${driver.name}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Currently assigned vehicles
                Text("Assigned Vehicles (${driverVehicles.size})", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                if (driverVehicles.isEmpty()) {
                    Text("No vehicles assigned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                } else {
                    driverVehicles.forEach { vehicle ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(vehicle.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(vehicle.plateNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                IconButton(onClick = { 
                                    dashboardViewModel.removeVehicleFromDriver(vehicle.id)
                                }) { Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Select existing vehicle dropdown
                Text("Assign Existing Vehicle", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                ExposedDropdownMenuBox(
                    expanded = vehicleDropdownExpanded,
                    onExpandedChange = { vehicleDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedVehicle?.let { "${it.name} (${it.plateNumber})" } ?: if (availableVehicles.isEmpty()) "No unassigned vehicles available" else "Select a vehicle",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Vehicle") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        enabled = availableVehicles.isNotEmpty()
                    )
                    if (availableVehicles.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = vehicleDropdownExpanded,
                            onDismissRequest = { vehicleDropdownExpanded = false }
                        ) {
                            availableVehicles.forEach { vehicle ->
                                DropdownMenuItem(
                                    text = { Text("${vehicle.name} (${vehicle.plateNumber})") },
                                    onClick = {
                                        selectedVehicle = vehicle
                                        vehicleDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        selectedVehicle?.let { vehicle ->
                            dashboardViewModel.assignVehicleToDriver(vehicle.id, driver.userId)
                            selectedVehicle = null
                        }
                    },
                    enabled = selectedVehicle != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Assign Selected Vehicle")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Add new vehicle section
                Text("Or Create New Vehicle", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { showAddVehicleForm = !showAddVehicleForm },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showAddVehicleForm) "Cancel" else "Create New Vehicle")
                }
                
                if (showAddVehicleForm) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newVehicleName,
                        onValueChange = { newVehicleName = it },
                        label = { Text("Vehicle Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newVehiclePlate,
                        onValueChange = { newVehiclePlate = it },
                        label = { Text("Plate Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newVehicleName.isNotBlank() && newVehiclePlate.isNotBlank()) {
                                dashboardViewModel.createVehicle(
                                    name = newVehicleName,
                                    plateNumber = newVehiclePlate,
                                    userId = driver.userId,
                                    employeeId = ""
                                )
                                newVehicleName = ""
                                newVehiclePlate = ""
                                showAddVehicleForm = false
                            }
                        },
                        enabled = newVehicleName.isNotBlank() && newVehiclePlate.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create & Assign")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignEmployeeDialog(driver: User, allEmployees: List<User>, onDismiss: () -> Unit, onAssign: (String?) -> Unit) {
    var selectedEmployeeId by remember { mutableStateOf(driver.managerId.takeIf { it.isNotEmpty() }) }
    var employeeDropdownExpanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Employee to ${driver.name}") },
        text = {
            Column {
                Text("Select an employee to manage this driver:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                
                ExposedDropdownMenuBox(
                    expanded = employeeDropdownExpanded,
                    onExpandedChange = { employeeDropdownExpanded = it }
                ) {
                    val selectedEmployee = allEmployees.find { it.userId == selectedEmployeeId }
                    OutlinedTextField(
                        value = selectedEmployee?.name ?: "No employee assigned",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Employee") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = employeeDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = employeeDropdownExpanded,
                        onDismissRequest = { employeeDropdownExpanded = false }
                    ) {
                        // Option to unassign
                        DropdownMenuItem(
                            text = { Text("No employee assigned") },
                            onClick = {
                                selectedEmployeeId = null
                                employeeDropdownExpanded = false
                            }
                        )
                        allEmployees.forEach { employee ->
                            DropdownMenuItem(
                                text = { Text("${employee.name} (${employee.email})") },
                                onClick = {
                                    selectedEmployeeId = employee.userId
                                    employeeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAssign(selectedEmployeeId) }) {
                Text("Assign")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun VehiclesTab(
    currentUser: User?,
    vehicles: List<Vehicle>,
    isLoading: Boolean,
    errorMessage: String,
    successMessage: String,
    dashboardViewModel: DashboardViewModel,
) {
    val services by dashboardViewModel.services.collectAsState()

    var showCreateForm by remember { mutableStateOf(false) }
    var vehicleName by remember { mutableStateOf("") }
    var plateNumber by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        if (errorMessage.isNotEmpty()) Text(text = errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        if (successMessage.isNotEmpty()) Text(text = successMessage, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))

        Button(onClick = { showCreateForm = !showCreateForm }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (showCreateForm) "Cancel" else "Add Vehicle")
        }

        if (showCreateForm) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Create Vehicle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(value = vehicleName, onValueChange = { vehicleName = it }, label = { Text("Vehicle Name") }, placeholder = { Text("e.g., Toyota Camry") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = plateNumber, onValueChange = { plateNumber = it }, label = { Text("Plate Number") }, placeholder = { Text("e.g., ABC-123") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            dashboardViewModel.createVehicle(name = vehicleName, plateNumber = plateNumber, userId = "", employeeId = currentUser?.userId ?: "")
                            vehicleName = ""
                            plateNumber = ""
                            showCreateForm = false
                        },
                        enabled = vehicleName.isNotBlank() && plateNumber.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create Vehicle")
                    }
                }
            }
        }

        Text("Vehicles (${vehicles.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))

        if (vehicles.isEmpty()) {
            Text("No vehicles found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        } else {
            LazyColumn {
                items(vehicles) { vehicle ->
                    VehicleManagementCard(
                        vehicle = vehicle,
                        services = services,
                        onDelete = { dashboardViewModel.deleteVehicle(vehicleId = vehicle.id, userId = currentUser?.userId ?: "") },
                        onAddService = { serviceId ->
                            dashboardViewModel.addServiceToVehicle(serviceId, vehicle.id)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleManagementCard(
    vehicle: Vehicle,
    services: List<Service>,
    onDelete: () -> Unit,
    onAddService: (String) -> Unit,
) {
    var showServiceMenu by remember { mutableStateOf(false) }
    val vehicleServices = services.filter { it.vehicleIds.contains(vehicle.id) }
    
    // Filter out services that are already assigned to this vehicle
    val availableServices = services.filter { service ->
        !service.vehicleIds.contains(vehicle.id)
    }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    vehicle.plateNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    "Status: ${vehicle.status.name}",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (vehicleServices.isNotEmpty()) {
                    Text(
                        "Services: ${vehicleServices.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row {
                Box {
                    IconButton(
                        onClick = { showServiceMenu = true },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Add Service",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DropdownMenu(
                        expanded = showServiceMenu,
                        onDismissRequest = { showServiceMenu = false },
                    ) {
                        if (availableServices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(if (vehicleServices.isNotEmpty()) "All services assigned" else "No services available") },
                                onClick = { showServiceMenu = false },
                            )
                        } else {
                            availableServices.forEach { service ->
                                DropdownMenuItem(
                                    text = { Text(service.name) },
                                    onClick = {
                                        onAddService(service.id)
                                        showServiceMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceVariantsTab(
    serviceVariants: List<ServiceVariant>,
    isLoading: Boolean,
    errorMessage: String,
    successMessage: String,
    dashboardViewModel: DashboardViewModel,
) {
    var showCreateForm by remember { mutableStateOf(false) }
    var variantName by remember { mutableStateOf("") }
    var variantDescription by remember { mutableStateOf("") }
    var mileageLimit by remember { mutableStateOf("1000") }
    
    // Edit dialog state
    var showEditDialog by remember { mutableStateOf(false) }
    var variantToEdit by remember { mutableStateOf<ServiceVariant?>(null) }
    var editVariantName by remember { mutableStateOf("") }
    var editVariantDescription by remember { mutableStateOf("") }
    var editMileageLimit by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        if (errorMessage.isNotEmpty()) Text(text = errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        if (successMessage.isNotEmpty()) Text(text = successMessage, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))

        Button(onClick = { showCreateForm = !showCreateForm }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (showCreateForm) "Cancel" else "Add Service Variant")
        }

        if (showCreateForm) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Create Service Variant", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(value = variantName, onValueChange = { variantName = it }, label = { Text("Variant Name") }, placeholder = { Text("e.g., Economy Oil Change") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = variantDescription, onValueChange = { variantDescription = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = mileageLimit, onValueChange = { mileageLimit = it }, label = { Text("Mileage Limit") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            dashboardViewModel.createServiceVariant(name = variantName, description = variantDescription, mileageLimit = mileageLimit.toFloatOrNull() ?: 1000f, createdBy = "")
                            variantName = ""
                            variantDescription = ""
                            mileageLimit = "1000"
                            showCreateForm = false
                        },
                        enabled = variantName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create Variant")
                    }
                }
            }
        }

        Text("Service Variants (${serviceVariants.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))

        if (serviceVariants.isEmpty()) {
            Text("No service variants found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        } else {
            LazyColumn {
                items(serviceVariants) { variant ->
                    ServiceVariantCard(
                        variant = variant,
                        onDelete = { dashboardViewModel.deleteServiceVariant(variantId = variant.id) },
                        onEdit = {
                            variantToEdit = variant
                            editVariantName = variant.name
                            editVariantDescription = variant.description
                            editMileageLimit = variant.mileageLimit.toInt().toString()
                            showEditDialog = true
                        }
                    )
                }
            }
        }
    }

    // Edit Dialog
    if (showEditDialog && variantToEdit != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Service Variant") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editVariantName,
                        onValueChange = { editVariantName = it },
                        label = { Text("Variant Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editVariantDescription,
                        onValueChange = { editVariantDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editMileageLimit,
                        onValueChange = { editMileageLimit = it.filter { char -> char.isDigit() } },
                        label = { Text("Mileage Limit") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        dashboardViewModel.updateServiceVariant(
                            variantId = variantToEdit!!.id,
                            name = editVariantName,
                            description = editVariantDescription,
                            mileageLimit = editMileageLimit.toFloatOrNull() ?: variantToEdit!!.mileageLimit
                        )
                        showEditDialog = false
                        variantToEdit = null
                    },
                    enabled = editVariantName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ServiceVariantCard(variant: ServiceVariant, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(variant.name, style = MaterialTheme.typography.titleMedium)
                Text(variant.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Mileage Limit: ${variant.mileageLimit.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
