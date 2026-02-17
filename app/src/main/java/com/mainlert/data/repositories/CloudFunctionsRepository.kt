package com.mainlert.data.repositories

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.mainlert.data.models.Result
import com.mainlert.data.models.User
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Firebase Cloud Functions operations.
 * Handles user management through secure server-side functions.
 */
@Singleton
class CloudFunctionsRepository @Inject constructor() {
    private val functions: FirebaseFunctions = Firebase.functions

    /**
     * Creates a new user via Cloud Function with Admin SDK.
     * Only callable by Admin or Employee users.
     */
    suspend fun createUser(
        email: String,
        password: String,
        role: User.UserRole,
        name: String = "",
    ): Result<Map<String, Any>> {
        return try {
            val data = hashMapOf(
                "email" to email,
                "password" to password,
                "role" to role.name,
                "name" to name.ifEmpty { email.substringBefore("@") },
            )

            val result = functions
                .getHttpsCallable("createUser")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val resultData = result.data as? Map<String, Any>
                ?: throw Exception("Invalid response from server")

            Result.Success(resultData)
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("already-exists") == true -> "Email already in use"
                e.message?.contains("permission-denied") == true -> "You don't have permission to create users"
                e.message?.contains("unauthenticated") == true -> "Please login first"
                e.message?.contains("not-found") == true -> "Creator user not found. Your account may not be properly set up. Please contact an administrator."
                e.message?.contains("Creator user not found") == true -> "Creator user not found. Your account may not be properly set up. Please contact an administrator."
                else -> e.message ?: "Failed to create user"
            }
            Result.Failure(message)
        }
    }

    /**
     * Deletes a user (Admin only).
     */
    suspend fun deleteUser(userId: String): Result<Unit> {
        return try {
            val data = hashMapOf("userId" to userId)

            functions
                .getHttpsCallable("deleteUser")
                .call(data)
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to delete user")
        }
    }

    /**
     * Updates user role (Admin only).
     */
    suspend fun updateUserRole(userId: String, newRole: User.UserRole): Result<Unit> {
        return try {
            val data = hashMapOf(
                "userId" to userId,
                "newRole" to newRole.name,
            )

            functions
                .getHttpsCallable("updateUserRole")
                .call(data)
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to update role")
        }
    }

    /**
     * Assigns a driver to an employee (Admin only).
     */
    suspend fun assignDriverToEmployee(driverId: String, employeeId: String): Result<Unit> {
        return try {
            val data = hashMapOf(
                "driverId" to driverId,
                "employeeId" to employeeId,
            )

            functions
                .getHttpsCallable("assignDriverToEmployee")
                .call(data)
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to assign driver")
        }
    }
}
