package com.fitforge.app.feature.fitnessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.data.repository.CachedUserState
import com.fitforge.app.data.repository.FitnessAgeInputsDomain
import com.fitforge.app.data.repository.UserStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface FitnessAgeUiState {
    data object Missing : FitnessAgeUiState

    data class Available(
        val fitnessAge: Double,
        val inputs: FitnessAgeInputsDomain?,
    ) : FitnessAgeUiState
}

/** Pure — no repository, no ViewModel — so the display-vs-missing decision
 * is unit-testable on its own, same shape as computeQuickCycleLogUiState /
 * computeMusicRecommendation. */
internal fun computeFitnessAgeUiState(state: CachedUserState?): FitnessAgeUiState {
    val fitnessAge = state?.body?.fitnessAge ?: return FitnessAgeUiState.Missing
    return FitnessAgeUiState.Available(fitnessAge = fitnessAge, inputs = state.body.fitnessAgeInputs)
}

@HiltViewModel
class FitnessAgeViewModel @Inject constructor(
    userStateRepository: UserStateRepository,
) : ViewModel() {

    val uiState: StateFlow<FitnessAgeUiState> = userStateRepository.userState
        .map { computeFitnessAgeUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FitnessAgeUiState.Missing)
}
