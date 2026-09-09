@file:Suppress("ReturnCount", "TooManyFunctions")

package com.fitforge.app.data.repository

import android.content.Context
import android.os.RemoteException
import android.util.Log
import java.io.IOException
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

private data class AggregateMetrics(
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCalories: Double? = null,
    val totalCalories: Double? = null,
)

private data class SleepData(
    val durationMinutes: Long? = null,
    val title: String? = null,
)

private data class ExerciseData(
    val title: String? = null,
    val durationMinutes: Long? = null,
)

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
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException fetching granted permissions", e)
            emptySet()
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException fetching granted permissions", e)
            emptySet()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException fetching granted permissions", e)
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

        val granted = getGrantedPermissions()
        val timeRange = TimeRangeFilter.between(startTime, endTime)
        Log.d(TAG, "readHealthSummary: startTime=$startTime, endTime=$endTime, grantedPermissions=$granted")

        val aggregates = readAggregateMetrics(client, timeRange, granted)
        val latestHeartRate = readLatestHeartRate(client, timeRange, granted)
        val restingHeartRate = readRestingHeartRate(client, timeRange, granted)
        val sleepData = readSleepSession(client, timeRange, granted)
        val exerciseData = readExerciseSession(client, timeRange, granted)
        val weight = readWeight(client, timeRange, granted)
        val height = readHeight(client, timeRange, granted)
        val bodyFat = readBodyFat(client, timeRange, granted)
        val oxygenSat = readOxygenSaturation(client, timeRange, granted)
        val respRate = readRespiratoryRate(client, timeRange, granted)

        return Result.success(
            HealthDataSummary(
                steps = aggregates.steps,
                distanceMeters = aggregates.distanceMeters,
                activeCaloriesKcal = aggregates.activeCalories,
                totalCaloriesKcal = aggregates.totalCalories,
                latestHeartRateBpm = latestHeartRate,
                restingHeartRateBpm = restingHeartRate,
                sleepDurationMinutes = sleepData.durationMinutes,
                sleepStagesSummary = sleepData.title,
                latestExerciseSessionTitle = exerciseData.title,
                latestExerciseDurationMinutes = exerciseData.durationMinutes,
                weightKg = weight,
                heightMeters = height,
                bodyFatPercentage = bodyFat,
                oxygenSaturationPercentage = oxygenSat,
                respiratoryRateBpm = respRate,
                lastSyncedAt = Instant.now(),
            )
        )
    }

    private suspend fun readAggregateMetrics(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter,
        granted: Set<String>,
    ): AggregateMetrics {
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

        if (aggregateMetrics.isEmpty()) return AggregateMetrics()

        return try {
            val result = client.aggregate(AggregateRequest(metrics = aggregateMetrics, timeRangeFilter = timeRange))
            AggregateMetrics(
                steps = result[StepsRecord.COUNT_TOTAL],
                distanceMeters = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
                activeCalories = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
                totalCalories = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
            )
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException reading aggregates", e)
            AggregateMetrics()
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading aggregates", e)
            AggregateMetrics()
        } catch (e: IOException) {
            Log.w(TAG, "IOException reading aggregates", e)
            AggregateMetrics()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException reading aggregates", e)
            AggregateMetrics()
        }
    }

    private suspend fun readLatestHeartRate(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter,
        granted: Set<String>,
    ): Long? {
        if (HealthPermission.getReadPermission(HeartRateRecord::class) !in granted) return null
        return try {
            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            records.records.firstOrNull()?.samples?.lastOrNull()?.beatsPerMinute
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException reading HeartRateRecord", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading HeartRateRecord", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "IOException reading HeartRateRecord", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException reading HeartRateRecord", e)
            null
        }
    }

    private suspend fun readRestingHeartRate(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter,
        granted: Set<String>,
    ): Long? {
        if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) !in granted) return null
        return try {
            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            records.records.firstOrNull()?.beatsPerMinute
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException reading RestingHeartRateRecord", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading RestingHeartRateRecord", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "IOException reading RestingHeartRateRecord", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException reading RestingHeartRateRecord", e)
            null
        }
    }

    private suspend fun readSleepSession(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter,
        granted: Set<String>,
    ): SleepData {
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) !in granted) return SleepData()
        return try {
            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            val latestSleep = records.records.firstOrNull() ?: return SleepData()
            SleepData(
                durationMinutes = Duration.between(latestSleep.startTime, latestSleep.endTime).toMinutes(),
                title = latestSleep.title ?: "Sleep Session",
            )
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException reading SleepSessionRecord", e)
            SleepData()
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading SleepSessionRecord", e)
            SleepData()
        } catch (e: IOException) {
            Log.w(TAG, "IOException reading SleepSessionRecord", e)
            SleepData()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException reading SleepSessionRecord", e)
            SleepData()
        }
    }

    private suspend fun readExerciseSession(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter,
        granted: Set<String>,
    ): ExerciseData {
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) !in granted) return ExerciseData()
        return try {
            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            val latestExercise = records.records.firstOrNull() ?: return ExerciseData()
            ExerciseData(
                title = latestExercise.title ?: "Exercise Session",
                durationMinutes = Duration.between(latestExercise.startTime, latestExercise.endTime).toMinutes(),
            )
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException reading ExerciseSessionRecord", e)
            ExerciseData()
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading ExerciseSessionRecord", e)
            ExerciseData()
        } catch (e: IOException) {
            Log.w(TAG, "IOException reading ExerciseSessionRecord", e)
            ExerciseData()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException reading ExerciseSessionRecord", e)
            ExerciseData()
        }
    }

    private suspend fun readWeight(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter,
        granted: Set<String>,
    ): Double? {
        if (HealthPermission.getReadPermission(WeightRecord::class) !in granted) return null
        return try {
            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            records.records.firstOrNull()?.weight?.inKilograms
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException reading WeightRecord", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading WeightRecord", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "IOException reading WeightRecord", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException reading WeightRecord", e)
            null
        }
    }

    private suspend fun readHeight(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter,
        granted: Set<String>,
    ): Double? {
        if (HealthPermission.getReadPermission(HeightRecord::class) !in granted) return null
        return try {
            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeightRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            records.records.firstOrNull()?.height?.inMeters
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException reading HeightRecord", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading HeightRecord", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "IOException reading HeightRecord", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException reading HeightRecord", e)
            null
        }
    }

    private suspend fun readBodyFat(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter,
        granted: Set<String>,
    ): Double? {
        if (HealthPermission.getReadPermission(BodyFatRecord::class) !in granted) return null
        return try {
            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = BodyFatRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            records.records.firstOrNull()?.percentage?.value
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException reading BodyFatRecord", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading BodyFatRecord", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "IOException reading BodyFatRecord", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException reading BodyFatRecord", e)
            null
        }
    }

    private suspend fun readOxygenSaturation(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter,
        granted: Set<String>,
    ): Double? {
        if (HealthPermission.getReadPermission(OxygenSaturationRecord::class) !in granted) return null
        return try {
            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            records.records.firstOrNull()?.percentage?.value
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException reading OxygenSaturationRecord", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading OxygenSaturationRecord", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "IOException reading OxygenSaturationRecord", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException reading OxygenSaturationRecord", e)
            null
        }
    }

    private suspend fun readRespiratoryRate(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter,
        granted: Set<String>,
    ): Double? {
        if (HealthPermission.getReadPermission(RespiratoryRateRecord::class) !in granted) return null
        return try {
            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = RespiratoryRateRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            records.records.firstOrNull()?.rate
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException reading RespiratoryRateRecord", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading RespiratoryRateRecord", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "IOException reading RespiratoryRateRecord", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException reading RespiratoryRateRecord", e)
            null
        }
    }

    override suspend fun syncHealthData(): Result<HealthDataSummary> {
        val availability = getAvailability()
        if (availability != HealthConnectAvailability.Available) {
            return Result.failure(
                IllegalStateException(
                    "Health Connect is not available on this device ($availability)"
                )
            )
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
