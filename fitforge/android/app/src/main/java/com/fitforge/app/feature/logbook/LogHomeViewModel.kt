package com.fitforge.app.feature.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.core.database.SetLogEntity
import com.fitforge.app.core.database.WorkoutSessionEntity
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.repository.WorkoutRepository
import com.fitforge.app.domain.workout.SetMetricInput
import com.fitforge.app.domain.workout.estimatedSessionKcal
import com.fitforge.app.domain.workout.sessionVolumeKg
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import javax.inject.Inject

private const val TICK_MILLIS = 60_000L

data class LiveMetrics(val elapsedMinutes: Int, val volumeKg: Double, val estimatedKcal: Double?)

/**
 * Bodyweight comes from the local DataStore cache (populated whenever the
 * profile is fetched elsewhere), never a direct network call — this is a
 * Room-reading screen and must work offline, matching "UI reads Room, never
 * the network directly" and keeping the live burn estimate usable offline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogHomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    preferences: UserPreferencesDataStore,
) : ViewModel() {

    val unitPreference: StateFlow<String> = preferences.unitPreference
        .map { it ?: "metric" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "metric")

    val activeSession: StateFlow<WorkoutSessionEntity?> = workoutRepository.activeSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val history: StateFlow<List<WorkoutSessionEntity>> = workoutRepository.history()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeSets: StateFlow<List<SetLogEntity>> = activeSession
        .flatMapLatest { session ->
            if (session == null) flowOf(emptyList()) else workoutRepository.setsForSession(session.diary.clientUuid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val ticker = flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(TICK_MILLIS)
        }
    }

    val liveMetrics: StateFlow<LiveMetrics?> =
        combine(activeSession, activeSets, preferences.bodyweightKg, ticker) { session, sets, bodyweight, _ ->
            if (session == null) return@combine null
            val elapsedMinutes = (Clock.System.now() - session.diary.occurredAt).inWholeMinutes.toInt()
            val metricInputs = toMetricInputs(sets)
            LiveMetrics(
                elapsedMinutes = elapsedMinutes,
                volumeKg = sessionVolumeKg(metricInputs),
                estimatedKcal = estimatedSessionKcal(metricInputs, bodyweight, elapsedMinutes.toDouble()),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private suspend fun toMetricInputs(sets: List<SetLogEntity>): List<SetMetricInput> {
        val metBySlug = sets.map { it.exerciseSlug }.distinct()
            .associateWith { slug -> workoutRepository.exerciseBySlug(slug)?.metValue ?: 0.0 }
        return sets.map { SetMetricInput(it.weightKg, it.reps, it.isWarmup, metBySlug[it.exerciseSlug] ?: 0.0) }
    }

    fun startSession() {
        viewModelScope.launch { workoutRepository.startSession() }
    }

    fun finishSession(sessionUuid: String) {
        viewModelScope.launch {
            workoutRepository.finishSession(sessionUuid)
        }
    }
}
