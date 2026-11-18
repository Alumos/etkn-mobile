package xyz.etkn.mobile.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

val EtknJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/*
 * Tolerant accessors. The ETKN TV client reads every field with optString/optInt semantics,
 * so the mobile client does the same: a field with an unexpected type degrades to its default
 * instead of failing the whole response.
 */

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

fun JsonObject.str(key: String, fallback: String = ""): String {
    val value = this[key] as? JsonPrimitive ?: return fallback
    if (value is JsonNull) return fallback
    return value.content
}

fun JsonObject.strOrNull(key: String): String? = str(key).ifEmpty { null }

fun JsonObject.int(key: String, fallback: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.let { if (it is JsonNull) null else it.content.toDoubleOrNull()?.toInt() } ?: fallback

fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.let { if (it is JsonNull) null else it.content.toDoubleOrNull()?.toInt() }

fun JsonObject.long(key: String, fallback: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.let { if (it is JsonNull) null else it.longOrNull ?: it.content.toDoubleOrNull()?.toLong() } ?: fallback

fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.let { if (it is JsonNull) null else it.longOrNull ?: it.content.toDoubleOrNull()?.toLong() }

fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.let { if (it is JsonNull) null else it.doubleOrNull ?: it.content.toDoubleOrNull() }

fun JsonObject.bool(key: String, fallback: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.let { if (it is JsonNull) null else it.booleanOrNull } ?: fallback

fun JsonObject.has(key: String): Boolean = this[key] != null && this[key] !is JsonNull

fun JsonObject.strList(key: String): List<String> =
    arr(key)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content?.takeIf(String::isNotBlank) }
        ?: emptyList()

fun JsonObject.strMap(key: String): Map<String, String> {
    val map = obj(key) ?: return emptyMap()
    val out = HashMap<String, String>(map.size)
    for ((k, v) in map) {
        val p = v as? JsonPrimitive ?: continue
        if (p is JsonNull) continue
        out[k] = p.content
    }
    return out
}

fun JsonObject.objects(key: String): List<JsonObject> = arr(key)?.mapNotNull { it as? JsonObject } ?: emptyList()

/** Mirrors the TV list helper: Items / results / resources / items / data, recursing into same-named objects. */
fun JsonElement.rows(): List<JsonObject> {
    val obj = this as? JsonObject ?: return (this as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    for (key in ROW_KEYS) {
        val value = obj[key]
        if (value is JsonArray) return value.mapNotNull { it as? JsonObject }
        if (value is JsonObject) return value.rows()
    }
    return emptyList()
}

private val ROW_KEYS = arrayOf("Items", "results", "resources", "items", "data")

fun JsonElement.asObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
