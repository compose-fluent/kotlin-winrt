package io.github.kitectlab.winrt.runtime

/**
 * JVM-side equivalent of `.cswinrt/src/WinRT.Runtime/IWinRTObject`.
 *
 * `.cswinrt` stores `QueryInterfaceCache` and `AdditionalTypeData` directly on each wrapper
 * instance. Kotlin interfaces cannot carry backing fields, so the JVM path keeps the same
 * responsibility split via a weak side-table keyed by the wrapper instance itself.
 */
data class WinRtTypeHandle(
    val projectedTypeName: String,
    val interfaceId: Guid,
)

interface IWinRTObject {
    val nativeObject: ComObjectReference

    /**
     * Kotlin-side equivalent of the "typed RCW" fast-path in cswinrt's `SingleInterfaceOptimizedObject`.
     *
     * When a wrapper already represents a specific projected interface type, later runtime projection code
     * must be able to retrieve that object reference directly instead of redundantly issuing `QueryInterface`.
     */
    val primaryTypeHandle: WinRtTypeHandle?
        get() = null

    val hasUnwrappableNativeObject: Boolean
        get() = true

    val queryInterfaceCache: ConcurrentCacheMap<WinRtTypeHandle, ComObjectReference>
        get() = WinRtObjectStateStore.stateFor(this).queryInterfaceCache

    val additionalTypeData: ConcurrentCacheMap<WinRtTypeHandle, Any>
        get() = WinRtObjectStateStore.stateFor(this).additionalTypeData

    fun isInterfaceImplemented(
        interfaceType: WinRtTypeHandle,
        throwIfNotImplemented: Boolean = false,
    ): Boolean {
        if (primaryTypeHandle == interfaceType) {
            return true
        }

        if (queryInterfaceCache.containsKey(interfaceType)) {
            return true
        }

        val queried = nativeObject.tryQueryInterface(interfaceType.interfaceId)
        if (queried != null) {
            val existing = queryInterfaceCache.putIfAbsent(interfaceType, queried)
            if (existing != null) {
                queried.close()
            }
            return true
        }

        if (throwIfNotImplemented) {
            throw WinRtUnsupportedOperationException(
                "Interface '${interfaceType.projectedTypeName}' is not implemented.",
                KnownHResults.E_NOINTERFACE,
            )
        }

        return false
    }

    fun getObjectReferenceForType(interfaceType: WinRtTypeHandle): ComObjectReference {
        if (primaryTypeHandle == interfaceType) {
            return nativeObject
        }

        if (isInterfaceImplemented(interfaceType, throwIfNotImplemented = true)) {
            return queryInterfaceCache[interfaceType]
                ?: error("Unreachable: interface presence check must populate the query-interface cache.")
        }

        error("Unreachable: interface presence check must either succeed or throw.")
    }

    fun <T : Any> getOrAddAdditionalTypeData(
        type: WinRtTypeHandle,
        factory: () -> T,
    ): T {
        val existing = additionalTypeData[type]
        if (existing != null) {
            @Suppress("UNCHECKED_CAST")
            return existing as T
        }

        val created = factory()
        val raced = additionalTypeData.putIfAbsent(type, created)
        @Suppress("UNCHECKED_CAST")
        return (raced ?: created) as T
    }
}

private object WinRtObjectStateStore {
    private val finalizationHook = FinalizationHook()
    private val states = WeakKeyStateMap<IWinRTObject, WinRtObjectStateHolder>()

    fun stateFor(instance: IWinRTObject): WinRtObjectState =
        states.getOrPut(instance) {
            createState(instance)
        }.state

    private fun createState(instance: IWinRTObject): WinRtObjectStateHolder {
        val state = WinRtObjectState()
        val cleanable = finalizationHook.register(instance) {
            state.close()
        }
        return WinRtObjectStateHolder(state, cleanable)
    }
}

private class WinRtObjectStateHolder(
    val state: WinRtObjectState,
    @Suppress("unused")
    val cleanable: AutoCloseable,
)

private class WinRtObjectState {
    val queryInterfaceCache = ConcurrentCacheMap<WinRtTypeHandle, ComObjectReference>()
    val additionalTypeData = ConcurrentCacheMap<WinRtTypeHandle, Any>()

    fun close() {
        queryInterfaceCache.values.forEach { reference ->
            runCatching { reference.close() }
        }
        queryInterfaceCache.clear()
        additionalTypeData.clear()
    }
}
