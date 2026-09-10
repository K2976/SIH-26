package com.fitforge.app.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks whether the backend has answered at least one request yet this
 * process lifetime. Render's free tier spins the service down after 15
 * minutes idle; the first request after that can take 30-50s to wake it
 * back up. [ColdStartInterceptor] drives this so the UI can show a real
 * "waking up" state instead of a frozen screen or a generic network error.
 */
object BackendWarmupState {
    private val _isWakingUp = MutableStateFlow(false)
    val isWakingUp: StateFlow<Boolean> = _isWakingUp

    @Volatile
    private var warmedUp = false

    /** True until a request has actually reached the server once. */
    val needsExtendedTimeout: Boolean get() = !warmedUp

    fun onRequestStarting() {
        _isWakingUp.value = true
    }

    /** Only a real response proves the server is awake — a timeout must not latch this. */
    fun onRequestSucceeded() {
        warmedUp = true
    }

    fun onRequestFinished() {
        _isWakingUp.value = false
    }
}
