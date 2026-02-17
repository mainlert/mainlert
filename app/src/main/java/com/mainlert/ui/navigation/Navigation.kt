package com.mainlert.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mainlert.ui.screens.dashboardScreen
import com.mainlert.ui.screens.loginScreen
import com.mainlert.ui.screens.serviceDetailsScreen
import com.mainlert.ui.screens.serviceHistoryScreen
import com.mainlert.ui.screens.serviceManagementScreen
import com.mainlert.ui.screens.SystemSettingsScreen
import com.mainlert.ui.screens.userManagementScreen

/**
 * Main navigation host for the MainLert app.
 * Handles navigation between different screens.
 */
@Composable
fun mainLertNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login",
    ) {
        composable("login") {
            loginScreen(navController = navController)
        }

        composable("dashboard") {
            dashboardScreen(navController = navController)
        }

        composable("service_management") {
            serviceManagementScreen(navController = navController)
        }

        composable("service_details/{serviceId}") { backStackEntry ->
            val serviceId = backStackEntry.arguments?.getString("serviceId") ?: ""
            serviceDetailsScreen(navController = navController, serviceId = serviceId)
        }

        composable("service_history") {
            serviceHistoryScreen()
        }

        composable("user_management") {
            userManagementScreen()
        }

        composable("system_settings") {
            SystemSettingsScreen()
        }
    }
}
