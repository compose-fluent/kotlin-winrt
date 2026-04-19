package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment

internal object WinRtDelegateAbiMarshaller {
    fun decodeArguments(
        parameterKinds: List<WinRtDelegateValueKind>,
        abiArguments: List<Any?>,
    ): List<Any?> {
        require(parameterKinds.size == abiArguments.size) {
            "ABI argument count ${abiArguments.size} must match delegate parameter count ${parameterKinds.size}."
        }

        return parameterKinds.zip(abiArguments).map { (kind, value) ->
            decodeArgument(kind, value)
        }
    }

    private fun decodeArgument(kind: WinRtDelegateValueKind, abiValue: Any?): Any? {
        return when (kind) {
            WinRtDelegateValueKind.OBJECT -> decodeObject(abiValue)
            WinRtDelegateValueKind.INT64 -> decodeInt64(abiValue)
            WinRtDelegateValueKind.HSTRING -> decodeHString(abiValue)
        }
    }

    private fun decodeObject(abiValue: Any?): Any? = when (abiValue) {
        null -> null
        is ComObjectReference -> abiValue
        is MemorySegment ->
            if (abiValue == MemorySegment.NULL) {
                null
            } else {
                IUnknownReference(abiValue)
            }
        else -> error("Unsupported ABI object argument: ${abiValue::class.qualifiedName}")
    }

    private fun decodeInt64(abiValue: Any?): Long = when (abiValue) {
        is Long -> abiValue
        is Int -> abiValue.toLong()
        else -> error("Unsupported ABI int64 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeHString(abiValue: Any?): String? = when (abiValue) {
        null -> null
        is String -> abiValue
        is HString -> abiValue.toKString()
        is ReferencedHString -> abiValue.toKString()
        is MemorySegment ->
            if (abiValue == MemorySegment.NULL) {
                null
            } else {
                HString.fromHandle(abiValue, owner = false).toKString()
            }
        else -> error("Unsupported ABI HSTRING argument: ${abiValue::class.qualifiedName}")
    }
}
