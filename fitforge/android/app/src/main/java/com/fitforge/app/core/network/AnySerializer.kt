package com.fitforge.app.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * Backs `@Contextual val x: Any?` fields openapi-generator emits for OpenAPI
 * properties with no declared type (`{}` — e.g. locale-keyed JSON like
 * Exercise.name). Round-trips through JsonElement into plain Kotlin
 * Map/List/String/Long/Double/Boolean/null.
 */
object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("AnySerializer only supports JSON")
        jsonEncoder.encodeJsonElement(value.toJsonElement())
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as? JsonDecoder ?: error("AnySerializer only supports JSON")
        // The nullable wrapper kotlinx.serialization generates for `Any?` fields
        // intercepts a JSON null before this runs, so this is unreached in practice.
        return jsonDecoder.decodeJsonElement().toKotlin() ?: JsonNull
    }
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Map<*, *> -> buildJsonObject { entries.forEach { (k, v) -> put(k.toString(), v.toJsonElement()) } }
    is List<*> -> buildJsonArray { forEach { add(it.toJsonElement()) } }
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> JsonPrimitive(this.toString())
}

private fun JsonElement.toKotlin(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> entries.associate { (k, v) -> k to v.toKotlin() }
    is JsonArray -> map { it.toKotlin() }
    is JsonPrimitive -> when {
        isString -> content
        this.booleanOrNull != null -> boolean
        this.longOrNull != null -> long
        this.doubleOrNull != null -> double
        else -> content
    }
}
