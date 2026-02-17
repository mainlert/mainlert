package com.mainlert.di

import com.google.firebase.auth.FirebaseAuth
import com.mainlert.data.repositories.AuthRepository
import com.mainlert.data.repositories.FirebaseAuthRepositoryImpl
import com.mainlert.data.repositories.FirebaseServiceRepositoryImpl
import com.mainlert.data.repositories.FirebaseServiceVariantRepositoryImpl
import com.mainlert.data.repositories.FirebaseVehicleRepositoryImpl
import com.mainlert.data.repositories.ServiceRepository
import com.mainlert.data.repositories.ServiceVariantRepository
import com.mainlert.data.repositories.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase dependency injection module for MainLert app.
 * Provides Firebase-based repository implementations.
 *
 * To use Firebase implementations:
 * 1. Add this module to your Hilt component
 * 2. Ensure Firebase dependencies are properly configured
 * 3. Configure google-services.json in your app
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseModule {
    @Binds
    @Singleton
    abstract fun bindFirebaseAuthRepository(firebaseAuthRepositoryImpl: FirebaseAuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindFirebaseServiceRepository(firebaseServiceRepositoryImpl: FirebaseServiceRepositoryImpl): ServiceRepository

    @Binds
    @Singleton
    abstract fun bindFirebaseVehicleRepository(firebaseVehicleRepositoryImpl: FirebaseVehicleRepositoryImpl): VehicleRepository

    @Binds
    @Singleton
    abstract fun bindFirebaseServiceVariantRepository(firebaseServiceVariantRepositoryImpl: FirebaseServiceVariantRepositoryImpl): ServiceVariantRepository
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseAuthModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
}
