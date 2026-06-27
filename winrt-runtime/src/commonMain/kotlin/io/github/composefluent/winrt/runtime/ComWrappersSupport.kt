package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

data class WinRTCcwDefinition(
    val interfaceDefinitions: List<WinRTInspectableInterfaceDefinition>,
    val defaultInterfaceId: Guid,
    val runtimeClassName: String? = null,
    val hiddenInterfaceDefinitions: List<WinRTInspectableInterfaceDefinition> = emptyList(),
    val queryInterfaceFallback: ((Any, Guid) -> RawAddress?)? = null,
)

class SingleInterfaceOptimizedObject(
    override val primaryTypeHandle: WinRTTypeHandle,
    override val nativeObject: ComObjectReference,
) : IWinRTObject {
    override val hasUnwrappableNativeObject: Boolean
        get() = false
}

object ComWrappersSupport {
    private val ccwHostCache = WeakKeyStateMap<Any, CachedCcwHost>()
    private val rcwCache = WeakValueCache<Long, Any>()

    init {
        platformEnsureInspectableProjectionInteropRegistered()
    }

    fun registerTypedRcwFactory(
        typeHandle: WinRTTypeHandle,
        factory: (IInspectableReference) -> Any,
    ): Boolean = RcwProjectionFactoryRegistry.registerTypedRcwFactory(typeHandle, factory)

    fun registerRuntimeClassFactory(
        runtimeClassName: String,
        factory: (IInspectableReference) -> Any,
    ): Boolean = RcwProjectionFactoryRegistry.registerRuntimeClassFactory(runtimeClassName, factory)

    fun registerInterfaceProjectionFactory(
        typeHandle: WinRTTypeHandle,
        factory: (IUnknownReference) -> Any,
    ): Boolean = RcwProjectionFactoryRegistry.registerInterfaceProjectionFactory(typeHandle, factory)

    fun registerInterfaceProjectionFactory(
        projectedTypeName: String,
        factory: (IUnknownReference) -> Any,
    ): Boolean = RcwProjectionFactoryRegistry.registerInterfaceProjectionFactory(projectedTypeName, factory)

    fun wrapGeneratedInterfaceProjection(
        typeHandle: WinRTTypeHandle,
        instance: IUnknownReference,
    ): Any =
        RcwProjectionFactoryRegistry.resolveInterfaceProjectionFactory(typeHandle, typeHandle.projectedTypeName)
            ?.invoke(instance)
            ?: throw WinRTUnsupportedOperationException(
                "Generated interface projection factory for '${typeHandle.projectedTypeName}' is not registered.",
                KnownHResults.E_NOINTERFACE,
            )

    fun wrapGeneratedInterfaceProjection(
        projectedTypeName: String,
        instance: IUnknownReference,
    ): Any =
        RcwProjectionFactoryRegistry.resolveInterfaceProjectionFactory(null, projectedTypeName)
            ?.invoke(instance)
            ?: throw WinRTUnsupportedOperationException(
                "Generated interface projection factory for '$projectedTypeName' is not registered.",
                KnownHResults.E_NOINTERFACE,
            )

    fun registerAuthoringActivationFactory(
        runtimeClassName: String,
        factory: () -> ComObjectReference,
    ): Boolean = AuthoringActivationFactoryRegistry.registerFactory(runtimeClassName, factory)

    fun tryGetAuthoringActivationFactory(
        runtimeClassName: String,
        interfaceId: Guid,
    ): ActivationResult = AuthoringActivationFactoryRegistry.tryGetFactory(runtimeClassName, interfaceId)

    fun registerAuthoringActivationFactoryFallback(
        lookup: (runtimeClassName: String, interfaceId: Guid) -> ActivationResult,
    ) {
        AuthoringActivationFactoryRegistry.registerFallback(lookup)
    }

    fun tryGetAuthoringActivationFactoryFallback(
        runtimeClassName: String,
        interfaceId: Guid,
    ): ActivationResult = AuthoringActivationFactoryRegistry.tryGetFallback(runtimeClassName, interfaceId)

    fun clearAuthoringActivationFactoryFallbacksForTests() {
        AuthoringActivationFactoryRegistry.clearFallbacksForTests()
    }

    fun registerHelperType(
        projectedType: WinRTTypeHandle,
        helperType: WinRTTypeHandle,
    ): Boolean = RcwProjectionFactoryRegistry.registerHelperType(projectedType, helperType)

    fun registerCcwFactory(
        implementationType: KClass<*>,
        factory: (Any) -> WinRTCcwDefinition,
    ): Boolean = CcwFactoryRegistry.registerFactory(implementationType, factory)

    fun registerAuthoringTypeDetailsFactory(
        implementationType: KClass<*>,
        factory: (Any) -> WinRTCcwDefinition,
    ): Boolean = registerCcwFactory(implementationType, factory)

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
        RuntimeTypeLookupRegistry.registerRuntimeClassNameLookup(lookup)
    }

    fun registerAuthoringMetadataTypeLookup(
        lookup: (String) -> String?,
    ) {
        RuntimeTypeLookupRegistry.registerAuthoringMetadataTypeLookup(lookup)
    }

    fun registerAuthoringMetadataTypeMappings(
        mappings: Map<String, String>,
    ) {
        RuntimeTypeLookupRegistry.registerAuthoringMetadataTypeMappings(mappings)
    }

    fun getAuthoringMetadataTypeName(projectedTypeName: String): String? =
        RuntimeTypeLookupRegistry.getAuthoringMetadataTypeName(projectedTypeName)

    fun getInspectableInfo(pointer: RawAddress): WinRTInspectableInfo? =
        WinRTInspectableComObject.findInspectableInfo(pointer)?.let {
            WinRTInspectableInfo(it.runtimeClassName, it.interfaceIds)
        }

    internal fun clearRuntimeCache() {
        rcwCache.clear()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findObject(
        pointer: RawAddress,
        expectedType: KClass<T>,
    ): T? {
        val managedValue = rcwCache[PlatformAbi.pointerKey(pointer)]
            ?: WinRTInspectableComObject.findManagedValue(pointer)
            ?: return null
        if (!expectedType.isInstance(managedValue)) {
            return null
        }
        return managedValue as T
    }

    inline fun <reified T : Any> findObject(pointer: RawAddress): T? = findObject(pointer, T::class)

    fun tryUnwrapObject(
        value: Any?,
        interfaceType: WinRTTypeHandle? = null,
    ): ComObjectReference? {
        if (value is ComObjectReference) {
            return if (interfaceType == null || interfaceType.interfaceId == value.interfaceId) {
                cloneComReference(value)
            } else {
                value.tryQueryInterface(interfaceType.interfaceId)
            }
        }
        return WinRTBorrowedReferenceSupport.tryBorrowReference(
            value = value,
            interfaceType = interfaceType,
            unwrapWinRTObject = ::borrowableWinRTObject,
            cloneReference = ::cloneComReference,
        )
    }

    fun createRcwForComObject(
        pointer: RawAddress,
        staticallyDeterminedType: WinRTTypeHandle? = null,
        tryUseCache: Boolean = true,
    ): Any? {
        platformEnsureInspectableProjectionInteropRegistered()
        if (PlatformAbi.isNull(pointer)) {
            return null
        }

        val pointerKey = rcwCacheKey(pointer)
        if (tryUseCache) {
            rcwCache[pointerKey]?.let { cached ->
                val cachedWinRT = cached as? IWinRTObject
                if (cachedWinRT != null && cachedWinRT.nativeObject.isDisposed) {
                    rcwCache.remove(pointerKey)
                    return@let
                }
                if (staticallyDeterminedType == null) {
                    return cached
                }
                if (cachedWinRT != null && cachedWinRT.isInterfaceImplemented(staticallyDeterminedType, false)) {
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

    internal fun registerObjectForComInterface(
        value: Any,
        pointer: RawAddress,
    ) {
        if (PlatformAbi.isNull(pointer)) {
            return
        }
        rcwCache[PlatformAbi.pointerKey(pointer)] = value
        rcwCache[rcwCacheKey(pointer)] = value
    }

    fun initializeComposableReference(instance: IInspectableReference): IInspectableReference =
        instance.also { it.tryInitializeReferenceTracker(addRefFromTrackerSource = false) }

    fun initializeComposableReference(
        instance: IUnknownReference,
        defaultInterfaceId: Guid,
    ): IInspectableReference =
        IInspectableReference(instance.getRefPointer(), defaultInterfaceId)
            .also { it.tryInitializeReferenceTracker(addRefFromTrackerSource = false) }

    fun registerComposableWrapper(
        value: Any,
        instance: IInspectableReference,
    ) {
        registerObjectForComInterface(value, PlatformAbi.fromRawComPtr(instance.pointer))
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

        tryCreateComposableCCWForObject(value, interfaceId)?.let { return it }

        val cachedHost = ccwHostCache.getOrPut(value) {
            createCachedCcwHost(value)
        }
        val requestedInterface = interfaceId ?: cachedHost.defaultInterfaceId
        return cachedHost.createReference(requestedInterface)
    }

    fun createCCWForActivationFactory(
        factory: WinRTActivationFactory,
        factoryInterfaces: List<WinRTInspectableInterfaceDefinition> = emptyList(),
        interfaceId: Guid = IID.IActivationFactory,
    ): ComObjectReference {
        val definition = WinRTActivationFactorySupport.createCcwDefinition(factory, factoryInterfaces)
        val host = WinRTInspectableComObject(
            interfaceDefinitions = definition.interfaceDefinitions,
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
            defaultInterfaceId = definition.defaultInterfaceId,
            runtimeClassName = definition.runtimeClassName,
            managedValue = factory,
            queryInterfaceFallback = null,
        )
        return CachedCcwHost(host, definition.defaultInterfaceId).createReference(interfaceId)
    }

    private fun createCachedCcwHost(value: Any): CachedCcwHost {
        val definition = createCcwDefinition(value)
        val composableInnerReference = (value as? WinRTComposableObject)
            ?.winRTComposableObjectReference
            ?.inner
        val host = WinRTInspectableComObject(
            interfaceDefinitions = definition.interfaceDefinitions,
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
            defaultInterfaceId = definition.defaultInterfaceId,
            runtimeClassName = definition.runtimeClassName,
            managedValue = value,
            queryInterfaceFallback = if (definition.queryInterfaceFallback != null || composableInnerReference != null) {
                { requestedInterfaceId ->
                    definition.queryInterfaceFallback
                        ?.invoke(value, requestedInterfaceId)
                        ?.takeUnless(PlatformAbi::isNull)
                        ?: composableInnerReference
                            ?.let { queryInterfacePointerForAbi(it, requestedInterfaceId) }
                }
            } else {
                null
            },
            cleanupAction = {
                ccwHostCache.remove(value)
            },
        )
        return CachedCcwHost(host, definition.defaultInterfaceId)
    }

    private fun tryCreateComposableCCWForObject(
        value: Any,
        interfaceId: Guid?,
    ): ComObjectReference? {
        val composableReference = (value as? WinRTComposableObject)
            ?.winRTComposableObjectReference
            ?: return null
        val outerReference = composableReference.outer
        traceCcw(
            "create composable CCW value=${value::class.qualifiedName} requested=$interfaceId " +
                "outer=${outerReference.interfaceId} instance=${composableReference.instance.interfaceId}",
        )
        return if (interfaceId == null || interfaceId == outerReference.interfaceId) {
            traceCcw("create composable CCW using outer")
            cloneComReference(outerReference)
        } else if (interfaceId == IID.IUnknown || interfaceId == IID.IInspectable) {
            traceCcw("create composable CCW querying outer for $interfaceId")
            outerReference.queryInterface(interfaceId).getOrThrow()
        } else {
            traceCcw("create composable CCW querying outer custom QI for $interfaceId")
            outerReference.queryInterface(interfaceId).getOrThrow()
        }
    }

    fun detachCCWForObject(
        value: Any?,
        interfaceId: Guid? = null,
    ): RawAddress {
        if (value == null) {
            return PlatformAbi.nullPointer
        }
        val winRTObject = value as? IWinRTObject
        if (winRTObject != null && winRTObject.hasUnwrappableNativeObject) {
            val nativeObject = winRTObject.nativeObject
            if (interfaceId == null || interfaceId == nativeObject.interfaceId) {
                return PlatformAbi.fromRawComPtr(nativeObject.getRefPointer())
            }
            return nativeObject.queryInterface(interfaceId).getOrThrow().useAndGetRef()
        }
        detachComposableCCWForObject(value, interfaceId)?.let { return it }
        val reference = createCCWForObject(value, interfaceId)
        traceCcw(
            "detach CCW value=${value::class.qualifiedName} requested=$interfaceId " +
                "reference=${reference.interfaceId} aggregated=${reference.isAggregated}",
        )
        return try {
            PlatformAbi.fromRawComPtr(reference.getRefPointer())
        } finally {
            reference.close()
        }
    }

    private fun detachComposableCCWForObject(
        value: Any,
        interfaceId: Guid?,
    ): RawAddress? {
        val composableReference = (value as? WinRTComposableObject)
            ?.winRTComposableObjectReference
            ?: return null
        val outerReference = composableReference.outer
        val detachedPointer = if (interfaceId == null || interfaceId == outerReference.interfaceId) {
            PlatformAbi.fromRawComPtr(outerReference.getRefPointer())
        } else if (interfaceId == IID.IUnknown || interfaceId == IID.IInspectable) {
            queryInterfacePointerForAbi(outerReference, interfaceId)
        } else {
            queryInterfacePointerForAbi(outerReference, interfaceId)
        } ?: throw WinRTUnsupportedOperationException(
            "Composable CCW does not implement interface '$interfaceId'.",
            KnownHResults.E_NOINTERFACE,
        )
        traceCcw(
            "detach composable CCW value=${value::class.qualifiedName} requested=$interfaceId " +
                "pointer=${PlatformAbi.pointerKey(detachedPointer)}",
        )
        return detachedPointer
    }

    fun createComposableCCWForObject(
        value: Any,
        outerInterfaceId: Guid? = null,
        createInstance: (baseInterface: RawAddress, innerOut: RawAddress, instanceOut: RawAddress) -> Int,
    ): WinRTComposableObjectReference =
        createComposableCCWForObject(
            value = value,
            outerInterfaceId = outerInterfaceId,
            instanceInterfaceId = null,
            createInstance = createInstance,
        )

    fun createComposableCCWForObject(
        value: Any,
        outerInterfaceId: Guid?,
        instanceInterfaceId: Guid? = null,
        createInstance: (baseInterface: RawAddress, innerOut: RawAddress, instanceOut: RawAddress) -> Int,
    ): WinRTComposableObjectReference {
        platformEnsureInspectableProjectionInteropRegistered()
        val definition = createCcwDefinition(value)
        var innerReference: IInspectableReference? = null
        lateinit var host: WinRTInspectableComObject
        host = WinRTInspectableComObject(
            interfaceDefinitions = definition.interfaceDefinitions,
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
            defaultInterfaceId = definition.defaultInterfaceId,
            runtimeClassName = definition.runtimeClassName,
            managedValue = value,
            queryInterfaceFallback = { requestedInterfaceId ->
                    definition.queryInterfaceFallback
                        ?.invoke(value, requestedInterfaceId)
                        ?.takeUnless(PlatformAbi::isNull)
                        ?: innerReference
                    ?.let { queryInterfacePointerForAbi(it, requestedInterfaceId) }
            },
        )
        val isAggregation = outerInterfaceId != null
        var outerReference: ComObjectReference? = null
        return try {
            PlatformAbi.confinedScope().use { scope ->
                val innerOut = PlatformAbi.allocatePointerSlot(scope)
                val instanceOut = PlatformAbi.allocatePointerSlot(scope)
                val baseInspectable = host.createReference(IID.IInspectable)
                val hResult = try {
                    createInstance(
                        PlatformAbi.fromRawComPtr(baseInspectable.pointer),
                        innerOut,
                        instanceOut,
                    )
                } finally {
                    baseInspectable.close()
                }
                HResult(hResult).requireSuccess()
                val innerPointer = PlatformAbi.readPointer(innerOut)
                if (!PlatformAbi.isNull(innerPointer)) {
                    host.registerExternalPointerAlias(innerPointer)
                    registerObjectForComInterface(value, innerPointer)
                }
                val instancePointer = PlatformAbi.readPointer(instanceOut)
                if (PlatformAbi.isNull(instancePointer)) {
                    throw WinRTUnsupportedOperationException(
                        "Composable factory returned a null instance pointer.",
                        KnownHResults.E_POINTER,
                    )
                }
                host.registerExternalPointerAlias(instancePointer)
                registerObjectForComInterface(value, instancePointer)
                val requestedInstanceInterfaceId = instanceInterfaceId ?: definition.defaultInterfaceId
                val projectedInstancePointer =
                    if (isAggregation) {
                        instancePointer
                    } else {
                        queryInterfacePointerForComposableInstance(instancePointer, requestedInstanceInterfaceId)
                    }
                if (projectedInstancePointer != instancePointer) {
                    host.registerExternalPointerAlias(projectedInstancePointer)
                    registerObjectForComInterface(value, projectedInstancePointer)
                }
                val referenceTrackerProbePointer =
                    if (!PlatformAbi.isNull(innerPointer)) innerPointer else projectedInstancePointer
                val isReferenceTrackerObject = hasReferenceTracker(referenceTrackerProbePointer)
                val isAggregatedReferenceTrackerObject =
                    !PlatformAbi.isNull(innerPointer) && isReferenceTrackerObject
                innerReference = if (PlatformAbi.isNull(innerPointer)) {
                    null
                } else {
                    IInspectableReference(
                        PlatformAbi.toRawComPtr(innerPointer),
                        IID.IInspectable,
                        preventReleaseOnDispose = isAggregatedReferenceTrackerObject,
                        isAggregated = isAggregation,
                    )
                }
                outerReference = host.createReference(definition.defaultInterfaceId)
                val composedReference = try {
                    val reference = IInspectableReference(
                        PlatformAbi.toRawComPtr(projectedInstancePointer),
                        requestedInstanceInterfaceId,
                        preventReleaseOnDispose = isAggregation || isAggregatedReferenceTrackerObject,
                    )
                    try {
                        if (!isAggregation && isReferenceTrackerObject) {
                            reference.tryInitializeReferenceTracker(addRefFromTrackerSource = false)
                        }
                        reference
                    } catch (failure: Throwable) {
                        reference.close()
                        throw failure
                    }
                } finally {
                    if (!isAggregation) {
                        WinRTPlatformApi.releaseRaw(instancePointer)
                    }
                }
                val projectedReference = if (isAggregation) {
                    requireNotNull(innerReference) {
                        "Composable aggregation requires the factory to return a non-null inner pointer."
                    }
                } else {
                    composedReference
                }
                WinRTComposableObjectReference(
                    instance = projectedReference,
                    inner = innerReference,
                    composed = composedReference.takeUnless { it === projectedReference },
                    outer = requireNotNull(outerReference),
                    isAggregatedReferenceTrackerObject = isAggregatedReferenceTrackerObject,
                    cleanup = host::releaseManagedReference,
                )
            }
        } catch (failure: Throwable) {
            outerReference?.close()
            host.releaseManagedReference()
            throw failure
        }
    }

    fun clearRegistriesForTests() {
        ccwHostCache.clear()
        rcwCache.clear()
        RuntimeRegistryResetSupport.clearForTests()
    }

    internal fun getRuntimeClassNameForNonWinRTTypeFromLookupTable(
        type: KClass<*>,
    ): String? = RuntimeTypeLookupRegistry.getRuntimeClassNameForNonWinRTType(type)

    private fun createRcwCore(
        pointer: RawAddress,
        staticallyDeterminedType: WinRTTypeHandle?,
    ): Any? {
        val inspectable = wrapInspectable(pointer)
        if (inspectable != null) {
            val runtimeClassName = inspectable.tryGetRuntimeClassName()
            RcwProjectionFactoryRegistry.resolveRuntimeClassFactory(staticallyDeterminedType, runtimeClassName)?.let { factory ->
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
            RcwProjectionFactoryRegistry.resolveInterfaceProjectionFactory(
                staticallyDeterminedType,
                staticallyDeterminedType.projectedTypeName,
            )?.let { factory ->
                return factory(typedReference.asUnknownReference(staticallyDeterminedType.interfaceId))
            }
            return SingleInterfaceOptimizedObject(
                primaryTypeHandle = staticallyDeterminedType,
                nativeObject = typedReference,
            )
        }

        if (staticallyDeterminedType != null) {
            val typedReference =
                IUnknownReference(ComPtr.create(pointer.asRawComPtr(), staticallyDeterminedType.interfaceId))
            RcwProjectionFactoryRegistry.resolveInterfaceProjectionFactory(
                staticallyDeterminedType,
                staticallyDeterminedType.projectedTypeName,
            )?.let { factory ->
                return factory(typedReference)
            }
            return SingleInterfaceOptimizedObject(
                primaryTypeHandle = staticallyDeterminedType,
                nativeObject = typedReference,
            )
        }

        return inspectable
    }

    private fun ComObjectReference.asUnknownReference(interfaceId: Guid): IUnknownReference =
        this as? IUnknownReference ?: try {
            IUnknownReference(getRefPointer(), interfaceId)
        } finally {
            close()
        }

    private fun hasReferenceTracker(pointer: RawAddress): Boolean {
        val result = WinRTPlatformApi.queryInterfaceRaw(pointer, IID.IReferenceTracker)
        val trackerPointer = result.pointer
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(trackerPointer)) {
            return false
        }
        WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
        WinRTPlatformApi.releaseRaw(trackerPointer)
        return true
    }

    private fun queryInterfacePointerForAbi(
        reference: ComObjectReference,
        requestedInterfaceId: Guid,
    ): RawAddress? {
        // Mirrors CsWinRT IObjectReference.TryAs(Guid, out IntPtr) used from ICustomQueryInterface:
        // the QI result is an ABI-owned pointer and must not take the aggregated As<T>() release path.
        val result = WinRTPlatformApi.queryInterfaceRaw(
            PlatformAbi.fromRawComPtr(reference.pointer),
            requestedInterfaceId,
        )
        val queriedPointer = result.pointer
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(queriedPointer)) {
            return null
        }
        WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
        return queriedPointer
    }

    private fun queryInterfacePointerForComposableInstance(
        pointer: RawAddress,
        requestedInterfaceId: Guid,
    ): RawAddress {
        val result = WinRTPlatformApi.queryInterfaceRaw(pointer, requestedInterfaceId)
        val queriedPointer = result.pointer
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(queriedPointer)) {
            throw WinRTUnsupportedOperationException(
                "Composable factory instance does not implement interface '$requestedInterfaceId'.",
                KnownHResults.E_NOINTERFACE,
            )
        }
        WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
        return queriedPointer
    }

    private fun rcwCacheKey(pointer: RawAddress): Long {
        val result = WinRTPlatformApi.queryInterfaceRaw(pointer, IID.IUnknown)
        val unknownPointer = result.pointer
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(unknownPointer)) {
            return PlatformAbi.pointerKey(pointer)
        }
        WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
        return try {
            PlatformAbi.pointerKey(unknownPointer)
        } finally {
            WinRTPlatformApi.releaseRaw(unknownPointer)
        }
    }

    private fun wrapInspectable(pointer: RawAddress): IInspectableReference? {
        val existingInspectable = runCatching {
            val borrowed = IUnknownReference(
                ComPtr.create(
                    raw = pointer.asRawComPtr(),
                    interfaceId = IID.IInspectable,
                    ownershipMode = ComOwnershipMode.Borrowed,
                ),
            )
            try {
                borrowed.asInspectable()
            } finally {
                borrowed.close()
            }
        }.getOrNull()
        if (existingInspectable != null) {
            return existingInspectable
        }
        return getInspectableInfo(pointer)?.let {
            if (it.interfaceIds.contains(IID.IInspectable)) {
                IInspectableReference(ComPtr.create(pointer.asRawComPtr(), IID.IInspectable))
            } else {
                null
            }
        }
    }

    private fun createCcwDefinition(value: Any): WinRTCcwDefinition {
        CcwFactoryRegistry.findFactory(value)?.let { factory ->
            traceCcw("create CCW definition value=${value::class.qualifiedName} source=registered-factory")
            return InteropRuntimeHooks.augmentInspectableDefinition(
                value,
                XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(value, factory(value)),
            )
        }
        platformCreateSyntheticCcwDefinition(value)?.let {
            traceCcw("create CCW definition value=${value::class.qualifiedName} source=synthetic")
            return InteropRuntimeHooks.augmentInspectableDefinition(
                value,
                XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(value, it),
            )
        }
        traceCcw("create CCW definition value=${value::class.qualifiedName} source=default-inspectable")
        return InteropRuntimeHooks.augmentInspectableDefinition(
            value,
            XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(
                value,
                WinRTCcwDefinition(
                    interfaceDefinitions = listOf(
                        WinRTInspectableInterfaceDefinition(
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

    private fun ownedReference(
        host: WinRTInspectableComObject,
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

    private class CachedCcwHost(
        private val host: WinRTInspectableComObject,
        val defaultInterfaceId: Guid,
    ) {
        private val lock = PlatformLock()
        private var rootReleased = false

        fun createReference(interfaceId: Guid): ComObjectReference {
            val reference = host.createReference(interfaceId)
            releaseInitialRootOnce()
            return reference
        }

        private fun releaseInitialRootOnce() {
            lock.withLock {
                if (!rootReleased) {
                    rootReleased = true
                    host.releaseManagedReference()
                }
            }
        }
    }

}

internal fun traceCcw(message: String) {
    if (FeatureSwitches.traceCcw) {
        println("winrt-ccw: $message")
    }
}
