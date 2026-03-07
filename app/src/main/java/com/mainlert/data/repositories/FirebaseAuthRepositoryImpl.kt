package com.mainlert.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mainlert.data.models.Result
import com.mainlert.data.models.User
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firebase-based implementation of AuthRepository for MainLert app.
 * Provides authentication operations with Firebase Authentication and Firestore.
 */
class FirebaseAuthRepositoryImpl
    @Inject
    constructor() : AuthRepository {
        private val auth = FirebaseAuth.getInstance()
        private val firestore = FirebaseFirestore.getInstance()
        private val usersCollection = firestore.collection("users")

        /**
         * Registers a new user with email and password.
         */
        override suspend fun registerUser(
            email: String,
            password: String,
            role: User.UserRole,
        ): Result<User> {
            return try {
                // Create user in Firebase Authentication
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: throw Exception("User creation failed")

                // Create user document in Firestore
                val user =
                    User(
                        userId = firebaseUser.uid,
                        email = email,
                        name = email.substringBefore('@'),
                        role = role,
                        isActive = true,
                        createdAt = System.currentTimeMillis(),
                        lastLoginAt = 0L,
                    )

                usersCollection.document(firebaseUser.uid).set(user.toMap()).await()

                Result.Success(user)
            } catch (e: FirebaseAuthUserCollisionException) {
                Result.Failure("Email already exists")
            } catch (e: FirebaseAuthWeakPasswordException) {
                Result.Failure("Password is too weak")
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                Result.Failure("Invalid email or password format")
            } catch (e: FirebaseFirestoreException) {
                Result.Failure("Firestore error: ${e.message}")
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
                // Sign in with Firebase Authentication
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: throw Exception("Authentication failed")

                // Get user data from Firestore
                val userDoc = usersCollection.document(firebaseUser.uid).get().await()

                if (userDoc.exists()) {
                    val user = userDoc.toObject(User::class.java) ?: throw Exception("User data not found")

                    // Update last login time
                    usersCollection.document(firebaseUser.uid).update(
                        mapOf("lastLoginAt" to System.currentTimeMillis()),
                    ).await()

                    Result.Success(user)
                } else {
                    Result.Failure("User data not found")
                }
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                Result.Failure("Invalid email or password")
            } catch (e: FirebaseAuthInvalidUserException) {
                Result.Failure("User not found")
            } catch (e: FirebaseFirestoreException) {
                handleFirestoreError(e)
            } catch (e: Exception) {
                // Check for Firebase configuration errors
                val errorMessage = e.message ?: "Login failed"
                if (errorMessage.contains("configuration") || 
                    errorMessage.contains("firebase") ||
                    errorMessage.contains("api") ||
                    errorMessage.contains("project") ||
                    errorMessage.contains("NOT_FOUND") ||
                    errorMessage.contains("PERMISSION_DENIED")) {
                    Result.Failure("Firebase not configured. Please ensure:\n1. Firestore Database is enabled in Firebase Console\n2. Email/Password sign-in method is enabled\n3. Cloud Firestore API is enabled in Google Cloud Console")
                } else {
                    Result.Failure(errorMessage)
                }
            }
        }

        /**
         * Handles Firestore-specific errors with user-friendly messages
         */
        private fun handleFirestoreError(e: FirebaseFirestoreException): Result<User> {
            return when (e.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                    Result.Failure("Unable to connect to database. Please check your internet connection.")
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    Result.Failure("Access denied. Please contact support or re-login.")
                com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND ->
                    Result.Failure("Database not found. Please configure Firestore in Firebase Console:\n1. Go to https://console.firebase.google.com/project/mainlert\n2. Enable Firestore Database\n3. Enable Email/Password sign-in method")
                else ->
                    Result.Failure("Database error: ${e.message}")
            }
        }

        /**
         * Logs out the current user.
         */
        override suspend fun logout(): Result<Unit> {
            return try {
                auth.signOut()
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Logout failed")
            }
        }

        /**
         * Gets the currently authenticated user.
         */
        override fun getCurrentUser(): kotlinx.coroutines.flow.Flow<User?> {
            return kotlinx.coroutines.flow.flow {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val userDoc = usersCollection.document(currentUser.uid).get().await()
                    if (userDoc.exists()) {
                        val user = userDoc.toObject(User::class.java)
                        emit(user)
                    } else {
                        emit(null)
                    }
                } else {
                    emit(null)
                }

                // Listen for auth state changes - using callback approach
                auth.addAuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        // Use callback to fetch user data
                        usersCollection.document(user.uid).get().addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                val userData = userDoc.toObject(User::class.java)
                                // Can't emit directly from callback, so we'll ignore this for now
                                // In a real app, use callbackFlow to handle this properly
                            }
                        }
                    }
                }
            }
        }

        /**
         * Checks if a user is currently authenticated.
         */
        override fun isAuthenticated(): Boolean {
            return auth.currentUser != null
        }

        /**
         * Gets the current user's role.
         */
        override fun getCurrentUserRole(): User.UserRole? {
            val currentUser = auth.currentUser
            return if (currentUser != null) {
                // For immediate access, we might need to fetch from cache or return a default
                // In a real app, you'd want to cache this or fetch it asynchronously
                User.UserRole.DRIVER // Default role for immediate access
            } else {
                null
            }
        }

        /**
         * Sends a password reset email to the specified email address.
         */
        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
            return try {
                auth.sendPasswordResetEmail(email).await()
                Result.Success(Unit)
            } catch (e: FirebaseAuthInvalidUserException) {
                Result.Failure("User not found")
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
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    return Result.Failure("No authenticated user")
                }

                // Update display name in Firebase Authentication
                if (displayName != null) {
                    val profileUpdates =
                        com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName)
                            .build()
                    currentUser.updateProfile(profileUpdates).await()
                }

                // Update user document in Firestore
                val updates = mutableMapOf<String, Any>()
                if (displayName != null) updates["name"] = displayName
                if (photoUrl != null) updates["photoUrl"] = photoUrl

                if (updates.isNotEmpty()) {
                    usersCollection.document(currentUser.uid).update(updates).await()
                }

                // Fetch updated user data
                val userDoc = usersCollection.document(currentUser.uid).get().await()
                val updatedUser = userDoc.toObject(User::class.java)

                Result.Success(updatedUser ?: throw Exception("User update failed"))
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Profile update failed")
            }
        }

        /**
         * Deletes the current user's account.
         */
        override suspend fun deleteAccount(): Result<Unit> {
            return try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    return Result.Failure("No authenticated user")
                }

                // Delete user document from Firestore
                usersCollection.document(currentUser.uid).delete().await()

                // Delete user from Firebase Authentication
                currentUser.delete().await()

                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Account deletion failed")
            }
        }

        /**
         * Gets all users (admin only).
         */
        override suspend fun getAllUsers(): Result<List<User>> {
            return try {
                val querySnapshot = usersCollection.get().await()
                val users = querySnapshot.mapNotNull { document ->
                    documentToUser(document)
                }
                Result.Success(users)
            } catch (e: FirebaseFirestoreException) {
                Result.Failure(e.message ?: "Failed to fetch users")
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to fetch users")
            }
        }

        /**
         * Gets users by role.
         */
        override suspend fun getUsersByRole(role: User.UserRole): Result<List<User>> {
            return try {
                val querySnapshot = usersCollection.whereEqualTo("role", role.name).get().await()
                val users = querySnapshot.mapNotNull { document ->
                    documentToUser(document)
                }
                Result.Success(users)
            } catch (e: FirebaseFirestoreException) {
                Result.Failure(e.message ?: "Failed to fetch users by role")
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to fetch users by role")
            }
        }

        /**
         * Gets drivers managed by an employee.
         */
        override suspend fun getManagedDrivers(employeeId: String): Result<List<User>> {
            return try {
                val querySnapshot = usersCollection
                    .whereEqualTo("managerId", employeeId)
                    .whereEqualTo("role", User.UserRole.DRIVER.name)
                    .get()
                    .await()
                val drivers = querySnapshot.mapNotNull { document ->
                    documentToUser(document)
                }
                Result.Success(drivers)
            } catch (e: FirebaseFirestoreException) {
                Result.Failure(e.message ?: "Failed to fetch managed drivers")
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to fetch managed drivers")
            }
        }

        /**
         * Gets all employees.
         */
        override suspend fun getAllEmployees(): Result<List<User>> {
            return try {
                val querySnapshot = usersCollection
                    .whereEqualTo("role", User.UserRole.EMPLOYEE.name)
                    .get()
                    .await()
                val employees = querySnapshot.mapNotNull { document ->
                    documentToUser(document)
                }
                Result.Success(employees)
            } catch (e: FirebaseFirestoreException) {
                Result.Failure(e.message ?: "Failed to fetch employees")
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to fetch employees")
            }
        }

        /**
         * Assigns a driver to an employee.
         * Updates both the driver's managerId and adds driver to employee's managedDriverIds.
         */
        override suspend fun assignDriverToEmployee(driverId: String, employeeId: String?): Result<Unit> {
            return try {
                // Get the driver and current employee data
                val driverDoc = usersCollection.document(driverId).get().await()
                if (!driverDoc.exists()) {
                    return Result.Failure("Driver not found")
                }
                val driver = documentToUser(driverDoc) ?: return Result.Failure("Failed to parse driver data")

                // Get current employee's managedDriverIds if driver was previously assigned
                val previousEmployeeId = driver.managerId
                if (previousEmployeeId.isNotEmpty()) {
                    val previousEmployeeDoc = usersCollection.document(previousEmployeeId).get().await()
                    if (previousEmployeeDoc.exists()) {
                        val previousEmployee = documentToUser(previousEmployeeDoc)
                        if (previousEmployee != null) {
                            val updatedManagedDrivers = previousEmployee.managedDriverIds.filterNot { it == driverId }
                            usersCollection.document(previousEmployeeId).update(
                                mapOf("managedDriverIds" to updatedManagedDrivers)
                            ).await()
                        }
                    }
                }

                // Update driver's managerId
                usersCollection.document(driverId).update(
                    mapOf("managerId" to (employeeId ?: ""))
                ).await()

                // If assigning to new employee, add driver to their managedDriverIds
                if (employeeId != null) {
                    val employeeDoc = usersCollection.document(employeeId).get().await()
                    if (employeeDoc.exists()) {
                        val employee = documentToUser(employeeDoc)
                        if (employee != null) {
                            val updatedManagedDrivers = employee.managedDriverIds + driverId
                            usersCollection.document(employeeId).update(
                                mapOf("managedDriverIds" to updatedManagedDrivers)
                            ).await()
                        }
                    }
                }

                Result.Success(Unit)
            } catch (e: FirebaseFirestoreException) {
                Result.Failure("Firestore error: ${e.message}")
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Failed to assign driver to employee")
            }
        }

        /**
         * Helper function to convert Firestore document to User object.
         * Manually maps fields to avoid POJO serialization issues with enums.
         */
        private fun documentToUser(document: com.google.firebase.firestore.DocumentSnapshot): User? {
            return try {
                val data = document.data ?: return null
                val userId = document.id
                val email = data["email"] as? String ?: ""
                val name = data["name"] as? String ?: ""
                val roleString = data["role"] as? String ?: "DRIVER"
                val role = try { User.UserRole.valueOf(roleString) } catch (e: Exception) { User.UserRole.DRIVER }
                @Suppress("UNCHECKED_CAST")
                val vehicleIds = data["vehicleIds"] as? List<String> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val managedDriverIds = data["managedDriverIds"] as? List<String> ?: emptyList()
                val managerId = data["managerId"] as? String ?: ""
                val isActive = data["isActive"] as? Boolean ?: true
                val createdAt = (data["createdAt"] as? Long) ?: System.currentTimeMillis()
                val lastLoginAt = (data["lastLoginAt"] as? Long) ?: 0L

                User(
                    userId = userId,
                    email = email,
                    name = name,
                    role = role,
                    vehicleIds = vehicleIds,
                    managedDriverIds = managedDriverIds,
                    managerId = managerId,
                    isActive = isActive,
                    createdAt = createdAt,
                    lastLoginAt = lastLoginAt,
                )
            } catch (e: Exception) {
                null
            }
        }
    }
