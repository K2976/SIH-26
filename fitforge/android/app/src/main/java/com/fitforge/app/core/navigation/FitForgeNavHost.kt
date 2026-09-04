package com.fitforge.app.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fitforge.app.feature.coach.CoachScreen
import com.fitforge.app.feature.dashboard.DashboardScreen
import com.fitforge.app.feature.logbook.LogScreen
import com.fitforge.app.feature.profile.ProfileScreen

/** The logged-in shell: bottom bar plus its four tabs. */
@Composable
fun FitForgeNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { FitForgeBottomBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FitForgeDestination.DASHBOARD.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(FitForgeDestination.DASHBOARD.route) { DashboardScreen() }
            composable(FitForgeDestination.LOG.route) { LogScreen() }
            composable(FitForgeDestination.COACH.route) { CoachScreen() }
            composable(FitForgeDestination.PROFILE.route) { ProfileScreen() }
        }
    }
}
