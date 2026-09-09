@file:Suppress("MagicNumber")

package com.fitforge.app.feature.logbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.core.database.SetLogEntity
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    preferences: UserPreferencesDataStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionUuid: String = checkNotNull(savedStateHandle["sessionUuid"])

    val unitPreference: StateFlow<String> = preferences.unitPreference
        .map { it ?: "metric" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "metric")

    val sets: StateFlow<List<SetLogEntity>> = workoutRepository.setsForSession(sessionUuid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exerciseNames: StateFlow<Map<String, String>> = sets
        .map { list -> list.map { it.exerciseSlug }.distinct() }
        .distinctUntilChanged()
        .map { slugs -> slugs.associateWith { slug -> workoutRepository.exerciseBySlug(slug)?.name ?: slug } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}
