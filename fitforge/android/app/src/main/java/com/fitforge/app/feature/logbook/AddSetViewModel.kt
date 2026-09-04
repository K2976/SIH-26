package com.fitforge.app.feature.logbook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.core.database.ExerciseEntity
import com.fitforge.app.core.database.SetLogEntity
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.core.units.lbToKg
import com.fitforge.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val REST_SECONDS = 90

@HiltViewModel
class AddSetViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    preferences: UserPreferencesDataStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionUuid: String = checkNotNull(savedStateHandle["sessionUuid"])
    private val exerciseSlug: String = checkNotNull(savedStateHandle["slug"])

    val unitPreference: StateFlow<String> = preferences.unitPreference
        .map { it ?: "metric" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "metric")

    val exercise: StateFlow<ExerciseEntity?> = flow { emit(workoutRepository.exerciseBySlug(exerciseSlug)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val loggedSets: StateFlow<List<SetLogEntity>> = workoutRepository.setsForSession(sessionUuid)
        .map { sets -> sets.filter { it.exerciseSlug == exerciseSlug } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var reps by mutableStateOf("")
        private set
    var weight by mutableStateOf("")
        private set
    var isWarmup by mutableStateOf(false)
        private set

    private val _restSecondsRemaining = MutableStateFlow<Int?>(null)
    val restSecondsRemaining: StateFlow<Int?> = _restSecondsRemaining.asStateFlow()
    private var restJob: Job? = null

    fun onRepsChanged(value: String) {
        reps = value
    }

    fun onWeightChanged(value: String) {
        weight = value
    }

    fun onWarmupChanged(value: Boolean) {
        isWarmup = value
    }

    fun logSet() {
        val repsInt = reps.toIntOrNull() ?: return
        val enteredWeight = weight.toDoubleOrNull()
        val weightKg = when {
            enteredWeight == null -> null
            unitPreference.value == "imperial" -> lbToKg(enteredWeight)
            else -> enteredWeight
        }
        viewModelScope.launch {
            workoutRepository.addSet(sessionUuid, exerciseSlug, repsInt, weightKg, isWarmup)
            reps = ""
            weight = ""
            isWarmup = false
            startRestTimer()
        }
    }

    private fun startRestTimer() {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            var remaining = REST_SECONDS
            _restSecondsRemaining.value = remaining
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _restSecondsRemaining.value = remaining
            }
            _restSecondsRemaining.value = null
        }
    }

    fun skipRest() {
        restJob?.cancel()
        _restSecondsRemaining.value = null
    }
}
