@file:Suppress("UnusedParameter", "LongMethod")

package com.fitforge.app.feature.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.nav_log),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedButton(onClick = onLogFood, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.food_log_meal), modifier = Modifier.padding(start = 8.dp))
        }

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
            Button(onClick = viewModel::startSession, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.log_start_workout), modifier = Modifier.padding(start = 8.dp))
            }
        }

        Text(
            stringResource(R.string.log_history_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (pastSessions.isEmpty()) {
            Text(
                stringResource(R.string.log_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.log_start_workout),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (metrics != null) {
                Text(
                    stringResource(R.string.log_elapsed_label, metrics.elapsedMinutes),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    if (unitPreference == "imperial") {
                        stringResource(
                            R.string.log_volume_label_lb,
                            com.fitforge.app.core.units.kgToLb(metrics.volumeKg),
                        )
                    } else {
                        stringResource(R.string.log_volume_label, metrics.volumeKg)
                    },
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (metrics.estimatedKcal != null) {
                    Text(
                        stringResource(R.string.log_burn_label, metrics.estimatedKcal),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else if (!metrics.bodyweightKnown) {
                    Text(
                        stringResource(R.string.log_burn_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(
                    R.string.log_session_row,
                    "${localDateTime.date}",
                    session.durationMinutes ?: 0,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
