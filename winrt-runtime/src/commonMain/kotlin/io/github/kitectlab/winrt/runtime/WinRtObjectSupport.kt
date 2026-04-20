package io.github.kitectlab.winrt.runtime

internal class WinRtObjectSupport<K : Any, TReference : AutoCloseable>(
    private val closeReference: (TReference) -> Unit,
) {
    private val stateStore = WinRtObjectStateStore<K, TReference>(closeReference)

    fun queryInterfaceCache(instance: K): ConcurrentCacheMap<WinRtTypeHandle, TReference> =
        stateStore.stateFor(instance).queryInterfaceCache

    fun additionalTypeData(instance: K): ConcurrentCacheMap<WinRtTypeHandle, Any> =
        stateStore.stateFor(instance).additionalTypeData

    fun isInterfaceImplemented(
        instance: K,
        primaryTypeHandle: WinRtTypeHandle?,
        interfaceType: WinRtTypeHandle,
        nativeObject: TReference,
        throwIfNotImplemented: Boolean = false,
        tryQueryInterface: (Guid) -> TReference?,
        missingInterfaceError: (WinRtTypeHandle) -> Throwable,
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
        primaryTypeHandle: WinRtTypeHandle?,
        interfaceType: WinRtTypeHandle,
        nativeObject: TReference,
        tryQueryInterface: (Guid) -> TReference?,
        missingInterfaceError: (WinRtTypeHandle) -> Throwable,
    ): TReference {
        if (primaryTypeHandle == interfaceType) {
            return nativeObject
        }

        if (isInterfaceImplemented(
                instance = instance,
                primaryTypeHandle = primaryTypeHandle,
                interfaceType = interfaceType,
                nativeObject = nativeObject,
                throwIfNotImplemented = true,
                tryQueryInterface = tryQueryInterface,
                missingInterfaceError = missingInterfaceError,
            )
        ) {
            return queryInterfaceCache(instance)[interfaceType]
                ?: error("Unreachable: interface presence check must populate the query-interface cache.")
        }

        error("Unreachable: interface presence check must either succeed or throw.")
    }

    fun <T : Any> getOrAddAdditionalTypeData(
        instance: K,
        type: WinRtTypeHandle,
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

private class WinRtObjectStateStore<K : Any, TReference : AutoCloseable>(
    private val closeReference: (TReference) -> Unit,
) {
    private val finalizationHook = FinalizationHook()
    private val states = WeakKeyStateMap<K, WinRtObjectStateHolder<TReference>>()

    fun stateFor(instance: K): WinRtObjectState<TReference> =
        states.getOrPut(instance) {
            createState(instance)
        }.state

    private fun createState(instance: K): WinRtObjectStateHolder<TReference> {
        val state = WinRtObjectState(closeReference)
        val cleanable = finalizationHook.register(instance) {
            state.close()
        }
        return WinRtObjectStateHolder(state, cleanable)
    }
}

private class WinRtObjectStateHolder<TReference : AutoCloseable>(
    val state: WinRtObjectState<TReference>,
    @Suppress("unused")
    val cleanable: AutoCloseable,
)

private class WinRtObjectState<TReference : AutoCloseable>(
    private val closeReference: (TReference) -> Unit,
) {
    val queryInterfaceCache = ConcurrentCacheMap<WinRtTypeHandle, TReference>()
    val additionalTypeData = ConcurrentCacheMap<WinRtTypeHandle, Any>()

    fun close() {
        queryInterfaceCache.values.forEach { reference ->
            runCatching { closeReference(reference) }
        }
        queryInterfaceCache.clear()
        additionalTypeData.clear()
    }
}
