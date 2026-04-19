package io.github.kitectlab.winrt.runtime

import java.lang.ref.WeakReference
import java.lang.foreign.MemorySegment
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * Kotlin runtime ownership layer corresponding to `.cswinrt/src/WinRT.Runtime/ComWrappersSupport*`.
 *
 * This slice owns typed RCW factory registration, runtime-class-name-based wrapper lookup, managed CCW
 * factory registration, unwrap helpers, and the Kotlin equivalent of `SingleInterfaceOptimizedObject`.
 * Universal marshaling policy still belongs to Runtime 1.14+.
 */
data class WinRtInspectableInfo(
    val runtimeClassName: String?,
    val interfaceIds: List<Guid>,
)

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
    private val typedRcwFactories = ConcurrentHashMap<WinRtTypeHandle, (IInspectableReference) -> Any>()
    private val runtimeClassFactories = ConcurrentHashMap<String, (IInspectableReference) -> Any>()
    private val helperTypeRegistry = ConcurrentHashMap<WinRtTypeHandle, WinRtTypeHandle>()
    private val ccwFactories = ConcurrentHashMap<Class<*>, (Any) -> WinRtCcwDefinition>()
    private val rcwCache = ConcurrentHashMap<Long, WeakReference<Any>>()
    private val runtimeClassNameLookups = CopyOnWriteArrayList<(Class<*>) -> String?>()

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
        implementationType: Class<*>,
        factory: (Any) -> WinRtCcwDefinition,
    ): Boolean = ccwFactories.putIfAbsent(implementationType, factory) == null

    fun registerProjectionType(
        type: Class<*>,
        runtimeClassName: String? = null,
    ) {
        TypeNameSupport.registerProjectionType(type, runtimeClassName)
    }

    fun registerProjectionAssembly(
        vararg projectionTypes: Class<*>,
    ) {
        TypeNameSupport.registerProjectionAssembly(*projectionTypes)
    }

    fun registerProjectionTypeBaseTypeMapping(
        typeNameToBaseTypeNameMapping: Map<String, String>,
    ) {
        TypeNameSupport.registerProjectionTypeBaseTypeMapping(typeNameToBaseTypeNameMapping)
    }

    fun registerTypeRuntimeClassNameLookup(
        lookup: (Class<*>) -> String?,
    ) {
        runtimeClassNameLookups += lookup
    }

    fun getInspectableInfo(pointer: MemorySegment): WinRtInspectableInfo? =
        WinRtInspectableComObject.findInspectableInfo(pointer)?.let {
            WinRtInspectableInfo(it.runtimeClassName, it.interfaceIds)
        }

    fun <T : Any> findObject(
        pointer: MemorySegment,
        expectedType: Class<T>,
    ): T? = WinRtInspectableComObject.findManagedValue(pointer)?.let(expectedType::cast)

    fun tryUnwrapObject(
        value: Any?,
        interfaceType: WinRtTypeHandle? = null,
    ): ComObjectReference? {
        val winrtObject = value as? IWinRTObject ?: return null
        if (!winrtObject.hasUnwrappableNativeObject) {
            return null
        }
        return when {
            interfaceType == null -> cloneReference(winrtObject.nativeObject)
            winrtObject.isInterfaceImplemented(interfaceType, false) ->
                cloneReference(winrtObject.getObjectReferenceForType(interfaceType))

            else -> null
        }
    }

    fun createRcwForComObject(
        pointer: MemorySegment,
        staticallyDeterminedType: WinRtTypeHandle? = null,
        tryUseCache: Boolean = true,
    ): Any? {
        if (pointer == MemorySegment.NULL) {
            return null
        }

        val pointerKey = pointer.address()
        if (tryUseCache) {
            rcwCache[pointerKey]?.get()?.let { cached ->
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
            rcwCache[pointerKey] = WeakReference(rcw)
        }
        return rcw
    }

    fun createCCWForObject(
        value: Any,
        interfaceId: Guid? = null,
    ): ComObjectReference {
        tryUnwrapObject(value)?.use { unwrapped ->
            return if (interfaceId == null || interfaceId == unwrapped.interfaceId) {
                cloneReference(unwrapped)
            } else {
                unwrapped.queryInterface(interfaceId).getOrThrow().use(::cloneReference)
            }
        }

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
    }

    internal fun getRuntimeClassNameForNonWinRTTypeFromLookupTable(
        type: Class<*>,
    ): String? = runtimeClassNameLookups.firstNotNullOfOrNull { lookup ->
        lookup(type)?.takeIf { it.isNotBlank() }
    }

    private fun createRcwCore(
        pointer: MemorySegment,
        staticallyDeterminedType: WinRtTypeHandle?,
    ): Any? {
        val inspectable = wrapInspectable(pointer)
        if (inspectable != null) {
            resolveFactory(staticallyDeterminedType, inspectable.tryGetRuntimeClassName())?.let { factory ->
                return factory(inspectable)
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

    private fun wrapInspectable(pointer: MemorySegment): IInspectableReference? {
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
        return WinRtCcwDefinition(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = IID.IInspectable,
                    methods = emptyList(),
                ),
            ),
            defaultInterfaceId = IID.IInspectable,
            runtimeClassName = value::class.qualifiedName,
        )
    }

    private fun findCcwFactory(value: Any): ((Any) -> WinRtCcwDefinition)? {
        ccwFactories[value.javaClass]?.let { return it }
        return ccwFactories.entries.firstOrNull { (type, _) -> type.isInstance(value) }?.value
    }

    private fun ownedReference(
        host: WinRtInspectableComObject,
        interfaceId: Guid,
    ): ComObjectReference {
        val inner = host.createReference(interfaceId)
        return OwnedCcwReference(
            inner = inner,
            cleanup = host::releaseManagedReference,
        )
    }

    private fun cloneReference(reference: ComObjectReference): ComObjectReference =
        when (reference) {
            is ActivationFactoryReference -> ActivationFactoryReference(reference.getRef(), reference.interfaceId)
            is IInspectableReference -> IInspectableReference(reference.getRef(), reference.interfaceId)
            else -> IUnknownReference(reference.getRef(), reference.interfaceId)
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
