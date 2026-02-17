# MainLert Android App - Setup Guide

## Overview

MainLert is a comprehensive vehicle service management Android application built with Kotlin, Jetpack Compose, and Firebase. The app features real-time accelerometer-based movement detection for monitoring vehicle vs. human movement and detecting service deadlocks.

## Project Structure

```
MainLertApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/mainlert/
│   │   │   ├── MainLertApplication.kt          # Hilt-enabled Application class
│   │   │   ├── di/AppModule.kt                 # Dependency injection configuration
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt             # Main activity with navigation host
│   │   │   │   ├── theme/                      # Custom theming (non-Material Design)
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   ├── navigation/                 # Navigation setup
│   │   │   │   │   └── Navigation.kt
│   │   │   │   ├── screens/                    # UI screens
│   │   │   │   │   ├── LoginScreen.kt
│   │   │   │   │   ├── DashboardScreen.kt
│   │   │   │   │   ├── ServiceManagementScreen.kt
│   │   │   │   │   └── ServiceDetailsScreen.kt
│   │   │   │   ├── viewmodels/                 # ViewModels
│   │   │   │   │   ├── AuthViewModel.kt
│   │   │   │   │   └── DashboardViewModel.kt
│   │   │   │   └── services/                   # Background services
│   │   │   │       └── AccelerometerService.kt
│   │   │   ├── data/
│   │   │   │   ├── models/                     # Data models
│   │   │   │   │   ├── User.kt
│   │   │   │   │   ├── Service.kt
│   │   │   │   │   ├── ServiceReading.kt
│   │   │   │   │   └── Result.kt
│   │   │   │   ├── repositories/               # Repository implementations
│   │   │   │   │   ├── AuthRepository.kt
│   │   │   │   │   ├── AuthRepositoryImpl.kt
│   │   │   │   │   ├── ServiceRepository.kt
│   │   │   │   │   └── ServiceRepositoryImpl.kt
│   │   │   │   └── utils/                      # Data utilities
│   │   │   │       └── Extensions.kt
│   │   │   └── utils/                          # General utilities
│   │   │       └── Constants.kt
│   │   ├── res/                                # Resources
│   │   │   ├── values/
│   │   │   │   ├── strings.xml                 # String resources
│   │   │   │   ├── colors.xml                  # Color scheme
│   │   │   │   ├── styles.xml                  # Theme configurations
│   │   │   │   └── themes/                     # Theme definitions
│   │   │   ├── layout/                         # Layout files (if needed)
│   │   │   └── drawable/                       # Drawable resources
│   │   └── AndroidManifest.xml                 # App manifest
│   ├── build.gradle                            # App-level build configuration
│   └── proguard-rules.pro                      # ProGuard configuration
├── build.gradle                                # Project-level build configuration
├── gradlew                                     # Gradle wrapper (Linux/Mac)
├── gradlew.bat                                 # Gradle wrapper (Windows)
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar                  # Gradle wrapper JAR
│       └── gradle-wrapper.properties           # Wrapper configuration
├── google-services.json                        # Firebase configuration
└── README.md                                   # Project documentation
```

## Prerequisites

### Development Environment

1. **Android Studio** (latest version recommended)
2. **Android SDK** with API level 24+ (Android 7.0 Nougat)
3. **Java Development Kit (JDK)** 8 or higher
4. **Kotlin** plugin (included with Android Studio)

### Firebase Setup

1. **Create Firebase Project:**
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Create a new project named "MainLert"
   - Enable Google Analytics (optional)

2. **Add Android App:**
   - Register your Android app with package name: `com.mainlert.mainlertapp`
   - Download `google-services.json` and place it in `app/` directory

3. **Enable Authentication:**
   - In Firebase Console, go to Authentication
   - Enable Email/Password authentication
   - Configure sign-in methods as needed

4. **Set up Firestore Database:**
   - In Firebase Console, go to Firestore Database
   - Create database in production mode
   - Set up security rules (see Security Rules section below)

## Installation and Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd mainlert
cd MainLertApp
```

### 2. Install Gradle Wrapper Dependencies

If the `gradle-wrapper.jar` is missing, download it:

```bash
# Download gradle-wrapper.jar
curl -L https://services.gradle.org/distributions/gradle-8.0-bin.zip -o gradle-8.0-bin.zip
unzip gradle-8.0-bin.zip -d gradle/
mv gradle/gradle-8.0/lib/gradle-wrapper.jar gradle/wrapper/
```

### 3. Configure Firebase

1. Ensure `google-services.json` is in the `app/` directory
2. Verify Firebase dependencies in `app/build.gradle` match your Firebase project

### 4. Build the Project

```bash
# Clean and build the project
./gradlew clean build

# Or using Android Studio:
# 1. Open the project in Android Studio
# 2. Wait for Gradle sync to complete
# 3. Build > Clean Project
# 4. Build > Rebuild Project
```

### 5. Run the Application

```bash
# Run on connected device or emulator
./gradlew installDebug

# Or using Android Studio:
# 1. Connect Android device or start emulator
# 2. Run > Run 'app'
```

## Firebase Security Rules

Create `firestore.rules` file in your Firebase project:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can only access their own data
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Services can only be accessed by their owners
    match /services/{serviceId} {
      allow read, write: if request.auth != null && 
                          request.resource.data.userId == request.auth.uid;
    }
    
    // Service readings can only be accessed by their owners
    match /service_readings/{readingId} {
      allow read, write: if request.auth != null && 
                          request.resource.data.userId == request.auth.uid;
    }
    
    // Movement updates can only be created by authenticated users
    match /movement_updates/{updateId} {
      allow create: if request.auth != null;
      allow read: if request.auth != null;
      allow update, delete: if false; // Prevent updates/deletes
    }
  }
}
```

## Key Features Implementation

### 1. Authentication System

The app implements a comprehensive authentication system with three user roles:

- **Driver**: Can view dashboard and monitor services
- **Employee**: Can manage services and view reports
- **Admin**: Full system access including user management

### 2. Accelerometer Service

The core feature is the `AccelerometerService` which:

- Runs as a foreground service for continuous monitoring
- Detects vehicle vs. human movement using accelerometer data
- Calculates total movement and detects deadlocks
- Synchronizes data with Firebase in real-time
- Automatically stops monitoring after 5 minutes of inactivity

### 3. Service Management

Users can:
- Create and manage services
- Monitor real-time movement data
- View service history and readings
- Reset service readings
- Detect and handle deadlock conditions

### 4. Role-Based Access Control

The app implements strict role-based access:
- UI elements are conditionally displayed based on user role
- API access is controlled through Firebase security rules
- Admin functions are only available to admin users

## Troubleshooting

### Common Issues

1. **Gradle Sync Failures:**
   - Ensure all dependencies are correctly specified
   - Check internet connection for dependency downloads
   - Try invalidating caches: File > Invalidate Caches and Restart

2. **Firebase Authentication Issues:**
   - Verify `google-services.json` is correctly placed
   - Check package name matches Firebase project
   - Ensure SHA-1 fingerprint is registered in Firebase Console

3. **Permission Issues:**
   - Ensure all required permissions are in `AndroidManifest.xml`
   - Check runtime permission handling in the app
   - Verify target SDK version compatibility

4. **Build Errors:**
   - Clean and rebuild the project
   - Check Kotlin and Android plugin versions compatibility
   - Verify all imports are correct

### Debugging Tips

1. **Enable Debug Logging:**
   ```kotlin
   Log.d("MainLert", "Debug message")
   ```

2. **Check Firebase Console:**
   - Monitor authentication events
   - View Firestore data changes
   - Check for security rule violations

3. **Use Android Studio Profiler:**
   - Monitor memory usage
   - Check CPU usage for background services
   - Analyze network traffic

## Performance Optimization

### Battery Optimization

The app includes several battery optimization features:

1. **Foreground Service:** Required for continuous accelerometer monitoring
2. **Deadlock Detection:** Automatically stops monitoring after 5 minutes of inactivity
3. **Efficient Data Sync:** Only syncs essential data to Firebase
4. **Background Processing:** Uses WorkManager for non-critical background tasks

### Memory Management

1. **Coroutines:** Uses Kotlin coroutines for asynchronous operations
2. **Flow:** Implements reactive programming with Kotlin Flow
3. **Dependency Injection:** Uses Hilt for efficient dependency management
4. **Lifecycle Awareness:** Properly manages component lifecycles

## Security Considerations

1. **Data Encryption:** All data is encrypted in transit via HTTPS
2. **Authentication:** Firebase Authentication provides secure user management
3. **Authorization:** Role-based access control prevents unauthorized access
4. **Input Validation:** All user inputs are validated before processing
5. **ProGuard:** Code obfuscation is enabled in release builds

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Write tests for your changes
5. Run the test suite
6. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support and questions:
- Create an issue in the repository
- Check the Firebase documentation
- Refer to Android development guides

## Notes

- The project uses modern Android development practices
- All code follows Kotlin coding conventions
- The app is designed for production use with proper error handling
- Security and performance are prioritized throughout the implementation