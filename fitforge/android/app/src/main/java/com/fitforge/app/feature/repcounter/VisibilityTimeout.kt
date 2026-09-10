package com.fitforge.app.feature.repcounter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fires [onTimeout] after [timeoutMs] of real elapsed time on [scope],
 * independent of whatever else is or isn't happening in that window. Used to
 * bound [RepCounterUiState.VisibilityCheck]: that state used to re-check its
 * own deadline only from inside the frame-analysis callback, so a stalled
 * camera pipeline (zero frames delivered) meant the deadline was never
 * re-checked and the user was stuck. A plain coroutine delay has no such
 * dependency on frames arriving.
 */
class VisibilityTimeout(
    private val scope: CoroutineScope,
    private val timeoutMs: Long,
    private val onTimeout: () -> Unit,
) {
    private var job: Job? = null

    /** (Re)starts the countdown from now, replacing any window already in progress. */
    fun start() {
        job?.cancel()
        job = scope.launch {
            delay(timeoutMs)
            onTimeout()
        }
    }

    /** Stops the countdown — call whenever the check resolves before the deadline. */
    fun cancel() {
        job?.cancel()
        job = null
    }
}
