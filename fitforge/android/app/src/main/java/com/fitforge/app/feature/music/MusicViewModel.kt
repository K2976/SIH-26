@file:Suppress("MagicNumber", "ReturnCount")

package com.fitforge.app.feature.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.core.database.NO_CYCLE_PHASE
import com.fitforge.app.core.database.TrainingMusicMappingEntity
import com.fitforge.app.data.repository.CachedUserState
import com.fitforge.app.data.repository.ReferenceRepository
import com.fitforge.app.data.repository.UserStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MusicRecommendation(
    val genre: String,
    val trainingFocus: String,
    /** Null when the pick fell back to the focus-only row — see
     * [computeMusicRecommendation]. */
    val cyclePhase: String?,
    val deepLinkUrl: String,
)

/**
 * Pure — no repository, no ViewModel — so the lookup-plus-fallback logic is
 * unit-testable on its own. Tries (focus, phase) first; when phase is null
 * (not natural tracking, or insufficient cycle history — apps/engine/cycle.py)
 * or no row matches that exact phase, falls back to the focus-only row.
 * No YouTube API, no OAuth, no quota: the result is a plain https deep link.
 */
internal fun computeMusicRecommendation(
    trainingFocus: String?,
    cyclePhase: String?,
    mappings: List<TrainingMusicMappingEntity>,
): MusicRecommendation? {
    if (trainingFocus == null) return null

    val exactMatch = cyclePhase?.let { phase ->
        mappings.firstOrNull { it.trainingFocus == trainingFocus && it.cyclePhase == phase }
    }
    val match = exactMatch
        ?: mappings.firstOrNull { it.trainingFocus == trainingFocus && it.cyclePhase == NO_CYCLE_PHASE }
        ?: return null

    return MusicRecommendation(
        genre = match.genre,
        trainingFocus = trainingFocus,
        cyclePhase = if (exactMatch != null) cyclePhase else null,
        deepLinkUrl = "https://www.youtube.com/playlist?list=${match.youtubePlaylistId}",
    )
}

/** Backs the workout-music card: reads UserState (training focus, cycle
 * phase) and the locally cached reference mapping, and nothing else. */
@HiltViewModel
class MusicViewModel @Inject constructor(
    userStateRepository: UserStateRepository,
    referenceRepository: ReferenceRepository,
) : ViewModel() {

    val recommendation: StateFlow<MusicRecommendation?> = combine(
        userStateRepository.userState,
        referenceRepository.musicMappings,
    ) { state: CachedUserState?, mappings ->
        computeMusicRecommendation(
            trainingFocus = state?.goal?.trainingFocus,
            cyclePhase = state?.cycle?.phase,
            mappings = mappings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
