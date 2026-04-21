package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles

enum class WinRtAbiCategory {
    BLITTABLE,
    STRING,
    INTERFACE,
    INSPECTABLE,
    DELEGATE,
}

class WinRtAbiArray internal constructor(
    val length: Int,
    val data: MemorySegment,
    private val cleanup: (() -> Unit)? = null,
) : AutoCloseable {
    override fun close() {
        cleanup?.invoke()
    }
}

class Marshaler<T> internal constructor(
    val abiCategory: WinRtAbiCategory,
    private val arrayComponentClass: Class<*>,
    private val createMarshalerImpl: (T?) -> Any?,
    private val getAbiImpl: (Any?) -> Any?,
    private val fromAbiImpl: (Any?) -> T?,
    private val fromManagedImpl: (T?) -> Any?,
    private val copyAbiImpl: (Any?, MemorySegment) -> Unit,
    private val copyManagedImpl: (T?, MemorySegment) -> Unit,
    private val disposeMarshalerImpl: (Any?) -> Unit,
    private val disposeAbiImpl: (Any?) -> Unit,
    private val createMarshalerArrayImpl: (Array<out T?>?) -> WinRtAbiArray?,
    private val fromAbiArrayImpl: (Int, MemorySegment) -> Array<T?>?,
    private val fromManagedArrayImpl: (Array<out T?>?) -> WinRtAbiArray?,
    private val copyManagedArrayImpl: (Array<out T?>?, MemorySegment) -> Unit,
    private val disposeMarshalerArrayImpl: (WinRtAbiArray?) -> Unit,
    private val disposeAbiArrayImpl: (Int, MemorySegment) -> Unit,
) {
    fun createMarshaler(value: T?): Any? = createMarshalerImpl(value)

    fun getAbi(value: Any?): Any? = getAbiImpl(value)

    fun fromAbi(value: Any?): T? = fromAbiImpl(value)

    fun fromManaged(value: T?): Any? = fromManagedImpl(value)

    fun copyAbi(value: Any?, destination: MemorySegment) {
        copyAbiImpl(value, destination)
    }

    fun copyManaged(value: T?, destination: MemorySegment) {
        copyManagedImpl(value, destination)
    }

    fun disposeMarshaler(value: Any?) {
        disposeMarshalerImpl(value)
    }

    fun disposeAbi(value: Any?) {
        disposeAbiImpl(value)
    }

    fun createMarshalerArray(values: Array<out T?>?): WinRtAbiArray? = createMarshalerArrayImpl(values)

    fun fromAbiArray(length: Int, data: MemorySegment): Array<T?>? = fromAbiArrayImpl(length, data)

    fun fromManagedArray(values: Array<out T?>?): WinRtAbiArray? = fromManagedArrayImpl(values)

    fun copyManagedArray(values: Array<out T?>?, destination: MemorySegment) {
        copyManagedArrayImpl(values, destination)
    }

    fun disposeMarshalerArray(array: WinRtAbiArray?) {
        disposeMarshalerArrayImpl(array)
    }

    fun disposeAbiArray(length: Int, data: MemorySegment) {
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
            expectedType: Class<T>,
            projector: (Any?) -> T = { expectedType.cast(it) },
        ): Marshaler<T> = MarshalInterface.of(typeHandle, expectedType, projector)

        fun <T : Any> inspectable(
            expectedType: Class<T>,
            projector: (Any?) -> T = { expectedType.cast(it) },
        ): Marshaler<T> = MarshalInspectable.of(expectedType, projector)

        fun inspectableAny(): Marshaler<Any?> = MarshalInspectable.any()
    }
}

object MarshalBlittable {
    fun int8(): Marshaler<Byte> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = Byte::class.javaObjectType,
        layout = ValueLayout.JAVA_BYTE,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Byte -> abi
                is UByte -> abi.toByte()
                is MemorySegment -> abi.reinterpret(ValueLayout.JAVA_BYTE.byteSize()).get(ValueLayout.JAVA_BYTE, 0)
                else -> error("Expected ABI Int8, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = { value, destination ->
            destination.reinterpret(ValueLayout.JAVA_BYTE.byteSize()).set(ValueLayout.JAVA_BYTE, 0, value)
        },
    )

    fun uint8(): Marshaler<UByte> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = UByte::class.java,
        layout = ValueLayout.JAVA_BYTE,
        toAbi = { it.toByte() },
        fromAbi = { abi ->
            when (abi) {
                is Byte -> abi.toUByte()
                is UByte -> abi
                is MemorySegment -> abi.reinterpret(ValueLayout.JAVA_BYTE.byteSize()).get(ValueLayout.JAVA_BYTE, 0).toUByte()
                else -> error("Expected ABI UInt8, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = { value, destination ->
            destination.reinterpret(ValueLayout.JAVA_BYTE.byteSize()).set(ValueLayout.JAVA_BYTE, 0, value.toByte())
        },
    )

    fun int16(): Marshaler<Short> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = Short::class.javaObjectType,
        layout = ValueLayout.JAVA_SHORT,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Short -> abi
                is UShort -> abi.toShort()
                is MemorySegment -> abi.reinterpret(ValueLayout.JAVA_SHORT.byteSize()).get(ValueLayout.JAVA_SHORT, 0)
                else -> error("Expected ABI Int16, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = { value, destination ->
            destination.reinterpret(ValueLayout.JAVA_SHORT.byteSize()).set(ValueLayout.JAVA_SHORT, 0, value)
        },
    )

    fun uint16(): Marshaler<UShort> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = UShort::class.java,
        layout = ValueLayout.JAVA_SHORT,
        toAbi = { it.toShort() },
        fromAbi = { abi ->
            when (abi) {
                is Short -> abi.toUShort()
                is UShort -> abi
                is MemorySegment -> abi.reinterpret(ValueLayout.JAVA_SHORT.byteSize()).get(ValueLayout.JAVA_SHORT, 0).toUShort()
                else -> error("Expected ABI UInt16, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = { value, destination ->
            destination.reinterpret(ValueLayout.JAVA_SHORT.byteSize()).set(ValueLayout.JAVA_SHORT, 0, value.toShort())
        },
    )

    fun boolean(): Marshaler<Boolean> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = Boolean::class.javaObjectType,
        layout = ValueLayout.JAVA_BYTE,
        toAbi = BooleanMarshaller::toAbi,
        fromAbi = { abi ->
            when (abi) {
                is Byte -> BooleanMarshaller.fromAbi(abi)
                is MemorySegment -> BooleanMarshaller.readFrom(abi)
                else -> error("Expected ABI Boolean byte, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = BooleanMarshaller::copyTo,
    )

    fun char16(): Marshaler<Char> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = Char::class.javaObjectType,
        layout = NativeLayoutsJvmCompat.CHAR16,
        toAbi = CharMarshaller::toAbi,
        fromAbi = { abi ->
            when (abi) {
                is Short -> CharMarshaller.fromAbi(abi)
                is MemorySegment -> CharMarshaller.readFrom(abi)
                else -> error("Expected ABI char16, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = { value, destination -> CharMarshaller.copyTo(value, destination) },
    )

    fun int32(): Marshaler<Int> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = Int::class.javaObjectType,
        layout = ValueLayout.JAVA_INT,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Int -> abi
                is UInt -> abi.toInt()
                is MemorySegment -> abi.reinterpret(ValueLayout.JAVA_INT.byteSize()).get(ValueLayout.JAVA_INT, 0)
                else -> error("Expected ABI Int32, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = { value, destination ->
            destination.reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, value)
        },
    )

    fun uint32(): Marshaler<UInt> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = UInt::class.java,
        layout = ValueLayout.JAVA_INT,
        toAbi = { it.toInt() },
        fromAbi = { abi ->
            when (abi) {
                is Int -> abi.toUInt()
                is UInt -> abi
                is MemorySegment -> abi.reinterpret(ValueLayout.JAVA_INT.byteSize()).get(ValueLayout.JAVA_INT, 0).toUInt()
                else -> error("Expected ABI UInt32, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = { value, destination ->
            destination.reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, value.toInt())
        },
    )

    fun int64(): Marshaler<Long> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = Long::class.javaObjectType,
        layout = ValueLayout.JAVA_LONG,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Long -> abi
                is ULong -> abi.toLong()
                is MemorySegment -> abi.reinterpret(ValueLayout.JAVA_LONG.byteSize()).get(ValueLayout.JAVA_LONG, 0)
                else -> error("Expected ABI Int64, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = { value, destination ->
            destination.reinterpret(ValueLayout.JAVA_LONG.byteSize()).set(ValueLayout.JAVA_LONG, 0, value)
        },
    )

    fun uint64(): Marshaler<ULong> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = ULong::class.java,
        layout = ValueLayout.JAVA_LONG,
        toAbi = { it.toLong() },
        fromAbi = { abi ->
            when (abi) {
                is Long -> abi.toULong()
                is ULong -> abi
                is MemorySegment -> abi.reinterpret(ValueLayout.JAVA_LONG.byteSize()).get(ValueLayout.JAVA_LONG, 0).toULong()
                else -> error("Expected ABI UInt64, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = { value, destination ->
            destination.reinterpret(ValueLayout.JAVA_LONG.byteSize()).set(ValueLayout.JAVA_LONG, 0, value.toLong())
        },
    )

    fun float32(): Marshaler<Float> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = Float::class.javaObjectType,
        layout = ValueLayout.JAVA_FLOAT,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Float -> abi
                is MemorySegment -> abi.reinterpret(ValueLayout.JAVA_FLOAT.byteSize()).get(ValueLayout.JAVA_FLOAT, 0)
                else -> error("Expected ABI Float32, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = { value, destination ->
            destination.reinterpret(ValueLayout.JAVA_FLOAT.byteSize()).set(ValueLayout.JAVA_FLOAT, 0, value)
        },
    )

    fun float64(): Marshaler<Double> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = Double::class.javaObjectType,
        layout = ValueLayout.JAVA_DOUBLE,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Double -> abi
                is MemorySegment -> abi.reinterpret(ValueLayout.JAVA_DOUBLE.byteSize()).get(ValueLayout.JAVA_DOUBLE, 0)
                else -> error("Expected ABI Float64, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = { value, destination ->
            destination.reinterpret(ValueLayout.JAVA_DOUBLE.byteSize()).set(ValueLayout.JAVA_DOUBLE, 0, value)
        },
    )

    fun guid(): Marshaler<Guid> = scalar(
        category = WinRtAbiCategory.BLITTABLE,
        componentClass = Guid::class.java,
        layout = NativeLayoutsJvmCompat.GUID,
        toAbi = { it },
        fromAbi = { abi ->
            when (abi) {
                is Guid -> abi
                is MemorySegment -> GuidMarshaller.readFrom(abi)
                else -> error("Expected ABI GUID, got '${abi?.javaClass?.name}'.")
            }
        },
        copyManaged = GuidMarshaller::copyTo,
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T, ABI : Any> scalar(
        category: WinRtAbiCategory,
        componentClass: Class<*>,
        layout: MemoryLayout,
        toAbi: (T) -> ABI,
        fromAbi: (Any?) -> T,
        copyManaged: (T, MemorySegment) -> Unit,
    ): Marshaler<T> =
        Marshaler(
            abiCategory = category,
            arrayComponentClass = componentClass,
            createMarshalerImpl = { it },
            getAbiImpl = { value ->
                when (value) {
                    null -> null
                    is MemorySegment -> fromAbi(value).let(toAbi)
                    else -> toAbi(value as T)
                }
            },
            fromAbiImpl = { abi ->
                if (abi == null) {
                    null
                } else {
                    fromAbi(abi)
                }
            },
            fromManagedImpl = { value -> value?.let(toAbi) },
            copyAbiImpl = { abi, destination ->
                val typed = when (abi) {
                    null -> null
                    is MemorySegment -> fromAbi(abi)
                    else -> fromAbi(abi)
                }
                if (typed != null) {
                    copyManaged(typed, destination)
                } else {
                    destination.fillZero(layout.byteSize())
                }
            },
            copyManagedImpl = { value, destination ->
                if (value == null) {
                    destination.fillZero(layout.byteSize())
                } else {
                    copyManaged(value, destination)
                }
            },
            disposeMarshalerImpl = {},
            disposeAbiImpl = {},
            createMarshalerArrayImpl = { values ->
                createBlittableArray(values, layout, copyManaged)
            },
            fromAbiArrayImpl = { length, data ->
                decodeBlittableArray(length, data, layout, componentClass, fromAbi)
            },
            fromManagedArrayImpl = { values ->
                createBlittableArray(values, layout, copyManaged)
            },
            copyManagedArrayImpl = { values, destination ->
                copyBlittableArray(values, destination, layout, copyManaged)
            },
            disposeMarshalerArrayImpl = { array -> array?.close() },
            disposeAbiArrayImpl = { _, _ -> },
        )
}

object MarshalString {
    fun marshaler(): Marshaler<String> =
        pointerMarshaler(
            category = WinRtAbiCategory.STRING,
            componentClass = String::class.java,
            nullFromAbi = { StringMarshaller.fromAbi(MemorySegment.NULL) },
            createMarshaler = StringMarshaller::createMarshaler,
            getAbiPointer = { value ->
                when (value) {
                    null -> MemorySegment.NULL
                    is ReferencedHString -> StringMarshaller.getAbi(value)
                    is HString -> StringMarshaller.getAbi(value)
                    is MemorySegment -> value
                    else -> error("Expected HSTRING marshaler, got '${value.javaClass.name}'.")
                }
            },
            fromAbiPointer = { pointer -> StringMarshaller.fromAbi(pointer) },
            fromManagedPointer = { value -> StringMarshaller.fromManaged(value)?.handle?.asMemorySegment() ?: MemorySegment.NULL },
            disposeMarshaler = { value ->
                when (value) {
                    null -> Unit
                    is ReferencedHString -> StringMarshaller.disposeMarshaler(value)
                    is HString -> value.close()
                    else -> error("Expected HSTRING marshaler, got '${value.javaClass.name}'.")
                }
            },
            disposeAbiPointer = StringMarshaller::disposeAbi,
        )
}

object MarshalInterface {
    fun <T : Any> of(
        typeHandle: WinRtTypeHandle,
        expectedType: Class<T>,
        projector: (Any?) -> T = { expectedType.cast(it) },
    ): Marshaler<T> =
        pointerMarshaler(
            category = WinRtAbiCategory.INTERFACE,
            componentClass = expectedType,
            createMarshaler = { value ->
                when (value) {
                    null -> null
                    else -> ComWrappersSupport.tryUnwrapObject(value, typeHandle)
                        ?: ComWrappersSupport.createCCWForObject(value, typeHandle.interfaceId)
                }
            },
            getAbiPointer = { value ->
                when (value) {
                    null -> MemorySegment.NULL
                    is ComObjectReference -> value.pointer.asMemorySegment()
                    is MemorySegment -> value
                    else -> error("Expected COM object reference marshaler, got '${value.javaClass.name}'.")
                }
            },
            fromAbiPointer = { pointer ->
                ComWrappersSupport.findObject(pointer.asNativePointer(), expectedType)
                    ?: projector(ComWrappersSupport.createRcwForComObject(pointer.asNativePointer(), typeHandle))
            },
            fromManagedPointer = { value ->
                when (value) {
                    null -> MemorySegment.NULL
                    else -> ComWrappersSupport.createCCWForObject(value, typeHandle.interfaceId).useAndGetRef().asMemorySegment()
                }
            },
            disposeMarshaler = { value ->
                (value as? ComObjectReference)?.close()
            },
            disposeAbiPointer = { pointer ->
                if (pointer != MemorySegment.NULL) {
                    IUnknownReference(pointer, typeHandle.interfaceId).close()
                }
            },
        )
}

object MarshalInspectable {
    fun <T : Any> of(
        expectedType: Class<T>,
        projector: (Any?) -> T = { expectedType.cast(it) },
    ): Marshaler<T> =
        pointerMarshaler(
            category = WinRtAbiCategory.INSPECTABLE,
            componentClass = expectedType,
            createMarshaler = { value ->
                when (value) {
                    null -> null
                    else -> ComWrappersSupport.tryUnwrapObject(value)
                        ?: ComWrappersSupport.createCCWForObject(value, IID.IInspectable)
                }
            },
            getAbiPointer = { value ->
                when (value) {
                    null -> MemorySegment.NULL
                    is ComObjectReference -> value.pointer.asMemorySegment()
                    is MemorySegment -> value
                    else -> error("Expected inspectable marshaler, got '${value.javaClass.name}'.")
                }
            },
            fromAbiPointer = { pointer ->
                ComWrappersSupport.findObject(pointer.asNativePointer(), expectedType)
                    ?: projector(ComWrappersSupport.createRcwForComObject(pointer.asNativePointer()))
            },
            fromManagedPointer = { value ->
                when (value) {
                    null -> MemorySegment.NULL
                    else -> ComWrappersSupport.createCCWForObject(value, IID.IInspectable).useAndGetRef().asMemorySegment()
                }
            },
            disposeMarshaler = { value ->
                (value as? ComObjectReference)?.close()
            },
            disposeAbiPointer = { pointer ->
                if (pointer != MemorySegment.NULL) {
                    IUnknownReference(pointer, IID.IInspectable).close()
                }
            },
        )

    fun any(): Marshaler<Any?> =
        pointerMarshaler(
            category = WinRtAbiCategory.INSPECTABLE,
            componentClass = Any::class.java,
            createMarshaler = { value ->
                when (value) {
                    null -> null
                    else -> ComWrappersSupport.tryUnwrapObject(value)
                        ?: ComWrappersSupport.createCCWForObject(value, IID.IInspectable)
                }
            },
            getAbiPointer = { value ->
                when (value) {
                    null -> MemorySegment.NULL
                    is ComObjectReference -> value.pointer.asMemorySegment()
                    is MemorySegment -> value
                    else -> error("Expected inspectable marshaler, got '${value.javaClass.name}'.")
                }
            },
            fromAbiPointer = { pointer ->
                WinRtInspectableComObject.findManagedValue(pointer.asNativePointer())
                    ?: ComWrappersSupport.createRcwForComObject(pointer.asNativePointer())
            },
            fromManagedPointer = { value ->
                when (value) {
                    null -> MemorySegment.NULL
                    else -> ComWrappersSupport.createCCWForObject(value, IID.IInspectable).useAndGetRef().asMemorySegment()
                }
            },
            disposeMarshaler = { value ->
                (value as? ComObjectReference)?.close()
            },
            disposeAbiPointer = { pointer ->
                if (pointer != MemorySegment.NULL) {
                    IUnknownReference(pointer, IID.IInspectable).close()
                }
            },
        )
}

object MarshalDelegate {
    fun createMarshaler(value: WinRtDelegateReference?): WinRtDelegateReference? =
        value?.let { WinRtDelegateReference.fromAbi(it.getRefPointer(), it.descriptor) }

    fun getAbi(value: WinRtDelegateReference?): MemorySegment = value?.pointer?.asMemorySegment() ?: MemorySegment.NULL

    fun fromAbi(pointer: MemorySegment, descriptor: WinRtDelegateDescriptor): WinRtDelegateReference? =
        WinRtDelegateReference.fromAbi(pointer.asNativePointer(), descriptor)

    fun fromManaged(value: WinRtDelegateHandle?): MemorySegment =
        value?.createReference()?.useAndGetRef()?.asMemorySegment() ?: MemorySegment.NULL

    fun disposeMarshaler(value: WinRtDelegateReference?) {
        value?.close()
    }

    fun disposeAbi(pointer: MemorySegment, descriptor: WinRtDelegateDescriptor) {
        if (pointer == MemorySegment.NULL) {
            return
        }
        val nativePointer = pointer.asNativePointer()
        if (!WinRtDelegateComObject.releaseLocalReference(nativePointer)) {
            WinRtDelegateReference.fromAbi(nativePointer, descriptor)?.close()
        }
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
        data: MemorySegment,
        descriptor: WinRtDelegateDescriptor,
    ): Array<WinRtDelegateReference?>? =
        decodePointerArray(
            length = length,
            data = data,
            componentClass = WinRtDelegateReference::class.java,
            fromAbiPointer = { pointer -> fromAbi(pointer, descriptor) },
        )

    fun disposeAbiArray(
        length: Int,
        data: MemorySegment,
        descriptor: WinRtDelegateDescriptor,
    ) {
        disposePointerArray(length, data) { pointer ->
            disposeAbi(pointer, descriptor)
        }
    }
}

private fun <T> pointerMarshaler(
    category: WinRtAbiCategory,
    componentClass: Class<*>,
    nullFromAbi: () -> T? = { null },
    createMarshaler: (T?) -> Any?,
    getAbiPointer: (Any?) -> MemorySegment,
    fromAbiPointer: (MemorySegment) -> T?,
    fromManagedPointer: (T?) -> MemorySegment,
    disposeMarshaler: (Any?) -> Unit,
    disposeAbiPointer: (MemorySegment) -> Unit,
): Marshaler<T> =
    Marshaler(
        abiCategory = category,
        arrayComponentClass = componentClass,
        createMarshalerImpl = createMarshaler,
        getAbiImpl = { value -> getAbiPointer(value) },
        fromAbiImpl = { abi ->
            val pointer = abiPointer(abi)
            if (pointer == MemorySegment.NULL) {
                nullFromAbi()
            } else {
                fromAbiPointer(pointer)
            }
        },
        fromManagedImpl = { value -> fromManagedPointer(value) },
        copyAbiImpl = { abi, destination ->
            destination.writePointer(abiPointer(abi))
        },
        copyManagedImpl = { value, destination ->
            destination.writePointer(fromManagedPointer(value))
        },
        disposeMarshalerImpl = disposeMarshaler,
        disposeAbiImpl = { abi -> disposeAbiPointer(abiPointer(abi)) },
        createMarshalerArrayImpl = { values ->
            createPointerArrayFromMarshallers(values, createMarshaler, getAbiPointer, disposeMarshaler)
        },
        fromAbiArrayImpl = { length, data ->
            decodePointerArray(length, data, componentClass, fromAbiPointer, nullFromAbi)
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
    layout: MemoryLayout,
    copyManaged: (T, MemorySegment) -> Unit,
): WinRtAbiArray? {
    if (values == null) {
        return null
    }

    val arena = Arena.ofConfined()
    return try {
        val data = arena.allocate(layout.byteSize() * values.size.toLong(), layout.byteAlignment())
        values.forEachIndexed { index, value ->
            val slice = data.elementSlice(index, layout.byteSize())
            if (value == null) {
                slice.fillZero(layout.byteSize())
            } else {
                copyManaged(value, slice)
            }
        }
        WinRtAbiArray(values.size, data, arena::close)
    } catch (error: Throwable) {
        arena.close()
        throw error
    }
}

private fun <T> decodeBlittableArray(
    length: Int,
    data: MemorySegment,
    layout: MemoryLayout,
    componentClass: Class<*>,
    fromAbi: (Any?) -> T,
): Array<T?>? {
    if (data == MemorySegment.NULL) {
        return null
    }
    val values = newTypedArray<T>(length, componentClass)
    repeat(length) { index ->
        values[index] = fromAbi(data.elementSlice(index, layout.byteSize()))
    }
    return values
}

private fun <T> copyBlittableArray(
    values: Array<out T?>?,
    destination: MemorySegment,
    layout: MemoryLayout,
    copyManaged: (T, MemorySegment) -> Unit,
) {
    if (values == null) {
        return
    }
    values.forEachIndexed { index, value ->
        val slice = destination.elementSlice(index, layout.byteSize())
        if (value == null) {
            slice.fillZero(layout.byteSize())
        } else {
            copyManaged(value, slice)
        }
    }
}

private fun <T> createPointerArrayFromMarshallers(
    values: Array<out T?>?,
    createMarshaler: (T?) -> Any?,
    getAbiPointer: (Any?) -> MemorySegment,
    disposeMarshaler: (Any?) -> Unit,
): WinRtAbiArray? {
    if (values == null) {
        return null
    }

    val arena = Arena.ofConfined()
    val marshalers = ArrayList<Any?>(values.size)
    return try {
        val data = arena.allocate(MemoryLayout.sequenceLayout(values.size.toLong(), ValueLayout.ADDRESS))
        values.forEachIndexed { index, value ->
            val marshaler = createMarshaler(value)
            marshalers += marshaler
            data.setAtIndex(ValueLayout.ADDRESS, index.toLong(), getAbiPointer(marshaler))
        }
        WinRtAbiArray(
            length = values.size,
            data = data,
        ) {
            marshalers.forEach(disposeMarshaler)
            arena.close()
        }
    } catch (error: Throwable) {
        marshalers.forEach(disposeMarshaler)
        arena.close()
        throw error
    }
}

private fun <T> createPointerArrayFromAbiValues(
    values: Array<out T?>?,
    fromManagedPointer: (T?) -> MemorySegment,
    disposeAbiPointer: (MemorySegment) -> Unit,
): WinRtAbiArray? {
    if (values == null) {
        return null
    }

    val arena = Arena.ofConfined()
    val ownedPointers = ArrayList<MemorySegment>(values.size)
    return try {
        val data = arena.allocate(MemoryLayout.sequenceLayout(values.size.toLong(), ValueLayout.ADDRESS))
        values.forEachIndexed { index, value ->
            val pointer = fromManagedPointer(value)
            ownedPointers += pointer
            data.setAtIndex(ValueLayout.ADDRESS, index.toLong(), pointer)
        }
        WinRtAbiArray(
            length = values.size,
            data = data,
        ) {
            ownedPointers.forEach(disposeAbiPointer)
            arena.close()
        }
    } catch (error: Throwable) {
        ownedPointers.forEach(disposeAbiPointer)
        arena.close()
        throw error
    }
}

private fun <T> decodePointerArray(
    length: Int,
    data: MemorySegment,
    componentClass: Class<*>,
    fromAbiPointer: (MemorySegment) -> T?,
    nullFromAbi: () -> T? = { null },
): Array<T?>? {
    if (data == MemorySegment.NULL) {
        return null
    }
    val values = newTypedArray<T>(length, componentClass)
    repeat(length) { index ->
        val pointer = data.getAtIndex(ValueLayout.ADDRESS, index.toLong())
        values[index] = if (pointer == MemorySegment.NULL) {
            nullFromAbi()
        } else {
            fromAbiPointer(pointer)
        }
    }
    return values
}

@Suppress("UNCHECKED_CAST")
private fun <T> newTypedArray(length: Int, componentClass: Class<*>): Array<T?> =
    MethodHandles.arrayConstructor(componentClass.arrayType()).invoke(length) as Array<T?>

private fun <T> copyManagedPointerArray(
    values: Array<out T?>?,
    destination: MemorySegment,
    fromManagedPointer: (T?) -> MemorySegment,
    disposeAbiPointer: (MemorySegment) -> Unit,
) {
    if (values == null) {
        return
    }

    val createdPointers = ArrayList<MemorySegment>(values.size)
    try {
        values.forEachIndexed { index, value ->
            val pointer = fromManagedPointer(value)
            createdPointers += pointer
            destination.setAtIndex(ValueLayout.ADDRESS, index.toLong(), pointer)
        }
    } catch (error: Throwable) {
        createdPointers.forEach(disposeAbiPointer)
        throw error
    }
}

private fun disposePointerArray(
    length: Int,
    data: MemorySegment,
    disposeAbiPointer: (MemorySegment) -> Unit,
) {
    if (data == MemorySegment.NULL) {
        return
    }

    repeat(length) { index ->
        val pointer = data.getAtIndex(ValueLayout.ADDRESS, index.toLong())
        if (pointer != MemorySegment.NULL) {
            disposeAbiPointer(pointer)
        }
    }
}

private fun MemorySegment.writePointer(pointer: MemorySegment) {
    reinterpret(ValueLayout.ADDRESS.byteSize()).set(ValueLayout.ADDRESS, 0, pointer)
}

private fun MemorySegment.elementSlice(index: Int, elementSize: Long): MemorySegment =
    asSlice(index.toLong() * elementSize, elementSize)

private fun MemorySegment.fillZero(size: Long) {
    asSlice(0, size).fill(0)
}

private fun abiPointer(value: Any?): MemorySegment =
    when (value) {
        null -> MemorySegment.NULL
        is MemorySegment -> value
        is ReferencedHString -> value.handle.asMemorySegment()
        is HString -> value.handle.asMemorySegment()
        is ComObjectReference -> value.pointer.asMemorySegment()
        else -> error("Expected pointer-backed ABI value, got '${value.javaClass.name}'.")
    }
