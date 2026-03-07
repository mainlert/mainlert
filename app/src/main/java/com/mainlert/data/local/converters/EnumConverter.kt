package com.mainlert.data.local.converters

import androidx.room.TypeConverter
import com.mainlert.data.models.Service
import com.mainlert.data.models.Vehicle
import com.mainlert.data.models.VehicleServiceMapping

/**
 * Type converter for enum objects in Room database.
 * Converts between enum objects and String values.
 */
class EnumConverter {
    
    // ServiceStatus converter
    @TypeConverter
    fun fromServiceStatus(status: Service.ServiceStatus?): String? {
        return status?.name
    }
    
    @TypeConverter
    fun toServiceStatus(status: String?): Service.ServiceStatus? {
        return status?.let { Service.ServiceStatus.valueOf(it) }
    }
    
    // VehicleStatus converter
    @TypeConverter
    fun fromVehicleStatus(status: Vehicle.VehicleStatus?): String? {
        return status?.name
    }
    
    @TypeConverter
    fun toVehicleStatus(status: String?): Vehicle.VehicleStatus? {
        return status?.let { Vehicle.VehicleStatus.valueOf(it) }
    }
    
    // MappingStatus converter
    @TypeConverter
    fun fromMappingStatus(status: VehicleServiceMapping.MappingStatus?): String? {
        return status?.name
    }
    
    @TypeConverter
    fun toMappingStatus(status: String?): VehicleServiceMapping.MappingStatus? {
        return status?.let { VehicleServiceMapping.MappingStatus.valueOf(it) }
    }
}