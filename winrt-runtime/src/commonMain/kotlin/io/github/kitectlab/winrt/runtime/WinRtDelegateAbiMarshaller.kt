package io.github.kitectlab.winrt.runtime

internal object WinRtDelegateAbiMarshaller {
    fun functionSignature(descriptor: WinRtDelegateDescriptor): ComMethodSignature {
        val parameterKinds = descriptor.parameterKinds.map(::abiKindFor)
        val trailingKinds = if (descriptor.returnKind == WinRtDelegateValueKind.UNIT) {
            emptyList()
        } else {
            listOf(ComAbiValueKind.Pointer)
        }
        return ComMethodSignature(
            explicitParameterKinds = parameterKinds + trailingKinds,
            resultKind = ComAbiValueKind.Int32,
        )
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
    ): List<Any?> {
        require(parameterKinds.size == abiArguments.size) {
            "ABI argument count ${abiArguments.size} must match delegate parameter count ${parameterKinds.size}."
        }

        return parameterKinds.zip(abiArguments).map { (kind, value) ->
            encodeArgument(kind, value)
        }
    }

    fun encodeArgumentsLease(
        parameterKinds: List<WinRtDelegateValueKind>,
        abiArguments: List<Any?>,
    ): EncodedDelegateArguments {
        require(parameterKinds.size == abiArguments.size) {
            "ABI argument count ${abiArguments.size} must match delegate parameter count ${parameterKinds.size}."
        }

        val cleanup = mutableListOf<() -> Unit>()
        val values = parameterKinds.zip(abiArguments).map { (kind, value) ->
            encodeArgument(kind, value, cleanup)
        }
        return EncodedDelegateArguments(values, cleanup)
    }

    fun allocateReturnOut(
        kind: WinRtDelegateValueKind,
        scope: NativeScope,
    ): RawAddress =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> PlatformAbi.nullPointer
            WinRtDelegateValueKind.OBJECT,
            WinRtDelegateValueKind.HSTRING,
            WinRtDelegateValueKind.IUNKNOWN,
            WinRtDelegateValueKind.IINSPECTABLE,
            -> PlatformAbi.allocatePointerSlot(scope)

            WinRtDelegateValueKind.BOOLEAN ->
                PlatformAbi.allocateInt8Slot(scope)

            WinRtDelegateValueKind.INT8,
            WinRtDelegateValueKind.UINT8,
            -> PlatformAbi.allocateInt8Slot(scope)

            WinRtDelegateValueKind.INT16,
            WinRtDelegateValueKind.UINT16,
            WinRtDelegateValueKind.CHAR16 ->
                PlatformAbi.allocateBytes(scope, 2)

            WinRtDelegateValueKind.INT32,
            WinRtDelegateValueKind.UINT32,
            -> PlatformAbi.allocateInt32Slot(scope)

            WinRtDelegateValueKind.INT64 ->
                PlatformAbi.allocateInt64Slot(scope)

            WinRtDelegateValueKind.UINT64 ->
                PlatformAbi.allocateInt64Slot(scope)

            WinRtDelegateValueKind.FLOAT ->
                PlatformAbi.allocateBytes(scope, 4)

            WinRtDelegateValueKind.DOUBLE ->
                PlatformAbi.allocateDoubleSlot(scope)
        }

    fun decodeReturnValue(kind: WinRtDelegateValueKind, resultOut: RawAddress): Any? =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> Unit
            WinRtDelegateValueKind.OBJECT,
            WinRtDelegateValueKind.IUNKNOWN,
            -> {
                val pointer = PlatformAbi.readPointer(resultOut)
                if (PlatformAbi.isNull(pointer)) null else IUnknownReference(pointer.asRawComPtr())
            }

            WinRtDelegateValueKind.IINSPECTABLE -> {
                val pointer = PlatformAbi.readPointer(resultOut)
                if (PlatformAbi.isNull(pointer)) {
                    null
                } else {
                    IUnknownReference(pointer.asRawComPtr()).asInspectable()
                }
            }

            WinRtDelegateValueKind.BOOLEAN -> PlatformAbi.readInt8(resultOut).toInt() != 0
            WinRtDelegateValueKind.INT8 -> PlatformAbi.readInt8(resultOut)
            WinRtDelegateValueKind.UINT8 -> PlatformAbi.readInt8(resultOut).toUByte()
            WinRtDelegateValueKind.INT16 -> PlatformAbi.readInt16(resultOut)
            WinRtDelegateValueKind.UINT16 -> PlatformAbi.readInt16(resultOut).toUShort()
            WinRtDelegateValueKind.INT32 -> PlatformAbi.readInt32(resultOut)
            WinRtDelegateValueKind.UINT32 -> PlatformAbi.readInt32(resultOut).toUInt()
            WinRtDelegateValueKind.INT64 -> PlatformAbi.readInt64(resultOut)
            WinRtDelegateValueKind.UINT64 -> PlatformAbi.readInt64(resultOut).toULong()
            WinRtDelegateValueKind.FLOAT -> PlatformAbi.readFloat(resultOut)
            WinRtDelegateValueKind.DOUBLE -> PlatformAbi.readDouble(resultOut)
            WinRtDelegateValueKind.CHAR16 -> PlatformAbi.readInt16(resultOut).toInt().toChar()
            WinRtDelegateValueKind.HSTRING -> {
                val handle = PlatformAbi.readPointer(resultOut)
                if (PlatformAbi.isNull(handle)) {
                    null
                } else {
                    HString.fromHandle(handle, owner = true).use { it.toKString() }
                }
            }
        }

    fun writeReturnValue(
        kind: WinRtDelegateValueKind,
        value: Any?,
        resultOut: RawAddress,
    ) {
        when (kind) {
            WinRtDelegateValueKind.UNIT -> Unit
            WinRtDelegateValueKind.OBJECT -> PlatformAbi.writePointer(resultOut, encodeObject(value))
            WinRtDelegateValueKind.IUNKNOWN -> PlatformAbi.writePointer(resultOut, encodeUnknownReference(value))
            WinRtDelegateValueKind.IINSPECTABLE -> PlatformAbi.writePointer(resultOut, encodeInspectableReference(value))
            WinRtDelegateValueKind.BOOLEAN -> PlatformAbi.writeInt8(resultOut, encodeBoolean(value))
            WinRtDelegateValueKind.INT8 -> PlatformAbi.writeInt8(resultOut, encodeInt8(value))
            WinRtDelegateValueKind.UINT8 -> PlatformAbi.writeInt8(resultOut, encodeUInt8(value))
            WinRtDelegateValueKind.INT16 -> PlatformAbi.writeInt16(resultOut, encodeInt16(value))
            WinRtDelegateValueKind.UINT16 -> PlatformAbi.writeInt16(resultOut, encodeUInt16(value))
            WinRtDelegateValueKind.INT32 -> PlatformAbi.writeInt32(resultOut, encodeInt32(value))
            WinRtDelegateValueKind.UINT32 -> PlatformAbi.writeInt32(resultOut, encodeUInt32(value))
            WinRtDelegateValueKind.INT64 -> PlatformAbi.writeInt64(resultOut, encodeInt64(value))
            WinRtDelegateValueKind.UINT64 -> PlatformAbi.writeInt64(resultOut, encodeUInt64(value))
            WinRtDelegateValueKind.FLOAT -> PlatformAbi.writeFloat(resultOut, encodeFloat(value))
            WinRtDelegateValueKind.DOUBLE -> PlatformAbi.writeDouble(resultOut, encodeDouble(value))
            WinRtDelegateValueKind.CHAR16 -> PlatformAbi.writeInt16(resultOut, encodeChar16(value))
            WinRtDelegateValueKind.HSTRING -> PlatformAbi.writePointer(resultOut, encodeHStringValue(value))
        }
    }

    private fun decodeArgument(kind: WinRtDelegateValueKind, abiValue: Any?): Any? =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> Unit
            WinRtDelegateValueKind.OBJECT -> decodeObject(abiValue)
            WinRtDelegateValueKind.IUNKNOWN -> decodeUnknownReference(abiValue)
            WinRtDelegateValueKind.IINSPECTABLE -> decodeInspectableReference(abiValue)
            WinRtDelegateValueKind.BOOLEAN -> decodeBoolean(abiValue)
            WinRtDelegateValueKind.INT8 -> decodeInt8(abiValue)
            WinRtDelegateValueKind.UINT8 -> decodeUInt8(abiValue)
            WinRtDelegateValueKind.INT16 -> decodeInt16(abiValue)
            WinRtDelegateValueKind.UINT16 -> decodeUInt16(abiValue)
            WinRtDelegateValueKind.INT32 -> decodeInt32(abiValue)
            WinRtDelegateValueKind.UINT32 -> decodeUInt32(abiValue)
            WinRtDelegateValueKind.INT64 -> decodeInt64(abiValue)
            WinRtDelegateValueKind.UINT64 -> decodeUInt64(abiValue)
            WinRtDelegateValueKind.FLOAT -> decodeFloat(abiValue)
            WinRtDelegateValueKind.DOUBLE -> decodeDouble(abiValue)
            WinRtDelegateValueKind.CHAR16 -> decodeChar16(abiValue)
            WinRtDelegateValueKind.HSTRING -> decodeHString(abiValue)
        }

    private fun encodeArgument(kind: WinRtDelegateValueKind, abiValue: Any?): Any? =
        encodeArgument(kind, abiValue, cleanup = null)

    private fun encodeArgument(
        kind: WinRtDelegateValueKind,
        abiValue: Any?,
        cleanup: MutableList<() -> Unit>?,
    ): Any? =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> error("UNIT is not a valid ABI parameter kind.")
            WinRtDelegateValueKind.OBJECT -> encodeObject(abiValue, cleanup)
            WinRtDelegateValueKind.IUNKNOWN -> encodeUnknownReference(abiValue)
            WinRtDelegateValueKind.IINSPECTABLE -> encodeInspectableReference(abiValue)
            WinRtDelegateValueKind.BOOLEAN -> encodeBoolean(abiValue)
            WinRtDelegateValueKind.INT8 -> encodeInt8(abiValue)
            WinRtDelegateValueKind.UINT8 -> encodeUInt8(abiValue)
            WinRtDelegateValueKind.INT16 -> encodeInt16(abiValue)
            WinRtDelegateValueKind.UINT16 -> encodeUInt16(abiValue)
            WinRtDelegateValueKind.INT32 -> encodeInt32(abiValue)
            WinRtDelegateValueKind.UINT32 -> encodeUInt32(abiValue)
            WinRtDelegateValueKind.INT64 -> encodeInt64(abiValue)
            WinRtDelegateValueKind.UINT64 -> encodeUInt64(abiValue)
            WinRtDelegateValueKind.FLOAT -> encodeFloat(abiValue)
            WinRtDelegateValueKind.DOUBLE -> encodeDouble(abiValue)
            WinRtDelegateValueKind.CHAR16 -> encodeChar16(abiValue)
            WinRtDelegateValueKind.HSTRING -> encodeHStringArgument(abiValue, cleanup)
        }

    private fun decodeObject(abiValue: Any?): Any? = when (abiValue) {
        null -> null
        is ComObjectReference -> abiValue
        is RawAddress ->
            if (PlatformAbi.isNull(abiValue)) {
                null
            } else {
                IUnknownReference(abiValue.asRawComPtr(), preventReleaseOnDispose = true)
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

    private fun decodeInt8(abiValue: Any?): Byte = when (abiValue) {
        is Byte -> abiValue
        is Int -> abiValue.toByte()
        else -> error("Unsupported ABI int8 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeUInt8(abiValue: Any?): UByte = when (abiValue) {
        is UByte -> abiValue
        is Byte -> abiValue.toUByte()
        is Int -> abiValue.toUByte()
        else -> error("Unsupported ABI uint8 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeInt16(abiValue: Any?): Short = when (abiValue) {
        is Short -> abiValue
        is Int -> abiValue.toShort()
        else -> error("Unsupported ABI int16 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeUInt16(abiValue: Any?): UShort = when (abiValue) {
        is UShort -> abiValue
        is Short -> abiValue.toUShort()
        is Int -> abiValue.toUShort()
        else -> error("Unsupported ABI uint16 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
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

    private fun decodeUInt64(abiValue: Any?): ULong = when (abiValue) {
        is ULong -> abiValue
        is Long -> abiValue.toULong()
        is Int -> abiValue.toULong()
        else -> error("Unsupported ABI uint64 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeFloat(abiValue: Any?): Float = when (abiValue) {
        is Float -> abiValue
        is Double -> abiValue.toFloat()
        else -> error("Unsupported ABI float argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeDouble(abiValue: Any?): Double = when (abiValue) {
        is Double -> abiValue
        is Float -> abiValue.toDouble()
        else -> error("Unsupported ABI double argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeChar16(abiValue: Any?): Char = when (abiValue) {
        is Char -> abiValue
        is Short -> abiValue.toInt().toChar()
        is Int -> abiValue.toChar()
        else -> error("Unsupported ABI char16 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeHString(abiValue: Any?): String? = when (abiValue) {
        null -> null
        is String -> abiValue
        is HString -> abiValue.toKString()
        is ReferencedHString -> abiValue.toKString()
        is RawAddress ->
            if (PlatformAbi.isNull(abiValue)) {
                null
            } else {
                HString.fromHandle(abiValue, owner = false).toKString()
            }
        else -> error("Unsupported ABI HSTRING argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeObject(
        abiValue: Any?,
        cleanup: MutableList<() -> Unit>? = null,
    ): RawAddress = when (abiValue) {
        null -> PlatformAbi.nullPointer
        is IWinRTObject -> abiValue.nativeObject.pointer.asRawAddress()
        is ComObjectReference -> abiValue.pointer.asRawAddress()
        is RawAddress -> abiValue
        is RawComPtr -> abiValue.asRawAddress()
        else -> {
            check(cleanup != null) {
                "Unsupported outbound ABI object argument: ${abiValue::class.qualifiedName}"
            }
            ComWrappersSupport.createCCWForObject(abiValue, IID.IInspectable).useAndGetRef().also { pointer ->
                cleanup += { IUnknownReference(pointer.asRawComPtr()).close() }
            }
        }
    }

    private fun encodeUnknownReference(abiValue: Any?): RawAddress = when (abiValue) {
        null -> PlatformAbi.nullPointer
        is IUnknownReference -> abiValue.pointer.asRawAddress()
        is IInspectableReference -> abiValue.pointer.asRawAddress()
        is ComObjectReference -> abiValue.pointer.asRawAddress()
        is IWinRTObject -> abiValue.nativeObject.pointer.asRawAddress()
        is RawAddress -> abiValue
        is RawComPtr -> abiValue.asRawAddress()
        else -> error("Unsupported outbound ABI unknown reference argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeInspectableReference(abiValue: Any?): RawAddress = when (abiValue) {
        null -> PlatformAbi.nullPointer
        is IInspectableReference -> abiValue.pointer.asRawAddress()
        is IUnknownReference -> abiValue.pointer.asRawAddress()
        is ComObjectReference -> abiValue.pointer.asRawAddress()
        is IWinRTObject -> abiValue.nativeObject.pointer.asRawAddress()
        is RawAddress -> abiValue
        is RawComPtr -> abiValue.asRawAddress()
        else -> error("Unsupported outbound ABI inspectable reference argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeBoolean(abiValue: Any?): Byte = when (abiValue) {
        is Boolean -> if (abiValue) 1.toByte() else 0.toByte()
        is Byte -> abiValue
        is Int -> if (abiValue != 0) 1.toByte() else 0.toByte()
        else -> error("Unsupported outbound ABI boolean argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeInt8(abiValue: Any?): Byte = when (abiValue) {
        is Byte -> abiValue
        is Int -> abiValue.toByte()
        else -> error("Unsupported outbound ABI int8 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeUInt8(abiValue: Any?): Byte = when (abiValue) {
        is UByte -> abiValue.toByte()
        is Byte -> abiValue
        is Int -> abiValue.toByte()
        else -> error("Unsupported outbound ABI uint8 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeInt16(abiValue: Any?): Short = when (abiValue) {
        is Short -> abiValue
        is Int -> abiValue.toShort()
        else -> error("Unsupported outbound ABI int16 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeUInt16(abiValue: Any?): Short = when (abiValue) {
        is UShort -> abiValue.toShort()
        is Short -> abiValue
        is Int -> abiValue.toShort()
        else -> error("Unsupported outbound ABI uint16 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
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

    private fun encodeUInt64(abiValue: Any?): Long = when (abiValue) {
        is ULong -> abiValue.toLong()
        is Long -> abiValue
        is Int -> abiValue.toLong()
        else -> error("Unsupported outbound ABI uint64 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeFloat(abiValue: Any?): Float = when (abiValue) {
        is Float -> abiValue
        is Double -> abiValue.toFloat()
        else -> error("Unsupported outbound ABI float argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeDouble(abiValue: Any?): Double = when (abiValue) {
        is Double -> abiValue
        is Float -> abiValue.toDouble()
        else -> error("Unsupported outbound ABI double argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeChar16(abiValue: Any?): Short = when (abiValue) {
        is Char -> abiValue.code.toShort()
        is Short -> abiValue
        is Int -> abiValue.toShort()
        else -> error("Unsupported outbound ABI char16 argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeHStringArgument(
        abiValue: Any?,
        cleanup: MutableList<() -> Unit>? = null,
    ): RawAddress = when (abiValue) {
        null -> PlatformAbi.nullPointer
        is RawAddress -> abiValue
        is HString -> abiValue.handle
        is ReferencedHString -> abiValue.handle
        is String -> {
            check(cleanup != null) {
                "Unsupported outbound ABI HSTRING argument: ${abiValue::class.qualifiedName}"
            }
            HString.create(abiValue).also { hstring ->
                cleanup += { hstring.close() }
            }.handle
        }
        else -> error("Unsupported outbound ABI HSTRING argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeHStringValue(abiValue: Any?): RawAddress = when (abiValue) {
        null -> PlatformAbi.nullPointer
        is String -> HString.create(abiValue).handle
        is HString -> abiValue.handle
        is ReferencedHString -> abiValue.handle
        is RawAddress -> abiValue
        else -> error("Unsupported outbound ABI HSTRING return value: ${abiValue::class.qualifiedName}")
    }

    private fun abiKindFor(kind: WinRtDelegateValueKind): ComAbiValueKind =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> error("UNIT does not have an ABI layout.")
            WinRtDelegateValueKind.OBJECT,
            WinRtDelegateValueKind.HSTRING,
            WinRtDelegateValueKind.IUNKNOWN,
            WinRtDelegateValueKind.IINSPECTABLE,
            -> ComAbiValueKind.Pointer

            WinRtDelegateValueKind.BOOLEAN ->
                ComAbiValueKind.Int8

            WinRtDelegateValueKind.INT8,
            WinRtDelegateValueKind.UINT8,
            -> ComAbiValueKind.Int8

            WinRtDelegateValueKind.INT16,
            WinRtDelegateValueKind.UINT16,
            WinRtDelegateValueKind.CHAR16 ->
                ComAbiValueKind.Int16

            WinRtDelegateValueKind.INT32,
            WinRtDelegateValueKind.UINT32,
            -> ComAbiValueKind.Int32

            WinRtDelegateValueKind.INT64 ->
                ComAbiValueKind.Int64

            WinRtDelegateValueKind.UINT64 ->
                ComAbiValueKind.Int64

            WinRtDelegateValueKind.FLOAT ->
                ComAbiValueKind.Float

            WinRtDelegateValueKind.DOUBLE ->
                ComAbiValueKind.Double
        }
}

internal class EncodedDelegateArguments(
    val values: List<Any?>,
    private val cleanup: List<() -> Unit>,
) : AutoCloseable {
    override fun close() {
        cleanup.asReversed().forEach { it() }
    }
}
