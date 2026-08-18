package io.github.composefluent.winrt.runtime

internal class ManagedComQueryInterfaceTableShape(
    internal val interfaceIdLowBits: LongArray,
    internal val interfaceIdHighBits: LongArray,
    internal val targetOffsetsBytes: LongArray,
) {
    init {
        require(interfaceIdLowBits.size == interfaceIdHighBits.size)
        require(interfaceIdLowBits.size == targetOffsetsBytes.size)
    }

    val entryCount: Int
        get() = interfaceIdLowBits.size
}

internal const val managedComQueryInterfaceTableEntryCountWord = 0
internal const val managedComQueryInterfaceTableForwardTargetWord = 1
internal const val managedComQueryInterfaceTableHeaderWordCount = 2
internal const val managedComQueryInterfaceTableEntryWordCount = 3

internal fun managedComQueryInterfaceTableWordCount(entryCount: Int): Int =
    managedComQueryInterfaceTableHeaderWordCount +
        entryCount * managedComQueryInterfaceTableEntryWordCount

internal fun managedComQueryInterfaceTableSizeBytes(entryCount: Int): Long =
    managedComQueryInterfaceTableWordCount(entryCount).toLong() * Long.SIZE_BYTES

internal fun initializeManagedComQueryInterfaceTable(
    storage: RawAddress,
    shape: ManagedComQueryInterfaceTableShape,
    interfaceObjectMemory: RawAddress,
    interfaceObjectCount: Int,
    interfaceObjectStrideBytes: Long,
    forwardTarget: RawAddress,
) {
    writeManagedComQueryInterfaceTable(storage, shape, interfaceObjectMemory, forwardTarget)
    attachManagedComQueryInterfaceTable(
        storage = storage,
        interfaceObjectMemory = interfaceObjectMemory,
        interfaceObjectCount = interfaceObjectCount,
        interfaceObjectStrideBytes = interfaceObjectStrideBytes,
    )
}

/**
 * Allocation-backed construction variant. It keeps the common table layout identical to the raw
 * address path while allowing JVM to write through the segment returned by Arena.allocate.
 */
internal fun initializeManagedComQueryInterfaceTable(
    storage: NativeMemoryView,
    storageOffsetBytes: Long,
    shape: ManagedComQueryInterfaceTableShape,
    interfaceObjectMemory: NativeMemoryView,
    interfaceObjectMemoryOffsetBytes: Long,
    interfaceObjectCount: Int,
    interfaceObjectStrideBytes: Long,
    forwardTarget: RawAddress,
) {
    writeManagedComQueryInterfaceTable(
        storage = storage,
        storageOffsetBytes = storageOffsetBytes,
        shape = shape,
        interfaceObjectMemory = interfaceObjectMemory,
        interfaceObjectMemoryOffsetBytes = interfaceObjectMemoryOffsetBytes,
        forwardTarget = forwardTarget,
    )
    attachManagedComQueryInterfaceTable(
        storage = storage,
        storageOffsetBytes = storageOffsetBytes,
        interfaceObjectMemory = interfaceObjectMemory,
        interfaceObjectMemoryOffsetBytes = interfaceObjectMemoryOffsetBytes,
        interfaceObjectCount = interfaceObjectCount,
        interfaceObjectStrideBytes = interfaceObjectStrideBytes,
    )
}

internal class ManagedComQueryInterfaceTablePublisher(
    private val shape: ManagedComQueryInterfaceTableShape,
    private val interfaceObjectMemory: RawAddress,
    private val interfaceObjectCount: Int,
    private val interfaceObjectStrideBytes: Long,
) : AutoCloseable {
    private val allocations = mutableListOf<OwnedNativeAllocation>()

    fun publish(forwardTarget: RawAddress) {
        val allocation = PlatformAbi.allocateBytesOwned(
            sizeBytes = managedComQueryInterfaceTableSizeBytes(shape.entryCount),
            alignmentBytes = Long.SIZE_BYTES.toLong(),
        )
        writeManagedComQueryInterfaceTable(
            storage = allocation.pointer,
            shape = shape,
            interfaceObjectMemory = interfaceObjectMemory,
            forwardTarget = forwardTarget,
        )
        allocations += allocation
        attachManagedComQueryInterfaceTable(
            storage = allocation.pointer,
            interfaceObjectMemory = interfaceObjectMemory,
            interfaceObjectCount = interfaceObjectCount,
            interfaceObjectStrideBytes = interfaceObjectStrideBytes,
        )
    }

    override fun close() {
        allocations.forEach(OwnedNativeAllocation::close)
        allocations.clear()
    }
}

private fun writeManagedComQueryInterfaceTable(
    storage: RawAddress,
    shape: ManagedComQueryInterfaceTableShape,
    interfaceObjectMemory: RawAddress,
    forwardTarget: RawAddress,
) {
    writeTableWord(storage, managedComQueryInterfaceTableEntryCountWord, shape.entryCount.toLong())
    writeTableWord(storage, managedComQueryInterfaceTableForwardTargetWord, forwardTarget.value)
    var index = 0
    while (index < shape.entryCount) {
        val offset = managedComQueryInterfaceTableHeaderWordCount +
            index * managedComQueryInterfaceTableEntryWordCount
        writeTableWord(storage, offset, shape.interfaceIdLowBits[index])
        writeTableWord(storage, offset + 1, shape.interfaceIdHighBits[index])
        writeTableWord(
            storage,
            offset + 2,
            interfaceObjectMemory.value + shape.targetOffsetsBytes[index],
        )
        index += 1
    }
}

private fun attachManagedComQueryInterfaceTable(
    storage: RawAddress,
    interfaceObjectMemory: RawAddress,
    interfaceObjectCount: Int,
    interfaceObjectStrideBytes: Long,
) {
    var index = 0
    while (index < interfaceObjectCount) {
        PlatformAbi.writePointerAt(
            array = RawAddress(interfaceObjectMemory.value + index * interfaceObjectStrideBytes),
            index = managedComQueryInterfaceTableSlot,
            value = storage,
        )
        index += 1
    }
}

private fun writeManagedComQueryInterfaceTable(
    storage: NativeMemoryView,
    storageOffsetBytes: Long,
    shape: ManagedComQueryInterfaceTableShape,
    interfaceObjectMemory: NativeMemoryView,
    interfaceObjectMemoryOffsetBytes: Long,
    forwardTarget: RawAddress,
) {
    writeTableWord(storage, storageOffsetBytes, managedComQueryInterfaceTableEntryCountWord, shape.entryCount.toLong())
    writeTableWord(storage, storageOffsetBytes, managedComQueryInterfaceTableForwardTargetWord, forwardTarget.value)
    var index = 0
    while (index < shape.entryCount) {
        val offset = managedComQueryInterfaceTableHeaderWordCount +
            index * managedComQueryInterfaceTableEntryWordCount
        writeTableWord(storage, storageOffsetBytes, offset, shape.interfaceIdLowBits[index])
        writeTableWord(storage, storageOffsetBytes, offset + 1, shape.interfaceIdHighBits[index])
        writeTableWord(
            storage,
            storageOffsetBytes,
            offset + 2,
            interfaceObjectMemory.pointer.value +
                interfaceObjectMemoryOffsetBytes +
                shape.targetOffsetsBytes[index],
        )
        index += 1
    }
}

private fun attachManagedComQueryInterfaceTable(
    storage: NativeMemoryView,
    storageOffsetBytes: Long,
    interfaceObjectMemory: NativeMemoryView,
    interfaceObjectMemoryOffsetBytes: Long,
    interfaceObjectCount: Int,
    interfaceObjectStrideBytes: Long,
) {
    val storagePointer = RawAddress(storage.pointer.value + storageOffsetBytes)
    var index = 0
    while (index < interfaceObjectCount) {
        interfaceObjectMemory.writePointer(
            interfaceObjectMemoryOffsetBytes +
                index * interfaceObjectStrideBytes +
                managedComQueryInterfaceTableSlot * Long.SIZE_BYTES,
            storagePointer,
        )
        index += 1
    }
}

private fun writeTableWord(
    storage: RawAddress,
    wordIndex: Int,
    value: Long,
) {
    PlatformAbi.writeInt64(
        RawAddress(storage.value + wordIndex * Long.SIZE_BYTES.toLong()),
        value,
    )
}

private fun writeTableWord(
    storage: NativeMemoryView,
    storageOffsetBytes: Long,
    wordIndex: Int,
    value: Long,
) {
    storage.writeInt64(storageOffsetBytes + wordIndex * Long.SIZE_BYTES.toLong(), value)
}
