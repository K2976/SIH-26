@file:Suppress("MagicNumber")

package com.fitforge.app.data.repository

import androidx.work.WorkManager
import com.fitforge.app.core.database.BaseDiaryColumns
import com.fitforge.app.core.database.CycleLogDao
import com.fitforge.app.core.database.CycleLogEntity
import com.fitforge.app.core.database.SyncStatus
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.remote.generated.apis.CycleApi
import com.fitforge.app.data.remote.generated.models.CycleExport
import com.fitforge.app.data.remote.generated.models.PatchedCycleProfile
import com.fitforge.app.data.remote.generated.models.TrackingModeEnum
import com.fitforge.app.sync.SyncWorker
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val EVENT_SOURCE = "user_created"

private fun currentUtcOffset(): String {
    val offset = TimeZone.currentSystemDefault().offsetAt(Clock.System.now())
    val totalMinutes = offset.totalSeconds / 60
    val sign = if (totalMinutes < 0) "-" else "+"
    val hours = kotlin.math.abs(totalMinutes) / 60
    val minutes = kotlin.math.abs(totalMinutes) % 60
    return "%s%02d:%02d".format(sign, hours, minutes)
}

private fun newDiaryColumns() = BaseDiaryColumns(
    clientUuid = UUID.randomUUID().toString(),
    occurredAt = Clock.System.now(),
    utcOffset = currentUtcOffset(),
    source = EVENT_SOURCE,
    createdAt = Clock.System.now(),
    syncStatus = SyncStatus.PENDING,
)

/**
 * All cycle-module writes land here, offline first, same as [WorkoutRepository].
 * Cycle data is never read by, or exposed to, the coach chat layer — that
 * boundary is enforced server-side (apps/coach/prompt.py's allowlist), not
 * here, but this repository still never forwards anything to that feature.
 */
@Singleton
class CycleRepository @Inject constructor(
    private val cycleLogDao: CycleLogDao,
    private val workManager: WorkManager,
    private val cycleApi: CycleApi,
    private val preferencesDataStore: UserPreferencesDataStore,
) {
    val discreetMode: Flow<Boolean> = preferencesDataStore.cycleDiscreetMode

    suspend fun setDiscreetMode(value: Boolean) = preferencesDataStore.setCycleDiscreetMode(value)

    /** Never inferred — set only by a direct question (§10). */
    suspend fun setTrackingMode(trackingMode: String): Result<Unit> = try {
        val mode = TrackingModeEnum.valueOf(trackingMode)
        val response = cycleApi.cycleProfilePartialUpdate(PatchedCycleProfile(trackingMode = mode))
        if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code()}"))
    } catch (e: IOException) {
        Result.failure(e)
    }

    suspend fun exportData(): Result<CycleExport> = try {
        val response = cycleApi.cycleDataRetrieve()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    } catch (e: IOException) {
        Result.failure(e)
    }

    /** Full, irreversible delete — server rows and the local cache both. */
    suspend fun deleteAllData(): Result<Unit> = try {
        val response = cycleApi.cycleDataDestroy()
        if (response.isSuccessful) {
            cycleLogDao.deleteAll()
            Result.success(Unit)
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    } catch (e: IOException) {
        Result.failure(e)
    }

    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** Every entry logged for [date] — most recent first. Empty (not null)
     * means "checked in, nothing to report"; no rows at all means "hasn't
     * opened today's log yet" — the UI tells these apart. */
    fun logsFor(date: LocalDate): Flow<List<CycleLogEntity>> = cycleLogDao.getByDate(date)

    fun periodStarts(): Flow<List<LocalDate>> =
        cycleLogDao.getPeriodStarts().map { rows -> rows.map { it.date } }

    /** The one-tap daily check-in: energy rating and/or symptoms for [date].
     * An explicit empty [symptoms] list is a real "logged, no symptoms"
     * signal — the row is still written so it's not indistinguishable from
     * not having logged at all. */
    suspend fun logSymptoms(date: LocalDate, energyRating: Int?, symptoms: List<String>) {
        cycleLogDao.insert(
            CycleLogEntity(
                diary = newDiaryColumns(),
                date = date,
                symptoms = symptoms,
                energyRating = energyRating,
            ),
        )
        SyncWorker.enqueue(workManager)
    }

    /** Period start/end/spotting entries from the calendar screen. */
    suspend fun logPeriodEvent(date: LocalDate, event: String, flow: String = "") {
        cycleLogDao.insert(
            CycleLogEntity(diary = newDiaryColumns(), date = date, event = event, flow = flow),
        )
        SyncWorker.enqueue(workManager)
    }
}
