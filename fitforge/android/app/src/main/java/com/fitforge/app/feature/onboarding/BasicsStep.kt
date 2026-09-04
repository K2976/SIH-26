package com.fitforge.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitforge.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Step 1: Date of birth, sex at birth, height, current weight, unit preference.
 * Timezone is read from the device and displayed but not editable.
 * This step is required — no skip button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicsStep(
    uiState: OnboardingUiState,
    onDateOfBirthChanged: (LocalDate) -> Unit,
    onSexAtBirthChanged: (String) -> Unit,
    onHeightCmChanged: (String) -> Unit,
    onCurrentWeightKgChanged: (String) -> Unit,
    onUnitPreferenceChanged: (String) -> Unit,
    onNext: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val isMetric = uiState.unitPreference == "metric"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_basics_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.onboarding_basics_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Unit preference toggle
        Text(
            text = stringResource(R.string.onboarding_unit_preference),
            style = MaterialTheme.typography.labelLarge,
        )
        val unitOptions = listOf("metric", "imperial")
        val unitLabels = listOf(
            stringResource(R.string.onboarding_unit_metric),
            stringResource(R.string.onboarding_unit_imperial),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            unitOptions.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = uiState.unitPreference == option,
                    onClick = { onUnitPreferenceChanged(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, unitOptions.size),
                ) {
                    Text(unitLabels[index])
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Date of birth
        Text(
            text = stringResource(R.string.onboarding_dob_label),
            style = MaterialTheme.typography.labelLarge,
        )
        var showDatePicker by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = uiState.dateOfBirth?.toString() ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.onboarding_dob_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        TextButton(onClick = { showDatePicker = true }) {
            Text(
                text = if (uiState.dateOfBirth == null) {
                    stringResource(R.string.onboarding_dob_pick)
                } else {
                    stringResource(R.string.onboarding_dob_change)
                },
            )
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = uiState.dateOfBirth?.let {
                    it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                },
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onDateOfBirthChanged(date)
                        }
                        showDatePicker = false
                    }) {
                        Text(stringResource(R.string.onboarding_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.onboarding_cancel))
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sex at birth
        Text(
            text = stringResource(R.string.onboarding_sex_label),
            style = MaterialTheme.typography.labelLarge,
        )
        val sexOptions = listOf("male", "female", "intersex")
        val sexLabels = listOf(
            stringResource(R.string.onboarding_sex_male),
            stringResource(R.string.onboarding_sex_female),
            stringResource(R.string.onboarding_sex_intersex),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            sexOptions.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = uiState.sexAtBirth == option,
                    onClick = { onSexAtBirthChanged(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, sexOptions.size),
                ) {
                    Text(sexLabels[index])
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Height
        val heightLabel = if (isMetric) {
            stringResource(R.string.onboarding_height_cm)
        } else {
            stringResource(R.string.onboarding_height_in)
        }
        OutlinedTextField(
            value = uiState.heightCm,
            onValueChange = { value -> onHeightCmChanged(value.filter { it.isDigit() || it == '.' }) },
            label = { Text(heightLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Current weight
        val weightLabel = if (isMetric) {
            stringResource(R.string.onboarding_weight_kg)
        } else {
            stringResource(R.string.onboarding_weight_lb)
        }
        OutlinedTextField(
            value = uiState.currentWeightKg,
            onValueChange = { value -> onCurrentWeightKgChanged(value.filter { it.isDigit() || it == '.' }) },
            label = { Text(weightLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Timezone (read-only info)
        Text(
            text = stringResource(R.string.onboarding_timezone_label, uiState.timezone),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Next button
        Button(
            onClick = onNext,
            enabled = !uiState.isSaving && uiState.dateOfBirth != null &&
                uiState.sexAtBirth != null &&
                uiState.heightCm.isNotBlank() &&
                uiState.currentWeightKg.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.onboarding_next))
            }
        }
    }
}
