package com.fitforge.app.feature.logbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.R

@Composable
fun ExercisePickerScreen(
    onExerciseSelected: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExercisePickerViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.onboarding_cancel)) }
        Text(stringResource(R.string.log_picker_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChanged,
            label = { Text(stringResource(R.string.log_picker_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (results.isEmpty()) {
            Text(
                stringResource(R.string.log_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(results, key = { it.slug }) { exercise ->
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExerciseSelected(exercise.slug) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
