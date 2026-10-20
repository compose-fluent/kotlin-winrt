package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_COMPILER_PROTOCOL_VERSION
import io.github.composefluent.winrt.runtime.WINRT_PROJECTION_RUNTIME_ABI_VERSION
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.intOrNull

/**
 * CsWinRT checks runtime assembly compatibility before generating interop code. Kotlin also
 * versions its serialized compiler protocol because lowering consumes it across library edges.
 * Unknown explicit versions must never fall back to regenerating dependency-owned types.
 */
internal fun readProjectionIdentityDocument(content: String, identityFile: File): JsonObject {
    // A producer output may not exist yet while Gradle constructs the dependency graph.
    if (content.isBlank()) return JsonObject(emptyMap())
    return try {
        Json.parseToJsonElement(content) as? JsonObject
            ?: invalidProjectionIdentity(identityFile, "expected a JSON object")
    } catch (failure: IllegalArgumentException) {
        invalidProjectionIdentity(identityFile, "malformed JSON (${failure.message})")
    }
}

private fun invalidProjectionIdentity(identityFile: File, reason: String): Nothing = throw IllegalStateException(
        "Incompatible WinRT projection identity '${identityFile.absolutePath}': $reason. " +
            "Use a library built with a compatible Kotlin/WinRT toolkit; dependency-owned types cannot be regenerated safely.",
    )

internal fun projectionIdentityNames(identity: JsonObject, name: String, identityFile: File): List<String>? {
    if (name !in identity) return null
    val values = identity[name] as? JsonArray
        ?: invalidProjectionIdentity(identityFile, "$name must be an array of nonblank strings")
    return values.map { element ->
        val value = element as? JsonPrimitive
        if (value == null || !value.isString || value.content.isBlank()) {
            invalidProjectionIdentity(identityFile, "$name must be an array of nonblank strings")
        }
        value.content
    }
}

internal fun validateProjectionContractVersions(identity: JsonObject, identityFile: File): Int? {
    if (identity.isEmpty() && !identityFile.isFile) return null
    fun invalid(reason: String): Nothing = invalidProjectionIdentity(identityFile, reason)
    fun version(name: String): Int? {
        if (name !in identity) return null
        val value = identity[name] as? JsonPrimitive ?: invalid("$name must be a positive integer")
        return value.intOrNull?.takeIf { !value.isString && it > 0 }
            ?: invalid("$name must be a positive integer")
    }
    fun check(name: String, supported: Int): Int? = version(name).also { actual ->
        if (actual != null && actual != supported) invalid("$name is $actual; this toolkit supports $supported")
    }
    val schema = check("schemaVersion", 1)
    val shape = check("projectionShapeVersion", CURRENT_PROJECTION_SHAPE_VERSION)
    val runtime = check("projectionRuntimeAbiVersion", WINRT_PROJECTION_RUNTIME_ABI_VERSION)
    val compiler = check("projectionCompilerProtocolVersion", WINRT_PROJECTION_COMPILER_PROTOCOL_VERSION)
    if (schema == null || shape == null || runtime == null || compiler == null) {
        invalid("the projection version tuple is incomplete; rebuild this library with the current toolkit")
    }
    return shape
}

