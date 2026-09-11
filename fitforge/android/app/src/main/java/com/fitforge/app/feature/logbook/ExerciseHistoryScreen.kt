package com.fitforge.app.feature.logbook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.R
import com.fitforge.app.core.database.SetLogEntity
import com.fitforge.app.core.units.kgToLb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistoryScreen(onBack: () -> Unit, viewModel: ExerciseHistoryViewModel = hiltViewModel()) {
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    val sets by viewModel.sets.collectAsStateWithLifecycle()
    val personalRecord by viewModel.personalRecord.collectAsStateWithLifecycle()
    val unitPreference by viewModel.unitPreference.collectAsStateWithLifecycle()
    val isImperial = unitPreference == "imperial"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_exercise_history_title, exercise?.name.orEmpty())) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            val pr = personalRecord
            Text(
                text = if (pr != null) {
                    if (isImperial) {
                        stringResource(R.string.log_pr_label_lb, kgToLb(pr))
                    } else {
                        stringResource(R.string.log_pr_label, pr)
                    }
                } else {
                    stringResource(R.string.log_pr_none)
                },
                style = MaterialTheme.typography.titleMedium,
            )

            if (sets.isEmpty()) {
                Text(
                    stringResource(R.string.log_exercise_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                    items(sets, key = { it.diary.clientUuid }) { set ->
                        Text(setRowText(set, isImperial), modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun setRowText(set: SetLogEntity, isImperial: Boolean): String = if (set.weightKg != null) {
    if (isImperial) {
        stringResource(R.string.log_set_row_lb, set.setIndex, set.reps, kgToLb(set.weightKg))
    } else {
        stringResource(R.string.log_set_row, set.setIndex, set.reps, set.weightKg)
    }
} else {
    stringResource(R.string.log_set_row_bodyweight, set.setIndex, set.reps)
}
