package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

data class WinRtCcwDefinition(
    val interfaceDefinitions: List<WinRtInspectableInterfaceDefinition>,
    val defaultInterfaceId: Guid,
    val runtimeClassName: String? = null,
)

class SingleInterfaceOptimizedObject(
    override val primaryTypeHandle: WinRtTypeHandle,
    override val nativeObject: ComObjectReference,
) : IWinRTObject {
    override val hasUnwrappableNativeObject: Boolean
        get() = false
}

object ComWrappersSupport {
    private val typedRcwFactories = ConcurrentCacheMap<WinRtTypeHandle, (IInspectableReference) -> Any>()
    private val runtimeClassFactories = ConcurrentCacheMap<String, (IInspectableReference) -> Any>()
    private val helperTypeRegistry = ConcurrentCacheMap<WinRtTypeHandle, WinRtTypeHandle>()
    private val ccwFactories = ConcurrentCacheMap<KClass<*>, (Any) -> WinRtCcwDefinition>()
    private val rcwCache = WeakValueCache<Long, Any>()
    private val runtimeClassNameLookups = SnapshotList<(KClass<*>) -> String?>()

    init {
        platformEnsureInspectableProjectionInteropRegistered()
    }

    fun registerTypedRcwFactory(
        typeHandle: WinRtTypeHandle,
        factory: (IInspectableReference) -> Any,
    ): Boolean = typedRcwFactories.putIfAbsent(typeHandle, factory) == null

    fun registerRuntimeClassFactory(
        runtimeClassName: String,
        factory: (IInspectableReference) -> Any,
    ): Boolean = runtimeClassFactories.putIfAbsent(runtimeClassName, factory) == null

    fun registerHelperType(
        projectedType: WinRtTypeHandle,
        helperType: WinRtTypeHandle,
    ): Boolean = helperTypeRegistry.putIfAbsent(projectedType, helperType) == null

    fun registerCcwFactory(
        implementationType: KClass<*>,
        factory: (Any) -> WinRtCcwDefinition,
    ): Boolean = ccwFactories.putIfAbsent(implementationType, factory) == null

    fun registerProjectionType(
        type: KClass<*>,
        runtimeClassName: String? = null,
    ) {
        TypeNameSupport.registerProjectionType(type, runtimeClassName)
    }

    fun registerProjectionAssembly(
        vararg projectionTypes: KClass<*>,
    ) {
        TypeNameSupport.registerProjectionAssembly(*projectionTypes)
    }

    fun registerProjectionTypeBaseTypeMapping(
        typeNameToBaseTypeNameMapping: Map<String, String>,
    ) {
        TypeNameSupport.registerProjectionTypeBaseTypeMapping(typeNameToBaseTypeNameMapping)
    }

    fun registerTypeRuntimeClassNameLookup(
        lookup: (KClass<*>) -> String?,
    ) {
        runtimeClassNameLookups.add(lookup)
    }

    fun getInspectableInfo(pointer: RawAddress): WinRtInspectableInfo? =
        WinRtInspectableComObject.findInspectableInfo(pointer)?.let {
            WinRtInspectableInfo(it.runtimeClassName, it.interfaceIds)
        }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findObject(
        pointer: RawAddress,
        expectedType: KClass<T>,
    ): T? {
        val managedValue = WinRtInspectableComObject.findManagedValue(pointer) ?: return null
        if (!expectedType.isInstance(managedValue)) {
            return null
        }
        return managedValue as T
    }

    inline fun <reified T : Any> findObject(pointer: RawAddress): T? = findObject(pointer, T::class)

    fun tryUnwrapObject(
        value: Any?,
        interfaceType: WinRtTypeHandle? = null,
    ): ComObjectReference? =
        WinRtBorrowedReferenceSupport.tryBorrowReference(
            value = value,
            interfaceType = interfaceType,
            unwrapWinRtObject = ::borrowableWinRtObject,
            cloneReference = ::cloneComReference,
        )

    fun createRcwForComObject(
        pointer: RawAddress,
        staticallyDeterminedType: WinRtTypeHandle? = null,
        tryUseCache: Boolean = true,
    ): Any? {
        platformEnsureInspectableProjectionInteropRegistered()
        if (PlatformAbi.isNull(pointer)) {
            return null
        }

        val pointerKey = rcwCacheKey(pointer)
        if (tryUseCache) {
            rcwCache[pointerKey]?.let { cached ->
                val cachedWinRt = cached as? IWinRTObject
                if (cachedWinRt != null && cachedWinRt.nativeObject.isDisposed) {
                    rcwCache.remove(pointerKey)
                    return@let
                }
                if (staticallyDeterminedType == null) {
                    return cached
                }
                if (cachedWinRt != null && cachedWinRt.isInterfaceImplemented(staticallyDeterminedType, false)) {
                    return cached
                }
            }
        }

        val rcw = createRcwCore(pointer, staticallyDeterminedType)
        if (tryUseCache && rcw != null) {
            rcwCache[pointerKey] = rcw
            val directPointerKey = PlatformAbi.pointerKey(pointer)
            if (directPointerKey != pointerKey) {
                rcwCache[directPointerKey] = rcw
            }
        }
        return rcw
    }

    fun createCCWForObject(
        value: Any,
        interfaceId: Guid? = null,
    ): ComObjectReference {
        platformEnsureInspectableProjectionInteropRegistered()
        tryUnwrapObject(value)?.use { unwrapped ->
            return if (interfaceId == null || interfaceId == unwrapped.interfaceId) {
                cloneComReference(unwrapped)
            } else {
                unwrapped.queryInterface(interfaceId).getOrThrow().use(::cloneComReference)
            }
        }

        platformTryCreateProjectedReference(value, interfaceId)?.let { return it }

        val definition = createCcwDefinition(value)
        val host = WinRtInspectableComObject(
            interfaceDefinitions = definition.interfaceDefinitions,
            runtimeClassName = definition.runtimeClassName,
            managedValue = value,
        )
        val requestedInterface = interfaceId ?: definition.defaultInterfaceId
        return ownedReference(host, requestedInterface)
    }

    fun createComposableCCWForObject(
        value: Any,
        outerInterfaceId: Guid? = null,
        createInstance: (baseInterface: RawAddress, innerOut: RawAddress, instanceOut: RawAddress) -> Int,
    ): WinRtComposableObjectReference {
        platformEnsureInspectableProjectionInteropRegistered()
        val definition = createCcwDefinition(value)
        var innerReference: IInspectableReference? = null
        val host = WinRtInspectableComObject(
            interfaceDefinitions = definition.interfaceDefinitions,
            runtimeClassName = definition.runtimeClassName,
            managedValue = value,
            queryInterfaceFallback = { requestedInterfaceId ->
                innerReference
                    ?.tryQueryInterface(requestedInterfaceId)
                    ?.use { queried -> PlatformAbi.fromRawComPtr(queried.getRefPointer()) }
            },
        )
        val requestedInterface = outerInterfaceId ?: definition.defaultInterfaceId
        val outerReference = host.createReference(requestedInterface)
        return try {
            PlatformAbi.confinedScope().use { scope ->
                val innerOut = PlatformAbi.allocatePointerSlot(scope)
                val instanceOut = PlatformAbi.allocatePointerSlot(scope)
                val hResult = createInstance(
                    PlatformAbi.fromRawComPtr(outerReference.pointer),
                    innerOut,
                    instanceOut,
                )
                HResult(hResult).requireSuccess()
                val innerPointer = PlatformAbi.readPointer(innerOut)
                if (!PlatformAbi.isNull(innerPointer)) {
                    innerReference = IInspectableReference(PlatformAbi.toRawComPtr(innerPointer), IID.IInspectable)
                    host.registerExternalPointerAlias(innerPointer)
                }
                val instancePointer = PlatformAbi.readPointer(instanceOut)
                if (PlatformAbi.isNull(instancePointer)) {
                    throw WinRtUnsupportedOperationException(
                        "Composable factory returned a null instance pointer.",
                        KnownHResults.E_POINTER,
                    )
                }
                host.registerExternalPointerAlias(instancePointer)
                val isAggregatedReferenceTrackerObject =
                    !PlatformAbi.isNull(innerPointer) && hasReferenceTracker(innerPointer)
                val instanceReference = IInspectableReference(
                    PlatformAbi.toRawComPtr(instancePointer),
                    IID.IInspectable,
                    preventReleaseOnDispose = isAggregatedReferenceTrackerObject,
                )
                WinRtComposableObjectReference(
                    instance = instanceReference,
                    inner = innerReference,
                    outer = outerReference,
                    isAggregatedReferenceTrackerObject = isAggregatedReferenceTrackerObject,
                    cleanup = host::releaseManagedReference,
                )
            }
        } catch (failure: Throwable) {
            try {
                outerReference.close()
            } finally {
                host.releaseManagedReference()
            }
            throw failure
        }
    }

    fun clearRegistriesForTests() {
        typedRcwFactories.clear()
        runtimeClassFactories.clear()
        helperTypeRegistry.clear()
        ccwFactories.clear()
        rcwCache.clear()
        runtimeClassNameLookups.clear()
        FreeThreadedMarshalerSupport.clearForTests()
        Projections.clearRegistriesForTests()
        TypeNameSupport.clearRegistriesForTests()
        TypeExtensions.clearRegistriesForTests()
        platformEnsureInspectableProjectionInteropRegistered()
    }

    internal fun getRuntimeClassNameForNonWinRTTypeFromLookupTable(
        type: KClass<*>,
    ): String? = runtimeClassNameLookups.firstNotNullOfOrNull { lookup ->
        lookup(type)?.takeIf { it.isNotBlank() }
    }

    private fun createRcwCore(
        pointer: RawAddress,
        staticallyDeterminedType: WinRtTypeHandle?,
    ): Any? {
        val inspectable = wrapInspectable(pointer)
        if (inspectable != null) {
            val runtimeClassName = inspectable.tryGetRuntimeClassName()
            resolveFactory(staticallyDeterminedType, runtimeClassName)?.let { factory ->
                return factory(inspectable)
            }
            if (staticallyDeterminedType == null) {
                platformTryProjectInspectable(inspectable, runtimeClassName)?.let { projectedValue ->
                    inspectable.close()
                    return projectedValue
                }
            }
            if (staticallyDeterminedType == null) {
                return inspectable
            }

            val typedReference = try {
                inspectable.queryInterface(staticallyDeterminedType.interfaceId).getOrThrow()
            } finally {
                inspectable.close()
            }
            return SingleInterfaceOptimizedObject(
                primaryTypeHandle = staticallyDeterminedType,
                nativeObject = typedReference,
            )
        }

        if (staticallyDeterminedType != null) {
            return SingleInterfaceOptimizedObject(
                primaryTypeHandle = staticallyDeterminedType,
                nativeObject = IUnknownReference(pointer.asRawComPtr(), staticallyDeterminedType.interfaceId),
            )
        }

        return inspectable
    }

    private fun resolveFactory(
        staticallyDeterminedType: WinRtTypeHandle?,
        runtimeClassName: String?,
    ): ((IInspectableReference) -> Any)? {
        if (staticallyDeterminedType != null) {
            typedRcwFactories[staticallyDeterminedType]?.let { return it }
            helperTypeRegistry[staticallyDeterminedType]?.let { helper ->
                typedRcwFactories[helper]?.let { return it }
            }
        }
        if (!runtimeClassName.isNullOrBlank()) {
            runtimeClassFactories[runtimeClassName]?.let { return it }
        }
        return null
    }

    private fun hasReferenceTracker(pointer: RawAddress): Boolean {
        val result = WinRtPlatformApi.queryInterfaceRaw(pointer, IID.IReferenceTracker)
        val trackerPointer = result.pointer
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(trackerPointer)) {
            return false
        }
        WinRtPlatformApi.checkSucceededRaw(result.hResultValue)
        WinRtPlatformApi.releaseRaw(trackerPointer)
        return true
    }

    private fun rcwCacheKey(pointer: RawAddress): Long {
        val result = WinRtPlatformApi.queryInterfaceRaw(pointer, IID.IUnknown)
        val unknownPointer = result.pointer
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(unknownPointer)) {
            return PlatformAbi.pointerKey(pointer)
        }
        WinRtPlatformApi.checkSucceededRaw(result.hResultValue)
        return try {
            PlatformAbi.pointerKey(unknownPointer)
        } finally {
            WinRtPlatformApi.releaseRaw(unknownPointer)
        }
    }

    private fun wrapInspectable(pointer: RawAddress): IInspectableReference? {
        val existingInspectable = runCatching {
            IUnknownReference(pointer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true).asInspectable()
        }.getOrNull()
        if (existingInspectable != null) {
            return existingInspectable
        }
        return getInspectableInfo(pointer)?.let {
            if (it.interfaceIds.contains(IID.IInspectable)) {
                IInspectableReference(pointer.asRawComPtr(), IID.IInspectable)
            } else {
                null
            }
        }
    }

    private fun createCcwDefinition(value: Any): WinRtCcwDefinition {
        findCcwFactory(value)?.let { factory ->
            return XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(
                value,
                InteropRuntimeHooks.augmentInspectableDefinition(value, factory(value)),
            )
        }
        platformCreateSyntheticCcwDefinition(value)?.let {
            return XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(
                value,
                InteropRuntimeHooks.augmentInspectableDefinition(value, it),
            )
        }
        return XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(
            value,
            InteropRuntimeHooks.augmentInspectableDefinition(
                value,
                WinRtCcwDefinition(
                    interfaceDefinitions = listOf(
                        WinRtInspectableInterfaceDefinition(
                            interfaceId = IID.IInspectable,
                            methods = emptyList(),
                        ),
                    ),
                    defaultInterfaceId = IID.IInspectable,
                    runtimeClassName = platformRuntimeClassNameFor(value),
                ),
            ),
        )
    }

    private fun findCcwFactory(value: Any): ((Any) -> WinRtCcwDefinition)? {
        ccwFactories[value::class]?.let { return it }
        return ccwFactories.entries.firstOrNull { (type, _) -> type.isInstance(value) }?.value
    }

    private fun ownedReference(
        host: WinRtInspectableComObject,
        interfaceId: Guid,
    ): ComObjectReference =
        ManagedReferenceHostSupport.wrapOwnedReference(
            createReference = { host.createReference(interfaceId) },
            releaseManagedReference = host::releaseManagedReference,
        ) { inner, cleanup ->
            OwnedCcwReference(
                inner = inner,
                cleanup = cleanup,
            )
        }

    private class OwnedCcwReference(
        private val inner: ComObjectReference,
        private val cleanup: () -> Unit,
    ) : ComObjectReference(
        pointer = inner.pointer,
        interfaceId = inner.interfaceId,
        preventReleaseOnDispose = true,
    ) {
        override fun close() {
            try {
                inner.close()
            } finally {
                cleanup()
            }
        }
    }
}
