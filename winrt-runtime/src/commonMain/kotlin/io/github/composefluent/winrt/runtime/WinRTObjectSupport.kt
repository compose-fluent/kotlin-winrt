package io.github.composefluent.winrt.runtime

internal class WinRTObjectSupport<K : Any, TReference : AutoCloseable>(
    private val queryInterfaceCacheFor: (K) -> ConcurrentCacheMap<WinRTTypeHandle, TReference>,
    private val additionalTypeDataFor: (K) -> ConcurrentCacheMap<WinRTTypeHandle, Any>,
    private val closeReference: (TReference) -> Unit,
) {
    fun queryInterfaceCache(instance: K): ConcurrentCacheMap<WinRTTypeHandle, TReference> =
        queryInterfaceCacheFor(instance)

    fun additionalTypeData(instance: K): ConcurrentCacheMap<WinRTTypeHandle, Any> =
        additionalTypeDataFor(instance)

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

// CsWinRT IInspectable.net5.cs and SingleInterfaceOptimizedObject.net5.cs own these
// caches on each RCW. A global weak-key table with strong values can retain its key
// through cached callbacks or tracker edges. Each cached reference already owns its
// native-only ComPtr cleaner; no cleaner may capture this managed cache graph.
internal class WinRTObjectState<TReference : AutoCloseable> {
    // CsWinRT 3.0 code_writers.h emits independent nullable cache fields. In
    // particular, using event/helper data must not allocate an unused QI cache
    // (including its OS lock and Cleaner on Native), or vice versa.
    @kotlin.concurrent.Volatile
    private var queryInterfaces: ConcurrentCacheMap<WinRTTypeHandle, TReference>? = null

    @kotlin.concurrent.Volatile
    private var typeData: ConcurrentCacheMap<WinRTTypeHandle, Any>? = null

    val queryInterfaceCache: ConcurrentCacheMap<WinRTTypeHandle, TReference>
        get() = queryInterfaces ?: initializeQueryInterfaceCache()

    private fun initializeQueryInterfaceCache(): ConcurrentCacheMap<WinRTTypeHandle, TReference> =
        WinRTObjectStateInitialization.lock.withLock {
            // Retain CsWinRT's independent per-object dictionaries, but start small:
            // Native eagerly allocates backing arrays even before the first insertion.
            queryInterfaces ?: ConcurrentCacheMap<WinRTTypeHandle, TReference>(1).also { queryInterfaces = it }
        }

    val additionalTypeData: ConcurrentCacheMap<WinRTTypeHandle, Any>
        get() = typeData ?: initializeAdditionalTypeData()

    private fun initializeAdditionalTypeData(): ConcurrentCacheMap<WinRTTypeHandle, Any> =
        WinRTObjectStateInitialization.lock.withLock {
            typeData ?: ConcurrentCacheMap<WinRTTypeHandle, Any>(1).also { typeData = it }
        }
}

internal object WinRTObjectStateInitialization {
    val lock = PlatformLock()
}
