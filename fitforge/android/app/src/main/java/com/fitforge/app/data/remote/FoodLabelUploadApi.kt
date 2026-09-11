package com.fitforge.app.data.remote

import com.fitforge.app.data.remote.generated.models.FoodLabelResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** Multipart adapter using the response model generated from schema.yaml. */
interface FoodLabelUploadApi {
    @Multipart
    @POST("api/v1/food/label/")
    suspend fun label(@Part image: MultipartBody.Part): Response<FoodLabelResponse>
}
