package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal fun BooleanMarshaller.copyTo(value: Boolean, destination: MemorySegment) {
    copyTo(value, destination.asNativePointer())
}

internal fun BooleanMarshaller.readFrom(source: MemorySegment): Boolean = readFrom(source.asNativePointer())

internal fun CharMarshaller.copyTo(value: Char, destination: MemorySegment) {
    copyTo(value, destination.asNativePointer())
}

internal fun CharMarshaller.readFrom(source: MemorySegment): Char = readFrom(source.asNativePointer())

internal fun GuidMarshaller.copyTo(value: Guid, destination: MemorySegment) {
    copyTo(value, destination.asNativePointer())
}

internal fun GuidMarshaller.readFrom(source: MemorySegment): Guid = readFrom(source.asNativePointer())

object StringMarshaller {
    fun createMarshaler(value: String?): ReferencedHString? =
        NativeStringMarshaller.createMarshaler(value)

    fun getAbi(value: ReferencedHString?): MemorySegment =
        NativeStringMarshaller.getAbi(value).asMemorySegment()

    fun getAbi(value: HString?): MemorySegment =
        NativeStringMarshaller.getAbi(value).asMemorySegment()

    fun disposeMarshaler(value: ReferencedHString?) {
        NativeStringMarshaller.disposeMarshaler(value)
    }

    fun disposeAbi(handle: MemorySegment) {
        NativeStringMarshaller.disposeAbi(handle.asNativePointer())
    }

    fun fromAbi(handle: MemorySegment): String =
        NativeStringMarshaller.fromAbi(handle.asNativePointer())

    fun fromManaged(value: String?): HString? =
        NativeStringMarshaller.fromManaged(value)

    fun readFrom(source: MemorySegment): String =
        fromAbi(source.get(ValueLayout.ADDRESS, 0))
}
