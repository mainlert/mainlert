package com.mainlert.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mainlert.data.local.LocalDatabase
import com.mainlert.data.local.conflict.ConflictResolver
import com.mainlert.data.local.network.NetworkMonitor
import com.mainlert.data.repositories.AuthRepository
import com.mainlert.data.repositories.FirebaseAuthRepositoryImpl
import com.mainlert.data.repositories.FirebaseLockRepositoryImpl
import com.mainlert.data.repositories.LockRepository
import com.mainlert.data.repositories.LocalServiceRepositoryImpl
import com.mainlert.data.repositories.LocalServiceVariantRepositoryImpl
import com.mainlert.data.repositories.LocalVehicleRepositoryImpl
import com.mainlert.data.repositories.LocalVehicleServiceMappingRepositoryImpl
import com.mainlert.data.repositories.ServiceRepository
import com.mainlert.data.repositories.ServiceVariantRepository
import com.mainlert.data.repositories.VehicleRepository
import com.mainlert.data.repositories.VehicleServiceMappingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(firebaseAuthRepositoryImpl: FirebaseAuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindVehicleRepository(localVehicleRepositoryImpl: LocalVehicleRepositoryImpl): VehicleRepository

    @Binds
    @Singleton
    abstract fun bindServiceRepository(localServiceRepositoryImpl: LocalServiceRepositoryImpl): ServiceRepository

    @Binds
    @Singleton
    abstract fun bindServiceVariantRepository(localServiceVariantRepositoryImpl: LocalServiceVariantRepositoryImpl): ServiceVariantRepository

    @Binds
    @Singleton
    abstract fun bindVehicleServiceMappingRepository(localVehicleServiceMappingRepositoryImpl: LocalVehicleServiceMappingRepositoryImpl): VehicleServiceMappingRepository

    @Binds
    @Singleton
    abstract fun bindLockRepository(firebaseLockRepositoryImpl: FirebaseLockRepositoryImpl): LockRepository
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseAuthModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
    
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }
    
    @Provides
    @Singleton
    fun provideLocalDatabase(
        @ApplicationContext context: android.content.Context
    ): LocalDatabase {
        return androidx.room.Room.databaseBuilder(
            context,
            LocalDatabase::class.java,
            "mainlert_database"
        ).addMigrations(
            LocalDatabase.MIGRATION_1_2,
            LocalDatabase.MIGRATION_2_3
        ).build()
    }
    
    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
    
    @Provides
    @Singleton
    fun provideConflictResolver(): ConflictResolver {
        return ConflictResolver()
    }
    
    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: android.content.Context
    ): NetworkMonitor {
        return NetworkMonitor(context)
    }
}

