package com.fitforge.app.core.di

import android.content.Context
import androidx.room.Room
import com.fitforge.app.core.database.FitForgeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FitForgeDatabase =
        Room.databaseBuilder(context, FitForgeDatabase::class.java, "fitforge.db").build()
}
