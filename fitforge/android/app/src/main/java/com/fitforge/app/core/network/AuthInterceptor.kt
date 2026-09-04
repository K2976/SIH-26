package com.fitforge.app.core.network

import com.fitforge.app.core.datastore.UserPreferencesDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches the stored access token to every request except the auth
 * endpoints themselves. Those are `AllowAny`, but DRF's JWT authentication
 * still rejects an expired/garbage bearer token before the view ever runs
 * — so a stale token on a login/signup/refresh call would 401 a request
 * that should otherwise succeed.
 */
class AuthInterceptor @Inject constructor(
    private val preferences: UserPreferencesDataStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.contains("/auth/")) {
            return chain.proceed(request)
        }

        val accessToken = runBlocking { preferences.currentAccessToken() }
        val authorized = if (accessToken != null) {
            request.newBuilder().header("Authorization", "Bearer $accessToken").build()
        } else {
            request
        }
        return chain.proceed(authorized)
    }
}
