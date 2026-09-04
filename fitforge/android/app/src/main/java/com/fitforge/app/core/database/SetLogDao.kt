package com.fitforge.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SetLogDao {
    @Insert
    suspend fun insert(setLog: SetLogEntity)

    @Query("SELECT * FROM set_logs WHERE sessionClientUuid = :sessionUuid ORDER BY setIndex ASC")
    fun getBySession(sessionUuid: String): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs WHERE exerciseSlug = :slug ORDER BY occurredAt DESC")
    fun getByExercise(slug: String): Flow<List<SetLogEntity>>

    @Query("SELECT COALESCE(MAX(setIndex), -1) FROM set_logs WHERE sessionClientUuid = :sessionUuid")
    suspend fun getMaxSetIndex(sessionUuid: String): Int

    /** Heaviest non-warmup weight ever logged for this exercise — a MAX aggregate, no thresholding. */
    @Query("SELECT MAX(weightKg) FROM set_logs WHERE exerciseSlug = :slug AND isWarmup = 0")
    fun getPersonalRecord(slug: String): Flow<Double?>

    @Query("SELECT * FROM set_logs WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<SetLogEntity>

    @Query("UPDATE set_logs SET syncStatus = :status WHERE clientUuid = :clientUuid")
    suspend fun updateSyncStatus(clientUuid: String, status: SyncStatus)
}
