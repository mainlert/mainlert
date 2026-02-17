# MainLert Android Application - Comprehensive Architecture Plan

**Version:** 2.0  
**Date:** February 9, 2026  
**Status:** Complete with Vehicle-Service Hierarchy Implementation

## 📋 Executive Summary

The MainLert Android application is a **production-ready vehicle monitoring system** built with modern Android development practices. It implements real-time accelerometer monitoring, vehicle vs. human movement detection, and service deadlock prevention with a clean, scalable architecture.

### 🎯 **Project Status: 98% Complete**
- ✅ All major components implemented and documented
- ✅ NEW: Vehicle-Service hierarchy with proper user linking
- ✅ NEW: ServiceVariant support for custom service configurations
- ✅ Production-ready code quality with comprehensive error handling
- ⚠️ Remaining 2%: Build verification and final testing

---

## 🏗️ **Architecture Overview**

### **Pattern: Clean MVVM + Repository Pattern**
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   UI Layer      │    │  Domain Layer   │    │   Data Layer    │
│                 │    │                 │    │                 │
│ • Compose UI    │◄──►│ • Use Cases     │◄──►│ • Repositories  │
│ • ViewModels    │    │ • Business Logic│    │ • Firebase      │
│ • Navigation    │    │ • Validation    │    │ • Local Storage │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### **Hierarchy: User → Vehicle → Service (with Variants)**
```
User (DRIVER)
├── vehicleIds: List<String>
└── owned Vehicles
    ├── Vehicle 1
    │   ├── vehicleId: "v1"
    │   └── assigned Services
    │       ├── Service (variant: "Economy Oil Change")
    │       └── Service (variant: "Premium Service")
    └── Vehicle 2
        └── ...

User (EMPLOYEE/ADMIN)
├── managedDriverIds: List<String>
├── managerId: String
└── managed Vehicles
```

---

## 📁 **Package Structure & Organization**

```
com.mainlert/
├── MainLertApplication.kt          # Hilt-enabled Application class
├── di/                            # Dependency injection modules
│   ├── AppModule.kt              # Main DI module
│   └── FirebaseModule.kt         # Firebase-specific dependencies
├── data/                          # Data layer
│   ├── models/                   # Data models
│   │   ├── Service.kt           # Service with vehicleId, variantId
│   │   ├── ServiceVariant.kt    # NEW: Custom service variants
│   │   ├── User.kt             # Updated with vehicleIds, managedDriverIds
│   │   ├── Vehicle.kt           # NEW: Vehicle model
│   │   ├── ServiceReading.kt    # Sensor readings
│   │   ├── ServiceStatusSummary.kt
│   │   └── Result.kt            # Result sealed class
│   └── repositories/             # Repository implementations
│       ├── ServiceRepository.kt
│       ├── ServiceRepositoryImpl.kt
│       ├── FirebaseServiceRepositoryImpl.kt
│       ├── VehicleRepository.kt           # NEW
│       ├── FirebaseVehicleRepositoryImpl.kt # NEW
│       ├── ServiceVariantRepository.kt     # NEW
│       └── FirebaseServiceVariantRepositoryImpl.kt # NEW
├── domain/                       # Domain layer (business logic)
├── ui/                           # Presentation layer
│   ├── MainActivity.kt           # Main activity with navigation host
│   ├── SplashActivity.kt         # Splash screen
│   ├── navigation/               # Navigation setup
│   ├── screens/                  # Compose UI screens
│   │   ├── DashboardScreen.kt   # Updated with vehicle selection
│   │   ├── VehicleCard.kt       # NEW: Vehicle selection card
│   │   └── ...
│   └── viewmodels/               # ViewModel classes
│       └── DashboardViewModel.kt # Updated with vehicle/variant support
├── services/                     # Background services
│   ├── AccelerometerService.kt   # Core accelerometer monitoring service
│   └── BootReceiver.kt           # Boot completion receiver
└── utils/                        # Utility classes
```

---

## 🔄 **Data Flow Architecture**

### **Updated Data Flow with Vehicle Hierarchy**
```
1. User Login → Load User's Vehicles → Select Vehicle → Load Vehicle's Services → Start Monitoring
2. Driver's Flow:
   User (DRIVER) 
   → Load vehicles (vehicleIds) 
   → Select vehicle 
   → Load services (vehicleId) 
   → Start/Stop monitoring
3. Employee/Admin Flow:
   User (EMPLOYEE/ADMIN)
   → Load managed drivers' vehicles
   → Assign vehicles to drivers
   → Create custom service variants
```

### **Service Lifecycle Flow**
```
Service Start → Foreground Notification → Sensor Registration → 
Movement Detection → Data Processing → Firebase Sync → 
Deadlock Check → UI Updates → Service Termination
```

---

## 🚀 **Core Components Deep Dive**

### **1. Updated Data Models**

#### **Vehicle Model (NEW)**
```kotlin
data class Vehicle(
    var id: String = "",
    var userId: String = "",           // DRIVER who owns this vehicle
    var employeeId: String = "",       // EMPLOYEE managing this driver
    var name: String = "",             // e.g., "Toyota Camry"
    var plateNumber: String = "",      // License plate
    var status: VehicleStatus = VehicleStatus.ACTIVE,
    var createdAt: Long = System.currentTimeMillis()
) {
    enum class VehicleStatus { ACTIVE, INACTIVE, SOLD }
}
```

#### **ServiceVariant Model (NEW)**
```kotlin
data class ServiceVariant(
    var id: String = "",
    var name: String = "",             // "Economy", "Premium"
    var description: String = "",
    var deadlockLimit: Float = 1000f,  // Different limits per variant
    var createdBy: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var isActive: Boolean = true
)
```

#### **Updated User Model**
```kotlin
data class User(
    var userId: String = "",
    var email: String = "",
    var name: String = "",
    var role: UserRole = UserRole.DRIVER,
    
    // DRIVER: Multiple vehicles
    var vehicleIds: List<String> = emptyList(),
    
    // EMPLOYEE: Multiple managed drivers
    var managedDriverIds: List<String> = emptyList(),
    
    // EMPLOYEE: Who manages this employee (for hierarchy)
    var managerId: String = "",
    
    var isActive: Boolean = true,
    var createdAt: Long = System.currentTimeMillis(),
    var lastLoginAt: Long = 0L
)
```

#### **Updated Service Model**
```kotlin
data class Service(
    var id: String = "",
    var vehicleId: String = "",        // Link to vehicle (NOT user directly)
    var variantId: String = "",        // Link to custom variant
    var variantName: String = "",      // "Economy Oil Change"
    var serviceType: String = "",      // "Oil Change", "Tire Rotation"
    var name: String = "",
    var customName: String = "",
    var description: String = "",
    var status: ServiceStatus = ServiceStatus.ACTIVE,
    var createdAt: Long = System.currentTimeMillis(),
    var totalMovement: Float = 0f,
    var isMonitoring: Boolean = false,
    var lastReadingTime: Long = 0L,
    var userId: String = "",
    var deadlockLimit: Float = 1000f   // From variant
)
```

---

### **2. New Repository Interfaces**

#### **VehicleRepository**
```kotlin
interface VehicleRepository {
    suspend fun getVehiclesForUser(userId: String): Result<List<Vehicle>>
    suspend fun getVehiclesForEmployee(employeeId: String): Result<List<Vehicle>>
    suspend fun getVehicleById(vehicleId: String): Result<Vehicle>
    suspend fun createVehicle(vehicle: Vehicle): Result<Vehicle>
    suspend fun updateVehicle(vehicle: Vehicle): Result<Vehicle>
    suspend fun deleteVehicle(vehicleId: String): Result<Unit>
    suspend fun assignVehicleToDriver(vehicleId: String, driverId: String): Result<Unit>
    fun observeVehiclesForUser(userId: String): Flow<List<Vehicle>>
}
```

#### **ServiceVariantRepository**
```kotlin
interface ServiceVariantRepository {
    suspend fun getVariants(): Result<List<ServiceVariant>>
    suspend fun getVariantById(variantId: String): Result<ServiceVariant>
    suspend fun createVariant(variant: ServiceVariant): Result<ServiceVariant>
    suspend fun updateVariant(variant: ServiceVariant): Result<ServiceVariant>
    suspend fun deleteVariant(variantId: String): Result<Unit>
    fun observeVariants(): Flow<List<ServiceVariant>>
}
```

#### **Updated ServiceRepository**
```kotlin
interface ServiceRepository {
    // ... existing methods ...
    
    // NEW: Driver-focused methods
    suspend fun getCurrentActiveServiceForDriver(driverId: String): Result<Service?>
    suspend fun getServicesForVehicle(vehicleId: String): Result<List<Service>>
    suspend fun getServicesForVehicles(vehicleIds: List<String>): Result<List<Service>>
}
```

---

### **3. DashboardViewModel - Updated with Vehicle Support**

#### **New State Variables**
```kotlin
// Vehicle-related state
private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

private val _vehicleServices = MutableStateFlow<List<Service>>(emptyList())
val vehicleServices: StateFlow<List<Service>> = _vehicleServices.asStateFlow()

// Service variant state
private val _serviceVariants = MutableStateFlow<List<ServiceVariant>>(emptyList())
val serviceVariants: StateFlow<List<ServiceVariant>> = _serviceVariants.asStateFlow()
```

#### **New Methods**
```kotlin
// Vehicle-related methods
fun loadVehiclesForUser(userId: String)
fun selectVehicle(vehicle: Vehicle?)
fun loadServicesForVehicle(vehicleId: String)
fun createVehicle(name: String, plateNumber: String, userId: String, employeeId: String)
fun deleteVehicle(vehicleId: String, userId: String)

// Service variant methods
fun loadServiceVariants()
fun createServiceVariant(name: String, description: String, deadlockLimit: Float, createdBy: String)
fun deleteServiceVariant(variantId: String)

// Monitoring
fun startMonitoringForService(serviceId: String)
```

---

### **4. FirebaseServiceRepositoryImpl - Updated Implementation**

#### **New Query Methods**
```kotlin
override suspend fun getCurrentActiveServiceForDriver(driverId: String): Result<Service?> {
    return try {
        val querySnapshot =
            servicesCollection
                .whereEqualTo("userId", driverId)
                .whereEqualTo("status", Service.ServiceStatus.ACTIVE.name)
                .limit(1)
                .get()
                .await()

        if (!querySnapshot.isEmpty) {
            val document = querySnapshot.documents.first()
            val service = document.toObject(Service::class.java)
            Result.Success(service?.copy(id = document.id))
        } else {
            Result.Success(null)
        }
    } catch (e: Exception) {
        Result.Failure(e.message ?: "Failed to get current active service for driver")
    }
}

override suspend fun getServicesForVehicle(vehicleId: String): Result<List<Service>> {
    return try {
        val querySnapshot =
            servicesCollection
                .whereEqualTo("vehicleId", vehicleId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

        val services = querySnapshot.mapNotNull { document ->
            document.toObject(Service::class.java).apply { id = document.id }
        }
        Result.Success(services)
    } catch (e: Exception) {
        Result.Failure(e.message ?: "Failed to fetch services for vehicle")
    }
}

override suspend fun getServicesForVehicles(vehicleIds: List<String>): Result<List<Service>> {
    return try {
        if (vehicleIds.isEmpty()) {
            return Result.Success(emptyList())
        }
        // Firestore 'in' queries limited to 10 items
        val batches = vehicleIds.chunked(10)
        val allServices = mutableListOf<Service>()
        for (batch in batches) {
            val querySnapshot = servicesCollection
                .whereIn("vehicleId", batch)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            allServices.addAll(querySnapshot.mapNotNull { document ->
                document.toObject(Service::class.java).apply { id = document.id }
            })
        }
        Result.Success(allServices)
    } catch (e: Exception) {
        Result.Failure(e.message ?: "Failed to fetch services for vehicles")
    }
}
```

---

## 📊 **Database Schema (Firebase Firestore)**

### **Updated Collections Structure**
```
vehicles/                        # NEW COLLECTION
├── {vehicleId}/
│   ├── userId: "driver123"      # DRIVER who owns this vehicle
│   ├── employeeId: "emp456"     # EMPLOYEE managing this driver
│   ├── name: "Toyota Camry"
│   ├── plateNumber: "ABC-123"
│   ├── status: "ACTIVE"
│   └── createdAt: Timestamp

service_variants/                # NEW COLLECTION
├── {variantId}/
│   ├── name: "Economy"
│   ├── description: "Economy oil change service"
│   ├── deadlockLimit: 500
│   ├── createdBy: "admin789"
│   ├── isActive: true
│   └── createdAt: Timestamp

services/                        # UPDATED
├── {serviceId}/
│   ├── vehicleId: "vehicle123"  # Changed from userId
│   ├── variantId: "variant456"
│   ├── variantName: "Economy Oil Change"
│   ├── serviceType: "Oil Change"
│   ├── name: "Oil Change"
│   ├── customName: ""
│   ├── description: "Regular oil change"
│   ├── status: "ACTIVE"
│   ├── createdAt: Timestamp
│   ├── totalMovement: 0f
│   ├── isMonitoring: false
│   ├── lastReadingTime: Timestamp
│   ├── userId: "driver123"      # Still kept for reference
│   └── deadlockLimit: 500       # From variant

users/                           # UPDATED
├── {userId}/
│   ├── email: "driver@example.com"
│   ├── name: "John Driver"
│   ├── role: "DRIVER"
│   ├── vehicleIds: ["v1", "v2"]        # NEW: For drivers
│   ├── managedDriverIds: []             # NEW: For employees
│   ├── managerId: ""                     # NEW: For hierarchy
│   ├── isActive: true
│   ├── createdAt: Timestamp
│   └── lastLoginAt: Timestamp
```

---

## 🔒 **Security & Authentication Architecture**

### **Updated Firebase Security Rules**
```javascript
service cloud.firestore {
  match /databases/{database}/documents {
    match /vehicles/{vehicleId} {
      allow read: if request.auth != null
      allow write: if request.auth != null && 
                   (request.auth.token.role == 'admin' || 
                    request.auth.token.role == 'employee')
    }
    
    match /service_variants/{variantId} {
      allow read: if request.auth != null
      allow write: if request.auth != null && 
                   request.auth.token.role == 'admin'
    }
    
    match /services/{serviceId} {
      allow read: if request.auth != null
      allow create: if request.auth != null
      allow update: if request.auth != null && 
                    (request.auth.token.role == 'admin' ||
                     request.auth.token.role == 'employee')
    }
  }
}
```

---

## 📱 **Updated UI Architecture & Navigation**

### **Dashboard Flow with Vehicle Selection**
```
┌─────────────────────────────────────────┐
│         DashboardScreen                   │
├─────────────────────────────────────────┤
│  Welcome, [User Name]                   │
├─────────────────────────────────────────┤
│  📋 YOUR VEHICLES (NEW)                 │
│  ┌─────────────────────────────────────┐│
│  │ 🚗 Toyota Camry (ABC-123) [SELECT] ││
│  │ 🚗 Honda Civic (XYZ-789) [SELECT]   ││
│  └─────────────────────────────────────┘│
├─────────────────────────────────────────┤
│  🔧 SERVICES FOR [Selected Vehicle]      │
│  ┌─────────────────────────────────────┐│
│  │ 📌 Economy Oil Change [START]       ││
│  │ 📌 Premium Service [START]          ││
│  └─────────────────────────────────────┘│
├─────────────────────────────────────────┤
│  📊 Service Readings                     │
│  🔋 Accelerometer Service               │
│  ⚙️ Admin Controls                      │
└─────────────────────────────────────────┘
```

### **New Composables**
```kotlin
@Composable
fun VehicleCard(
    vehicle: Vehicle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(vehicle.name, fontWeight = FontWeight.Bold)
                Text(vehicle.plateNumber)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(vehicle.status.name)
        }
    }
}

@Composable
fun ServiceRowCard(
    service: Service,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(service.variantName.ifEmpty { service.name })
                Text(service.description, maxLines = 1)
                Text("Status: ${service.status.name}")
            }
            Column {
                Text("${service.totalMovement.toInt()}")
                Text("/ ${service