package com.mainlert.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mainlert.data.local.dao.ServiceDao
import com.mainlert.data.local.dao.ServiceVariantDao
import com.mainlert.data.local.dao.VehicleDao
import com.mainlert.data.local.dao.VehicleServiceMappingDao
import com.mainlert.data.local.entities.ServiceEntity
import com.mainlert.data.local.entities.ServiceVariantEntity
import com.mainlert.data.local.entities.VehicleEntity
import com.mainlert.data.local.entities.VehicleServiceMappingEntity
import com.mainlert.data.local.converters.DateConverter
import com.mainlert.data.local.converters.EnumConverter

/**
 * Room database for local data storage.
 * Provides offline-first architecture with hierarchical sync capabilities.
 */
@Database(
    entities = [
        VehicleEntity::class,
        ServiceEntity::class,
        ServiceVariantEntity::class,
        VehicleServiceMappingEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(DateConverter::class, EnumConverter::class)
abstract class LocalDatabase : RoomDatabase() {
    
    abstract fun vehicleDao(): VehicleDao
    abstract fun serviceDao(): ServiceDao
    abstract fun serviceVariantDao(): ServiceVariantDao
    abstract fun mappingDao(): VehicleServiceMappingDao
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add missing columns: model (TEXT) and year (INTEGER)
                database.execSQL("ALTER TABLE vehicles ADD COLUMN model TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE vehicles ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create service_variants table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `service_variants` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `mileageLimit` REAL NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `lastSyncTime` INTEGER NOT NULL DEFAULT 0,
                        `isSynced` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}