package com.mainlert.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mainlert.data.models.Result
import com.mainlert.data.models.ServiceVariant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firebase-based implementation of ServiceVariantRepository.
 */
class FirebaseServiceVariantRepositoryImpl
    @Inject
    constructor() : ServiceVariantRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val variantsCollection = firestore.collection("service_variants")

    override suspend fun getVariants(): Result<List<ServiceVariant>> {
        return try {
            val querySnapshot =
                variantsCollection
                    .whereEqualTo("isActive", true)
                    .get()
                    .await()

            val variants =
                querySnapshot.mapNotNull { document ->
                    document.toObject(ServiceVariant::class.java).apply {
                        id = document.id
                    }
                }

            Result.Success(variants)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure(e.message ?: "Failed to fetch variants")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch variants")
        }
    }

    override suspend fun getVariantById(variantId: String): Result<ServiceVariant> {
        return try {
            val document = variantsCollection.document(variantId).get().await()

            if (document.exists()) {
                val variant = document.toObject(ServiceVariant::class.java) ?: throw Exception("Variant data not found")
                Result.Success(variant.copy(id = variantId))
            } else {
                Result.Failure("Variant not found")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch variant")
        }
    }

    override suspend fun createVariant(variant: ServiceVariant): Result<ServiceVariant> {
        return try {
            val variantData = variant.copy(id = "").toMap()
            val documentRef = variantsCollection.add(variantData).await()
            val newVariant = variant.copy(id = documentRef.id)
            Result.Success(newVariant)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to create variant")
        }
    }

    override suspend fun updateVariant(variant: ServiceVariant): Result<ServiceVariant> {
        return try {
            if (variant.id.isEmpty()) {
                return Result.Failure("Variant ID is required")
            }
            val variantData = variant.toMap()
            variantsCollection.document(variant.id).set(variantData).await()
            Result.Success(variant)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to update variant")
        }
    }

    override suspend fun deleteVariant(variantId: String): Result<Unit> {
        return try {
            variantsCollection.document(variantId).update(
                mapOf("isActive" to false),
            ).await()
            Result.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            Result.Failure("Firestore error: ${e.message}")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to delete variant")
        }
    }

    override fun observeVariants(): Flow<List<ServiceVariant>> {
        return callbackFlow {
            val query = variantsCollection.whereEqualTo("isActive", true)

            val listener =
                query.addSnapshotListener { querySnapshot, exception ->
                    if (exception != null) {
                        channel.close(exception)
                        return@addSnapshotListener
                    }

                    val variants =
                        querySnapshot?.mapNotNull { document ->
                            document.toObject(ServiceVariant::class.java).apply {
                                id = document.id
                            }
                        } ?: emptyList()

                    channel.trySend(variants)
                }

            awaitClose {
                listener.remove()
            }
        }
    }
}
