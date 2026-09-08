package com.fitforge.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface CycleLogDao {
    @Insert
    suspend fun insert(log: CycleLogEntity)

    /** Every entry logged for that local day — a symptom check-in and a
     * period event can both exist for the same date as separate rows. */
    @Query("SELECT * FROM cycle_logs WHERE date = :date ORDER BY occurredAt DESC")
    fun getByDate(date: LocalDate): Flow<List<CycleLogEntity>>

    @Query("SELECT * FROM cycle_logs WHERE event = 'period_start' ORDER BY date DESC")
    fun getPeriodStarts(): Flow<List<CycleLogEntity>>

    @Query("SELECT * FROM cycle_logs WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<CycleLogEntity>

    @Query("UPDATE cycle_logs SET syncStatus = :status WHERE clientUuid = :clientUuid")
    suspend fun updateSyncStatus(clientUuid: String, status: SyncStatus)

    /** Local half of a full delete (§10) — the server side is a separate DELETE call. */
    @Query("DELETE FROM cycle_logs")
    suspend fun deleteAll()
}
