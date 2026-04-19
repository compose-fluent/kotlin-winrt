package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal object WinRtDelegateAbiMarshaller {
    fun functionDescriptor(descriptor: WinRtDelegateDescriptor): FunctionDescriptor {
        val parameterLayouts = descriptor.parameterKinds.map(::layoutFor)
        val trailingLayouts = if (descriptor.returnKind == WinRtDelegateValueKind.UNIT) {
            emptyList()
        } else {
            listOf(ValueLayout.ADDRESS)
        }
        return FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            *(parameterLayouts + trailingLayouts).toTypedArray(),
        )
    }

    fun carrierClass(kind: WinRtDelegateValueKind): Class<*> =
        when (kind) {
            WinRtDelegateValueKind.OBJECT,
            WinRtDelegateValueKind.HSTRING,
            WinRtDelegateValueKind.IUNKNOWN,
            WinRtDelegateValueKind.IINSPECTABLE,
            -> MemorySegment::class.java

            WinRtDelegateValueKind.BOOLEAN,
            -> Byte::class.javaPrimitiveType!!

            WinRtDelegateValueKind.INT32,
            WinRtDelegateValueKind.UINT32,
            -> Int::class.javaPrimitiveType!!

            WinRtDelegateValueKind.INT64,
            -> Long::class.javaPrimitiveType!!

            WinRtDelegateValueKind.DOUBLE,
            -> Double::class.javaPrimitiveType!!

            WinRtDelegateValueKind.UNIT,
            -> error("UNIT is not a valid ABI carrier kind.")
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

    fun allocateReturnOut(kind: WinRtDelegateValueKind, arena: Arena): MemorySegment =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> MemorySegment.NULL
            else -> arena.allocate(layoutFor(kind))
        }

    fun decodeReturnValue(kind: WinRtDelegateValueKind, resultOut: MemorySegment): Any? =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> Unit
            WinRtDelegateValueKind.OBJECT,
            WinRtDelegateValueKind.IUNKNOWN,
            -> {
                val pointer = resultOut.get(ValueLayout.ADDRESS, 0)
                if (pointer == MemorySegment.NULL) null else IUnknownReference(pointer)
            }

            WinRtDelegateValueKind.IINSPECTABLE ->
                resultOut.get(ValueLayout.ADDRESS, 0).let { pointer ->
                    if (pointer == MemorySegment.NULL) {
                        null
                    } else {
                        IUnknownReference(pointer).asInspectable()
                    }
                }

            WinRtDelegateValueKind.BOOLEAN -> resultOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0
            WinRtDelegateValueKind.INT32 -> resultOut.get(ValueLayout.JAVA_INT, 0)
            WinRtDelegateValueKind.UINT32 -> resultOut.get(ValueLayout.JAVA_INT, 0).toUInt()
            WinRtDelegateValueKind.INT64 -> resultOut.get(ValueLayout.JAVA_LONG, 0)
            WinRtDelegateValueKind.DOUBLE -> resultOut.get(ValueLayout.JAVA_DOUBLE, 0)
            WinRtDelegateValueKind.HSTRING ->
                resultOut.get(ValueLayout.ADDRESS, 0).let { handle ->
                    if (handle == MemorySegment.NULL) {
                        null
                    } else {
                        HString.fromHandle(handle, owner = true).use { it.toKString() }
                    }
                }
        }

    fun writeReturnValue(kind: WinRtDelegateValueKind, value: Any?, resultOut: MemorySegment) {
        val writableOut = if (kind == WinRtDelegateValueKind.UNIT) {
            resultOut
        } else {
            resultOut.reinterpret(layoutFor(kind).byteSize())
        }
        when (kind) {
            WinRtDelegateValueKind.UNIT -> Unit
            WinRtDelegateValueKind.OBJECT -> writableOut.set(ValueLayout.ADDRESS, 0, encodeObject(value))
            WinRtDelegateValueKind.IUNKNOWN -> writableOut.set(ValueLayout.ADDRESS, 0, encodeUnknownReference(value))
            WinRtDelegateValueKind.IINSPECTABLE -> writableOut.set(ValueLayout.ADDRESS, 0, encodeInspectableReference(value))
            WinRtDelegateValueKind.BOOLEAN -> writableOut.set(ValueLayout.JAVA_BYTE, 0, encodeBoolean(value))
            WinRtDelegateValueKind.INT32 -> writableOut.set(ValueLayout.JAVA_INT, 0, encodeInt32(value))
            WinRtDelegateValueKind.UINT32 -> writableOut.set(ValueLayout.JAVA_INT, 0, encodeUInt32(value))
            WinRtDelegateValueKind.INT64 -> writableOut.set(ValueLayout.JAVA_LONG, 0, encodeInt64(value))
            WinRtDelegateValueKind.DOUBLE -> writableOut.set(ValueLayout.JAVA_DOUBLE, 0, encodeDouble(value))
            WinRtDelegateValueKind.HSTRING -> writableOut.set(ValueLayout.ADDRESS, 0, encodeHStringValue(value))
        }
    }

    private fun decodeArgument(kind: WinRtDelegateValueKind, abiValue: Any?): Any? =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> Unit
            WinRtDelegateValueKind.OBJECT -> decodeObject(abiValue)
            WinRtDelegateValueKind.IUNKNOWN -> decodeUnknownReference(abiValue)
            WinRtDelegateValueKind.IINSPECTABLE -> decodeInspectableReference(abiValue)
            WinRtDelegateValueKind.BOOLEAN -> decodeBoolean(abiValue)
            WinRtDelegateValueKind.INT32 -> decodeInt32(abiValue)
            WinRtDelegateValueKind.UINT32 -> decodeUInt32(abiValue)
            WinRtDelegateValueKind.INT64 -> decodeInt64(abiValue)
            WinRtDelegateValueKind.DOUBLE -> decodeDouble(abiValue)
            WinRtDelegateValueKind.HSTRING -> decodeHString(abiValue)
        }

    private fun encodeArgument(kind: WinRtDelegateValueKind, abiValue: Any?): Any =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> error("UNIT is not a valid ABI parameter kind.")
            WinRtDelegateValueKind.OBJECT -> encodeObject(abiValue)
            WinRtDelegateValueKind.IUNKNOWN -> encodeUnknownReference(abiValue)
            WinRtDelegateValueKind.IINSPECTABLE -> encodeInspectableReference(abiValue)
            WinRtDelegateValueKind.BOOLEAN -> encodeBoolean(abiValue)
            WinRtDelegateValueKind.INT32 -> encodeInt32(abiValue)
            WinRtDelegateValueKind.UINT32 -> encodeUInt32(abiValue)
            WinRtDelegateValueKind.INT64 -> encodeInt64(abiValue)
            WinRtDelegateValueKind.DOUBLE -> encodeDouble(abiValue)
            WinRtDelegateValueKind.HSTRING -> encodeHStringArgument(abiValue)
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

    private fun decodeUnknownReference(abiValue: Any?): IUnknownReference? =
        decodeObject(abiValue)?.let { reference ->
            when (reference) {
                is IUnknownReference -> reference
                is ComObjectReference -> IUnknownReference(reference.pointer)
                else -> error("Unsupported ABI unknown reference argument: ${reference::class.qualifiedName}")
            }
        }

    private fun decodeInspectableReference(abiValue: Any?): IInspectableReference? =
        decodeUnknownReference(abiValue)?.asInspectable()

    private fun decodeBoolean(abiValue: Any?): Boolean = when (abiValue) {
        is Byte -> abiValue.toInt() != 0
        is Int -> abiValue != 0
        else -> error("Unsupported ABI boolean argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeInt32(abiValue: Any?): Int = when (abiValue) {
        is Int -> abiValue
        is Long -> abiValue.toInt()
        else -> error("Unsupported ABI int32 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeUInt32(abiValue: Any?): UInt = when (abiValue) {
        is UInt -> abiValue
        is Int -> abiValue.toUInt()
        is Long -> abiValue.toInt().toUInt()
        else -> error("Unsupported ABI uint32 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeInt64(abiValue: Any?): Long = when (abiValue) {
        is Long -> abiValue
        is Int -> abiValue.toLong()
        else -> error("Unsupported ABI int64 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeDouble(abiValue: Any?): Double = when (abiValue) {
        is Double -> abiValue
        is Float -> abiValue.toDouble()
        else -> error("Unsupported ABI double argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
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
        is IWinRTObject -> abiValue.nativeObject.pointer
        is ComObjectReference -> abiValue.pointer
        is MemorySegment -> abiValue
        else -> error("Unsupported outbound ABI object argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeUnknownReference(abiValue: Any?): MemorySegment = when (abiValue) {
        null -> MemorySegment.NULL
        is IUnknownReference -> abiValue.pointer
        is IInspectableReference -> abiValue.pointer
        is ComObjectReference -> abiValue.pointer
        is IWinRTObject -> abiValue.nativeObject.pointer
        is MemorySegment -> abiValue
        else -> error("Unsupported outbound ABI unknown reference argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeInspectableReference(abiValue: Any?): MemorySegment = when (abiValue) {
        null -> MemorySegment.NULL
        is IInspectableReference -> abiValue.pointer
        is IUnknownReference -> abiValue.pointer
        is ComObjectReference -> abiValue.pointer
        is IWinRTObject -> abiValue.nativeObject.pointer
        is MemorySegment -> abiValue
        else -> error("Unsupported outbound ABI inspectable reference argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeBoolean(abiValue: Any?): Byte = when (abiValue) {
        is Boolean -> if (abiValue) 1.toByte() else 0.toByte()
        is Byte -> abiValue
        is Int -> if (abiValue != 0) 1.toByte() else 0.toByte()
        else -> error("Unsupported outbound ABI boolean argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeInt32(abiValue: Any?): Int = when (abiValue) {
        is Int -> abiValue
        is Long -> abiValue.toInt()
        else -> error("Unsupported outbound ABI int32 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeUInt32(abiValue: Any?): Int = when (abiValue) {
        is UInt -> abiValue.toInt()
        is Int -> abiValue
        is Long -> abiValue.toInt()
        else -> error("Unsupported outbound ABI uint32 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeInt64(abiValue: Any?): Long = when (abiValue) {
        is Long -> abiValue
        is Int -> abiValue.toLong()
        else -> error("Unsupported outbound ABI int64 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeDouble(abiValue: Any?): Double = when (abiValue) {
        is Double -> abiValue
        is Float -> abiValue.toDouble()
        else -> error("Unsupported outbound ABI double argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeHStringArgument(abiValue: Any?): MemorySegment = when (abiValue) {
        null -> MemorySegment.NULL
        is MemorySegment -> abiValue
        is HString -> abiValue.handle
        is ReferencedHString -> abiValue.handle
        else -> error("Unsupported outbound ABI HSTRING argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeHStringValue(abiValue: Any?): MemorySegment = when (abiValue) {
        null -> MemorySegment.NULL
        is String -> HString.create(abiValue).handle
        is HString -> abiValue.handle
        is ReferencedHString -> abiValue.handle
        is MemorySegment -> abiValue
        else -> error("Unsupported outbound ABI HSTRING return value: ${abiValue::class.qualifiedName}")
    }

    private fun layoutFor(kind: WinRtDelegateValueKind): ValueLayout =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> error("UNIT does not have an ABI layout.")
            WinRtDelegateValueKind.OBJECT,
            WinRtDelegateValueKind.HSTRING,
            WinRtDelegateValueKind.IUNKNOWN,
            WinRtDelegateValueKind.IINSPECTABLE,
            -> ValueLayout.ADDRESS

            WinRtDelegateValueKind.BOOLEAN,
            -> ValueLayout.JAVA_BYTE

            WinRtDelegateValueKind.INT32,
            WinRtDelegateValueKind.UINT32,
            -> ValueLayout.JAVA_INT

            WinRtDelegateValueKind.INT64,
            -> ValueLayout.JAVA_LONG

            WinRtDelegateValueKind.DOUBLE,
            -> ValueLayout.JAVA_DOUBLE
        }
}
