package com.fitforge.app.feature.fitnessage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.data.repository.FitnessAgeInputsDomain

/**
 * Fitness age is computed entirely server-side (apps/engine/fitness_age.py)
 * from a published non-exercise VO2max model plus published age norms —
 * never estimated here, never touched by the LLM. This card only displays
 * the number the server already computed, the inputs that produced it, and
 * both citations, or — when any required input is missing or declined —
 * what's needed instead of a placeholder number (CLAUDE.md rule 2).
 */
@Composable
fun FitnessAgeCard(
    viewModel: FitnessAgeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Fitness age",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            when (uiState) {
                is FitnessAgeUiState.Missing -> Text(
                    text = "Needs age, sex, height, weight, resting heart rate, and " +
                        "activity level in your profile — add any missing ones to see this.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is FitnessAgeUiState.Available -> FitnessAgeDetails(uiState)
            }
        }
    }
}

@Composable
private fun FitnessAgeDetails(state: FitnessAgeUiState.Available) {
    Text(
        text = "${state.fitnessAge.toInt()} years",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    )
    val inputs: FitnessAgeInputsDomain? = state.inputs
    if (inputs != null) {
        Text(
            text = "${inputs.mets?.let { "%.1f".format(it) }} METs " +
                "(${inputs.vo2maxMlKgMin?.let { "%.0f".format(it) }} mL/kg/min) from " +
                "age ${inputs.age}, ${inputs.sexAtBirth}, BMI ${inputs.bmi}, " +
                "resting HR ${inputs.restingHr}, activity level ${inputs.activityLevel}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = inputs.modelCitation.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = inputs.normsCitation.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
