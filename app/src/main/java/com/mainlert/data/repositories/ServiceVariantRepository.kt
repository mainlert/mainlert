package com.mainlert.data.repositories

import com.mainlert.data.models.Result
import com.mainlert.data.models.ServiceVariant
import kotlinx.coroutines.flow.Flow

/**
 * ServiceVariant repository interface for MainLert app.
 * Handles service variant operations.
 */
interface ServiceVariantRepository {
    /**
     * Get all active service variants.
     */
    suspend fun getVariants(): Result<List<ServiceVariant>>

    /**
     * Get variant by ID.
     */
    suspend fun getVariantById(variantId: String): Result<ServiceVariant>

    /**
     * Create new service variant.
     */
    suspend fun createVariant(variant: ServiceVariant): Result<ServiceVariant>

    /**
     * Update service variant.
     */
    suspend fun updateVariant(variant: ServiceVariant): Result<ServiceVariant>

    /**
     * Delete service variant.
     */
    suspend fun deleteVariant(variantId: String): Result<Unit>

    /**
     * Observe variants in real-time.
     */
    fun observeVariants(): Flow<List<ServiceVariant>>
}
