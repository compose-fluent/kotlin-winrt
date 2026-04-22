package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal data class WinRtCcwDefinition(
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

    internal fun registerCcwFactory(
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

    fun getInspectableInfo(pointer: NativePointer): WinRtInspectableInfo? =
        WinRtInspectableComObject.findInspectableInfo(pointer)?.let {
            WinRtInspectableInfo(it.runtimeClassName, it.interfaceIds)
        }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findObject(
        pointer: NativePointer,
        expectedType: KClass<T>,
    ): T? {
        val managedValue = WinRtInspectableComObject.findManagedValue(pointer) ?: return null
        if (!expectedType.isInstance(managedValue)) {
            return null
        }
        return managedValue as T
    }

    inline fun <reified T : Any> findObject(pointer: NativePointer): T? = findObject(pointer, T::class)

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
        pointer: NativePointer,
        staticallyDeterminedType: WinRtTypeHandle? = null,
        tryUseCache: Boolean = true,
    ): Any? {
        platformEnsureInspectableProjectionInteropRegistered()
        if (NativeInterop.isNull(pointer)) {
            return null
        }

        val pointerKey = NativeInterop.pointerKey(pointer)
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
        pointer: NativePointer,
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
                nativeObject = IUnknownReference(pointer, staticallyDeterminedType.interfaceId),
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

    private fun wrapInspectable(pointer: NativePointer): IInspectableReference? {
        val existingInspectable = runCatching {
            IUnknownReference(pointer, IID.IInspectable, preventReleaseOnDispose = true).asInspectable()
        }.getOrNull()
        if (existingInspectable != null) {
            return existingInspectable
        }
        return getInspectableInfo(pointer)?.let {
            if (it.interfaceIds.contains(IID.IInspectable)) {
                IInspectableReference(pointer, IID.IInspectable)
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
