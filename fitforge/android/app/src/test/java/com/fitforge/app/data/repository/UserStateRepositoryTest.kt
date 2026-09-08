package com.fitforge.app.data.repository

import com.fitforge.app.core.database.UserStateDao
import com.fitforge.app.core.database.UserStateEntity
import com.fitforge.app.data.remote.generated.apis.StateApi
import com.fitforge.app.data.remote.generated.models.BodyState
import com.fitforge.app.data.remote.generated.models.CycleState
import com.fitforge.app.data.remote.generated.models.EnergyState
import com.fitforge.app.data.remote.generated.models.FlagsState
import com.fitforge.app.data.remote.generated.models.GoalState
import com.fitforge.app.data.remote.generated.models.PlanDelta
import com.fitforge.app.data.remote.generated.models.ProfileState
import com.fitforge.app.data.remote.generated.models.TrainingState
import com.fitforge.app.data.remote.generated.models.UserState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private class FakeUserStateDao(initial: UserStateEntity? = null) : UserStateDao {
    val stateFlow = MutableStateFlow(initial)
    var upserted: UserStateEntity? = null

    override fun getLatest(): Flow<UserStateEntity?> = stateFlow

    override suspend fun upsert(entity: UserStateEntity) {
        upserted = entity
        stateFlow.value = entity
    }

    override suspend fun deleteAll() {
        stateFlow.value = null
    }
}

private class FakeStateApi(private val response: Response<UserState>) : StateApi {
    var callCount = 0
    override suspend fun stateRetrieve(): Response<UserState> {
        callCount++
        return response
    }
}

class UserStateRepositoryTest {

    private fun sampleUserState(
        targetKcal: Double? = 2000.0,
        consumedKcal: Double? = 500.0,
        hasPlanDelta: Boolean = true,
    ) = UserState(
        asOf = LocalDate(2026, 9, 5),
        profile = ProfileState(
            age = 28,
            sexAtBirth = "male",
            heightCm = 180.0,
            unitPreference = "metric",
            tracksCycle = false,
            timezone = "UTC",
        ),
        goal = GoalState(
            energyDirection = "cut",
            trainingFocus = "hypertrophy",
            targetRateKgPerWeek = 0.5,
            autoAdjust = true,
        ),
        energy = EnergyState(
            maintenanceKcal = 2500.0,
            maintenanceSource = "equation",
            maintenanceConfidence = 0.8,
            targetKcalToday = targetKcal,
            consumedKcalToday = consumedKcal,
            consumedKcalTodayPartial = false,
            burnedKcalToday = 300.0,
            burnedKcalTodayPartial = false,
            weekBalanceKcal = -200.0,
            weekBalanceKcalPartial = false,
            dataSufficiency = "sufficient",
        ),
        training = TrainingState(
            weekVolumePlanned = 10000.0,
            weekVolumePlannedPartial = false,
            weekVolumeCompleted = 4000.0,
            weekVolumeCompletedPartial = false,
            volumeByMuscleGroup = mapOf("chest" to 2000.0, "back" to 2000.0),
            volumeByMuscleGroupPartial = false,
            adherence7d = 0.85,
            adherence7dPartial = false,
            readinessCeilingToday = 0.9,
            daysSinceRest = 1,
            daysSinceRestPartial = false,
        ),
        planDelta = if (hasPlanDelta) {
            PlanDelta(
                targetKcal = 1950.0,
                volumeTodayKg = 2500.0,
                readinessCeiling = 0.9,
                droppedSessions = 0,
                reason = "Target adjusted based on adherence",
                clampedBy = listOf("min_daily_kcal (1500)"),
            )
        } else {
            null
        },
        body = BodyState(
            latestWeightKg = 78.5,
            weightTrendKgPerWeek = -0.4,
            trendConfidence = 0.8,
            fitnessAge = null,
            fitnessAgeInputs = null,
        ),
        cycle = CycleState(
            enabled = false,
            trackingMode = null,
            phase = null,
            cycleDay = null,
            confidence = null,
            daysUntilPredictedPeriod = null,
            learnedPattern = null,
            energyAvailabilityFlag = null,
        ),
        flags = FlagsState(
            missingFoodDays7d = 1,
            missingWeightDays14d = 2,
            goalRecentlyChanged = false,
            insufficientDataForAdaptation = false,
        ),
    )

    @Test
    fun `userState flow emits cached state from DAO`() = runBlocking {
        val initialEntity = UserStateEntity(
            id = 1,
            computedAt = Instant.fromEpochMilliseconds(1700000000000L),
            asOfDate = "2026-09-05",
            profileAge = 25,
            profileSexAtBirth = "female",
            profileHeightCm = 165.0,
            profileUnitPreference = "metric",
            profileTracksCycle = true,
            profileTimezone = "UTC",
            goalEnergyDirection = "maintain",
            goalTrainingFocus = "strength",
            goalTargetRateKgPerWeek = 0.0,
            goalAutoAdjust = false,
            energyMaintenanceKcal = 1800.0,
            energyMaintenanceSource = "blended",
            energyMaintenanceConfidence = 0.7,
            energyTargetKcalToday = 1800.0,
            energyConsumedKcalToday = 1200.0,
            energyConsumedKcalTodayPartial = false,
            energyBurnedKcalToday = 250.0,
            energyBurnedKcalTodayPartial = false,
            energyWeekBalanceKcal = 0.0,
            energyWeekBalanceKcalPartial = false,
            energyDataSufficiency = "sufficient",
            trainingWeekVolumePlanned = 8000.0,
            trainingWeekVolumePlannedPartial = false,
            trainingWeekVolumeCompleted = 6000.0,
            trainingWeekVolumeCompletedPartial = false,
            trainingVolumeByMuscleGroupJson = "{\"legs\":3000.0}",
            trainingVolumeByMuscleGroupPartial = false,
            trainingAdherence7d = 1.0,
            trainingAdherence7dPartial = false,
            trainingReadinessCeilingToday = 0.95,
            trainingDaysSinceRest = 2,
            trainingDaysSinceRestPartial = false,
            hasPlanDelta = false,
            planDeltaTargetKcal = null,
            planDeltaVolumeTodayKg = null,
            planDeltaReadinessCeiling = null,
            planDeltaDroppedSessions = null,
            planDeltaReason = null,
            planDeltaClampedByJson = null,
            bodyLatestWeightKg = 60.0,
            bodyWeightTrendKgPerWeek = 0.0,
            bodyTrendConfidence = 0.8,
            bodyFitnessAge = null,
            cycleEnabled = true,
            cycleTrackingMode = null,
            cyclePhase = null,
            cycleDay = null,
            cycleConfidence = null,
            cycleDaysUntilPredictedPeriod = null,
            cycleEnergyAvailabilityFlag = null,
            cycleLearnedPatternSampleCycles = null,
            cycleLearnedPatternConfidence = null,
            flagsMissingFoodDays7d = 0,
            flagsMissingWeightDays14d = 0,
            flagsGoalRecentlyChanged = false,
            flagsInsufficientDataForAdaptation = false,
        )

        val dao = FakeUserStateDao(initialEntity)
        val api = FakeStateApi(Response.success(sampleUserState()))
        val repository = UserStateRepository(dao, api)

        val domain = repository.userState.first()
        assertNotNull(domain)
        assertEquals(1800.0, domain!!.energy.targetKcalToday)
        assertEquals("blended", domain.energy.maintenanceSource)
        assertEquals(0.7, domain.energy.maintenanceConfidence)
        assertNull(domain.planDelta)
        assertEquals(3000.0, domain.training.volumeByMuscleGroup?.get("legs"))
    }

    @Test
    fun `refresh fetches from network and updates DAO with PlanDelta`() = runBlocking {
        val dao = FakeUserStateDao(null)
        val api = FakeStateApi(Response.success(sampleUserState()))
        val repository = UserStateRepository(dao, api)

        val result = repository.refresh()
        assertTrue(result.isSuccess)
        assertEquals(1, api.callCount)

        val upserted = dao.upserted
        assertNotNull(upserted)
        assertEquals(2000.0, upserted!!.energyTargetKcalToday)
        assertTrue(upserted.hasPlanDelta)
        assertEquals(1950.0, upserted.planDeltaTargetKcal)
        assertEquals("Target adjusted based on adherence", upserted.planDeltaReason)

        val domain = repository.userState.first()
        assertNotNull(domain)
        assertNotNull(domain!!.planDelta)
        assertEquals(1950.0, domain.planDelta!!.targetKcal)
        assertEquals(listOf("min_daily_kcal (1500)"), domain.planDelta!!.clampedBy)
    }

    @Test
    fun `empty user response with null PlanDelta maps correctly without crashing`() = runBlocking {
        val dao = FakeUserStateDao(null)
        val api = FakeStateApi(
            Response.success(
                sampleUserState(
                    targetKcal = null,
                    consumedKcal = null,
                    hasPlanDelta = false,
                )
            )
        )
        val repository = UserStateRepository(dao, api)

        val result = repository.refresh()
        assertTrue(result.isSuccess)

        val domain = repository.userState.first()
        assertNotNull(domain)
        assertNull(domain!!.energy.targetKcalToday)
        assertNull(domain.energy.consumedKcalToday)
        assertNull(domain.planDelta)
    }
}
