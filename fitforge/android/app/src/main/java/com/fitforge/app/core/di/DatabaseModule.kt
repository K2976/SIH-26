package com.fitforge.app.core.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.fitforge.app.core.database.AppDatabase
import com.fitforge.app.core.database.CalibrationProfileDao
import com.fitforge.app.core.database.CycleLogDao
import com.fitforge.app.core.database.ExerciseDao
import com.fitforge.app.core.database.SetLogDao
import com.fitforge.app.core.database.TrainingMusicMappingDao
import com.fitforge.app.core.database.UserStateDao
import com.fitforge.app.core.database.WorkoutSessionDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "fitforge.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideWorkoutSessionDao(db: AppDatabase): WorkoutSessionDao = db.workoutSessionDao()

    @Provides
    fun provideSetLogDao(db: AppDatabase): SetLogDao = db.setLogDao()

    @Provides
    fun provideUserStateDao(db: AppDatabase): UserStateDao = db.userStateDao()

    @Provides
    fun provideCalibrationProfileDao(db: AppDatabase): CalibrationProfileDao = db.calibrationProfileDao()

    @Provides
    fun provideCycleLogDao(db: AppDatabase): CycleLogDao = db.cycleLogDao()

    @Provides
    fun provideTrainingMusicMappingDao(db: AppDatabase): TrainingMusicMappingDao =
        db.trainingMusicMappingDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
