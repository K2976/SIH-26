package com.fitforge.app.core.network

import android.util.Log
import com.fitforge.app.core.datastore.UserPreferencesDataStore
import com.fitforge.app.data.remote.generated.apis.AuthApi
import com.fitforge.app.data.remote.generated.models.TokenRefresh
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import javax.inject.Inject

private const val TAG = "TokenAuthenticator"

private const val MAX_RETRIES = 3
private const val AUTH_HEADER = "Authorization"
private const val BEARER_PREFIX = "Bearer "

/**
 * Refreshes the access token on a 401 and retries once. `ROTATE_REFRESH_TOKENS`
 * is on server-side, so a successful refresh must persist *both* the new
 * access and the new refresh token — the old refresh token is blacklisted
 * the moment it's used.
 *
 * Concurrent 401s (several in-flight requests hitting an expired token at
 * once) are serialized behind [mutex]; each waiter re-checks the stored
 * token before firing its own refresh call, so only one network refresh
 * happens per expiry.
 */
class TokenAuthenticator @Inject constructor(
    private val preferences: UserPreferencesDataStore,
    @RefreshApi private val refreshAuthApi: AuthApi,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val alreadyRetriedOrIsAuthCall = responseCount(response) > MAX_RETRIES ||
            response.request.url.encodedPath.contains("/auth/")
        if (alreadyRetriedOrIsAuthCall) return null

        val failedToken = response.request.header(AUTH_HEADER)?.removePrefix(BEARER_PREFIX)

        return runBlocking {
            mutex.withLock {
                val currentToken = preferences.currentAccessToken()
                val accessToken = when {
                    currentToken != null && currentToken != failedToken -> currentToken
                    else -> refreshAccessToken()
                } ?: return@withLock null

                response.request.newBuilder()
                    .header(AUTH_HEADER, "$BEARER_PREFIX$accessToken")
                    .build()
            }
        }
    }

    private suspend fun refreshAccessToken(): String? {
        val refreshToken = preferences.currentRefreshToken() ?: return null
        return try {
            // `access` is required by the generated request type but ignored
            // by the server on input — TokenRefresh doubles as the response
            // shape, where it's the field the server actually populates.
            // The generated method returns Response<T> (useCoroutines only
            // adds `suspend`, it doesn't unwrap the body), so a rejected
            // refresh token shows up as !isSuccessful, not a thrown exception.
            val response = refreshAuthApi.authTokenRefreshCreate(
                TokenRefresh(access = "", refresh = refreshToken),
            )
            val pair = response.body()
            if (response.isSuccessful && pair != null) {
                preferences.saveSession(accessToken = pair.access, refreshToken = pair.refresh)
                pair.access
            } else {
                // The refresh token itself was rejected — the session is over.
                preferences.clearSession()
                null
            }
        } catch (e: IOException) {
            // Network unreachable; leave the stored session alone and just
            // fail this one request.
            Log.w(TAG, "Refresh call failed", e)
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
