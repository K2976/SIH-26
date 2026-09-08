package com.fitforge.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingMusicMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mappings: List<TrainingMusicMappingEntity>)

    @Query("SELECT COUNT(*) FROM training_music_mappings")
    suspend fun count(): Int

    @Query("SELECT * FROM training_music_mappings WHERE isActive = 1")
    fun getAllActive(): Flow<List<TrainingMusicMappingEntity>>
}
