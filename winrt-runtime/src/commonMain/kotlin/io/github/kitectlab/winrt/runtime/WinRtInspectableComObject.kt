package io.github.kitectlab.winrt.runtime

internal data class WinRtInspectableMethodDefinition(
    val signature: ComMethodSignature,
    val handler: (List<Any?>) -> Int,
)

internal enum class WinRtComInterfaceBaseKind {
    IUnknown,
    IInspectable,
}

internal data class WinRtInspectableInterfaceDefinition(
    val interfaceId: Guid,
    val methods: List<WinRtInspectableMethodDefinition>,
    val baseKind: WinRtComInterfaceBaseKind = WinRtComInterfaceBaseKind.IInspectable,
)

internal data class WinRtInspectableInfoSnapshot(
    val runtimeClassName: String?,
    val interfaceIds: List<Guid>,
)

internal class WinRtInspectableComObject(
    interfaceDefinitions: List<WinRtInspectableInterfaceDefinition>,
    private val runtimeClassName: String? = null,
    private val trustLevel: Int = 0,
    private val managedValue: Any? = null,
) : ManagedReferenceHost, AutoCloseable {
    private val scope = PlatformAbi.sharedScope()
    private val state = ManagedComHostState(::cleanup)
    private val interfaces = interfaceDefinitions.associateBy { it.interfaceId }
    private val interfaceEntries = interfaceDefinitions.associate { definition ->
        definition.interfaceId to createInterfaceEntry(definition)
    }
    private val interfaceIdsMemory = allocateInterfaceIds(interfaceDefinitions)
    private val primaryInspectableInterfaceId = interfaceDefinitions.firstOrNull {
        it.baseKind == WinRtComInterfaceBaseKind.IInspectable
    }?.interfaceId
    private val primaryInterfaceId = primaryInspectableInterfaceId ?: interfaceDefinitions.firstOrNull()?.interfaceId
        ?: error("Inspectable COM object must expose at least one interface.")

    init {
        interfaceEntries.values.forEach { entry ->
            registry[PlatformAbi.pointerKey(entry.objectMemory)] = this
        }
    }

    override fun close() {
        releaseManagedReference()
    }

    override fun createReference(interfaceId: Guid): ComObjectReference {
        addReference()
        return ComObjectReference(interfacePointer(interfaceId).asRawComPtr(), interfaceId)
    }

    fun createPrimaryReference(): ComObjectReference = createReference(primaryInterfaceId)

    fun detachReference(interfaceId: Guid = primaryInterfaceId): RawAddress =
        ManagedReferenceHostSupport.detachReference(
            createReference = {
                addReference()
                interfacePointer(interfaceId)
            },
            releaseManagedReference = ::releaseManagedReference,
        )

    override fun releaseManagedReference() {
        releaseReference()
    }

    private fun interfacePointer(interfaceId: Guid): RawAddress =
        when (interfaceId) {
            IID.IUnknown -> interfaceEntries.getValue(primaryInterfaceId).objectMemory
            IID.IInspectable -> primaryInspectableInterfaceId?.let { interfaceEntries.getValue(it).objectMemory }
            else -> interfaceEntries[interfaceId]?.objectMemory
        } ?: throw WinRtUnsupportedOperationException(
            "Managed COM object does not implement interface '$interfaceId'.",
            KnownHResults.E_NOINTERFACE,
        )

    private fun createInterfaceEntry(
        definition: WinRtInspectableInterfaceDefinition,
    ): WinRtInspectableInterfaceEntry {
        val objectMemory = PlatformAbi.allocatePointerSlot(scope)
        val firstCustomSlot = when (definition.baseKind) {
            WinRtComInterfaceBaseKind.IUnknown -> IUnknownVftblSlots.Release + 1
            WinRtComInterfaceBaseKind.IInspectable -> IInspectableVftblSlots.FirstCustom
        }
        val vtableMemory = PlatformAbi.allocatePointerArray(scope, firstCustomSlot + definition.methods.size)
        val queryInterfaceCallback = callbackOf(IUnknownVftbl.QueryInterface) { args ->
            queryInterface(
                requestedInterfaceId = PlatformAbi.readGuid(args[1] as RawAddress),
                resultPointer = args[2] as RawAddress,
            )
        }
        val addRefCallback = callbackOf(IUnknownVftbl.AddRef) { addReference() }
        val releaseCallback = callbackOf(IUnknownVftbl.Release) { releaseReference() }
        val getIidsCallback = callbackOf(IInspectableVftbl.GetIids) { args ->
            getIids(countOut = args[1] as RawAddress, idsOut = args[2] as RawAddress)
        }
        val getRuntimeClassNameCallback = callbackOf(IInspectableVftbl.GetRuntimeClassName) { args ->
            getRuntimeClassName(args[1] as RawAddress)
        }
        val getTrustLevelCallback = callbackOf(IInspectableVftbl.GetTrustLevel) { args ->
            getTrustLevel(args[1] as RawAddress)
        }
        val methodCallbacks = definition.methods.mapIndexed { index, method ->
            callbackOf(method.signature) { args ->
                invokeMethod(
                    interfaceId = definition.interfaceId,
                    methodIndex = index,
                    rawArguments = args.drop(1),
                )
            }
        }
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.Release, releaseCallback.pointer)
        if (definition.baseKind == WinRtComInterfaceBaseKind.IInspectable) {
            PlatformAbi.writePointerAt(vtableMemory, IInspectableVftblSlots.GetIids, getIidsCallback.pointer)
            PlatformAbi.writePointerAt(vtableMemory, IInspectableVftblSlots.GetRuntimeClassName, getRuntimeClassNameCallback.pointer)
            PlatformAbi.writePointerAt(vtableMemory, IInspectableVftblSlots.GetTrustLevel, getTrustLevelCallback.pointer)
        }
        methodCallbacks.forEachIndexed { index, callback ->
            PlatformAbi.writePointerAt(vtableMemory, firstCustomSlot + index, callback.pointer)
        }
        PlatformAbi.writePointer(objectMemory, vtableMemory)
        return WinRtInspectableInterfaceEntry(
            objectMemory = objectMemory,
            callbacks = buildList {
                add(queryInterfaceCallback)
                add(addRefCallback)
                add(releaseCallback)
                if (definition.baseKind == WinRtComInterfaceBaseKind.IInspectable) {
                    add(getIidsCallback)
                    add(getRuntimeClassNameCallback)
                    add(getTrustLevelCallback)
                }
                addAll(methodCallbacks)
            },
        )
    }

    private fun queryInterface(
        requestedInterfaceId: Guid,
        resultPointer: RawAddress,
    ): Int {
        val targetPointer = when (requestedInterfaceId) {
            IID.IUnknown -> interfacePointer(primaryInterfaceId)
            IID.IInspectable -> primaryInspectableInterfaceId?.let(::interfacePointer)
            else -> interfaceEntries[requestedInterfaceId]?.objectMemory
        }
        val queryResult = state.queryInterface(requestedInterfaceId) { _ -> targetPointer }
        PlatformAbi.writePointer(
            resultPointer,
            queryResult.target ?: PlatformAbi.nullPointer,
        )
        return queryResult.hResult.value
    }

    private fun addReference(): Int = state.addReference()

    private fun releaseReference(): Int = state.releaseReference()

    private fun getIids(
        countOut: RawAddress,
        idsOut: RawAddress,
    ): Int {
        PlatformAbi.writeInt32(countOut, interfaces.size)
        PlatformAbi.writePointer(idsOut, interfaceIdsMemory)
        return KnownHResults.S_OK.value
    }

    private fun getRuntimeClassName(resultOut: RawAddress): Int {
        if (runtimeClassName == null) {
            PlatformAbi.writePointer(resultOut, PlatformAbi.nullPointer)
            return KnownHResults.S_OK.value
        }
        PlatformAbi.writePointer(resultOut, HString.create(runtimeClassName).handle)
        return KnownHResults.S_OK.value
    }

    private fun getTrustLevel(resultOut: RawAddress): Int {
        PlatformAbi.writeInt32(resultOut, trustLevel)
        return KnownHResults.S_OK.value
    }

    private fun invokeMethod(
        interfaceId: Guid,
        methodIndex: Int,
        rawArguments: List<Any?>,
    ): Int = runCatching {
        interfaces.getValue(interfaceId).methods[methodIndex].handler(rawArguments)
    }.getOrElse { error ->
        platformSetErrorInfo(error)
        platformHResultFromThrowable(error).value
    }

    private fun cleanup() {
        interfaceEntries.values.forEach { entry ->
            registry.remove(PlatformAbi.pointerKey(entry.objectMemory))
            entry.callbacks.forEach(NativeCallbackHandle::close)
        }
        scope.close()
    }

    private fun allocateInterfaceIds(
        definitions: List<WinRtInspectableInterfaceDefinition>,
    ): RawAddress {
        val memory = PlatformAbi.allocateBytes(scope, definitions.size.toLong() * Guid.BYTE_SIZE)
        definitions.forEachIndexed { index, definition ->
            PlatformAbi.writeGuid(memory, index.toLong() * Guid.BYTE_SIZE, definition.interfaceId)
        }
        return memory
    }

    private fun callbackOf(
        signature: ComMethodSignature,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle = ComAbiInteropBridge.createComMethodCallback(signature, callback)

    private data class WinRtInspectableInterfaceEntry(
        val objectMemory: RawAddress,
        val callbacks: List<NativeCallbackHandle>,
    )

    companion object {
        private val registry = ConcurrentCacheMap<Long, WinRtInspectableComObject>()

        internal fun findManagedValue(pointer: RawAddress): Any? =
            registry[PlatformAbi.pointerKey(pointer)]?.managedValue

        internal fun findInspectableInfo(pointer: RawAddress): WinRtInspectableInfoSnapshot? =
            registry[PlatformAbi.pointerKey(pointer)]?.let { host ->
                WinRtInspectableInfoSnapshot(
                    runtimeClassName = host.runtimeClassName,
                    interfaceIds = host.interfaces.keys.toList(),
                )
            }

        internal fun inspectableBox(
            value: Any?,
            runtimeClassName: String? = null,
        ): WinRtInspectableComObject =
            WinRtInspectableComObject(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = IID.IInspectable,
                        methods = emptyList(),
                    ),
                ),
                runtimeClassName = runtimeClassName,
                managedValue = value,
            )
    }
}
