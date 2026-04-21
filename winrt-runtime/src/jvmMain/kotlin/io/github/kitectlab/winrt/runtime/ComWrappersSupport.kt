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

/**
 * Kotlin runtime ownership layer corresponding to `.cswinrt/src/WinRT.Runtime/ComWrappersSupport*`.
 *
 * This slice owns typed RCW factory registration, runtime-class-name-based wrapper lookup, managed CCW
 * factory registration, unwrap helpers, and the Kotlin equivalent of `SingleInterfaceOptimizedObject`.
 * Universal marshaling policy still belongs to Runtime 1.14+.
 */
object ComWrappersSupport {
    private val typedRcwFactories = ConcurrentCacheMap<WinRtTypeHandle, (IInspectableReference) -> Any>()
    private val runtimeClassFactories = ConcurrentCacheMap<String, (IInspectableReference) -> Any>()
    private val helperTypeRegistry = ConcurrentCacheMap<WinRtTypeHandle, WinRtTypeHandle>()
    private val ccwFactories = ConcurrentCacheMap<KClass<*>, (Any) -> WinRtCcwDefinition>()
    private val rcwCache = WeakValueCache<Long, Any>()
    private val runtimeClassNameLookups = SnapshotList<(KClass<*>) -> String?>()

    init {
        WinRtBuiltInProjectionRuntimeHooks.ensureRegistered()
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

    fun registerCcwFactory(
        implementationType: Class<*>,
        factory: (Any) -> WinRtCcwDefinition,
    ): Boolean = registerCcwFactory(implementationType.kotlin, factory)

    fun registerProjectionType(
        type: KClass<*>,
        runtimeClassName: String? = null,
    ) {
        TypeNameSupport.registerProjectionType(type, runtimeClassName)
    }

    fun registerProjectionType(
        type: Class<*>,
        runtimeClassName: String? = null,
    ) {
        registerProjectionType(type.kotlin, runtimeClassName)
    }

    fun registerProjectionAssembly(
        vararg projectionTypes: KClass<*>,
    ) {
        TypeNameSupport.registerProjectionAssembly(*projectionTypes)
    }

    fun registerProjectionAssembly(
        vararg projectionTypes: Class<*>,
    ) {
        registerProjectionAssembly(*projectionTypes.map { it.kotlin }.toTypedArray())
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

    fun registerJavaTypeRuntimeClassNameLookup(
        lookup: (Class<*>) -> String?,
    ) {
        runtimeClassNameLookups.add { type: KClass<*> -> lookup(type.registeredClass()) }
    }

    fun getInspectableInfo(pointer: NativePointer): WinRtInspectableInfo? =
        WinRtInspectableComObject.findInspectableInfo(pointer)?.let {
            WinRtInspectableInfo(it.runtimeClassName, it.interfaceIds)
        }

    fun <T : Any> findObject(
        pointer: NativePointer,
        expectedType: KClass<T>,
    ): T? = WinRtInspectableComObject.findManagedValue(pointer)?.takeIf(expectedType::isInstance) as? T

    fun <T : Any> findObject(
        pointer: NativePointer,
        expectedType: Class<T>,
    ): T? = findObject(pointer, expectedType.kotlin)

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
        WinRtBuiltInProjectionRuntimeHooks.ensureRegistered()
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
        WinRtBuiltInProjectionRuntimeHooks.ensureRegistered()
        tryUnwrapObject(value)?.use { unwrapped ->
            return if (interfaceId == null || interfaceId == unwrapped.interfaceId) {
                cloneComReference(unwrapped)
            } else {
                unwrapped.queryInterface(interfaceId).getOrThrow().use(::cloneComReference)
            }
        }

        WinRtBuiltInProjectionRuntimeHooks.tryCreateProjectedReference(value, interfaceId)?.let { return it }

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
        Projections.clearRegistriesForTests()
        TypeNameSupport.clearRegistriesForTests()
        TypeExtensions.clearRegistriesForTests()
        WinRtBuiltInProjectionRuntimeHooks.ensureRegistered()
    }

    internal fun getRuntimeClassNameForNonWinRTTypeFromLookupTable(
        type: KClass<*>,
    ): String? = runtimeClassNameLookups.firstNotNullOfOrNull { lookup ->
        lookup(type)?.takeIf { it.isNotBlank() }
    }

    internal fun getRuntimeClassNameForNonWinRTTypeFromLookupTable(
        type: Class<*>,
    ): String? = getRuntimeClassNameForNonWinRTTypeFromLookupTable(type.kotlin)

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
                WinRtValueBoxing.tryProjectInspectable(inspectable, runtimeClassName)?.let { projectedValue ->
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
            return factory(value)
        }
        WinRtBuiltInProjectionRuntimeHooks.createSyntheticCcwDefinition(value)?.let { return it }
        return WinRtCcwDefinition(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = IID.IInspectable,
                    methods = emptyList(),
                ),
            ),
            defaultInterfaceId = IID.IInspectable,
            runtimeClassName = WinRtBuiltInProjectionRuntimeHooks.runtimeClassNameFor(value),
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
