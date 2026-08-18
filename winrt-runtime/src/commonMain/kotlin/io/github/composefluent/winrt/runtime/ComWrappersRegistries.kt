package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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

internal class CcwRegistrationSource internal constructor(
    internal val factory: ((Any) -> WinRTCcwDefinition)? = null,
    internal val staticDefinition: WinRTCcwDefinition? = null,
) {
    init {
        require((factory == null) != (staticDefinition == null)) {
            "A CCW registration must contain exactly one source."
        }
    }
}

internal class CcwRegistrationResolution internal constructor(
    internal val sources: List<CcwRegistrationSource>,
) {
    internal val factories: List<(Any) -> WinRTCcwDefinition> = sources.mapNotNull { it.factory }
    internal val staticDefinitions: List<WinRTCcwDefinition> = sources.mapNotNull { it.staticDefinition }
    internal val augmentedStaticDefinition: WinRTCcwDefinition? =
        staticDefinitions
            .takeIf { definitions -> definitions.isNotEmpty() && factories.isEmpty() }
            ?.let(::mergeCcwDefinitions)
            ?.let(::augmentCcwDefinition)
}

internal object CcwFactoryRegistry {
    private val ccwRegistrations = ConcurrentCacheMap<KClass<*>, CcwRegistrationSource>()
    private val resolvedRegistrations = ConcurrentCacheMap<KClass<*>, ResolvedCcwRegistrations>()
    @OptIn(ExperimentalAtomicApi::class)
    private val resolutionGeneration = AtomicInt(0)

    init {
        registerBuiltInFactories()
    }

    fun registerFactory(
        implementationType: KClass<*>,
        factory: (Any) -> WinRTCcwDefinition,
    ): Boolean {
        traceCcw { "register CCW factory type=${implementationType.qualifiedName}" }
        val registered = ccwRegistrations.putIfAbsent(
            implementationType,
            CcwRegistrationSource(factory = factory),
        ) == null
        if (registered) {
            invalidateResolvedFactories()
        }
        return registered
    }

    fun registerStaticDefinition(
        implementationType: KClass<*>,
        definition: WinRTCcwDefinition,
    ): Boolean {
        traceCcw { "register static CCW definition type=${implementationType.qualifiedName}" }
        val registered = ccwRegistrations.putIfAbsent(
            implementationType,
            CcwRegistrationSource(staticDefinition = definition),
        ) == null
        if (registered) {
            invalidateResolvedFactories()
        }
        return registered
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun findRegistration(value: Any): CcwRegistrationResolution {
        val implementationType = value::class
        while (true) {
            val generation = resolutionGeneration.load()
            resolvedRegistrations[implementationType]
                ?.takeIf { resolved -> resolved.generation == generation }
                ?.let { resolved -> return resolved.resolution }

            val resolution = resolveRegistrations(value)
            if (resolutionGeneration.load() != generation) {
                continue
            }
            resolvedRegistrations[implementationType] = ResolvedCcwRegistrations(generation, resolution)
            if (resolutionGeneration.load() == generation) {
                return resolution
            }
        }
    }

    fun findFactories(value: Any): List<(Any) -> WinRTCcwDefinition> =
        findRegistration(value).factories

    fun findStaticDefinitions(value: Any): List<WinRTCcwDefinition> =
        findRegistration(value).staticDefinitions

    fun clearForTests() {
        ccwRegistrations.clear()
        registerBuiltInFactories()
        invalidateResolvedFactories()
    }

    private fun resolveRegistrations(value: Any): CcwRegistrationResolution {
        ccwRegistrations[value::class]?.let { return CcwRegistrationResolution(listOf(it)) }
        val sources = ccwRegistrations.entries
            .asSequence()
            .filter { (type, _) -> type.isInstance(value) }
            .sortedBy { (type, _) -> type.qualifiedName.orEmpty() }
            .map { (_, source) -> source }
            .toList()
        return CcwRegistrationResolution(sources)
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun invalidateResolvedFactories() {
        while (true) {
            val current = resolutionGeneration.load()
            if (resolutionGeneration.compareAndSet(current, current + 1)) {
                resolvedRegistrations.clear()
                return
            }
        }
    }

    private fun registerBuiltInFactories() {
        ccwRegistrations[WinRTActivationFactory::class] = CcwRegistrationSource(
            factory = { value ->
                WinRTActivationFactorySupport.createCcwDefinition(value as WinRTActivationFactory)
            },
        )
    }

    private class ResolvedCcwRegistrations(
        val generation: Int,
        val resolution: CcwRegistrationResolution,
    )
}

internal object RuntimeRegistryResetSupport {
    fun clearForTests() {
        RcwProjectionFactoryRegistry.clearForTests()
        AuthoringActivationFactoryRegistry.clearForTests()
        CcwFactoryRegistry.clearForTests()
        ProjectedDelegateCcwCache.clearForTests()
        ProjectedDelegateObjectRoots.clearForTests()
        RuntimeTypeLookupRegistry.clearForTests()
        InteropRuntimeHooks.clearForTests()
        FreeThreadedMarshalerSupport.clearForTests()
        TypeNameSupport.clearRegistriesForTests()
        Projections.clearRegistriesForTests()
        TypeExtensions.clearRegistriesForTests()
        WinRTBuiltInProjectionRuntimeHooks.clearForTests()
        platformEnsureInspectableProjectionInteropRegistered()
    }
}
