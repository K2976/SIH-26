package com.fitforge.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * No `@Entity` tables yet — this slice only lays the shared-column
 * convention ([BaseDiaryColumns]) and [Converters] every future diary
 * table will use. Bump `version` when the first entity is added.
 */
@Database(entities = [], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class FitForgeDatabase : RoomDatabase()
