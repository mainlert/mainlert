package com.mainlert.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mainlert.ui.navigation.mainLertNavHost
import com.mainlert.ui.theme.mainLertAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity for MainLert app.
 * This is the entry point of the application.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            mainLertAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    mainLertNavHost()
                }
            }
        }
    }

    /**
     * Checks and requests all required permissions at runtime.
     * Shows a user education dialog if permissions are not granted.
     * Only dangerous permissions are requested based on Android version support.
     * Note: ACTIVITY_RECOGNITION requires API 29+, POST_NOTIFICATIONS requires API 33+
     */
    private fun checkAndRequestPermissions() {
        // Build list of permissions based on Android version support
        val requiredPermissions = mutableListOf<String>()

        // Location permissions required on API 23+ (Android 6+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Activity Recognition requires API 29+ (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // Notification permission requires API 33+ (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Check which permissions are missing
        val missingPermissions =
            requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

        if (missingPermissions.isNotEmpty()) {
            showPermissionRationale(missingPermissions)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            // Permission results handled - next launch will check status again
            // If denied, user will be prompted again; if granted, no prompt will show
        }
    }

    /**
     * Shows a dialog explaining why permissions are needed, then requests them.
     */
    private fun showPermissionRationale(permissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(
                "MainLert needs activity recognition, foreground service, notification, and internet " +
                    "permissions to monitor vehicle movement and send alerts. Please grant these " +
                    "permissions for full functionality.",
            )
            .setPositiveButton("Grant") { _, _ ->
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
