package com.fitforge.app.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.fitforge.app.R

/** The bottom-bar tabs. Route strings double as the NavHost's composable routes. */
enum class FitForgeDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    DASHBOARD(route = "dashboard", labelRes = R.string.nav_dashboard, icon = Icons.Filled.Dashboard),
    LOG(route = "log", labelRes = R.string.nav_log, icon = Icons.Filled.EditNote),
    COMMUNITIES(route = "communities", labelRes = R.string.nav_communities, icon = Icons.Filled.Groups),
    COACH(route = "coach", labelRes = R.string.nav_coach, icon = Icons.Filled.Forum),
    PROFILE(route = "profile", labelRes = R.string.nav_profile, icon = Icons.Filled.Person),
}
