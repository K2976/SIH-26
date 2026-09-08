package com.fitforge.app.core.database

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    fun syncStatusToString(value: SyncStatus?): String? = value?.name

    @TypeConverter
    fun stringToSyncStatus(value: String?): SyncStatus? = value?.let(SyncStatus::valueOf)

    @TypeConverter
    fun localDateToIso(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun isoToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun stringListToJson(value: List<String>?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter
    fun jsonToStringList(value: String?): List<String>? =
        value?.let { Json.decodeFromString(it) }
}
