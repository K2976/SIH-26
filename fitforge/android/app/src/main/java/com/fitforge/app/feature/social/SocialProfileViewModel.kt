package com.fitforge.app.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.data.remote.generated.models.SocialProfile
import com.fitforge.app.data.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MyProfileUiState {
    data object Loading : MyProfileUiState
    data object NoProfile : MyProfileUiState
    data class Success(val profile: SocialProfile) : MyProfileUiState
    data class Error(val message: String) : MyProfileUiState
}

sealed interface UsernameCheckUiState {
    data object Idle : UsernameCheckUiState
    data object Checking : UsernameCheckUiState
    data class Available(val message: String) : UsernameCheckUiState
    data class Taken(val message: String) : UsernameCheckUiState
    data class Invalid(val message: String) : UsernameCheckUiState
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Searching : SearchUiState
    data class Results(val users: List<SocialProfile>) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}

private const val MIN_HANDLE_LENGTH = 3
private const val MAX_HANDLE_LENGTH = 32
private const val USERNAME_DEBOUNCE_MS = 350L
private const val SEARCH_DEBOUNCE_MS = 300L
private val USERNAME_REGEX_PATTERN = Regex("^[a-zA-Z0-9_]{3,32}$")

@HiltViewModel
class SocialProfileViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
) : ViewModel() {

    private val _profileState = MutableStateFlow<MyProfileUiState>(MyProfileUiState.Loading)
    val profileState: StateFlow<MyProfileUiState> = _profileState.asStateFlow()

    private val _usernameCheckState = MutableStateFlow<UsernameCheckUiState>(UsernameCheckUiState.Idle)
    val usernameCheckState: StateFlow<UsernameCheckUiState> = _usernameCheckState.asStateFlow()

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _selectedPublicProfile = MutableStateFlow<SocialProfile?>(null)
    val selectedPublicProfile: StateFlow<SocialProfile?> = _selectedPublicProfile.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _actionErrorMessage = MutableStateFlow<String?>(null)
    val actionErrorMessage: StateFlow<String?> = _actionErrorMessage.asStateFlow()

    private var usernameCheckJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadMyProfile()
    }

    fun loadMyProfile() {
        viewModelScope.launch {
            _profileState.value = MyProfileUiState.Loading
            socialRepository.getMyProfile()
                .onSuccess { profile ->
                    _profileState.value = if (profile != null) {
                        MyProfileUiState.Success(profile)
                    } else {
                        MyProfileUiState.NoProfile
                    }
                }
                .onFailure { error ->
                    _profileState.value = MyProfileUiState.Error(
                        error.message ?: "Failed to load social profile"
                    )
                }
        }
    }

    fun checkUsernameDebounced(candidate: String, currentUsername: String? = null) {
        usernameCheckJob?.cancel()
        val clean = candidate.trim().removePrefix("@")
        when {
            clean.isEmpty() -> {
                _usernameCheckState.value = UsernameCheckUiState.Idle
            }
            currentUsername != null && clean.equals(currentUsername.removePrefix("@"), ignoreCase = true) -> {
                _usernameCheckState.value = UsernameCheckUiState.Available("Your current username")
            }
            clean.length < MIN_HANDLE_LENGTH ||
                clean.length > MAX_HANDLE_LENGTH ||
                !clean.matches(USERNAME_REGEX_PATTERN) -> {
                _usernameCheckState.value = UsernameCheckUiState.Invalid(
                    "Must be 3-32 characters (letters, numbers, underscores only)"
                )
            }
            else -> {
                usernameCheckJob = viewModelScope.launch {
                    _usernameCheckState.value = UsernameCheckUiState.Checking
                    delay(USERNAME_DEBOUNCE_MS)
                    socialRepository.checkUsername(clean)
                        .onSuccess { result ->
                            _usernameCheckState.value = if (result.available) {
                                UsernameCheckUiState.Available(result.message)
                            } else {
                                UsernameCheckUiState.Taken(result.message)
                            }
                        }
                        .onFailure {
                            _usernameCheckState.value = UsernameCheckUiState.Invalid(
                                "Unable to verify username availability"
                            )
                        }
                }
            }
        }
    }

    fun createProfile(
        username: String,
        displayName: String,
        bio: String,
        isPrivate: Boolean,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _actionErrorMessage.value = null
            socialRepository.createProfile(username, displayName, bio, isPrivate)
                .onSuccess { newProfile ->
                    _isSubmitting.value = false
                    _profileState.value = MyProfileUiState.Success(newProfile)
                    onSuccess()
                }
                .onFailure { error ->
                    _isSubmitting.value = false
                    _actionErrorMessage.value = error.message ?: "Failed to create profile"
                }
        }
    }

    fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        isPrivate: Boolean,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _actionErrorMessage.value = null
            socialRepository.updateProfile(username, displayName, bio, isPrivate)
                .onSuccess { updatedProfile ->
                    _isSubmitting.value = false
                    _profileState.value = MyProfileUiState.Success(updatedProfile)
                    onSuccess()
                }
                .onFailure { error ->
                    _isSubmitting.value = false
                    _actionErrorMessage.value = error.message ?: "Failed to update profile"
                }
        }
    }

    fun searchUsers(query: String) {
        searchJob?.cancel()
        val clean = query.trim().removePrefix("@")
        if (clean.isEmpty()) {
            _searchState.value = SearchUiState.Idle
            return
        }

        searchJob = viewModelScope.launch {
            _searchState.value = SearchUiState.Searching
            delay(SEARCH_DEBOUNCE_MS)
            socialRepository.searchUsers(clean)
                .onSuccess { list ->
                    _searchState.value = if (list.isEmpty()) {
                        SearchUiState.Empty
                    } else {
                        SearchUiState.Results(list)
                    }
                }
                .onFailure { error ->
                    _searchState.value = SearchUiState.Error(
                        error.message ?: "Search failed"
                    )
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchState.value = SearchUiState.Idle
    }

    fun selectPublicProfile(profile: SocialProfile?) {
        _selectedPublicProfile.value = profile
    }

    fun clearActionError() {
        _actionErrorMessage.value = null
    }
}
