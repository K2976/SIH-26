package com.fitforge.app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.core.startup.AppStartupViewModel
import com.fitforge.app.core.startup.StartupState
import com.fitforge.app.feature.onboarding.LoginScreen
import com.fitforge.app.feature.onboarding.UpdateRequiredScreen

/** Root of the composable tree: version gate, then login, then the bottom-tab shell. */
@Composable
fun FitForgeApp(viewModel: AppStartupViewModel = hiltViewModel()) {
    val startupState by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = startupState) {
            is StartupState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is StartupState.UpdateRequired -> {
                UpdateRequiredScreen(minSupportedVersion = current.minSupportedVersion)
            }
            is StartupState.Ready -> {
                var isLoggedIn by remember { mutableStateOf(current.isLoggedIn) }
                if (isLoggedIn) {
                    FitForgeNavHost()
                } else {
                    LoginScreen(onLoginSuccess = { isLoggedIn = true })
                }
            }
        }
    }
}
