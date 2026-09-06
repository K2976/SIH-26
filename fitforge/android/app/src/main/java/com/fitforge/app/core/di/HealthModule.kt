package com.fitforge.app.core.di

import com.fitforge.app.data.local.HealthDataStoreLocalDataSource
import com.fitforge.app.data.local.HealthLocalDataSource
import com.fitforge.app.data.repository.HealthConnectRepository
import com.fitforge.app.domain.repository.HealthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {

    @Binds
    @Singleton
    abstract fun bindHealthRepository(
        healthConnectRepository: HealthConnectRepository,
    ): HealthRepository

    @Binds
    @Singleton
    abstract fun bindHealthLocalDataSource(
        healthDataStoreLocalDataSource: HealthDataStoreLocalDataSource,
    ): HealthLocalDataSource
}

