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

    // CsWinRT 3 ComputeVtables returns type-level ComInterfaceEntry storage. Keep our
    // IID/relative-offset entries on the cached shape too, never on another instance.
    @kotlin.concurrent.Volatile
    private var sharedEntries: OwnedNativeAllocation? = null
    @Suppress("unused")
    private var cleanup: AutoCloseable? = null

    // Some runtime definitions capture an instance and are never reused. Unlike generated
    // CsWinRT static entries, our native table needs an allocation; defer it until shape reuse
    // is observed. Existing inline tables remain valid when later hosts use shared entries.
    fun shareNativeEntries() {
        if (sharedEntries != null) return
        creationLock.withLock {
            if (sharedEntries != null) return@withLock
            val allocation = PlatformAbi.allocateBytesOwned(
                (entryCount.toLong() * managedComQueryInterfaceTableEntryWordCount * Long.SIZE_BYTES).coerceAtLeast(1L),
                Long.SIZE_BYTES.toLong(),
            )
            try {
                writeEntryWords(this) { index, value ->
                    allocation.memory.writeInt64(index.toLong() * Long.SIZE_BYTES, value)
                }
                cleanup = finalizationHook.register(this, allocation::close)
                sharedEntries = allocation
            } catch (error: Throwable) {
                allocation.close()
                throw error
            }
        }
    }

    val sharedEntriesPointer: RawAddress
        get() {
            val entries = sharedEntries ?: return RawAddress.Null
            return entries.pointer
        }

    val instanceStorageSizeBytes: Long
        get() = (managedComQueryInterfaceTableHeaderWordCount.toLong() +
            if (sharedEntries == null) entryCount.toLong() * managedComQueryInterfaceTableEntryWordCount else 0L) * Long.SIZE_BYTES

    companion object {
        private val finalizationHook = FinalizationHook()
        private val creationLock = PlatformLock()
    }
}

internal const val managedComQueryInterfaceTableEntryCountWord = 0
internal const val managedComQueryInterfaceTableForwardTargetWord = 1
internal const val managedComQueryInterfaceTableEntriesWord = 2
internal const val managedComQueryInterfaceTableObjectBaseWord = 3
internal const val managedComQueryInterfaceTableHeaderWordCount = 4
internal const val managedComQueryInterfaceTableEntryWordCount = 3

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
            sizeBytes = shape.instanceStorageSizeBytes,
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
    writeTableWords(storage, shape, interfaceObjectMemory, forwardTarget) { index, value ->
        writeTableWord(storage, index, value)
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
    writeTableWords(
        RawAddress(storage.pointer.value + storageOffsetBytes),
        shape,
        RawAddress(interfaceObjectMemory.pointer.value + interfaceObjectMemoryOffsetBytes),
        forwardTarget,
    ) { index, value ->
        writeTableWord(storage, storageOffsetBytes, index, value)
    }
}

private inline fun writeTableWords(
    storage: RawAddress,
    shape: ManagedComQueryInterfaceTableShape,
    interfaceObjectMemory: RawAddress,
    forwardTarget: RawAddress,
    writeWord: (Int, Long) -> Unit,
) {
    val shared = shape.sharedEntriesPointer
    val entries = if (PlatformAbi.isNull(shared)) {
        RawAddress(storage.value + managedComQueryInterfaceTableHeaderWordCount * Long.SIZE_BYTES)
    } else shared
    writeWord(managedComQueryInterfaceTableEntryCountWord, shape.entryCount.toLong())
    writeWord(managedComQueryInterfaceTableForwardTargetWord, forwardTarget.value)
    writeWord(managedComQueryInterfaceTableEntriesWord, entries.value)
    writeWord(managedComQueryInterfaceTableObjectBaseWord, interfaceObjectMemory.value)
    if (PlatformAbi.isNull(shared)) {
        writeEntryWords(shape) { index, value ->
            writeWord(managedComQueryInterfaceTableHeaderWordCount + index, value)
        }
    }
}

private inline fun writeEntryWords(shape: ManagedComQueryInterfaceTableShape, writeWord: (Int, Long) -> Unit) {
    var index = 0
    while (index < shape.entryCount) {
        val offset = index * managedComQueryInterfaceTableEntryWordCount
        writeWord(offset, shape.interfaceIdLowBits[index])
        writeWord(offset + 1, shape.interfaceIdHighBits[index])
        writeWord(offset + 2, shape.targetOffsetsBytes[index])
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
