package io.github.composefluent.winrt.runtime

internal const val managedComAddRefFastPathMinimumCount = 2
internal const val managedComReleaseFastPathMinimumCount = 3
internal const val managedComRootTransitionCount = -1L
internal const val managedComBorrowReadyReferenceFlag = 1L shl 62
internal const val managedComReferenceCountMask = Int.MAX_VALUE.toLong()

@PublishedApi
internal expect class PlatformManagedComReferenceCounter(
    initialValue: Long,
    initialStorage: RawAddress = RawAddress.Null,
    initialStorageView: NativeMemoryView? = null,
    initialStorageOffsetBytes: Long = 0L,
) : AutoCloseable {
    fun load(): Long

    fun compareAndSet(expectedValue: Long, newValue: Long): Boolean

    fun store(newValue: Long)

    fun attach(
        objectMemory: RawAddress,
        objectMemoryView: NativeMemoryView? = null,
        objectMemoryOffsetBytes: Long = 0L,
    )

    override fun close()
}

internal fun PlatformManagedComReferenceCounter.detach(
    objectMemory: RawAddress,
    objectMemoryView: NativeMemoryView? = null,
    objectMemoryOffsetBytes: Long = 0L,
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
