package com.fitforge.app.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.R
import com.fitforge.app.core.network.BackendWarmupState
import com.fitforge.app.core.startup.AppStartupViewModel
import com.fitforge.app.core.startup.StartupState
import com.fitforge.app.feature.onboarding.LoginScreen
import com.fitforge.app.feature.onboarding.OnboardingScreen
import com.fitforge.app.feature.onboarding.SignupScreen
import com.fitforge.app.feature.onboarding.UpdateRequiredScreen
import kotlinx.coroutines.launch

/** Root of the composable tree: version gate, then login, then onboarding (if needed), then the bottom-tab shell. */
@Composable
fun FitForgeApp(viewModel: AppStartupViewModel = hiltViewModel()) {
    val startupState by viewModel.state.collectAsStateWithLifecycle()
    val isWakingUp by BackendWarmupState.isWakingUp.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    var showSignup by remember { mutableStateOf(false) }
                    var needsOnboarding by remember { mutableStateOf(current.needsOnboarding) }
                    // A fresh login can hit an account that finished onboarding
                    // long ago (e.g. the demo account) just as easily as a brand
                    // new one — re-check the server profile rather than assuming.
                    var checkingOnboarding by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    when {
                        checkingOnboarding -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        !isLoggedIn && showSignup -> SignupScreen(
                            onSignupSuccess = {
                                isLoggedIn = true
                                needsOnboarding = true
                            },
                            onGoToLogin = { showSignup = false },
                        )
                        !isLoggedIn -> LoginScreen(
                            onLoginSuccess = {
                                isLoggedIn = true
                                checkingOnboarding = true
                                scope.launch {
                                    needsOnboarding = viewModel.checkOnboardingNeeded()
                                    checkingOnboarding = false
                                }
                            },
                            onGoToSignup = { showSignup = true },
                        )
                        needsOnboarding -> OnboardingScreen(
                            onOnboardingComplete = { needsOnboarding = false },
                        )
                        else -> FitForgeNavHost()
                    }
                }
            }
            // Layered above whichever screen is current so a cold-started
            // backend is visible no matter where the first request happens
            // (startup's app-config check, a login, or an early API call).
            if (isWakingUp) {
                WakingUpBanner(modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun WakingUpBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(top = 16.dp)
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = stringResource(R.string.backend_waking_up),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
