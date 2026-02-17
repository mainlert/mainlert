package com.mainlert.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import android.util.Log
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mainlert.data.models.Service
import com.mainlert.data.models.User
import com.mainlert.data.models.Vehicle
import com.mainlert.ui.components.YouTubePlayer
import com.mainlert.ui.viewmodels.AuthViewModel
import com.mainlert.ui.viewmodels.DashboardViewModel
import com.mainlert.ui.viewmodels.DashboardViewModel.ResetDialogStep
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun dashboardScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
) {
    var currentServiceIndex by remember { mutableStateOf(0) }
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val successMessage by authViewModel.successMessage.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val serviceReadings by dashboardViewModel.serviceReadings.collectAsState()
    val services by dashboardViewModel.services.collectAsState()
    val vehicles by dashboardViewModel.vehicles.collectAsState()
    val selectedVehicle by dashboardViewModel.selectedVehicle.collectAsState()
    val vehicleServices by dashboardViewModel.vehicleServices.collectAsState()
    val isMonitoring by dashboardViewModel.isMonitoring.collectAsState()
    val accelerometerReadings by dashboardViewModel.accelerometerReadings.collectAsState()
    val showVehicleSelectionDialog by dashboardViewModel.showVehicleSelectionDialog.collectAsState()
    val showResetServiceDialog by dashboardViewModel.showResetServiceDialog.collectAsState()
    val resetDialogStep by dashboardViewModel.resetDialogStep.collectAsState()
    val selectedDriverForReset by dashboardViewModel.selectedDriverForReset.collectAsState()
    val selectedVehicleForReset by dashboardViewModel.selectedVehicleForReset.collectAsState()
    val selectedServiceForReset by dashboardViewModel.selectedServiceForReset.collectAsState()
    val allUsers by dashboardViewModel.allUsers.collectAsState()
    var vehiclesForReset by remember { mutableStateOf(vehicles) }
    var servicesForReset by remember { mutableStateOf(vehicleServices) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isOverlayVisible by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(true) }
    val inactivityTimeout = 15000L

    // Load vehicles for current user on launch
    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            dashboardViewModel.loadVehiclesForUser(user.userId)
        }
    }

    // Auto-rotate through vehicle services every 10 seconds
    LaunchedEffect(vehicleServices) {
        if (vehicleServices.isNotEmpty()) {
            currentServiceIndex = 0
        }
    }

    LaunchedEffect(currentServiceIndex) {
        while (true) {
            delay(10000) // 10 seconds per service
            if (vehicleServices.isNotEmpty()) {
                currentServiceIndex = (currentServiceIndex + 1) % vehicleServices.size
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val elapsed = System.currentTimeMillis() - lastInteractionTime
            isOverlayVisible = elapsed >= inactivityTimeout
        }
    }

    val onInteraction: () -> Unit = {
        lastInteractionTime = System.currentTimeMillis()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = if (isOverlayVisible) 160.dp else 0.dp)
                    .pointerInput(Unit) {
                        // Reset countdown on ANY touch to the dashboard
                        detectTapGestures { onInteraction() }
                    },
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!isOverlayVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "MainLert Dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = {
                            onInteraction()
                            authViewModel.logout()
                            navController.navigate("login") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        },
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
            if (errorMessage.isNotEmpty()) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            }
            if (successMessage.isNotEmpty()) {
                Text(text = successMessage, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
            }
            currentUser?.let { user ->
                Text(text = "Welcome, ${user.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
            }

            // Vehicle Selection Section
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Your Vehicles", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                    
                    if (vehicles.isEmpty()) {
                        Text(
                            text = "No vehicles assigned. Contact your administrator.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.height((vehicles.size * 80).coerceAtMost(240).dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(vehicles) { vehicle ->
                                VehicleCard(
                                    vehicle = vehicle,
                                    isSelected = selectedVehicle?.id == vehicle.id,
                                    onClick = { dashboardViewModel.selectVehicle(vehicle) }
                                )
                            }
                        }
                    }
                }
            }

            // Services for Selected Vehicle Section
            if (selectedVehicle != null) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Services for ${selectedVehicle?.name}",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            IconButton(onClick = { dashboardViewModel.selectVehicle(null) }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back to vehicles")
                            }
                        }
                        
                        if (vehicleServices.isEmpty()) {
                            Text(
                                text = "No services for this vehicle. Create a service to get started.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.height((vehicleServices.size * 100).coerceAtMost(300).dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(vehicleServices) { service ->
                                    ServiceRowCard(
                                        service = service,
                                        currentReadings = serviceReadings,
                                        onClick = { dashboardViewModel.startMonitoringForService(service.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Service Readings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                    if (vehicleServices.isEmpty()) {
                        Text(
                            text = "No services for selected vehicle",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val currentService = vehicleServices[currentServiceIndex]
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(currentService.variantName.ifEmpty { currentService.name }, style = MaterialTheme.typography.bodyMedium)
                                Text("$serviceReadings", style = MaterialTheme.typography.displaySmall, color = if (serviceReadings >= currentService.mileageLimit.toInt()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Mileage Limit", style = MaterialTheme.typography.bodyMedium)
                                Text("${currentService.mileageLimit.toInt()}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Text(
                            text = "Service ${currentServiceIndex + 1} of ${vehicleServices.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Accelerometer Service", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = {
                                onInteraction()
                                navController.navigate("service_management")
                            },
                            enabled = authViewModel.isAdmin(),
                            colors = ButtonDefaults.buttonColors(containerColor = if (authViewModel.isAdmin()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Manage Services")
                        }
                    }
                }
            }

            if (authViewModel.isEmployee() || authViewModel.isAdmin()) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Service Management", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(
                                onClick = {
                                    onInteraction()
                                    dashboardViewModel.showResetServiceDialog()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                enabled = !isLoading,
                            ) {
                                Text("Reset Service")
                            }
                            TextButton(onClick = { onInteraction(); navController.navigate("service_history") }, enabled = !isLoading) {
                                Text("Service History")
                            }
                        }
                    }
                }
            }

            if (authViewModel.isAdmin()) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Admin Controls", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val buttonTextStyle = when {
                                maxWidth < 300.dp -> TextStyle(fontSize = 11.sp)
                                maxWidth < 400.dp -> TextStyle(fontSize = 12.sp)
                                else -> TextStyle(fontSize = 14.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onInteraction(); navController.navigate("user_management") }, enabled = !isLoading, modifier = Modifier.weight(1f)) {
                                    Text("User Management", style = buttonTextStyle)
                                }
                                Button(onClick = { onInteraction(); navController.navigate("system_settings") }, enabled = !isLoading, modifier = Modifier.weight(1f)) {
                                    Text("System Settings", style = buttonTextStyle)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        if (isOverlayVisible) {
            InactivityOverlay(
                modifier = Modifier.fillMaxSize(),
                vehicleServices = vehicleServices,
                selectedVehicle = selectedVehicle,
                accelerometerReadings = accelerometerReadings,
                isMonitoring = isMonitoring,
                isVideoEnabled = isVideoEnabled,
                serviceReadings = serviceReadings,
                onToggleMonitoring = {
                    if (isMonitoring) {
                        dashboardViewModel.stopMonitoringService()
                    } else {
                        dashboardViewModel.startMonitoringService()
                    }
                },
                onToggleVideo = { isVideoEnabled = !isVideoEnabled },
                onInteraction = onInteraction,
            )
        }

        // Show vehicle selection dialog when triggered
        if (showVehicleSelectionDialog) {
            VehicleSelectionDialog(
                vehicles = vehicles,
                onVehicleSelected = { vehicle ->
                    dashboardViewModel.onVehicleSelectedForMonitoring(vehicle)
                },
                onDismiss = {
                    dashboardViewModel.hideVehicleSelectionDialog()
                },
            )
        }

        // Show reset service dialog when triggered
        if (showResetServiceDialog) {
            ResetServiceDialog(
                isAdmin = authViewModel.isAdmin(),
                dialogStep = resetDialogStep,
                allUsers = allUsers,
                selectedDriver = selectedDriverForReset,
                vehicles = vehiclesForReset,
                selectedVehicle = selectedVehicleForReset,
                services = servicesForReset,
                selectedService = selectedServiceForReset,
                onSelectDriver = { driver ->
                    dashboardViewModel.selectDriverForReset(driver)
                    vehiclesForReset = dashboardViewModel.getVehiclesForCurrentUser()
                },
                onSelectVehicle = { vehicle ->
                    dashboardViewModel.selectVehicleForReset(vehicle)
                    servicesForReset = dashboardViewModel.getServicesForResetVehicle()
                },
                onSelectService = { service ->
                    dashboardViewModel.selectServiceForReset(service)
                },
                onConfirmReset = {
                    dashboardViewModel.confirmResetService()
                },
                onPrevious = {
                    dashboardViewModel.resetDialogPreviousStep()
                },
                onDismiss = {
                    dashboardViewModel.hideResetServiceDialog()
                },
            )
        }
    }
}

@Composable
fun InactivityOverlay(
    modifier: Modifier = Modifier,
    vehicleServices: List<Service>,
    selectedVehicle: Vehicle?,
    accelerometerReadings: Triple<Float, Float, Float>,
    isMonitoring: Boolean,
    isVideoEnabled: Boolean,
    serviceReadings: Int = 0,
    onToggleMonitoring: () -> Unit,
    onToggleVideo: () -> Unit,
    onInteraction: () -> Unit,
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.95f), Color(0xFF1A1A2E).copy(alpha = 0.98f), Color(0xFF16213E).copy(alpha = 0.98f)))))

        Box(modifier = Modifier.fillMaxSize().offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }.pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    if (abs(offsetX) > 100 || abs(offsetY) > 100) {
                        onInteraction()
                    }
                    offsetX = 0f
                    offsetY = 0f
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                },
            )
        }) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
                // Top-right video toggle button
                Box(modifier = Modifier.fillMaxWidth().padding(top = 56.dp, end = 16.dp)) {
                    IconButton(
                        onClick = {
                            onToggleVideo()
                            onInteraction()
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            imageVector = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = if (isVideoEnabled) "Turn video off" else "Turn video on",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // Vehicle info header
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selectedVehicle != null) {
                        Text(
                            text = selectedVehicle.name.uppercase(),
                            color = Color.Cyan.copy(alpha = 0.8f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                        Text(
                            text = selectedVehicle.plateNumber.uppercase(),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            letterSpacing = 2.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "SERVICES", color = Color.White.copy(alpha = 0.7f), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
                }

                if (isVideoEnabled) {
                    YouTubePlayer(
                        playlistId = "PLhFO614gb9CpY3ABVEPe_knvJmSXQfpVB",
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                    )
                }

                AutoScrollingServicesRow(
                    services = vehicleServices,
                    serviceReadings = serviceReadings,
                    modifier = Modifier.fillMaxWidth().padding(top = if (isVideoEnabled) 24.dp else 16.dp)
                )
            }
        }

        RoundedAccelerometerButton(
            x = accelerometerReadings.first,
            y = accelerometerReadings.second,
            z = accelerometerReadings.third,
            isMonitoring = isMonitoring,
            onClick = {
                Log.d("Dashboard", ">>> BUTTON CLICKED! isMonitoring=$isMonitoring <<<")
                Log.d("Dashboard", ">>> Calling onToggleMonitoring()")
                onToggleMonitoring()
                Log.d("Dashboard", ">>> Calling onInteraction()")
                onInteraction()
                Log.d("Dashboard", ">>> Button handler complete")
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        )
    }
}

@Composable
fun AutoScrollingServicesRow(
    services: List<Service>,
    modifier: Modifier = Modifier,
    serviceReadings: Int = 0
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Get the actual service count to loop through
    val displayServices = if (services.isEmpty()) {
        listOf(Service(name = "No Services", description = "", mileageLimit = 0f, variantName = ""))
    } else {
        services
    }
    
    // Calculate the number of items for seamless infinite scrolling
    // We create multiple copies to simulate infinite scrolling
    val totalItems = displayServices.size * 10 // 10 complete cycles for smooth looping
    
    // Auto-scroll effect for continuous looping animation
    LaunchedEffect(displayServices) {
        if (displayServices.isNotEmpty()) {
            var currentIndex = 0
            while (true) {
                // Delay between scrolls (adjust for speed)
                delay(3000L) // 3 seconds per scroll
                
                // Move to next item
                currentIndex = (currentIndex + 1) % displayServices.size
                
                // Scroll to position with animation
                // We use a multiplier to create the illusion of infinite scrolling
                val targetIndex = currentIndex + (displayServices.size * 5) // Start from middle
                
                coroutineScope.launch {
                    listState.animateScrollToItem(targetIndex)
                }
            }
        }
    }

    Column(modifier = modifier) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Create items for seamless infinite scrolling
            itemsIndexed(
                items = List(totalItems) { index ->
                    displayServices[index % displayServices.size]
                }
            ) { _, service ->
                ServiceCard(
                    service = service,
                    reading = if (service.name == "No Services") 0 else serviceReadings
                )
            }
        }
    }
}

@Composable
fun ServiceCard(service: Service, reading: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(160.dp).height(120.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Show variant name if available, otherwise service name
            Text(
                text = service.variantName.ifEmpty { service.name },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Show reading value with color based on limit
            Text(
                text = "$reading",
                color = if (reading >= service.mileageLimit.toInt() && service.mileageLimit > 0) {
                    Color.Red.copy(alpha = 0.9f)
                } else {
                    Color.Cyan.copy(alpha = 0.9f)
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            // Show limit info
            if (service.mileageLimit > 0) {
                Text(
                    text = "/ ${service.mileageLimit.toInt()}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun RoundedAccelerometerButton(x: Float, y: Float, z: Float, isMonitoring: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Log.d("RoundedButton", ">>> RoundedAccelerometerButton Composable rendered, isMonitoring=$isMonitoring <<<")
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = if (isMonitoring) 1.05f else 1f, animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1000), repeatMode = RepeatMode.Reverse), label = "pulseScale")

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isMonitoring) {
            Box(modifier = Modifier.size(180.dp).clip(CircleShape).background(Color.Cyan.copy(alpha = 0.2f * pulseScale)))
        }
        Button(
            onClick = {
                Log.d("RoundedButton", ">>> Button onClick lambda executed <<<")
                onClick()
                Log.d("RoundedButton", ">>> Button onClick lambda complete <<<")
            },
            modifier = Modifier.size(width = 280.dp, height = 80.dp),
            shape = RoundedCornerShape(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isMonitoring) Color.Cyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.1f)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (isMonitoring) "STOP" else "START", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ReadingItem(label = "X", value = x)
                    ReadingItem(label = "Y", value = y)
                    ReadingItem(label = "Z", value = z)
                }
            }
        }
    }
}

@Composable
private fun ReadingItem(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(text = String.format("%.2f", value), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(32.dp),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = vehicle.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = vehicle.plateNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Text(
                text = vehicle.status.name,
                style = MaterialTheme.typography.labelMedium,
                color = when (vehicle.status) {
                    Vehicle.VehicleStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                    Vehicle.VehicleStatus.INACTIVE -> MaterialTheme.colorScheme.secondary
                    Vehicle.VehicleStatus.SOLD -> MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
fun ServiceRowCard(
    service: Service,
    currentReadings: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Persist readings per service - survives process death
    // Only resets when admin/employee resets this specific service
    var accumulatedReadings by rememberSaveable(service.id) { mutableStateOf(0) }
    
    // Update when new readings come in (accumulate on top, never decrease)
    LaunchedEffect(currentReadings) {
        if (currentReadings > accumulatedReadings) {
            accumulatedReadings = currentReadings
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = service.variantName.ifEmpty { service.name },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = service.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = "Status: ${service.status.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (service.status) {
                        Service.ServiceStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                        Service.ServiceStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                        Service.ServiceStatus.CANCELLED -> MaterialTheme.colorScheme.error
                    },
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$accumulatedReadings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (accumulatedReadings >= service.mileageLimit.toInt()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = "/ ${service.mileageLimit.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun VehicleSelectionDialog(
    vehicles: List<Vehicle>,
    onVehicleSelected: (Vehicle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Vehicle",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose a vehicle to start monitoring:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                LazyColumn {
                    items(vehicles) { vehicle ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onVehicleSelected(vehicle) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                                Column {
                                    Text(
                                        text = vehicle.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = vehicle.plateNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ResetServiceDialog(
    isAdmin: Boolean,
    dialogStep: ResetDialogStep,
    allUsers: List<User>,
    selectedDriver: User?,
    vehicles: List<Vehicle>,
    selectedVehicle: Vehicle?,
    services: List<Service>,
    selectedService: Service?,
    onSelectDriver: (User) -> Unit,
    onSelectVehicle: (Vehicle) -> Unit,
    onSelectService: (Service) -> Unit,
    onConfirmReset: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, instruction) = when (dialogStep) {
        ResetDialogStep.SELECT_DRIVER -> "Select Driver" to if (isAdmin) "Choose a driver to reset their service readings:" else "Select your vehicle:"
        ResetDialogStep.SELECT_VEHICLE -> "Select Vehicle" to "Choose a vehicle:"
        ResetDialogStep.SELECT_SERVICE -> "Select Service" to "Choose a service to reset:"
        ResetDialogStep.CONFIRM_RESET -> "Confirm Reset" to "Are you sure you want to reset readings for:"
    }

    val items = when (dialogStep) {
        ResetDialogStep.SELECT_DRIVER -> allUsers.ifEmpty {
            // For employees, show current user's vehicles as if selecting driver
            // This is a fallback when no users are loaded
            emptyList()
        }
        ResetDialogStep.SELECT_VEHICLE -> vehicles
        ResetDialogStep.SELECT_SERVICE -> services
        ResetDialogStep.CONFIRM_RESET -> emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                when (dialogStep) {
                    ResetDialogStep.CONFIRM_RESET -> {
                        selectedService?.let { service ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = service.variantName.ifEmpty { service.name },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = service.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "This action will reset all readings for this service to zero.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        if (items.isEmpty() && dialogStep == ResetDialogStep.SELECT_DRIVER && !isAdmin) {
                            // For employees, show vehicles directly when no driver selection needed
                            Text(
                                text = "No vehicles available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(300.dp),
                            ) {
                                items(items) { item ->
                                    when (item) {
                                        is User -> {
                                            DriverSelectionItem(
                                                user = item,
                                                isSelected = selectedDriver?.userId == item.userId,
                                                onClick = { onSelectDriver(item) }
                                            )
                                        }
                                        is Vehicle -> {
                                            VehicleSelectionItem(
                                                vehicle = item,
                                                isSelected = selectedVehicle?.id == item.id,
                                                onClick = { onSelectVehicle(item) }
                                            )
                                        }
                                        is Service -> {
                                            ServiceSelectionItem(
                                                service = item,
                                                isSelected = selectedService?.id == item.id,
                                                onClick = { onSelectService(item) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (dialogStep == ResetDialogStep.CONFIRM_RESET) {
                Button(
                    onClick = onConfirmReset,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Text("Reset")
                }
            }
        },
        dismissButton = {
            Row {
                if (dialogStep != ResetDialogStep.SELECT_DRIVER || (dialogStep == ResetDialogStep.SELECT_DRIVER && isAdmin)) {
                    TextButton(onClick = onPrevious) {
                        Text("Back")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun DriverSelectionItem(
    user: User,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VehicleSelectionItem(
    vehicle: Vehicle,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Home,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vehicle.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = vehicle.plateNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ServiceSelectionItem(
    service: Service,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = service.variantName.ifEmpty { service.name },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = service.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Limit: ${service.mileageLimit.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
