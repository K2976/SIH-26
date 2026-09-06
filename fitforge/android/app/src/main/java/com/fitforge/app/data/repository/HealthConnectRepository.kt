package com.fitforge.app.data.repository

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.fitforge.app.data.local.HealthLocalDataSource
import com.fitforge.app.domain.model.HealthConnectAvailability
import com.fitforge.app.domain.model.HealthDataSummary
import com.fitforge.app.domain.repository.HealthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HealthConnectRepo"

@Singleton
class HealthConnectRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val healthLocalDataSource: HealthLocalDataSource,
) : HealthRepository {

    override val latestHealthData: Flow<HealthDataSummary?> = healthLocalDataSource.latestHealthData
    override val lastSyncedAt: Flow<Instant?> = healthLocalDataSource.lastSyncedAt

    private val healthConnectClient: HealthConnectClient? by lazy {

        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    private val requiredPermissionsSet = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
    )

    override fun getAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NotInstalled
            else -> HealthConnectAvailability.NotSupported
        }
    }

    override fun getRequiredPermissions(): Set<String> = requiredPermissionsSet

    override suspend fun getGrantedPermissions(): Set<String> {
        val client = healthConnectClient ?: return emptySet()
        return try {
            client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching granted permissions", e)
            emptySet()
        }
    }

    override suspend fun hasAllPermissions(): Boolean {
        val granted = getGrantedPermissions()
        return requiredPermissionsSet.all { it in granted }
    }

    override suspend fun hasAnyPermission(): Boolean {
        val granted = getGrantedPermissions()
        return granted.isNotEmpty()
    }

    override suspend fun readHealthSummary(startTime: Instant, endTime: Instant): Result<HealthDataSummary> {
        val client = healthConnectClient
            ?: return Result.failure(IllegalStateException("Health Connect is not available on this device"))

        return runCatching {
            val granted = getGrantedPermissions()
            val timeRange = TimeRangeFilter.between(startTime, endTime)

            Log.d(TAG, "readHealthSummary: startTime=$startTime, endTime=$endTime, grantedPermissions=$granted")

            var steps: Long? = null
            var distanceMeters: Double? = null
            var activeCalories: Double? = null
            var totalCalories: Double? = null

            // Aggregate metrics if permitted
            val aggregateMetrics = mutableSetOf<androidx.health.connect.client.aggregate.AggregateMetric<*>>()
            if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
                aggregateMetrics.add(StepsRecord.COUNT_TOTAL)
            }
            if (HealthPermission.getReadPermission(DistanceRecord::class) in granted) {
                aggregateMetrics.add(DistanceRecord.DISTANCE_TOTAL)
            }
            if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) {
                aggregateMetrics.add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            }
            if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted) {
                aggregateMetrics.add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            }

            if (aggregateMetrics.isNotEmpty()) {
                try {
                    val aggregateResult = client.aggregate(
                        AggregateRequest(
                            metrics = aggregateMetrics,
                            timeRangeFilter = timeRange,
                        )
                    )
                    steps = aggregateResult[StepsRecord.COUNT_TOTAL]
                    distanceMeters = aggregateResult[DistanceRecord.DISTANCE_TOTAL]?.inMeters
                    activeCalories = aggregateResult[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                    totalCalories = aggregateResult[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                    
                    Log.d(TAG, "Aggregate result: steps=$steps, distanceMeters=$distanceMeters, activeCalories=$activeCalories, totalCalories=$totalCalories")
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading aggregated health metrics", e)
                }
            } else {
                Log.w(TAG, "No aggregate metrics requested - no permissions granted for aggregated metrics")
            }

            // Latest Heart Rate
            var latestHeartRate: Long? = null
            if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
                try {
                    val records = client.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = timeRange,
                            ascendingOrder = false,
                            pageSize = 1,
                        )
                    )
                    latestHeartRate = records.records.firstOrNull()?.samples?.lastOrNull()?.beatsPerMinute
                    Log.d(TAG, "HeartRateRecord: count=${records.records.size}, latestHeartRate=$latestHeartRate")
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading HeartRateRecord", e)
                }
            } else {
                Log.d(TAG, "HeartRateRecord permission not granted")
            }

            // Resting Heart Rate
            var restingHeartRate: Long? = null
            if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in granted) {
                try {
                    val records = client.readRecords(
                        ReadRecordsRequest(
                            recordType = RestingHeartRateRecord::class,
                            timeRangeFilter = timeRange,
                            ascendingOrder = false,
                            pageSize = 1,
                        )
                    )
                    restingHeartRate = records.records.firstOrNull()?.beatsPerMinute
                    Log.d(TAG, "RestingHeartRateRecord: count=${records.records.size}, restingHeartRate=$restingHeartRate")
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading RestingHeartRateRecord", e)
                }
            } else {
                Log.d(TAG, "RestingHeartRateRecord permission not granted")
            }

            // Sleep Session
            var sleepDurationMinutes: Long? = null
            var sleepTitle: String? = null
            if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
                try {
                    val records = client.readRecords(
                        ReadRecordsRequest(
                            recordType = SleepSessionRecord::class,
                            timeRangeFilter = timeRange,
                            ascendingOrder = false,
                            pageSize = 1,
                        )
                    )
                    val latestSleep = records.records.firstOrNull()
                    if (latestSleep != null) {
                        sleepDurationMinutes = Duration.between(latestSleep.startTime, latestSleep.endTime).toMinutes()
                        sleepTitle = latestSleep.title ?: "Sleep Session"
                    }
                    Log.d(TAG, "SleepSessionRecord: count=${records.records.size}, sleepDurationMinutes=$sleepDurationMinutes, sleepTitle=$sleepTitle")
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading SleepSessionRecord", e)
                }
            } else {
                Log.d(TAG, "SleepSessionRecord permission not granted")
            }

            // Exercise Session
            var exerciseTitle: String? = null
            var exerciseDurationMinutes: Long? = null
            if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
                try {
                    val records = client.readRecords(
                        ReadRecordsRequest(
                            recordType = ExerciseSessionRecord::class,
                            timeRangeFilter = timeRange,
                            ascendingOrder = false,
                            pageSize = 1,
                        )
                    )
                    val latestExercise = records.records.firstOrNull()
                    if (latestExercise != null) {
                        exerciseDurationMinutes = Duration.between(latestExercise.startTime, latestExercise.endTime).toMinutes()
                        exerciseTitle = latestExercise.title ?: "Exercise Session"
                    }
                    Log.d(TAG, "ExerciseSessionRecord: count=${records.records.size}, exerciseDurationMinutes=$exerciseDurationMinutes, exerciseTitle=$exerciseTitle")
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading ExerciseSessionRecord", e)
                }
            } else {
                Log.d(TAG, "ExerciseSessionRecord permission not granted")
            }

            // Weight
            var weightKg: Double? = null
            if (HealthPermission.getReadPermission(WeightRecord::class) in granted) {
                try {
                    val records = client.readRecords(
                        ReadRecordsRequest(
                            recordType = WeightRecord::class,
                            timeRangeFilter = timeRange,
                            ascendingOrder = false,
                            pageSize = 1,
                        )
                    )
                    weightKg = records.records.firstOrNull()?.weight?.inKilograms
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading WeightRecord", e)
                }
            }

            // Height
            var heightMeters: Double? = null
            if (HealthPermission.getReadPermission(HeightRecord::class) in granted) {
                try {
                    val records = client.readRecords(
                        ReadRecordsRequest(
                            recordType = HeightRecord::class,
                            timeRangeFilter = timeRange,
                            ascendingOrder = false,
                            pageSize = 1,
                        )
                    )
                    heightMeters = records.records.firstOrNull()?.height?.inMeters
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading HeightRecord", e)
                }
            }

            // Body Fat
            var bodyFatPercentage: Double? = null
            if (HealthPermission.getReadPermission(BodyFatRecord::class) in granted) {
                try {
                    val records = client.readRecords(
                        ReadRecordsRequest(
                            recordType = BodyFatRecord::class,
                            timeRangeFilter = timeRange,
                            ascendingOrder = false,
                            pageSize = 1,
                        )
                    )
                    bodyFatPercentage = records.records.firstOrNull()?.percentage?.value
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading BodyFatRecord", e)
                }
            }

            // Oxygen Saturation
            var oxygenSaturationPercentage: Double? = null
            if (HealthPermission.getReadPermission(OxygenSaturationRecord::class) in granted) {
                try {
                    val records = client.readRecords(
                        ReadRecordsRequest(
                            recordType = OxygenSaturationRecord::class,
                            timeRangeFilter = timeRange,
                            ascendingOrder = false,
                            pageSize = 1,
                        )
                    )
                    oxygenSaturationPercentage = records.records.firstOrNull()?.percentage?.value
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading OxygenSaturationRecord", e)
                }
            }

            // Respiratory Rate
            var respiratoryRateBpm: Double? = null
            if (HealthPermission.getReadPermission(RespiratoryRateRecord::class) in granted) {
                try {
                    val records = client.readRecords(
                        ReadRecordsRequest(
                            recordType = RespiratoryRateRecord::class,
                            timeRangeFilter = timeRange,
                            ascendingOrder = false,
                            pageSize = 1,
                        )
                    )
                    respiratoryRateBpm = records.records.firstOrNull()?.rate
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading RespiratoryRateRecord", e)
                }
            }

            Log.d(TAG, "Final HealthDataSummary: steps=$steps, distanceMeters=$distanceMeters, activeCalories=$activeCalories, totalCalories=$totalCalories, latestHeartRate=$latestHeartRate, restingHeartRate=$restingHeartRate, sleepDurationMinutes=$sleepDurationMinutes, sleepTitle=$sleepTitle, exerciseTitle=$exerciseTitle, exerciseDurationMinutes=$exerciseDurationMinutes, weightKg=$weightKg, heightMeters=$heightMeters, bodyFatPercentage=$bodyFatPercentage, oxygenSaturation=$oxygenSaturationPercentage, respiratoryRate=$respiratoryRateBpm")

            HealthDataSummary(
                steps = steps,
                distanceMeters = distanceMeters,
                activeCaloriesKcal = activeCalories,
                totalCaloriesKcal = totalCalories,
                latestHeartRateBpm = latestHeartRate,
                restingHeartRateBpm = restingHeartRate,
                sleepDurationMinutes = sleepDurationMinutes,
                sleepStagesSummary = sleepTitle,
                latestExerciseSessionTitle = exerciseTitle,
                latestExerciseDurationMinutes = exerciseDurationMinutes,
                weightKg = weightKg,
                heightMeters = heightMeters,
                bodyFatPercentage = bodyFatPercentage,
                oxygenSaturationPercentage = oxygenSaturationPercentage,
                respiratoryRateBpm = respiratoryRateBpm,
                lastSyncedAt = Instant.now(),
            )
        }
    }

    override suspend fun syncHealthData(): Result<HealthDataSummary> {
        val availability = getAvailability()
        if (availability != HealthConnectAvailability.Available) {
            return Result.failure(IllegalStateException("Health Connect is not available on this device ($availability)"))
        }

        if (!hasAnyPermission()) {
            return Result.failure(SecurityException("No Health Connect permissions are currently granted"))
        }

        val now = Instant.now()
        // Query from start of current day in user's local timezone
        val startOfDay = ZonedDateTime.now(ZoneId.systemDefault())
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()

        Log.d(TAG, "Starting health data sync: time window $startOfDay to $now")
        val summaryResult = readHealthSummary(startOfDay, now)

        summaryResult.onSuccess { summary ->
            Log.d(TAG, "Health data sync succeeded, persisting summary locally")
            healthLocalDataSource.saveHealthSummary(summary)
        }.onFailure { error ->
            Log.e(TAG, "Health data sync query failed", error)
        }

        return summaryResult
    }
}

