package com.fitforge.app.core.di

import com.fitforge.app.BuildConfig
import com.fitforge.app.core.network.AuthInterceptor
import com.fitforge.app.core.network.RefreshApi
import com.fitforge.app.core.network.TokenAuthenticator
import com.fitforge.app.data.remote.generated.apis.AppConfigApi
import com.fitforge.app.data.remote.generated.apis.AuthApi
import com.fitforge.app.data.remote.generated.apis.CommunityApi
import com.fitforge.app.data.remote.generated.apis.GoalApi
import com.fitforge.app.data.remote.generated.apis.ProfileApi
import com.fitforge.app.data.remote.generated.apis.SocialApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.fitforge.app.core.network.createJsonWithJavaTime
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = createJsonWithJavaTime()

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
    fun provideSocialApi(retrofit: Retrofit): SocialApi =
        retrofit.create(SocialApi::class.java)

    @Provides
    @Singleton
    fun provideCommunityApi(retrofit: Retrofit): CommunityApi =
        retrofit.create(CommunityApi::class.java)
}

