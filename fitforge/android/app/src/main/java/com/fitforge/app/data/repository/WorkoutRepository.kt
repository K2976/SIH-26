package com.fitforge.app.data.repository

import com.fitforge.app.core.database.BaseDiaryColumns
import com.fitforge.app.core.database.ExerciseDao
import com.fitforge.app.core.database.ExerciseEntity
import com.fitforge.app.core.database.SetLogDao
import com.fitforge.app.core.database.SetLogEntity
import com.fitforge.app.core.database.SyncStatus
import com.fitforge.app.core.database.WorkoutSessionDao
import com.fitforge.app.core.database.WorkoutSessionEntity
import com.fitforge.app.sync.SyncWorker
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val EVENT_SOURCE = "user_created"

/** `SetLog.source` for a rep counted by the camera pipeline — see WorkoutRepository.addSet. */
const val CAMERA_SOURCE = "camera"

private fun currentUtcOffset(): String {
    val offset = TimeZone.currentSystemDefault().offsetAt(Clock.System.now())
    val totalMinutes = offset.totalSeconds / 60
    val sign = if (totalMinutes < 0) "-" else "+"
    val hours = kotlin.math.abs(totalMinutes) / 60
    val minutes = kotlin.math.abs(totalMinutes) % 60
    return "%s%02d:%02d".format(sign, hours, minutes)
}

private fun newDiaryColumns(source: String = EVENT_SOURCE) = BaseDiaryColumns(
    clientUuid = UUID.randomUUID().toString(),
    occurredAt = Clock.System.now(),
    utcOffset = currentUtcOffset(),
    source = source,
    createdAt = Clock.System.now(),
    syncStatus = SyncStatus.PENDING,
)

/**
 * The one place a `SetLog` row gets built, whether the set was typed in or
 * counted by the camera — pulled out of [WorkoutRepository.addSet] so the
 * "camera rows are identical to manual ones except source/rom/tempo"
 * property is directly unit-testable without a DAO or WorkManager.
 */
internal fun buildSetLogEntity(
    sessionUuid: String,
    exerciseSlug: String,
    setIndex: Int,
    reps: Int,
    weightKg: Double?,
    isWarmup: Boolean,
    source: String,
    romAchieved: String?,
    tempoMs: Int?,
) = SetLogEntity(
    diary = newDiaryColumns(source),
    sessionClientUuid = sessionUuid,
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    reps = reps,
    weightKg = weightKg,
    isWarmup = isWarmup,
    romAchieved = romAchieved,
    tempoMs = tempoMs,
)

/**
 * All logbook writes land here and only here — Room immediately, offline
 * first. [SyncWorker] is kicked after every write so a live connection picks
 * it up promptly; WorkManager itself is what survives app death.
 */
@Singleton
class WorkoutRepository @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val setLogDao: SetLogDao,
    private val exerciseDao: ExerciseDao,
    private val workManager: WorkManager,
) {
    fun activeSession(): Flow<WorkoutSessionEntity?> = sessionDao.getActiveSession()

    fun history(): Flow<List<WorkoutSessionEntity>> = sessionDao.getAll()

    fun setsForSession(sessionUuid: String): Flow<List<SetLogEntity>> = setLogDao.getBySession(sessionUuid)

    fun historyForExercise(slug: String): Flow<List<SetLogEntity>> = setLogDao.getByExercise(slug)

    fun personalRecord(slug: String): Flow<Double?> = setLogDao.getPersonalRecord(slug)

    fun allExercises(): Flow<List<ExerciseEntity>> = exerciseDao.getAllActive()

    /** Strips FTS4 MATCH syntax characters (quotes, hyphens, ...) so a stray one can't throw a SQLiteException. */
    fun searchExercises(query: String): Flow<List<ExerciseEntity>> {
        val sanitized = query.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()
        return if (sanitized.isBlank()) exerciseDao.getAllActive() else exerciseDao.search("$sanitized*")
    }

    suspend fun exerciseBySlug(slug: String): ExerciseEntity? = exerciseDao.getBySlug(slug)

    suspend fun startSession(): String {
        val session = WorkoutSessionEntity(diary = newDiaryColumns())
        sessionDao.insert(session)
        SyncWorker.enqueue(workManager)
        return session.diary.clientUuid
    }

    suspend fun addSet(
        sessionUuid: String,
        exerciseSlug: String,
        reps: Int,
        weightKg: Double?,
        isWarmup: Boolean = false,
        source: String = EVENT_SOURCE,
        romAchieved: String? = null,
        tempoMs: Int? = null,
    ) {
        val nextIndex = setLogDao.getMaxSetIndex(sessionUuid) + 1
        setLogDao.insert(
            buildSetLogEntity(sessionUuid, exerciseSlug, nextIndex, reps, weightKg, isWarmup, source, romAchieved, tempoMs),
        )
        SyncWorker.enqueue(workManager)
    }

    suspend fun finishSession(sessionUuid: String) {
        val session = sessionDao.getByUuid(sessionUuid) ?: return
        val elapsedMinutes = (Clock.System.now() - session.diary.occurredAt).inWholeMinutes.toInt()
        sessionDao.update(session.copy(durationMinutes = elapsedMinutes))
        SyncWorker.enqueue(workManager)
    }
}
