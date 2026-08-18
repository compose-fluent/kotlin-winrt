package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

@PublishedApi
internal actual class PlatformManagedComReferenceCounter actual constructor(
    initialValue: Long,
    initialStorage: RawAddress,
    initialStorageView: NativeMemoryView?,
    initialStorageOffsetBytes: Long,
) : AutoCloseable {
    private var arena: Arena? = if (initialStorageView == null && PlatformAbi.isNull(initialStorage)) {
        Arena.ofShared()
    } else {
        null
    }
    private var storage: MemorySegment? = (
        initialStorageView?.segment ?: if (PlatformAbi.isNull(initialStorage)) {
            requireNotNull(arena).allocate(ValueLayout.JAVA_LONG)
        } else {
            initialStorage.asMemorySegment().reinterpret(ValueLayout.JAVA_LONG.byteSize())
        }
    ).also { pointer ->
        pointer.set(ValueLayout.JAVA_LONG, initialStorageOffsetBytes, initialValue)
    }
    private val storageOffsetBytes = initialStorageOffsetBytes
    private val storagePointer =
        if (PlatformAbi.isNull(initialStorage)) requireNotNull(storage).asRawAddress() else initialStorage

    actual fun load(): Long = storage?.let { pointer ->
        longHandle.getVolatile(pointer, storageOffsetBytes) as Long
    } ?: 0L

    actual fun compareAndSet(expectedValue: Long, newValue: Long): Boolean =
        storage?.let { pointer ->
            longHandle.compareAndSet(pointer, storageOffsetBytes, expectedValue, newValue)
        } ?: false

    actual fun store(newValue: Long) {
        storage?.let { pointer ->
            longHandle.setVolatile(pointer, storageOffsetBytes, newValue)
        }
    }

    actual fun attach(
        objectMemory: RawAddress,
        objectMemoryView: NativeMemoryView?,
        objectMemoryOffsetBytes: Long,
    ) {
        if (storage == null) return
        if (objectMemoryView != null) {
            objectMemoryView.writePointer(
                objectMemoryOffsetBytes + managedComReferenceCounterSlot * Long.SIZE_BYTES.toLong(),
                storagePointer,
            )
        } else {
            PlatformAbi.writePointerAt(
                objectMemory,
                managedComReferenceCounterSlot,
                storagePointer,
            )
        }
    }

    actual fun detach(
        objectMemory: RawAddress,
        objectMemoryView: NativeMemoryView?,
        objectMemoryOffsetBytes: Long,
    ) {
        if (objectMemoryView != null) {
            objectMemoryView.writePointer(
                objectMemoryOffsetBytes + managedComReferenceCounterSlot * Long.SIZE_BYTES.toLong(),
                PlatformAbi.nullPointer,
            )
        } else {
            PlatformAbi.writePointerAt(
                objectMemory,
                managedComReferenceCounterSlot,
                PlatformAbi.nullPointer,
            )
        }
    }

    actual override fun close() {
        val owner = arena
        storage = null
        arena = null
        owner?.close()
    }

    private companion object {
        val longHandle = ValueLayout.JAVA_LONG.varHandle()
    }
}
