package io.github.composefluent.winrt.runtime

internal class WinRTObjectSupport<K : Any, TReference : AutoCloseable>(
    private val closeReference: (TReference) -> Unit,
) {
    private val stateStore = WinRTObjectStateStore<K, TReference>(closeReference)

    fun queryInterfaceCache(instance: K): ConcurrentCacheMap<WinRTTypeHandle, TReference> =
        stateStore.stateFor(instance).queryInterfaceCache

    fun additionalTypeData(instance: K): ConcurrentCacheMap<WinRTTypeHandle, Any> =
        stateStore.stateFor(instance).additionalTypeData

    fun isInterfaceImplemented(
        instance: K,
        primaryTypeHandle: WinRTTypeHandle?,
        interfaceType: WinRTTypeHandle,
        nativeObject: TReference,
        throwIfNotImplemented: Boolean = false,
        tryQueryInterface: (Guid) -> TReference?,
        missingInterfaceError: (WinRTTypeHandle) -> Throwable,
    ): Boolean {
        if (primaryTypeHandle == interfaceType) {
            return true
        }

        val queryInterfaceCache = queryInterfaceCache(instance)
        if (queryInterfaceCache.containsKey(interfaceType)) {
            return true
        }

        val queried = tryQueryInterface(interfaceType.interfaceId)
        if (queried != null) {
            val existing = queryInterfaceCache.putIfAbsent(interfaceType, queried)
            if (existing != null) {
                closeReference(queried)
            }
            return true
        }

        if (throwIfNotImplemented) {
            throw missingInterfaceError(interfaceType)
        }

        return false
    }

    fun getObjectReferenceForType(
        instance: K,
        primaryTypeHandle: WinRTTypeHandle?,
        interfaceType: WinRTTypeHandle,
        nativeObject: TReference,
        tryQueryInterface: (Guid) -> TReference?,
        missingInterfaceError: (WinRTTypeHandle) -> Throwable,
    ): TReference {
        if (primaryTypeHandle == interfaceType) {
            return nativeObject
        }

        val queryInterfaceCache = queryInterfaceCache(instance)
        queryInterfaceCache[interfaceType]?.let { return it }

        val queried = tryQueryInterface(interfaceType.interfaceId)
        if (queried != null) {
            val existing = queryInterfaceCache.putIfAbsent(interfaceType, queried)
            if (existing != null) {
                closeReference(queried)
                return existing
            }
            return queried
        }

        throw missingInterfaceError(interfaceType)
    }

    fun <T : Any> getOrAddAdditionalTypeData(
        instance: K,
        type: WinRTTypeHandle,
        factory: () -> T,
    ): T {
        val additionalTypeData = additionalTypeData(instance)
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

private class WinRTObjectStateStore<K : Any, TReference : AutoCloseable>(
    private val closeReference: (TReference) -> Unit,
) {
    private val finalizationHook = FinalizationHook()
    private val states = WeakKeyStateMap<K, WinRTObjectStateHolder<TReference>>()

    fun stateFor(instance: K): WinRTObjectState<TReference> =
        states.getOrPut(instance) {
            createState(instance)
        }.state

    private fun createState(instance: K): WinRTObjectStateHolder<TReference> {
        val state = WinRTObjectState(closeReference)
        val cleanable = finalizationHook.register(instance) {
            state.close()
        }
        return WinRTObjectStateHolder(state, cleanable)
    }
}

private class WinRTObjectStateHolder<TReference : AutoCloseable>(
    val state: WinRTObjectState<TReference>,
    @Suppress("unused")
    val cleanable: AutoCloseable,
)

private class WinRTObjectState<TReference : AutoCloseable>(
    private val closeReference: (TReference) -> Unit,
) {
    val queryInterfaceCache = ConcurrentCacheMap<WinRTTypeHandle, TReference>()
    val additionalTypeData = ConcurrentCacheMap<WinRTTypeHandle, Any>()

    fun close() {
        queryInterfaceCache.values.forEach { reference ->
            runCatching { closeReference(reference) }
        }
        queryInterfaceCache.clear()
        additionalTypeData.clear()
    }
}
