package com.mainlert.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dependency injection module for MainLert app.
 * Provides repository implementations and other dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    // This module is kept for future dependencies
    // For Firebase repository implementations, see FirebaseModule
}
