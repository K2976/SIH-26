@file:Suppress("MagicNumber")

package com.fitforge.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitforge.app.R
import com.fitforge.app.feature.community.CommunityHomeScreen
import com.fitforge.app.feature.cycle.CycleScreen
import com.fitforge.app.feature.health.HealthConnectScreen
import com.fitforge.app.feature.social.SocialProfileScreen

@Composable
fun ProfileScreen(
    onCommunityClick: (Int) -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LogoutButtonRow(onClick = { showLogoutConfirm = true })
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Profile") },
                icon = { Icon(Icons.Default.People, contentDescription = "Social") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Communities") },
                icon = { Icon(Icons.Default.Groups, contentDescription = "Communities") },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Health Sync") },
                icon = { Icon(Icons.Default.HealthAndSafety, contentDescription = "Health Connect") },
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text(stringResource(R.string.nav_cycle)) },
                icon = { Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.nav_cycle)) },
            )
        }

        when (selectedTab) {
            0 -> SocialProfileScreen()
            1 -> CommunityHomeScreen(onCommunityClick = onCommunityClick)
            2 -> HealthConnectScreen()
            3 -> CycleScreen()
        }
    }

    if (showLogoutConfirm) {
        LogoutConfirmDialog(
            onConfirm = {
                showLogoutConfirm = false
                viewModel.logout(onLogout)
            },
            onDismiss = { showLogoutConfirm = false },
        )
    }
}

@Composable
private fun LogoutButtonRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onClick) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Text(
                text = stringResource(R.string.profile_logout),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun LogoutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_logout_confirm_title)) },
        text = { Text(stringResource(R.string.profile_logout_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.profile_logout_confirm_button),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_logout_cancel))
            }
        },
    )
}
