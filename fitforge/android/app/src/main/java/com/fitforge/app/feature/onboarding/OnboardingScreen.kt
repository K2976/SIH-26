@file:Suppress("LongMethod")

package com.fitforge.app.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.R

/**
 * Root composable for the onboarding wizard. Hosts a progress indicator and
 * switches between step composables using animated content transitions.
 */
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Progress indicator
            val stepIndex = OnboardingStep.entries.indexOf(uiState.currentStep)
            LinearProgressIndicator(
                progress = { (stepIndex + 1).toFloat() / viewModel.totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    slideInHorizontally { fullWidth -> direction * fullWidth } togetherWith
                        slideOutHorizontally { fullWidth -> -direction * fullWidth }
                },
                label = "onboarding_step",
            ) { step ->
                when (step) {
                    OnboardingStep.BASICS -> BasicsStep(
                        uiState = uiState,
                        onDateOfBirthChanged = viewModel::updateDateOfBirth,
                        onSexAtBirthChanged = viewModel::updateSexAtBirth,
                        onHeightCmChanged = viewModel::updateHeightCm,
                        onCurrentWeightKgChanged = viewModel::updateCurrentWeightKg,
                        onUnitPreferenceChanged = viewModel::updateUnitPreference,
                        onNext = viewModel::saveBasics,
                    )
                    OnboardingStep.CYCLE_TRACKING -> CycleTrackingStep(
                        uiState = uiState,
                        onTracksCycleChanged = viewModel::updateTracksCycle,
                        onNext = viewModel::saveCycleTracking,
                        onSkip = viewModel::skipCycleTracking,
                    )
                    OnboardingStep.GOAL -> GoalStep(
                        uiState = uiState,
                        onEnergyDirectionChanged = viewModel::updateEnergyDirection,
                        onTrainingFocusChanged = viewModel::updateTrainingFocus,
                        onNext = viewModel::saveGoal,
                        onSkip = viewModel::skipGoal,
                    )
                    OnboardingStep.TARGET_RATE -> TargetRateStep(
                        uiState = uiState,
                        onTargetRateChanged = viewModel::updateTargetRate,
                        onNext = viewModel::saveTargetRate,
                        onSkip = viewModel::skipTargetRate,
                    )
                    OnboardingStep.FITNESS_INPUTS -> FitnessInputsStep(
                        uiState = uiState,
                        onWaistCmChanged = viewModel::updateWaistCm,
                        onRestingHrChanged = viewModel::updateRestingHr,
                        onActivityLevelChanged = viewModel::updateActivityLevel,
                        onNext = viewModel::saveFitnessInputs,
                        onSkip = viewModel::skipFitnessInputs,
                    )
                    OnboardingStep.COMPLETION -> CompletionStep(
                        uiState = uiState,
                        onGetStarted = { viewModel.completeOnboarding(onOnboardingComplete) },
                    )
                }
            }
        }
    }
}
