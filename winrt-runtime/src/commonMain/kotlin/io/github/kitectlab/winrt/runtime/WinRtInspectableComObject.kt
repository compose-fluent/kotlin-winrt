package io.github.kitectlab.winrt.runtime

internal data class WinRtInspectableMethodDefinition(
    val descriptor: NativeFunctionDescriptor,
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
    private val scope = NativeInterop.sharedScope()
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
            registry[NativeInterop.pointerKey(entry.objectMemory)] = this
        }
    }

    override fun close() {
        releaseManagedReference()
    }

    override fun createReference(interfaceId: Guid): ComObjectReference {
        addReference()
        return ComObjectReference(interfacePointer(interfaceId), interfaceId)
    }

    fun createPrimaryReference(): ComObjectReference = createReference(primaryInterfaceId)

    fun detachReference(interfaceId: Guid = primaryInterfaceId): NativePointer =
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

    private fun interfacePointer(interfaceId: Guid): NativePointer =
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
        val objectMemory = NativeInterop.allocatePointerSlot(scope)
        val firstCustomSlot = when (definition.baseKind) {
            WinRtComInterfaceBaseKind.IUnknown -> IUnknownVftblSlots.Release + 1
            WinRtComInterfaceBaseKind.IInspectable -> IInspectableVftblSlots.FirstCustom
        }
        val vtableMemory = NativeInterop.allocatePointerArray(scope, firstCustomSlot + definition.methods.size)
        val queryInterfaceCallback = callbackOf(
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            queryInterface(
                requestedInterfaceId = NativeInterop.readGuid(args[1] as NativePointer),
                resultPointer = args[2] as NativePointer,
            )
        }
        val addRefCallback = callbackOf(
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
            ),
        ) { addReference() }
        val releaseCallback = callbackOf(
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
            ),
        ) { releaseReference() }
        val getIidsCallback = callbackOf(
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            getIids(countOut = args[1] as NativePointer, idsOut = args[2] as NativePointer)
        }
        val getRuntimeClassNameCallback = callbackOf(
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            getRuntimeClassName(args[1] as NativePointer)
        }
        val getTrustLevelCallback = callbackOf(
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            getTrustLevel(args[1] as NativePointer)
        }
        val methodCallbacks = definition.methods.mapIndexed { index, method ->
            callbackOf(method.descriptor) { args ->
                invokeMethod(
                    interfaceId = definition.interfaceId,
                    methodIndex = index,
                    rawArguments = args.drop(1),
                )
            }
        }
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.Release, releaseCallback.pointer)
        if (definition.baseKind == WinRtComInterfaceBaseKind.IInspectable) {
            NativeInterop.writePointerAt(vtableMemory, IInspectableVftblSlots.GetIids, getIidsCallback.pointer)
            NativeInterop.writePointerAt(vtableMemory, IInspectableVftblSlots.GetRuntimeClassName, getRuntimeClassNameCallback.pointer)
            NativeInterop.writePointerAt(vtableMemory, IInspectableVftblSlots.GetTrustLevel, getTrustLevelCallback.pointer)
        }
        methodCallbacks.forEachIndexed { index, callback ->
            NativeInterop.writePointerAt(vtableMemory, firstCustomSlot + index, callback.pointer)
        }
        NativeInterop.writePointer(objectMemory, vtableMemory)
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
        resultPointer: NativePointer,
    ): Int {
        val targetPointer = when (requestedInterfaceId) {
            IID.IUnknown -> interfacePointer(primaryInterfaceId)
            IID.IInspectable -> primaryInspectableInterfaceId?.let(::interfacePointer)
            else -> interfaceEntries[requestedInterfaceId]?.objectMemory
        }
        val queryResult = state.queryInterface(requestedInterfaceId) { _ -> targetPointer }
        NativeInterop.writePointer(
            resultPointer,
            queryResult.target ?: NativeInterop.nullPointer,
        )
        return queryResult.hResult.value
    }

    private fun addReference(): Int = state.addReference()

    private fun releaseReference(): Int = state.releaseReference()

    private fun getIids(
        countOut: NativePointer,
        idsOut: NativePointer,
    ): Int {
        NativeInterop.writeInt32(countOut, interfaces.size)
        NativeInterop.writePointer(idsOut, interfaceIdsMemory)
        return KnownHResults.S_OK.value
    }

    private fun getRuntimeClassName(resultOut: NativePointer): Int {
        if (runtimeClassName == null) {
            NativeInterop.writePointer(resultOut, NativeInterop.nullPointer)
            return KnownHResults.S_OK.value
        }
        NativeInterop.writePointer(resultOut, HString.create(runtimeClassName).handle)
        return KnownHResults.S_OK.value
    }

    private fun getTrustLevel(resultOut: NativePointer): Int {
        NativeInterop.writeInt32(resultOut, trustLevel)
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
            registry.remove(NativeInterop.pointerKey(entry.objectMemory))
            entry.callbacks.forEach(NativeCallbackHandle::close)
        }
        scope.close()
    }

    private fun allocateInterfaceIds(
        definitions: List<WinRtInspectableInterfaceDefinition>,
    ): NativePointer {
        val memory = NativeInterop.allocateBytes(scope, definitions.size.toLong() * Guid.BYTE_SIZE)
        definitions.forEachIndexed { index, definition ->
            NativeInterop.writeGuid(memory, index.toLong() * Guid.BYTE_SIZE, definition.interfaceId)
        }
        return memory
    }

    private fun callbackOf(
        descriptor: NativeFunctionDescriptor,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle = NativeInterop.createCallback(descriptor, callback)

    private data class WinRtInspectableInterfaceEntry(
        val objectMemory: NativePointer,
        val callbacks: List<NativeCallbackHandle>,
    )

    companion object {
        private val registry = ConcurrentCacheMap<Long, WinRtInspectableComObject>()

        internal fun findManagedValue(pointer: NativePointer): Any? =
            registry[NativeInterop.pointerKey(pointer)]?.managedValue

        internal fun findInspectableInfo(pointer: NativePointer): WinRtInspectableInfoSnapshot? =
            registry[NativeInterop.pointerKey(pointer)]?.let { host ->
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
