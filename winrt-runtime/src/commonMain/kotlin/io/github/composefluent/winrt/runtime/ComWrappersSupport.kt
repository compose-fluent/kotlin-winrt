package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

data class WinRtCcwDefinition(
    val interfaceDefinitions: List<WinRtInspectableInterfaceDefinition>,
    val defaultInterfaceId: Guid,
    val runtimeClassName: String? = null,
    val hiddenInterfaceDefinitions: List<WinRtInspectableInterfaceDefinition> = emptyList(),
    val queryInterfaceFallback: ((Any, Guid) -> RawAddress?)? = null,
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
    private val interfaceProjectionFactoriesByHandle = ConcurrentCacheMap<WinRtTypeHandle, (IUnknownReference) -> Any>()
    private val interfaceProjectionFactoriesByTypeName = ConcurrentCacheMap<String, (IUnknownReference) -> Any>()
    private val authoringActivationFactories = ConcurrentCacheMap<String, () -> ComObjectReference>()
    private val authoringActivationFactoryFallbacks = SnapshotList<(String, Guid) -> ActivationResult>()
    private val helperTypeRegistry = ConcurrentCacheMap<WinRtTypeHandle, WinRtTypeHandle>()
    private val ccwFactories = ConcurrentCacheMap<KClass<*>, (Any) -> WinRtCcwDefinition>()
    private val rcwCache = WeakValueCache<Long, Any>()
    private val runtimeClassNameLookups = SnapshotList<(KClass<*>) -> String?>()
    private val authoringMetadataTypeLookups = SnapshotList<(String) -> String?>()

    init {
        registerBuiltInCcwFactories()
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

    fun registerInterfaceProjectionFactory(
        typeHandle: WinRtTypeHandle,
        factory: (IUnknownReference) -> Any,
    ): Boolean {
        require(typeHandle.projectedTypeName.isNotBlank()) { "Projected interface type name must not be blank." }
        interfaceProjectionFactoriesByTypeName.putIfAbsent(typeHandle.projectedTypeName, factory)
        return interfaceProjectionFactoriesByHandle.putIfAbsent(typeHandle, factory) == null
    }

    fun registerInterfaceProjectionFactory(
        projectedTypeName: String,
        factory: (IUnknownReference) -> Any,
    ): Boolean {
        require(projectedTypeName.isNotBlank()) { "Projected interface type name must not be blank." }
        return interfaceProjectionFactoriesByTypeName.putIfAbsent(projectedTypeName, factory) == null
    }

    fun wrapGeneratedInterfaceProjection(
        typeHandle: WinRtTypeHandle,
        instance: IUnknownReference,
    ): Any =
        resolveInterfaceProjectionFactory(typeHandle, typeHandle.projectedTypeName)
            ?.invoke(instance)
            ?: throw WinRtUnsupportedOperationException(
                "Generated interface projection factory for '${typeHandle.projectedTypeName}' is not registered.",
                KnownHResults.E_NOINTERFACE,
            )

    fun wrapGeneratedInterfaceProjection(
        projectedTypeName: String,
        instance: IUnknownReference,
    ): Any =
        resolveInterfaceProjectionFactory(null, projectedTypeName)
            ?.invoke(instance)
            ?: throw WinRtUnsupportedOperationException(
                "Generated interface projection factory for '$projectedTypeName' is not registered.",
                KnownHResults.E_NOINTERFACE,
            )

    fun registerAuthoringActivationFactory(
        runtimeClassName: String,
        factory: () -> ComObjectReference,
    ): Boolean {
        require(runtimeClassName.isNotBlank()) { "Authored runtime class name must not be blank." }
        return authoringActivationFactories.putIfAbsent(runtimeClassName, factory) == null
    }

    fun tryGetAuthoringActivationFactory(
        runtimeClassName: String,
        interfaceId: Guid,
    ): ActivationResult {
        val createFactory = authoringActivationFactories[runtimeClassName]
            ?: return ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, PlatformAbi.nullPointer)
        val factory = createFactory()
        val requestedFactoryPointer = try {
            if (factory.interfaceId == interfaceId || interfaceId == IID.IUnknown) {
                factory.getRefPointer()
            } else {
                factory.queryInterface(interfaceId).getOrThrow().use { reference ->
                    reference.getRefPointer()
                }
            }
        } finally {
            factory.close()
        }
        return ActivationResult(KnownHResults.S_OK, PlatformAbi.fromRawComPtr(requestedFactoryPointer))
    }

    fun registerAuthoringActivationFactoryFallback(
        lookup: (runtimeClassName: String, interfaceId: Guid) -> ActivationResult,
    ) {
        authoringActivationFactoryFallbacks.add(lookup)
    }

    fun tryGetAuthoringActivationFactoryFallback(
        runtimeClassName: String,
        interfaceId: Guid,
    ): ActivationResult =
        authoringActivationFactoryFallbacks.firstNotNullOfOrNull { fallback ->
            fallback(runtimeClassName, interfaceId).takeIf { it.hResult != KnownHResults.REGDB_E_CLASSNOTREG }
        } ?: ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, PlatformAbi.nullPointer)

    fun clearAuthoringActivationFactoryFallbacksForTests() {
        authoringActivationFactoryFallbacks.clear()
    }

    fun registerHelperType(
        projectedType: WinRtTypeHandle,
        helperType: WinRtTypeHandle,
    ): Boolean = helperTypeRegistry.putIfAbsent(projectedType, helperType) == null

    fun registerCcwFactory(
        implementationType: KClass<*>,
        factory: (Any) -> WinRtCcwDefinition,
    ): Boolean {
        traceCcw("register CCW factory type=${implementationType.qualifiedName}")
        return ccwFactories.putIfAbsent(implementationType, factory) == null
    }

    fun registerAuthoringTypeDetailsFactory(
        implementationType: KClass<*>,
        factory: (Any) -> WinRtCcwDefinition,
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
        runtimeClassNameLookups.add(lookup)
    }

    fun registerAuthoringMetadataTypeLookup(
        lookup: (String) -> String?,
    ) {
        authoringMetadataTypeLookups.add(lookup)
    }

    fun registerAuthoringMetadataTypeMappings(
        mappings: Map<String, String>,
    ) {
        if (mappings.isEmpty()) {
            return
        }
        val stableMappings = mappings.toMap()
        registerAuthoringMetadataTypeLookup { typeName -> stableMappings[typeName] }
    }

    fun getAuthoringMetadataTypeName(projectedTypeName: String): String? =
        authoringMetadataTypeLookups.firstNotNullOfOrNull { lookup ->
            lookup(projectedTypeName)?.takeIf { it.isNotBlank() }
        }

    fun getInspectableInfo(pointer: RawAddress): WinRtInspectableInfo? =
        WinRtInspectableComObject.findInspectableInfo(pointer)?.let {
            WinRtInspectableInfo(it.runtimeClassName, it.interfaceIds)
        }

    internal fun clearRuntimeCache() {
        rcwCache.clear()
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

        val definition = createCcwDefinition(value)
        val composableInnerReference = (value as? WinRtComposableObject)
            ?.winRtComposableObjectReference
            ?.inner
        val host = WinRtInspectableComObject(
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
        )
        val requestedInterface = interfaceId ?: definition.defaultInterfaceId
        return ownedReference(host, requestedInterface)
    }

    private fun tryCreateComposableCCWForObject(
        value: Any,
        interfaceId: Guid?,
    ): ComObjectReference? {
        val composableReference = (value as? WinRtComposableObject)
            ?.winRtComposableObjectReference
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
        val winRtObject = value as? IWinRTObject
        if (winRtObject != null && winRtObject.hasUnwrappableNativeObject) {
            val nativeObject = winRtObject.nativeObject
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
        val composableReference = (value as? WinRtComposableObject)
            ?.winRtComposableObjectReference
            ?: return null
        val outerReference = composableReference.outer
        val detachedPointer = if (interfaceId == null || interfaceId == outerReference.interfaceId) {
            PlatformAbi.fromRawComPtr(outerReference.getRefPointer())
        } else if (interfaceId == IID.IUnknown || interfaceId == IID.IInspectable) {
            queryInterfacePointerForAbi(outerReference, interfaceId)
        } else {
            queryInterfacePointerForAbi(outerReference, interfaceId)
        } ?: throw WinRtUnsupportedOperationException(
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
    ): WinRtComposableObjectReference =
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
    ): WinRtComposableObjectReference {
        platformEnsureInspectableProjectionInteropRegistered()
        val definition = createCcwDefinition(value)
        var innerReference: IInspectableReference? = null
        val host = WinRtInspectableComObject(
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
                    throw WinRtUnsupportedOperationException(
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
                        WinRtPlatformApi.releaseRaw(instancePointer)
                    }
                }
                val projectedReference = if (isAggregation) {
                    requireNotNull(innerReference) {
                        "Composable aggregation requires the factory to return a non-null inner pointer."
                    }
                } else {
                    composedReference
                }
                WinRtComposableObjectReference(
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
        typedRcwFactories.clear()
        runtimeClassFactories.clear()
        interfaceProjectionFactoriesByHandle.clear()
        interfaceProjectionFactoriesByTypeName.clear()
        authoringActivationFactories.clear()
        helperTypeRegistry.clear()
        ccwFactories.clear()
        rcwCache.clear()
        runtimeClassNameLookups.clear()
        authoringMetadataTypeLookups.clear()
        FreeThreadedMarshalerSupport.clearForTests()
        Projections.clearRegistriesForTests()
        TypeNameSupport.clearRegistriesForTests()
        TypeExtensions.clearRegistriesForTests()
        registerBuiltInCcwFactories()
        platformEnsureInspectableProjectionInteropRegistered()
    }

    private fun registerBuiltInCcwFactories() {
        ccwFactories[WinRtActivationFactory::class] = { value ->
            WinRtActivationFactorySupport.createCcwDefinition(value as WinRtActivationFactory)
        }
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
            resolveInterfaceProjectionFactory(staticallyDeterminedType, staticallyDeterminedType.projectedTypeName)?.let { factory ->
                return factory(typedReference.asUnknownReference(staticallyDeterminedType.interfaceId))
            }
            return SingleInterfaceOptimizedObject(
                primaryTypeHandle = staticallyDeterminedType,
                nativeObject = typedReference,
            )
        }

        if (staticallyDeterminedType != null) {
            val typedReference = IUnknownReference(pointer.asRawComPtr(), staticallyDeterminedType.interfaceId)
            resolveInterfaceProjectionFactory(staticallyDeterminedType, staticallyDeterminedType.projectedTypeName)?.let { factory ->
                return factory(typedReference)
            }
            return SingleInterfaceOptimizedObject(
                primaryTypeHandle = staticallyDeterminedType,
                nativeObject = typedReference,
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

    private fun resolveInterfaceProjectionFactory(
        staticallyDeterminedType: WinRtTypeHandle?,
        projectedTypeName: String?,
    ): ((IUnknownReference) -> Any)? {
        if (staticallyDeterminedType != null) {
            interfaceProjectionFactoriesByHandle[staticallyDeterminedType]?.let { return it }
        }
        if (!projectedTypeName.isNullOrBlank()) {
            interfaceProjectionFactoriesByTypeName[projectedTypeName]?.let { return it }
        }
        return null
    }

    private fun ComObjectReference.asUnknownReference(interfaceId: Guid): IUnknownReference =
        this as? IUnknownReference ?: try {
            IUnknownReference(getRefPointer(), interfaceId)
        } finally {
            close()
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

    private fun queryInterfacePointerForAbi(
        reference: ComObjectReference,
        requestedInterfaceId: Guid,
    ): RawAddress? {
        // Mirrors CsWinRT IObjectReference.TryAs(Guid, out IntPtr) used from ICustomQueryInterface:
        // the QI result is an ABI-owned pointer and must not take the aggregated As<T>() release path.
        val result = WinRtPlatformApi.queryInterfaceRaw(
            PlatformAbi.fromRawComPtr(reference.pointer),
            requestedInterfaceId,
        )
        val queriedPointer = result.pointer
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(queriedPointer)) {
            return null
        }
        WinRtPlatformApi.checkSucceededRaw(result.hResultValue)
        return queriedPointer
    }

    private fun queryInterfacePointerForComposableInstance(
        pointer: RawAddress,
        requestedInterfaceId: Guid,
    ): RawAddress {
        val result = WinRtPlatformApi.queryInterfaceRaw(pointer, requestedInterfaceId)
        val queriedPointer = result.pointer
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(queriedPointer)) {
            throw WinRtUnsupportedOperationException(
                "Composable factory instance does not implement interface '$requestedInterfaceId'.",
                KnownHResults.E_NOINTERFACE,
            )
        }
        WinRtPlatformApi.checkSucceededRaw(result.hResultValue)
        return queriedPointer
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
            val borrowed = IUnknownReference(pointer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true)
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
                IInspectableReference(pointer.asRawComPtr(), IID.IInspectable)
            } else {
                null
            }
        }
    }

    private fun createCcwDefinition(value: Any): WinRtCcwDefinition {
        findCcwFactory(value)?.let { factory ->
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

    private fun traceCcw(message: String) {
        if (FeatureSwitches.traceCcw) {
            println("winrt-ccw: $message")
        }
    }
}
