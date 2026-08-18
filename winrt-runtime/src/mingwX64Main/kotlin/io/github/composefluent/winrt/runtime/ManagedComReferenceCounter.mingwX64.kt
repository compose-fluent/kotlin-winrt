@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
import platform.windows._InterlockedCompareExchange64

@PublishedApi
internal actual class PlatformManagedComReferenceCounter actual constructor(
    initialValue: Long,
    initialStorage: RawAddress,
    @Suppress("UNUSED_PARAMETER") initialStorageView: NativeMemoryView?,
    @Suppress("UNUSED_PARAMETER") initialStorageOffsetBytes: Long,
) : AutoCloseable {
    private val ownsStorage = PlatformAbi.isNull(initialStorage)
    private var storage: CPointer<LongVar>? = if (ownsStorage) {
        nativeHeap.alloc<LongVar>().ptr
    } else {
        initialStorage.value.toCPointer<LongVar>()
    }.also { pointer ->
        checkNotNull(pointer)
        pointer.pointed.value = initialValue
    }

    actual fun load(): Long = storage?.atomicLoad() ?: 0L

    actual fun compareAndSet(expectedValue: Long, newValue: Long): Boolean =
        storage?.let { pointer ->
            _InterlockedCompareExchange64(pointer, newValue, expectedValue) == expectedValue
        } ?: false

    actual fun store(newValue: Long) {
        storage?.let { pointer ->
            // The transition owner is the only writer while the sentinel is published.
            // Win64 aligned 64-bit stores are atomic, and x64 store ordering publishes the
            // preceding volatile root update before native fast paths observe this count.
            pointer.pointed.value = newValue
        }
    }

    actual fun attach(
        objectMemory: RawAddress,
        objectMemoryView: NativeMemoryView?,
        objectMemoryOffsetBytes: Long,
    ) {
        val pointer = storage ?: return
        if (objectMemoryView != null) {
            objectMemoryView.writePointer(
                objectMemoryOffsetBytes + managedComReferenceCounterSlot * Long.SIZE_BYTES.toLong(),
                RawAddress(pointer.rawValue.toLong()),
            )
        } else {
            PlatformAbi.writePointerAt(
                objectMemory,
                managedComReferenceCounterSlot,
                RawAddress(pointer.rawValue.toLong()),
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
        val pointer = storage ?: return
        storage = null
        if (ownsStorage) {
            nativeHeap.free(pointer.rawValue)
        }
    }
}

// The succeeding CAS is the synchronization point; an aligned observation
// avoids a second locked RMW on every steady-state AddRef and Release.
private fun CPointer<LongVar>.atomicLoad(): Long =
    pointed.value
