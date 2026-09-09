@file:Suppress("TooGenericExceptionCaught")

package com.fitforge.app.data.repository

import android.util.Log
import com.fitforge.app.data.remote.generated.apis.CommunityApi
import com.fitforge.app.data.remote.generated.models.CommunityCreate
import com.fitforge.app.data.remote.generated.models.CommunityDetail
import com.fitforge.app.data.remote.generated.models.CommunityList
import com.fitforge.app.data.remote.generated.models.CommunityMember
import com.fitforge.app.data.remote.generated.models.PatchedCommunityUpdate
import com.fitforge.app.data.remote.generated.models.VisibilityEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CommunityRepository"

@Singleton
class CommunityRepository @Inject constructor(
    private val communityApi: CommunityApi,
) {
    /**
     * Retrieve communities the authenticated user has joined.
     */
    suspend fun getMyCommunities(): Result<List<CommunityList>> = withContext(Dispatchers.IO) {
        try {
            val response = communityApi.socialCommunitiesList(filter = "my")
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                val error = response.errorBody()?.string() ?: "Failed to fetch joined communities: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "getMyCommunities failed", e)
            Result.failure(e)
        }
    }

    /**
     * Discover public communities, optionally filtered by keyword search.
     */
    suspend fun getDiscoverCommunities(
        search: String? = null,
    ): Result<List<CommunityList>> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = search?.trim()?.takeIf { it.isNotEmpty() }
            val response = communityApi.socialCommunitiesList(filter = "discover", search = cleanQuery)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                val error = response.errorBody()?.string() ?: "Failed to fetch public communities: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "getDiscoverCommunities failed", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieve full details for a community by ID.
     */
    suspend fun getCommunityDetail(id: Int): Result<CommunityDetail> = withContext(Dispatchers.IO) {
        try {
            val response = communityApi.socialCommunitiesRetrieve(id)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to fetch community #$id: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "getCommunityDetail failed for id=$id", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieve the member roster of a community.
     */
    suspend fun getCommunityMembers(id: Int): Result<List<CommunityMember>> = withContext(Dispatchers.IO) {
        try {
            val response = communityApi.socialCommunitiesMembersList(id)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                val error = response.errorBody()?.string()
                    ?: "Failed to fetch members for community #$id: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "getCommunityMembers failed for id=$id", e)
            Result.failure(e)
        }
    }

    /**
     * Create a new community.
     */
    suspend fun createCommunity(
        name: String,
        description: String = "",
        visibility: VisibilityEnum = VisibilityEnum.`public`,
    ): Result<CommunityDetail> = withContext(Dispatchers.IO) {
        try {
            val request = CommunityCreate(
                name = name.trim(),
                description = description.trim(),
                visibility = visibility,
            )
            val response = communityApi.socialCommunitiesCreate(request)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to create community: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "createCommunity failed", e)
            Result.failure(e)
        }
    }

    /**
     * Partially update community properties (name, description, visibility).
     */
    suspend fun updateCommunity(
        id: Int,
        name: String? = null,
        description: String? = null,
        visibility: VisibilityEnum? = null,
    ): Result<CommunityDetail> = withContext(Dispatchers.IO) {
        try {
            val request = PatchedCommunityUpdate(
                name = name?.trim(),
                description = description?.trim(),
                visibility = visibility,
            )
            val response = communityApi.socialCommunitiesPartialUpdate(id, request)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to update community #$id: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "updateCommunity failed for id=$id", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a community (owner only).
     */
    suspend fun deleteCommunity(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = communityApi.socialCommunitiesDestroy(id)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to delete community #$id: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "deleteCommunity failed for id=$id", e)
            Result.failure(e)
        }
    }

    /**
     * Join a public community.
     */
    suspend fun joinCommunity(id: Int): Result<CommunityMember> = withContext(Dispatchers.IO) {
        try {
            val response = communityApi.socialCommunitiesJoinCreate(id)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to join community #$id: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "joinCommunity failed for id=$id", e)
            Result.failure(e)
        }
    }

    /**
     * Leave a community.
     */
    suspend fun leaveCommunity(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = communityApi.socialCommunitiesLeaveCreate(id)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to leave community #$id: ${response.code()}"
                Result.failure(IOException(error))
            }
        } catch (e: Exception) {
            Log.w(TAG, "leaveCommunity failed for id=$id", e)
            Result.failure(e)
        }
    }
}
