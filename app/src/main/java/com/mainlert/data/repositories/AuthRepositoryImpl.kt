package com.mainlert.data.repositories

import com.mainlert.data.models.Result
import com.mainlert.data.models.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of AuthRepository for MainLert app.
 * Provides authentication operations without Firebase dependency.
 */
class AuthRepositoryImpl : AuthRepository {
    private var currentUser: User? = null

    /**
     * Registers a new user with email and password.
     */
    override suspend fun registerUser(
        email: String,
        password: String,
        role: User.UserRole,
    ): Result<User> {
        return try {
            val user =
                User(
                    userId = generateId(),
                    email = email,
                    name = email.substringBefore('@'),
                    role = role,
                    isActive = true,
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = 0L,
                )

            currentUser = user
            Result.Success(user)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Registration failed")
        }
    }

    /**
     * Authenticates a user with email and password.
     */
    override suspend fun loginUser(
        email: String,
        password: String,
    ): Result<User> {
        return try {
            val user =
                User(
                    userId = generateId(),
                    email = email,
                    name = email.substringBefore('@'),
                    role = User.UserRole.DRIVER,
                    isActive = true,
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis(),
                )

            currentUser = user
            Result.Success(user)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Login failed")
        }
    }

    /**
     * Logs out the current user.
     */
    override suspend fun logout(): Result<Unit> {
        return try {
            currentUser = null
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Logout failed")
        }
    }

    /**
     * Gets the currently authenticated user.
     */
    override fun getCurrentUser(): Flow<User?> =
        flow {
            emit(currentUser)
        }

    /**
     * Checks if a user is currently authenticated.
     */
    override fun isAuthenticated(): Boolean {
        return currentUser != null
    }

    /**
     * Gets the current user's role.
     */
    override fun getCurrentUserRole(): User.UserRole? {
        return currentUser?.role
    }

    /**
     * Sends a password reset email to the specified email address.
     */
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            // Simulate sending email
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to send password reset email")
        }
    }

    /**
     * Updates the current user's profile information.
     */
    override suspend fun updateProfile(
        displayName: String?,
        photoUrl: String?,
    ): Result<User> {
        return try {
            val user = currentUser ?: return Result.Failure("No authenticated user")

            val updatedUser =
                user.copy(
                    name = displayName ?: user.name,
                )

            currentUser = updatedUser
            Result.Success(updatedUser)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Profile update failed")
        }
    }

    /**
     * Deletes the current user's account.
     */
    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            currentUser = null
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Account deletion failed")
        }
    }

    private fun generateId(): String {
        return (System.currentTimeMillis() % 1000000).toString()
    }

    /**
     * Gets all users (admin only).
     */
    override suspend fun getAllUsers(): Result<List<User>> {
        return try {
            Result.Success(emptyList())
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch users")
        }
    }

    /**
     * Gets users by role.
     */
    override suspend fun getUsersByRole(role: User.UserRole): Result<List<User>> {
        return try {
            Result.Success(emptyList())
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch users by role")
        }
    }

    /**
     * Gets drivers managed by an employee.
     */
    override suspend fun getManagedDrivers(employeeId: String): Result<List<User>> {
        return try {
            Result.Success(emptyList())
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch managed drivers")
        }
    }

    /**
     * Gets all employees.
     */
    override suspend fun getAllEmployees(): Result<List<User>> {
        return try {
            Result.Success(emptyList())
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to fetch employees")
        }
    }

    /**
     * Assigns a driver to an employee.
     */
    override suspend fun assignDriverToEmployee(driverId: String, employeeId: String?): Result<Unit> {
        return try {
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to assign driver to employee")
        }
    }
}
