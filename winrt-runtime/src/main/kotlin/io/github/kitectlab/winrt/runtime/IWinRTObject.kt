package io.github.kitectlab.winrt.runtime

import java.lang.ref.Cleaner
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

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

    val hasUnwrappableNativeObject: Boolean
        get() = true

    val queryInterfaceCache: ConcurrentHashMap<WinRtTypeHandle, ComObjectReference>
        get() = WinRtObjectStateStore.stateFor(this).queryInterfaceCache

    val additionalTypeData: ConcurrentHashMap<WinRtTypeHandle, Any>
        get() = WinRtObjectStateStore.stateFor(this).additionalTypeData

    fun isInterfaceImplemented(
        interfaceType: WinRtTypeHandle,
        throwIfNotImplemented: Boolean = false,
    ): Boolean {
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
        if (isInterfaceImplemented(interfaceType, throwIfNotImplemented = true)) {
            return queryInterfaceCache.getValue(interfaceType)
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
    private val cleaner: Cleaner = Cleaner.create()
    private val states = Collections.synchronizedMap(WeakHashMap<IWinRTObject, WinRtObjectStateHolder>())

    fun stateFor(instance: IWinRTObject): WinRtObjectState = synchronized(states) {
        states[instance]?.state ?: createState(instance)
    }

    private fun createState(instance: IWinRTObject): WinRtObjectState {
        val state = WinRtObjectState()
        val cleanable = cleaner.register(instance) {
            state.close()
        }
        states[instance] = WinRtObjectStateHolder(state, cleanable)
        return state
    }
}

private class WinRtObjectStateHolder(
    val state: WinRtObjectState,
    @Suppress("unused")
    val cleanable: Cleaner.Cleanable,
)

private class WinRtObjectState {
    val queryInterfaceCache = ConcurrentHashMap<WinRtTypeHandle, ComObjectReference>()
    val additionalTypeData = ConcurrentHashMap<WinRtTypeHandle, Any>()

    fun close() {
        queryInterfaceCache.values.forEach { reference ->
            runCatching { reference.close() }
        }
        queryInterfaceCache.clear()
        additionalTypeData.clear()
    }
}
