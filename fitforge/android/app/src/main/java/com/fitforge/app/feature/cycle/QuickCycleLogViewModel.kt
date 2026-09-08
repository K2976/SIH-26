package com.fitforge.app.feature.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.core.database.CycleLogEntity
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.repository.CachedUserState
import com.fitforge.app.data.repository.CycleRepository
import com.fitforge.app.data.repository.UserStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fixed, ranked symptom set — deliberately small so the picker stays a
 * quick tap, not a form. Storage keys, not display text; labels live in
 * strings.xml (feature/cycle/QuickCycleLogCard.kt). */
val CYCLE_SYMPTOM_KEYS = listOf(
    "cramps", "fatigue", "headache", "bloating",
    "mood_swings", "tender_breasts", "acne", "back_pain",
)

data class QuickCycleLogUiState(
    /** False whenever the card shouldn't render at all: cycle tracking is
     * off, discreet mode is on, or she's already dismissed it this session. */
    val visible: Boolean = false,
    val hasLoggedToday: Boolean = false,
    val loggedEnergyRating: Int? = null,
    val loggedSymptoms: Set<String> = emptySet(),
    val showSymptomPicker: Boolean = false,
)

/** Pure — no repository, no ViewModel — so the "when does this card show,
 * and what does it show" logic is unit-testable on its own. */
internal fun computeQuickCycleLogUiState(
    cycleEnabled: Boolean,
    discreetMode: Boolean,
    dismissedThisSession: Boolean,
    todaysLogs: List<CycleLogEntity>,
    showSymptomPicker: Boolean,
): QuickCycleLogUiState {
    if (!cycleEnabled || discreetMode || dismissedThisSession) {
        return QuickCycleLogUiState(visible = false)
    }
    val loggedEnergyRating = todaysLogs.firstOrNull { it.energyRating != null }?.energyRating
    val loggedSymptoms = todaysLogs.flatMap { it.symptoms }.toSet()
    return QuickCycleLogUiState(
        visible = true,
        hasLoggedToday = todaysLogs.isNotEmpty(),
        loggedEnergyRating = loggedEnergyRating,
        loggedSymptoms = loggedSymptoms,
        showSymptomPicker = showSymptomPicker,
    )
}

/**
 * Backs the one-tap daily check-in on the dashboard. The entire module
 * depends on this interaction happening (§10) — logging must stay a single
 * tap, with symptoms as a fully optional, equally-weighted extra tap, and
 * skipping must carry no guilt framing (no streaks, no warnings).
 */
@HiltViewModel
class QuickCycleLogViewModel @Inject constructor(
    private val cycleRepository: CycleRepository,
    userStateRepository: UserStateRepository,
    preferencesDataStore: UserPreferencesDataStore,
) : ViewModel() {

    private val today = cycleRepository.today()
    private val dismissedThisSession = MutableStateFlow(false)
    private val showSymptomPicker = MutableStateFlow(false)

    val uiState: StateFlow<QuickCycleLogUiState> = combine(
        userStateRepository.userState,
        preferencesDataStore.cycleDiscreetMode,
        dismissedThisSession,
        cycleRepository.logsFor(today),
        showSymptomPicker,
    ) { state: CachedUserState?, discreetMode, dismissed, todaysLogs, pickerOpen ->
        computeQuickCycleLogUiState(
            cycleEnabled = state?.cycle?.enabled == true,
            discreetMode = discreetMode,
            dismissedThisSession = dismissed,
            todaysLogs = todaysLogs,
            showSymptomPicker = pickerOpen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickCycleLogUiState())

    /** The 3-second happy path: one tap logs energy and nothing else. */
    fun logEnergy(rating: Int) {
        viewModelScope.launch {
            cycleRepository.logSymptoms(today, energyRating = rating, symptoms = emptyList())
        }
    }

    fun toggleSymptomPicker() {
        showSymptomPicker.update { !it }
    }

    /** A separate, optional entry — merges with today's energy rating on
     * read (apps/engine/cycle_service.py unions same-day symptoms). */
    fun logSymptoms(symptoms: Set<String>) {
        viewModelScope.launch {
            cycleRepository.logSymptoms(today, energyRating = null, symptoms = symptoms.toList())
            showSymptomPicker.value = false
        }
    }

    /** No guilt: this writes nothing. A skipped day is genuinely unlogged,
     * not a fabricated "no symptoms" entry (missing is not zero). */
    fun skipToday() {
        dismissedThisSession.value = true
    }
}
