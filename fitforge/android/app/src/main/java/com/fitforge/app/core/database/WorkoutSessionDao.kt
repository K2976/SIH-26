package com.fitforge.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Insert
    suspend fun insert(session: WorkoutSessionEntity)

    @Update
    suspend fun update(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE clientUuid = :clientUuid")
    suspend fun getByUuid(clientUuid: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions ORDER BY occurredAt DESC")
    fun getAll(): Flow<List<WorkoutSessionEntity>>

    /** At most one session is ever in progress; `durationMinutes` unset means active. */
    @Query("SELECT * FROM workout_sessions WHERE durationMinutes IS NULL ORDER BY occurredAt DESC LIMIT 1")
    fun getActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<WorkoutSessionEntity>

    @Query("UPDATE workout_sessions SET syncStatus = :status WHERE clientUuid = :clientUuid")
    suspend fun updateSyncStatus(clientUuid: String, status: SyncStatus)
}
