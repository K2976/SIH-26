package com.fitforge.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Render's free tier can take 30-50s to serve the first request after the
 * instance spins down from inactivity. OkHttp's default 10s read timeout
 * would fail that request outright, so extend it only until the process has
 * seen one successful response — every later request uses the normal
 * client timeout.
 */
class ColdStartInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!BackendWarmupState.needsExtendedTimeout) {
            return chain.proceed(chain.request())
        }
        BackendWarmupState.onRequestStarting()
        try {
            val response = chain
                .withConnectTimeout(COLD_START_TIMEOUT_SECONDS.toInt(), TimeUnit.SECONDS)
                .withReadTimeout(COLD_START_TIMEOUT_SECONDS.toInt(), TimeUnit.SECONDS)
                .proceed(chain.request())
            BackendWarmupState.onRequestSucceeded()
            return response
        } finally {
            BackendWarmupState.onRequestFinished()
        }
    }

    private companion object {
        // Render's docs describe the cold start as "about one minute" —
        // padded above that so a slow-but-real wake-up doesn't time out at
        // the exact scenario this interceptor exists to handle.
        const val COLD_START_TIMEOUT_SECONDS = 90L
    }
}
