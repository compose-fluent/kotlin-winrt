package io.github.kitectlab.winrt.projections.windows.data.json

import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.WinRtProjectionSupport

class JsonValue internal constructor(
    private val reference: IUnknownReference,
) : AutoCloseable {
    internal fun asReference(): IUnknownReference = reference

    val valueType: JsonValueType
        get() = JsonValueType.fromAbiValue(reference.invokeUInt32Method(VALUE_TYPE_SLOT).toInt())

    fun stringify(): String = reference.invokeHStringMethod(STRINGIFY_SLOT).use { it.toKString() }

    fun getString(): String = reference.invokeHStringMethod(GET_STRING_SLOT).use { it.toKString() }

    fun getNumber(): Double = reference.invokeDoubleMethod(GET_NUMBER_SLOT)

    fun getBoolean(): Boolean = reference.invokeBooleanMethod(GET_BOOLEAN_SLOT)

    fun getArray(): JsonArray = JsonArray(reference.invokeObjectMethod(GET_ARRAY_SLOT))

    fun getObject(): JsonObject = JsonObject(reference.invokeObjectMethod(GET_OBJECT_SLOT))

    override fun close() {
        reference.close()
    }

    companion object {
        private const val RUNTIME_CLASS_NAME = "Windows.Data.Json.JsonValue"
        private const val VALUE_TYPE_SLOT = 6
        private const val STRINGIFY_SLOT = 7
        private const val GET_STRING_SLOT = 8
        private const val GET_NUMBER_SLOT = 9
        private const val GET_BOOLEAN_SLOT = 10
        private const val GET_ARRAY_SLOT = 11
        private const val GET_OBJECT_SLOT = 12
        private const val PARSE_SLOT = 6
        private const val TRY_PARSE_SLOT = 7
        private const val CREATE_BOOLEAN_VALUE_SLOT = 8
        private const val CREATE_NUMBER_VALUE_SLOT = 9
        private const val CREATE_STRING_VALUE_SLOT = 10
        private val IID_IJSON_VALUE_STATICS = Guid("5F6B544A-2F53-48E1-91A3-F78B50A6345C")

        fun parse(json: String): JsonValue =
            WinRtProjectionSupport.invokeStaticObjectMethodWithStringArg(
                runtimeClassName = RUNTIME_CLASS_NAME,
                interfaceId = IID_IJSON_VALUE_STATICS,
                slot = PARSE_SLOT,
                value = json,
                wrap = ::JsonValue,
            )

        fun tryParse(json: String): JsonValue? =
            WinRtProjectionSupport.tryInvokeStaticObjectMethodWithStringArg(
                runtimeClassName = RUNTIME_CLASS_NAME,
                interfaceId = IID_IJSON_VALUE_STATICS,
                slot = TRY_PARSE_SLOT,
                value = json,
                wrap = ::JsonValue,
            )

        fun createBooleanValue(value: Boolean): JsonValue =
            WinRtProjectionSupport.invokeStaticObjectMethodWithBooleanArg(
                runtimeClassName = RUNTIME_CLASS_NAME,
                interfaceId = IID_IJSON_VALUE_STATICS,
                slot = CREATE_BOOLEAN_VALUE_SLOT,
                value = value,
                wrap = ::JsonValue,
            )

        fun createNumberValue(value: Double): JsonValue =
            WinRtProjectionSupport.invokeStaticObjectMethodWithDoubleArg(
                runtimeClassName = RUNTIME_CLASS_NAME,
                interfaceId = IID_IJSON_VALUE_STATICS,
                slot = CREATE_NUMBER_VALUE_SLOT,
                value = value,
                wrap = ::JsonValue,
            )

        fun createStringValue(value: String): JsonValue =
            WinRtProjectionSupport.invokeStaticObjectMethodWithStringArg(
                runtimeClassName = RUNTIME_CLASS_NAME,
                interfaceId = IID_IJSON_VALUE_STATICS,
                slot = CREATE_STRING_VALUE_SLOT,
                value = value,
                wrap = ::JsonValue,
            )
    }
}
