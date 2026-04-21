package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder

internal object NativeLayoutsJvmCompat {
    val CHAR16: ValueLayout.OfChar = ValueLayout.JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN)
    const val GUID_SIZE_BYTES: Long = Guid.BYTE_SIZE.toLong()
    val GUID: MemoryLayout = MemoryLayout.sequenceLayout(GUID_SIZE_BYTES, ValueLayout.JAVA_BYTE)
    val TYPE_NAME: MemoryLayout = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("name"),
        ValueLayout.JAVA_INT.withName("kind"),
    )
    const val HSTRING_HEADER_SIZE_BYTES: Long = 24
    const val IUNKNOWN_VFTBL_SIZE_BYTES: Long = 24
}

internal object RawVtableCallJvmCompat {
    fun entry(pointer: MemorySegment, slot: Int): MemorySegment {
        val objectMemory = pointer.reinterpret(ValueLayout.ADDRESS.byteSize())
        val vtable = objectMemory.get(ValueLayout.ADDRESS, 0)
        val requiredBytes = maxOf(NativeLayoutsJvmCompat.IUNKNOWN_VFTBL_SIZE_BYTES, (slot + 1L) * ValueLayout.ADDRESS.byteSize())
        val vtableMemory = vtable.reinterpret(requiredBytes)
        return vtableMemory.getAtIndex(ValueLayout.ADDRESS, slot.toLong())
    }
}
