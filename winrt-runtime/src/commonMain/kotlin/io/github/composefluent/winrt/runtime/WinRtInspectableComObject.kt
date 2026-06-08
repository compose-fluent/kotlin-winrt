package io.github.composefluent.winrt.runtime

data class WinRtInspectableMethodDefinition(
    val signature: ComMethodSignature,
    val handler: (List<Any?>) -> Int,
)

enum class WinRtComInterfaceBaseKind {
    IUnknown,
    IInspectable,
}

data class WinRtInspectableInterfaceDefinition(
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
    hiddenInterfaceDefinitions: List<WinRtInspectableInterfaceDefinition> = emptyList(),
    defaultInterfaceId: Guid? = null,
    private val runtimeClassName: String? = null,
    private val trustLevel: Int = 0,
    private val managedValue: Any? = null,
    private val queryInterfaceFallback: ((Guid) -> RawAddress?)? = null,
) : ManagedReferenceHost, AutoCloseable {
    private val scope = PlatformAbi.sharedScope()
    private val state = ManagedComHostState(::cleanup)
    private val interfaces = interfaceDefinitions.associateBy { it.interfaceId }
    private val allInterfaces = (interfaceDefinitions + hiddenInterfaceDefinitions).associateBy { it.interfaceId }
    private val interfaceEntries = (interfaceDefinitions + hiddenInterfaceDefinitions).associate { definition ->
        definition.interfaceId to createInterfaceEntry(definition)
    }
    private val externalPointerAliases = mutableListOf<Long>()
    private val primaryInspectableInterfaceId = interfaceDefinitions.firstOrNull {
        it.baseKind == WinRtComInterfaceBaseKind.IInspectable
    }?.interfaceId
    private val primaryInterfaceId = defaultInterfaceId
        ?.takeIf { it in interfaces }
        ?: primaryInspectableInterfaceId
        ?: interfaceDefinitions.firstOrNull()?.interfaceId
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
        return ComObjectReference(interfacePointerOrFallback(interfaceId).asRawComPtr(), interfaceId)
    }

    fun createPrimaryReference(): ComObjectReference = createReference(primaryInterfaceId)

    fun registerExternalPointerAlias(pointer: RawAddress) {
        if (PlatformAbi.isNull(pointer)) {
            return
        }
        val key = PlatformAbi.pointerKey(pointer)
        registry[key] = this
        externalPointerAliases.add(key)
    }

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
        interfacePointerOrNull(interfaceId) ?: throw WinRtUnsupportedOperationException(
            "Managed COM object does not implement interface '$interfaceId'.",
            KnownHResults.E_NOINTERFACE,
        )

    private fun interfacePointerOrFallback(interfaceId: Guid): RawAddress =
        interfacePointerOrNull(interfaceId)
            ?: queryInterfaceFallback?.invoke(interfaceId)?.takeUnless(PlatformAbi::isNull)?.also(::registerExternalPointerAlias)
            ?: throw WinRtUnsupportedOperationException(
                "Managed COM object does not implement interface '$interfaceId'.",
                KnownHResults.E_NOINTERFACE,
            )

    private fun interfacePointerOrNull(interfaceId: Guid): RawAddress? =
        when (interfaceId) {
            IID.IUnknown -> interfaceEntries.getValue(primaryInterfaceId).objectMemory
            IID.IInspectable -> interfaceEntries[IID.IInspectable]?.objectMemory
                ?: primaryInspectableInterfaceId?.let { interfaceEntries.getValue(it).objectMemory }
            else -> interfaceEntries[interfaceId]?.objectMemory
        }

    private fun createInterfaceEntry(
        definition: WinRtInspectableInterfaceDefinition,
    ): WinRtInspectableInterfaceEntry {
        val objectMemory = PlatformAbi.allocatePointerSlot(scope)
        val vtableMemory = SharedInspectableVtables.getOrCreate(definition)
        PlatformAbi.writePointer(objectMemory, vtableMemory)
        return WinRtInspectableInterfaceEntry(
            objectMemory = objectMemory,
            callbacks = emptyList(),
        )
    }

    private fun queryInterface(
        requestedInterfaceId: Guid,
        resultPointer: RawAddress,
    ): Int {
        trace("QI request=$requestedInterfaceId runtimeClassName=$runtimeClassName primary=$primaryInterfaceId")
        val targetPointer = when (requestedInterfaceId) {
            IID.IUnknown -> interfacePointer(primaryInterfaceId)
            IID.IInspectable -> interfacePointer(IID.IInspectable)
            else -> interfaceEntries[requestedInterfaceId]?.objectMemory
        }
        if (targetPointer == null) {
            val fallbackPointer = queryInterfaceFallback?.invoke(requestedInterfaceId)
            if (fallbackPointer != null && !PlatformAbi.isNull(fallbackPointer)) {
                registerExternalPointerAlias(fallbackPointer)
                PlatformAbi.writePointer(resultPointer, fallbackPointer)
                trace("QI fallback success request=$requestedInterfaceId pointer=${PlatformAbi.pointerKey(fallbackPointer)}")
                return KnownHResults.S_OK.value
            }
        }
        val queryResult = state.queryInterface(requestedInterfaceId) { _ -> targetPointer }
        PlatformAbi.writePointer(
            resultPointer,
            queryResult.target ?: PlatformAbi.nullPointer,
        )
        trace(
            "QI result request=$requestedInterfaceId hr=${queryResult.hResult.value} " +
                "pointer=${queryResult.target?.let(PlatformAbi::pointerKey) ?: 0L}",
        )
        return queryResult.hResult.value
    }

    private fun addReference(): Int =
        state.addReference().also { count ->
            trace("AddRef runtimeClassName=$runtimeClassName count=$count")
        }

    private fun releaseReference(): Int =
        state.releaseReference().also { count ->
            trace("Release runtimeClassName=$runtimeClassName count=$count")
        }

    private fun getIids(
        countOut: RawAddress,
        idsOut: RawAddress,
    ): Int {
        val interfaceIds = interfaces.keys.toList()
        trace("GetIids count=${interfaceIds.size} ids=${interfaceIds.joinToString()}")
        val memory = WinRtPlatformApi.coTaskMemAllocRaw(interfaceIds.size.toLong() * Guid.BYTE_SIZE)
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
        trace("GetRuntimeClassName runtimeClassName=$runtimeClassName")
        PlatformAbi.writePointer(resultOut, HString.create(runtimeClassName.orEmpty()).handle)
        return KnownHResults.S_OK.value
    }

    private fun getTrustLevel(resultOut: RawAddress): Int {
        trace("GetTrustLevel trustLevel=$trustLevel")
        PlatformAbi.writeInt32(resultOut, trustLevel)
        return KnownHResults.S_OK.value
    }

    private fun trace(message: String) {
        if (FeatureSwitches.traceCcw) {
            println("winrt-ccw: $message")
        }
    }

    private fun invokeMethod(
        interfaceId: Guid,
        methodIndex: Int,
        rawArguments: List<Any?>,
    ): Int = runCatching {
        trace("Invoke interface=$interfaceId methodIndex=$methodIndex runtimeClassName=$runtimeClassName")
        if (interfaceId == IID.IReferenceTrackerTarget) {
            when (methodIndex) {
                0 -> return@runCatching state.addTrackerReference()
                1 -> return@runCatching state.releaseTrackerReference()
            }
        }
        allInterfaces.getValue(interfaceId).methods[methodIndex].handler(rawArguments)
    }.getOrElse { error ->
        platformSetErrorInfo(error)
        platformHResultFromThrowable(error).value
    }

    private fun cleanup() {
        externalPointerAliases.forEach { key ->
            if (registry[key] === this) {
                registry.remove(key)
            }
        }
        interfaceEntries.values.forEach { entry ->
            registry.remove(PlatformAbi.pointerKey(entry.objectMemory))
            entry.callbacks.forEach(NativeCallbackHandle::close)
        }
        scope.close()
    }

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
                defaultInterfaceId = IID.IInspectable,
                runtimeClassName = runtimeClassName,
                managedValue = value,
            )

        private fun findHost(pointer: RawAddress): WinRtInspectableComObject? =
            registry[PlatformAbi.pointerKey(pointer)]
    }

    private object SharedInspectableVtables {
        private val scope = PlatformAbi.sharedScope()
        private val vtables = ConcurrentCacheMap<SharedVtableKey, RawAddress>()
        private val methodCallbacks = ConcurrentCacheMap<SharedMethodCallbackKey, NativeCallbackHandle>()

        private val queryInterfaceCallback =
            callbackOf(IUnknownVftbl.QueryInterface) { args ->
                findHost(args[0] as RawAddress)?.queryInterface(
                    requestedInterfaceId = PlatformAbi.readGuid(args[1] as RawAddress),
                    resultPointer = args[2] as RawAddress,
                ) ?: KnownHResults.E_POINTER.value
            }
        private val addRefCallback =
            callbackOf(IUnknownVftbl.AddRef) { args ->
                findHost(args[0] as RawAddress)?.addReference() ?: 0
            }
        private val releaseCallback =
            callbackOf(IUnknownVftbl.Release) { args ->
                findHost(args[0] as RawAddress)?.releaseReference() ?: 0
            }
        private val getIidsCallback =
            callbackOf(IInspectableVftbl.GetIids) { args ->
                findHost(args[0] as RawAddress)?.getIids(
                    countOut = args[1] as RawAddress,
                    idsOut = args[2] as RawAddress,
                ) ?: KnownHResults.E_POINTER.value
            }
        private val getRuntimeClassNameCallback =
            callbackOf(IInspectableVftbl.GetRuntimeClassName) { args ->
                findHost(args[0] as RawAddress)?.getRuntimeClassName(args[1] as RawAddress)
                    ?: KnownHResults.E_POINTER.value
            }
        private val getTrustLevelCallback =
            callbackOf(IInspectableVftbl.GetTrustLevel) { args ->
                findHost(args[0] as RawAddress)?.getTrustLevel(args[1] as RawAddress)
                    ?: KnownHResults.E_POINTER.value
            }

        fun getOrCreate(definition: WinRtInspectableInterfaceDefinition): RawAddress =
            vtables.computeIfAbsent(SharedVtableKey.from(definition)) { key ->
                val vtable = PlatformAbi.allocatePointerArray(scope, key.firstCustomSlot + key.methodSignatures.size)
                PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
                PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
                PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
                if (key.baseKind == WinRtComInterfaceBaseKind.IInspectable) {
                    PlatformAbi.writePointerAt(vtable, IInspectableVftblSlots.GetIids, getIidsCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, IInspectableVftblSlots.GetRuntimeClassName, getRuntimeClassNameCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, IInspectableVftblSlots.GetTrustLevel, getTrustLevelCallback.pointer)
                }
                key.methodSignatures.forEachIndexed { index, signature ->
                    val methodKey = SharedMethodCallbackKey(key.interfaceId, index, signature)
                    PlatformAbi.writePointerAt(vtable, key.firstCustomSlot + index, methodCallback(methodKey).pointer)
                }
                vtable
            }

        private fun methodCallback(key: SharedMethodCallbackKey): NativeCallbackHandle =
            methodCallbacks.computeIfAbsent(key) {
                callbackOf(key.signature) { args ->
                    findHost(args[0] as RawAddress)?.invokeMethod(
                        interfaceId = key.interfaceId,
                        methodIndex = key.methodIndex,
                        rawArguments = args.drop(1),
                    ) ?: KnownHResults.E_POINTER.value
                }
            }

        private fun callbackOf(
            signature: ComMethodSignature,
            callback: (List<Any?>) -> Int,
        ): NativeCallbackHandle = ComAbiInteropBridge.createComMethodCallback(signature, callback)
    }

    private data class SharedVtableKey(
        val interfaceId: Guid,
        val baseKind: WinRtComInterfaceBaseKind,
        val methodSignatures: List<ComMethodSignature>,
    ) {
        val firstCustomSlot: Int
            get() = when (baseKind) {
                WinRtComInterfaceBaseKind.IUnknown -> IUnknownVftblSlots.Release + 1
                WinRtComInterfaceBaseKind.IInspectable -> IInspectableVftblSlots.FirstCustom
            }

        companion object {
            fun from(definition: WinRtInspectableInterfaceDefinition): SharedVtableKey =
                SharedVtableKey(
                    interfaceId = definition.interfaceId,
                    baseKind = definition.baseKind,
                    methodSignatures = definition.methods.map { method -> method.signature },
                )
        }
    }

    private data class SharedMethodCallbackKey(
        val interfaceId: Guid,
        val methodIndex: Int,
        val signature: ComMethodSignature,
    )

}
