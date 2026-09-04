package com.fitforge.app.core.startup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.BuildConfig
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.remote.generated.apis.AppConfigApi
import com.fitforge.app.data.remote.generated.apis.ProfileApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

private const val TAG = "AppStartupViewModel"

sealed interface StartupState {
    data object Loading : StartupState
    data class UpdateRequired(val minSupportedVersion: String) : StartupState
    data class Ready(val isLoggedIn: Boolean, val needsOnboarding: Boolean) : StartupState
}

/**
 * Decides, once at process start, whether the app is blocked by the
 * version gate, whether a stored session already exists, and whether
 * onboarding has been completed. Pure plumbing — the only "decisions"
 * here are a string version compare and a null check.
 */
@HiltViewModel
class AppStartupViewModel @Inject constructor(
    private val appConfigApi: AppConfigApi,
    private val profileApi: ProfileApi,
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
            val hasToken = preferences.accessToken.first() != null
            val needsOnboarding = if (hasToken) checkOnboardingNeeded() else true
            _state.value = StartupState.Ready(isLoggedIn = hasToken, needsOnboarding = needsOnboarding)
        }
    }

    // The generated method returns Response<T> (useCoroutines only adds
    // `suspend`, it doesn't unwrap the body).
    private suspend fun fetchMinSupportedVersion(): String? = try {
        appConfigApi.appConfigRetrieve().body()?.minSupportedVersion
    } catch (e: IOException) {
        // Backend unreachable — don't block startup on a check that can't complete.
        Log.w(TAG, "app-config check failed", e)
        null
    }

    /**
     * Checks whether onboarding has been completed by fetching the profile
     * and looking at onboarding_completed_at.
     */
    private suspend fun checkOnboardingNeeded(): Boolean = try {
        val response = profileApi.profileRetrieve()
        val profile = response.body()
        profile?.currentWeightKg?.toDouble()?.let { preferences.setBodyweightKg(it) }
        profile?.onboardingCompletedAt == null
    } catch (e: IOException) {
        Log.w(TAG, "profile check failed", e)
        // Can't reach backend — assume onboarding needed; the onboarding
        // screen will show its own errors if the network stays down.
        true
    }
}
