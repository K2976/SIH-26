@file:Suppress("ReturnCount")

package com.fitforge.app.data.repository

import com.fitforge.app.core.database.CycleLogDao
import com.fitforge.app.core.database.CycleLogEntity
import com.fitforge.app.core.database.SetLogDao
import com.fitforge.app.core.database.SetLogEntity
import com.fitforge.app.core.database.SyncStatus
import com.fitforge.app.core.database.WorkoutSessionDao
import com.fitforge.app.core.database.WorkoutSessionEntity
import com.fitforge.app.data.remote.generated.apis.SyncApi
import com.fitforge.app.data.remote.generated.models.SyncEvent
import com.fitforge.app.data.remote.generated.models.SyncRequest
import com.fitforge.app.data.remote.generated.models.TypeEnum
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private fun WorkoutSessionEntity.toSyncEvent() = SyncEvent(
    type = TypeEnum.workout_session,
    clientUuid = UUID.fromString(diary.clientUuid),
    occurredAt = diary.occurredAt,
    utcOffset = diary.utcOffset,
    source = diary.source,
    supersedes = diary.supersedes?.let(UUID::fromString),
    plannedSession = plannedSessionClientUuid?.let(UUID::fromString),
    durationMinutes = durationMinutes,
    notes = notes,
)

private fun SetLogEntity.toSyncEvent() = SyncEvent(
    type = TypeEnum.set_log,
    clientUuid = UUID.fromString(diary.clientUuid),
    occurredAt = diary.occurredAt,
    utcOffset = diary.utcOffset,
    source = diary.source,
    supersedes = diary.supersedes?.let(UUID::fromString),
    session = UUID.fromString(sessionClientUuid),
    exercise = exerciseSlug,
    setIndex = setIndex,
    reps = reps,
    weightKg = weightKg?.toBigDecimal(),
    rpe = rpe?.toBigDecimal(),
    isWarmup = isWarmup,
    romAchieved = romAchieved,
    tempoMs = tempoMs,
)

private fun CycleLogEntity.toSyncEvent() = SyncEvent(
    type = TypeEnum.cycle_log,
    clientUuid = UUID.fromString(diary.clientUuid),
    occurredAt = diary.occurredAt,
    utcOffset = diary.utcOffset,
    source = diary.source,
    supersedes = diary.supersedes?.let(UUID::fromString),
    date = date,
    event = event,
    flow = flow,
    symptoms = symptoms,
    energyRating = energyRating,
)

/**
 * Pushes PENDING rows to `/api/v1/sync/`. Sessions are ordered ahead of their
 * own sets in every batch — `SyncView` does a live, non-transactional lookup
 * of the parent session, so a set posted before its session fails server-side.
 * `client_uuid` is generated once at write time and never regenerated on
 * retry, since it's the sole idempotency key (`update_or_create`).
 */
/** Which DAO a pending row came from, so a sync response's per-index errors
 * can be routed back to the right table's updateSyncStatus. */
private enum class PendingKind { SESSION, SET, CYCLE_LOG }

@Singleton
class SyncRepository @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val setLogDao: SetLogDao,
    private val cycleLogDao: CycleLogDao,
    private val syncApi: SyncApi,
) {
    /** Returns true if every pending row synced (or there were none); false means retry. */
    suspend fun syncPending(): Boolean {
        val pendingSessions = sessionDao.getPending()
        val pendingSets = setLogDao.getPending()
        val pendingCycleLogs = cycleLogDao.getPending()
        if (pendingSessions.isEmpty() && pendingSets.isEmpty() && pendingCycleLogs.isEmpty()) return true

        val ordered = pendingSessions.map { it.diary.clientUuid to PendingKind.SESSION } +
            pendingSets.map { it.diary.clientUuid to PendingKind.SET } +
            pendingCycleLogs.map { it.diary.clientUuid to PendingKind.CYCLE_LOG }
        val events = pendingSessions.map { it.toSyncEvent() } +
            pendingSets.map { it.toSyncEvent() } +
            pendingCycleLogs.map { it.toSyncEvent() }

        val response = syncApi.syncCreate(SyncRequest(events = events))
        if (!response.isSuccessful) return false
        val body = response.body() ?: return false

        val failedIndices = body.errors.map { it.index }.toSet()
        ordered.forEachIndexed { index, (clientUuid, kind) ->
            val status = if (index in failedIndices) SyncStatus.FAILED else SyncStatus.SYNCED
            when (kind) {
                PendingKind.SESSION -> sessionDao.updateSyncStatus(clientUuid, status)
                PendingKind.SET -> setLogDao.updateSyncStatus(clientUuid, status)
                PendingKind.CYCLE_LOG -> cycleLogDao.updateSyncStatus(clientUuid, status)
            }
        }
        return failedIndices.isEmpty()
    }
}
