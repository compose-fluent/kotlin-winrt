package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemoryLayout
import java.lang.foreign.StructLayout
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder

internal object AbiLayouts {
    val CHAR16: ValueLayout.OfChar = ValueLayout.JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN)
    const val GUID_SIZE_BYTES: Long = 16
    val GUID: MemoryLayout = MemoryLayout.sequenceLayout(GUID_SIZE_BYTES, ValueLayout.JAVA_BYTE)
    val HSTRING_HEADER: StructLayout = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("reserved1"),
        ValueLayout.JAVA_INT.withName("reserved2"),
        ValueLayout.JAVA_INT.withName("reserved3"),
        ValueLayout.JAVA_INT.withName("reserved4"),
        ValueLayout.JAVA_INT.withName("reserved5"),
    )
    val IUNKNOWN_VFTBL: StructLayout = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("queryInterface"),
        ValueLayout.ADDRESS.withName("addRef"),
        ValueLayout.ADDRESS.withName("release"),
    )
}

internal object RawVtableCallSupport {
    fun entry(pointer: java.lang.foreign.MemorySegment, slot: Int): java.lang.foreign.MemorySegment {
        val objectMemory = pointer.reinterpret(ValueLayout.ADDRESS.byteSize())
        val vtable = objectMemory.get(ValueLayout.ADDRESS, 0)
        val requiredBytes = maxOf(AbiLayouts.IUNKNOWN_VFTBL.byteSize(), (slot + 1L) * ValueLayout.ADDRESS.byteSize())
        val vtableMemory = vtable.reinterpret(requiredBytes)
        return vtableMemory.getAtIndex(ValueLayout.ADDRESS, slot.toLong())
    }
}
