package io.github.kitectlab.winrt.runtime

internal object WinRtDelegateAbiMarshaller {
    fun functionDescriptor(descriptor: WinRtDelegateDescriptor): NativeFunctionDescriptor {
        val parameterLayouts = descriptor.parameterKinds.map(::layoutFor)
        val trailingLayouts = if (descriptor.returnKind == WinRtDelegateValueKind.UNIT) {
            emptyList()
        } else {
            listOf(NativeValueLayout.ADDRESS)
        }
        return NativeFunctionDescriptor.of(
            NativeValueLayout.JAVA_INT,
            NativeValueLayout.ADDRESS,
            *(parameterLayouts + trailingLayouts).toTypedArray(),
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

    fun allocateReturnOut(
        kind: WinRtDelegateValueKind,
        scope: NativeScope,
    ): NativePointer =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> NativeInterop.nullPointer
            WinRtDelegateValueKind.OBJECT,
            WinRtDelegateValueKind.HSTRING,
            WinRtDelegateValueKind.IUNKNOWN,
            WinRtDelegateValueKind.IINSPECTABLE,
            -> NativeInterop.allocatePointerSlot(scope)

            WinRtDelegateValueKind.BOOLEAN ->
                NativeInterop.allocateInt8Slot(scope)

            WinRtDelegateValueKind.INT32,
            WinRtDelegateValueKind.UINT32,
            -> NativeInterop.allocateInt32Slot(scope)

            WinRtDelegateValueKind.INT64 ->
                NativeInterop.allocateInt64Slot(scope)

            WinRtDelegateValueKind.DOUBLE ->
                NativeInterop.allocateDoubleSlot(scope)
        }

    fun decodeReturnValue(kind: WinRtDelegateValueKind, resultOut: NativePointer): Any? =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> Unit
            WinRtDelegateValueKind.OBJECT,
            WinRtDelegateValueKind.IUNKNOWN,
            -> {
                val pointer = NativeInterop.readPointer(resultOut)
                if (NativeInterop.isNull(pointer)) null else IUnknownReference(pointer)
            }

            WinRtDelegateValueKind.IINSPECTABLE -> {
                val pointer = NativeInterop.readPointer(resultOut)
                if (NativeInterop.isNull(pointer)) {
                    null
                } else {
                    IUnknownReference(pointer).asInspectable()
                }
            }

            WinRtDelegateValueKind.BOOLEAN -> NativeInterop.readInt8(resultOut).toInt() != 0
            WinRtDelegateValueKind.INT32 -> NativeInterop.readInt32(resultOut)
            WinRtDelegateValueKind.UINT32 -> NativeInterop.readInt32(resultOut).toUInt()
            WinRtDelegateValueKind.INT64 -> NativeInterop.readInt64(resultOut)
            WinRtDelegateValueKind.DOUBLE -> NativeInterop.readDouble(resultOut)
            WinRtDelegateValueKind.HSTRING -> {
                val handle = NativeInterop.readPointer(resultOut)
                if (NativeInterop.isNull(handle)) {
                    null
                } else {
                    HString.fromHandle(handle, owner = true).use { it.toKString() }
                }
            }
        }

    fun writeReturnValue(
        kind: WinRtDelegateValueKind,
        value: Any?,
        resultOut: NativePointer,
    ) {
        when (kind) {
            WinRtDelegateValueKind.UNIT -> Unit
            WinRtDelegateValueKind.OBJECT -> NativeInterop.writePointer(resultOut, encodeObject(value))
            WinRtDelegateValueKind.IUNKNOWN -> NativeInterop.writePointer(resultOut, encodeUnknownReference(value))
            WinRtDelegateValueKind.IINSPECTABLE -> NativeInterop.writePointer(resultOut, encodeInspectableReference(value))
            WinRtDelegateValueKind.BOOLEAN -> NativeInterop.writeInt8(resultOut, encodeBoolean(value))
            WinRtDelegateValueKind.INT32 -> NativeInterop.writeInt32(resultOut, encodeInt32(value))
            WinRtDelegateValueKind.UINT32 -> NativeInterop.writeInt32(resultOut, encodeUInt32(value))
            WinRtDelegateValueKind.INT64 -> NativeInterop.writeInt64(resultOut, encodeInt64(value))
            WinRtDelegateValueKind.DOUBLE -> NativeInterop.writeDouble(resultOut, encodeDouble(value))
            WinRtDelegateValueKind.HSTRING -> NativeInterop.writePointer(resultOut, encodeHStringValue(value))
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

    private fun encodeArgument(kind: WinRtDelegateValueKind, abiValue: Any?): Any? =
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
        is NativePointer ->
            if (NativeInterop.isNull(abiValue)) {
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
        is NativePointer ->
            if (NativeInterop.isNull(abiValue)) {
                null
            } else {
                HString.fromHandle(abiValue, owner = false).toKString()
            }
        else -> error("Unsupported ABI HSTRING argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeObject(abiValue: Any?): NativePointer = when (abiValue) {
        null -> NativeInterop.nullPointer
        is IWinRTObject -> abiValue.nativeObject.pointer
        is ComObjectReference -> abiValue.pointer
        is NativePointer -> abiValue
        else -> error("Unsupported outbound ABI object argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeUnknownReference(abiValue: Any?): NativePointer = when (abiValue) {
        null -> NativeInterop.nullPointer
        is IUnknownReference -> abiValue.pointer
        is IInspectableReference -> abiValue.pointer
        is ComObjectReference -> abiValue.pointer
        is IWinRTObject -> abiValue.nativeObject.pointer
        is NativePointer -> abiValue
        else -> error("Unsupported outbound ABI unknown reference argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeInspectableReference(abiValue: Any?): NativePointer = when (abiValue) {
        null -> NativeInterop.nullPointer
        is IInspectableReference -> abiValue.pointer
        is IUnknownReference -> abiValue.pointer
        is ComObjectReference -> abiValue.pointer
        is IWinRTObject -> abiValue.nativeObject.pointer
        is NativePointer -> abiValue
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

    private fun encodeHStringArgument(abiValue: Any?): NativePointer = when (abiValue) {
        null -> NativeInterop.nullPointer
        is NativePointer -> abiValue
        is HString -> abiValue.handle
        is ReferencedHString -> abiValue.handle
        else -> error("Unsupported outbound ABI HSTRING argument: ${abiValue::class.qualifiedName}")
    }

    private fun encodeHStringValue(abiValue: Any?): NativePointer = when (abiValue) {
        null -> NativeInterop.nullPointer
        is String -> HString.create(abiValue).handle
        is HString -> abiValue.handle
        is ReferencedHString -> abiValue.handle
        is NativePointer -> abiValue
        else -> error("Unsupported outbound ABI HSTRING return value: ${abiValue::class.qualifiedName}")
    }

    private fun layoutFor(kind: WinRtDelegateValueKind): NativeValueLayout =
        when (kind) {
            WinRtDelegateValueKind.UNIT -> error("UNIT does not have an ABI layout.")
            WinRtDelegateValueKind.OBJECT,
            WinRtDelegateValueKind.HSTRING,
            WinRtDelegateValueKind.IUNKNOWN,
            WinRtDelegateValueKind.IINSPECTABLE,
            -> NativeValueLayout.ADDRESS

            WinRtDelegateValueKind.BOOLEAN ->
                NativeValueLayout.JAVA_BYTE

            WinRtDelegateValueKind.INT32,
            WinRtDelegateValueKind.UINT32,
            -> NativeValueLayout.JAVA_INT

            WinRtDelegateValueKind.INT64 ->
                NativeValueLayout.JAVA_LONG

            WinRtDelegateValueKind.DOUBLE ->
                NativeValueLayout.JAVA_DOUBLE
        }
}
