package com.fitforge.app.feature.fitnessage

import com.fitforge.app.data.repository.BodyDomain
import com.fitforge.app.data.repository.CachedUserState
import com.fitforge.app.data.repository.CycleDomain
import com.fitforge.app.data.repository.EnergyDomain
import com.fitforge.app.data.repository.FitnessAgeInputsDomain
import com.fitforge.app.data.repository.FlagsDomain
import com.fitforge.app.data.repository.GoalDomain
import com.fitforge.app.data.repository.ProfileDomain
import com.fitforge.app.data.repository.TrainingDomain
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun state(fitnessAge: Double?, inputs: FitnessAgeInputsDomain? = null) = CachedUserState(
    asOfDate = "2026-09-08",
    computedAt = Instant.fromEpochMilliseconds(0),
    profile = ProfileDomain(null, null, null, null, false, "UTC"),
    goal = GoalDomain(null, null, null, null),
    energy = EnergyDomain(
        maintenanceKcal = null,
        maintenanceSource = null,
        maintenanceConfidence = null,
        targetKcalToday = null,
        consumedKcalToday = null,
        consumedKcalTodayPartial = false,
        burnedKcalToday = null,
        burnedKcalTodayPartial = false,
        weekBalanceKcal = null,
        weekBalanceKcalPartial = false,
        dataSufficiency = "insufficient",
    ),
    training = TrainingDomain(
        weekVolumePlanned = null,
        weekVolumePlannedPartial = false,
        weekVolumeCompleted = null,
        weekVolumeCompletedPartial = false,
        volumeByMuscleGroup = null,
        volumeByMuscleGroupPartial = false,
        adherence7d = null,
        adherence7dPartial = false,
        readinessCeilingToday = null,
        daysSinceRest = null,
        daysSinceRestPartial = false,
    ),
    planDelta = null,
    body = BodyDomain(
        latestWeightKg = null,
        weightTrendKgPerWeek = null,
        trendConfidence = null,
        fitnessAge = fitnessAge,
        fitnessAgeInputs = inputs,
    ),
    cycle = CycleDomain(
        enabled = false,
        trackingMode = null,
        phase = null,
        cycleDay = null,
        confidence = null,
        daysUntilPredictedPeriod = null,
        energyAvailabilityFlag = null,
        learnedPatternSampleCycles = null,
        learnedPatternConfidence = null,
    ),
    flags = FlagsDomain(
        missingFoodDays7d = 0,
        missingWeightDays14d = 0,
        goalRecentlyChanged = false,
        insufficientDataForAdaptation = true,
    ),
)

class FitnessAgeViewModelTest {

    @Test
    fun `null cached state shows missing`() {
        assertTrue(computeFitnessAgeUiState(null) is FitnessAgeUiState.Missing)
    }

    @Test
    fun `null fitness age shows missing, not zero or a guess`() {
        val result = computeFitnessAgeUiState(state(fitnessAge = null))
        assertTrue(result is FitnessAgeUiState.Missing)
    }

    @Test
    fun `present fitness age is shown with its inputs`() {
        val inputs = FitnessAgeInputsDomain(
            mets = 12.19,
            vo2maxMlKgMin = 42.67,
            age = 40,
            sexAtBirth = "male",
            bmi = 26.2,
            restingHr = 65,
            activityLevel = "very_active",
            modelCitation = "Jurca et al. 2005",
            normsCitation = "Kaminsky et al. 2015",
        )
        val result = computeFitnessAgeUiState(state(fitnessAge = 34.0, inputs = inputs))
        assertTrue(result is FitnessAgeUiState.Available)
        val available = result as FitnessAgeUiState.Available
        assertEquals(34.0, available.fitnessAge, 0.0)
        assertEquals(inputs, available.inputs)
    }
}
