package com.fitforge.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Query("DELETE FROM exercise_search WHERE slug IN (:slugs)")
    suspend fun deleteSearchRows(slugs: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchRows(rows: List<ExerciseSearchEntity>)

    /** Upserts exercises and keeps the FTS index in step, in one transaction. */
    @Transaction
    suspend fun upsertWithSearchIndex(exercises: List<ExerciseEntity>) {
        upsertAll(exercises)
        deleteSearchRows(exercises.map { it.slug })
        insertSearchRows(exercises.map { ExerciseSearchEntity(slug = it.slug, name = it.name) })
    }

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT * FROM exercises WHERE slug = :slug")
    suspend fun getBySlug(slug: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE isActive = 1 ORDER BY name")
    fun getAllActive(): Flow<List<ExerciseEntity>>

    /** [ftsQuery] should already carry any prefix-match `*` suffix the caller wants. */
    @Query(
        """
        SELECT exercises.* FROM exercises
        INNER JOIN exercise_search ON exercises.slug = exercise_search.slug
        WHERE exercise_search MATCH :ftsQuery AND exercises.isActive = 1
        """,
    )
    fun search(ftsQuery: String): Flow<List<ExerciseEntity>>
}
