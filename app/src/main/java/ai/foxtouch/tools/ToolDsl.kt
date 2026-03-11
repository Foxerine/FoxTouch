package ai.foxtouch.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
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

    fun objectArray(
        name: String,
        description: String,
        required: Boolean = false,
        init: ToolParametersBuilder.() -> Unit,
    ) {
        val itemSchema = ToolParametersBuilder().apply(init).build()
        properties[name] = buildJsonObject {
            put("type", "array")
            put("items", itemSchema)
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
// All helpers skip JSON `null` values (sent by OpenAI models for nullable
// optional params) so that they're treated the same as absent.

/** Get the string content of a JsonElement, or null if the element is JsonNull. */
private fun JsonObject.primitiveOrNull(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return element.jsonPrimitive.content
}

/** Extract a required string parameter, or return null if missing/null. */
fun JsonObject.requireString(key: String): String? = primitiveOrNull(key)

/** Extract a required int parameter, or return null. */
fun JsonObject.requireInt(key: String): Int? = primitiveOrNull(key)?.toIntOrNull()

/** Extract an optional int parameter with a default. */
fun JsonObject.optionalInt(key: String, default: Int): Int =
    primitiveOrNull(key)?.toIntOrNull() ?: default

/** Extract an optional long parameter with a default. */
fun JsonObject.optionalLong(key: String, default: Long): Long =
    primitiveOrNull(key)?.toLongOrNull() ?: default

/** Extract a required float parameter, or return null. */
fun JsonObject.requireFloat(key: String): Float? = primitiveOrNull(key)?.toFloatOrNull()

/** Extract an optional boolean parameter with a default. */
fun JsonObject.optionalBoolean(key: String, default: Boolean): Boolean =
    primitiveOrNull(key)?.toBooleanStrictOrNull() ?: default

/** Extract an optional string array parameter. */
fun JsonObject.optionalStringArray(key: String): List<String> {
    val element = this[key] ?: return emptyList()
    if (element is JsonNull) return emptyList()
    return (element as? JsonArray)?.mapNotNull {
        if (it is JsonNull) null else it.jsonPrimitive.content
    } ?: emptyList()
}

/** Extract an optional array of JSON objects. */
fun JsonObject.optionalObjectArray(key: String): List<JsonObject> {
    val element = this[key] ?: return emptyList()
    if (element is JsonNull) return emptyList()
    return (element as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()
}
