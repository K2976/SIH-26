package com.fitforge.app.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitforge.app.R

/**
 * Step 5: Fitness inputs — waist circumference, resting heart rate, activity
 * level. Explicitly skippable with a visible "Skip" that records DECLINED,
 * not null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FitnessInputsStep(
    uiState: OnboardingUiState,
    onWaistCmChanged: (String) -> Unit,
    onRestingHrChanged: (String) -> Unit,
    onActivityLevelChanged: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val isMetric = uiState.unitPreference == "metric"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
    ) {
        FitnessInputsHeader()
        Spacer(modifier = Modifier.height(24.dp))
        WaistInputField(
            value = uiState.waistCm,
            isMetric = isMetric,
            onValueChange = onWaistCmChanged,
        )
        Spacer(modifier = Modifier.height(16.dp))
        RestingHrInputField(
            value = uiState.restingHr,
            onValueChange = onRestingHrChanged,
        )
        Spacer(modifier = Modifier.height(20.dp))
        ActivityLevelSection(
            selectedLevel = uiState.activityLevel,
            onLevelChanged = onActivityLevelChanged,
        )
        Spacer(modifier = Modifier.height(24.dp))
        FitnessStepButtons(
            isSaving = uiState.isSaving,
            onNext = onNext,
            onSkip = onSkip,
        )
    }
}

@Composable
private fun FitnessInputsHeader() {
    Text(
        text = stringResource(R.string.onboarding_fitness_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = stringResource(R.string.onboarding_fitness_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun WaistInputField(
    value: String,
    isMetric: Boolean,
    onValueChange: (String) -> Unit,
) {
    val waistLabel = if (isMetric) {
        stringResource(R.string.onboarding_waist_cm)
    } else {
        stringResource(R.string.onboarding_waist_in)
    }
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == '.' }) },
        label = { Text(waistLabel) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RestingHrInputField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }) },
        label = { Text(stringResource(R.string.onboarding_resting_hr)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        suffix = { Text(stringResource(R.string.onboarding_bpm)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityLevelSection(
    selectedLevel: String?,
    onLevelChanged: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_activity_level_label),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(12.dp))

    val activityOptions = listOf(
        "sedentary" to R.string.onboarding_activity_sedentary,
        "lightly_active" to R.string.onboarding_activity_lightly_active,
        "moderately_active" to R.string.onboarding_activity_moderately_active,
        "very_active" to R.string.onboarding_activity_very_active,
        "extra_active" to R.string.onboarding_activity_extra_active,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        activityOptions.forEach { (value, labelRes) ->
            val selected = selectedLevel == value
            OutlinedCard(
                onClick = { onLevelChanged(value) },
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
                    text = stringResource(labelRes),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun FitnessStepButtons(
    isSaving: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Button(
        onClick = onNext,
        enabled = !isSaving,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Text(stringResource(R.string.onboarding_next))
        }
    }

    TextButton(
        onClick = onSkip,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSaving,
    ) {
        Text(stringResource(R.string.onboarding_skip))
    }
}
