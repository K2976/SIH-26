package com.fitforge.app.feature.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.data.remote.generated.apis.GoalApi
import com.fitforge.app.data.remote.generated.apis.ProfileApi
import com.fitforge.app.data.remote.generated.models.GoalRequest
import com.fitforge.app.data.remote.generated.models.PatchedUserProfile
import com.fitforge.app.data.remote.generated.models.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject

private const val TAG = "OnboardingViewModel"

/** The steps of the onboarding wizard, in order. */
enum class OnboardingStep {
    BASICS,
    CYCLE_TRACKING,
    GOAL,
    TARGET_RATE,
    FITNESS_INPUTS,
    COMPLETION,
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.BASICS,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,

    // Step 1 — Basics
    val dateOfBirth: LocalDate? = null,
    val sexAtBirth: String? = null,
    val heightCm: String = "",
    val currentWeightKg: String = "",
    val unitPreference: String = "metric",
    val timezone: String = kotlinx.datetime.TimeZone.currentSystemDefault().id,

    // Step 2 — Cycle tracking
    val tracksCycle: Boolean? = null,

    // Step 3 — Goal
    val energyDirection: String? = null,
    val trainingFocus: String? = null,

    // Step 4 — Target rate
    val targetRateKgPerWeek: String = "0.5",
    val clampReason: String? = null,

    // Step 5 — Fitness inputs
    val waistCm: String = "",
    val restingHr: String = "",
    val activityLevel: String? = null,

    // Accumulated declined fields across all steps
    val declinedFields: MutableList<String> = mutableListOf(),

    // Whether onboarding was already completed (re-run mode)
    val isRerun: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileApi: ProfileApi,
    private val goalApi: GoalApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        loadExistingProfile()
    }

    private fun loadExistingProfile() {
        viewModelScope.launch {
            try {
                val profileResp = profileApi.profileRetrieve()
                val profile = profileResp.body()
                val goal = try {
                    goalApi.goalRetrieve().body()
                } catch (e: IOException) {
                    Log.w(TAG, "Goal fetch network error", e)
                    null
                } catch (e: HttpException) {
                    Log.w(TAG, "Goal not set (HTTP ${e.code()})", e)
                    null
                } catch (e: SerializationException) {
                    Log.w(TAG, "Goal failed to deserialize", e)
                    null
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Goal illegal state", e)
                    null
                }

                if (profile != null) {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isRerun = profile.onboardingCompletedAt != null,
                            dateOfBirth = profile.dateOfBirth,
                            sexAtBirth = profile.sexAtBirth?.value,
                            heightCm = profile.heightCm ?: "",
                            currentWeightKg = profile.currentWeightKg ?: "",
                            unitPreference = profile.unitPreference?.value ?: "metric",
                            timezone = profile.timezone ?: state.timezone,
                            tracksCycle = profile.tracksCycle,
                            waistCm = profile.waistCm ?: "",
                            restingHr = profile.restingHr?.toString() ?: "",
                            activityLevel = profile.activityLevel?.value,
                            declinedFields = profile.declinedFields?.toMutableList() ?: mutableListOf(),
                            energyDirection = goal?.energyDirection?.value,
                            trainingFocus = goal?.trainingFocus?.value,
                            targetRateKgPerWeek = goal?.targetRateKgPerWeek ?: "0.5",
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to load existing profile due to network IO error", e)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: HttpException) {
                Log.w(TAG, "Failed to load existing profile due to HTTP error", e)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: SerializationException) {
                Log.w(TAG, "Failed to deserialize profile response", e)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Illegal state loading existing profile", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // --- Field updates ---

    fun updateDateOfBirth(date: LocalDate) {
        _uiState.update { it.copy(dateOfBirth = date) }
    }

    fun updateSexAtBirth(sex: String) {
        _uiState.update { it.copy(sexAtBirth = sex) }
    }

    fun updateHeightCm(value: String) {
        _uiState.update { it.copy(heightCm = value) }
    }

    fun updateCurrentWeightKg(value: String) {
        _uiState.update { it.copy(currentWeightKg = value) }
    }

    fun updateUnitPreference(value: String) {
        _uiState.update { it.copy(unitPreference = value) }
    }

    fun updateTracksCycle(value: Boolean) {
        _uiState.update { it.copy(tracksCycle = value) }
    }

    fun updateEnergyDirection(value: String) {
        _uiState.update { it.copy(energyDirection = value) }
    }

    fun updateTrainingFocus(value: String) {
        _uiState.update { it.copy(trainingFocus = value) }
    }

    fun updateTargetRate(value: String) {
        _uiState.update { it.copy(targetRateKgPerWeek = value, clampReason = null) }
    }

    fun updateWaistCm(value: String) {
        _uiState.update { it.copy(waistCm = value) }
    }

    fun updateRestingHr(value: String) {
        _uiState.update { it.copy(restingHr = value) }
    }

    fun updateActivityLevel(value: String) {
        _uiState.update { it.copy(activityLevel = value) }
    }

    // --- Step navigation ---

    val totalSteps: Int = OnboardingStep.entries.size

    fun currentStepIndex(): Int = OnboardingStep.entries.indexOf(_uiState.value.currentStep)

    /** Whether step 1 has enough data to proceed. */
    fun isBasicsValid(): Boolean {
        val state = _uiState.value
        return state.dateOfBirth != null &&
            state.sexAtBirth != null &&
            state.heightCm.isNotBlank() &&
            state.currentWeightKg.isNotBlank()
    }

    /** Whether the target rate step should be shown. */
    fun shouldShowTargetRate(): Boolean {
        val direction = _uiState.value.energyDirection
        return direction == "cut" || direction == "gain"
    }

    /** Save step 1 (Basics) to the API and advance. */
    fun saveBasics() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                profileApi.profilePartialUpdate(
                    PatchedUserProfile(
                        dateOfBirth = state.dateOfBirth,
                        sexAtBirth = state.sexAtBirth,
                        heightCm = state.heightCm,
                        currentWeightKg = state.currentWeightKg,
                        unitPreference = state.unitPreference,
                        timezone = state.timezone,
                    ),
                )
                _uiState.update { it.copy(isSaving = false, currentStep = OnboardingStep.CYCLE_TRACKING) }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to save basics", e)
                _uiState.update { it.copy(isSaving = false, error = "Network error. Please try again.") }
            }
        }
    }

    /** Save step 2 (Cycle tracking). */
    fun saveCycleTracking() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                profileApi.profilePartialUpdate(
                    PatchedUserProfile(tracksCycle = state.tracksCycle),
                )
                _uiState.update { it.copy(isSaving = false, currentStep = OnboardingStep.GOAL) }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to save cycle tracking", e)
                _uiState.update { it.copy(isSaving = false, error = "Network error. Please try again.") }
            }
        }
    }

    /** Skip step 2 — records tracks_cycle as DECLINED. */
    fun skipCycleTracking() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val declined = (_uiState.value.declinedFields + "tracks_cycle").distinct()
                profileApi.profilePartialUpdate(
                    PatchedUserProfile(declinedFields = declined),
                )
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        tracksCycle = null,
                        declinedFields = declined.toMutableList(),
                        currentStep = OnboardingStep.GOAL,
                    )
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to skip cycle tracking", e)
                _uiState.update { it.copy(isSaving = false, error = "Network error. Please try again.") }
            }
        }
    }

    /** Save step 3 (Goal) — posts a goal and advances. */
    fun saveGoal() {
        val state = _uiState.value
        val direction = state.energyDirection ?: return
        val focus = state.trainingFocus ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val response = goalApi.goalCreate(
                    GoalRequest(
                        energyDirection = GoalRequest.EnergyDirection.valueOf(direction),
                        trainingFocus = GoalRequest.TrainingFocus.valueOf(focus),
                        targetRateKgPerWeek = state.targetRateKgPerWeek,
                        autoAdjust = true,
                    ),
                )
                val goal = response.body()
                val nextStep = if (direction == "cut" || direction == "gain") {
                    OnboardingStep.TARGET_RATE
                } else {
                    OnboardingStep.FITNESS_INPUTS
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        clampReason = goal?.clampReason,
                        targetRateKgPerWeek = goal?.targetRateKgPerWeek ?: state.targetRateKgPerWeek,
                        currentStep = nextStep,
                    )
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to save goal", e)
                _uiState.update { it.copy(isSaving = false, error = "Network error. Please try again.") }
            }
        }
    }

    /** Skip step 3 — no goal is created. */
    fun skipGoal() {
        _uiState.update { it.copy(currentStep = OnboardingStep.FITNESS_INPUTS) }
    }

    /** Save step 4 (Target rate) — re-posts goal with updated rate. */
    fun saveTargetRate() {
        val state = _uiState.value
        val direction = state.energyDirection ?: return
        val focus = state.trainingFocus ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val response = goalApi.goalCreate(
                    GoalRequest(
                        energyDirection = GoalRequest.EnergyDirection.valueOf(direction),
                        trainingFocus = GoalRequest.TrainingFocus.valueOf(focus),
                        targetRateKgPerWeek = state.targetRateKgPerWeek,
                        autoAdjust = true,
                    ),
                )
                val goal = response.body()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        clampReason = goal?.clampReason,
                        targetRateKgPerWeek = goal?.targetRateKgPerWeek ?: state.targetRateKgPerWeek,
                        currentStep = OnboardingStep.FITNESS_INPUTS,
                    )
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to save target rate", e)
                _uiState.update { it.copy(isSaving = false, error = "Network error. Please try again.") }
            }
        }
    }

    /** Skip step 4 — keep the rate from step 3. */
    fun skipTargetRate() {
        _uiState.update { it.copy(currentStep = OnboardingStep.FITNESS_INPUTS) }
    }

    /** Save step 5 (Fitness inputs). */
    fun saveFitnessInputs() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                profileApi.profilePartialUpdate(
                    PatchedUserProfile(
                        waistCm = state.waistCm.ifBlank { null },
                        restingHr = state.restingHr.toIntOrNull(),
                        activityLevel = state.activityLevel,
                    ),
                )
                _uiState.update { it.copy(isSaving = false, currentStep = OnboardingStep.COMPLETION) }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to save fitness inputs", e)
                _uiState.update { it.copy(isSaving = false, error = "Network error. Please try again.") }
            }
        }
    }

    /** Skip step 5 — records waist, resting_hr, activity_level as DECLINED. */
    fun skipFitnessInputs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val declined = (_uiState.value.declinedFields + listOf(
                    "waist_cm", "resting_hr", "activity_level",
                )).distinct()
                profileApi.profilePartialUpdate(
                    PatchedUserProfile(declinedFields = declined),
                )
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        declinedFields = declined.toMutableList(),
                        currentStep = OnboardingStep.COMPLETION,
                    )
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to skip fitness inputs", e)
                _uiState.update { it.copy(isSaving = false, error = "Network error. Please try again.") }
            }
        }
    }

    /** Final step — marks onboarding as complete. */
    fun completeOnboarding(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                profileApi.profilePartialUpdate(
                    PatchedUserProfile(
                        onboardingCompletedAt = OffsetDateTime.now(ZoneOffset.UTC),
                    ),
                )
                _uiState.update { it.copy(isSaving = false) }
                onComplete()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to complete onboarding", e)
                _uiState.update { it.copy(isSaving = false, error = "Network error. Please try again.") }
            }
        }
    }

    fun goBack() {
        val state = _uiState.value
        val steps = OnboardingStep.entries
        val currentIndex = steps.indexOf(state.currentStep)
        if (currentIndex > 0) {
            var previousIndex = currentIndex - 1
            // Skip TARGET_RATE going back if it shouldn't be shown
            if (steps[previousIndex] == OnboardingStep.TARGET_RATE && !shouldShowTargetRate()) {
                previousIndex--
            }
            if (previousIndex >= 0) {
                _uiState.update { it.copy(currentStep = steps[previousIndex]) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
