package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class WinRTInspectableMethodDefinition private constructor(
    val signature: ComMethodSignature,
    internal val handler: (Any?, List<Any?>) -> Int,
    internal val readsHostManagedValue: Boolean,
    internal val hostHandler: ((WinRTInspectableComObject, Any?, List<Any?>) -> Int)?,
) {
    internal var abiEntryPoint: RawAddress? = null
        private set

    internal var rawWordHandler: ComRawWordCallback? = null
        private set

    constructor(
        signature: ComMethodSignature,
        handler: (List<Any?>) -> Int,
    ) : this(
        signature = signature,
        handler = { _, rawArguments -> handler(rawArguments) },
        readsHostManagedValue = false,
        hostHandler = null,
    )

    constructor(
        signature: ComMethodSignature,
        managedHandler: (Any?, List<Any?>) -> Int,
    ) : this(
        signature = signature,
        handler = managedHandler,
        readsHostManagedValue = true,
        hostHandler = null,
    )

    /**
     * Defines a shape-stable method whose behavior is resolved from the current CCW host.
     *
     * This is the managed equivalent of CsWinRT's type-level vtable entry: the method object can
     * be shared by every instance of one closed shape without capturing an older callback/value.
     */
    internal constructor(
        signature: ComMethodSignature,
        hostHandler: (WinRTInspectableComObject, Any?, List<Any?>) -> Int,
    ) : this(
        signature = signature,
        handler = { _, _ -> error("Host-aware COM method entered the compatibility callback path.") },
        readsHostManagedValue = true,
        hostHandler = hostHandler,
    )

    internal constructor(
        signature: ComMethodSignature,
        rawWordHandler: ComRawWordCallback,
    ) : this(
        signature = signature,
        handler = { _, _ -> error("Raw-word COM method handler entered the compatibility callback path.") },
        readsHostManagedValue = false,
        hostHandler = null,
    ) {
        this.rawWordHandler = rawWordHandler
    }

    constructor(
        signature: ComMethodSignature,
        abiEntryPoint: RawAddress,
    ) : this(
        signature = signature,
        handler = { _, _ -> error("Static COM method entry entered the compatibility callback path.") },
        readsHostManagedValue = true,
        hostHandler = null,
    ) {
        this.abiEntryPoint = abiEntryPoint
    }

    internal constructor(
        signature: ComMethodSignature,
        handler: (List<Any?>) -> Int,
        rawWordHandler: ComRawWordCallback,
    ) : this(
        signature = signature,
        handler = { _, rawArguments -> handler(rawArguments) },
        readsHostManagedValue = false,
        hostHandler = null,
    ) {
        this.rawWordHandler = rawWordHandler
    }
}

enum class WinRTComInterfaceBaseKind {
    IUnknown,
    IInspectable,
}

data class WinRTInspectableInterfaceDefinition(
    val interfaceId: Guid,
    val methods: List<WinRTInspectableMethodDefinition>,
    val baseKind: WinRTComInterfaceBaseKind = WinRTComInterfaceBaseKind.IInspectable,
)

internal data class WinRTInspectableInfoSnapshot(
    val runtimeClassName: String?,
    val interfaceIds: List<Guid>,
)

@OptIn(ExperimentalAtomicApi::class)
@PublishedApi
internal class WinRTInspectableComObject(
    interfaceDefinitions: List<WinRTInspectableInterfaceDefinition>,
    hiddenInterfaceDefinitions: List<WinRTInspectableInterfaceDefinition> = emptyList(),
    defaultInterfaceId: Guid? = null,
    private val runtimeClassName: String? = null,
    private val trustLevel: Int = 0,
    managedValue: Any? = null,
    weakManagedValue: Boolean = false,
    private val queryInterfaceFallback: ((Guid) -> RawAddress?)? = null,
    initialQueryInterfaceForwardTarget: ComObjectReference? = null,
    private val cleanupAction: (() -> Unit)? = null,
    shapeCacheKey: WinRTCcwDefinition? = null,
) : ManagedReferenceHost, AutoCloseable {
    private val ccwShape = shapeCacheKey?.let(::cachedShape)
        ?: ManagedCcwShape.create(interfaceDefinitions, hiddenInterfaceDefinitions, defaultInterfaceId)
    private val referencesAreAgile = ccwShape.referencesAreAgile
    internal val primaryInterfaceId = ccwShape.primaryInterfaceId
    private val initialQueryInterfaceTableEntryCount = ccwShape.queryInterfaceTableShape.entryCount
    private val interfaceObjectMemorySizeBytes = ccwShape.interfaceObjectMemorySizeBytes
    private val referenceCounterStorageOffsetBytes = interfaceObjectMemorySizeBytes
    private val queryInterfaceTableStorageOffsetBytes =
        referenceCounterStorageOffsetBytes + Long.SIZE_BYTES
    private val hostOwnedMemory = PlatformAbi.allocateBytesOwned(
        sizeBytes = queryInterfaceTableStorageOffsetBytes +
            managedComQueryInterfaceTableSizeBytes(initialQueryInterfaceTableEntryCount),
        alignmentBytes = NativeAbiLayout.ADDRESS.byteAlignment,
    )
    private val interfaceObjectMemory = hostOwnedMemory.pointer
    private val referenceCounterStorage = RawAddress(
        hostOwnedMemory.pointer.value + referenceCounterStorageOffsetBytes,
    )
    private val canonicalObjectMemory = interfaceObjectPointer(ccwShape.primaryInterfaceIndex)
    private val inboundBinding = ManagedComInboundBinding(
        host = this,
        value = managedValue,
        weak = weakManagedValue,
        canonicalObjectMemory = canonicalObjectMemory,
    )
    @PublishedApi
    internal val state = ManagedComHostState(
        rootReference = inboundBinding,
        cleanup = ::cleanup,
        borrowReady = weakManagedValue,
        referenceCounterStorage = referenceCounterStorage,
        referenceCounterStorageView = hostOwnedMemory.memory,
        referenceCounterStorageOffsetBytes = referenceCounterStorageOffsetBytes,
    )
    internal val managedValue: Any?
        get() = inboundBinding.get()
    private var externalPointerAliases: MutableSet<Long>? = null
    @kotlin.concurrent.Volatile
    private var lastStaticCallLease: StaticCallLeaseEntry? = null
    @kotlin.concurrent.Volatile
    private var queryInterfaceForwardTarget = initialQueryInterfaceForwardTarget
    private var queryInterfaceTablePublisher: ManagedComQueryInterfaceTablePublisher? = null

    init {
        initializeManagedComQueryInterfaceTable(
            storage = hostOwnedMemory.memory,
            storageOffsetBytes = queryInterfaceTableStorageOffsetBytes,
            shape = ccwShape.queryInterfaceTableShape,
            interfaceObjectMemory = hostOwnedMemory.memory,
            interfaceObjectMemoryOffsetBytes = 0L,
            interfaceObjectCount = ccwShape.interfaceCount,
            interfaceObjectStrideBytes = managedComInterfaceObjectSizeBytes,
            forwardTarget = directQueryInterfaceForwardTarget(),
        )
        var index = 0
        while (index < ccwShape.interfaceCount) {
            val objectMemoryOffsetBytes = index * managedComInterfaceObjectSizeBytes
            val objectMemory = interfaceObjectPointer(index)
            hostOwnedMemory.memory.writePointer(
                objectMemoryOffsetBytes,
                RawAddress(ccwShape.vtablePointerValues[index]),
            )
            inboundBinding.attach(
                objectMemory = objectMemory,
                objectMemoryView = hostOwnedMemory.memory,
                objectMemoryOffsetBytes = objectMemoryOffsetBytes,
            )
            state.attachReferenceCounter(
                objectMemory = objectMemory,
                objectMemoryView = hostOwnedMemory.memory,
                objectMemoryOffsetBytes = objectMemoryOffsetBytes,
            )
            index += 1
        }
    }

    override fun close() {
        state.releaseBaselineReference()
    }

    override fun createReference(interfaceId: Guid): ComObjectReference =
        tryCreateReference(interfaceId)
            ?: throw WinRTObjectDisposedException("Managed COM host is already closed.")

    internal fun createReferenceForKnownManagedValue(
        interfaceId: Guid,
        managedValue: Any,
    ): ComObjectReference =
        tryCreateReference(interfaceId, managedValue)
            ?: throw WinRTObjectDisposedException("Managed COM host is already closed.")

    internal fun tryCreateReference(
        interfaceId: Guid,
        knownManagedValue: Any? = null,
    ): ComObjectReference? {
        val localPointer = interfacePointerOrNull(interfaceId)
        val pointer = if (localPointer != null) {
            if (addReferenceCount(knownManagedValue) == 0) return null
            localPointer
        } else {
            tryAcquireExternalInterfacePointer(interfaceId)
        }
        return try {
            ComObjectReference(
                ComPtr.create(
                    raw = pointer.asRawComPtr(),
                    interfaceId = interfaceId,
                    trackContext = !referencesAreAgile,
                    managedCcwReleaseIdentity =
                        canonicalObjectMemory.takeIf { localPointer != null } ?: RawAddress.Null,
                ),
            )
        } catch (failure: Throwable) {
            if (localPointer != null) {
                releaseKnownLocalReference()
            } else {
                WinRTPlatformApi.releaseRaw(pointer)
            }
            throw failure
        }
    }

    override fun acquireReference(interfaceId: Guid): RawAddress =
        tryAcquireReference(interfaceId)
            ?: throw WinRTObjectDisposedException("Managed COM host is already closed.")

    internal fun acquireReferenceForKnownManagedValue(
        interfaceId: Guid,
        managedValue: Any,
    ): RawAddress =
        tryAcquireReference(interfaceId, managedValue)
            ?: throw WinRTObjectDisposedException("Managed COM host is already closed.")

    internal fun retainReferenceForKnownManagedValue(managedValue: Any) {
        if (addReferenceCount(managedValue) == 0) {
            throw WinRTObjectDisposedException("Managed COM host is already closed.")
        }
    }

    internal fun tryAcquireReference(
        interfaceId: Guid,
        knownManagedValue: Any? = null,
    ): RawAddress? = tryAcquireInterfacePointer(interfaceId, knownManagedValue)

    internal fun borrowCachedInterfacePointer(interfaceId: Guid): RawAddress =
        interfacePointer(interfaceId)

    internal fun tryBorrowCachedInterfacePointer(interfaceId: Guid): RawAddress =
        interfacePointerOrNull(interfaceId) ?: RawAddress.Null

    internal fun tryCreateStaticCallLease(
        interfaceId: Guid,
        identityVerifiedManagedValue: Any,
    ): WinRTProjectionMarshaler? {
        val lease = tryAcquireStaticCallLease(interfaceId, identityVerifiedManagedValue) ?: return null
        return try {
            WinRTProjectionMarshaler.guardedCallLease(lease, identityVerifiedManagedValue)
        } catch (failure: Throwable) {
            endStaticCallLease(identityVerifiedManagedValue)
            throw failure
        }
    }

    internal fun tryAcquireStaticCallLease(
        interfaceId: Guid,
        identityVerifiedManagedValue: Any,
    ): WinRTProjectionMarshaler? {
        val abi = interfacePointerOrNull(interfaceId) ?: return null
        beginStaticCallLease(identityVerifiedManagedValue)
        return try {
            lastStaticCallLease
                ?.takeIf { entry -> entry.interfaceId == interfaceId }
                ?.marshaler
                ?: WinRTProjectionMarshaler.cachedCallLease(abi, this).also { marshaler ->
                    lastStaticCallLease = StaticCallLeaseEntry(interfaceId, marshaler)
                }
        } catch (failure: Throwable) {
            state.endBorrowedCall(identityVerifiedManagedValue)
            throw failure
        }
    }

    internal fun beginStaticCallLease(identityVerifiedManagedValue: Any) {
        if (!state.tryBeginBorrowedCall(identityVerifiedManagedValue)) {
            throw WinRTObjectDisposedException("Managed COM host is already closed.")
        }
    }

    @PublishedApi
    internal inline fun endStaticCallLease(identityVerifiedManagedValue: Any?) {
        state.endBorrowedCall(identityVerifiedManagedValue)
    }

    internal fun releaseInitialReference(): Int = state.releaseBaselineReference()

    internal fun createStaticCallLease(
        interfaceId: Guid,
        identityVerifiedManagedValue: Any,
    ): WinRTProjectionMarshaler =
        tryCreateStaticCallLease(interfaceId, identityVerifiedManagedValue)
            ?: throw WinRTUnsupportedOperationException(
                "Managed COM object does not implement interface '$interfaceId'.",
                KnownHResults.E_NOINTERFACE,
            )

    internal fun tryCreateDetachedReference(interfaceId: Guid): RawAddress? =
        tryAcquireReference(interfaceId)

    fun createPrimaryReference(): ComObjectReference = createReference(primaryInterfaceId)

    fun registerExternalPointerAlias(pointer: RawAddress) {
        if (PlatformAbi.isNull(pointer)) {
            return
        }
        val key = PlatformAbi.pointerKey(pointer)
        val existing = platformTryWinRTProjectionInboundBinding(pointer.value)
            ?: externalAliasRegistry[key]
        if (existing != null && existing.host !== this) {
            return
        }
        val aliases = externalPointerAliases ?: mutableSetOf<Long>().also { externalPointerAliases = it }
        if (!aliases.add(key)) {
            return
        }
        externalAliasRegistry[key] = inboundBinding
        incrementExternalPointerAliasCount()
    }

    internal fun setQueryInterfaceForwardTarget(reference: ComObjectReference?) {
        queryInterfaceForwardTarget = reference
        val publisher = queryInterfaceTablePublisher
            ?: ManagedComQueryInterfaceTablePublisher(
                shape = ccwShape.queryInterfaceTableShape,
                interfaceObjectMemory = interfaceObjectMemory,
                interfaceObjectCount = ccwShape.interfaceCount,
                interfaceObjectStrideBytes = managedComInterfaceObjectSizeBytes,
            ).also { queryInterfaceTablePublisher = it }
        publisher.publish(directQueryInterfaceForwardTarget())
    }

    override fun detachReference(interfaceId: Guid): RawAddress =
        ManagedReferenceHostSupport.detachReference(
            createReference = { acquireReference(interfaceId) },
            releaseManagedReference = ::releaseManagedReference,
        )

    override fun releaseManagedReference() {
        releaseReference()
    }

    private fun interfacePointer(interfaceId: Guid): RawAddress =
        interfacePointerOrNull(interfaceId) ?: throw WinRTUnsupportedOperationException(
            "Managed COM object does not implement interface '$interfaceId'.",
            KnownHResults.E_NOINTERFACE,
        )

    private fun interfacePointerOrNull(interfaceId: Guid): RawAddress? =
        ccwShape.interfaceIndex(interfaceId)
            .takeIf { index -> index >= 0 }
            ?.let(::interfaceObjectPointer)

    private fun tryAcquireInterfacePointer(
        interfaceId: Guid,
        knownManagedValue: Any?,
    ): RawAddress? {
        val hostPointer = interfacePointerOrNull(interfaceId)
        if (hostPointer != null) {
            return if (addReferenceCount(knownManagedValue) == 0) null else hostPointer
        }
        return tryAcquireExternalInterfacePointer(interfaceId)
    }

    private fun tryAcquireExternalInterfacePointer(interfaceId: Guid): RawAddress {
        val result = queryExternalInterface(interfaceId)
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value) {
            throw WinRTUnsupportedOperationException(
                "Managed COM object does not implement interface '$interfaceId'.",
                KnownHResults.E_NOINTERFACE,
            )
        }
        WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
        if (PlatformAbi.isNull(result.pointer)) {
            throw WinRTNullReferenceException(
                "QueryInterface succeeded for '$interfaceId' but returned a null pointer.",
                KnownHResults.E_POINTER,
            )
        }
        return result.pointer
    }

    private fun queryExternalInterface(interfaceId: Guid): NativePointerResult {
        queryInterfaceFallback
            ?.invoke(interfaceId)
            ?.takeUnless(PlatformAbi::isNull)
            ?.let { pointer ->
                registerExternalPointerAlias(pointer)
                return NativePointerResult(KnownHResults.S_OK.value, pointer)
            }
        val forwardTarget = queryInterfaceForwardTarget
            ?: return NativePointerResult(KnownHResults.E_NOINTERFACE.value, PlatformAbi.nullPointer)
        return WinRTPlatformApi.queryInterfaceRaw(
            PlatformAbi.fromRawComPtr(forwardTarget.pointer),
            interfaceId,
        )
    }

    private fun directQueryInterfaceForwardTarget(): RawAddress {
        if (queryInterfaceFallback != null) {
            return PlatformAbi.nullPointer
        }
        return queryInterfaceForwardTarget
            ?.let { reference -> PlatformAbi.fromRawComPtr(reference.pointer) }
            ?: PlatformAbi.nullPointer
    }

    private fun interfaceObjectPointer(index: Int): RawAddress =
        RawAddress(interfaceObjectMemory.value + index * managedComInterfaceObjectSizeBytes)

    private fun queryInterface(
        requestedInterfaceId: Guid,
        resultPointer: RawAddress,
    ): Int {
        trace { "QI request=$requestedInterfaceId runtimeClassName=$runtimeClassName primary=$primaryInterfaceId" }
        val targetPointer = interfacePointerOrNull(requestedInterfaceId)
        if (targetPointer != null) {
            val hResult = if (addReferenceCount() == 0) KnownHResults.E_POINTER else KnownHResults.S_OK
            PlatformAbi.writePointer(
                resultPointer,
                targetPointer.takeIf { hResult == KnownHResults.S_OK } ?: PlatformAbi.nullPointer,
            )
            return hResult.value
        }

        val externalResult = queryExternalInterface(requestedInterfaceId)
        val hResultValue = when {
            externalResult.hResultValue < 0 -> externalResult.hResultValue
            PlatformAbi.isNull(externalResult.pointer) -> KnownHResults.E_POINTER.value
            else -> externalResult.hResultValue
        }
        PlatformAbi.writePointer(
            resultPointer,
            externalResult.pointer.takeIf { hResultValue >= 0 } ?: PlatformAbi.nullPointer,
        )
        trace {
            "QI external result request=$requestedInterfaceId hr=$hResultValue " +
                "pointer=${externalResult.pointer.takeIf { hResultValue >= 0 }?.let(PlatformAbi::pointerKey) ?: 0L}"
        }
        return hResultValue
    }

    /**
     * Fast QI entry used by the Native fixed IUnknown callback.  Native WinRT
     * passes an IID as two little-endian 64-bit words; matching those words
     * directly keeps the steady-state callback free of Guid/ByteArray and
     * query-result allocations.  A Guid is reconstructed only for fallback or
     * diagnostic paths that genuinely need the value object.
     */
    private fun queryInterfaceByAbiWords(
        requestedInterfaceLowBits: Long,
        requestedInterfaceHighBits: Long,
        resultPointer: RawAddress,
    ): Int {
        val targetPointerValue = interfacePointerValueByAbiWords(
            requestedInterfaceLowBits,
            requestedInterfaceHighBits,
        )
        if (targetPointerValue != 0L) {
            if (addReferenceCount() == 0) {
                PlatformAbi.writePointer(resultPointer, PlatformAbi.nullPointer)
                return KnownHResults.E_POINTER.value
            }
            PlatformAbi.writePointer(resultPointer, RawAddress(targetPointerValue))
            return KnownHResults.S_OK.value
        }

        // Only misses can reach the fallback, so paying for the Guid object is
        // intentional here and keeps the common generated-interface path lean.
        return queryInterface(
            requestedInterfaceId = Guid.fromAbiWords(
                requestedInterfaceLowBits,
                requestedInterfaceHighBits,
            ),
            resultPointer = resultPointer,
        )
    }

    private fun interfacePointerValueByAbiWords(
        requestedInterfaceLowBits: Long,
        requestedInterfaceHighBits: Long,
    ): Long {
        val index = ccwShape.interfaceIndexByAbiWords(
            requestedInterfaceLowBits,
            requestedInterfaceHighBits,
        )
        return index.takeIf { it >= 0 }?.let(::interfaceObjectPointer)?.value ?: 0L
    }

    private fun addReference(): Int {
        val count = state.addBorrowedReference()
        trace { "AddRef runtimeClassName=$runtimeClassName count=$count" }
        return count
    }

    private fun addReferenceCount(knownManagedValue: Any? = null): Int {
        val count = state.addReference(knownManagedValue)
        trace { "AddRef runtimeClassName=$runtimeClassName count=$count" }
        return count
    }

    private fun releaseReference(): Int {
        val count = state.releaseReference()
        trace { "Release runtimeClassName=$runtimeClassName count=$count" }
        return count
    }

    internal fun releaseKnownLocalReference() {
        releaseReference()
    }

    private fun tryProbeReferenceCount(): UInt? {
        if (addReferenceCount() == 0) {
            return null
        }
        return releaseReference().toUInt()
    }

    private fun addTrackerReference(): Int = state.addTrackerReference()

    private fun releaseTrackerReference(): Int = state.releaseTrackerReference()

    private fun getIids(
        countOut: RawAddress,
        idsOut: RawAddress,
    ): Int {
        val interfaceIds = ccwShape.visibleInterfaceIds
        trace { "GetIids count=${interfaceIds.size} ids=${interfaceIds.joinToString()}" }
        val memory = WinRTPlatformApi.coTaskMemAllocRaw(interfaceIds.size.toLong() * Guid.BYTE_SIZE)
        if (PlatformAbi.isNull(memory)) {
            PlatformAbi.writeInt32(countOut, 0)
            PlatformAbi.writePointer(idsOut, PlatformAbi.nullPointer)
            return KnownHResults.E_OUTOFMEMORY.value
        }
        interfaceIds.forEachIndexed { index, interfaceId ->
            PlatformAbi.writeGuid(memory, index.toLong() * Guid.BYTE_SIZE, interfaceId)
        }
        PlatformAbi.writeInt32(countOut, interfaceIds.size)
        PlatformAbi.writePointer(idsOut, memory)
        return KnownHResults.S_OK.value
    }

    private fun getRuntimeClassName(resultOut: RawAddress): Int {
        trace { "GetRuntimeClassName runtimeClassName=$runtimeClassName" }
        PlatformAbi.writePointer(resultOut, HString.create(runtimeClassName.orEmpty()).handle)
        return KnownHResults.S_OK.value
    }

    private fun getTrustLevel(resultOut: RawAddress): Int {
        trace { "GetTrustLevel trustLevel=$trustLevel" }
        PlatformAbi.writeInt32(resultOut, trustLevel)
        return KnownHResults.S_OK.value
    }

    private inline fun trace(message: () -> String) {
        if (FeatureSwitches.traceCcw) {
            println("winrt-ccw: ${message()}")
        }
    }

    private fun invokeMethod(
        interfaceId: Guid,
        methodIndex: Int,
        method: WinRTInspectableMethodDefinition,
        rawArguments: List<Any?>,
    ): Int = runCatching {
        trace { "Invoke interface=$interfaceId methodIndex=$methodIndex runtimeClassName=$runtimeClassName" }
        if (interfaceId == IID.IReferenceTrackerTarget) {
            when (methodIndex) {
                0 -> return@runCatching addTrackerReference()
                1 -> return@runCatching releaseTrackerReference()
            }
        }
        method.hostHandler?.invoke(this, managedValue, rawArguments)
            ?: method.handler(managedValue, rawArguments)
    }.getOrElse { error ->
        platformSetErrorInfo(error)
        platformHResultFromThrowable(error).value
    }

    private fun methodDefinition(
        interfaceId: Guid,
        methodIndex: Int,
    ): WinRTInspectableMethodDefinition =
        ccwShape.allInterfaceDefinitions[
            ccwShape.interfaceIndexById.getValue(interfaceId)
        ].methods[methodIndex]

    private fun cleanup() {
        externalPointerAliases?.forEach { key ->
            try {
                if (externalAliasRegistry[key]?.host === this) {
                    externalAliasRegistry.remove(key)
                }
            } finally {
                decrementExternalPointerAliasCount()
            }
        }
        externalPointerAliases?.clear()
        var index = 0
        while (index < ccwShape.interfaceCount) {
            val objectMemoryOffsetBytes = index * managedComInterfaceObjectSizeBytes
            val objectMemory = interfaceObjectPointer(index)
            inboundBinding.detach(
                objectMemory = objectMemory,
                objectMemoryView = hostOwnedMemory.memory,
                objectMemoryOffsetBytes = objectMemoryOffsetBytes,
            )
            index += 1
        }
        queryInterfaceTablePublisher?.close()
        inboundBinding.close()
        hostOwnedMemory.close()
        cleanupAction?.invoke()
    }

    private data class StaticCallLeaseEntry(
        val interfaceId: Guid,
        val marshaler: WinRTProjectionMarshaler,
    )

    /**
     * Immutable exposed-interface metadata. It deliberately contains no method handler or managed
     * value; those remain owned by each CCW host and are resolved at callback time.
     */
    private class ManagedCcwShape(
        val referencesAreAgile: Boolean,
        val primaryInterfaceId: Guid,
        val primaryInterfaceIndex: Int,
        val inspectableInterfaceIndex: Int,
        val visibleInterfaceIds: List<Guid>,
        val allInterfaceDefinitions: List<WinRTInspectableInterfaceDefinition>,
        val interfaceIndexById: Map<Guid, Int>,
        val interfaceIdLowBits: LongArray,
        val interfaceIdHighBits: LongArray,
        val interfaceObjectMemorySizeBytes: Long,
        val vtablePointerValues: LongArray,
        val queryInterfaceTableShape: ManagedComQueryInterfaceTableShape,
    ) {
        val interfaceCount: Int
            get() = allInterfaceDefinitions.size

        fun interfaceIndex(interfaceId: Guid): Int =
            when (interfaceId) {
                IID.IUnknown -> primaryInterfaceIndex
                IID.IInspectable -> inspectableInterfaceIndex
                else -> interfaceIndexById[interfaceId] ?: -1
            }

        fun interfaceIndexByAbiWords(
            lowBits: Long,
            highBits: Long,
        ): Int {
            if (lowBits == IID.IUnknown.abiLowBits && highBits == IID.IUnknown.abiHighBits) {
                return primaryInterfaceIndex
            }
            if (lowBits == IID.IInspectable.abiLowBits && highBits == IID.IInspectable.abiHighBits) {
                return inspectableInterfaceIndex
            }
            var index = 0
            while (index < interfaceCount) {
                if (interfaceIdLowBits[index] == lowBits && interfaceIdHighBits[index] == highBits) {
                    return index
                }
                index += 1
            }
            return -1
        }

        companion object {
            fun create(
                interfaceDefinitions: List<WinRTInspectableInterfaceDefinition>,
                hiddenInterfaceDefinitions: List<WinRTInspectableInterfaceDefinition>,
                defaultInterfaceId: Guid? = null,
            ): ManagedCcwShape {
                val visibleById = linkedMapOf<Guid, WinRTInspectableInterfaceDefinition>()
                interfaceDefinitions.forEach { definition ->
                    visibleById[definition.interfaceId] = definition
                }
                val allById = linkedMapOf<Guid, WinRTInspectableInterfaceDefinition>()
                (interfaceDefinitions + hiddenInterfaceDefinitions).forEach { definition ->
                    allById[definition.interfaceId] = definition
                }
                val primaryInspectableInterfaceId = interfaceDefinitions.firstOrNull {
                    it.baseKind == WinRTComInterfaceBaseKind.IInspectable
                }?.interfaceId
                val visibleInterfaceIds = visibleById.keys.toList()
                val allInterfaceDefinitions = allById.values.toList()
                val interfaceIndexById = buildMap(allInterfaceDefinitions.size) {
                    allInterfaceDefinitions.forEachIndexed { index, definition ->
                        put(definition.interfaceId, index)
                    }
                }
                val primaryInterfaceId = defaultInterfaceId
                    ?.takeIf(visibleById::containsKey)
                    ?: primaryInspectableInterfaceId
                    ?: interfaceDefinitions.firstOrNull()?.interfaceId
                    ?: error("Inspectable COM object must expose at least one interface.")
                val primaryInterfaceIndex = interfaceIndexById.getValue(primaryInterfaceId)
                val inspectableInterfaceIndex = interfaceIndexById[IID.IInspectable]
                    ?: primaryInspectableInterfaceId?.let(interfaceIndexById::get)
                    ?: -1
                val queryInterfaceIds = buildList(allById.size + 2) {
                    fun addIfMissing(interfaceId: Guid) {
                        if (interfaceId !in this) {
                            add(interfaceId)
                        }
                    }
                    addIfMissing(IID.IUnknown)
                    if (IID.IInspectable in allById || primaryInspectableInterfaceId != null) {
                        addIfMissing(IID.IInspectable)
                    }
                    allById.keys.forEach(::addIfMissing)
                }
                val queryInterfaceLowBits = LongArray(queryInterfaceIds.size)
                val queryInterfaceHighBits = LongArray(queryInterfaceIds.size)
                val queryInterfaceTargetOffsets = LongArray(queryInterfaceIds.size)
                queryInterfaceIds.forEachIndexed { index, interfaceId ->
                    queryInterfaceLowBits[index] = interfaceId.abiLowBits
                    queryInterfaceHighBits[index] = interfaceId.abiHighBits
                    val targetIndex = when (interfaceId) {
                        IID.IUnknown -> primaryInterfaceIndex
                        IID.IInspectable -> inspectableInterfaceIndex
                        else -> interfaceIndexById[interfaceId] ?: -1
                    }
                    check(targetIndex >= 0) {
                        "Managed COM QI table entry '$interfaceId' has no interface target."
                    }
                    queryInterfaceTargetOffsets[index] =
                        targetIndex.toLong() * managedComInterfaceObjectSizeBytes
                }
                return ManagedCcwShape(
                    referencesAreAgile = interfaceIndexById.containsKey(IID.IAgileObject),
                    primaryInterfaceId = primaryInterfaceId,
                    primaryInterfaceIndex = primaryInterfaceIndex,
                    inspectableInterfaceIndex = inspectableInterfaceIndex,
                    visibleInterfaceIds = visibleInterfaceIds,
                    allInterfaceDefinitions = allInterfaceDefinitions,
                    interfaceIndexById = interfaceIndexById,
                    interfaceIdLowBits = LongArray(allInterfaceDefinitions.size) { index ->
                        allInterfaceDefinitions[index].interfaceId.abiLowBits
                    },
                    interfaceIdHighBits = LongArray(allInterfaceDefinitions.size) { index ->
                        allInterfaceDefinitions[index].interfaceId.abiHighBits
                    },
                    interfaceObjectMemorySizeBytes =
                        allInterfaceDefinitions.size.toLong() * managedComInterfaceObjectSizeBytes,
                    vtablePointerValues = LongArray(allInterfaceDefinitions.size) { index ->
                        SharedInspectableVtables.getOrCreate(allInterfaceDefinitions[index]).value
                    },
                    queryInterfaceTableShape = ManagedComQueryInterfaceTableShape(
                        interfaceIdLowBits = queryInterfaceLowBits,
                        interfaceIdHighBits = queryInterfaceHighBits,
                        targetOffsetsBytes = queryInterfaceTargetOffsets,
                    ),
                )
            }
        }
    }

    companion object {
        private const val managedComInterfaceObjectPointerCount = 4
        private val managedComInterfaceObjectSizeBytes =
            managedComInterfaceObjectPointerCount * NativeAbiLayout.ADDRESS.byteSize
        private val externalAliasRegistry = ConcurrentCacheMap<Long, ManagedComInboundBinding>()
        private val externalPointerAliasCount = AtomicInt(0)

        private fun cachedShape(definition: WinRTCcwDefinition): ManagedCcwShape {
            @Suppress("UNCHECKED_CAST")
            (definition.preparedShape.load() as ManagedCcwShape?)?.let { return it }

            val created = ManagedCcwShape.create(
                interfaceDefinitions = definition.interfaceDefinitions,
                hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
                defaultInterfaceId = definition.defaultInterfaceId,
            )
            if (definition.preparedShape.compareAndSet(null, created)) {
                return created
            }

            @Suppress("UNCHECKED_CAST")
            return definition.preparedShape.load() as ManagedCcwShape
        }

        internal fun findManagedValue(pointer: RawAddress): Any? =
            findRegisteredInboundBinding(pointer)?.get()

        internal fun findInspectableInfo(pointer: RawAddress): WinRTInspectableInfoSnapshot? =
            findRegisteredInboundBinding(pointer)?.host?.let { host ->
                WinRTInspectableInfoSnapshot(
                    runtimeClassName = host.runtimeClassName,
                    interfaceIds = host.ccwShape.visibleInterfaceIds,
                )
            }

        internal fun tryProbeReferenceCount(pointer: RawAddress): UInt? =
            findRegisteredInboundBinding(pointer)?.host?.tryProbeReferenceCount()

        internal fun inspectableBox(
            value: Any?,
            runtimeClassName: String? = null,
        ): WinRTInspectableComObject =
            WinRTInspectableComObject(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = IID.IInspectable,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = IID.IInspectable,
                runtimeClassName = runtimeClassName,
                managedValue = value,
            )

        @PublishedApi
        internal fun findRegisteredInboundBinding(pointer: RawAddress): ManagedComInboundBinding? =
            platformTryWinRTProjectionInboundBinding(pointer.value)
                ?: findRegisteredExternalAliasBinding(pointer)

        internal fun isManagedVtablePointer(pointer: RawAddress): Boolean =
            SharedInspectableVtables.contains(pointer)

        private fun findRegisteredExternalAliasBinding(pointer: RawAddress): ManagedComInboundBinding? =
            if (externalPointerAliasCount.load() == 0) {
                null
            } else {
                externalAliasRegistry[PlatformAbi.pointerKey(pointer)]
            }

        private fun incrementExternalPointerAliasCount() {
            while (true) {
                val current = externalPointerAliasCount.load()
                if (externalPointerAliasCount.compareAndSet(current, current + 1)) {
                    return
                }
            }
        }

        private fun decrementExternalPointerAliasCount() {
            while (true) {
                val current = externalPointerAliasCount.load()
                check(current > 0) { "External pointer alias count underflow." }
                if (externalPointerAliasCount.compareAndSet(current, current - 1)) {
                    return
                }
            }
        }

        private fun findCallbackInboundBinding(pointer: RawAddress): ManagedComInboundBinding? =
            winRTProjectionInboundBinding(pointer.value)

        private fun findHost(pointer: RawAddress): WinRTInspectableComObject? =
            findCallbackInboundBinding(pointer)?.host

        internal fun invokeQueryInterfaceCallback(
            thisPointer: RawAddress,
            interfaceIdPointer: RawAddress,
            resultPointer: RawAddress,
        ): Int = try {
            findHost(thisPointer)?.queryInterface(
                requestedInterfaceId = PlatformAbi.readGuid(interfaceIdPointer),
                resultPointer = resultPointer,
            ) ?: KnownHResults.E_POINTER.value
        } catch (error: Throwable) {
            winRTProjectionInboundFailure(error)
        }

        internal fun invokeQueryInterfaceCallbackByAbiWords(
            thisPointer: RawAddress,
            interfaceIdLowBits: Long,
            interfaceIdHighBits: Long,
            resultPointer: RawAddress,
        ): Int = try {
            findHost(thisPointer)?.queryInterfaceByAbiWords(
                requestedInterfaceLowBits = interfaceIdLowBits,
                requestedInterfaceHighBits = interfaceIdHighBits,
                resultPointer = resultPointer,
            ) ?: KnownHResults.E_POINTER.value
        } catch (error: Throwable) {
            winRTProjectionInboundFailure(error)
        }

        internal fun invokeAddRefCallback(thisPointer: RawAddress): Int =
            try {
                findHost(thisPointer)?.addReference() ?: 0
            } catch (error: Throwable) {
                platformSetErrorInfo(error)
                0
            }

        internal fun invokeReleaseCallback(thisPointer: RawAddress): Int =
            try {
                findHost(thisPointer)?.releaseReference() ?: 0
            } catch (error: Throwable) {
                platformSetErrorInfo(error)
                0
            }
    }

    private object SharedInspectableVtables {
        private val scope = PlatformAbi.sharedScope()
        private val vtables = ConcurrentCacheMap<SharedVtableKey, RawAddress>()
        private val vtablePointers = ConcurrentCacheSet<Long>()
        private val methodCallbacks = ConcurrentCacheMap<SharedMethodCallbackKey, NativeCallbackHandle>()

        private val queryInterfaceCallback = platformCreateInspectableQueryInterfaceCallback()
        private val addRefCallback = platformCreateInspectableAddRefCallback()
        private val releaseCallback = platformCreateInspectableReleaseCallback()
        private val getIidsCallback =
            rawWordCallbackOf(IInspectableVftbl.GetIids) { thisWord, countWord, idsWord, _, _, _, _ ->
                findHost(RawAddress(thisWord))?.getIids(
                    countOut = RawAddress(countWord),
                    idsOut = RawAddress(idsWord),
                ) ?: KnownHResults.E_POINTER.value
            }
        private val getRuntimeClassNameCallback =
            rawWordCallbackOf(IInspectableVftbl.GetRuntimeClassName) { thisWord, resultWord, _, _, _, _, _ ->
                findHost(RawAddress(thisWord))?.getRuntimeClassName(RawAddress(resultWord))
                    ?: KnownHResults.E_POINTER.value
            }
        private val getTrustLevelCallback =
            rawWordCallbackOf(IInspectableVftbl.GetTrustLevel) { thisWord, resultWord, _, _, _, _, _ ->
                findHost(RawAddress(thisWord))?.getTrustLevel(RawAddress(resultWord))
                    ?: KnownHResults.E_POINTER.value
            }

        fun getOrCreate(definition: WinRTInspectableInterfaceDefinition): RawAddress =
            vtables.computeIfAbsent(SharedVtableKey.from(definition)) { key ->
                val vtable = PlatformAbi.allocatePointerArray(scope, key.firstCustomSlot + key.methodSignatures.size)
                PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
                PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
                PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
                if (key.baseKind == WinRTComInterfaceBaseKind.IInspectable) {
                    PlatformAbi.writePointerAt(vtable, IInspectableVftblSlots.GetIids, getIidsCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, IInspectableVftblSlots.GetRuntimeClassName, getRuntimeClassNameCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, IInspectableVftblSlots.GetTrustLevel, getTrustLevelCallback.pointer)
                }
                key.methodSignatures.forEachIndexed { index, signature ->
                    val methodKey = SharedMethodCallbackKey(key.interfaceId, index, signature)
                    PlatformAbi.writePointerAt(
                        vtable,
                        key.firstCustomSlot + index,
                        key.methodEntryPoints[index] ?: methodCallback(methodKey).pointer,
                    )
                }
                vtable.also { pointer -> vtablePointers.add(pointer.value) }
            }

        fun contains(pointer: RawAddress): Boolean = pointer.value in vtablePointers

        private fun methodCallback(key: SharedMethodCallbackKey): NativeCallbackHandle =
            methodCallbacks.computeIfAbsent(key) {
                if (key.signature.supportsRawWordCallback()) {
                    rawWordCallbackOf(key.signature) { thisWord, arg0, arg1, arg2, arg3, arg4, arg5 ->
                        val binding = findCallbackInboundBinding(RawAddress(thisWord))
                            ?: return@rawWordCallbackOf KnownHResults.E_POINTER.value
                        val method = binding.host.methodDefinition(key.interfaceId, key.methodIndex)
                        method.rawWordHandler?.let { handler ->
                            handler.invoke(arg0, arg1, arg2, arg3, arg4, arg5, 0L)
                        } ?: binding.host.invokeMethod(
                            interfaceId = key.interfaceId,
                            methodIndex = key.methodIndex,
                            method = method,
                            rawArguments = key.signature.explicitParameterKinds.mapIndexed { index, kind ->
                                rawWordToCallbackValue(kind, rawWordAt(index, arg0, arg1, arg2, arg3, arg4, arg5))
                            },
                        )
                    }
                } else {
                    callbackOf(key.signature) { args ->
                        val binding = findCallbackInboundBinding(args[0] as RawAddress)
                            ?: return@callbackOf KnownHResults.E_POINTER.value
                        val method = binding.host.methodDefinition(key.interfaceId, key.methodIndex)
                        binding.host.invokeMethod(
                            interfaceId = key.interfaceId,
                            methodIndex = key.methodIndex,
                            method = method,
                            rawArguments = args.drop(1),
                        )
                    }
                }
            }

        private fun rawWordCallbackOf(
            signature: ComMethodSignature,
            callback: ComRawWordCallback,
        ): NativeCallbackHandle = ComAbiInteropBridge.createRawWordComMethodCallback(signature, callback)

        private fun callbackOf(
            signature: ComMethodSignature,
            callback: (List<Any?>) -> Int,
        ): NativeCallbackHandle = ComAbiInteropBridge.createComMethodCallback(signature, callback)

        private fun ComMethodSignature.supportsRawWordCallback(): Boolean =
            explicitParameterKinds.size <= 6 && explicitParameterKinds.none { it is ComAbiValueKind.Struct }

        private fun rawWordAt(
            index: Int,
            arg0: Long,
            arg1: Long,
            arg2: Long,
            arg3: Long,
            arg4: Long,
            arg5: Long,
        ): Long = when (index) {
            0 -> arg0
            1 -> arg1
            2 -> arg2
            3 -> arg3
            4 -> arg4
            5 -> arg5
            else -> error("Raw-word COM callback index $index exceeds the supported arity.")
        }

        private fun rawWordToCallbackValue(kind: ComAbiValueKind, word: Long): Any =
            when (kind) {
                ComAbiValueKind.Pointer -> RawAddress(word)
                ComAbiValueKind.Int8 -> word.toByte()
                ComAbiValueKind.Int16 -> word.toShort()
                ComAbiValueKind.Int32 -> word.toInt()
                ComAbiValueKind.Int64 -> word
                ComAbiValueKind.Float -> Float.fromBits(word.toInt())
                ComAbiValueKind.Double -> Double.fromBits(word)
                is ComAbiValueKind.Struct -> error("Struct callbacks use the compatibility callback path.")
            }
    }

    private data class SharedVtableKey(
        val interfaceId: Guid,
        val baseKind: WinRTComInterfaceBaseKind,
        val methodSignatures: List<ComMethodSignature>,
        val methodEntryPoints: List<RawAddress?>,
    ) {
        val firstCustomSlot: Int
            get() = when (baseKind) {
                WinRTComInterfaceBaseKind.IUnknown -> IUnknownVftblSlots.Release + 1
                WinRTComInterfaceBaseKind.IInspectable -> IInspectableVftblSlots.FirstCustom
            }

        companion object {
            fun from(definition: WinRTInspectableInterfaceDefinition): SharedVtableKey =
                SharedVtableKey(
                    interfaceId = definition.interfaceId,
                    baseKind = definition.baseKind,
                    methodSignatures = definition.methods.map { method -> method.signature },
                    methodEntryPoints = definition.methods.map { method -> method.abiEntryPoint },
                )
        }
    }

    private data class SharedMethodCallbackKey(
        val interfaceId: Guid,
        val methodIndex: Int,
        val signature: ComMethodSignature,
    )

}

@PublishedApi
internal fun winRTProjectionInboundFailure(error: Throwable): Int {
    platformSetErrorInfo(error)
    return platformHResultFromThrowable(error).value
}
