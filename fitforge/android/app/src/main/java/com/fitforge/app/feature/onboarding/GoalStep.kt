package com.fitforge.app.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitforge.app.R

/**
 * Step 3: Goal — two independent choices. Energy direction first, then
 * training focus. Presented as independent selections because they are.
 * Skippable — skipping means no goal is created.
 */
@Composable
fun GoalStep(
    uiState: OnboardingUiState,
    onEnergyDirectionChanged: (String) -> Unit,
    onTrainingFocusChanged: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_goal_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Energy direction
        Text(
            text = stringResource(R.string.onboarding_energy_direction_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_energy_direction_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        val energyOptions = listOf(
            "cut" to R.string.onboarding_energy_cut,
            "maintain" to R.string.onboarding_energy_maintain,
            "gain" to R.string.onboarding_energy_gain,
            "not_tracking" to R.string.onboarding_energy_not_tracking,
        )
        energyOptions.forEach { (value, labelRes) ->
            SelectableCard(
                label = stringResource(labelRes),
                selected = uiState.energyDirection == value,
                onClick = { onEnergyDirectionChanged(value) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Training focus
        Text(
            text = stringResource(R.string.onboarding_training_focus_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_training_focus_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        val focusOptions = listOf(
            "strength" to R.string.onboarding_focus_strength,
            "hypertrophy" to R.string.onboarding_focus_hypertrophy,
            "endurance" to R.string.onboarding_focus_endurance,
            "general" to R.string.onboarding_focus_general,
        )
        focusOptions.forEach { (value, labelRes) ->
            SelectableCard(
                label = stringResource(labelRes),
                selected = uiState.trainingFocus == value,
                onClick = { onTrainingFocusChanged(value) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNext,
            enabled = !uiState.isSaving && uiState.energyDirection != null && uiState.trainingFocus != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.onboarding_next))
            }
        }

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving,
        ) {
            Text(stringResource(R.string.onboarding_skip))
        }
    }
}

@Composable
private fun SelectableCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
