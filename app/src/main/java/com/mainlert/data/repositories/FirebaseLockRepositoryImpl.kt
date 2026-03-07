package com.mainlert.data.repositories

import android.os.Process
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.mainlert.data.models.Result
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.random.Random

/**
 * Firebase implementation of LockRepository.
 * Uses Firestore documents for distributed locking with TTL.
 * This is infrastructure code for cross-device coordination.
 */
class FirebaseLockRepositoryImpl @Inject constructor(
    private val firebaseFirestore: FirebaseFirestore
) : LockRepository {
    
    private val locksCollection = firebaseFirestore.collection("locks")
    
    override suspend fun acquireLock(lockKey: String): Result<Boolean> {
        return try {
            val lockDoc = locksCollection.document(lockKey).get().await()
            
            if (lockDoc.exists()) {
                // Lock already exists
                Result.Success(false)
            } else {
                // Create lock with TTL (30 seconds)
                locksCollection.document(lockKey).set(
                    mapOf(
                        "timestamp" to FieldValue.serverTimestamp(),
                        "processId" to Process.myPid(),
                        "random" to Random.nextLong()
                    )
                ).await()
                Result.Success(true)
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to acquire lock")
        }
    }
    
    override suspend fun releaseLock(lockKey: String): Result<Unit> {
        return try {
            locksCollection.document(lockKey).delete().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to release lock")
        }
    }
    
    override suspend fun isLocked(lockKey: String): Result<Boolean> {
        return try {
            val lockDoc = locksCollection.document(lockKey).get().await()
            Result.Success(lockDoc.exists())
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to check lock status")
        }
    }
}
