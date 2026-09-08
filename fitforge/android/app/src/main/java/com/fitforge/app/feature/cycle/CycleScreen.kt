package com.fitforge.app.feature.cycle

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.R
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

private val TRACKING_MODES = listOf(
    "natural" to R.string.cycle_tracking_mode_natural,
    "hormonal_contraception" to R.string.cycle_tracking_mode_hormonal_contraception,
    "pregnant" to R.string.cycle_tracking_mode_pregnant,
    "postpartum" to R.string.cycle_tracking_mode_postpartum,
    "perimenopause" to R.string.cycle_tracking_mode_perimenopause,
    "not_cycling" to R.string.cycle_tracking_mode_not_cycling,
    "unknown" to R.string.cycle_tracking_mode_unknown,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleScreen(viewModel: CycleScreenViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.cycle_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (uiState.energyAvailabilityFlag != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cycle_energy_availability_suggestion),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            ForecastSection(uiState)
            PatternSection(uiState)
            CalendarSection(uiState = uiState, onLogStart = viewModel::logPeriodStart, onLogEnd = viewModel::logPeriodEnd, onLogSpotting = viewModel::logSpotting)
            TrackingModeSection(selected = uiState.trackingMode, onSelect = viewModel::setTrackingMode)
            DiscreetModeSection(enabled = uiState.discreetMode, onToggle = viewModel::setDiscreetMode)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val result = viewModel.export()
                        result.onSuccess { export ->
                            val json = Json { prettyPrint = true }.encodeToString(export)
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_TEXT, json)
                                    },
                                    null,
                                ),
                            )
                            statusMessage = context.getString(R.string.cycle_export_success)
                        }.onFailure {
                            statusMessage = context.getString(R.string.cycle_export_failure, it.message ?: "")
                        }
                    }
                }) {
                    Text(stringResource(R.string.cycle_export_data))
                }

                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text(stringResource(R.string.cycle_delete_data), color = MaterialTheme.colorScheme.error)
                }
            }

            statusMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.cycle_delete_data)) },
            text = { Text(stringResource(R.string.cycle_delete_data_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        val result = viewModel.deleteAllData()
                        statusMessage = result.fold(
                            onSuccess = { context.getString(R.string.cycle_delete_success) },
                            onFailure = { context.getString(R.string.cycle_delete_failure, it.message ?: "") },
                        )
                    }
                }) {
                    Text(stringResource(R.string.cycle_delete_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cycle_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun ForecastSection(uiState: CycleUiState) {
    SectionCard(title = stringResource(R.string.cycle_section_forecast)) {
        if (uiState.daysUntilPredictedPeriod != null) {
            Text(stringResource(R.string.cycle_forecast_days_until, uiState.daysUntilPredictedPeriod))
        } else {
            Text(stringResource(R.string.cycle_forecast_unavailable), style = MaterialTheme.typography.bodySmall)
        }
        if (uiState.phase != null) {
            Text(stringResource(R.string.cycle_phase_label, uiState.phase))
        } else {
            Text(stringResource(R.string.cycle_phase_unavailable), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PatternSection(uiState: CycleUiState) {
    SectionCard(title = stringResource(R.string.cycle_section_pattern)) {
        val sampleCycles = uiState.learnedPatternSampleCycles
        val confidence = uiState.learnedPatternConfidence
        if (sampleCycles != null && confidence != null) {
            Text(
                stringResource(R.string.cycle_pattern_sample_size, sampleCycles, (confidence * 100).roundToInt()),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(stringResource(R.string.cycle_pattern_empty), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CalendarSection(
    uiState: CycleUiState,
    onLogStart: () -> Unit,
    onLogEnd: () -> Unit,
    onLogSpotting: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.cycle_section_calendar)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLogStart) { Text(stringResource(R.string.cycle_log_period_start)) }
            OutlinedButton(onClick = onLogEnd) { Text(stringResource(R.string.cycle_log_period_end)) }
            OutlinedButton(onClick = onLogSpotting) { Text(stringResource(R.string.cycle_log_spotting)) }
        }
        if (uiState.periodStarts.isEmpty()) {
            Text(stringResource(R.string.cycle_calendar_no_periods_logged), style = MaterialTheme.typography.bodySmall)
        } else {
            uiState.periodStarts.take(6).forEach { date ->
                Text(
                    stringResource(R.string.cycle_calendar_period_start_on, date.toString()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TrackingModeSection(selected: String?, onSelect: (String) -> Unit) {
    SectionCard(title = stringResource(R.string.cycle_tracking_mode_label)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TRACKING_MODES.forEach { (mode, labelRes) ->
                FilterChip(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun DiscreetModeSection(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cycle_discreet_mode_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(stringResource(R.string.cycle_discreet_mode_desc), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            content()
        }
    }
}
