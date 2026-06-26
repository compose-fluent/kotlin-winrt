package io.github.composefluent.winrt.runtime

data class WinRTInspectableMethodDefinition(
    val signature: ComMethodSignature,
    val handler: (List<Any?>) -> Int,
)

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

internal class WinRTInspectableComObject(
    interfaceDefinitions: List<WinRTInspectableInterfaceDefinition>,
    hiddenInterfaceDefinitions: List<WinRTInspectableInterfaceDefinition> = emptyList(),
    defaultInterfaceId: Guid? = null,
    private val runtimeClassName: String? = null,
    private val trustLevel: Int = 0,
    private val managedValue: Any? = null,
    private val queryInterfaceFallback: ((Guid) -> RawAddress?)? = null,
    private val cleanupAction: (() -> Unit)? = null,
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
        it.baseKind == WinRTComInterfaceBaseKind.IInspectable
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
        val existing = registry[key]
        if (existing != null && existing !== this) {
            return
        }
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
        interfacePointerOrNull(interfaceId) ?: throw WinRTUnsupportedOperationException(
            "Managed COM object does not implement interface '$interfaceId'.",
            KnownHResults.E_NOINTERFACE,
        )

    private fun interfacePointerOrFallback(interfaceId: Guid): RawAddress =
        interfacePointerOrNull(interfaceId)
            ?: queryInterfaceFallback?.invoke(interfaceId)?.takeUnless(PlatformAbi::isNull)?.also(::registerExternalPointerAlias)
            ?: throw WinRTUnsupportedOperationException(
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
        definition: WinRTInspectableInterfaceDefinition,
    ): WinRTInspectableInterfaceEntry {
        val objectMemory = PlatformAbi.allocatePointerSlot(scope)
        val vtableMemory = SharedInspectableVtables.getOrCreate(definition)
        PlatformAbi.writePointer(objectMemory, vtableMemory)
        return WinRTInspectableInterfaceEntry(
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
        cleanupAction?.invoke()
    }

    private data class WinRTInspectableInterfaceEntry(
        val objectMemory: RawAddress,
        val callbacks: List<NativeCallbackHandle>,
    )

    companion object {
        private val registry = ConcurrentCacheMap<Long, WinRTInspectableComObject>()

        internal fun findManagedValue(pointer: RawAddress): Any? =
            registry[PlatformAbi.pointerKey(pointer)]?.managedValue

        internal fun findInspectableInfo(pointer: RawAddress): WinRTInspectableInfoSnapshot? =
            registry[PlatformAbi.pointerKey(pointer)]?.let { host ->
                WinRTInspectableInfoSnapshot(
                    runtimeClassName = host.runtimeClassName,
                    interfaceIds = host.interfaces.keys.toList(),
                )
            }

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

        private fun findHost(pointer: RawAddress): WinRTInspectableComObject? =
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
        val baseKind: WinRTComInterfaceBaseKind,
        val methodSignatures: List<ComMethodSignature>,
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
                )
        }
    }

    private data class SharedMethodCallbackKey(
        val interfaceId: Guid,
        val methodIndex: Int,
        val signature: ComMethodSignature,
    )

}
