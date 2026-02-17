# MainLert Android App

A comprehensive vehicle service management application with intelligent accelerometer-based monitoring.

## 🚀 Features

- **Role-based Access Control**: Driver, Employee, and Admin roles
- **Real-time Service Monitoring**: Accelerometer-based vehicle movement detection
- **Service Management**: Track and manage vehicle services
- **Deadlock Alerts**: Automatic notifications when service readings reach critical levels
- **Firebase Integration**: Real-time data sync and authentication
- **Background Monitoring**: Continuous accelerometer monitoring in foreground service

## 📋 Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM with Repository pattern
- **UI Framework**: Jetpack Compose
- **Dependency Injection**: Hilt
- **Database**: Firebase Firestore
- **Authentication**: Firebase Auth
- **Background Services**: Foreground Service + WorkManager
- **Sensors**: Android Sensor API
- **Coroutines**: For asynchronous operations

## 🏗️ Project Structure

```
MainLertApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/mainlert/
│   │   │   ├── MainLertApplication.kt          # Application class
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt             # Main activity
│   │   │   │   ├── theme/                      # Compose theming
│   │   │   │   ├── navigation/                 # Navigation setup
│   │   │   │   ├── screens/                    # UI screens
│   │   │   │   └── viewmodels/                 # ViewModels
│   │   │   ├── data/
│   │   │   │   ├── models/                     # Data models
│   │   │   │   └── repositories/               # Repository interfaces
│   │   │   ├── services/                       # Background services
│   │   │   └── di/                             # Dependency injection
│   │   ├── res/                                # Resources
│   │   └── AndroidManifest.xml
│   ├── build.gradle                            # App-level build config
│   └── google-services.json                    # Firebase configuration
├── build.gradle                                # Project-level build config
└── README.md
```

## 📱 Screens

### Login Screen
- User authentication with email/password
- Registration for new users
- Password reset functionality

### Dashboard Screen
- Service readings display
- Accelerometer monitoring controls
- Service management (for Employees/Admin)
- Real-time status updates

## 🔧 Setup Instructions

### 1. Firebase Configuration
1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Add an Android app with package name: `com.mainlert.mainlertapp`
3. Download `google-services.json` and place it in `app/` directory
4. Enable Firebase Authentication and Firestore

### 2. Required Permissions
The app requires the following permissions:
- `ACTIVITY_RECOGNITION` - For movement detection
- `FOREGROUND_SERVICE` - For background monitoring
- `POST_NOTIFICATIONS` - For alerts
- `INTERNET` - For Firebase connectivity

### 3. Build & Run
1. Open the project in Android Studio
2. Sync Gradle dependencies
3. Build and run on an Android device (API 24+)


## 🎯 Usage Examples

### Authentication
**Register a new user:**
```kotlin
viewModel.registerUser("user@email.com", "password", User.UserRole.DRIVER)
```

**Login a user:**
```kotlin
viewModel.loginUser("user@email.com", "password")
```

### Service Management
**Get all services:**
```kotlin
val result = serviceRepository.getServices()
```

**Create a new service:**
```kotlin
val newService = Service(/* ... */)
val createResult = serviceRepository.createService(newService)
```

### Accelerometer Service
**Start monitoring service:**
```kotlin
val intent = Intent(context, AccelerometerService::class.java)
context.startForegroundService(intent)
```

---

## 🔐 Security Features

- Firebase Authentication with email/password
- Role-based access control (Driver/Employee/Admin)
- Secure data transmission via Firebase
- Proper permission handling
- Background service security

## 📊 Database Structure

### Users Collection
```javascript
users/{userId}: {
  userId: string,
  email: string,
  name: string,
  role: "DRIVER" | "EMPLOYEE" | "ADMIN",
  assignedVehicleId: string,
  isActive: boolean,
  createdAt: timestamp,
  lastLoginAt: timestamp
}
```

### Services Collection
```javascript
services/{serviceId}: {
  serviceId: string,
  title: string,
  leadEmployeeId: string,
  startDate: timestamp,
  endDate: timestamp,
  cost: number,
  status: "ACTIVE" | "COMPLETED" | "CANCELLED",
  createdAt: timestamp,
  updatedAt: timestamp
}
```

### ServiceReadings Subcollection
```javascript
services/{serviceId}/readings/{readingId}: {
  readingId: string,
  serviceId: string,
  userId: string,
  vehicleId: string,
  timestamp: timestamp,
  readings: number,
  deadlockThreshold: number,
  isDeadlockReached: boolean,
  movementType: "VEHICLE" | "HUMAN" | "STATIONARY",
  accelerationData: {
    x: number,
    y: number,
    z: number,
    magnitude: number,
    timestamp: timestamp
  }
}
```

## 🚨 Important Notes

1. **Battery Optimization**: Users should disable battery optimization for optimal performance
2. **Permissions**: All required permissions must be granted for full functionality
3. **Background Monitoring**: Service runs in foreground to comply with Android background execution limits
4. **Testing**: Requires physical Android device for accelerometer testing

## 🤝 Contributing

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Android Jetpack libraries
- Firebase platform
- Kotlin coroutines
- Hilt dependency injection
- Material Design components