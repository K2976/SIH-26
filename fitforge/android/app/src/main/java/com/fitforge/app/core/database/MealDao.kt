package com.fitforge.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface MealDao {
    @Insert
    suspend fun insertMealLog(log: MealLogEntity)

    @Insert
    suspend fun insertMealItem(item: MealItemEntity)

    @Transaction
    suspend fun insertMeal(log: MealLogEntity, item: MealItemEntity) {
        insertMealLog(log)
        insertMealItem(item)
    }

    @Query("SELECT * FROM meal_logs WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingLogs(): List<MealLogEntity>

    @Query("SELECT * FROM meal_items WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingItems(): List<MealItemEntity>

    @Query("UPDATE meal_logs SET syncStatus = :status WHERE clientUuid = :clientUuid")
    suspend fun updateMealLogStatus(clientUuid: String, status: SyncStatus)

    @Query("UPDATE meal_items SET syncStatus = :status WHERE clientUuid = :clientUuid")
    suspend fun updateMealItemStatus(clientUuid: String, status: SyncStatus)
}
