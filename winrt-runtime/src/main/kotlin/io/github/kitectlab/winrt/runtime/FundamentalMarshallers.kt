package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder

object BooleanMarshaller {
    fun toAbi(value: Boolean): Byte = if (value) 1 else 0

    fun fromAbi(value: Byte): Boolean = value.toInt() != 0

    fun copyTo(value: Boolean, destination: MemorySegment) {
        destination.set(ValueLayout.JAVA_BYTE, 0, toAbi(value))
    }

    fun readFrom(source: MemorySegment): Boolean = fromAbi(source.get(ValueLayout.JAVA_BYTE, 0))
}

object CharMarshaller {
    fun toAbi(value: Char): Short = value.code.toShort()

    fun fromAbi(value: Short): Char = value.toInt().toChar()

    fun copyTo(value: Char, destination: MemorySegment) {
        destination.set(AbiLayouts.CHAR16, 0, value)
    }

    fun readFrom(source: MemorySegment): Char =
        source.get(AbiLayouts.CHAR16, 0)
}

object GuidMarshaller {
    fun copyTo(value: Guid, destination: MemorySegment) {
        value.writeTo(destination)
    }

    fun readFrom(source: MemorySegment): Guid {
        val buffer = source.asSlice(0, 16).asByteBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val bytes = ByteArray(Guid.BYTE_SIZE)
        buffer.get(bytes)
        return Guid.fromLittleEndianBytes(bytes)
    }
}

object StringMarshaller {
    fun createMarshaler(value: String?): ReferencedHString? =
        if (value.isNullOrEmpty()) {
            null
        } else {
            HString.createReference(value)
        }

    fun getAbi(value: ReferencedHString?): MemorySegment = value?.handle ?: MemorySegment.NULL

    fun getAbi(value: HString?): MemorySegment = value?.handle ?: MemorySegment.NULL

    fun disposeMarshaler(value: ReferencedHString?) {
        value?.close()
    }

    fun disposeAbi(handle: MemorySegment) {
        if (handle != MemorySegment.NULL) {
            WindowsRuntimePlatform.windowsDeleteString(handle)
        }
    }

    fun fromAbi(handle: MemorySegment): String =
        if (handle == MemorySegment.NULL) {
            ""
        } else {
            HString.fromHandle(handle, owner = false).toKString()
        }

    fun fromManaged(value: String?): HString? =
        value?.let(HString::create)

    fun readFrom(source: MemorySegment): String =
        fromAbi(source.get(ValueLayout.ADDRESS, 0))
}
