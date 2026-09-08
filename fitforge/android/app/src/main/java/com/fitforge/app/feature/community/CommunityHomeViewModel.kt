package com.fitforge.app.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.data.remote.generated.models.CommunityList
import com.fitforge.app.data.repository.CommunityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CommunityListUiState {
    data object Loading : CommunityListUiState
    data class Success(val communities: List<CommunityList>) : CommunityListUiState
    data object Empty : CommunityListUiState
    data class Error(val message: String) : CommunityListUiState
}

private const val SEARCH_DEBOUNCE_MS = 350L

@HiltViewModel
class CommunityHomeViewModel @Inject constructor(
    private val communityRepository: CommunityRepository,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0) // 0 = My Communities, 1 = Discover
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _myCommunitiesState = MutableStateFlow<CommunityListUiState>(CommunityListUiState.Loading)
    val myCommunitiesState: StateFlow<CommunityListUiState> = _myCommunitiesState.asStateFlow()

    private val _discoverState = MutableStateFlow<CommunityListUiState>(CommunityListUiState.Loading)
    val discoverState: StateFlow<CommunityListUiState> = _discoverState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadData()
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
        if (tabIndex == 1 && _discoverState.value is CommunityListUiState.Loading) {
            loadDiscoverCommunities()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loadDiscoverCommunities(query)
        }
    }

    fun clearSearchQuery() {
        _searchQuery.value = ""
        loadDiscoverCommunities(null)
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                loadMyCommunitiesInternal()
                loadDiscoverCommunitiesInternal(_searchQuery.value)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            loadMyCommunitiesInternal()
            loadDiscoverCommunitiesInternal(_searchQuery.value)
        }
    }

    private suspend fun loadMyCommunitiesInternal() {
        communityRepository.getMyCommunities()
            .onSuccess { list ->
                _myCommunitiesState.value = if (list.isEmpty()) {
                    CommunityListUiState.Empty
                } else {
                    CommunityListUiState.Success(list)
                }
            }
            .onFailure { error ->
                _myCommunitiesState.value = CommunityListUiState.Error(
                    error.message ?: "Failed to load your communities"
                )
            }
    }

    private suspend fun loadDiscoverCommunitiesInternal(query: String? = null) {
        communityRepository.getDiscoverCommunities(search = query)
            .onSuccess { list ->
                _discoverState.value = if (list.isEmpty()) {
                    CommunityListUiState.Empty
                } else {
                    CommunityListUiState.Success(list)
                }
            }
            .onFailure { error ->
                _discoverState.value = CommunityListUiState.Error(
                    error.message ?: "Failed to discover communities"
                )
            }
    }

    fun loadDiscoverCommunities(query: String? = null) {
        viewModelScope.launch {
            _discoverState.value = CommunityListUiState.Loading
            loadDiscoverCommunitiesInternal(query)
        }
    }
}
