package com.fitforge.app.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.data.remote.generated.models.ChallengeCreate
import com.fitforge.app.data.remote.generated.models.ChallengeList
import com.fitforge.app.data.remote.generated.models.ChallengeTypeEnum
import com.fitforge.app.data.remote.generated.models.CommunityDetail
import com.fitforge.app.data.remote.generated.models.CommunityMember
import com.fitforge.app.data.remote.generated.models.VisibilityEnum
import com.fitforge.app.data.repository.ChallengeRepository
import com.fitforge.app.data.repository.CommunityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlinx.datetime.Instant
import javax.inject.Inject

sealed interface CommunityDetailUiState {
    data object Loading : CommunityDetailUiState
    data class Success(val community: CommunityDetail) : CommunityDetailUiState
    data class Error(val message: String) : CommunityDetailUiState
}

sealed interface CommunityMembersUiState {
    data object Loading : CommunityMembersUiState
    data class Success(val members: List<CommunityMember>) : CommunityMembersUiState
    data class Error(val message: String) : CommunityMembersUiState
}

sealed interface CommunityChallengesUiState {
    data object Loading : CommunityChallengesUiState
    data class Success(val challenges: List<ChallengeList>) : CommunityChallengesUiState
    data class Error(val message: String) : CommunityChallengesUiState
}

@HiltViewModel
class CommunityDetailViewModel @Inject constructor(
    private val communityRepository: CommunityRepository,
    private val challengeRepository: ChallengeRepository,
) : ViewModel() {

    private val _detailState = MutableStateFlow<CommunityDetailUiState>(CommunityDetailUiState.Loading)
    val detailState: StateFlow<CommunityDetailUiState> = _detailState.asStateFlow()

    private val _membersState = MutableStateFlow<CommunityMembersUiState>(CommunityMembersUiState.Loading)
    val membersState: StateFlow<CommunityMembersUiState> = _membersState.asStateFlow()

    private val _challengesState = MutableStateFlow<CommunityChallengesUiState>(CommunityChallengesUiState.Loading)
    val challengesState: StateFlow<CommunityChallengesUiState> = _challengesState.asStateFlow()

    private val _selectedChallengeFilter = MutableStateFlow("all")
    val selectedChallengeFilter: StateFlow<String> = _selectedChallengeFilter.asStateFlow()

    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress: StateFlow<Boolean> = _actionInProgress.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private var currentCommunityId: Int? = null

    fun loadCommunity(id: Int) {
        currentCommunityId = id
        viewModelScope.launch {
            _detailState.value = CommunityDetailUiState.Loading
            _membersState.value = CommunityMembersUiState.Loading
            _challengesState.value = CommunityChallengesUiState.Loading
            refreshInternal(id)
        }
    }

    fun refresh() {
        val id = currentCommunityId ?: return
        viewModelScope.launch {
            refreshInternal(id)
        }
    }

    private suspend fun refreshInternal(id: Int) {
        // Fetch detail
        communityRepository.getCommunityDetail(id)
            .onSuccess { detail ->
                _detailState.value = CommunityDetailUiState.Success(detail)
            }
            .onFailure { error ->
                _detailState.value = CommunityDetailUiState.Error(
                    error.message ?: "Failed to load community details"
                )
            }

        // Fetch members
        communityRepository.getCommunityMembers(id)
            .onSuccess { members ->
                _membersState.value = CommunityMembersUiState.Success(members)
            }
            .onFailure { error ->
                _membersState.value = CommunityMembersUiState.Error(
                    error.message ?: "Failed to load community members"
                )
            }

        // Fetch challenges
        val filter = _selectedChallengeFilter.value
        challengeRepository.getChallenges(id, filter)
            .onSuccess { challenges ->
                _challengesState.value = CommunityChallengesUiState.Success(challenges)
            }
            .onFailure { error ->
                _challengesState.value = CommunityChallengesUiState.Error(
                    error.message ?: "Failed to load challenges"
                )
            }
    }

    fun setChallengeFilter(filter: String) {
        _selectedChallengeFilter.value = filter
        val id = currentCommunityId ?: return
        viewModelScope.launch {
            _challengesState.value = CommunityChallengesUiState.Loading
            challengeRepository.getChallenges(id, filter)
                .onSuccess { challenges ->
                    _challengesState.value = CommunityChallengesUiState.Success(challenges)
                }
                .onFailure { error ->
                    _challengesState.value = CommunityChallengesUiState.Error(
                        error.message ?: "Failed to load challenges"
                    )
                }
        }
    }

    fun joinCommunity(onSuccess: () -> Unit = {}) {
        val id = currentCommunityId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionError.value = null
            communityRepository.joinCommunity(id)
                .onSuccess {
                    refreshInternal(id)
                    onSuccess()
                }
                .onFailure { error ->
                    _actionError.value = error.message ?: "Failed to join community"
                }
            _actionInProgress.value = false
        }
    }

    fun leaveCommunity(onSuccess: () -> Unit = {}) {
        val id = currentCommunityId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionError.value = null
            communityRepository.leaveCommunity(id)
                .onSuccess {
                    refreshInternal(id)
                    onSuccess()
                }
                .onFailure { error ->
                    _actionError.value = error.message ?: "Failed to leave community"
                }
            _actionInProgress.value = false
        }
    }

    fun joinChallenge(challengeId: Int, onSuccess: () -> Unit = {}) {
        val id = currentCommunityId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionError.value = null
            challengeRepository.joinChallenge(id, challengeId)
                .onSuccess {
                    refreshInternal(id)
                    onSuccess()
                }
                .onFailure { error ->
                    _actionError.value = error.message ?: "Failed to join challenge"
                }
            _actionInProgress.value = false
        }
    }

    fun createChallenge(
        title: String,
        description: String,
        type: ChallengeTypeEnum,
        target: Double,
        durationDays: Int,
        onSuccess: () -> Unit = {},
    ) {
        val id = currentCommunityId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionError.value = null
            val now = Instant.fromEpochMilliseconds(java.lang.System.currentTimeMillis())
            val end = Instant.fromEpochMilliseconds(
                java.lang.System.currentTimeMillis() + durationDays.toLong() * 86_400_000L,
            )
            val request = ChallengeCreate(
                title = title,
                description = description,
                challengeType = type,
                targetValue = BigDecimal.valueOf(target),
                startDate = now,
                endDate = end,
            )
            challengeRepository.createChallenge(id, request)
                .onSuccess {
                    refreshInternal(id)
                    onSuccess()
                }
                .onFailure { error ->
                    _actionError.value = error.message ?: "Failed to create challenge"
                }
            _actionInProgress.value = false
        }
    }

    fun updateCommunity(
        name: String? = null,
        description: String? = null,
        visibility: VisibilityEnum? = null,
        onSuccess: () -> Unit = {},
    ) {
        val id = currentCommunityId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionError.value = null
            communityRepository.updateCommunity(id, name, description, visibility)
                .onSuccess {
                    refreshInternal(id)
                    onSuccess()
                }
                .onFailure { error ->
                    _actionError.value = error.message ?: "Failed to update community"
                }
            _actionInProgress.value = false
        }
    }

    fun deleteCommunity(onSuccess: () -> Unit = {}) {
        val id = currentCommunityId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionError.value = null
            communityRepository.deleteCommunity(id)
                .onSuccess {
                    onSuccess()
                }
                .onFailure { error ->
                    _actionError.value = error.message ?: "Failed to delete community"
                }
            _actionInProgress.value = false
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }
}
