@file:Suppress("LongMethod", "MagicNumber")

package com.fitforge.app.feature.logbook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.R
import com.fitforge.app.core.database.SetLogEntity

@Composable
fun AddSetScreen(
    onDone: () -> Unit,
    onUseCamera: () -> Unit,
    viewModel: AddSetViewModel = hiltViewModel(),
) {
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    val loggedSets by viewModel.loggedSets.collectAsStateWithLifecycle()
    val unitPreference by viewModel.unitPreference.collectAsStateWithLifecycle()
    val restSecondsRemaining by viewModel.restSecondsRemaining.collectAsStateWithLifecycle()
    val isImperial = unitPreference == "imperial"

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(exercise?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)

        if (exercise?.poseAngleFrom != null) {
            OutlinedButton(onClick = onUseCamera, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.log_use_camera))
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            items(loggedSets, key = { it.diary.clientUuid }) { set ->
                SetLogRow(set = set, isImperial = isImperial)
            }
        }

        restSecondsRemaining?.let { remaining ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(stringResource(R.string.log_rest_timer_label, remaining / 60, remaining % 60))
                OutlinedButton(onClick = viewModel::skipRest, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.log_skip_rest))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = viewModel.reps,
                onValueChange = viewModel::onRepsChanged,
                label = { Text(stringResource(R.string.log_reps_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = viewModel.weight,
                onValueChange = viewModel::onWeightChanged,
                label = {
                    Text(
                        stringResource(
                            if (isImperial) R.string.log_weight_label_lb else R.string.log_weight_label,
                        ),
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Checkbox(checked = viewModel.isWarmup, onCheckedChange = viewModel::onWarmupChanged)
            Text(stringResource(R.string.log_warmup_label), modifier = Modifier.padding(top = 12.dp))
        }

        Button(
            onClick = viewModel::logSet,
            enabled = viewModel.reps.toIntOrNull() != null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.log_log_set))
        }

        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.log_done_exercise))
        }
    }
}

@Composable
private fun SetLogRow(set: SetLogEntity, isImperial: Boolean) {
    val text = if (set.weightKg != null) {
        if (isImperial) {
            stringResource(
                R.string.log_set_row_lb,
                set.setIndex,
                set.reps,
                com.fitforge.app.core.units.kgToLb(set.weightKg),
            )
        } else {
            stringResource(R.string.log_set_row, set.setIndex, set.reps, set.weightKg)
        }
    } else {
        stringResource(R.string.log_set_row_bodyweight, set.setIndex, set.reps)
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
}
