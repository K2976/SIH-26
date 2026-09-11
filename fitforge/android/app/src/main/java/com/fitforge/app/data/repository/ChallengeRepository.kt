package com.fitforge.app.data.repository

import android.util.Log
import com.fitforge.app.data.remote.generated.apis.ChallengeApi
import com.fitforge.app.data.remote.generated.models.ChallengeCreate
import com.fitforge.app.data.remote.generated.models.ChallengeDetail
import com.fitforge.app.data.remote.generated.models.ChallengeList
import com.fitforge.app.data.remote.generated.models.ChallengeParticipant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChallengeRepository"

@Singleton
class ChallengeRepository @Inject constructor(
    private val challengeApi: ChallengeApi,
) {
    /**
     * Retrieve challenges for a community, optionally filtered by status.
     */
    suspend fun getChallenges(communityId: Int, status: String? = null): Result<List<ChallengeList>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanStatus = status?.trim()?.takeIf { it.isNotEmpty() && it != "all" }
                val response = challengeApi.socialCommunitiesChallengesList(
                    communityPk = communityId,
                    status = cleanStatus,
                )
                if (response.isSuccessful) {
                    Result.success(response.body() ?: emptyList())
                } else {
                    val error = response.errorBody()?.string()
                        ?: "Failed to fetch challenges: ${response.code()}"
                    Result.failure(IOException(error))
                }
            } catch (e: Exception) {
                Log.w(TAG, "getChallenges failed", e)
                Result.failure(e)
            }
        }

    /**
     * Retrieve full details for a challenge.
     */
    suspend fun getChallengeDetail(communityId: Int, challengeId: Int): Result<ChallengeDetail> =
        withContext(Dispatchers.IO) {
            try {
                val response = challengeApi.socialCommunitiesChallengesRetrieve(
                    communityPk = communityId,
                    id = challengeId,
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Result.success(body)
                    } else {
                        Result.failure(IOException("Challenge detail body is null"))
                    }
                } else {
                    val error = response.errorBody()?.string()
                        ?: "Failed to fetch challenge details: ${response.code()}"
                    Result.failure(IOException(error))
                }
            } catch (e: Exception) {
                Log.w(TAG, "getChallengeDetail failed", e)
                Result.failure(e)
            }
        }

    /**
     * Create a new challenge within a community (Owner/Admin only).
     */
    suspend fun createChallenge(communityId: Int, request: ChallengeCreate): Result<ChallengeDetail> =
        withContext(Dispatchers.IO) {
            try {
                val response = challengeApi.socialCommunitiesChallengesCreate(
                    communityPk = communityId,
                    challengeCreate = request,
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Result.success(body)
                    } else {
                        Result.failure(IOException("Challenge creation body is null"))
                    }
                } else {
                    val error = response.errorBody()?.string()
                        ?: "Failed to create challenge: ${response.code()}"
                    Result.failure(IOException(error))
                }
            } catch (e: Exception) {
                Log.w(TAG, "createChallenge failed", e)
                Result.failure(e)
            }
        }

    /**
     * Join an upcoming or active challenge.
     */
    suspend fun joinChallenge(communityId: Int, challengeId: Int): Result<ChallengeParticipant> =
        withContext(Dispatchers.IO) {
            try {
                val response = challengeApi.socialCommunitiesChallengesJoinCreate(
                    communityPk = communityId,
                    id = challengeId,
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Result.success(body)
                    } else {
                        Result.failure(IOException("Join response body is null"))
                    }
                } else {
                    val error = response.errorBody()?.string()
                        ?: "Failed to join challenge: ${response.code()}"
                    Result.failure(IOException(error))
                }
            } catch (e: Exception) {
                Log.w(TAG, "joinChallenge failed", e)
                Result.failure(e)
            }
        }

    /**
     * Leave a joined challenge.
     */
    suspend fun leaveChallenge(communityId: Int, challengeId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = challengeApi.socialCommunitiesChallengesLeaveCreate(
                    communityPk = communityId,
                    id = challengeId,
                )
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val error = response.errorBody()?.string()
                        ?: "Failed to leave challenge: ${response.code()}"
                    Result.failure(IOException(error))
                }
            } catch (e: Exception) {
                Log.w(TAG, "leaveChallenge failed", e)
                Result.failure(e)
            }
        }

    /**
     * Retrieve the server-derived progress for the authenticated user.
     */
    suspend fun getChallengeProgress(communityId: Int, challengeId: Int): Result<com.fitforge.app.data.remote.generated.models.ChallengeProgress?> =
        withContext(Dispatchers.IO) {
            try {
                val response = challengeApi.socialCommunitiesChallengesRetrieve(
                    communityPk = communityId,
                    id = challengeId,
                )
                if (response.isSuccessful) {
                    Result.success(response.body()?.userProgress)
                } else {
                    val error = response.errorBody()?.string()
                        ?: "Failed to fetch progress: ${response.code()}"
                    Result.failure(IOException(error))
                }
            } catch (e: Exception) {
                Log.w(TAG, "getChallengeProgress failed", e)
                Result.failure(e)
            }
        }

    /**
     * Delete a challenge (Creator or Community Owner only).
     */
    suspend fun deleteChallenge(communityId: Int, challengeId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = challengeApi.socialCommunitiesChallengesDestroy(
                    communityPk = communityId,
                    id = challengeId,
                )
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val error = response.errorBody()?.string()
                        ?: "Failed to delete challenge: ${response.code()}"
                    Result.failure(IOException(error))
                }
            } catch (e: Exception) {
                Log.w(TAG, "deleteChallenge failed", e)
                Result.failure(e)
            }
        }
}
