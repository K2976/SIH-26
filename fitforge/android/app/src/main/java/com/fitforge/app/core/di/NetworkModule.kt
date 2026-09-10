@file:Suppress("TooManyFunctions")

package com.fitforge.app.core.di

import com.fitforge.app.BuildConfig
import com.fitforge.app.core.network.AnySerializer
import com.fitforge.app.core.network.AuthInterceptor
import com.fitforge.app.core.network.RefreshApi
import com.fitforge.app.core.network.TokenAuthenticator
import com.fitforge.app.core.network.createJsonWithJavaTime
import com.fitforge.app.data.remote.generated.apis.AppConfigApi
import com.fitforge.app.data.remote.generated.apis.AuthApi
import com.fitforge.app.data.remote.generated.apis.CommunityApi
import com.fitforge.app.data.remote.generated.apis.CycleApi
import com.fitforge.app.data.remote.generated.apis.GoalApi
import com.fitforge.app.data.remote.generated.apis.ProfileApi
import com.fitforge.app.data.remote.generated.apis.ReferenceApi
import com.fitforge.app.data.remote.generated.apis.SocialApi
import com.fitforge.app.data.remote.generated.apis.StateApi
import com.fitforge.app.data.remote.generated.apis.SyncApi
import com.fitforge.app.data.remote.FoodLabelUploadApi
import com.fitforge.app.data.remote.generated.infrastructure.Serializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.plus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // The generated model classes mark BigDecimal/UUID/etc. fields @Contextual and rely on
    // the generator's own Serializer.kotlinxSerializationAdapters to resolve them — a Json
    // instance without it throws SerializationException on any request touching those types
    // (e.g. PatchedUserProfile.heightCm). Merge it in, don't reinvent it.
    @Provides
    @Singleton
    fun provideJson(): Json = createJsonWithJavaTime(
        Serializer.kotlinxSerializationAdapters +
            SerializersModule { contextual(Any::class, AnySerializer) },
    )

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    /** No [AuthInterceptor]/[TokenAuthenticator] — used only to call refresh itself. */
    @Provides
    @Singleton
    @RefreshApi
    fun provideRefreshRetrofit(json: Json, logging: HttpLoggingInterceptor): Retrofit {
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @RefreshApi
    fun provideRefreshAuthApi(@RefreshApi retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAppConfigApi(retrofit: Retrofit): AppConfigApi =
        retrofit.create(AppConfigApi::class.java)

    @Provides
    @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApi =
        retrofit.create(ProfileApi::class.java)

    @Provides
    @Singleton
    fun provideGoalApi(retrofit: Retrofit): GoalApi =
        retrofit.create(GoalApi::class.java)

    @Provides
    @Singleton
    fun provideSyncApi(retrofit: Retrofit): SyncApi =
        retrofit.create(SyncApi::class.java)

    @Provides
    @Singleton
    fun provideReferenceApi(retrofit: Retrofit): ReferenceApi =
        retrofit.create(ReferenceApi::class.java)

    @Provides
    @Singleton
    fun provideStateApi(retrofit: Retrofit): StateApi =
        retrofit.create(StateApi::class.java)

    @Provides
    @Singleton
    fun provideCycleApi(retrofit: Retrofit): CycleApi =
        retrofit.create(CycleApi::class.java)

    @Provides
    @Singleton
    fun provideSocialApi(retrofit: Retrofit): SocialApi =
        retrofit.create(SocialApi::class.java)

    @Provides
    @Singleton
    fun provideCommunityApi(retrofit: Retrofit): CommunityApi =
        retrofit.create(CommunityApi::class.java)

    @Provides
    @Singleton
    fun provideFoodLabelUploadApi(retrofit: Retrofit): FoodLabelUploadApi =
        retrofit.create(FoodLabelUploadApi::class.java)
}
