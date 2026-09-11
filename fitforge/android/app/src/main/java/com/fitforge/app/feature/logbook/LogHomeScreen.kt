@file:Suppress("UnusedParameter")

package com.fitforge.app.feature.logbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.R
import com.fitforge.app.core.database.WorkoutSessionEntity
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun LogHomeScreen(
    onAddExercise: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenExerciseHistory: (String) -> Unit,
    onLogFood: () -> Unit,
    viewModel: LogHomeViewModel = hiltViewModel(),
) {
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val liveMetrics by viewModel.liveMetrics.collectAsStateWithLifecycle()
    val unitPreference by viewModel.unitPreference.collectAsStateWithLifecycle()

    val pastSessions = history.filter { it.durationMinutes != null }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Button(onClick = onLogFood, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.food_log_meal))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        val session = activeSession
        if (session != null) {
            ActiveSessionCard(
                session = session,
                metrics = liveMetrics,
                unitPreference = unitPreference,
                onAddExercise = { onAddExercise(session.diary.clientUuid) },
                onFinish = { viewModel.finishSession(session.diary.clientUuid) },
            )
        } else {
            // No navigation needed — activeSession is a live Flow, so this
            // screen re-renders the ActiveSessionCard in place once the row lands.
            Button(
                onClick = viewModel::startSession,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.log_start_workout))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text(stringResource(R.string.log_history_title), style = MaterialTheme.typography.titleMedium)
        if (pastSessions.isEmpty()) {
            Text(
                stringResource(R.string.log_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(pastSessions, key = { it.diary.clientUuid }) { pastSession ->
                    SessionRow(session = pastSession, onClick = { onOpenSession(pastSession.diary.clientUuid) })
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(
    session: WorkoutSessionEntity,
    metrics: LiveMetrics?,
    unitPreference: String,
    onAddExercise: () -> Unit,
    onFinish: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (metrics != null) {
                Text(stringResource(R.string.log_elapsed_label, metrics.elapsedMinutes))
                Text(
                    if (unitPreference == "imperial") {
                        stringResource(
                            R.string.log_volume_label_lb,
                            com.fitforge.app.core.units.kgToLb(metrics.volumeKg),
                        )
                    } else {
                        stringResource(R.string.log_volume_label, metrics.volumeKg)
                    },
                )
                if (metrics.estimatedKcal != null) {
                    Text(stringResource(R.string.log_burn_label, metrics.estimatedKcal))
                } else if (!metrics.bodyweightKnown) {
                    // Only a real warning when bodyweight is truly missing — no
                    // sets logged yet is a normal, expected start-of-session state.
                    Text(stringResource(R.string.log_burn_unavailable), style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = onAddExercise, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(stringResource(R.string.log_add_exercise))
            }
            OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.log_finish_session))
            }
        }
    }
}

@Composable
private fun SessionRow(session: WorkoutSessionEntity, onClick: () -> Unit) {
    val localDateTime = session.diary.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault())
    Text(
        text = stringResource(
            R.string.log_session_row,
            "${localDateTime.date}",
            session.durationMinutes ?: 0,
        ),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}
