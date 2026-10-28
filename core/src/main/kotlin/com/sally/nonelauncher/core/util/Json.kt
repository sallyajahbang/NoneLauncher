package com.sally.nonelauncher.core.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

val LauncherJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
