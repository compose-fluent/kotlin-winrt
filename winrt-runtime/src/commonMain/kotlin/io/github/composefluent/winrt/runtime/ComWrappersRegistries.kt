package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal object RcwProjectionFactoryRegistry {
    private val typedRcwFactories = ConcurrentCacheMap<WinRTTypeHandle, (IInspectableReference) -> Any>()
    private val runtimeClassFactories = ConcurrentCacheMap<String, (IInspectableReference) -> Any>()
    private val interfaceProjectionFactoriesByHandle = ConcurrentCacheMap<WinRTTypeHandle, (IUnknownReference) -> Any>()
    private val interfaceProjectionFactoriesByTypeName = ConcurrentCacheMap<String, (IUnknownReference) -> Any>()
    private val helperTypeRegistry = ConcurrentCacheMap<WinRTTypeHandle, WinRTTypeHandle>()

    fun registerTypedRcwFactory(
        typeHandle: WinRTTypeHandle,
        factory: (IInspectableReference) -> Any,
    ): Boolean = typedRcwFactories.putIfAbsent(typeHandle, factory) == null

    fun registerRuntimeClassFactory(
        runtimeClassName: String,
        factory: (IInspectableReference) -> Any,
    ): Boolean = runtimeClassFactories.putIfAbsent(runtimeClassName, factory) == null

    fun registerInterfaceProjectionFactory(
        typeHandle: WinRTTypeHandle,
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

    fun registerHelperType(
        projectedType: WinRTTypeHandle,
        helperType: WinRTTypeHandle,
    ): Boolean = helperTypeRegistry.putIfAbsent(projectedType, helperType) == null

    fun resolveRuntimeClassFactory(
        staticallyDeterminedType: WinRTTypeHandle?,
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

    fun resolveInterfaceProjectionFactory(
        staticallyDeterminedType: WinRTTypeHandle?,
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

    fun clearForTests() {
        typedRcwFactories.clear()
        runtimeClassFactories.clear()
        interfaceProjectionFactoriesByHandle.clear()
        interfaceProjectionFactoriesByTypeName.clear()
        helperTypeRegistry.clear()
    }
}

internal object AuthoringActivationFactoryRegistry {
    private val authoringActivationFactories = ConcurrentCacheMap<String, () -> ComObjectReference>()
    private val authoringActivationFactoryFallbacks = SnapshotList<(String, Guid) -> ActivationResult>()

    fun registerFactory(
        runtimeClassName: String,
        factory: () -> ComObjectReference,
    ): Boolean {
        require(runtimeClassName.isNotBlank()) { "Authored runtime class name must not be blank." }
        return authoringActivationFactories.putIfAbsent(runtimeClassName, factory) == null
    }

    fun tryGetFactory(
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

    fun registerFallback(
        lookup: (runtimeClassName: String, interfaceId: Guid) -> ActivationResult,
    ) {
        authoringActivationFactoryFallbacks.add(lookup)
    }

    fun tryGetFallback(
        runtimeClassName: String,
        interfaceId: Guid,
    ): ActivationResult =
        authoringActivationFactoryFallbacks.firstNotNullOfOrNull { fallback ->
            fallback(runtimeClassName, interfaceId).takeIf { it.hResult != KnownHResults.REGDB_E_CLASSNOTREG }
        } ?: ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, PlatformAbi.nullPointer)

    fun clearFallbacksForTests() {
        authoringActivationFactoryFallbacks.clear()
    }

    fun clearForTests() {
        authoringActivationFactories.clear()
        authoringActivationFactoryFallbacks.clear()
    }
}

internal object RuntimeTypeLookupRegistry {
    private val runtimeClassNameLookups = SnapshotList<(KClass<*>) -> String?>()
    private val authoringMetadataTypeLookups = SnapshotList<(String) -> String?>()

    fun registerRuntimeClassNameLookup(
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

    fun getRuntimeClassNameForNonWinRTType(type: KClass<*>): String? =
        runtimeClassNameLookups.firstNotNullOfOrNull { lookup ->
            lookup(type)?.takeIf { it.isNotBlank() }
        }

    fun clearForTests() {
        runtimeClassNameLookups.clear()
        authoringMetadataTypeLookups.clear()
    }
}

internal object CcwFactoryRegistry {
    private val ccwFactories = ConcurrentCacheMap<KClass<*>, (Any) -> WinRTCcwDefinition>()

    init {
        registerBuiltInFactories()
    }

    fun registerFactory(
        implementationType: KClass<*>,
        factory: (Any) -> WinRTCcwDefinition,
    ): Boolean {
        traceCcw("register CCW factory type=${implementationType.qualifiedName}")
        return ccwFactories.putIfAbsent(implementationType, factory) == null
    }

    fun findFactory(value: Any): ((Any) -> WinRTCcwDefinition)? {
        ccwFactories[value::class]?.let { return it }
        return ccwFactories.entries.firstOrNull { (type, _) -> type.isInstance(value) }?.value
    }

    fun clearForTests() {
        ccwFactories.clear()
        registerBuiltInFactories()
    }

    private fun registerBuiltInFactories() {
        ccwFactories[WinRTActivationFactory::class] = { value ->
            WinRTActivationFactorySupport.createCcwDefinition(value as WinRTActivationFactory)
        }
    }
}

internal object RuntimeRegistryResetSupport {
    fun clearForTests() {
        RcwProjectionFactoryRegistry.clearForTests()
        AuthoringActivationFactoryRegistry.clearForTests()
        CcwFactoryRegistry.clearForTests()
        ProjectedDelegateCcwCache.clearForTests()
        ProjectedDelegateObjectRoots.clearForTests()
        RuntimeTypeLookupRegistry.clearForTests()
        FreeThreadedMarshalerSupport.clearForTests()
        Projections.clearRegistriesForTests()
        TypeNameSupport.clearRegistriesForTests()
        TypeExtensions.clearRegistriesForTests()
        platformEnsureInspectableProjectionInteropRegistered()
    }
}
