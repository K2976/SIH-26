package com.fitforge.app.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fitforge.app.feature.coach.CoachScreen
import com.fitforge.app.feature.community.ChallengeDetailScreen
import com.fitforge.app.feature.community.CommunityDetailScreen
import com.fitforge.app.feature.community.CommunityHomeScreen
import com.fitforge.app.feature.cycle.CycleScreen
import com.fitforge.app.feature.dashboard.DashboardScreen
import com.fitforge.app.feature.logbook.LogScreen
import com.fitforge.app.feature.profile.ProfileScreen

/** The logged-in shell: bottom bar plus its tabs and sub-routes. */
@Composable
fun FitForgeNavHost(onLogout: () -> Unit = {}) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { FitForgeBottomBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FitForgeDestination.DASHBOARD.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(FitForgeDestination.DASHBOARD.route) {
                DashboardScreen(onOpenCycle = { navController.navigate("cycle") })
            }
            composable(FitForgeDestination.LOG.route) { LogScreen() }
            composable(FitForgeDestination.COMMUNITIES.route) {
                CommunityHomeScreen(
                    onCommunityClick = { communityId ->
                        navController.navigate("community/$communityId")
                    },
                )
            }
            composable(FitForgeDestination.COACH.route) { CoachScreen() }
            composable(FitForgeDestination.PROFILE.route) {
                ProfileScreen(
                    onCommunityClick = { communityId ->
                        navController.navigate("community/$communityId")
                    },
                    onLogout = onLogout,
                )
            }
            composable("cycle") { CycleScreen() }
            composable("community/{communityId}") { backStackEntry ->
                val communityId = backStackEntry.arguments?.getString("communityId")?.toIntOrNull()
                if (communityId != null) {
                    CommunityDetailScreen(
                        communityId = communityId,
                        onBackClick = { navController.popBackStack() },
                        onChallengeClick = { challengeId ->
                            navController.navigate("community/$communityId/challenge/$challengeId")
                        },
                    )
                }
            }
            composable("community/{communityId}/challenge/{challengeId}") { backStackEntry ->
                val communityId = backStackEntry.arguments?.getString("communityId")?.toIntOrNull()
                val challengeId = backStackEntry.arguments?.getString("challengeId")?.toIntOrNull()
                if (communityId != null && challengeId != null) {
                    ChallengeDetailScreen(
                        communityId = communityId,
                        challengeId = challengeId,
                        onBackClick = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
