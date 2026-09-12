package com.fitforge.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CalibrationProfileDao {
    @Query("SELECT * FROM calibration_profiles WHERE exerciseSlug = :slug LIMIT 1")
    suspend fun getForExercise(slug: String): CalibrationProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CalibrationProfileEntity)

    /** Cleared on logout — a calibration is body-specific and must not carry over to the next account. */
    @Query("DELETE FROM calibration_profiles")
    suspend fun deleteAll()
}
