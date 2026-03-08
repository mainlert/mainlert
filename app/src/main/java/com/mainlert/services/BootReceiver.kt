package com.mainlert.services

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mainlert.ui.MainActivity

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        const val ACTION_BOOT_DETECTION_COMPLETE = "com.mainlert.mainlertapp.BOOT_DETECTION_COMPLETE"
        const val EXTRA_DETECTION_RESULT = "detection_result"
        const val DETECTION_RESULT_MOVEMENT = "movement_detected"
        
        // Intent action for launching app from boot detection
        const val ACTION_LAUNCH_APP_FROM_BOOT = "com.mainlert.mainlertapp.LAUNCH_APP_FROM_BOOT"
        
        // Extras for launching app to show vehicle selection
        const val EXTRA_SHOW_VEHICLE_SELECTION = "extra_show_vehicle_selection"
        const val EXTRA_FROM_BOOT_DETECTION = "extra_from_boot_detection"
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
            Log.d(TAG, "Boot completed - checking if boot receiver is enabled")
            
            // Check if boot receiver is enabled
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val bootReceiverEnabled = prefs.getBoolean("boot_receiver_enabled", false)
            
            if (!bootReceiverEnabled) {
                Log.d(TAG, "Boot receiver disabled by user setting")
                return
            }
            
            // Start movement detection by starting AccelerometerService in detection mode
            Log.d(TAG, "Boot receiver enabled - starting movement detection")
            
            val serviceIntent = Intent(context, AccelerometerService::class.java).apply {
                action = AccelerometerService.ACTION_START_MONITORING
                putExtra(AccelerometerService.EXTRA_DETECTION_MODE, true)
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
