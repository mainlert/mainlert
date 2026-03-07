package com.mainlert.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Log.d(TAG, "Boot completed received")

        // Check if the intent is for boot completion
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d(TAG, "Boot completed received - skipping auto-start")

            // Note: We no longer auto-start the service on boot because:
            // 1. We need a valid serviceId to start monitoring
            // 2. User should manually start monitoring when they want to track a vehicle
            // 3. This prevents unexpected battery drain

            // The service should be started manually from the dashboard when needed
            Log.d(TAG, "User must manually start monitoring from the app")
        }
    }
}
