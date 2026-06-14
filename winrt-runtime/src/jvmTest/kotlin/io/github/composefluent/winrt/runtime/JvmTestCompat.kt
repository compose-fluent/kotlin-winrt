package io.github.composefluent.winrt.runtime

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal typealias NativePointer = RawAddress

internal object NativeValueLayout {
    val JAVA_INT: ComAbiValueKind = ComAbiValueKind.Int32
    val JAVA_LONG: ComAbiValueKind = ComAbiValueKind.Int64
    val ADDRESS: ComAbiValueKind = ComAbiValueKind.Pointer
}

internal object AbiFunctionDescriptor {
    fun of(
        returnLayout: ComAbiValueKind,
        vararg argumentLayouts: ComAbiValueKind,
    ): ComMethodSignature = ComMethodSignature.of(*argumentLayouts)
}

internal fun MemorySegment.asNativePointer(): RawAddress = asRawAddress()

internal fun BooleanMarshaller.copyTo(value: Boolean, destination: MemorySegment) {
    copyTo(value, destination.asRawAddress())
}

internal fun BooleanMarshaller.readFrom(source: MemorySegment): Boolean = readFrom(source.asRawAddress())

internal fun CharMarshaller.copyTo(value: Char, destination: MemorySegment) {
    copyTo(value, destination.asRawAddress())
}

internal fun CharMarshaller.readFrom(source: MemorySegment): Char = readFrom(source.asRawAddress())

internal fun GuidMarshaller.copyTo(value: Guid, destination: MemorySegment) {
    copyTo(value, destination.asRawAddress())
}

internal fun GuidMarshaller.readFrom(source: MemorySegment): Guid = readFrom(source.asRawAddress())

internal fun ComObjectReference.invokeAbi(
    slot: Int,
    descriptor: ComMethodSignature,
    vararg args: Any,
): Int {
    val rawArgs = args.map {
        when (it) {
            is RawAddress -> it
            is RawComPtr -> it.asRawAddress()
            is MemorySegment -> it.asRawAddress()
            else -> error("Unsupported test ABI argument: ${it::class.qualifiedName}")
        }
    }
    return when (rawArgs.size) {
        0 -> ComVtableInvoker.invoke(pointer, slot)
        1 -> ComVtableInvoker.invokeArgs(pointer, slot, rawArgs[0])
        2 -> ComVtableInvoker.invokeArgs(pointer, slot, rawArgs[0], rawArgs[1])
        3 -> ComVtableInvoker.invokeArgs(pointer, slot, rawArgs[0], rawArgs[1], rawArgs[2])
        4 -> ComVtableInvoker.invokeArgs(pointer, slot, rawArgs[0], rawArgs[1], rawArgs[2], rawArgs[3])
        5 -> ComVtableInvoker.invokeGeneric(pointer, slot, descriptor, rawArgs.map(RawAddress::value).toLongArray())
        else -> error("Unsupported test ABI argument count: ${rawArgs.size}")
    }
}

internal fun ComObjectReference.invokeAbi(
    slot: Int,
    descriptor: java.lang.foreign.FunctionDescriptor,
    vararg args: Any,
): Int =
    invokeAbi(
        slot = slot,
        descriptor = ComMethodSignature.of(*Array(args.size) { ComAbiValueKind.Pointer }),
        args = *args,
    )

internal fun ComWrappersSupport.createRcwForComObject(
    pointer: RawComPtr,
    staticallyDeterminedType: WinRtTypeHandle? = null,
): Any? = createRcwForComObject(pointer.asRawAddress(), staticallyDeterminedType)

internal fun <T : Any> ComWrappersSupport.findObject(
    pointer: RawComPtr,
    expectedType: kotlin.reflect.KClass<T>,
): T? = findObject(pointer.asRawAddress(), expectedType)

internal fun ComWrappersSupport.getInspectableInfo(pointer: RawComPtr): WinRtInspectableInfo? =
    getInspectableInfo(pointer.asRawAddress())

internal fun WinRtBindableObjectMarshaller.fromBorrowedAbi(pointer: RawComPtr): Any? =
    fromBorrowedAbi(pointer.asRawAddress())

internal fun WinRtBindableObjectMarshaller.fromOwnedAbi(pointer: RawComPtr): Any? =
    fromOwnedAbi(pointer.asRawAddress())

internal fun <T> WinRtIterableProjection.fromAbi(
    pointer: RawComPtr,
    adapter: WinRtReferenceValueAdapter<T>,
): WinRtIterableProjection.FromAbiHelper<T>? =
    fromAbi(pointer.asRawAddress(), adapter)

internal object NativeLayoutsJvmCompat {
    const val GUID_SIZE_BYTES: Long = Guid.BYTE_SIZE.toLong()
    const val HSTRING_HEADER_SIZE_BYTES: Long = 24L
    const val IUNKNOWN_VFTBL_SIZE_BYTES: Long = 24L

    val GUID: MemoryLayout = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_SHORT,
        ValueLayout.JAVA_SHORT,
        MemoryLayout.sequenceLayout(8, ValueLayout.JAVA_BYTE),
    )
}

internal object StringMarshaller {
    fun createMarshaler(value: String?): ReferencedHString? = NativeStringMarshaller.createMarshaler(value)

    fun getAbi(value: ReferencedHString?): MemorySegment = NativeStringMarshaller.getAbi(value).asMemorySegment()

    fun getAbi(value: HString?): MemorySegment = NativeStringMarshaller.getAbi(value).asMemorySegment()

    fun disposeMarshaler(value: ReferencedHString?) {
        NativeStringMarshaller.disposeMarshaler(value)
    }

    fun fromAbi(handle: RawAddress): String = NativeStringMarshaller.fromAbi(handle)

    fun fromAbi(handle: MemorySegment): String = NativeStringMarshaller.fromAbi(handle.asRawAddress())

    fun fromManaged(value: String?): HString? = NativeStringMarshaller.fromManaged(value)

    fun readFrom(source: MemorySegment): String =
        fromAbi(source.get(ValueLayout.ADDRESS, 0).asRawAddress())
}

internal object RawVtableCallJvmCompat {
    fun entry(instance: MemorySegment, slot: Int): MemorySegment {
        val vtable = instance.get(ValueLayout.ADDRESS, 0)
        return vtable.reinterpret((slot + 1).toLong() * ValueLayout.ADDRESS.byteSize())
            .getAtIndex(ValueLayout.ADDRESS, slot.toLong())
    }
}
