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
        resolveInterfaceProjectionFactoryWithGeneratedRegistryFallback(typeHandle, typeHandle.projectedTypeName)
            ?.invoke(instance)
            ?: throw WinRtUnsupportedOperationException(
                "Generated interface projection factory for '${typeHandle.projectedTypeName}' is not registered.",
                KnownHResults.E_NOINTERFACE,
            )

    fun wrapGeneratedInterfaceProjection(
        projectedTypeName: String,
        instance: IUnknownReference,
    ): Any =
        resolveInterfaceProjectionFactoryWithGeneratedRegistryFallback(null, projectedTypeName)
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
    ): Boolean = ccwFactories.putIfAbsent(implementationType, factory) == null

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
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
            defaultInterfaceId = definition.defaultInterfaceId,
            runtimeClassName = definition.runtimeClassName,
            managedValue = value,
            queryInterfaceFallback = definition.queryInterfaceFallback?.let { fallback ->
                { requestedInterfaceId -> fallback(value, requestedInterfaceId) }
            },
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
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
            defaultInterfaceId = definition.defaultInterfaceId,
            runtimeClassName = definition.runtimeClassName,
            managedValue = value,
            queryInterfaceFallback = { requestedInterfaceId ->
                definition.queryInterfaceFallback
                    ?.invoke(value, requestedInterfaceId)
                    ?.takeUnless(PlatformAbi::isNull)
                    ?: innerReference
                    ?.tryQueryInterface(requestedInterfaceId)
                    ?.use { queried -> PlatformAbi.fromRawComPtr(queried.getRefPointer()) }
            },
        )
        val isAggregation = outerInterfaceId != null
        val requestedInterface = outerInterfaceId ?: definition.defaultInterfaceId
        val outerReference = host.createReference(requestedInterface)
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
                val isAggregatedReferenceTrackerObject =
                    !PlatformAbi.isNull(innerPointer) && hasReferenceTracker(innerPointer)
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
                val composedReference = IInspectableReference(
                    PlatformAbi.toRawComPtr(instancePointer),
                    IID.IInspectable,
                    preventReleaseOnDispose = isAggregation || isAggregatedReferenceTrackerObject,
                )
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

    private fun resolveInterfaceProjectionFactoryWithGeneratedRegistryFallback(
        staticallyDeterminedType: WinRtTypeHandle?,
        projectedTypeName: String?,
    ): ((IUnknownReference) -> Any)? =
        resolveInterfaceProjectionFactory(staticallyDeterminedType, projectedTypeName)
            ?: run {
                registerCompilerGeneratedProjectionTypeIndexes()
                resolveInterfaceProjectionFactory(staticallyDeterminedType, projectedTypeName)
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
            return InteropRuntimeHooks.augmentInspectableDefinition(
                value,
                XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(value, factory(value)),
            )
        }
        platformCreateSyntheticCcwDefinition(value)?.let {
            return InteropRuntimeHooks.augmentInspectableDefinition(
                value,
                XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(value, it),
            )
        }
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
}
