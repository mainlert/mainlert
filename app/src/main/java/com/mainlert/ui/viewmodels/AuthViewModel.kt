package com.mainlert.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mainlert.data.models.Result
import com.mainlert.data.models.User
import com.mainlert.data.repositories.AuthRepository
import com.mainlert.data.repositories.CloudFunctionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Authentication ViewModel for MainLert app.
 * Handles authentication state and operations with role-based access control.
 */
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val cloudFunctionsRepository: CloudFunctionsRepository,
    ) : ViewModel() {
        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _errorMessage = MutableStateFlow("")
        val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

        private val _successMessage = MutableStateFlow("")
        val successMessage: StateFlow<String> = _successMessage.asStateFlow()

        private val _isAuthenticated = MutableStateFlow(false)
        val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

        private val _currentUser = MutableStateFlow<User?>(null)
        val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

        private val _currentUserRole = MutableStateFlow<User.UserRole?>(null)
        val currentUserRole: StateFlow<User.UserRole?> = _currentUserRole.asStateFlow()

        private val _allUsers = MutableStateFlow<List<User>>(emptyList())
        val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

        private val _managedDrivers = MutableStateFlow<List<User>>(emptyList())
        val managedDrivers: StateFlow<List<User>> = _managedDrivers.asStateFlow()

        private val _allDrivers = MutableStateFlow<List<User>>(emptyList())
        val allDrivers: StateFlow<List<User>> = _allDrivers.asStateFlow()

        private val _allEmployees = MutableStateFlow<List<User>>(emptyList())
        val allEmployees: StateFlow<List<User>> = _allEmployees.asStateFlow()

        init {
            viewModelScope.launch {
                authRepository.getCurrentUser().collect { user ->
                    _currentUser.value = user
                    _isAuthenticated.value = user != null
                    _currentUserRole.value = user?.role
                }
            }
        }

        fun registerUser(
            email: String,
            password: String,
            role: User.UserRole,
        ) {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = ""
                _successMessage.value = ""

                when (val result = authRepository.registerUser(email, password, role)) {
                    is Result.Success -> {
                        _successMessage.value = "Registration successful"
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Registration failed"
                    }
                }

                _isLoading.value = false
            }
        }

        fun loginUser(
            email: String,
            password: String,
        ) {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = ""
                _successMessage.value = ""

                when (val result = authRepository.loginUser(email, password)) {
                    is Result.Success -> {
                        _currentUser.value = result.data
                        _isAuthenticated.value = true
                        _currentUserRole.value = result.data?.role
                        _successMessage.value = "Login successful"
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Login failed"
                    }
                }

                _isLoading.value = false
            }
        }

        fun logout() {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = ""
                _successMessage.value = ""

                when (val result = authRepository.logout()) {
                    is Result.Success -> {
                        _successMessage.value = "Logout successful"
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Logout failed"
                    }
                }

                _isLoading.value = false
            }
        }

        fun isAdmin(): Boolean {
            return _currentUserRole.value == User.UserRole.ADMIN
        }

        fun isEmployee(): Boolean {
            val role = _currentUserRole.value
            return role == User.UserRole.EMPLOYEE || role == User.UserRole.ADMIN
        }

        fun getAllUsers() {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = ""

                when (val result = authRepository.getAllUsers()) {
                    is Result.Success -> {
                        _allUsers.value = result.data ?: emptyList()
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Failed to fetch users"
                    }
                }

                _isLoading.value = false
            }
        }

        fun getAllDrivers() {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = ""

                when (val result = authRepository.getUsersByRole(User.UserRole.DRIVER)) {
                    is Result.Success -> {
                        _allDrivers.value = result.data ?: emptyList()
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Failed to fetch drivers"
                    }
                }

                _isLoading.value = false
            }
        }

        fun getManagedDrivers() {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = ""

                val currentUserId = _currentUser.value?.userId ?: run {
                    _isLoading.value = false
                    return@launch
                }

                when (val result = authRepository.getManagedDrivers(currentUserId)) {
                    is Result.Success -> {
                        _managedDrivers.value = result.data ?: emptyList()
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Failed to fetch managed drivers"
                    }
                }

                _isLoading.value = false
            }
        }

        fun getAllEmployees() {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = ""

                when (val result = authRepository.getAllEmployees()) {
                    is Result.Success -> {
                        _allEmployees.value = result.data ?: emptyList()
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Failed to fetch employees"
                    }
                }

                _isLoading.value = false
            }
        }

        fun createUser(
            email: String,
            password: String,
            role: User.UserRole,
            name: String = "",
        ) {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = ""
                _successMessage.value = ""

                when (val result = cloudFunctionsRepository.createUser(email, password, role, name)) {
                    is Result.Success -> {
                        _successMessage.value = "User created successfully"
                        if (role == User.UserRole.DRIVER) {
                            if (isAdmin()) getAllDrivers() else getManagedDrivers()
                        }
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Failed to create user"
                    }
                }

                _isLoading.value = false
            }
        }

        fun deleteUser(userId: String) {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = ""

                when (val result = cloudFunctionsRepository.deleteUser(userId)) {
                    is Result.Success -> {
                        _successMessage.value = "User deleted"
                        getAllDrivers()
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Failed to delete user"
                    }
                }

                _isLoading.value = false
            }
        }

        fun assignDriverToEmployee(driverId: String, employeeId: String?) {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = ""
                _successMessage.value = ""

                when (val result = authRepository.assignDriverToEmployee(driverId, employeeId)) {
                    is Result.Success -> {
                        _successMessage.value = "Driver assigned successfully"
                        getAllDrivers()
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Failed to assign driver"
                    }
                }

                _isLoading.value = false
            }
        }

        fun sendPasswordResetEmail(email: String) {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = ""
                _successMessage.value = ""

                when (val result = authRepository.sendPasswordResetEmail(email)) {
                    is Result.Success -> {
                        _successMessage.value = "Password reset email sent"
                    }
                    is Result.Failure -> {
                        _errorMessage.value = result.message ?: "Failed to send password reset email"
                    }
                }

                _isLoading.value = false
            }
        }
    }
