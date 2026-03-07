#!/bin/bash

# Test Firebase Connectivity Script for MainLert App
# This script helps diagnose Firebase service reading issues

echo "=== MainLert Firebase Connectivity Test ==="
echo ""

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    echo "❌ ADB not found. Please install Android SDK Platform Tools."
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ No Android device connected. Please connect your device and enable USB debugging."
    exit 1
fi

echo "✅ ADB and device connection OK"
echo ""

# Get the package name
PACKAGE_NAME="com.mainlert.mainlertapp"

echo "=== Clearing existing logs ==="
adb logcat -c
echo "✅ Logs cleared"
echo ""

echo "=== Starting Firebase connectivity test ==="
echo "Please start the MainLert app and begin a service reading."
echo "The service should start monitoring and attempt to write to Firebase."
echo ""
echo "Monitoring logs for Firebase operations..."
echo ""

# Monitor logs for Firebase-related operations
echo "Monitoring for Firebase operations (will run for 60 seconds):"
echo "Look for these log tags:"
echo "  - AccelerometerService"
echo "  - FirebaseVehicleServiceMappingRepositoryImpl"
echo "  - BootReceiver"
echo ""

# Filter logs for relevant Firebase operations
adb logcat -v time | grep -E "(AccelerometerService|FirebaseVehicleServiceMappingRepositoryImpl|BootReceiver|Firebase|Firestore)" | head -50

echo ""
echo "=== Test Complete ==="
echo ""
echo "If you see Firebase operations in the logs above, the connectivity is working."
echo "If you see errors, check:"
echo "  1. Internet connection on the device"
echo "  2. Firebase authentication"
echo "  3. Firebase rules permissions"
echo "  4. Service parameters (serviceId and vehicleId)"
echo ""
echo "To see all logs, run: adb logcat"
echo "To filter for specific tags, run: adb logcat | grep 'AccelerometerService'"