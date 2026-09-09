package com.fitforge.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(foods: List<FoodItemEntity>)

    @Query("DELETE FROM food_search WHERE slug IN (:slugs)")
    suspend fun deleteSearchRows(slugs: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchRows(rows: List<FoodSearchEntity>)

    @Transaction
    suspend fun upsertWithSearchIndex(foods: List<FoodItemEntity>) {
        if (foods.isEmpty()) return
        upsertAll(foods)
        deleteSearchRows(foods.map { it.slug })
        insertSearchRows(foods.map { FoodSearchEntity(slug = it.slug, name = it.name) })
    }

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Query("SELECT * FROM foods WHERE slug = :slug AND isActive = 1")
    suspend fun getBySlug(slug: String): FoodItemEntity?

    @Query("SELECT * FROM foods WHERE lower(name) = lower(:name) AND isActive = 1 LIMIT 1")
    suspend fun getByName(name: String): FoodItemEntity?

    @Query("SELECT * FROM foods WHERE barcode = :barcode AND isActive = 1 LIMIT 1")
    suspend fun getByBarcode(barcode: String): FoodItemEntity?

    @Query("SELECT * FROM foods WHERE isActive = 1 ORDER BY name")
    fun getAllActive(): Flow<List<FoodItemEntity>>

    @Query(
        """
        SELECT foods.* FROM foods
        INNER JOIN food_search ON foods.slug = food_search.slug
        WHERE food_search MATCH :ftsQuery AND foods.isActive = 1
        ORDER BY foods.name
        """,
    )
    fun search(ftsQuery: String): Flow<List<FoodItemEntity>>
}
