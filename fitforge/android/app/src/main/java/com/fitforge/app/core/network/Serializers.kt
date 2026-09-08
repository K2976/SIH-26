package com.fitforge.app.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.plus
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val LocalDateSerializer = object : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        val string = decoder.decodeString()
        return try {
            LocalDate.parse(string, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: DateTimeParseException) {
            throw kotlinx.serialization.SerializationException("Invalid LocalDate format: $string", e)
        }
    }
}

private val OffsetDateTimeSerializer = object : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }

    override fun deserialize(decoder: Decoder): OffsetDateTime {
        val string = decoder.decodeString()
        return try {
            OffsetDateTime.parse(string, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } catch (e: DateTimeParseException) {
            throw kotlinx.serialization.SerializationException("Invalid OffsetDateTime format: $string", e)
        }
    }
}

private val javaTimeSerializersModule = SerializersModule {
    contextual(LocalDate::class, LocalDateSerializer)
    contextual(OffsetDateTime::class, OffsetDateTimeSerializer)
}

fun createJsonWithJavaTime(
    additionalSerializersModule: SerializersModule = SerializersModule {},
): Json = Json {
    ignoreUnknownKeys = true
    serializersModule = javaTimeSerializersModule + additionalSerializersModule
}
