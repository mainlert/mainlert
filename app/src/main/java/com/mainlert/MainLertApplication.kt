package com.mainlert

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main Application class for MainLert app.
 * This class is annotated with @HiltAndroidApp to enable dependency injection.
 */
@HiltAndroidApp
class MainLertApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize any application-wide components here
    }
}
