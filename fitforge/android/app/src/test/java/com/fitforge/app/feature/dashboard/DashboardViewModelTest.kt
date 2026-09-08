package com.fitforge.app.feature.dashboard

import com.fitforge.app.data.repository.BodyDomain
import com.fitforge.app.data.repository.CachedUserState
import com.fitforge.app.data.repository.CycleDomain
import com.fitforge.app.data.repository.EnergyDomain
import com.fitforge.app.data.repository.FlagsDomain
import com.fitforge.app.data.repository.GoalDomain
import com.fitforge.app.data.repository.PlanDeltaDomain
import com.fitforge.app.data.repository.ProfileDomain
import com.fitforge.app.data.repository.TrainingDomain
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelTest {

    @Test
    fun `brand new account with null cache maps to coherent empty states and never zeroes`() {
        val state = mapUserStateToUiState(null)
        assertNull(state.lastSyncedAt)

        // Today section is Empty with explanation, NOT zero
        assertTrue(state.today is TodaySection.Empty)
        val todayEmpty = state.today as TodaySection.Empty
        assertEquals("No energy targets yet", todayEmpty.title)

        // Plan delta is Inactive with explanation
        assertTrue(state.planDelta is PlanDeltaSection.Inactive)
        val deltaInactive = state.planDelta as PlanDeltaSection.Inactive
        assertTrue(deltaInactive.message.contains("baseline plan"))

        // Week is Empty
        assertTrue(state.week is WeekSection.Empty)

        // Trends is Empty
        assertTrue(state.trends is TrendsSection.Empty)
    }

    @Test
    fun `sparse user state with no energy targets shows empty today state`() {
        val sparse = CachedUserState(
            asOfDate = "2026-09-05",
            computedAt = Instant.fromEpochMilliseconds(1700000000000L),
            profile = ProfileDomain(
                age = null,
                sexAtBirth = null,
                heightCm = null,
                unitPreference = null,
                tracksCycle = false,
                timezone = "UTC",
            ),
            goal = GoalDomain(
                energyDirection = null,
                trainingFocus = null,
                targetRateKgPerWeek = null,
                autoAdjust = null,
            ),
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
                fitnessAge = null,
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
                missingFoodDays7d = 7,
                missingWeightDays14d = 14,
                goalRecentlyChanged = false,
                insufficientDataForAdaptation = true,
            ),
        )

        val state = mapUserStateToUiState(sparse)
        assertEquals(Instant.fromEpochMilliseconds(1700000000000L), state.lastSyncedAt)
        assertTrue(state.today is TodaySection.Empty)
        assertTrue(state.planDelta is PlanDeltaSection.Inactive)
        assertTrue(state.week is WeekSection.Empty)
        assertTrue(state.trends is TrendsSection.Empty)
    }

    @Test
    fun `user state with active data populates all dashboard sections correctly`() {
        val cached = CachedUserState(
            asOfDate = "2026-09-05",
            computedAt = Instant.fromEpochMilliseconds(1700000000000L),
            profile = ProfileDomain(
                age = 30,
                sexAtBirth = "male",
                heightCm = 182.0,
                unitPreference = "metric",
                tracksCycle = false,
                timezone = "UTC",
            ),
            goal = GoalDomain(
                energyDirection = "cut",
                trainingFocus = "hypertrophy",
                targetRateKgPerWeek = 0.5,
                autoAdjust = true,
            ),
            energy = EnergyDomain(
                maintenanceKcal = 2400.0,
                maintenanceSource = "equation",
                maintenanceConfidence = 0.75,
                targetKcalToday = 1900.0,
                consumedKcalToday = 850.0,
                consumedKcalTodayPartial = false,
                burnedKcalToday = 350.0,
                burnedKcalTodayPartial = false,
                weekBalanceKcal = -150.0,
                weekBalanceKcalPartial = false,
                dataSufficiency = "sufficient",
            ),
            training = TrainingDomain(
                weekVolumePlanned = 12000.0,
                weekVolumePlannedPartial = false,
                weekVolumeCompleted = 4500.0,
                weekVolumeCompletedPartial = false,
                volumeByMuscleGroup = mapOf("chest" to 2500.0, "back" to 2000.0),
                volumeByMuscleGroupPartial = false,
                adherence7d = 0.85,
                adherence7dPartial = false,
                readinessCeilingToday = 0.92,
                daysSinceRest = 1,
                daysSinceRestPartial = false,
            ),
            planDelta = PlanDeltaDomain(
                targetKcal = 1880.0,
                volumeTodayKg = 2200.0,
                readinessCeiling = 0.92,
                droppedSessions = 0,
                reason = "cut @ 0.50 kg/wk; adherence damped adjustment",
                clampedBy = listOf("min_daily_kcal (1500)"),
            ),
            body = BodyDomain(
                latestWeightKg = 82.3,
                weightTrendKgPerWeek = -0.45,
                trendConfidence = 0.85,
                fitnessAge = null,
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
                missingFoodDays7d = 1,
                missingWeightDays14d = 1,
                goalRecentlyChanged = false,
                insufficientDataForAdaptation = false,
            ),
        )

        val state = mapUserStateToUiState(cached)
        assertEquals(Instant.fromEpochMilliseconds(1700000000000L), state.lastSyncedAt)

        // Today Data
        assertTrue(state.today is TodaySection.Data)
        val today = state.today as TodaySection.Data
        assertEquals(1900.0, today.targetKcal)
        assertEquals(850.0, today.consumedKcal)
        assertEquals(350.0, today.burnedKcal)
        // remaining = target (1900) - consumed (850) + burned (350) = 1400.0
        assertEquals(1400.0, today.remainingKcal)
        assertEquals("equation", today.maintenanceSource)
        assertEquals(0.75, today.maintenanceConfidence)

        // PlanDelta Active
        assertTrue(state.planDelta is PlanDeltaSection.Active)
        val planDelta = state.planDelta as PlanDeltaSection.Active
        assertEquals(1880.0, planDelta.targetKcal)
        assertEquals(2200.0, planDelta.volumeTodayKg)
        assertEquals("cut @ 0.50 kg/wk; adherence damped adjustment", planDelta.reason)
        assertEquals(listOf("min_daily_kcal (1500)"), planDelta.clampedBy)

        // Week Data
        assertTrue(state.week is WeekSection.Data)
        val week = state.week as WeekSection.Data
        assertEquals(85, week.adherencePercent)
        assertEquals(4500.0, week.volumeCompletedKg)
        assertEquals(12000.0, week.volumePlannedKg)
        assertEquals(-150.0, week.energyBalanceKcal)

        // Trends Data
        assertTrue(state.trends is TrendsSection.Data)
        val trends = state.trends as TrendsSection.Data
        assertEquals(82.3, trends.latestWeightKg)
        assertEquals(-0.45, trends.weightTrendKgPerWeek)
        assertEquals(0.85, trends.trendConfidence)
        assertNotNull(trends.volumeByMuscleGroup)
        assertEquals(2500.0, trends.volumeByMuscleGroup?.get("chest"))
    }
}
