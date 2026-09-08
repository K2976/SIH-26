package com.fitforge.app.feature.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.data.remote.generated.models.CycleExport
import com.fitforge.app.data.repository.CycleRepository
import com.fitforge.app.data.repository.UserStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import javax.inject.Inject

data class CycleUiState(
    val trackingMode: String? = null,
    val phase: String? = null,
    val cycleDay: Int? = null,
    val phaseConfidence: Double? = null,
    val daysUntilPredictedPeriod: Int? = null,
    val energyAvailabilityFlag: String? = null,
    val learnedPatternSampleCycles: Int? = null,
    val learnedPatternConfidence: Double? = null,
    val periodStarts: List<LocalDate> = emptyList(),
    val discreetMode: Boolean = false,
)

/** Backs the calendar/forecast/pattern screen (§10). Everything here is
 * display, forecast, and pattern-analysis only — none of it is a budget
 * input; see apps/engine/budgets.py's readiness_budget docstring. */
@HiltViewModel
class CycleScreenViewModel @Inject constructor(
    private val cycleRepository: CycleRepository,
    userStateRepository: UserStateRepository,
) : ViewModel() {

    val uiState: StateFlow<CycleUiState> = combine(
        userStateRepository.userState,
        cycleRepository.periodStarts(),
        cycleRepository.discreetMode,
    ) { state, starts, discreet ->
        val cycle = state?.cycle
        CycleUiState(
            trackingMode = cycle?.trackingMode,
            phase = cycle?.phase,
            cycleDay = cycle?.cycleDay,
            phaseConfidence = cycle?.confidence,
            daysUntilPredictedPeriod = cycle?.daysUntilPredictedPeriod,
            energyAvailabilityFlag = cycle?.energyAvailabilityFlag,
            learnedPatternSampleCycles = cycle?.learnedPatternSampleCycles,
            learnedPatternConfidence = cycle?.learnedPatternConfidence,
            periodStarts = starts,
            discreetMode = discreet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CycleUiState())

    fun logPeriodStart() {
        viewModelScope.launch { cycleRepository.logPeriodEvent(cycleRepository.today(), "period_start") }
    }

    fun logPeriodEnd() {
        viewModelScope.launch { cycleRepository.logPeriodEvent(cycleRepository.today(), "period_end") }
    }

    fun logSpotting() {
        viewModelScope.launch { cycleRepository.logPeriodEvent(cycleRepository.today(), "spotting") }
    }

    fun setDiscreetMode(value: Boolean) {
        viewModelScope.launch { cycleRepository.setDiscreetMode(value) }
    }

    fun setTrackingMode(mode: String) {
        viewModelScope.launch { cycleRepository.setTrackingMode(mode) }
    }

    suspend fun export(): Result<CycleExport> = cycleRepository.exportData()

    suspend fun deleteAllData(): Result<Unit> = cycleRepository.deleteAllData()
}
