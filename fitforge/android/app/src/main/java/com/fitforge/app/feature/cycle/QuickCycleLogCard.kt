package com.fitforge.app.feature.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.R

private val ENERGY_LABELS = listOf(
    1 to R.string.cycle_energy_1,
    2 to R.string.cycle_energy_2,
    3 to R.string.cycle_energy_3,
    4 to R.string.cycle_energy_4,
    5 to R.string.cycle_energy_5,
)

private val SYMPTOM_LABELS = mapOf(
    "cramps" to R.string.cycle_symptom_cramps,
    "fatigue" to R.string.cycle_symptom_fatigue,
    "headache" to R.string.cycle_symptom_headache,
    "bloating" to R.string.cycle_symptom_bloating,
    "mood_swings" to R.string.cycle_symptom_mood_swings,
    "tender_breasts" to R.string.cycle_symptom_tender_breasts,
    "acne" to R.string.cycle_symptom_acne,
    "back_pain" to R.string.cycle_symptom_back_pain,
)

/**
 * The one-tap daily check-in. Renders nothing when cycle tracking is off,
 * discreet mode is on, or she's dismissed it for this session — callers can
 * place it unconditionally on the dashboard.
 */
@Composable
fun QuickCycleLogCard(
    viewModel: QuickCycleLogViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    onOpenCycle: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.visible) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.hasLoggedToday) {
                LoggedState(uiState = uiState, onToggleSymptoms = viewModel::toggleSymptomPicker)
            } else {
                PromptState(onLogEnergy = viewModel::logEnergy, onSkip = viewModel::skipToday)
            }

            if (uiState.showSymptomPicker) {
                SymptomPicker(
                    selected = uiState.loggedSymptoms,
                    onSubmit = viewModel::logSymptoms,
                )
            }

            TextButton(onClick = onOpenCycle) {
                Text(stringResource(R.string.cycle_title))
            }
        }
    }
}

@Composable
private fun PromptState(onLogEnergy: (Int) -> Unit, onSkip: () -> Unit) {
    Text(
        text = stringResource(R.string.cycle_quick_log_prompt),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ENERGY_LABELS.forEach { (rating, labelRes) ->
            EnergyDot(rating = rating, label = stringResource(labelRes), onClick = { onLogEnergy(rating) })
        }
    }
    TextButton(onClick = onSkip) {
        Text(stringResource(R.string.cycle_quick_log_skip))
    }
}

@Composable
private fun EnergyDot(rating: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier,
        ) {
            Text(
                text = rating.toString(),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LoggedState(uiState: QuickCycleLogUiState, onToggleSymptoms: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.cycle_quick_log_logged),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        if (!uiState.showSymptomPicker) {
            TextButton(onClick = onToggleSymptoms) {
                Text(stringResource(R.string.cycle_quick_log_add_symptoms))
            }
        }
    }
}

@Composable
private fun SymptomPicker(selected: Set<String>, onSubmit: (Set<String>) -> Unit) {
    var picked by remember(selected) { mutableStateOf(selected) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CYCLE_SYMPTOM_KEYS.forEach { key ->
            val labelRes = SYMPTOM_LABELS.getValue(key)
            FilterChip(
                selected = key in picked,
                onClick = {
                    picked = if (key in picked) picked - key else picked + key
                },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
    OutlinedButton(onClick = { onSubmit(picked) }) {
        Text(stringResource(R.string.cycle_quick_log_symptoms_done))
    }
}
