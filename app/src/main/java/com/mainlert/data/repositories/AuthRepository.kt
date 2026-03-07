package com.mainlert.data.repositories

import com.mainlert.data.models.Result
import com.mainlert.data.models.User
import kotlinx.coroutines.flow.Flow

/**
 * Authentication repository interface for managing user authentication and role-based access.
 *
 * This repository handles Firebase Authentication operations including user registration,
 * login, logout, and role verification. It provides a clean abstraction layer between
 * the authentication logic and the UI layer.
 *
 * Usage examples:
 * ```kotlin
 * // Register a new user
 * val result = authRepository.registerUser("user@email.com", "password", User.UserRole.DRIVER)
 *
 * // Login user
 * val loginResult = authRepository.loginUser("user@email.com", "password")
 * ```
 */
interface AuthRepository {
    /**
     * Registers a new user with email and password.
     * Usage:
     * ```kotlin
     * val result = authRepository.registerUser("user@email.com", "password", User.UserRole.DRIVER)
     * ```
     * @param email User's email address
     * @param password User's password
     * @param role User's role (Driver, Employee, Admin)
     * @return Result containing the created User or an error message
     */
    suspend fun registerUser(
        email: String,
        password: String,
        role: User.UserRole,
    ): Result<User>

    /**
     * Authenticates a user with email and password.
     * Usage:
     * ```kotlin
     * val result = authRepository.loginUser("user@email.com", "password")
     * ```
     * @param email User's email address
     * @param password User's password
     * @return Result containing the authenticated User or an error message
     */
    suspend fun loginUser(
        email: String,
        password: String,
    ): Result<User>

    /**
     * Logs out the current user.
     *
     * @return Result indicating success or failure
     */
    suspend fun logout(): Result<Unit>

    /**
     * Gets the currently authenticated user.
     *
     * @return Flow emitting the current User or null if not authenticated
     */
    fun getCurrentUser(): Flow<User?>

    /**
     * Checks if a user is currently authenticated.
     *
     * @return true if user is authenticated, false otherwise
     */
    fun isAuthenticated(): Boolean

    /**
     * Gets the current user's role.
     *
     * @return UserRole of the current user, or null if not authenticated
     */
    fun getCurrentUserRole(): User.UserRole?

    /**
     * Sends a password reset email to the specified email address.
     *
     * @param email Email address to send reset link to
     * @return Result indicating success or failure
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /**
     * Updates the current user's profile information.
     *
     * @param displayName New display name (optional)
     * @param photoUrl New photo URL (optional)
     * @return Result containing the updated User or an error message
     */
    suspend fun updateProfile(
        displayName: String? = null,
        photoUrl: String? = null,
    ): Result<User>

    /**
     * Deletes the current user's account.
     *
     * @return Result indicating success or failure
     */
    suspend fun deleteAccount(): Result<Unit>

    /**
     * Gets all users (admin only).
     *
     * @return Result containing list of users or an error message
     */
    suspend fun getAllUsers(): Result<List<User>>

    /**
     * Gets users by role.
     *
     * @param role User role to filter by
     * @return Result containing list of users or an error message
     */
    suspend fun getUsersByRole(role: User.UserRole): Result<List<User>>

    /**
     * Gets drivers managed by an employee.
     *
     * @param employeeId Employee's user ID
     * @return Result containing list of drivers or an error message
     */
    suspend fun getManagedDrivers(employeeId: String): Result<List<User>>

    /**
     * Gets all employees.
     *
     * @return Result containing list of employees or an error message
     */
    suspend fun getAllEmployees(): Result<List<User>>

    /**
     * Assigns a driver to an employee.
     * Updates both the driver's managerId and adds driver to employee's managedDriverIds.
     *
     * @param driverId Driver's user ID
     * @param employeeId Employee's user ID (null to unassign)
     * @return Result indicating success or failure
     */
    suspend fun assignDriverToEmployee(driverId: String, employeeId: String?): Result<Unit>
}
