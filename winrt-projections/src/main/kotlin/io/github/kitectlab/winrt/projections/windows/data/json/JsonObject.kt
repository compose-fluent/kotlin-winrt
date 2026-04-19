package io.github.kitectlab.winrt.projections.windows.data.json

import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.KnownHResults
import io.github.kitectlab.winrt.runtime.WinRtProjectionSupport
import io.github.kitectlab.winrt.runtime.WinRtRuntimeException

class JsonObject internal constructor(
    private val reference: IUnknownReference,
) : AutoCloseable {
    fun setNamedValue(name: String, value: JsonValue) {
        reference.invokeUnitMethodWithStringAndObjectArg(SET_NAMED_VALUE_SLOT, name, value.asReference())
    }

    fun getNamedObject(name: String): JsonObject =
        JsonObject(reference.invokeObjectMethodWithStringArg(GET_NAMED_OBJECT_SLOT, name))

    fun getNamedArray(name: String): JsonArray =
        JsonArray(reference.invokeObjectMethodWithStringArg(GET_NAMED_ARRAY_SLOT, name))

    fun getNamedValue(name: String): JsonValue =
        JsonValue(reference.invokeObjectMethodWithStringArg(GET_NAMED_VALUE_SLOT, name))

    fun getNamedString(name: String): String =
        reference.invokeHStringMethodWithStringArg(GET_NAMED_STRING_SLOT, name).use { it.toKString() }

    fun getNamedString(name: String, defaultValue: String): String =
        getOrDefault(defaultValue) { getNamedString(name) }

    fun getNamedNumber(name: String): Double =
        reference.invokeDoubleMethodWithStringArg(GET_NAMED_NUMBER_SLOT, name)

    fun getNamedBoolean(name: String): Boolean =
        reference.invokeBooleanMethodWithStringArg(GET_NAMED_BOOLEAN_SLOT, name)

    fun getNamedBoolean(name: String, defaultValue: Boolean): Boolean =
        getOrDefault(defaultValue) { getNamedBoolean(name) }

    fun getNamedArray(name: String, defaultValue: JsonArray): JsonArray =
        getOrDefault(defaultValue) { getNamedArray(name) }

    fun getRuntimeClassName(): String? =
        reference.asInspectable().use { it.getRuntimeClassName() }

    override fun close() {
        reference.close()
    }

    private inline fun <T> getOrDefault(defaultValue: T, operation: () -> T): T =
        try {
            operation()
        } catch (error: WinRtRuntimeException) {
            if (error.hResult == KnownHResults.WEB_E_JSON_VALUE_NOT_FOUND) {
                defaultValue
            } else {
                throw error
            }
        }

    companion object {
        private const val RUNTIME_CLASS_NAME = "Windows.Data.Json.JsonObject"
        private const val PARSE_SLOT = 6
        private const val TRY_PARSE_SLOT = 7
        private const val GET_NAMED_VALUE_SLOT = 6
        private const val SET_NAMED_VALUE_SLOT = 7
        private const val GET_NAMED_OBJECT_SLOT = 8
        private const val GET_NAMED_ARRAY_SLOT = 9
        private const val GET_NAMED_STRING_SLOT = 10
        private const val GET_NAMED_NUMBER_SLOT = 11
        private const val GET_NAMED_BOOLEAN_SLOT = 12
        private val IID_IJSON_OBJECT_STATICS = Guid("2289F159-54DE-45D8-ABCC-22603FA066A0")

        fun parse(json: String): JsonObject =
            WinRtProjectionSupport.withStaticInterface(RUNTIME_CLASS_NAME, IID_IJSON_OBJECT_STATICS) {
                JsonObject(it.invokeObjectMethodWithStringArg(PARSE_SLOT, json))
            }

        fun tryParse(json: String): JsonObject? {
            return WinRtProjectionSupport.withStaticInterface(RUNTIME_CLASS_NAME, IID_IJSON_OBJECT_STATICS) {
                val (reference, succeeded) = it.invokeTryParseObjectMethodWithStringArg(TRY_PARSE_SLOT, json)
                if (!succeeded || reference == null) {
                    reference?.close()
                    return null
                }
                JsonObject(reference)
            }
        }
    }
}
