package com.fitforge.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ExerciseEntity::class,
        ExerciseSearchEntity::class,
        WorkoutSessionEntity::class,
        SetLogEntity::class,
        UserStateEntity::class,
        CalibrationProfileEntity::class,
        CycleLogEntity::class,
        TrainingMusicMappingEntity::class,
        PoseLandmarkSequenceEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun setLogDao(): SetLogDao
    abstract fun userStateDao(): UserStateDao
    abstract fun calibrationProfileDao(): CalibrationProfileDao
    abstract fun cycleLogDao(): CycleLogDao
    abstract fun trainingMusicMappingDao(): TrainingMusicMappingDao
    abstract fun poseLandmarkSequenceDao(): PoseLandmarkSequenceDao
}
