package com.fitforge.app.feature.health

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.domain.model.HealthConnectAvailability
import com.fitforge.app.domain.model.HealthDataSummary
import com.fitforge.app.domain.repository.HealthRepository
import com.fitforge.app.sync.HealthConnectSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val TAG = "HealthConnectViewModel"

data class HealthConnectUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.Available,
    val hasAllPermissions: Boolean = false,
    val grantedPermissionsCount: Int = 0,
    val totalPermissionsCount: Int = 0,
    val isLoading: Boolean = false,
    val healthData: HealthDataSummary? = null,
    val errorMessage: String? = null,
    val lastSyncedAt: Instant? = null,
)

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
    private val syncScheduler: HealthConnectSyncScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthConnectUiState())
    val uiState: StateFlow<HealthConnectUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "HealthConnectViewModel created")
        observePersistentHealthData()
        checkAvailabilityAndPermissions()
    }

    private fun observePersistentHealthData() {
        viewModelScope.launch {
            healthRepository.latestHealthData.collect { data ->
                if (data != null) {
                    _uiState.update { it.copy(healthData = data) }
                }
            }
        }
        viewModelScope.launch {
            healthRepository.lastSyncedAt.collect { timestamp ->
                if (timestamp != null) {
                    _uiState.update { it.copy(lastSyncedAt = timestamp) }
                }
            }
        }
    }

    fun getRequiredPermissions(): Set<String> {
        return healthRepository.getRequiredPermissions()
    }

    fun checkAvailabilityAndPermissions() {
        Log.d(TAG, "checkAvailabilityAndPermissions called")
        viewModelScope.launch {
            val availability = healthRepository.getAvailability()
            val required = healthRepository.getRequiredPermissions()
            val granted = healthRepository.getGrantedPermissions()
            val hasAll = required.isNotEmpty() && required.all { it in granted }

            Log.d(TAG, "availability=$availability, required=$required, granted=$granted, hasAll=$hasAll")

            _uiState.update {
                it.copy(
                    availability = availability,
                    hasAllPermissions = hasAll,
                    grantedPermissionsCount = granted.size,
                    totalPermissionsCount = required.size,
                )
            }

            if (availability == HealthConnectAvailability.Available && granted.isNotEmpty()) {
                Log.d(TAG, "Scheduling periodic sync and performing initial data load")
                syncScheduler.schedulePeriodicSync()
                loadHealthData()
            } else {
                Log.d(TAG, "Permissions missing or Health Connect unavailable; cancelling periodic sync")
                syncScheduler.cancelPeriodicSync()
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        Log.d(TAG, "onPermissionsResult called with granted=$granted")
        viewModelScope.launch {
            val required = healthRepository.getRequiredPermissions()
            val hasAll = required.isNotEmpty() && required.all { it in granted }

            _uiState.update {
                it.copy(
                    hasAllPermissions = hasAll,
                    grantedPermissionsCount = granted.size,
                    totalPermissionsCount = required.size,
                )
            }

            if (granted.isNotEmpty()) {
                Log.d(TAG, "Permissions granted: scheduling periodic sync and triggering initial sync")
                syncScheduler.schedulePeriodicSync()
                loadHealthData()
            } else {
                Log.d(TAG, "Permissions revoked/empty: cancelling periodic sync")
                syncScheduler.cancelPeriodicSync()
            }
        }
    }

    fun loadHealthData() {
        Log.d(TAG, "loadHealthData (Sync Now) called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = healthRepository.syncHealthData()
            result.fold(
                onSuccess = { data ->
                    Log.d(TAG, "Health data sync succeeded: $data")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            healthData = data,
                            errorMessage = null,
                            lastSyncedAt = data.lastSyncedAt,
                        )
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to sync health data", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to read health data",
                        )
                    }
                },
            )
        }
    }
}

