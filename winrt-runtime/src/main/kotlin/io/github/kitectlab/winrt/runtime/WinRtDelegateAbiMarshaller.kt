package io.github.kitectlab.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal object WinRtDelegateAbiMarshaller {
    fun functionDescriptor(parameterKinds: List<WinRtDelegateValueKind>): FunctionDescriptor =
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            *parameterKinds.map(::layoutFor).toTypedArray(),
        )

    fun carrierClass(kind: WinRtDelegateValueKind): Class<*> =
        when (kind) {
            WinRtDelegateValueKind.OBJECT,
            WinRtDelegateValueKind.HSTRING,
            -> MemorySegment::class.java

            WinRtDelegateValueKind.INT32,
            -> Int::class.javaPrimitiveType!!

            WinRtDelegateValueKind.INT64,
            -> Long::class.javaPrimitiveType!!
        }

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

    fun encodeArguments(
        parameterKinds: List<WinRtDelegateValueKind>,
        abiArguments: List<Any?>,
    ): List<Any> {
        require(parameterKinds.size == abiArguments.size) {
            "ABI argument count ${abiArguments.size} must match delegate parameter count ${parameterKinds.size}."
        }

        return parameterKinds.zip(abiArguments).map { (kind, value) ->
            encodeArgument(kind, value)
        }
    }

    private fun decodeArgument(kind: WinRtDelegateValueKind, abiValue: Any?): Any? {
        return when (kind) {
            WinRtDelegateValueKind.OBJECT -> decodeObject(abiValue)
            WinRtDelegateValueKind.INT32 -> decodeInt32(abiValue)
            WinRtDelegateValueKind.INT64 -> decodeInt64(abiValue)
            WinRtDelegateValueKind.HSTRING -> decodeHString(abiValue)
        }
    }

    private fun encodeArgument(kind: WinRtDelegateValueKind, abiValue: Any?): Any =
        when (kind) {
            WinRtDelegateValueKind.OBJECT -> encodeObject(abiValue)
            WinRtDelegateValueKind.INT32 -> encodeInt32(abiValue)
            WinRtDelegateValueKind.INT64 -> encodeInt64(abiValue)
            WinRtDelegateValueKind.HSTRING -> encodeHString(abiValue)
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

    private fun decodeInt32(abiValue: Any?): Int = when (abiValue) {
        is Int -> abiValue
        is Long -> abiValue.toInt()
        else -> error("Unsupported ABI int32 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
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

        private fun encodeObject(abiValue: Any?): MemorySegment = when (abiValue) {
            null -> MemorySegment.NULL
            is ComObjectReference -> abiValue.pointer
            is MemorySegment -> abiValue
            else -> error("Unsupported outbound ABI object argument: ${abiValue::class.qualifiedName}")
        }

        private fun encodeInt32(abiValue: Any?): Int = when (abiValue) {
            is Int -> abiValue
            is Long -> abiValue.toInt()
            else -> error("Unsupported outbound ABI int32 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
        }

        private fun encodeInt64(abiValue: Any?): Long = when (abiValue) {
            is Long -> abiValue
            is Int -> abiValue.toLong()
            else -> error("Unsupported outbound ABI int64 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
        }

        private fun encodeHString(abiValue: Any?): MemorySegment = when (abiValue) {
            null -> MemorySegment.NULL
            is MemorySegment -> abiValue
            is HString -> abiValue.handle
            is ReferencedHString -> abiValue.handle
            else -> error("Unsupported outbound ABI HSTRING argument: ${abiValue::class.qualifiedName}")
        }

        private fun layoutFor(kind: WinRtDelegateValueKind): ValueLayout =
            when (kind) {
                WinRtDelegateValueKind.OBJECT,
                WinRtDelegateValueKind.HSTRING,
                -> ValueLayout.ADDRESS

                WinRtDelegateValueKind.INT32,
                -> ValueLayout.JAVA_INT

                WinRtDelegateValueKind.INT64,
                -> ValueLayout.JAVA_LONG
            }
}
