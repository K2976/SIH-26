package com.fitforge.app.feature.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.remote.generated.apis.AuthApi
import com.fitforge.app.data.remote.generated.models.EmailTokenObtainPair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

private const val TAG = "LoginViewModel"

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object Error : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val preferences: UserPreferencesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                // Generated methods return retrofit2.Response<T> (useCoroutines
                // only adds `suspend`, it doesn't unwrap the body) — a 401
                // shows up as !isSuccessful, not a thrown exception.
                val response = authApi.authLoginCreate(
                    EmailTokenObtainPair(email = email, password = password),
                )
                val tokenPair = response.body()
                if (response.isSuccessful && tokenPair != null) {
                    preferences.saveSession(accessToken = tokenPair.access, refreshToken = tokenPair.refresh)
                    _uiState.value = LoginUiState.Idle
                    onSuccess()
                } else {
                    _uiState.value = LoginUiState.Error
                }
            } catch (e: IOException) {
                Log.w(TAG, "Login call failed", e)
                _uiState.value = LoginUiState.Error
            }
        }
    }
}
