package com.dhunya.app.data.remote.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Null safe accessors for the loosely typed InnerTube JSON. YouTube changes its response
 * shapes frequently, so every lookup returns null instead of throwing.
 */
internal fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

internal fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.contentOrNull

internal fun JsonElement?.asInt(): Int? = when (this) {
    is JsonPrimitive -> intOrNull ?: contentOrNull?.toDoubleOrNull()?.toInt()
    else -> null
}

internal fun JsonElement?.asLong(): Long? = when (this) {
    is JsonPrimitive -> longOrNull ?: contentOrNull?.toDoubleOrNull()?.toLong()
    else -> null
}

internal fun JsonElement?.asBool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject?.obj(key: String): JsonObject? = this?.get(key).asObj()

internal fun JsonObject?.arr(key: String): JsonArray? = this?.get(key).asArr()

internal fun JsonObject?.text(key: String): String? = this?.get(key).asText()

internal fun JsonObject?.int(key: String): Int? = this?.get(key).asInt()

internal fun JsonObject?.long(key: String): Long? = this?.get(key).asLong()

internal fun JsonObject?.bool(key: String): Boolean? = this?.get(key).asBool()

internal fun JsonArray?.objects(): List<JsonObject> = this?.mapNotNull { it as? JsonObject }.orEmpty()
