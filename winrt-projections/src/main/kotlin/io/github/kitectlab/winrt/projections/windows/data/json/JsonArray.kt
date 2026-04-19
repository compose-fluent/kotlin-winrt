package io.github.kitectlab.winrt.projections.windows.data.json

import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.WinRtProjectionSupport

class JsonArray internal constructor(
    private val reference: IUnknownReference,
) : AutoCloseable {
    fun getObjectAt(index: UInt): JsonObject =
        JsonObject(reference.invokeObjectMethodWithUInt32Arg(GET_OBJECT_AT_SLOT, index))

    fun getArrayAt(index: UInt): JsonArray =
        JsonArray(reference.invokeObjectMethodWithUInt32Arg(GET_ARRAY_AT_SLOT, index))

    fun getStringAt(index: UInt): String =
        reference.invokeHStringMethodWithUInt32Arg(GET_STRING_AT_SLOT, index).use { it.toKString() }

    fun getNumberAt(index: UInt): Double =
        reference.invokeDoubleMethodWithUInt32Arg(GET_NUMBER_AT_SLOT, index)

    fun getBooleanAt(index: UInt): Boolean =
        reference.invokeBooleanMethodWithUInt32Arg(GET_BOOLEAN_AT_SLOT, index)

    override fun close() {
        reference.close()
    }

    companion object {
        private const val RUNTIME_CLASS_NAME = "Windows.Data.Json.JsonArray"
        private const val PARSE_SLOT = 6
        private const val TRY_PARSE_SLOT = 7
        private const val GET_OBJECT_AT_SLOT = 6
        private const val GET_ARRAY_AT_SLOT = 7
        private const val GET_STRING_AT_SLOT = 8
        private const val GET_NUMBER_AT_SLOT = 9
        private const val GET_BOOLEAN_AT_SLOT = 10
        private val IID_IJSON_ARRAY_STATICS = Guid("DB1434A9-E164-499F-93E2-8A8F49BB90BA")

        fun create(): JsonArray =
            JsonArray(WinRtProjectionSupport.activateUnknown(RUNTIME_CLASS_NAME))

        fun parse(json: String): JsonArray =
            WinRtProjectionSupport.invokeStaticObjectMethodWithStringArg(
                runtimeClassName = RUNTIME_CLASS_NAME,
                interfaceId = IID_IJSON_ARRAY_STATICS,
                slot = PARSE_SLOT,
                value = json,
                wrap = ::JsonArray,
            )

        fun tryParse(json: String): JsonArray? =
            WinRtProjectionSupport.tryInvokeStaticObjectMethodWithStringArg(
                runtimeClassName = RUNTIME_CLASS_NAME,
                interfaceId = IID_IJSON_ARRAY_STATICS,
                slot = TRY_PARSE_SLOT,
                value = json,
                wrap = ::JsonArray,
            )
    }
}
