package com.fitforge.app.feature.health

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
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class HealthConnectUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.Available,
    val hasAllPermissions: Boolean = false,
    val grantedPermissionsCount: Int = 0,
    val totalPermissionsCount: Int = 0,
    val isLoading: Boolean = false,
    val healthData: HealthDataSummary? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthConnectUiState())
    val uiState: StateFlow<HealthConnectUiState> = _uiState.asStateFlow()

    init {
        checkAvailabilityAndPermissions()
    }

    fun getRequiredPermissions(): Set<String> {
        return healthRepository.getRequiredPermissions()
    }

    fun checkAvailabilityAndPermissions() {
        viewModelScope.launch {
            val availability = healthRepository.getAvailability()
            val required = healthRepository.getRequiredPermissions()
            val granted = healthRepository.getGrantedPermissions()
            val hasAll = required.isNotEmpty() && required.all { it in granted }

            _uiState.update {
                it.copy(
                    availability = availability,
                    hasAllPermissions = hasAll,
                    grantedPermissionsCount = granted.size,
                    totalPermissionsCount = required.size,
                )
            }

            if (availability == HealthConnectAvailability.Available && granted.isNotEmpty()) {
                loadHealthData()
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val now = Instant.now()
            // Query past 24 hours for daily metrics and recent sleep/workouts
            val startWindow = now.minus(24, ChronoUnit.HOURS)

            val result = healthRepository.readHealthSummary(startWindow, now)
            result.fold(
                onSuccess = { data ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            healthData = data,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
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
