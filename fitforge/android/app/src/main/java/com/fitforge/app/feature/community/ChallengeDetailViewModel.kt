package com.fitforge.app.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.data.remote.generated.models.ChallengeDetail
import com.fitforge.app.data.repository.ChallengeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChallengeDetailUiState {
    data object Loading : ChallengeDetailUiState
    data class Success(val challenge: ChallengeDetail) : ChallengeDetailUiState
    data class Error(val message: String) : ChallengeDetailUiState
}

@HiltViewModel
class ChallengeDetailViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository,
) : ViewModel() {

    private val _detailState = MutableStateFlow<ChallengeDetailUiState>(ChallengeDetailUiState.Loading)
    val detailState: StateFlow<ChallengeDetailUiState> = _detailState.asStateFlow()

    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress: StateFlow<Boolean> = _actionInProgress.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private var currentCommunityId: Int? = null
    private var currentChallengeId: Int? = null

    fun loadChallenge(communityId: Int, challengeId: Int) {
        currentCommunityId = communityId
        currentChallengeId = challengeId
        viewModelScope.launch {
            _detailState.value = ChallengeDetailUiState.Loading
            refreshInternal(communityId, challengeId)
        }
    }

    fun refresh() {
        val communityId = currentCommunityId ?: return
        val challengeId = currentChallengeId ?: return
        viewModelScope.launch {
            refreshInternal(communityId, challengeId)
        }
    }

    private suspend fun refreshInternal(communityId: Int, challengeId: Int) {
        challengeRepository.getChallengeDetail(communityId, challengeId)
            .onSuccess { detail ->
                _detailState.value = ChallengeDetailUiState.Success(detail)
            }
            .onFailure { error ->
                _detailState.value = ChallengeDetailUiState.Error(
                    error.message ?: "Failed to load challenge details"
                )
            }
    }

    fun joinChallenge(onSuccess: () -> Unit = {}) {
        val communityId = currentCommunityId ?: return
        val challengeId = currentChallengeId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionError.value = null
            challengeRepository.joinChallenge(communityId, challengeId)
                .onSuccess {
                    refreshInternal(communityId, challengeId)
                    onSuccess()
                }
                .onFailure { error ->
                    _actionError.value = error.message ?: "Failed to join challenge"
                }
            _actionInProgress.value = false
        }
    }

    fun leaveChallenge(onSuccess: () -> Unit = {}) {
        val communityId = currentCommunityId ?: return
        val challengeId = currentChallengeId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionError.value = null
            challengeRepository.leaveChallenge(communityId, challengeId)
                .onSuccess {
                    refreshInternal(communityId, challengeId)
                    onSuccess()
                }
                .onFailure { error ->
                    _actionError.value = error.message ?: "Failed to leave challenge"
                }
            _actionInProgress.value = false
        }
    }

    fun deleteChallenge(onSuccess: () -> Unit = {}) {
        val communityId = currentCommunityId ?: return
        val challengeId = currentChallengeId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionError.value = null
            challengeRepository.deleteChallenge(communityId, challengeId)
                .onSuccess {
                    onSuccess()
                }
                .onFailure { error ->
                    _actionError.value = error.message ?: "Failed to delete challenge"
                }
            _actionInProgress.value = false
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }
}
