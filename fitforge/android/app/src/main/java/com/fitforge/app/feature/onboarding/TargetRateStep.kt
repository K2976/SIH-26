package com.fitforge.app.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitforge.app.R

/**
 * Step 4: Target rate — only shown when energy direction is cut or gain.
 * A slider for 0.0–1.0 kg/week. Shows the clamp reason from the API when
 * the server clamps the value.
 */
@Composable
fun TargetRateStep(
    uiState: OnboardingUiState,
    onTargetRateChanged: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val currentRate = uiState.targetRateKgPerWeek.toFloatOrNull() ?: 0.5f
    val isMetric = uiState.unitPreference == "metric"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_target_rate_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_target_rate_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Rate display
        val displayRate = if (isMetric) {
            stringResource(R.string.onboarding_rate_kg_per_week, String.format("%.1f", currentRate))
        } else {
            val lbRate = currentRate * 2.205f
            stringResource(R.string.onboarding_rate_lb_per_week, String.format("%.1f", lbRate))
        }
        Text(
            text = displayRate,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Slider(
            value = currentRate,
            onValueChange = { newValue ->
                onTargetRateChanged(String.format("%.2f", newValue))
            },
            valueRange = 0f..1f,
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )

        // Clamp reason banner
        uiState.clampReason?.let { reason ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNext,
            enabled = !uiState.isSaving,
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
