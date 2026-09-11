@file:Suppress("MagicNumber", "TooManyFunctions")

package com.fitforge.app.feature.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.core.database.FoodItemEntity
import com.fitforge.app.data.repository.FOOD_SOURCE_BARCODE
import com.fitforge.app.data.repository.FOOD_SOURCE_PHOTO
import com.fitforge.app.data.repository.FOOD_SOURCE_SEARCH
import com.fitforge.app.data.repository.FoodRepository
import com.fitforge.app.data.repository.GeminiSetupException
import com.fitforge.app.data.repository.NutritionPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FoodLogStep {
    data object ChooseMethod : FoodLogStep
    data class Label(
        val candidates: List<FoodItemEntity> = emptyList(),
        val loading: Boolean = true,
        val error: FoodLabelError? = null,
    ) : FoodLogStep

    data class Search(val source: String, val fallbackFromPhoto: Boolean = false) : FoodLogStep
    data class Barcode(val notFound: Boolean = false) : FoodLogStep
    data class Portion(
        val food: FoodItemEntity,
        val source: String,
        val grams: String,
        val invalid: Boolean = false,
    ) : FoodLogStep

    data class Nutrition(val preview: NutritionPreview, val source: String) : FoodLogStep
    data object Logged : FoodLogStep
}

enum class FoodLabelError { NOT_CONFIGURED, UNAVAILABLE }

internal fun FoodLogStep.loggableNutrition(): FoodLogStep.Nutrition? =
    this as? FoodLogStep.Nutrition

@HiltViewModel
class FoodLogViewModel @Inject constructor(
    private val repository: FoodRepository,
) : ViewModel() {
    private val _step = MutableStateFlow<FoodLogStep>(FoodLogStep.ChooseMethod)
    val step: StateFlow<FoodLogStep> = _step.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<FoodItemEntity>> = _searchQuery
        .flatMapLatest(repository::search)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun beginPhoto() {
        _step.value = FoodLogStep.Label()
    }

    fun beginSearch() {
        val fromPhoto = _step.value is FoodLogStep.Label
        val source = if (fromPhoto) FOOD_SOURCE_PHOTO else FOOD_SOURCE_SEARCH
        _searchQuery.value = ""
        _step.value = FoodLogStep.Search(source, fallbackFromPhoto = fromPhoto)
    }

    fun beginBarcode() {
        _step.value = FoodLogStep.Barcode()
    }

    fun labelPhoto(jpeg: ByteArray) {
        _step.value = FoodLogStep.Label(loading = true)
        viewModelScope.launch {
            runCatching { repository.labelPhoto(jpeg, "image/jpeg") }
                .onSuccess { candidates ->
                    _step.value = if (candidates.isEmpty()) {
                        FoodLogStep.Search(FOOD_SOURCE_PHOTO, fallbackFromPhoto = true)
                    } else {
                        FoodLogStep.Label(candidates = candidates, loading = false)
                    }
                }
                .onFailure { error ->
                    _step.value = FoodLogStep.Label(
                        loading = false,
                        error = if (error is GeminiSetupException) {
                            FoodLabelError.NOT_CONFIGURED
                        } else {
                            FoodLabelError.UNAVAILABLE
                        },
                    )
                }
        }
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun selectLabel(food: FoodItemEntity) = selectFood(food, FOOD_SOURCE_PHOTO)

    fun selectSearchResult(food: FoodItemEntity) {
        val source = (_step.value as? FoodLogStep.Search)?.source ?: return
        selectFood(food, source)
    }

    fun findBarcode(barcode: String) {
        viewModelScope.launch {
            val food = repository.findByBarcode(barcode)
            if (food == null) {
                _step.value = FoodLogStep.Barcode(notFound = true)
            } else {
                selectFood(food, FOOD_SOURCE_BARCODE)
            }
        }
    }

    private fun selectFood(food: FoodItemEntity, source: String) {
        _step.value = FoodLogStep.Portion(
            food = food,
            source = source,
            grams = food.defaultPortionG.toString().removeSuffix(".0"),
        )
    }

    fun updatePortion(grams: String) {
        val portion = _step.value as? FoodLogStep.Portion ?: return
        _step.value = portion.copy(
            grams = grams.filter { it.isDigit() || it == '.' },
            invalid = false,
        )
    }

    fun confirmPortion() {
        val portion = _step.value as? FoodLogStep.Portion ?: return
        val grams = portion.grams.toDoubleOrNull()
        if (grams == null || grams <= 0) {
            _step.value = portion.copy(invalid = true)
            return
        }
        _step.value = FoodLogStep.Nutrition(
            preview = repository.nutrition(portion.food, grams),
            source = portion.source,
        )
    }

    fun logNutrition() {
        val nutrition = _step.value.loggableNutrition() ?: return
        viewModelScope.launch {
            repository.log(nutrition.preview, nutrition.source)
            _step.value = FoodLogStep.Logged
        }
    }

    fun reset() {
        _searchQuery.value = ""
        _step.value = FoodLogStep.ChooseMethod
    }
}
