@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "LongMethod", "MagicNumber")

package com.fitforge.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.data.repository.CachedUserState
import com.fitforge.app.data.repository.UserStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import javax.inject.Inject

sealed interface TodaySection {
    data object Loading : TodaySection

    data class Empty(
        val title: String,
        val message: String,
    ) : TodaySection

    data class Data(
        val targetKcal: Double?,
        val consumedKcal: Double?,
        val isConsumedPartial: Boolean,
        val burnedKcal: Double?,
        val isBurnedPartial: Boolean,
        val remainingKcal: Double?,
        val isOverTarget: Boolean,
        val maintenanceKcal: Double?,
        val maintenanceSource: String?,
        val maintenanceConfidence: Double?,
    ) : TodaySection
}

sealed interface PlanDeltaSection {
    data object Loading : PlanDeltaSection

    data class Inactive(
        val message: String,
    ) : PlanDeltaSection

    data class Active(
        val targetKcal: Double?,
        val volumeTodayKg: Double?,
        val readinessCeiling: Double?,
        val droppedSessions: Int?,
        val reason: String,
        val clampedBy: List<String>,
    ) : PlanDeltaSection
}

sealed interface WeekSection {
    data object Loading : WeekSection

    data class Empty(
        val message: String,
    ) : WeekSection

    data class Data(
        val adherencePercent: Int?,
        val isAdherencePartial: Boolean,
        val volumeCompletedKg: Double?,
        val isVolumeCompletedPartial: Boolean,
        val volumePlannedKg: Double?,
        val isVolumePlannedPartial: Boolean,
        val energyBalanceKcal: Double?,
        val isEnergyBalancePartial: Boolean,
    ) : WeekSection
}

sealed interface TrendsSection {
    data object Loading : TrendsSection

    data class Empty(
        val message: String,
    ) : TrendsSection

    data class Data(
        val latestWeightKg: Double?,
        val weightTrendKgPerWeek: Double?,
        val trendConfidence: Double?,
        val volumeByMuscleGroup: Map<String, Double>?,
        val isMuscleVolumePartial: Boolean,
    ) : TrendsSection
}

data class DashboardUiState(
    val isRefreshing: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val today: TodaySection = TodaySection.Loading,
    val planDelta: PlanDeltaSection = PlanDeltaSection.Loading,
    val week: WeekSection = WeekSection.Loading,
    val trends: TrendsSection = TrendsSection.Loading,
    val errorMessage: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userStateRepository: UserStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userStateRepository.userState.collect { cachedState ->
                updateFromState(cachedState)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = userStateRepository.refresh()
            _uiState.update { current ->
                current.copy(
                    isRefreshing = false,
                    errorMessage = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    internal fun updateFromState(state: CachedUserState?) {
        _uiState.update { current ->
            val mapped = mapUserStateToUiState(state)
            mapped.copy(
                isRefreshing = current.isRefreshing,
                errorMessage = current.errorMessage,
            )
        }
    }
}

internal fun mapUserStateToUiState(state: CachedUserState?): DashboardUiState {
    if (state == null) {
        return DashboardUiState(
            lastSyncedAt = null,
            today = TodaySection.Empty(
                title = "No energy targets yet",
                message = "Set a goal in your profile and log meals to see your daily calorie and burn targets.",
            ),
            planDelta = PlanDeltaSection.Inactive(
                message = "No active adjustments. Targets are running on your baseline plan.",
            ),
            week = WeekSection.Empty(
                message = "No weekly data recorded yet. Completed workouts and logged meals will show progress here.",
            ),
            trends = TrendsSection.Empty(
                message = "No trend data available. Consistently log weight and workouts " +
                    "over multiple days to generate trends.",
            ),
        )
    }

    // 1. Today section
    val energy = state.energy
    val todaySection = if (energy.targetKcalToday == null &&
        energy.consumedKcalToday == null &&
        energy.burnedKcalToday == null
    ) {
        TodaySection.Empty(
            title = "No energy targets yet",
            message = "Set a goal in your profile and log meals to see your daily calorie and burn targets.",
        )
    } else {
        val target = energy.targetKcalToday
        val consumed = energy.consumedKcalToday
        val burned = energy.burnedKcalToday
        val remaining = if (target != null && consumed != null) {
            target - consumed + (burned ?: 0.0)
        } else {
            null
        }
        val isOver = remaining != null && remaining < 0.0

        TodaySection.Data(
            targetKcal = target,
            consumedKcal = consumed,
            isConsumedPartial = energy.consumedKcalTodayPartial,
            burnedKcal = burned,
            isBurnedPartial = energy.burnedKcalTodayPartial,
            remainingKcal = remaining,
            isOverTarget = isOver,
            maintenanceKcal = energy.maintenanceKcal,
            maintenanceSource = energy.maintenanceSource,
            maintenanceConfidence = energy.maintenanceConfidence,
        )
    }

    // 2. Plan Delta section
    val delta = state.planDelta
    val planDeltaSection = if (delta != null) {
        PlanDeltaSection.Active(
            targetKcal = delta.targetKcal,
            volumeTodayKg = delta.volumeTodayKg,
            readinessCeiling = delta.readinessCeiling,
            droppedSessions = delta.droppedSessions,
            reason = delta.reason ?: "",
            clampedBy = delta.clampedBy,
        )
    } else {
        PlanDeltaSection.Inactive(
            message = "No active adjustments. Targets are running on your baseline plan.",
        )
    }

    // 3. Week section
    val training = state.training
    val weekSection = if (training.adherence7d == null &&
        training.weekVolumeCompleted == null &&
        training.weekVolumePlanned == null &&
        energy.weekBalanceKcal == null
    ) {
        WeekSection.Empty(
            message = "No weekly data recorded yet. Completed workouts and logged meals will show progress here.",
        )
    } else {
        val adherencePct = training.adherence7d?.let { (it * 100).toInt() }
        WeekSection.Data(
            adherencePercent = adherencePct,
            isAdherencePartial = training.adherence7dPartial,
            volumeCompletedKg = training.weekVolumeCompleted,
            isVolumeCompletedPartial = training.weekVolumeCompletedPartial,
            volumePlannedKg = training.weekVolumePlanned,
            isVolumePlannedPartial = training.weekVolumePlannedPartial,
            energyBalanceKcal = energy.weekBalanceKcal,
            isEnergyBalancePartial = energy.weekBalanceKcalPartial,
        )
    }

    // 4. Trends section
    val body = state.body
    val muscleVolume = training.volumeByMuscleGroup
    val trendsSection = if (body.weightTrendKgPerWeek == null &&
        body.latestWeightKg == null &&
        (muscleVolume == null || muscleVolume.isEmpty())
    ) {
        TrendsSection.Empty(
            message = "No trend data available. Consistently log weight and workouts " +
                "over multiple days to generate trends.",
        )
    } else {
        TrendsSection.Data(
            latestWeightKg = body.latestWeightKg,
            weightTrendKgPerWeek = body.weightTrendKgPerWeek,
            trendConfidence = body.trendConfidence,
            volumeByMuscleGroup = muscleVolume,
            isMuscleVolumePartial = training.volumeByMuscleGroupPartial,
        )
    }

    return DashboardUiState(
        lastSyncedAt = state.computedAt,
        today = todaySection,
        planDelta = planDeltaSection,
        week = weekSection,
        trends = trendsSection,
    )
}
