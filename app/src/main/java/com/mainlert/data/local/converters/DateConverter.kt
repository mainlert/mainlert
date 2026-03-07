package com.mainlert.data.local.converters

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converter for Date objects in Room database.
 * Converts between Date objects and Long timestamps.
 */
class DateConverter {
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}