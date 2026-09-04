package com.fitforge.app.core.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.BuildConfig
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.remote.generated.apis.AppConfigApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

sealed interface StartupState {
    data object Loading : StartupState
    data class UpdateRequired(val minSupportedVersion: String) : StartupState
    data class Ready(val isLoggedIn: Boolean) : StartupState
}

/**
 * Decides, once at process start, whether the app is blocked by the
 * version gate and whether a stored session already exists. Pure
 * plumbing — the only "decision" here is a string version compare.
 */
@HiltViewModel
class AppStartupViewModel @Inject constructor(
    private val appConfigApi: AppConfigApi,
    private val preferences: UserPreferencesDataStore,
) : ViewModel() {

    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val minSupportedVersion = fetchMinSupportedVersion()
            if (minSupportedVersion != null && isVersionBelow(BuildConfig.VERSION_NAME, minSupportedVersion)) {
                _state.value = StartupState.UpdateRequired(minSupportedVersion)
                return@launch
            }
            _state.value = StartupState.Ready(isLoggedIn = preferences.accessToken.first() != null)
        }
    }

    // The generated method returns Response<T> (useCoroutines only adds
    // `suspend`, it doesn't unwrap the body).
    private suspend fun fetchMinSupportedVersion(): String? = try {
        appConfigApi.appConfigRetrieve().body()?.minSupportedVersion
    } catch (e: IOException) {
        // Backend unreachable — don't block startup on a check that can't complete.
        null
    }
}
