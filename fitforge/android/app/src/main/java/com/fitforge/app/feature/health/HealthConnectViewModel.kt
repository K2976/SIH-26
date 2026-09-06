package com.fitforge.app.feature.health

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.domain.model.HealthConnectAvailability
import com.fitforge.app.domain.model.HealthDataSummary
import com.fitforge.app.domain.repository.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthConnectUiState())
    val uiState: StateFlow<HealthConnectUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "HealthConnectViewModel created")
        checkAvailabilityAndPermissions()
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
                Log.d(TAG, "Loading health data...")
                loadHealthData()
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
                loadHealthData()
            }
        }
    }

    fun loadHealthData() {
        Log.d(TAG, "loadHealthData called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val now = Instant.now()
            // Query from start of current day in user's local timezone
            val startOfDay = ZonedDateTime.now(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant()
            Log.d(TAG, "Querying health data from $startOfDay (start of day) to $now")

            val result = healthRepository.readHealthSummary(startOfDay, now)
            result.fold(
                onSuccess = { data ->
                    Log.d(TAG, "Health data loaded successfully: $data")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            healthData = data,
                            errorMessage = null,
                            lastSyncedAt = Instant.now(),
                        )
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to load health data", error)
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
