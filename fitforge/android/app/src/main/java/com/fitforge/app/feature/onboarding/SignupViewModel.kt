package com.fitforge.app.feature.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.remote.generated.apis.AuthApi
import com.fitforge.app.data.remote.generated.models.Signup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

private const val TAG = "SignupViewModel"

sealed interface SignupUiState {
    data object Idle : SignupUiState
    data object Loading : SignupUiState
    data object Error : SignupUiState
}

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val preferences: UserPreferencesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignupUiState>(SignupUiState.Idle)
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    fun signup(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = SignupUiState.Loading
            try {
                val response = authApi.authSignupCreate(Signup(email = email, password = password))
                val tokenPair = response.body()
                if (response.isSuccessful && tokenPair != null) {
                    preferences.saveSession(accessToken = tokenPair.access, refreshToken = tokenPair.refresh)
                    _uiState.value = SignupUiState.Idle
                    onSuccess()
                } else {
                    _uiState.value = SignupUiState.Error
                }
            } catch (e: IOException) {
                Log.w(TAG, "Signup call failed", e)
                _uiState.value = SignupUiState.Error
            }
        }
    }
}
