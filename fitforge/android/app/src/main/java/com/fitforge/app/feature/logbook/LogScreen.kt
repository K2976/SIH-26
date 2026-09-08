@file:Suppress("LongMethod")

package com.fitforge.app.feature.logbook

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitforge.app.feature.repcounter.RepCounterScreen

private const val ROUTE_HOME = "log_home"
private const val ROUTE_PICK_EXERCISE = "log_pick_exercise/{sessionUuid}"
private const val ROUTE_ADD_SET = "log_add_set/{sessionUuid}/{slug}"
private const val ROUTE_CAMERA_REP = "log_camera_rep/{sessionUuid}/{slug}"
private const val ROUTE_SESSION_DETAIL = "log_session/{sessionUuid}"
private const val ROUTE_EXERCISE_HISTORY = "log_exercise/{slug}"

/** Root of the Log tab. A small nested graph, not the app-level nav host. */
@Composable
fun LogScreen() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            LogHomeScreen(
                onAddExercise = { sessionUuid ->
                    navController.navigate("log_pick_exercise/$sessionUuid")
                },
                onOpenSession = { sessionUuid ->
                    navController.navigate("log_session/$sessionUuid")
                },
                onOpenExerciseHistory = { slug ->
                    navController.navigate("log_exercise/$slug")
                },
            )
        }
        composable(
            ROUTE_PICK_EXERCISE,
            arguments = listOf(navArgument("sessionUuid") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionUuid = backStackEntry.arguments?.getString("sessionUuid").orEmpty()
            ExercisePickerScreen(
                onExerciseSelected = { slug ->
                    navController.navigate("log_add_set/$sessionUuid/$slug") {
                        popUpTo(ROUTE_HOME)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            ROUTE_ADD_SET,
            arguments = listOf(
                navArgument("sessionUuid") { type = NavType.StringType },
                navArgument("slug") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val sessionUuid = backStackEntry.arguments?.getString("sessionUuid").orEmpty()
            val slug = backStackEntry.arguments?.getString("slug").orEmpty()
            AddSetScreen(
                onDone = { navController.popBackStack(ROUTE_HOME, inclusive = false) },
                onUseCamera = { navController.navigate("log_camera_rep/$sessionUuid/$slug") },
            )
        }
        composable(
            ROUTE_CAMERA_REP,
            arguments = listOf(
                navArgument("sessionUuid") { type = NavType.StringType },
                navArgument("slug") { type = NavType.StringType },
            ),
        ) {
            RepCounterScreen(onDone = { navController.popBackStack() })
        }
        composable(
            ROUTE_SESSION_DETAIL,
            arguments = listOf(navArgument("sessionUuid") { type = NavType.StringType }),
        ) {
            SessionDetailScreen(onExerciseSelected = { slug -> navController.navigate("log_exercise/$slug") })
        }
        composable(
            ROUTE_EXERCISE_HISTORY,
            arguments = listOf(navArgument("slug") { type = NavType.StringType }),
        ) {
            ExerciseHistoryScreen()
        }
    }
}
