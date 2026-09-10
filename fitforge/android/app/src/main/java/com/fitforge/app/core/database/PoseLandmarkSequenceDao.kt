package com.fitforge.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PoseLandmarkSequenceDao {
    @Insert
    suspend fun insert(sequence: PoseLandmarkSequenceEntity)

    @Query("SELECT * FROM pose_landmark_sequences WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<PoseLandmarkSequenceEntity>

    @Query("UPDATE pose_landmark_sequences SET syncStatus = :status WHERE clientUuid = :clientUuid")
    suspend fun updateSyncStatus(clientUuid: String, status: SyncStatus)
}
