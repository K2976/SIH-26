package com.fitforge.app.feature.logbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
fun SessionDetailScreen(
    onBack: () -> Unit,
    onExerciseSelected: (String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val sets by viewModel.sets.collectAsStateWithLifecycle()
    val exerciseNames by viewModel.exerciseNames.collectAsStateWithLifecycle()
    val unitPreference by viewModel.unitPreference.collectAsStateWithLifecycle()
    val isImperial = unitPreference == "imperial"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_session_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        val grouped = sets.groupBy { it.exerciseSlug }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp)) {
            grouped.forEach { (slug, exerciseSets) ->
                item(key = "header_$slug") {
                    Text(
                        text = exerciseNames[slug] ?: slug,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExerciseSelected(slug) }
                            .padding(top = 12.dp),
                    )
                }
                items(exerciseSets, key = { it.diary.clientUuid }) { set ->
                    Text(setRowText(set, isImperial), modifier = Modifier.padding(start = 8.dp, top = 2.dp))
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
