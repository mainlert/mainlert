package com.mainlert.data.repositories

import com.mainlert.data.models.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository for distributed locking operations.
 * Uses Firebase Firestore for cross-device coordination.
 * All operations are fire-and-forget with short TTL locks.
 */
interface LockRepository {
    /**
     * Attempts to acquire a lock for the given key.
     * Returns true if lock was acquired, false if already locked.
     */
    suspend fun acquireLock(lockKey: String): Result<Boolean>
    
    /**
     * Releases a lock if held.
     */
    suspend fun releaseLock(lockKey: String): Result<Unit>
    
    /**
     * Checks if a lock exists.
     */
    suspend fun isLocked(lockKey: String): Result<Boolean>
}
