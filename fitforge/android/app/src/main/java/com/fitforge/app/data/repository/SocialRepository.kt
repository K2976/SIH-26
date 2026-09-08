package com.fitforge.app.data.repository

import android.util.Log
import com.fitforge.app.data.remote.generated.apis.SocialApi
import com.fitforge.app.data.remote.generated.models.PatchedSocialProfileUpdate
import com.fitforge.app.data.remote.generated.models.SocialProfile
import com.fitforge.app.data.remote.generated.models.SocialProfileCreate
import com.fitforge.app.data.remote.generated.models.UsernameCheckResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SocialRepository"
private const val HTTP_NOT_FOUND = 404

@Singleton
class SocialRepository @Inject constructor(
    private val socialApi: SocialApi,
) {
    /**
     * Retrieve the authenticated user's social profile.
     * Returns null if the profile does not exist yet (404).
     */
    suspend fun getMyProfile(): Result<SocialProfile?> = withContext(Dispatchers.IO) {
        try {
            val response = socialApi.socialMyProfileRetrieve()
            when {
                response.isSuccessful -> Result.success(response.body())
                response.code() == HTTP_NOT_FOUND -> Result.success(null)
                else -> {
                    val error = response.errorBody()?.string() ?: "Failed to fetch profile: ${response.code()}"
                    Result.failure(IOException(error))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getMyProfile failed", e)
            Result.failure(e)
        }
    }

    /**
     * Create the authenticated user's social profile.
     */
    suspend fun createProfile(
        username: String,
        displayName: String,
        bio: String = "",
        isPrivate: Boolean = false,
    ): Result<SocialProfile> = withContext(Dispatchers.IO) {
        try {
            val request = SocialProfileCreate(
                username = username.trim().removePrefix("@"),
                displayName = displayName.trim(),
                bio = bio.trim(),
                isPrivate = isPrivate,
            )
            val response = socialApi.socialProfileCreate(request)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to create profile: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "createProfile failed", e)
            Result.failure(e)
        }
    }

    /**
     * Update the authenticated user's social profile.
     */
    suspend fun updateProfile(
        username: String? = null,
        displayName: String? = null,
        bio: String? = null,
        isPrivate: Boolean? = null,
    ): Result<SocialProfile> = withContext(Dispatchers.IO) {
        try {
            val request = PatchedSocialProfileUpdate(
                username = username?.trim()?.removePrefix("@"),
                displayName = displayName?.trim(),
                bio = bio?.trim(),
                isPrivate = isPrivate,
            )
            val response = socialApi.socialProfilePartialUpdate(request)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to update profile: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "updateProfile failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch a public profile by its username handle.
     */
    suspend fun getProfileByUsername(username: String): Result<SocialProfile> = withContext(Dispatchers.IO) {
        try {
            val cleanHandle = username.trim().removePrefix("@")
            val response = socialApi.socialPublicProfileRetrieve(cleanHandle)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val error = response.errorBody()?.string() ?: "User '@$cleanHandle' not found"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "getProfileByUsername failed", e)
            Result.failure(e)
        }
    }

    /**
     * Search users by handle or display name.
     */
    suspend fun searchUsers(query: String): Result<List<SocialProfile>> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim().removePrefix("@")
            if (cleanQuery.isEmpty()) {
                return@withContext Result.success(emptyList())
            }
            val response = socialApi.socialSearchList(cleanQuery)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                val error = response.errorBody()?.string() ?: "Search failed: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "searchUsers failed", e)
            Result.failure(e)
        }
    }

    /**
     * Check username availability and format validity.
     */
    suspend fun checkUsername(username: String): Result<UsernameCheckResponse> = withContext(Dispatchers.IO) {
        try {
            val cleanHandle = username.trim().removePrefix("@")
            val response = socialApi.socialCheckUsernameRetrieve(cleanHandle)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val error = response.errorBody()?.string() ?: "Username check failed: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkUsername failed", e)
            Result.failure(e)
        }
    }
}
