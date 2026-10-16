package io.github.composefluent.winrt.runtime

import kotlinx.atomicfu.atomic

internal class WinRTObjectSupport<K : Any, TReference : AutoCloseable>(
    private val queryInterfaceCacheFor: (K) -> ConcurrentCacheMap<WinRTTypeHandle, TReference>,
    private val additionalTypeDataFor: (K) -> ConcurrentCacheMap<WinRTTypeHandle, Any>,
    private val closeReference: (TReference) -> Unit,
    private val validateReference: (TReference) -> Unit,
    private val ownReference: (TReference, TReference) -> TReference,
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
        validateReference(nativeObject)
        if (primaryTypeHandle == interfaceType) {
            return true
        }

        val queryInterfaceCache = queryInterfaceCache(instance)
        queryInterfaceCache[interfaceType]?.let {
            validateReference(it)
            return true
        }

        val queried = tryQueryInterface(interfaceType.interfaceId)
        if (queried != null) {
            val existing = queryInterfaceCache.putIfAbsent(interfaceType, queried)
            if (existing != null) {
                closeReference(queried)
                validateReference(existing)
            } else {
                ownReference(nativeObject, queried)
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
        validateReference(nativeObject)
        if (primaryTypeHandle == interfaceType) {
            return nativeObject
        }

        val queryInterfaceCache = queryInterfaceCache(instance)
        queryInterfaceCache[interfaceType]?.let {
            validateReference(it)
            return it
        }

        val queried = tryQueryInterface(interfaceType.interfaceId)
        if (queried != null) {
            val existing = queryInterfaceCache.putIfAbsent(interfaceType, queried)
            if (existing != null) {
                closeReference(queried)
                validateReference(existing)
                return existing
            }
            return ownReference(nativeObject, queried)
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
    private val queryInterfaces = atomic<ConcurrentCacheMap<WinRTTypeHandle, TReference>?>(null)

    private val typeData = atomic<ConcurrentCacheMap<WinRTTypeHandle, Any>?>(null)

    val queryInterfaceCache: ConcurrentCacheMap<WinRTTypeHandle, TReference>
        get() = queryInterfaces.value ?: initializeQueryInterfaceCache()

    private fun initializeQueryInterfaceCache(): ConcurrentCacheMap<WinRTTypeHandle, TReference> {
        // CsWinRT WindowsRuntimeObject.TypeHandleCache constructs before publishing.
        // AtomicFU lowers this to a field CAS, without a lock or atomic wrapper.
        val candidate = ConcurrentCacheMap<WinRTTypeHandle, TReference>(1)
        return if (queryInterfaces.compareAndSet(null, candidate)) candidate else checkNotNull(queryInterfaces.value)
    }

    val additionalTypeData: ConcurrentCacheMap<WinRTTypeHandle, Any>
        get() = typeData.value ?: initializeAdditionalTypeData()

    private fun initializeAdditionalTypeData(): ConcurrentCacheMap<WinRTTypeHandle, Any> {
        val candidate = ConcurrentCacheMap<WinRTTypeHandle, Any>(1)
        return if (typeData.compareAndSet(null, candidate)) candidate else checkNotNull(typeData.value)
    }
}
