package com.fitforge.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserStateDao {
    @Query("SELECT * FROM user_state WHERE id = 1 LIMIT 1")
    fun getLatest(): Flow<UserStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserStateEntity)

    @Query("DELETE FROM user_state")
    suspend fun deleteAll()
}
