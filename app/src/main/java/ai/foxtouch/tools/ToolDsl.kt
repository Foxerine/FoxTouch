package ai.foxtouch.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * DSL for building JSON Schema tool parameters declaratively.
 * Eliminates the repeated buildJsonObject/buildJsonArray boilerplate in every tool.
 */
class ToolParametersBuilder {
    private val properties = mutableMapOf<String, JsonObject>()
    private val requiredParams = mutableListOf<String>()

    fun string(name: String, description: String, required: Boolean = false) {
        properties[name] = buildJsonObject {
            put("type", "string")
            put("description", description)
        }
        if (required) requiredParams.add(name)
    }

    fun int(name: String, description: String, required: Boolean = false) {
        properties[name] = buildJsonObject {
            put("type", "integer")
            put("description", description)
        }
        if (required) requiredParams.add(name)
    }

    fun boolean(name: String, description: String, required: Boolean = false) {
        properties[name] = buildJsonObject {
            put("type", "boolean")
            put("description", description)
        }
        if (required) requiredParams.add(name)
    }

    fun stringArray(name: String, description: String, required: Boolean = false) {
        properties[name] = buildJsonObject {
            put("type", "array")
            put("items", buildJsonObject { put("type", "string") })
            put("description", description)
        }
        if (required) requiredParams.add(name)
    }

    fun enum(name: String, values: List<String>, description: String, required: Boolean = false) {
        properties[name] = buildJsonObject {
            put("type", "string")
            put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
            put("description", description)
        }
        if (required) requiredParams.add(name)
    }

    fun build(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            for ((name, schema) in properties) put(name, schema)
        })
        if (requiredParams.isNotEmpty()) {
            put("required", buildJsonArray {
                requiredParams.forEach { add(JsonPrimitive(it)) }
            })
        }
    }
}

/** Build a JSON Schema `parameters` object using the DSL. */
fun toolParameters(init: ToolParametersBuilder.() -> Unit): JsonObject =
    ToolParametersBuilder().apply(init).build()

/** Shorthand for tools with no parameters. */
val emptyParameters: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {})
}

// ── Argument extraction helpers ─────────────────────────────────────

/** Extract a required string parameter, or return an error message. */
fun JsonObject.requireString(key: String): String? =
    this[key]?.jsonPrimitive?.content

/** Extract a required int parameter, or return null. */
fun JsonObject.requireInt(key: String): Int? =
    this[key]?.jsonPrimitive?.content?.toIntOrNull()

/** Extract an optional int parameter with a default. */
fun JsonObject.optionalInt(key: String, default: Int): Int =
    this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: default

/** Extract an optional long parameter with a default. */
fun JsonObject.optionalLong(key: String, default: Long): Long =
    this[key]?.jsonPrimitive?.content?.toLongOrNull() ?: default

/** Extract a required float parameter, or return null. */
fun JsonObject.requireFloat(key: String): Float? =
    this[key]?.jsonPrimitive?.content?.toFloatOrNull()

/** Extract an optional boolean parameter with a default. */
fun JsonObject.optionalBoolean(key: String, default: Boolean): Boolean =
    this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default

/** Extract an optional string array parameter. */
fun JsonObject.optionalStringArray(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
