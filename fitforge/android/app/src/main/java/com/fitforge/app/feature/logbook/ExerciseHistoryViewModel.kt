@file:Suppress("MagicNumber")

package com.fitforge.app.feature.logbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.core.database.ExerciseEntity
import com.fitforge.app.core.database.SetLogEntity
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    preferences: UserPreferencesDataStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val slug: String = checkNotNull(savedStateHandle["slug"])

    val unitPreference: StateFlow<String> = preferences.unitPreference
        .map { it ?: "metric" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "metric")

    val exercise: StateFlow<ExerciseEntity?> = flow { emit(workoutRepository.exerciseBySlug(slug)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sets: StateFlow<List<SetLogEntity>> = workoutRepository.historyForExercise(slug)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personalRecord: StateFlow<Double?> = workoutRepository.personalRecord(slug)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
