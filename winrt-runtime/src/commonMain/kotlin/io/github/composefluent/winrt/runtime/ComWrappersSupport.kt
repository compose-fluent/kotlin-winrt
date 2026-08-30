package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass

data class WinRTCcwDefinition(
    val interfaceDefinitions: List<WinRTInspectableInterfaceDefinition>,
    val defaultInterfaceId: Guid,
    val runtimeClassName: String? = null,
    val hiddenInterfaceDefinitions: List<WinRTInspectableInterfaceDefinition> = emptyList(),
    val queryInterfaceFallback: ((Any, Guid) -> RawAddress?)? = null,
) {
    /**
     * Shape storage for callers that retain this immutable definition.  The value is deliberately
     * kept outside the data-class constructor so it cannot affect definition equality or retain a
     * managed value.  [WinRTInspectableComObject] fills it once and then bypasses the global weak
     * identity map for every host created from the same definition.
     */
    @OptIn(ExperimentalAtomicApi::class)
    internal val preparedShape = AtomicReference<Any?>(null)

    internal val supportsWeakManagedValue: Boolean =
        queryInterfaceFallback == null &&
            interfaceDefinitions.all { definition ->
                definition.methods.all(WinRTInspectableMethodDefinition::readsHostManagedValue)
            } &&
            hiddenInterfaceDefinitions.all { definition ->
                definition.methods.all(WinRTInspectableMethodDefinition::readsHostManagedValue)
            }
}

internal fun augmentCcwDefinition(definition: WinRTCcwDefinition): WinRTCcwDefinition =
    InteropRuntimeHooks.augmentInspectableDefinition(
        XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(definition),
    )

internal fun mergeCcwDefinitions(definitions: List<WinRTCcwDefinition>): WinRTCcwDefinition {
    require(definitions.isNotEmpty()) { "At least one CCW definition is required." }
    if (definitions.size == 1) {
        return definitions.single()
    }
    val visibleInterfaces = definitions
        .flatMap(WinRTCcwDefinition::interfaceDefinitions)
        .distinctBy(WinRTInspectableInterfaceDefinition::interfaceId)
    val visibleInterfaceIds = visibleInterfaces.mapTo(mutableSetOf()) { definition -> definition.interfaceId }
    val hiddenInterfaces = definitions
        .flatMap(WinRTCcwDefinition::hiddenInterfaceDefinitions)
        .filterNot { definition -> definition.interfaceId in visibleInterfaceIds }
        .distinctBy(WinRTInspectableInterfaceDefinition::interfaceId)
    val fallbacks = definitions.mapNotNull(WinRTCcwDefinition::queryInterfaceFallback)
    return WinRTCcwDefinition(
        interfaceDefinitions = visibleInterfaces,
        defaultInterfaceId = definitions.first().defaultInterfaceId,
        runtimeClassName = definitions.firstNotNullOfOrNull(WinRTCcwDefinition::runtimeClassName),
        hiddenInterfaceDefinitions = hiddenInterfaces,
        queryInterfaceFallback = fallbacks.takeIf(List<*>::isNotEmpty)?.let {
            { value, interfaceId ->
                fallbacks.firstNotNullOfOrNull { fallback -> fallback(value, interfaceId) }
            }
        },
    )
}

class SingleInterfaceOptimizedObject(
    primaryTypeHandle: WinRTTypeHandle,
    nativeObject: ComObjectReference,
) : WinRTObjectBase<ComObjectReference>(nativeObject, primaryTypeHandle) {
    override val hasUnwrappableNativeObject: Boolean
        get() = false
}

object ComWrappersSupport {
    @OptIn(ExperimentalAtomicApi::class)
    private val ccwHostCacheGeneration = AtomicInt(0)
    private val ccwSlowStateCreationLock = PlatformLock()
    private val rcwSlowStateCreationLock = PlatformLock()
    private val ccwHostCache =
        WeakKeyStateMap<Any, CachedCcwHosts> { cachedHosts ->
            cachedHosts.releaseCacheRoots()
        }
    private val rcwCache = RcwIdentityCache()
    private val closedInterfaceRcwCache = ClosedInterfaceRcwIdentityCache()

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

    /**
     * Registers an immutable, value-independent CCW definition for an implementation type.
     * Generated projected-interface support uses this path so object construction does not
     * invoke a factory or allocate a definition list for every managed instance.
     */
    fun registerStaticCcwDefinition(
        implementationType: KClass<*>,
        definition: WinRTCcwDefinition,
    ): Boolean = CcwFactoryRegistry.registerStaticDefinition(implementationType, definition)

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
        closedInterfaceRcwCache.clear()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findObject(
        pointer: RawAddress,
        expectedType: KClass<T>,
    ): T? {
        val managedValue = findCachedRcw(PlatformAbi.pointerKey(pointer))
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

        val directPointerKey = PlatformAbi.pointerKey(pointer)
        if (tryUseCache) {
            findCachedRcw(directPointerKey, staticallyDeterminedType)?.let { cached ->
                return cached
            }
        }

        val pointerKey = rcwCacheKey(pointer)
        if (tryUseCache) {
            findCachedRcw(pointerKey, staticallyDeterminedType)?.let { cached ->
                if (directPointerKey != pointerKey) {
                    rcwCache[directPointerKey] = cached
                }
                return cached
            }
        }

        val rcw = createRcwCore(pointer, staticallyDeterminedType)
        if (tryUseCache && rcw != null) {
            rcwCache[pointerKey] = rcw
            if (directPointerKey != pointerKey) {
                rcwCache[directPointerKey] = rcw
            }
        }
        return rcw
    }

    /**
     * Projects an ABI-owned COM pointer and transfers that ownership to the resulting RCW.
     * A cache hit consumes the duplicate ABI reference without constructing another [ComPtr].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> createRcwForOwnedComObject(
        pointer: RawAddress,
        staticallyDeterminedType: WinRTTypeHandle,
        factory: (IUnknownReference, WinRTTypeHandle) -> T,
    ): T? {
        if (PlatformAbi.isNull(pointer)) {
            return null
        }

        val directPointerKey = PlatformAbi.pointerKey(pointer)
        findHotCachedRcw(directPointerKey, staticallyDeterminedType)?.let { cached ->
            WinRTPlatformApi.releaseRaw(pointer)
            return cached as T
        }

        val pointerKey = rcwCacheKey(pointer)
        if (directPointerKey != pointerKey) {
            findHotCachedRcw(pointerKey, staticallyDeterminedType)?.let { cached ->
                rcwCache[directPointerKey] = cached
                WinRTPlatformApi.releaseRaw(pointer)
                return cached as T
            }
        }

        return rcwSlowStateCreationLock.withLock {
            val cached = findCachedRcw(pointerKey, staticallyDeterminedType)
                ?: if (directPointerKey != pointerKey) {
                    findCachedRcw(directPointerKey, staticallyDeterminedType)
                } else {
                    null
                }
            cached?.let {
                if (directPointerKey != pointerKey) {
                    rcwCache[pointerKey] = cached
                    rcwCache[directPointerKey] = cached
                }
                WinRTPlatformApi.releaseRaw(pointer)
                return@withLock cached as T
            }

            val reference = IUnknownReference(pointer.asRawComPtr(), staticallyDeterminedType.interfaceId)
            try {
                factory(reference, staticallyDeterminedType).also { rcw ->
                    rcwCache[pointerKey] = rcw
                    if (directPointerKey != pointerKey) {
                        rcwCache[directPointerKey] = rcw
                    }
                }
            } catch (error: Throwable) {
                reference.close()
                throw error
            }
        }
    }

    /**
     * Projects an ABI-owned closed interface while preserving identity for that exact
     * parameterized interface. Different closed interfaces on the same COM identity keep
     * independent Kotlin views because Kotlin cannot add CsWinRT-style dynamic interfaces
     * to one wrapper at runtime.
     */
    @PublishedApi
    internal inline fun <T : IWinRTObject> createRcwForOwnedInterfaceProjection(
        pointer: RawAddress,
        interfaceId: Guid,
        crossinline typeHandleFactory: () -> WinRTTypeHandle,
        crossinline factory: (RawAddress, WinRTTypeHandle) -> T,
    ): T? {
        if (PlatformAbi.isNull(pointer)) {
            return null
        }

        val directPointerKey = PlatformAbi.pointerKey(pointer)
        findHotClosedInterfaceRcw(directPointerKey, interfaceId)?.let { cached ->
            WinRTPlatformApi.releaseRaw(pointer)
            @Suppress("UNCHECKED_CAST")
            return cached as T
        }

        val pointerKey = rcwCacheKey(pointer)
        if (directPointerKey != pointerKey) {
            findHotClosedInterfaceRcw(pointerKey, interfaceId)?.let { cached ->
                cacheClosedInterfaceRcw(directPointerKey, interfaceId, cached)
                WinRTPlatformApi.releaseRaw(pointer)
                @Suppress("UNCHECKED_CAST")
                return cached as T
            }
        }

        return withRcwSlowStateCreationLock {
            val cached = findClosedInterfaceRcw(pointerKey, interfaceId)
                ?: if (directPointerKey != pointerKey) {
                    findClosedInterfaceRcw(directPointerKey, interfaceId)
                } else {
                    null
                }
            cached?.let {
                cacheClosedInterfaceRcw(pointerKey, interfaceId, cached)
                if (directPointerKey != pointerKey) {
                    cacheClosedInterfaceRcw(directPointerKey, interfaceId, cached)
                }
                WinRTPlatformApi.releaseRaw(pointer)
                @Suppress("UNCHECKED_CAST")
                return@withRcwSlowStateCreationLock cached as T
            }

            val typeHandle = typeHandleFactory()
            try {
                factory(pointer, typeHandle).also { rcw ->
                    cacheClosedInterfaceRcw(pointerKey, interfaceId, rcw)
                    if (directPointerKey != pointerKey) {
                        cacheClosedInterfaceRcw(directPointerKey, interfaceId, rcw)
                    }
                }
            } catch (error: Throwable) {
                WinRTPlatformApi.releaseRaw(pointer)
                throw error
            }
        }
    }

    @PublishedApi
    internal fun <R> withRcwSlowStateCreationLock(block: () -> R): R =
        rcwSlowStateCreationLock.withLock(block)

    @PublishedApi
    internal fun findHotClosedInterfaceRcw(
        pointerKey: Long,
        interfaceId: Guid,
    ): IWinRTObject? = validateClosedInterfaceRcw(
        pointerKey,
        interfaceId,
        closedInterfaceRcwCache.getHot(pointerKey, interfaceId),
    )

    @PublishedApi
    internal fun findClosedInterfaceRcw(
        pointerKey: Long,
        interfaceId: Guid,
    ): IWinRTObject? = validateClosedInterfaceRcw(
        pointerKey,
        interfaceId,
        closedInterfaceRcwCache[pointerKey, interfaceId],
    )

    @PublishedApi
    internal fun cacheClosedInterfaceRcw(
        pointerKey: Long,
        interfaceId: Guid,
        value: IWinRTObject,
    ) {
        closedInterfaceRcwCache[pointerKey, interfaceId] = value
    }

    private fun validateClosedInterfaceRcw(
        pointerKey: Long,
        interfaceId: Guid,
        cached: IWinRTObject?,
    ): IWinRTObject? {
        cached ?: return null
        if (
            cached.nativeObject.isDisposed ||
            cached.primaryTypeHandle?.interfaceId != interfaceId
        ) {
            closedInterfaceRcwCache.remove(pointerKey, interfaceId)
            return null
        }
        return cached
    }

    private inline fun findHotCachedRcw(
        pointerKey: Long,
        staticallyDeterminedType: WinRTTypeHandle? = null,
    ): Any? = validateCachedRcw(pointerKey, rcwCache.getHot(pointerKey), staticallyDeterminedType)

    private inline fun findCachedRcw(
        pointerKey: Long,
        staticallyDeterminedType: WinRTTypeHandle? = null,
    ): Any? = validateCachedRcw(pointerKey, rcwCache[pointerKey], staticallyDeterminedType)

    private inline fun validateCachedRcw(
        pointerKey: Long,
        cached: Any?,
        staticallyDeterminedType: WinRTTypeHandle?,
    ): Any? {
        cached ?: return null
        val winRTObject = cached as? IWinRTObject
        if (winRTObject == null) {
            return if (staticallyDeterminedType == null) cached else null
        }
        if (winRTObject.nativeObject.isDisposed) {
            rcwCache.remove(pointerKey)
            return null
        }
        val primaryTypeHandle = winRTObject.primaryTypeHandle
        if (
            staticallyDeterminedType == null ||
            primaryTypeHandle === staticallyDeterminedType ||
            primaryTypeHandle == staticallyDeterminedType ||
            winRTObject.isInterfaceImplemented(staticallyDeterminedType, false)
        ) {
            return cached
        }
        return null
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

    fun registerRuntimeClassWrapper(
        value: Any,
        instance: ComObjectReference,
    ) {
        registerObjectForComInterface(value, PlatformAbi.fromRawComPtr(instance.pointer))
    }

    fun initializeComposableReference(instance: IInspectableReference): IInspectableReference =
        instance.also { it.tryInitializeReferenceTracker(addRefFromTrackerSource = false) }

    fun initializeComposableReference(
        instance: IUnknownReference,
        defaultInterfaceId: Guid,
    ): IInspectableReference =
        IInspectableReference(instance.getRefPointer(), defaultInterfaceId)
            .also { it.tryInitializeReferenceTracker(addRefFromTrackerSource = false) }

    fun attachComposableFactoryResult(
        result: WinRTComposableFactoryResult,
        defaultInterfaceId: Guid,
    ): IInspectableReference {
        val instance = IInspectableReference(result.instance, defaultInterfaceId)
        return try {
            val inner = PlatformAbi.fromRawComPtr(result.inner)
            if (!PlatformAbi.isNull(inner)) {
                WinRTPlatformApi.releaseRaw(inner)
            }
            instance.also { it.tryInitializeReferenceTracker(addRefFromTrackerSource = false) }
        } catch (error: Throwable) {
            instance.close()
            throw error
        }
    }

    fun registerComposableWrapper(
        value: Any,
        instance: IInspectableReference,
    ) {
        registerRuntimeClassWrapper(value, instance)
    }

    fun createCCWForObject(
        value: Any,
        interfaceId: Guid? = null,
        declaredReferenceArrayElementType: KClass<*>? = null,
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

        val effectiveElementType = declaredReferenceArrayElementType.takeIf { value is Array<*> }
        val cachedHost = cachedCcwHost(value, effectiveElementType)
        val requestedInterface = interfaceId ?: cachedHost.primaryInterfaceId
        return cachedHost.createCachedReference(requestedInterface, value)
    }

    internal fun createReferenceTrackerTargetForObject(value: Any): ComObjectReference =
        cachedCcwHost(value, declaredReferenceArrayElementType = null)
            .createCachedReference(IID.IReferenceTrackerTarget, value)

    @PublishedApi
    internal fun createCCWForObjectForMarshaling(
        value: Any,
        interfaceId: Guid,
        declaredReferenceArrayElementType: KClass<*>? = null,
    ): WinRTProjectionMarshaler {
        val effectiveElementType = declaredReferenceArrayElementType.takeIf { value is Array<*> }
        if (value is ComObjectReference) {
            val reference = if (interfaceId == value.interfaceId) {
                cloneComReference(value)
            } else {
                value.queryInterface(interfaceId).getOrThrow()
            }
            return WinRTProjectionMarshaler.owned(reference)
        }

        (value as? WinRTComposableObject)
            ?.winRTComposableObjectReference
            ?.tryCreateStaticCallLease(interfaceId, value)
            ?.let { return it }

        cachedCcwHostOrNull(value, effectiveElementType)?.let { cachedHost ->
            return cachedHost.createCachedMarshaler(interfaceId, value)
        }

        platformEnsureInspectableProjectionInteropRegistered()

        platformTryCreateProjectedReference(value, interfaceId)?.let {
            return WinRTProjectionMarshaler.owned(it)
        }
        tryCreateComposableCCWForObject(value, interfaceId)?.let {
            return WinRTProjectionMarshaler.owned(it)
        }

        return cachedCcwHost(value, effectiveElementType)
            .createCachedMarshaler(interfaceId, value)
    }

    @PublishedApi
    internal fun tryBorrowCachedCCWForObjectForMarshaling(
        value: Any,
        interfaceId: Guid,
    ): RawAddress =
        cachedCcwHostOrNull(value, declaredReferenceArrayElementType = null)
            ?.tryBorrowCachedAbi(interfaceId)
            ?: RawAddress.Null

    @PublishedApi
    internal fun tryAcquireCachedCCWCallLease(
        value: Any,
        interfaceId: Guid,
    ): WinRTProjectionMarshaler? =
        cachedCcwHostOrNull(value, declaredReferenceArrayElementType = null)
            ?.tryAcquireCachedCallLease(interfaceId, value)

    private fun cachedCcwHost(
        value: Any,
        declaredReferenceArrayElementType: KClass<*>?,
    ): WinRTInspectableComObject {
        val metadataKey = ccwMetadataKey(declaredReferenceArrayElementType)
        val cachedHosts = cachedCcwHosts(
            value = value,
            initialMetadataKey = metadataKey,
        ) { removeFromCache ->
            createCachedCcwHost(value, declaredReferenceArrayElementType, removeFromCache)
        }
        return cachedHosts.getOrCreate(metadataKey) {
            createCachedCcwHost(value, declaredReferenceArrayElementType) {
                cachedHosts.remove(metadataKey)
            }
        }
    }

    private fun cachedCcwHostOrNull(
        value: Any,
        declaredReferenceArrayElementType: KClass<*>?,
    ): WinRTInspectableComObject? =
        cachedCcwHostsOrNull(value)?.get(ccwMetadataKey(declaredReferenceArrayElementType))

    @OptIn(ExperimentalAtomicApi::class)
    private fun cachedCcwHosts(
        value: Any,
        initialMetadataKey: CcwMetadataKey,
        createInitialHost: (() -> Unit) -> WinRTInspectableComObject,
    ): CachedCcwHosts {
        val createCachedHosts = {
            CachedCcwHosts(initialMetadataKey, createInitialHost)
        }
        val state = (value as? WinRTManagedProjectionStateOwner)?.winRTManagedProjectionState()
            ?: return ccwHostCache.getOrPut(value, createCachedHosts)
        while (true) {
            val generation = ccwHostCacheGeneration.load()
            state.binding.load()
                ?.takeIf { binding -> binding.generation == generation }
                ?.let { binding -> return binding.cache as CachedCcwHosts }

            val current = state.binding.load()
            if (current?.generation == generation) {
                return current.cache as CachedCcwHosts
            }
            if (ccwHostCacheGeneration.load() != generation) {
                continue
            }
            val cachedHosts = ccwHostCache.getOrPut(value, createCachedHosts)
            val newBinding = WinRTManagedProjectionStateBinding(generation, cachedHosts)
            if (state.binding.compareAndSet(current, newBinding)) {
                cachedHosts.attachProjectionState(state)
                return cachedHosts
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun cachedCcwHostsOrNull(value: Any): CachedCcwHosts? {
        val state = (value as? WinRTManagedProjectionStateOwner)?.winRTManagedProjectionState()
        if (state != null) {
            val generation = ccwHostCacheGeneration.load()
            state.binding.load()
                ?.takeIf { binding -> binding.generation == generation }
                ?.let { binding -> return binding.cache as CachedCcwHosts }
        }
        return ccwHostCache[value]
    }

    private fun ccwMetadataKey(declaredReferenceArrayElementType: KClass<*>?): CcwMetadataKey =
        declaredReferenceArrayElementType?.let(::CcwMetadataKey) ?: defaultCcwMetadataKey

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
            shapeCacheKey = definition,
        )
        return host.createCachedReference(interfaceId)
    }

    private fun createCachedCcwHost(
        value: Any,
        declaredReferenceArrayElementType: KClass<*>?,
        removeFromCache: () -> Unit,
    ): WinRTInspectableComObject {
        val definition = createCcwDefinition(value, declaredReferenceArrayElementType)
        val composableInnerReference = (value as? WinRTComposableObject)
            ?.winRTComposableObjectReference
            ?.inner
        val retainCacheRoot =
            composableInnerReference == null &&
                definition.supportsWeakManagedValue
        val host = WinRTInspectableComObject(
            interfaceDefinitions = definition.interfaceDefinitions,
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
            defaultInterfaceId = definition.defaultInterfaceId,
            runtimeClassName = definition.runtimeClassName,
            managedValue = value,
            weakManagedValue = retainCacheRoot,
            queryInterfaceFallback = definition.queryInterfaceFallback?.let { fallback ->
                { requestedInterfaceId ->
                    fallback(value, requestedInterfaceId)?.takeUnless(PlatformAbi::isNull)
                }
            },
            initialQueryInterfaceForwardTarget = composableInnerReference,
            cleanupAction = {
                removeFromCache()
            },
            shapeCacheKey = definition,
        )
        return host
    }

    private fun WinRTInspectableComObject.createCachedReference(
        interfaceId: Guid,
    ): ComObjectReference {
        val reference = createReference(interfaceId)
        if (!state.borrowReady) {
            releaseInitialReference()
        }
        return reference
    }

    private fun WinRTInspectableComObject.createCachedReference(
        interfaceId: Guid,
        identityVerifiedManagedValue: Any,
    ): ComObjectReference {
        val reference = createReferenceForKnownManagedValue(interfaceId, identityVerifiedManagedValue)
        if (!state.borrowReady) {
            releaseInitialReference()
        }
        return reference
    }

    private fun WinRTInspectableComObject.createCachedMarshaler(
        interfaceId: Guid,
        identityVerifiedManagedValue: Any,
    ): WinRTProjectionMarshaler {
        if (state.borrowReady) {
            return createStaticCallLease(interfaceId, identityVerifiedManagedValue)
        }

        val abi = acquireReferenceForKnownManagedValue(interfaceId, identityVerifiedManagedValue)
        releaseInitialReference()
        return WinRTProjectionMarshaler.managed(abi, this)
    }

    private fun WinRTInspectableComObject.tryBorrowCachedAbi(interfaceId: Guid): RawAddress =
        if (state.borrowReady) tryBorrowCachedInterfacePointer(interfaceId) else RawAddress.Null

    private fun WinRTInspectableComObject.tryAcquireCachedCallLease(
        interfaceId: Guid,
        identityVerifiedManagedValue: Any,
    ): WinRTProjectionMarshaler? =
        if (state.borrowReady) {
            tryAcquireStaticCallLease(interfaceId, identityVerifiedManagedValue)
        } else {
            null
        }

    private fun tryCreateComposableCCWForObject(
        value: Any,
        interfaceId: Guid?,
    ): ComObjectReference? {
        val composableReference = (value as? WinRTComposableObject)
            ?.winRTComposableObjectReference
            ?: return null
        val outerReference = composableReference.outer
        traceCcw {
            "create composable CCW value=${value::class.qualifiedName} requested=$interfaceId " +
                "outer=${outerReference.interfaceId} instance=${composableReference.instance.interfaceId}"
        }
        return if (interfaceId == null || interfaceId == outerReference.interfaceId) {
            traceCcw { "create composable CCW using outer" }
            cloneComReference(outerReference)
        } else if (interfaceId == IID.IUnknown || interfaceId == IID.IInspectable) {
            traceCcw { "create composable CCW querying outer for $interfaceId" }
            outerReference.queryInterface(interfaceId).getOrThrow()
        } else {
            traceCcw { "create composable CCW querying outer custom QI for $interfaceId" }
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
        traceCcw {
            "detach CCW value=${value::class.qualifiedName} requested=$interfaceId " +
                "reference=${reference.interfaceId} aggregated=${reference.isAggregated}"
        }
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
        traceCcw {
            "detach composable CCW value=${value::class.qualifiedName} requested=$interfaceId " +
                "pointer=${PlatformAbi.pointerKey(detachedPointer)}"
        }
        return detachedPointer
    }

    fun createComposableCCWForObject(
        value: Any,
        outerInterfaceId: Guid? = null,
        createInstance: (baseInterface: RawAddress, innerOut: RawAddress, instanceOut: RawAddress) -> Int,
    ): WinRTComposableObjectReference =
        createComposableCCWForObject(value, outerInterfaceId, null) { baseInterface ->
            PlatformAbi.confinedScope().use { scope ->
                val innerOut = PlatformAbi.allocatePointerSlot(scope)
                val instanceOut = PlatformAbi.allocatePointerSlot(scope)
                HResult(createInstance(baseInterface, innerOut, instanceOut)).requireSuccess()
                WinRTComposableFactoryResult(
                    inner = PlatformAbi.toRawComPtr(PlatformAbi.readPointer(innerOut)),
                    instance = PlatformAbi.toRawComPtr(PlatformAbi.readPointer(instanceOut)),
                )
            }
        }

    fun createComposableCCWForObject(
        value: Any,
        outerInterfaceId: Guid? = null,
        createInstance: (baseInterface: RawAddress) -> WinRTComposableFactoryResult,
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
    ): WinRTComposableObjectReference =
        createComposableCCWForObject(value, outerInterfaceId, instanceInterfaceId) { baseInterface ->
            PlatformAbi.confinedScope().use { scope ->
                val innerOut = PlatformAbi.allocatePointerSlot(scope)
                val instanceOut = PlatformAbi.allocatePointerSlot(scope)
                HResult(createInstance(baseInterface, innerOut, instanceOut)).requireSuccess()
                WinRTComposableFactoryResult(
                    inner = PlatformAbi.toRawComPtr(PlatformAbi.readPointer(innerOut)),
                    instance = PlatformAbi.toRawComPtr(PlatformAbi.readPointer(instanceOut)),
                )
            }
        }

    fun createComposableCCWForObject(
        value: Any,
        outerInterfaceId: Guid?,
        instanceInterfaceId: Guid? = null,
        createInstance: (baseInterface: RawAddress) -> WinRTComposableFactoryResult,
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
            queryInterfaceFallback = definition.queryInterfaceFallback?.let { fallback ->
                { requestedInterfaceId ->
                    fallback(value, requestedInterfaceId)?.takeUnless(PlatformAbi::isNull)
                }
            },
            shapeCacheKey = definition,
        )
        val isAggregation = outerInterfaceId != null
        var outerReference: ComObjectReference? = null
        return try {
            val baseInspectable = host.createReference(IID.IInspectable)
            val factoryResult = try {
                createInstance(PlatformAbi.fromRawComPtr(baseInspectable.pointer))
            } finally {
                baseInspectable.close()
            }
            val innerPointer = PlatformAbi.fromRawComPtr(factoryResult.inner)
            if (!PlatformAbi.isNull(innerPointer)) {
                host.registerExternalPointerAlias(innerPointer)
                registerObjectForComInterface(value, innerPointer)
            }
            val instancePointer = PlatformAbi.fromRawComPtr(factoryResult.instance)
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
            host.setQueryInterfaceForwardTarget(innerReference)
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
                outerHost = host,
                cleanup = host::releaseManagedReference,
            )
        } catch (failure: Throwable) {
            outerReference?.close()
            host.releaseManagedReference()
            throw failure
        }
    }

    /** Test-only global reset. Callers must ensure no projected call is using a cached ABI. */
    fun clearRegistriesForTests() {
        ReferenceTrackerManager.clearForTests()
        ccwHostCache.clear()
        advanceCcwHostCacheGeneration()
        rcwCache.clear()
        closedInterfaceRcwCache.clear()
        RuntimeRegistryResetSupport.clearForTests()
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun advanceCcwHostCacheGeneration() {
        while (true) {
            val current = ccwHostCacheGeneration.load()
            if (ccwHostCacheGeneration.compareAndSet(current, current + 1)) {
                return
            }
        }
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

    @PublishedApi
    internal fun rcwCacheKey(pointer: RawAddress): Long {
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

    private fun createCcwDefinition(
        value: Any,
        declaredReferenceArrayElementType: KClass<*>? = null,
    ): WinRTCcwDefinition {
        val registration = CcwFactoryRegistry.findRegistration(value)
        registration.augmentedStaticDefinition?.let { definition ->
            traceCcw { "create CCW definition value=${value::class.qualifiedName} source=static-definition" }
            return definition
        }
        registration.sources.takeIf(List<*>::isNotEmpty)?.let { sources ->
            val sourceName = if (registration.staticDefinitions.isNotEmpty()) {
                if (registration.factories.isEmpty()) "static-definition" else "registered-factory-and-static-definition"
            } else {
                "registered-factory"
            }
            traceCcw { "create CCW definition value=${value::class.qualifiedName} source=$sourceName" }
            val definition = if (sources.size == 1) {
                val source = sources.single()
                source.staticDefinition ?: requireNotNull(source.factory).invoke(value)
            } else {
                mergeCcwDefinitions(
                    sources.map { source ->
                        source.staticDefinition ?: requireNotNull(source.factory).invoke(value)
                    },
                )
            }
            return augmentCcwDefinition(definition)
        }
        val syntheticDefinition = if (declaredReferenceArrayElementType == null) {
            platformCreateSyntheticCcwDefinition(value)
        } else {
            createSyntheticInspectableCcwDefinition(value, declaredReferenceArrayElementType)
        }
        syntheticDefinition?.let {
            traceCcw { "create CCW definition value=${value::class.qualifiedName} source=synthetic" }
            return augmentCcwDefinition(it)
        }
        traceCcw { "create CCW definition value=${value::class.qualifiedName} source=default-inspectable" }
        return augmentCcwDefinition(
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

    @OptIn(ExperimentalAtomicApi::class)
    private class CachedCcwHosts(
        initialMetadataKey: CcwMetadataKey,
        createInitialHost: (() -> Unit) -> WinRTInspectableComObject,
    ) : WinRTManagedProjectionAbiSource {
        private val defaultHost = AtomicReference<WinRTInspectableComObject?>(null)
        @kotlin.concurrent.Volatile
        private var projectionState: WinRTManagedProjectionState? = null
        @kotlin.concurrent.Volatile
        private var slowState: CachedCcwSlowState? = null

        init {
            val initialHost = createInitialHost {
                remove(initialMetadataKey)
            }
            if (initialMetadataKey.isDefault) {
                defaultHost.store(initialHost)
            } else {
                slowState = CachedCcwSlowState(
                    specializedHosts = mutableMapOf(initialMetadataKey to initialHost),
                )
            }
        }

        fun attachProjectionState(state: WinRTManagedProjectionState) {
            projectionState = state
        }

        operator fun get(metadataKey: CcwMetadataKey): WinRTInspectableComObject? =
            if (metadataKey.isDefault) {
                defaultHost.load()
            } else {
                slowState?.let { state ->
                    state.lock.withLock { state.specializedHosts?.get(metadataKey) }
                }
            }

        fun getOrCreate(
            metadataKey: CcwMetadataKey,
            create: () -> WinRTInspectableComObject,
        ): WinRTInspectableComObject {
            if (metadataKey.isDefault) {
                defaultHost.load()?.let { return it }
            }
            val state = slowStateOrCreate()
            return state.lock.withLock {
                if (metadataKey.isDefault) {
                    defaultHost.load() ?: create().also(defaultHost::store)
                } else {
                    val hosts = state.specializedHosts
                        ?: mutableMapOf<CcwMetadataKey, WinRTInspectableComObject>().also {
                            state.specializedHosts = it
                        }
                    hosts[metadataKey] ?: create().also { hosts[metadataKey] = it }
                }
            }
        }

        fun remove(metadataKey: CcwMetadataKey) {
            projectionState?.invalidateBorrowedAbi(this)
            // The default host is stored independently of the specialized-host map.  Cleanup of
            // the ordinary CCW path must not allocate the slow state (and its lock) just to clear
            // that slot; the slow state is only needed when an array/declaration-specific host
            // has actually been requested.
            if (metadataKey.isDefault) {
                defaultHost.store(null)
                return
            }
            val state = slowStateOrCreate()
            state.lock.withLock {
                state.specializedHosts?.remove(metadataKey)
                if (state.specializedHosts?.isEmpty() == true) {
                    state.specializedHosts = null
                }
            }
        }

        fun releaseCacheRoots() {
            projectionState?.invalidateBorrowedAbi(this)
            detachDefaultHost()?.releaseInitialReference()
            slowState?.let { state ->
                val specializedHosts = state.lock.withLock {
                    state.specializedHosts?.values?.toList().orEmpty().also {
                        state.specializedHosts = null
                    }
                }
                specializedHosts.forEach(WinRTInspectableComObject::releaseInitialReference)
            }
            projectionState = null
        }

        override fun tryBorrowAbi(interfaceId: Guid): RawAddress =
            defaultHost.load()?.tryBorrowCachedAbi(interfaceId) ?: RawAddress.Null

        override fun tryAcquireCallLease(
            knownManagedValue: Any,
            interfaceId: Guid,
        ): WinRTProjectionMarshaler? =
            defaultHost.load()?.tryAcquireCachedCallLease(interfaceId, knownManagedValue)

        private fun slowStateOrCreate(): CachedCcwSlowState {
            slowState?.let { return it }
            return ComWrappersSupport.ccwSlowStateCreationLock.withLock {
                slowState ?: CachedCcwSlowState().also { created ->
                    slowState = created
                }
            }
        }

        private fun detachDefaultHost(): WinRTInspectableComObject? {
            while (true) {
                val host = defaultHost.load() ?: return null
                if (defaultHost.compareAndSet(host, null)) {
                    return host
                }
            }
        }
    }

    private class CachedCcwSlowState(
        var specializedHosts: MutableMap<CcwMetadataKey, WinRTInspectableComObject>? = null,
    ) {
        val lock = PlatformLock()
    }

    private data class CcwMetadataKey(
        val declaredReferenceArrayElementType: KClass<*>?,
    ) {
        val isDefault: Boolean
            get() = declaredReferenceArrayElementType == null
    }

    private val defaultCcwMetadataKey = CcwMetadataKey(null)

}

@OptIn(ExperimentalAtomicApi::class)
private class RcwIdentityCache {
    private val entries = WeakValueCache<Long, Any>()
    private val hotEntry = AtomicReference<RcwIdentityCacheEntry?>(null)

    fun getHot(pointerKey: Long): Any? {
        val hot = hotEntry.load()
        if (hot != null && hot.pointerKey == pointerKey) {
            return hot.reference.get()
        }
        return null
    }

    operator fun get(pointerKey: Long): Any? {
        getHot(pointerKey)?.let { return it }
        val reference = entries.reference(pointerKey) ?: return null
        return reference.get()?.also {
            hotEntry.store(RcwIdentityCacheEntry(pointerKey, reference))
        }
    }

    operator fun set(
        pointerKey: Long,
        value: Any,
    ) {
        val reference = entries.put(pointerKey, value)
        hotEntry.store(RcwIdentityCacheEntry(pointerKey, reference))
    }

    fun remove(pointerKey: Long): Any? {
        val removed = entries.remove(pointerKey)
        while (true) {
            val hot = hotEntry.load() ?: break
            if (hot.pointerKey != pointerKey || hotEntry.compareAndSet(hot, null)) {
                break
            }
        }
        return removed
    }

    fun clear() {
        hotEntry.store(null)
        entries.clear()
    }
}

private class RcwIdentityCacheEntry(
    val pointerKey: Long,
    @Suppress("unused") val reference: WeakValueCacheReference<Any>,
) {
}

@OptIn(ExperimentalAtomicApi::class)
private class ClosedInterfaceRcwIdentityCache {
    private val entries = WeakValueCache<ClosedInterfaceRcwIdentityKey, IWinRTObject>()
    private val hotEntry = AtomicReference<ClosedInterfaceRcwIdentityCacheEntry?>(null)

    fun getHot(
        pointerKey: Long,
        interfaceId: Guid,
    ): IWinRTObject? {
        val hot = hotEntry.load()
        if (hot != null && hot.pointerKey == pointerKey && hot.interfaceId == interfaceId) {
            return hot.reference.get()
        }
        return null
    }

    operator fun get(
        pointerKey: Long,
        interfaceId: Guid,
    ): IWinRTObject? {
        getHot(pointerKey, interfaceId)?.let { return it }
        val key = ClosedInterfaceRcwIdentityKey(pointerKey, interfaceId)
        val reference = entries.reference(key) ?: return null
        return reference.get()?.also {
            hotEntry.store(ClosedInterfaceRcwIdentityCacheEntry(pointerKey, interfaceId, reference))
        }
    }

    operator fun set(
        pointerKey: Long,
        interfaceId: Guid,
        value: IWinRTObject,
    ) {
        val key = ClosedInterfaceRcwIdentityKey(pointerKey, interfaceId)
        val reference = entries.put(key, value)
        hotEntry.store(ClosedInterfaceRcwIdentityCacheEntry(pointerKey, interfaceId, reference))
    }

    fun remove(
        pointerKey: Long,
        interfaceId: Guid,
    ): IWinRTObject? {
        val removed = entries.remove(ClosedInterfaceRcwIdentityKey(pointerKey, interfaceId))
        while (true) {
            val hot = hotEntry.load() ?: break
            if (
                hot.pointerKey != pointerKey ||
                hot.interfaceId != interfaceId ||
                hotEntry.compareAndSet(hot, null)
            ) {
                break
            }
        }
        return removed
    }

    fun clear() {
        hotEntry.store(null)
        entries.clear()
    }
}

private data class ClosedInterfaceRcwIdentityKey(
    val pointerKey: Long,
    val interfaceId: Guid,
)

private class ClosedInterfaceRcwIdentityCacheEntry(
    val pointerKey: Long,
    val interfaceId: Guid,
    @Suppress("unused") val reference: WeakValueCacheReference<IWinRTObject>,
)

internal inline fun traceCcw(message: () -> String) {
    if (FeatureSwitches.traceCcw) {
        println("winrt-ccw: ${message()}")
    }
}
