package com.fitforge.app.data.repository

import com.fitforge.app.core.database.UserStateDao
import com.fitforge.app.core.database.UserStateEntity
import com.fitforge.app.data.remote.generated.apis.StateApi
import com.fitforge.app.data.remote.generated.models.UserState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class ProfileDomain(
    val age: Int?,
    val sexAtBirth: String?,
    val heightCm: Double?,
    val unitPreference: String?,
    val tracksCycle: Boolean?,
    val timezone: String?,
)

data class GoalDomain(
    val energyDirection: String?,
    val trainingFocus: String?,
    val targetRateKgPerWeek: Double?,
    val autoAdjust: Boolean?,
)

data class EnergyDomain(
    val maintenanceKcal: Double?,
    val maintenanceSource: String?,
    val maintenanceConfidence: Double?,
    val targetKcalToday: Double?,
    val consumedKcalToday: Double?,
    val consumedKcalTodayPartial: Boolean,
    val burnedKcalToday: Double?,
    val burnedKcalTodayPartial: Boolean,
    val weekBalanceKcal: Double?,
    val weekBalanceKcalPartial: Boolean,
    val dataSufficiency: String,
)

data class TrainingDomain(
    val weekVolumePlanned: Double?,
    val weekVolumePlannedPartial: Boolean,
    val weekVolumeCompleted: Double?,
    val weekVolumeCompletedPartial: Boolean,
    val volumeByMuscleGroup: Map<String, Double>?,
    val volumeByMuscleGroupPartial: Boolean,
    val adherence7d: Double?,
    val adherence7dPartial: Boolean,
    val readinessCeilingToday: Double?,
    val daysSinceRest: Int?,
    val daysSinceRestPartial: Boolean,
)

data class PlanDeltaDomain(
    val targetKcal: Double?,
    val volumeTodayKg: Double?,
    val readinessCeiling: Double?,
    val droppedSessions: Int?,
    val reason: String?,
    val clampedBy: List<String>,
)

data class BodyDomain(
    val latestWeightKg: Double?,
    val weightTrendKgPerWeek: Double?,
    val trendConfidence: Double?,
    val fitnessAge: Double?,
)

data class CycleDomain(
    val enabled: Boolean,
    val trackingMode: String?,
    val phase: String?,
    val cycleDay: Int?,
    val confidence: Double?,
    val daysUntilPredictedPeriod: Int?,
    val energyAvailabilityFlag: String?,
)

data class FlagsDomain(
    val missingFoodDays7d: Int,
    val missingWeightDays14d: Int,
    val goalRecentlyChanged: Boolean,
    val insufficientDataForAdaptation: Boolean,
)

data class CachedUserState(
    val asOfDate: String,
    val computedAt: Instant,
    val profile: ProfileDomain,
    val goal: GoalDomain,
    val energy: EnergyDomain,
    val training: TrainingDomain,
    val planDelta: PlanDeltaDomain?,
    val body: BodyDomain,
    val cycle: CycleDomain,
    val flags: FlagsDomain,
)

@Singleton
class UserStateRepository @Inject constructor(
    private val userStateDao: UserStateDao,
    private val stateApi: StateApi,
) {
    val userState: Flow<CachedUserState?> = userStateDao.getLatest().map { entity ->
        entity?.toDomain()
    }

    suspend fun refresh(): Result<Unit> {
        return try {
            val response = stateApi.stateRetrieve()
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
            val body = response.body() ?: return Result.failure(Exception("Empty response body"))
            val entity = body.toEntity(Clock.System.now())
            userStateDao.upsert(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun UserStateEntity.toDomain(): CachedUserState {
    val muscleGroupMap: Map<String, Double>? = trainingVolumeByMuscleGroupJson?.let {
        try {
            Json.decodeFromString<Map<String, Double>>(it)
        } catch (_: Exception) {
            null
        }
    }

    val clampedByList: List<String> = planDeltaClampedByJson?.let {
        try {
            Json.decodeFromString<List<String>>(it)
        } catch (_: Exception) {
            emptyList()
        }
    } ?: emptyList()

    val planDelta = if (hasPlanDelta) {
        PlanDeltaDomain(
            targetKcal = planDeltaTargetKcal,
            volumeTodayKg = planDeltaVolumeTodayKg,
            readinessCeiling = planDeltaReadinessCeiling,
            droppedSessions = planDeltaDroppedSessions,
            reason = planDeltaReason,
            clampedBy = clampedByList,
        )
    } else {
        null
    }

    return CachedUserState(
        asOfDate = asOfDate,
        computedAt = computedAt,
        profile = ProfileDomain(
            age = profileAge,
            sexAtBirth = profileSexAtBirth,
            heightCm = profileHeightCm,
            unitPreference = profileUnitPreference,
            tracksCycle = profileTracksCycle,
            timezone = profileTimezone,
        ),
        goal = GoalDomain(
            energyDirection = goalEnergyDirection,
            trainingFocus = goalTrainingFocus,
            targetRateKgPerWeek = goalTargetRateKgPerWeek,
            autoAdjust = goalAutoAdjust,
        ),
        energy = EnergyDomain(
            maintenanceKcal = energyMaintenanceKcal,
            maintenanceSource = energyMaintenanceSource,
            maintenanceConfidence = energyMaintenanceConfidence,
            targetKcalToday = energyTargetKcalToday,
            consumedKcalToday = energyConsumedKcalToday,
            consumedKcalTodayPartial = energyConsumedKcalTodayPartial,
            burnedKcalToday = energyBurnedKcalToday,
            burnedKcalTodayPartial = energyBurnedKcalTodayPartial,
            weekBalanceKcal = energyWeekBalanceKcal,
            weekBalanceKcalPartial = energyWeekBalanceKcalPartial,
            dataSufficiency = energyDataSufficiency,
        ),
        training = TrainingDomain(
            weekVolumePlanned = trainingWeekVolumePlanned,
            weekVolumePlannedPartial = trainingWeekVolumePlannedPartial,
            weekVolumeCompleted = trainingWeekVolumeCompleted,
            weekVolumeCompletedPartial = trainingWeekVolumeCompletedPartial,
            volumeByMuscleGroup = muscleGroupMap,
            volumeByMuscleGroupPartial = trainingVolumeByMuscleGroupPartial,
            adherence7d = trainingAdherence7d,
            adherence7dPartial = trainingAdherence7dPartial,
            readinessCeilingToday = trainingReadinessCeilingToday,
            daysSinceRest = trainingDaysSinceRest,
            daysSinceRestPartial = trainingDaysSinceRestPartial,
        ),
        planDelta = planDelta,
        body = BodyDomain(
            latestWeightKg = bodyLatestWeightKg,
            weightTrendKgPerWeek = bodyWeightTrendKgPerWeek,
            trendConfidence = bodyTrendConfidence,
            fitnessAge = bodyFitnessAge,
        ),
        cycle = CycleDomain(
            enabled = cycleEnabled,
            trackingMode = cycleTrackingMode,
            phase = cyclePhase,
            cycleDay = cycleDay,
            confidence = cycleConfidence,
            daysUntilPredictedPeriod = cycleDaysUntilPredictedPeriod,
            energyAvailabilityFlag = cycleEnergyAvailabilityFlag,
        ),
        flags = FlagsDomain(
            missingFoodDays7d = flagsMissingFoodDays7d,
            missingWeightDays14d = flagsMissingWeightDays14d,
            goalRecentlyChanged = flagsGoalRecentlyChanged,
            insufficientDataForAdaptation = flagsInsufficientDataForAdaptation,
        ),
    )
}

private fun UserState.toEntity(computedAt: Instant): UserStateEntity {
    val muscleJson = training.volumeByMuscleGroup?.let { map ->
        try {
            Json.encodeToString(map)
        } catch (_: Exception) {
            null
        }
    }

    val delta = planDelta
    val clampedJson = delta?.clampedBy?.let { list ->
        try {
            Json.encodeToString(list)
        } catch (_: Exception) {
            null
        }
    }

    return UserStateEntity(
        id = 1,
        computedAt = computedAt,
        asOfDate = asOf.toString(),
        profileAge = profile.age,
        profileSexAtBirth = profile.sexAtBirth,
        profileHeightCm = profile.heightCm,
        profileUnitPreference = profile.unitPreference,
        profileTracksCycle = profile.tracksCycle,
        profileTimezone = profile.timezone,
        goalEnergyDirection = goal.energyDirection,
        goalTrainingFocus = goal.trainingFocus,
        goalTargetRateKgPerWeek = goal.targetRateKgPerWeek,
        goalAutoAdjust = goal.autoAdjust,
        energyMaintenanceKcal = energy.maintenanceKcal,
        energyMaintenanceSource = energy.maintenanceSource,
        energyMaintenanceConfidence = energy.maintenanceConfidence,
        energyTargetKcalToday = energy.targetKcalToday,
        energyConsumedKcalToday = energy.consumedKcalToday,
        energyConsumedKcalTodayPartial = energy.consumedKcalTodayPartial,
        energyBurnedKcalToday = energy.burnedKcalToday,
        energyBurnedKcalTodayPartial = energy.burnedKcalTodayPartial,
        energyWeekBalanceKcal = energy.weekBalanceKcal,
        energyWeekBalanceKcalPartial = energy.weekBalanceKcalPartial,
        energyDataSufficiency = energy.dataSufficiency,
        trainingWeekVolumePlanned = training.weekVolumePlanned,
        trainingWeekVolumePlannedPartial = training.weekVolumePlannedPartial,
        trainingWeekVolumeCompleted = training.weekVolumeCompleted,
        trainingWeekVolumeCompletedPartial = training.weekVolumeCompletedPartial,
        trainingVolumeByMuscleGroupJson = muscleJson,
        trainingVolumeByMuscleGroupPartial = training.volumeByMuscleGroupPartial,
        trainingAdherence7d = training.adherence7d,
        trainingAdherence7dPartial = training.adherence7dPartial,
        trainingReadinessCeilingToday = training.readinessCeilingToday,
        trainingDaysSinceRest = training.daysSinceRest,
        trainingDaysSinceRestPartial = training.daysSinceRestPartial,
        hasPlanDelta = delta != null,
        planDeltaTargetKcal = delta?.targetKcal,
        planDeltaVolumeTodayKg = delta?.volumeTodayKg,
        planDeltaReadinessCeiling = delta?.readinessCeiling,
        planDeltaDroppedSessions = delta?.droppedSessions,
        planDeltaReason = delta?.reason,
        planDeltaClampedByJson = clampedJson,
        bodyLatestWeightKg = body.latestWeightKg,
        bodyWeightTrendKgPerWeek = body.weightTrendKgPerWeek,
        bodyTrendConfidence = body.trendConfidence,
        bodyFitnessAge = body.fitnessAge,
        cycleEnabled = cycle.enabled,
        cycleTrackingMode = cycle.trackingMode,
        cyclePhase = cycle.phase,
        cycleDay = cycle.cycleDay,
        cycleConfidence = cycle.confidence,
        cycleDaysUntilPredictedPeriod = cycle.daysUntilPredictedPeriod,
        cycleEnergyAvailabilityFlag = cycle.energyAvailabilityFlag,
        flagsMissingFoodDays7d = flags.missingFoodDays7d,
        flagsMissingWeightDays14d = flags.missingWeightDays14d,
        flagsGoalRecentlyChanged = flags.goalRecentlyChanged,
        flagsInsufficientDataForAdaptation = flags.insufficientDataForAdaptation,
    )
}
