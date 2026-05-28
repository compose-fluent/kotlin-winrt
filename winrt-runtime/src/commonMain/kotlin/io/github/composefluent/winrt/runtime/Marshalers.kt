package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

enum class WinRtAbiCategory {
    BLITTABLE,
    STRING,
    INTERFACE,
    INSPECTABLE,
    DELEGATE,
}

class WinRtAbiArray internal constructor(
    val length: Int,
    val data: RawAddress,
    private val cleanup: (() -> Unit)? = null,
) : AutoCloseable {
    override fun close() {
        cleanup?.invoke()
    }
}

class Marshaler<T> internal constructor(
    val abiCategory: WinRtAbiCategory,
    private val createMarshalerImpl: (T?) -> Any?,
    private val getAbiImpl: (Any?) -> Any?,
    private val fromAbiImpl: (Any?) -> T?,
    private val fromManagedImpl: (T?) -> Any?,
    private val copyAbiImpl: (Any?, RawAddress) -> Unit,
    private val copyManagedImpl: (T?, RawAddress) -> Unit,
    private val disposeMarshalerImpl: (Any?) -> Unit,
    private val disposeAbiImpl: (Any?) -> Unit,
    private val createMarshalerArrayImpl: (Array<out T?>?) -> WinRtAbiArray?,
    private val fromAbiArrayImpl: (Int, RawAddress) -> List<T?>?,
    private val fromManagedArrayImpl: (Array<out T?>?) -> WinRtAbiArray?,
    private val copyManagedArrayImpl: (Array<out T?>?, RawAddress) -> Unit,
    private val disposeMarshalerArrayImpl: (WinRtAbiArray?) -> Unit,
    private val disposeAbiArrayImpl: (Int, RawAddress) -> Unit,
) {
    fun createMarshaler(value: T?): Any? = createMarshalerImpl(value)

    fun getAbi(value: Any?): Any? = getAbiImpl(value)

    fun fromAbi(value: Any?): T? = fromAbiImpl(value)

    fun fromManaged(value: T?): Any? = fromManagedImpl(value)

    fun copyAbi(value: Any?, destination: RawAddress) {
        copyAbiImpl(value, destination)
    }

    fun copyManaged(value: T?, destination: RawAddress) {
        copyManagedImpl(value, destination)
    }

    fun disposeMarshaler(value: Any?) {
        disposeMarshalerImpl(value)
    }

    fun disposeAbi(value: Any?) {
        disposeAbiImpl(value)
    }

    fun createMarshalerArray(values: Array<out T?>?): WinRtAbiArray? = createMarshalerArrayImpl(values)

    fun fromAbiArray(length: Int, data: RawAddress): List<T?>? = fromAbiArrayImpl(length, data)

    fun fromManagedArray(values: Array<out T?>?): WinRtAbiArray? = fromManagedArrayImpl(values)

    fun copyManagedArray(values: Array<out T?>?, destination: RawAddress) {
        copyManagedArrayImpl(values, destination)
    }

    fun disposeMarshalerArray(array: WinRtAbiArray?) {
        disposeMarshalerArrayImpl(array)
    }

    fun disposeAbiArray(length: Int, data: RawAddress) {
        disposeAbiArrayImpl(length, data)
    }

    companion object {
        fun int8(): Marshaler<Byte> = MarshalBlittable.int8()

        fun uint8(): Marshaler<UByte> = MarshalBlittable.uint8()

        fun int16(): Marshaler<Short> = MarshalBlittable.int16()

        fun uint16(): Marshaler<UShort> = MarshalBlittable.uint16()

        fun boolean(): Marshaler<Boolean> = MarshalBlittable.boolean()

        fun char16(): Marshaler<Char> = MarshalBlittable.char16()

        fun int32(): Marshaler<Int> = MarshalBlittable.int32()

        fun uint32(): Marshaler<UInt> = MarshalBlittable.uint32()

        fun int64(): Marshaler<Long> = MarshalBlittable.int64()

        fun uint64(): Marshaler<ULong> = MarshalBlittable.uint64()

        fun float32(): Marshaler<Float> = MarshalBlittable.float32()

        fun float64(): Marshaler<Double> = MarshalBlittable.float64()

        fun guid(): Marshaler<Guid> = MarshalBlittable.guid()

        fun string(): Marshaler<String> = MarshalString.marshaler()

        fun <T : Any> interfaceType(
            typeHandle: WinRtTypeHandle,
            expectedType: KClass<T>,
            projector: (Any?) -> T = { expectedType.castProjected(it) },
        ): Marshaler<T> = MarshalInterface.of(typeHandle, expectedType, projector)

        fun <T : Any> inspectable(
            expectedType: KClass<T>,
            projector: (Any?) -> T = { expectedType.castProjected(it) },
        ): Marshaler<T> = MarshalInspectable.of(expectedType, projector)

        fun inspectableAny(): Marshaler<Any?> = MarshalInspectable.any()

        fun <T> genericParameter(): Marshaler<T> = MarshalGenericParameter.of()

        fun <T> referenceValueAdapter(adapter: WinRtReferenceValueAdapter<T>): Marshaler<T> =
            MarshalReferenceValueAdapter.of(adapter)
    }
}

object MarshalBlittable {
    fun int8(): Marshaler<Byte> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.INT8,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Byte -> abi
                is UByte -> abi.toByte()
                is RawAddress -> PlatformAbi.readInt8(abi)
                else -> error("Expected ABI Int8, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = { value, destination -> PlatformAbi.writeInt8(destination, value) },
    )

    fun uint8(): Marshaler<UByte> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.INT8,
        toAbi = { it.toByte() },
        fromAbi = { abi ->
            when (abi) {
                is Byte -> abi.toUByte()
                is UByte -> abi
                is RawAddress -> PlatformAbi.readInt8(abi).toUByte()
                else -> error("Expected ABI UInt8, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = { value, destination -> PlatformAbi.writeInt8(destination, value.toByte()) },
    )

    fun int16(): Marshaler<Short> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.INT16,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Short -> abi
                is UShort -> abi.toShort()
                is RawAddress -> PlatformAbi.readInt16(abi)
                else -> error("Expected ABI Int16, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = { value, destination -> PlatformAbi.writeInt16(destination, value) },
    )

    fun uint16(): Marshaler<UShort> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.INT16,
        toAbi = { it.toShort() },
        fromAbi = { abi ->
            when (abi) {
                is Short -> abi.toUShort()
                is UShort -> abi
                is RawAddress -> PlatformAbi.readInt16(abi).toUShort()
                else -> error("Expected ABI UInt16, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = { value, destination -> PlatformAbi.writeInt16(destination, value.toShort()) },
    )

    fun boolean(): Marshaler<Boolean> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.INT8,
        toAbi = BooleanMarshaller::toAbi,
        fromAbi = { abi ->
            when (abi) {
                is Byte -> BooleanMarshaller.fromAbi(abi)
                is RawAddress -> BooleanMarshaller.readFrom(abi)
                else -> error("Expected ABI Boolean byte, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = BooleanMarshaller::copyTo,
    )

    fun char16(): Marshaler<Char> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.CHAR16,
        toAbi = CharMarshaller::toAbi,
        fromAbi = { abi ->
            when (abi) {
                is Short -> CharMarshaller.fromAbi(abi)
                is Char -> abi
                is RawAddress -> CharMarshaller.readFrom(abi)
                else -> error("Expected ABI char16, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = CharMarshaller::copyTo,
    )

    fun int32(): Marshaler<Int> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.INT32,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Int -> abi
                is UInt -> abi.toInt()
                is RawAddress -> PlatformAbi.readInt32(abi)
                else -> error("Expected ABI Int32, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = { value, destination -> PlatformAbi.writeInt32(destination, value) },
    )

    fun uint32(): Marshaler<UInt> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.INT32,
        toAbi = { it.toInt() },
        fromAbi = { abi ->
            when (abi) {
                is Int -> abi.toUInt()
                is UInt -> abi
                is RawAddress -> PlatformAbi.readInt32(abi).toUInt()
                else -> error("Expected ABI UInt32, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = { value, destination -> PlatformAbi.writeInt32(destination, value.toInt()) },
    )

    fun int64(): Marshaler<Long> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.INT64,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Long -> abi
                is ULong -> abi.toLong()
                is RawAddress -> PlatformAbi.readInt64(abi)
                else -> error("Expected ABI Int64, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = { value, destination -> PlatformAbi.writeInt64(destination, value) },
    )

    fun uint64(): Marshaler<ULong> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.INT64,
        toAbi = { it.toLong() },
        fromAbi = { abi ->
            when (abi) {
                is Long -> abi.toULong()
                is ULong -> abi
                is RawAddress -> PlatformAbi.readInt64(abi).toULong()
                else -> error("Expected ABI UInt64, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = { value, destination -> PlatformAbi.writeInt64(destination, value.toLong()) },
    )

    fun float32(): Marshaler<Float> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.FLOAT32,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Float -> abi
                is RawAddress -> PlatformAbi.readFloat(abi)
                else -> error("Expected ABI Float32, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = { value, destination -> PlatformAbi.writeFloat(destination, value) },
    )

    fun float64(): Marshaler<Double> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.DOUBLE,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Double -> abi
                is RawAddress -> PlatformAbi.readDouble(abi)
                else -> error("Expected ABI Float64, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = { value, destination -> PlatformAbi.writeDouble(destination, value) },
    )

    fun guid(): Marshaler<Guid> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        abiKind = NativeStructScalarKind.GUID,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Guid -> abi
                is RawAddress -> GuidMarshaller.readFrom(abi)
                else -> error("Expected ABI GUID, got '${abiTypeName(abi)}'.")
            }
        },
        copyManaged = GuidMarshaller::copyTo,
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T, ABI : Any> scalar(
        category: WinRtAbiCategory,
        abiKind: NativeStructScalarKind,
        toAbi: (T) -> ABI,
        fromAbi: (Any?) -> T,
        copyManaged: (T, RawAddress) -> Unit,
    ): Marshaler<T> =
        Marshaler(
            abiCategory = category,
            createMarshalerImpl = { it },
            getAbiImpl = { value ->
                when (value) {
                    null -> null
                    is RawAddress -> fromAbi(value).let(toAbi)
                    else -> toAbi(value as T)
                }
            },
            fromAbiImpl = { abi -> abi?.let(fromAbi) },
            fromManagedImpl = { value -> value?.let(toAbi) },
            copyAbiImpl = { abi, destination ->
                val typed = abi?.let(fromAbi)
                if (typed == null) {
                    writeZeroValue(abiKind, destination)
                } else {
                    copyManaged(typed, destination)
                }
            },
            copyManagedImpl = { value, destination ->
                if (value == null) {
                    writeZeroValue(abiKind, destination)
                } else {
                    copyManaged(value, destination)
                }
            },
            disposeMarshalerImpl = {},
            disposeAbiImpl = {},
            createMarshalerArrayImpl = { values ->
                createBlittableArray(values, abiKind, copyManaged)
            },
            fromAbiArrayImpl = { length, data ->
                decodeBlittableArray(length, data, abiKind, fromAbi)
            },
            fromManagedArrayImpl = { values ->
                createBlittableArray(values, abiKind, copyManaged)
            },
            copyManagedArrayImpl = { values, destination ->
                copyBlittableArray(values, destination, abiKind, copyManaged)
            },
            disposeMarshalerArrayImpl = { array -> array?.close() },
            disposeAbiArrayImpl = { _, _ -> },
        )
}

object MarshalString {
    fun marshaler(): Marshaler<String> =
        pointerMarshaler(
            category = WinRtAbiCategory.STRING,
            nullFromAbi = { NativeStringMarshaller.fromAbi(PlatformAbi.nullPointer) },
            createMarshaler = NativeStringMarshaller::createMarshaler,
            getAbiPointer = { value ->
                when (value) {
                    null -> PlatformAbi.nullPointer
                    is ReferencedHString -> NativeStringMarshaller.getAbi(value)
                    is HString -> NativeStringMarshaller.getAbi(value)
                    is RawAddress -> value
                    else -> error("Expected HSTRING marshaler, got '${abiTypeName(value)}'.")
                }
            },
            fromAbiPointer = NativeStringMarshaller::fromAbi,
            fromManagedPointer = { value -> NativeStringMarshaller.fromManaged(value)?.handle ?: PlatformAbi.nullPointer },
            disposeMarshaler = { value ->
                when (value) {
                    null -> Unit
                    is ReferencedHString -> NativeStringMarshaller.disposeMarshaler(value)
                    is HString -> value.close()
                    else -> error("Expected HSTRING marshaler, got '${abiTypeName(value)}'.")
                }
            },
            disposeAbiPointer = NativeStringMarshaller::disposeAbi,
        )
}

object MarshalInterface {
    fun <T : Any> of(
        typeHandle: WinRtTypeHandle,
        expectedType: KClass<T>,
        projector: (Any?) -> T = { expectedType.castProjected(it) },
    ): Marshaler<T> =
        pointerMarshaler(
            category = WinRtAbiCategory.INTERFACE,
            createMarshaler = { value ->
                when (value) {
                    null -> null
                    else ->
                        ComWrappersSupport.tryUnwrapObject(value, typeHandle)
                            ?: ComWrappersSupport.createCCWForObject(value, typeHandle.interfaceId)
                }
            },
            getAbiPointer = { value ->
                when (value) {
                    null -> PlatformAbi.nullPointer
                    is ComObjectReference -> value.pointer.asRawAddress()
                    is RawAddress -> value
                    else -> error("Expected COM object reference marshaler, got '${abiTypeName(value)}'.")
                }
            },
            fromAbiPointer = { pointer ->
                ComWrappersSupport.findObject(pointer, expectedType)
                    ?: projector(ComWrappersSupport.createRcwForComObject(pointer, typeHandle))
            },
            fromManagedPointer = { value ->
                when (value) {
                    null -> PlatformAbi.nullPointer
                    else -> ComWrappersSupport.createCCWForObject(value, typeHandle.interfaceId).useAndGetRef()
                }
            },
            disposeMarshaler = { value -> (value as? ComObjectReference)?.close() },
            disposeAbiPointer = { pointer ->
                if (!PlatformAbi.isNull(pointer)) {
                    IUnknownReference(pointer.asRawComPtr(), typeHandle.interfaceId).close()
                }
            },
        )
}

object MarshalInspectable {
    fun <T : Any> of(
        expectedType: KClass<T>,
        projector: (Any?) -> T = { expectedType.castProjected(it) },
    ): Marshaler<T> =
        pointerMarshaler(
            category = WinRtAbiCategory.INSPECTABLE,
            createMarshaler = { value ->
                when (value) {
                    null -> null
                    else ->
                        ComWrappersSupport.tryUnwrapObject(value)
                            ?: ComWrappersSupport.createCCWForObject(value, IID.IInspectable)
                }
            },
            getAbiPointer = { value ->
                when (value) {
                    null -> PlatformAbi.nullPointer
                    is ComObjectReference -> value.pointer.asRawAddress()
                    is RawAddress -> value
                    else -> error("Expected inspectable marshaler, got '${abiTypeName(value)}'.")
                }
            },
            fromAbiPointer = { pointer ->
                ComWrappersSupport.findObject(pointer, expectedType)
                    ?: projector(ComWrappersSupport.createRcwForComObject(pointer))
            },
            fromManagedPointer = { value ->
                when (value) {
                    null -> PlatformAbi.nullPointer
                    else -> ComWrappersSupport.createCCWForObject(value, IID.IInspectable).useAndGetRef()
                }
            },
            disposeMarshaler = { value -> (value as? ComObjectReference)?.close() },
            disposeAbiPointer = { pointer ->
                if (!PlatformAbi.isNull(pointer)) {
                    IUnknownReference(pointer.asRawComPtr(), IID.IInspectable).close()
                }
            },
        )

    fun any(): Marshaler<Any?> =
        pointerMarshaler(
            category = WinRtAbiCategory.INSPECTABLE,
            createMarshaler = WinRtObjectMarshaller::createMarshaler,
            getAbiPointer = { value ->
                when (value) {
                    null -> PlatformAbi.nullPointer
                    is WinRtObjectMarshaler -> value.abi
                    is ComObjectReference -> value.pointer.asRawAddress()
                    is RawAddress -> value
                    else -> error("Expected inspectable marshaler, got '${abiTypeName(value)}'.")
                }
            },
            fromAbiPointer = WinRtObjectMarshaller::fromAbi,
            fromManagedPointer = WinRtObjectMarshaller::fromManaged,
            disposeMarshaler = { value -> (value as? AutoCloseable)?.close() },
            disposeAbiPointer = { pointer ->
                if (!PlatformAbi.isNull(pointer)) {
                    IUnknownReference(pointer.asRawComPtr(), IID.IInspectable).close()
                }
            },
        )
}

object MarshalGenericParameter {
    fun <T> of(): Marshaler<T> =
        pointerMarshaler(
            category = WinRtAbiCategory.INSPECTABLE,
            createMarshaler = WinRtObjectMarshaller::createMarshaler,
            getAbiPointer = { value ->
                when (value) {
                    null -> PlatformAbi.nullPointer
                    is WinRtObjectMarshaler -> value.abi
                    is ComObjectReference -> value.pointer.asRawAddress()
                    is RawAddress -> value
                    else -> error("Expected generic parameter marshaler, got '${abiTypeName(value)}'.")
                }
            },
            fromAbiPointer = { pointer -> WinRtGenericParameterProjection.fromAbi(pointer) },
            fromManagedPointer = { value -> WinRtGenericParameterProjection.fromManaged(value) },
            disposeMarshaler = { value -> (value as? AutoCloseable)?.close() },
            disposeAbiPointer = { pointer ->
                if (!PlatformAbi.isNull(pointer)) {
                    IUnknownReference(pointer.asRawComPtr(), IID.IInspectable).close()
                }
            },
        )
}

object MarshalReferenceValueAdapter {
    fun <T> of(adapter: WinRtReferenceValueAdapter<T>): Marshaler<T> =
        pointerMarshaler(
            category = WinRtAbiCategory.INTERFACE,
            nullFromAbi = { adapter.projector(null) },
            createMarshaler = { value -> value?.let(adapter::createOutputMarshaler) },
            getAbiPointer = { value ->
                when (value) {
                    null -> PlatformAbi.nullPointer
                    is WinRtObjectMarshaler -> value.abi
                    is ComObjectReference -> value.pointer.asRawAddress()
                    is RawAddress -> value
                    else -> error("Expected reference-value adapter marshaler, got '${abiTypeName(value)}'.")
                }
            },
            fromAbiPointer = { pointer ->
                adapter.projector(IUnknownReference(pointer.asRawComPtr(), preventReleaseOnDispose = true))
            },
            fromManagedPointer = { value ->
                value?.let { adapter.createOutputMarshaler(it).use { marshaler -> marshaler.abi } }
                    ?: PlatformAbi.nullPointer
            },
            disposeMarshaler = { value -> (value as? AutoCloseable)?.close() },
            disposeAbiPointer = { pointer ->
                if (!PlatformAbi.isNull(pointer)) {
                    IUnknownReference(pointer.asRawComPtr()).close()
                }
            },
        )
}

object MarshalDelegate {
    fun createMarshaler(value: WinRtDelegateReference?): WinRtDelegateReference? =
        value?.let { WinRtDelegateReference.fromAbi(it.getRefPointer().asRawAddress(), it.descriptor) }

    fun getAbi(value: WinRtDelegateReference?): RawAddress = value?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer

    fun fromAbi(pointer: RawAddress, descriptor: WinRtDelegateDescriptor): WinRtDelegateReference? =
        WinRtDelegateReference.fromAbi(pointer, descriptor)

    fun fromManaged(value: WinRtDelegateHandle?): RawAddress =
        value?.createReference()?.useAndGetRef() ?: PlatformAbi.nullPointer

    fun disposeMarshaler(value: WinRtDelegateReference?) {
        value?.close()
    }

    fun disposeAbi(pointer: RawAddress, descriptor: WinRtDelegateDescriptor) {
        if (PlatformAbi.isNull(pointer)) {
            return
        }
        WinRtDelegateReference.fromAbi(pointer, descriptor)?.close()
    }

    fun createMarshalerArray(values: Array<out WinRtDelegateReference?>?): WinRtAbiArray? =
        createPointerArrayFromMarshallers(
            values = values,
            createMarshaler = ::createMarshaler,
            getAbiPointer = { value -> getAbi(value as? WinRtDelegateReference) },
            disposeMarshaler = { value -> disposeMarshaler(value as? WinRtDelegateReference) },
        )

    fun fromManagedArray(values: Array<out WinRtDelegateHandle?>?): WinRtAbiArray? =
        createPointerArrayFromAbiValues(
            values = values,
            fromManagedPointer = ::fromManaged,
            disposeAbiPointer = { _ -> },
        )

    fun fromAbiArray(
        length: Int,
        data: RawAddress,
        descriptor: WinRtDelegateDescriptor,
    ): List<WinRtDelegateReference?>? =
        decodePointerArray(
            length = length,
            data = data,
            fromAbiPointer = { pointer -> fromAbi(pointer, descriptor) },
        )

    fun disposeAbiArray(
        length: Int,
        data: RawAddress,
        descriptor: WinRtDelegateDescriptor,
    ) {
        disposePointerArray(length, data) { pointer ->
            disposeAbi(pointer, descriptor)
        }
    }
}

private fun <T> pointerMarshaler(
    category: WinRtAbiCategory,
    nullFromAbi: () -> T? = { null },
    createMarshaler: (T?) -> Any?,
    getAbiPointer: (Any?) -> RawAddress,
    fromAbiPointer: (RawAddress) -> T?,
    fromManagedPointer: (T?) -> RawAddress,
    disposeMarshaler: (Any?) -> Unit,
    disposeAbiPointer: (RawAddress) -> Unit,
): Marshaler<T> =
    Marshaler(
        abiCategory = category,
        createMarshalerImpl = createMarshaler,
        getAbiImpl = { value -> getAbiPointer(value) },
        fromAbiImpl = { abi ->
            val pointer = abiPointer(abi)
            if (PlatformAbi.isNull(pointer)) {
                nullFromAbi()
            } else {
                fromAbiPointer(pointer)
            }
        },
        fromManagedImpl = { value -> fromManagedPointer(value) },
        copyAbiImpl = { abi, destination ->
            PlatformAbi.writePointer(destination, abiPointer(abi))
        },
        copyManagedImpl = { value, destination ->
            PlatformAbi.writePointer(destination, fromManagedPointer(value))
        },
        disposeMarshalerImpl = disposeMarshaler,
        disposeAbiImpl = { abi -> disposeAbiPointer(abiPointer(abi)) },
        createMarshalerArrayImpl = { values ->
            createPointerArrayFromMarshallers(values, createMarshaler, getAbiPointer, disposeMarshaler)
        },
        fromAbiArrayImpl = { length, data ->
            decodePointerArray(length, data, fromAbiPointer, nullFromAbi)
        },
        fromManagedArrayImpl = { values ->
            createPointerArrayFromAbiValues(values, fromManagedPointer, disposeAbiPointer)
        },
        copyManagedArrayImpl = { values, destination ->
            copyManagedPointerArray(values, destination, fromManagedPointer, disposeAbiPointer)
        },
        disposeMarshalerArrayImpl = { array -> array?.close() },
        disposeAbiArrayImpl = { length, data ->
            disposePointerArray(length, data, disposeAbiPointer)
        },
    )

private fun <T> createBlittableArray(
    values: Array<out T?>?,
    abiKind: NativeStructScalarKind,
    copyManaged: (T, RawAddress) -> Unit,
): WinRtAbiArray? {
    if (values == null) {
        return null
    }

    val scope = PlatformAbi.confinedScope()
    return try {
        val data =
            PlatformAbi.allocateBytes(
                scope = scope,
                sizeBytes = abiKind.sizeBytes * values.size.toLong(),
                alignmentBytes = abiKind.alignmentBytes,
            )
        values.forEachIndexed { index, value ->
            val slice = data.elementSlice(index, abiKind.sizeBytes)
            if (value == null) {
                writeZeroValue(abiKind, slice)
            } else {
                copyManaged(value, slice)
            }
        }
        WinRtAbiArray(values.size, data, scope::close)
    } catch (error: Throwable) {
        scope.close()
        throw error
    }
}

private fun <T> decodeBlittableArray(
    length: Int,
    data: RawAddress,
    abiKind: NativeStructScalarKind,
    fromAbi: (Any?) -> T,
): List<T?>? {
    if (PlatformAbi.isNull(data)) {
        return null
    }
    return List(length) { index ->
        fromAbi(data.elementSlice(index, abiKind.sizeBytes))
    }
}

private fun <T> copyBlittableArray(
    values: Array<out T?>?,
    destination: RawAddress,
    abiKind: NativeStructScalarKind,
    copyManaged: (T, RawAddress) -> Unit,
) {
    if (values == null) {
        return
    }
    values.forEachIndexed { index, value ->
        val slice = destination.elementSlice(index, abiKind.sizeBytes)
        if (value == null) {
            writeZeroValue(abiKind, slice)
        } else {
            copyManaged(value, slice)
        }
    }
}

private fun <T> createPointerArrayFromMarshallers(
    values: Array<out T?>?,
    createMarshaler: (T?) -> Any?,
    getAbiPointer: (Any?) -> RawAddress,
    disposeMarshaler: (Any?) -> Unit,
): WinRtAbiArray? {
    if (values == null) {
        return null
    }

    val scope = PlatformAbi.confinedScope()
    val marshalers = mutableListOf<Any?>()
    return try {
        val data = PlatformAbi.allocatePointerArray(scope, values.size)
        values.forEachIndexed { index, value ->
            val marshaler = createMarshaler(value)
            marshalers += marshaler
            PlatformAbi.writePointerAt(data, index, getAbiPointer(marshaler))
        }
        WinRtAbiArray(
            length = values.size,
            data = data,
        ) {
            marshalers.forEach(disposeMarshaler)
            scope.close()
        }
    } catch (error: Throwable) {
        marshalers.forEach(disposeMarshaler)
        scope.close()
        throw error
    }
}

private fun <T> createPointerArrayFromAbiValues(
    values: Array<out T?>?,
    fromManagedPointer: (T?) -> RawAddress,
    disposeAbiPointer: (RawAddress) -> Unit,
): WinRtAbiArray? {
    if (values == null) {
        return null
    }

    val scope = PlatformAbi.confinedScope()
    val ownedPointers = mutableListOf<RawAddress>()
    return try {
        val data = PlatformAbi.allocatePointerArray(scope, values.size)
        values.forEachIndexed { index, value ->
            val pointer = fromManagedPointer(value)
            ownedPointers += pointer
            PlatformAbi.writePointerAt(data, index, pointer)
        }
        WinRtAbiArray(
            length = values.size,
            data = data,
        ) {
            ownedPointers.forEach(disposeAbiPointer)
            scope.close()
        }
    } catch (error: Throwable) {
        ownedPointers.forEach(disposeAbiPointer)
        scope.close()
        throw error
    }
}

private fun <T> decodePointerArray(
    length: Int,
    data: RawAddress,
    fromAbiPointer: (RawAddress) -> T?,
    nullFromAbi: () -> T? = { null },
): List<T?>? {
    if (PlatformAbi.isNull(data)) {
        return null
    }
    return List(length) { index ->
        val pointer = PlatformAbi.readPointerAt(data, index)
        if (PlatformAbi.isNull(pointer)) {
            nullFromAbi()
        } else {
            fromAbiPointer(pointer)
        }
    }
}

private fun <T> copyManagedPointerArray(
    values: Array<out T?>?,
    destination: RawAddress,
    fromManagedPointer: (T?) -> RawAddress,
    disposeAbiPointer: (RawAddress) -> Unit,
) {
    if (values == null) {
        return
    }

    disposePointerArray(values.size, destination, disposeAbiPointer)
    val createdPointers = mutableListOf<RawAddress>()
    try {
        values.forEachIndexed { index, value ->
            val pointer = fromManagedPointer(value)
            createdPointers += pointer
            PlatformAbi.writePointerAt(destination, index, pointer)
        }
    } catch (error: Throwable) {
        createdPointers.forEach(disposeAbiPointer)
        throw error
    }
}

private fun disposePointerArray(
    length: Int,
    data: RawAddress,
    disposeAbiPointer: (RawAddress) -> Unit,
) {
    if (PlatformAbi.isNull(data)) {
        return
    }

    repeat(length) { index ->
        val pointer = PlatformAbi.readPointerAt(data, index)
        if (!PlatformAbi.isNull(pointer)) {
            disposeAbiPointer(pointer)
        }
    }
}

private fun RawAddress.elementSlice(index: Int, elementSize: Long): RawAddress =
    PlatformAbi.slice(this, index.toLong() * elementSize, elementSize)

private fun abiPointer(value: Any?): RawAddress =
    when (value) {
        null -> PlatformAbi.nullPointer
        is RawAddress -> value
        is RawComPtr -> value.asRawAddress()
        is ReferencedHString -> value.handle
        is HString -> value.handle
        is ComObjectReference -> value.pointer.asRawAddress()
        is WinRtObjectMarshaler -> value.abi
        else -> error("Expected pointer-backed ABI value, got '${abiTypeName(value)}'.")
    }

private fun abiTypeName(value: Any?): String = value?.let { it::class.typeDisplayName() } ?: "null"

private fun <T : Any> KClass<T>.castProjected(value: Any?): T {
    if (value != null && isInstance(value)) {
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
    throw IllegalStateException("Expected projected value assignable to ${typeDisplayName()}, got ${abiTypeName(value)}.")
}

private fun writeZeroValue(
    abiKind: NativeStructScalarKind,
    destination: RawAddress,
) {
    when (abiKind) {
        NativeStructScalarKind.ADDRESS -> PlatformAbi.writePointer(destination, PlatformAbi.nullPointer)
        NativeStructScalarKind.INT8 -> PlatformAbi.writeInt8(destination, 0)
        NativeStructScalarKind.INT16 -> PlatformAbi.writeInt16(destination, 0)
        NativeStructScalarKind.INT32 -> PlatformAbi.writeInt32(destination, 0)
        NativeStructScalarKind.INT64 -> PlatformAbi.writeInt64(destination, 0L)
        NativeStructScalarKind.DOUBLE -> PlatformAbi.writeDouble(destination, 0.0)
        NativeStructScalarKind.FLOAT32 -> PlatformAbi.writeFloat(destination, 0f)
        NativeStructScalarKind.CHAR16 -> PlatformAbi.writeChar16(destination, '\u0000')
        NativeStructScalarKind.GUID -> PlatformAbi.writeGuid(destination, ZERO_GUID)
    }
}

private val ZERO_GUID = Guid.fromLittleEndianBytes(ByteArray(Guid.BYTE_SIZE))
