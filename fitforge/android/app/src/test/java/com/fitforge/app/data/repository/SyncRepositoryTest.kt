package com.fitforge.app.data.repository

import com.fitforge.app.core.database.BaseDiaryColumns
import com.fitforge.app.core.database.CycleLogDao
import com.fitforge.app.core.database.CycleLogEntity
import com.fitforge.app.core.database.SetLogDao
import com.fitforge.app.core.database.SetLogEntity
import com.fitforge.app.core.database.SyncStatus
import com.fitforge.app.core.database.WorkoutSessionDao
import com.fitforge.app.core.database.WorkoutSessionEntity
import kotlinx.datetime.LocalDate
import com.fitforge.app.data.remote.generated.apis.SyncApi
import com.fitforge.app.data.remote.generated.models.SyncRequest
import com.fitforge.app.data.remote.generated.models.SyncResponse
import com.fitforge.app.data.remote.generated.models.TypeEnum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private fun diary(clientUuid: String) = BaseDiaryColumns(
    clientUuid = clientUuid,
    occurredAt = Instant.fromEpochMilliseconds(0),
    utcOffset = "+00:00",
    source = "user_created",
    createdAt = Instant.fromEpochMilliseconds(0),
    syncStatus = SyncStatus.PENDING,
)

private class FakeSessionDao(private val pending: List<WorkoutSessionEntity>) : WorkoutSessionDao {
    val statusUpdates = mutableListOf<Pair<String, SyncStatus>>()
    override suspend fun insert(session: WorkoutSessionEntity) = error("not needed")
    override suspend fun update(session: WorkoutSessionEntity) = error("not needed")
    override suspend fun getByUuid(clientUuid: String): WorkoutSessionEntity? = error("not needed")
    override fun getAll(): Flow<List<WorkoutSessionEntity>> = error("not needed")
    override fun getActiveSession(): Flow<WorkoutSessionEntity?> = error("not needed")
    override suspend fun getPending(): List<WorkoutSessionEntity> = pending
    override suspend fun updateSyncStatus(clientUuid: String, status: SyncStatus) {
        statusUpdates += clientUuid to status
    }
}

private class FakeSetLogDao(private val pending: List<SetLogEntity>) : SetLogDao {
    val statusUpdates = mutableListOf<Pair<String, SyncStatus>>()
    override suspend fun insert(setLog: SetLogEntity) = error("not needed")
    override fun getBySession(sessionUuid: String): Flow<List<SetLogEntity>> = error("not needed")
    override fun getByExercise(slug: String): Flow<List<SetLogEntity>> = error("not needed")
    override suspend fun getMaxSetIndex(sessionUuid: String): Int = error("not needed")
    override fun getPersonalRecord(slug: String): Flow<Double?> = flowOf(null)
    override suspend fun getPending(): List<SetLogEntity> = pending
    override suspend fun updateSyncStatus(clientUuid: String, status: SyncStatus) {
        statusUpdates += clientUuid to status
    }
}

private class FakeCycleLogDao(private val pending: List<CycleLogEntity> = emptyList()) : CycleLogDao {
    val statusUpdates = mutableListOf<Pair<String, SyncStatus>>()
    override suspend fun insert(log: CycleLogEntity) = error("not needed")
    override fun getByDate(date: LocalDate): Flow<List<CycleLogEntity>> = error("not needed")
    override fun getPeriodStarts(): Flow<List<CycleLogEntity>> = error("not needed")
    override suspend fun getPending(): List<CycleLogEntity> = pending
    override suspend fun updateSyncStatus(clientUuid: String, status: SyncStatus) {
        statusUpdates += clientUuid to status
    }
    override suspend fun deleteAll() = error("not needed")
}

/** Records the events it was sent so the test can assert ordering. */
private class RecordingSyncApi(private val response: SyncResponse) : SyncApi {
    var lastRequest: SyncRequest? = null
    override suspend fun syncCreate(syncRequest: SyncRequest): Response<SyncResponse> {
        lastRequest = syncRequest
        return Response.success(response)
    }
}

class SyncRepositoryTest {

    @Test
    fun `sessions are ordered ahead of their own sets in the same batch`() = runBlocking {
        val sessionUuid = "11111111-1111-1111-1111-111111111111"
        val setUuid = "22222222-2222-2222-2222-222222222222"
        val session = WorkoutSessionEntity(diary = diary(sessionUuid))
        val set = SetLogEntity(
            diary = diary(setUuid),
            sessionClientUuid = sessionUuid,
            exerciseSlug = "barbell-back-squat",
            setIndex = 0,
            reps = 5,
            weightKg = 100.0,
        )
        val api = RecordingSyncApi(SyncResponse(successCount = 2, errors = emptyList()))
        val repository = SyncRepository(FakeSessionDao(listOf(session)), FakeSetLogDao(listOf(set)), FakeCycleLogDao(), api)

        val success = repository.syncPending()

        assertTrue(success)
        val events = api.lastRequest!!.events
        assertEquals(TypeEnum.workout_session, events[0].type)
        assertEquals(TypeEnum.set_log, events[1].type)
        // client_uuid sent is exactly the locally-generated one — never regenerated.
        assertEquals(sessionUuid, events[0].clientUuid.toString())
        assertEquals(setUuid, events[1].clientUuid.toString())
    }

    @Test
    fun `partial failure marks only the failed row FAILED, the rest SYNCED`() = runBlocking {
        val okSession = WorkoutSessionEntity(diary = diary("11111111-1111-1111-1111-111111111111"))
        val badSet = SetLogEntity(
            diary = diary("22222222-2222-2222-2222-222222222222"),
            sessionClientUuid = "11111111-1111-1111-1111-111111111111",
            exerciseSlug = "barbell-back-squat",
            setIndex = 0,
            reps = 5,
            weightKg = 100.0,
        )
        val response = SyncResponse(
            successCount = 1,
            errors = listOf(
                com.fitforge.app.data.remote.generated.models.SyncError(
                    index = 1,
                    clientUuid = "22222222-2222-2222-2222-222222222222",
                    error = "exercise slug not found",
                ),
            ),
        )
        val api = RecordingSyncApi(response)
        val sessionDao = FakeSessionDao(listOf(okSession))
        val setLogDao = FakeSetLogDao(listOf(badSet))
        val repository = SyncRepository(sessionDao, setLogDao, FakeCycleLogDao(), api)

        val success = repository.syncPending()

        assertTrue(!success)
        assertEquals(listOf("11111111-1111-1111-1111-111111111111" to SyncStatus.SYNCED), sessionDao.statusUpdates)
        assertEquals(listOf("22222222-2222-2222-2222-222222222222" to SyncStatus.FAILED), setLogDao.statusUpdates)
    }
}
