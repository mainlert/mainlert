package com.mainlert.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mainlert.data.models.User
import kotlinx.coroutines.tasks.await

/**
 * Utility class for initializing the default admin user "Gnerdy".
 * This ensures the admin user is created only once during app setup.
 */
class AdminInitializer(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("admin_init_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val ADMIN_INIT_KEY = "admin_initialized"
        private const val ADMIN_EMAIL = "arthurgideonfun1@gmail.com"
        private const val ADMIN_PASSWORD = "Athug0277"
        private const val ADMIN_NAME = "Gnerdy"
    }

    /**
     * Initializes the admin user if not already created.
     * This method should be called during app startup.
     *
     * @return true if admin was created or already exists, false if initialization failed
     */
    suspend fun initializeAdmin(): Boolean {
        return try {
            // Check if admin has already been initialized
            if (isAdminInitialized()) {
                return true
            }

            // Check if admin user already exists in Firebase
            val existingUser = findAdminUser()
            if (existingUser != null) {
                // Admin already exists, mark as initialized
                markAdminInitialized()
                return true
            }

            // Create admin user
            val adminUser = createAdminUser()
            if (adminUser != null) {
                markAdminInitialized()
                return true
            }

            false
        } catch (e: Exception) {
            // Log error for debugging
            android.util.Log.e("AdminInitializer", "Failed to initialize admin: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if admin initialization has already been completed.
     */
    private fun isAdminInitialized(): Boolean {
        return sharedPreferences.getBoolean(ADMIN_INIT_KEY, false)
    }

    /**
     * Marks admin initialization as completed.
     */
    private fun markAdminInitialized() {
        sharedPreferences.edit()
            .putBoolean(ADMIN_INIT_KEY, true)
            .apply()
    }

    /**
     * Searches for existing admin user in Firestore.
     */
    private suspend fun findAdminUser(): User? {
        return try {
            val querySnapshot =
                usersCollection
                    .whereEqualTo("email", ADMIN_EMAIL)
                    .whereEqualTo("role", User.UserRole.ADMIN.name)
                    .limit(1)
                    .get()
                    .await()

            if (querySnapshot.documents.isNotEmpty()) {
                documentToUser(querySnapshot.documents.first())
            } else {
                null
            }
        } catch (e: FirebaseFirestoreException) {
            android.util.Log.e("AdminInitializer", "Failed to find admin user: ${e.message}", e)
            null
        }
    }

    /**
     * Helper function to convert Firestore document to User object.
     * Manually maps fields to avoid POJO serialization issues.
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
            android.util.Log.e("AdminInitializer", "Error converting document to User: ${e.message}", e)
            null
        }
    }

    /**
     * Creates a new admin user with the predefined credentials.
     */
    private suspend fun createAdminUser(): User? {
        return try {
            // Create user in Firebase Authentication
            val authResult = auth.createUserWithEmailAndPassword(ADMIN_EMAIL, ADMIN_PASSWORD).await()
            val firebaseUser = authResult.user ?: throw Exception("User creation failed")

            // Create user document in Firestore
            val adminUser =
                User(
                    userId = firebaseUser.uid,
                    email = ADMIN_EMAIL,
                    name = ADMIN_NAME,
                    role = User.UserRole.ADMIN,
                    isActive = true,
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = 0L,
                )

            usersCollection.document(firebaseUser.uid).set(adminUser.toMap()).await()

            android.util.Log.i("AdminInitializer", "Admin user created successfully: $ADMIN_EMAIL")
            adminUser
        } catch (e: FirebaseAuthUserCollisionException) {
            // User already exists in Firebase Auth, try to find and update in Firestore
            android.util.Log.w("AdminInitializer", "Admin user already exists in Firebase Auth")
            handleExistingUser()
        } catch (e: Exception) {
            android.util.Log.e("AdminInitializer", "Failed to create admin user: ${e.message}", e)
            null
        }
    }

    /**
     * Handles case where user exists in Firebase Auth but not in Firestore.
     */
    private suspend fun handleExistingUser(): User? {
        return try {
            // Sign in to get the user
            val authResult = auth.signInWithEmailAndPassword(ADMIN_EMAIL, ADMIN_PASSWORD).await()
            val firebaseUser = authResult.user ?: return null

            // Check if user exists in Firestore
            val userDoc = usersCollection.document(firebaseUser.uid).get().await()

            if (userDoc.exists()) {
                // User exists, update to admin role if needed
                val existingUser = documentToUser(userDoc)
                if (existingUser?.role != User.UserRole.ADMIN) {
                    usersCollection.document(firebaseUser.uid).update(
                        mapOf("role" to User.UserRole.ADMIN.name),
                    ).await()
                }
                existingUser
            } else {
                // Create user document in Firestore
                val adminUser =
                    User(
                        userId = firebaseUser.uid,
                        email = ADMIN_EMAIL,
                        name = ADMIN_NAME,
                        role = User.UserRole.ADMIN,
                        isActive = true,
                        createdAt = System.currentTimeMillis(),
                        lastLoginAt = 0L,
                    )

                usersCollection.document(firebaseUser.uid).set(adminUser.toMap()).await()
                adminUser
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminInitializer", "Failed to handle existing user: ${e.message}", e)
            null
        }
    }

    /**
     * Resets admin initialization flag.
     * This can be used for testing or if admin needs to be recreated.
     */
    fun resetAdminInitialization() {
        sharedPreferences.edit()
            .putBoolean(ADMIN_INIT_KEY, false)
            .apply()
    }
}
