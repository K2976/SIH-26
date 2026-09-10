@file:Suppress("MagicNumber")

package com.fitforge.app.data.repository

import androidx.work.WorkManager
import com.fitforge.app.core.database.BaseDiaryColumns
import com.fitforge.app.core.database.FoodItemDao
import com.fitforge.app.core.database.FoodItemEntity
import com.fitforge.app.core.database.MealDao
import com.fitforge.app.core.database.MealItemEntity
import com.fitforge.app.core.database.MealLogEntity
import com.fitforge.app.core.database.SyncStatus
import com.fitforge.app.data.remote.FoodLabelUploadApi
import com.fitforge.app.sync.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

const val FOOD_SOURCE_PHOTO = "photo"
const val FOOD_SOURCE_SEARCH = "search"
const val FOOD_SOURCE_BARCODE = "barcode"

data class NutritionPreview(
    val food: FoodItemEntity,
    val portionG: Double,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

class GeminiSetupException : IllegalStateException("Gemini API key is not configured")

class FoodLabelUnavailableException : IllegalStateException("Food recognition is unavailable")

internal data class MealRows(val log: MealLogEntity, val item: MealItemEntity)

private fun scaled(value: Double, portionG: Double): Double = BigDecimal.valueOf(value)
    .multiply(BigDecimal.valueOf(portionG))
    .divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
    .toDouble()

internal fun nutritionForPortion(food: FoodItemEntity, portionG: Double): NutritionPreview {
    require(portionG > 0) { "Portion must be greater than zero" }
    return NutritionPreview(
        food = food,
        portionG = portionG,
        calories = scaled(food.kcalPer100g, portionG),
        proteinG = scaled(food.proteinG, portionG),
        carbsG = scaled(food.carbsG, portionG),
        fatG = scaled(food.fatG, portionG),
    )
}

private fun utcOffset(at: Instant): String {
    val totalMinutes = TimeZone.currentSystemDefault().offsetAt(at).totalSeconds / 60
    val sign = if (totalMinutes < 0) "-" else "+"
    return "%s%02d:%02d".format(
        sign,
        kotlin.math.abs(totalMinutes) / 60,
        kotlin.math.abs(totalMinutes) % 60,
    )
}

internal fun buildMealRows(
    preview: NutritionPreview,
    source: String,
    now: Instant = Clock.System.now(),
    logUuid: String = UUID.randomUUID().toString(),
    itemUuid: String = UUID.randomUUID().toString(),
): MealRows {
    require(source in setOf(FOOD_SOURCE_PHOTO, FOOD_SOURCE_SEARCH, FOOD_SOURCE_BARCODE))
    val base = BaseDiaryColumns(
        clientUuid = logUuid,
        occurredAt = now,
        utcOffset = utcOffset(now),
        source = source,
        createdAt = now,
        syncStatus = SyncStatus.PENDING,
    )
    return MealRows(
        log = MealLogEntity(base),
        item = MealItemEntity(
            diary = base.copy(clientUuid = itemUuid),
            logClientUuid = logUuid,
            foodSlug = preview.food.slug,
            quantity = preview.portionG,
            calories = preview.calories,
            proteinG = preview.proteinG,
            carbsG = preview.carbsG,
            fatG = preview.fatG,
        ),
    )
}

@Singleton
class FoodRepository @Inject constructor(
    private val foodDao: FoodItemDao,
    private val mealDao: MealDao,
    private val labelApi: FoodLabelUploadApi,
    private val workManager: WorkManager,
) {
    fun search(query: String): Flow<List<FoodItemEntity>> {
        val sanitized = query.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()
        return if (sanitized.isBlank()) foodDao.getAllActive() else foodDao.search("$sanitized*")
    }

    suspend fun findByBarcode(barcode: String): FoodItemEntity? =
        foodDao.getByBarcode(barcode.filter(Char::isDigit))

    suspend fun labelPhoto(image: ByteArray, mimeType: String): List<FoodItemEntity> {
        val body = image.toRequestBody(mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("image", "food.jpg", body)
        val response = labelApi.label(part)
        if (!response.isSuccessful) {
            if (response.code() == 503) {
                throw GeminiSetupException()
            } else {
                throw FoodLabelUnavailableException()
            }
        }
        return response.body()?.candidates.orEmpty().mapNotNull { foodDao.getByName(it) }
    }

    fun nutrition(food: FoodItemEntity, portionG: Double): NutritionPreview =
        nutritionForPortion(food, portionG)

    suspend fun log(preview: NutritionPreview, source: String) {
        val rows = buildMealRows(preview, source)
        mealDao.insertMeal(rows.log, rows.item)
        SyncWorker.enqueue(workManager)
    }
}
