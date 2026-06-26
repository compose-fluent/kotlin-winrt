package io.github.composefluent.winrt.runtime

internal object WinRTDelegateAbiMarshaller {
    fun functionSignature(descriptor: WinRTDelegateDescriptor): ComMethodSignature {
        val parameterKinds = descriptor.parameterKinds.flatMapIndexed { index, kind ->
            abiKindsForParameter(kind, descriptor.parameterStructAdapter(index))
        }
        val trailingKinds = if (descriptor.returnKind == WinRTDelegateValueKind.UNIT) {
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
        parameterKinds: List<WinRTDelegateValueKind>,
        abiArguments: List<Any?>,
    ): List<Any?> {
        require(expectedAbiArgumentCount(parameterKinds) == abiArguments.size) {
            "ABI argument count ${abiArguments.size} must match delegate ABI parameter count ${expectedAbiArgumentCount(parameterKinds)}."
        }

        return decodeArgumentList(parameterKinds, abiArguments) { _, _ -> null }
    }

    fun decodeArguments(
        descriptor: WinRTDelegateDescriptor,
        abiArguments: List<Any?>,
    ): List<Any?> {
        require(expectedAbiArgumentCount(descriptor.parameterKinds) == abiArguments.size) {
            "ABI argument count ${abiArguments.size} must match delegate ABI parameter count ${expectedAbiArgumentCount(descriptor.parameterKinds)}."
        }

        return decodeArgumentList(descriptor.parameterKinds, abiArguments) { index, _ -> descriptor.parameterStructAdapter(index) }
    }

    fun encodeArguments(
        parameterKinds: List<WinRTDelegateValueKind>,
        abiArguments: List<Any?>,
    ): List<Any?> {
        require(parameterKinds.size == abiArguments.size) {
            "ABI argument count ${abiArguments.size} must match delegate parameter count ${parameterKinds.size}."
        }

        return encodeArgumentList(parameterKinds, abiArguments, mutableListOf()) { _, _ -> null }
    }

    fun encodeArgumentsLease(
        parameterKinds: List<WinRTDelegateValueKind>,
        abiArguments: List<Any?>,
    ): EncodedDelegateArguments {
        require(parameterKinds.size == abiArguments.size) {
            "ABI argument count ${abiArguments.size} must match delegate parameter count ${parameterKinds.size}."
        }

        val cleanup = mutableListOf<() -> Unit>()
        val values = encodeArgumentList(parameterKinds, abiArguments, cleanup) { _, _ -> null }
        return EncodedDelegateArguments(values, cleanup)
    }

    fun encodeArgumentsLease(
        descriptor: WinRTDelegateDescriptor,
        abiArguments: List<Any?>,
    ): EncodedDelegateArguments {
        require(descriptor.parameterKinds.size == abiArguments.size) {
            "ABI argument count ${abiArguments.size} must match delegate parameter count ${descriptor.parameterKinds.size}."
        }

        val cleanup = mutableListOf<() -> Unit>()
        val values = encodeArgumentList(descriptor.parameterKinds, abiArguments, cleanup) { index, _ -> descriptor.parameterStructAdapter(index) }
        return EncodedDelegateArguments(values, cleanup)
    }

    fun allocateReturnOut(
        kind: WinRTDelegateValueKind,
        scope: NativeScope,
    ): RawAddress =
        when (kind) {
            WinRTDelegateValueKind.UNIT -> PlatformAbi.nullPointer
            WinRTDelegateValueKind.OBJECT,
            WinRTDelegateValueKind.HSTRING,
            WinRTDelegateValueKind.IUNKNOWN,
            WinRTDelegateValueKind.IINSPECTABLE,
            WinRTDelegateValueKind.UINT8_ARRAY,
            -> PlatformAbi.allocatePointerSlot(scope)
            WinRTDelegateValueKind.GUID -> PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            WinRTDelegateValueKind.STRUCT -> error("STRUCT return allocation requires a NativeStructAdapter.")

            WinRTDelegateValueKind.BOOLEAN ->
                PlatformAbi.allocateInt8Slot(scope)

            WinRTDelegateValueKind.INT8,
            WinRTDelegateValueKind.UINT8,
            -> PlatformAbi.allocateInt8Slot(scope)

            WinRTDelegateValueKind.INT16,
            WinRTDelegateValueKind.UINT16,
            WinRTDelegateValueKind.CHAR16 ->
                PlatformAbi.allocateBytes(scope, 2)

            WinRTDelegateValueKind.INT32,
            WinRTDelegateValueKind.UINT32,
            -> PlatformAbi.allocateInt32Slot(scope)

            WinRTDelegateValueKind.INT64 ->
                PlatformAbi.allocateInt64Slot(scope)

            WinRTDelegateValueKind.UINT64 ->
                PlatformAbi.allocateInt64Slot(scope)

            WinRTDelegateValueKind.FLOAT ->
                PlatformAbi.allocateBytes(scope, 4)

            WinRTDelegateValueKind.DOUBLE ->
                PlatformAbi.allocateDoubleSlot(scope)
        }

    fun decodeReturnValue(kind: WinRTDelegateValueKind, resultOut: RawAddress): Any? =
        when (kind) {
            WinRTDelegateValueKind.UNIT -> Unit
            WinRTDelegateValueKind.OBJECT -> WinRTObjectMarshaller.fromAbi(PlatformAbi.readPointer(resultOut))

            WinRTDelegateValueKind.IUNKNOWN -> {
                val pointer = PlatformAbi.readPointer(resultOut)
                if (PlatformAbi.isNull(pointer)) null else IUnknownReference(pointer.asRawComPtr())
            }

            WinRTDelegateValueKind.IINSPECTABLE -> {
                val pointer = PlatformAbi.readPointer(resultOut)
                if (PlatformAbi.isNull(pointer)) {
                    null
                } else {
                    val reference = IUnknownReference(pointer.asRawComPtr())
                    try {
                        reference.asInspectable()
                    } finally {
                        reference.close()
                    }
                }
            }

            WinRTDelegateValueKind.BOOLEAN -> PlatformAbi.readInt8(resultOut).toInt() != 0
            WinRTDelegateValueKind.INT8 -> PlatformAbi.readInt8(resultOut)
            WinRTDelegateValueKind.UINT8 -> PlatformAbi.readInt8(resultOut).toUByte()
            WinRTDelegateValueKind.INT16 -> PlatformAbi.readInt16(resultOut)
            WinRTDelegateValueKind.UINT16 -> PlatformAbi.readInt16(resultOut).toUShort()
            WinRTDelegateValueKind.INT32 -> PlatformAbi.readInt32(resultOut)
            WinRTDelegateValueKind.UINT32 -> PlatformAbi.readInt32(resultOut).toUInt()
            WinRTDelegateValueKind.INT64 -> PlatformAbi.readInt64(resultOut)
            WinRTDelegateValueKind.UINT64 -> PlatformAbi.readInt64(resultOut).toULong()
            WinRTDelegateValueKind.FLOAT -> PlatformAbi.readFloat(resultOut)
            WinRTDelegateValueKind.DOUBLE -> PlatformAbi.readDouble(resultOut)
            WinRTDelegateValueKind.CHAR16 -> PlatformAbi.readInt16(resultOut).toInt().toChar()
            WinRTDelegateValueKind.GUID -> PlatformAbi.readGuid(resultOut)
            WinRTDelegateValueKind.STRUCT -> error("STRUCT return decode requires a NativeStructAdapter.")
            WinRTDelegateValueKind.UINT8_ARRAY -> error("UINT8_ARRAY return decode requires array length metadata.")
            WinRTDelegateValueKind.HSTRING -> {
                val handle = PlatformAbi.readPointer(resultOut)
                if (PlatformAbi.isNull(handle)) {
                    null
                } else {
                    HString.fromHandle(handle, owner = true).use { it.toKString() }
                }
            }
        }

    fun allocateReturnOut(
        descriptor: WinRTDelegateDescriptor,
        scope: NativeScope,
    ): RawAddress =
        if (descriptor.returnKind == WinRTDelegateValueKind.STRUCT) {
            PlatformAbi.allocateBytes(scope, descriptor.requireReturnStructAdapter().layout.sizeBytes)
        } else {
            allocateReturnOut(descriptor.returnKind, scope)
        }

    fun decodeReturnValue(descriptor: WinRTDelegateDescriptor, resultOut: RawAddress): Any? =
        if (descriptor.returnKind == WinRTDelegateValueKind.STRUCT) {
            val adapter = descriptor.requireReturnStructAdapter()
            try {
                adapter.readProjected(resultOut)
            } finally {
                adapter.disposeAbi(resultOut)
            }
        } else {
            decodeReturnValue(descriptor.returnKind, resultOut)
        }

    fun writeReturnValue(
        kind: WinRTDelegateValueKind,
        value: Any?,
        resultOut: RawAddress,
    ) {
        when (kind) {
            WinRTDelegateValueKind.UNIT -> Unit
            WinRTDelegateValueKind.OBJECT -> PlatformAbi.writePointer(resultOut, WinRTObjectMarshaller.fromManaged(value))
            WinRTDelegateValueKind.IUNKNOWN -> PlatformAbi.writePointer(resultOut, encodeUnknownReference(value))
            WinRTDelegateValueKind.IINSPECTABLE -> PlatformAbi.writePointer(resultOut, encodeInspectableReference(value))
            WinRTDelegateValueKind.BOOLEAN -> PlatformAbi.writeInt8(resultOut, encodeBoolean(value))
            WinRTDelegateValueKind.INT8 -> PlatformAbi.writeInt8(resultOut, encodeInt8(value))
            WinRTDelegateValueKind.UINT8 -> PlatformAbi.writeInt8(resultOut, encodeUInt8(value))
            WinRTDelegateValueKind.INT16 -> PlatformAbi.writeInt16(resultOut, encodeInt16(value))
            WinRTDelegateValueKind.UINT16 -> PlatformAbi.writeInt16(resultOut, encodeUInt16(value))
            WinRTDelegateValueKind.INT32 -> PlatformAbi.writeInt32(resultOut, encodeInt32(value))
            WinRTDelegateValueKind.UINT32 -> PlatformAbi.writeInt32(resultOut, encodeUInt32(value))
            WinRTDelegateValueKind.INT64 -> PlatformAbi.writeInt64(resultOut, encodeInt64(value))
            WinRTDelegateValueKind.UINT64 -> PlatformAbi.writeInt64(resultOut, encodeUInt64(value))
            WinRTDelegateValueKind.FLOAT -> PlatformAbi.writeFloat(resultOut, encodeFloat(value))
            WinRTDelegateValueKind.DOUBLE -> PlatformAbi.writeDouble(resultOut, encodeDouble(value))
            WinRTDelegateValueKind.CHAR16 -> PlatformAbi.writeInt16(resultOut, encodeChar16(value))
            WinRTDelegateValueKind.GUID -> PlatformAbi.writeGuid(resultOut, encodeGuid(value))
            WinRTDelegateValueKind.STRUCT -> error("STRUCT return write requires a NativeStructAdapter.")
            WinRTDelegateValueKind.UINT8_ARRAY -> error("UINT8_ARRAY return write requires array length metadata.")
            WinRTDelegateValueKind.HSTRING -> PlatformAbi.writePointer(resultOut, encodeHStringValue(value))
        }
    }

    fun writeReturnValue(
        descriptor: WinRTDelegateDescriptor,
        value: Any?,
        resultOut: RawAddress,
    ) {
        if (descriptor.returnKind == WinRTDelegateValueKind.STRUCT) {
            descriptor.requireReturnStructAdapter().writeProjected(value, resultOut)
        } else {
            writeReturnValue(descriptor.returnKind, value, resultOut)
        }
    }

    private fun decodeArgument(kind: WinRTDelegateValueKind, abiValue: Any?, adapter: NativeStructAdapter<*>?): Any? =
        when (kind) {
            WinRTDelegateValueKind.UNIT -> Unit
            WinRTDelegateValueKind.OBJECT -> decodeObject(abiValue)
            WinRTDelegateValueKind.IUNKNOWN -> decodeUnknownReference(abiValue)
            WinRTDelegateValueKind.IINSPECTABLE -> decodeInspectableReference(abiValue)
            WinRTDelegateValueKind.BOOLEAN -> decodeBoolean(abiValue)
            WinRTDelegateValueKind.INT8 -> decodeInt8(abiValue)
            WinRTDelegateValueKind.UINT8 -> decodeUInt8(abiValue)
            WinRTDelegateValueKind.INT16 -> decodeInt16(abiValue)
            WinRTDelegateValueKind.UINT16 -> decodeUInt16(abiValue)
            WinRTDelegateValueKind.INT32 -> decodeInt32(abiValue)
            WinRTDelegateValueKind.UINT32 -> decodeUInt32(abiValue)
            WinRTDelegateValueKind.INT64 -> decodeInt64(abiValue)
            WinRTDelegateValueKind.UINT64 -> decodeUInt64(abiValue)
            WinRTDelegateValueKind.FLOAT -> decodeFloat(abiValue)
            WinRTDelegateValueKind.DOUBLE -> decodeDouble(abiValue)
            WinRTDelegateValueKind.CHAR16 -> decodeChar16(abiValue)
            WinRTDelegateValueKind.GUID -> decodeGuid(abiValue)
            WinRTDelegateValueKind.STRUCT -> decodeStruct(abiValue, adapter)
            WinRTDelegateValueKind.UINT8_ARRAY -> error("UINT8_ARRAY argument decode requires length and data ABI arguments.")
            WinRTDelegateValueKind.HSTRING -> decodeHString(abiValue)
        }

    private fun encodeArgument(kind: WinRTDelegateValueKind, abiValue: Any?): Any? =
        encodeArgument(kind, abiValue, cleanup = null, adapter = null)

    private fun encodeArgument(
        kind: WinRTDelegateValueKind,
        abiValue: Any?,
        cleanup: MutableList<() -> Unit>?,
        adapter: NativeStructAdapter<*>?,
    ): Any? =
        when (kind) {
            WinRTDelegateValueKind.UNIT -> error("UNIT is not a valid ABI parameter kind.")
            WinRTDelegateValueKind.OBJECT -> encodeObject(abiValue, cleanup)
            WinRTDelegateValueKind.IUNKNOWN -> encodeUnknownReference(abiValue)
            WinRTDelegateValueKind.IINSPECTABLE -> encodeInspectableReference(abiValue)
            WinRTDelegateValueKind.BOOLEAN -> encodeBoolean(abiValue)
            WinRTDelegateValueKind.INT8 -> encodeInt8(abiValue)
            WinRTDelegateValueKind.UINT8 -> encodeUInt8(abiValue)
            WinRTDelegateValueKind.INT16 -> encodeInt16(abiValue)
            WinRTDelegateValueKind.UINT16 -> encodeUInt16(abiValue)
            WinRTDelegateValueKind.INT32 -> encodeInt32(abiValue)
            WinRTDelegateValueKind.UINT32 -> encodeUInt32(abiValue)
            WinRTDelegateValueKind.INT64 -> encodeInt64(abiValue)
            WinRTDelegateValueKind.UINT64 -> encodeUInt64(abiValue)
            WinRTDelegateValueKind.FLOAT -> encodeFloat(abiValue)
            WinRTDelegateValueKind.DOUBLE -> encodeDouble(abiValue)
            WinRTDelegateValueKind.CHAR16 -> encodeChar16(abiValue)
            WinRTDelegateValueKind.GUID -> encodeGuidArgument(abiValue, cleanup)
            WinRTDelegateValueKind.STRUCT -> encodeStructArgument(abiValue, cleanup, adapter)
            WinRTDelegateValueKind.UINT8_ARRAY -> error("UINT8_ARRAY argument encode requires flattened length and data ABI arguments.")
            WinRTDelegateValueKind.HSTRING -> encodeHStringArgument(abiValue, cleanup)
        }

    private fun decodeObject(abiValue: Any?): Any? = when (abiValue) {
        null -> null
        is ComObjectReference -> WinRTObjectMarshaller.fromAbi(abiValue.pointer.asRawAddress())
        is RawAddress ->
            if (PlatformAbi.isNull(abiValue)) {
                null
            } else {
                WinRTObjectMarshaller.fromAbi(abiValue)
            }
        else -> error("Unsupported ABI object argument: ${abiValue::class.qualifiedName}")
    }

    private fun decodeUnknownReference(abiValue: Any?): IUnknownReference? =
        decodeObjectReference(abiValue)?.let { reference ->
            reference as? IUnknownReference ?: IUnknownReference(reference.pointer)
        }

    private fun decodeInspectableReference(abiValue: Any?): IInspectableReference? =
        decodeUnknownReference(abiValue)?.asInspectable()

    private fun decodeObjectReference(abiValue: Any?): ComObjectReference? = when (abiValue) {
        null -> null
        is ComObjectReference -> abiValue
        is RawAddress ->
            if (PlatformAbi.isNull(abiValue)) {
                null
            } else {
                IUnknownReference(abiValue.asRawComPtr(), preventReleaseOnDispose = true)
            }
        else -> error("Unsupported ABI object reference argument: ${abiValue::class.qualifiedName}")
    }

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

    private fun decodeGuid(abiValue: Any?): Guid = when (abiValue) {
        is Guid -> abiValue
        is RawAddress -> PlatformAbi.readGuid(abiValue)
        else -> error("Unsupported ABI GUID argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun decodeStruct(abiValue: Any?, adapter: NativeStructAdapter<*>?): Any? {
        val source = abiValue as? RawAddress
            ?: error("Unsupported ABI struct argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
        return adapter?.readProjected(source) ?: source
    }

    private fun encodeObject(
        abiValue: Any?,
        cleanup: MutableList<() -> Unit>? = null,
    ): RawAddress {
        if (cleanup == null) {
            return when (abiValue) {
                null -> PlatformAbi.nullPointer
                is RawAddress -> abiValue
                is RawComPtr -> abiValue.asRawAddress()
                else -> error("Outbound ABI object arguments require an EncodedDelegateArguments lease.")
            }
        }

        val marshaler = WinRTObjectMarshaller.createMarshaler(abiValue)
        cleanup += marshaler::close
        return marshaler.abi
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

    private fun encodeGuid(abiValue: Any?): Guid = when (abiValue) {
        is Guid -> abiValue
        is RawAddress -> PlatformAbi.readGuid(abiValue)
        else -> error("Unsupported outbound ABI GUID value: ${abiValue?.let { it::class.qualifiedName } ?: "null"}")
    }

    private fun encodeGuidArgument(
        abiValue: Any?,
        cleanup: MutableList<() -> Unit>?,
    ): RawAddress = when (abiValue) {
        is RawAddress -> abiValue
        else -> {
            check(cleanup != null) {
                "Unsupported outbound ABI GUID argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}"
            }
            val scope = PlatformAbi.confinedScope()
            val destination = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            PlatformAbi.writeGuid(destination, encodeGuid(abiValue))
            cleanup += { scope.close() }
            destination
        }
    }

    private fun encodeStructArgument(
        abiValue: Any?,
        cleanup: MutableList<() -> Unit>?,
        adapter: NativeStructAdapter<*>?,
    ): RawAddress = when (abiValue) {
        is RawAddress -> abiValue
        else -> {
            val structAdapter = adapter ?: error("Outbound ABI struct argument requires a NativeStructAdapter.")
            check(cleanup != null) {
                "Unsupported outbound ABI struct argument: ${abiValue?.let { it::class.qualifiedName } ?: "null"}"
            }
            val scope = PlatformAbi.confinedScope()
            val destination = PlatformAbi.allocateBytes(scope, structAdapter.layout.sizeBytes)
            structAdapter.writeProjected(abiValue, destination)
            cleanup += {
                structAdapter.disposeAbi(destination)
                scope.close()
            }
            destination
        }
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

    private fun abiKindFor(kind: WinRTDelegateValueKind, adapter: NativeStructAdapter<*>?): ComAbiValueKind =
        when (kind) {
            WinRTDelegateValueKind.UNIT -> error("UNIT does not have an ABI layout.")
            WinRTDelegateValueKind.OBJECT,
            WinRTDelegateValueKind.HSTRING,
            WinRTDelegateValueKind.IUNKNOWN,
            WinRTDelegateValueKind.IINSPECTABLE,
            WinRTDelegateValueKind.UINT8_ARRAY,
            -> ComAbiValueKind.Pointer

            WinRTDelegateValueKind.BOOLEAN ->
                ComAbiValueKind.Int8

            WinRTDelegateValueKind.INT8,
            WinRTDelegateValueKind.UINT8,
            -> ComAbiValueKind.Int8

            WinRTDelegateValueKind.INT16,
            WinRTDelegateValueKind.UINT16,
            WinRTDelegateValueKind.CHAR16 ->
                ComAbiValueKind.Int16

            WinRTDelegateValueKind.INT32,
            WinRTDelegateValueKind.UINT32,
            -> ComAbiValueKind.Int32

            WinRTDelegateValueKind.INT64 ->
                ComAbiValueKind.Int64

            WinRTDelegateValueKind.UINT64 ->
                ComAbiValueKind.Int64

            WinRTDelegateValueKind.FLOAT ->
                ComAbiValueKind.Float

            WinRTDelegateValueKind.DOUBLE ->
                ComAbiValueKind.Double

            WinRTDelegateValueKind.GUID ->
                ComAbiValueKind.Struct(NativeAbiLayout.GUID)

            WinRTDelegateValueKind.STRUCT ->
                ComAbiValueKind.Struct((adapter ?: error("STRUCT ABI kind requires a NativeStructAdapter.")).layout.abiLayout)
        }

    private fun abiKindsForParameter(kind: WinRTDelegateValueKind, adapter: NativeStructAdapter<*>?): List<ComAbiValueKind> =
        when (kind) {
            WinRTDelegateValueKind.UINT8_ARRAY -> listOf(ComAbiValueKind.Int32, ComAbiValueKind.Pointer)
            else -> listOf(abiKindFor(kind, adapter))
        }

    private fun expectedAbiArgumentCount(parameterKinds: List<WinRTDelegateValueKind>): Int =
        parameterKinds.sumOf { kind -> if (kind == WinRTDelegateValueKind.UINT8_ARRAY) 2 else 1 }

    private inline fun decodeArgumentList(
        parameterKinds: List<WinRTDelegateValueKind>,
        abiArguments: List<Any?>,
        adapterAt: (Int, WinRTDelegateValueKind) -> NativeStructAdapter<*>?,
    ): List<Any?> {
        val decoded = ArrayList<Any?>(parameterKinds.size)
        var abiIndex = 0
        parameterKinds.forEachIndexed { parameterIndex, kind ->
            if (kind == WinRTDelegateValueKind.UINT8_ARRAY) {
                decoded.add(decodeUInt8Array(abiArguments[abiIndex], abiArguments[abiIndex + 1]))
                abiIndex += 2
            } else {
                decoded.add(decodeArgument(kind, abiArguments[abiIndex], adapterAt(parameterIndex, kind)))
                abiIndex += 1
            }
        }
        return decoded
    }

    private inline fun encodeArgumentList(
        parameterKinds: List<WinRTDelegateValueKind>,
        abiArguments: List<Any?>,
        cleanup: MutableList<() -> Unit>,
        adapterAt: (Int, WinRTDelegateValueKind) -> NativeStructAdapter<*>?,
    ): List<Any?> {
        val encoded = ArrayList<Any?>(expectedAbiArgumentCount(parameterKinds))
        parameterKinds.zip(abiArguments).forEachIndexed { parameterIndex, (kind, value) ->
            if (kind == WinRTDelegateValueKind.UINT8_ARRAY) {
                val array = encodeUInt8ArrayArgument(value, cleanup)
                encoded += array.length
                encoded += array.data
            } else {
                encoded += encodeArgument(kind, value, cleanup, adapterAt(parameterIndex, kind))
            }
        }
        return encoded
    }

    private fun decodeUInt8Array(lengthAbi: Any?, dataAbi: Any?): Array<UByte> {
        val length = decodeInt32(lengthAbi)
        val data = dataAbi as? RawAddress
            ?: error("Unsupported ABI UInt8 array data argument: ${dataAbi?.let { it::class.qualifiedName } ?: "null"}")
        return Marshaler.uint8().fromAbiArray(length, data)?.filterNotNull()?.toTypedArray() ?: emptyArray()
    }

    private fun encodeUInt8ArrayArgument(value: Any?, cleanup: MutableList<() -> Unit>): WinRTAbiArray {
        val values = when (value) {
            null -> emptyArray()
            is Array<*> -> value.filterIsInstance<UByte>().toTypedArray()
            is ByteArray -> value.map { it.toUByte() }.toTypedArray()
            else -> error("Unsupported outbound ABI UInt8 array argument: ${value::class.qualifiedName}")
        }
        val array = Marshaler.uint8().createMarshalerArray(values) ?: WinRTAbiArray(0, PlatformAbi.nullPointer)
        cleanup += array::close
        return array
    }
}

private fun WinRTDelegateDescriptor.requireReturnStructAdapter(): NativeStructAdapter<*> =
    returnStructAdapter ?: error("Delegate STRUCT return requires a NativeStructAdapter.")

@Suppress("UNCHECKED_CAST")
private fun NativeStructAdapter<*>.readProjected(source: RawAddress): Any? =
    (this as NativeStructAdapter<Any?>).read(source)

@Suppress("UNCHECKED_CAST")
private fun NativeStructAdapter<*>.writeProjected(value: Any?, destination: RawAddress) {
    (this as NativeStructAdapter<Any?>).write(value, destination)
}

internal class EncodedDelegateArguments(
    val values: List<Any?>,
    private val cleanup: List<() -> Unit>,
) : AutoCloseable {
    override fun close() {
        cleanup.asReversed().forEach { it() }
    }
}
