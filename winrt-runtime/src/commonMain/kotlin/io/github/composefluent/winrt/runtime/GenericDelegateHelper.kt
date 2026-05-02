package io.github.composefluent.winrt.runtime

/**
 * Kotlin equivalent of `.cswinrt/src/WinRT.Runtime/Projections/GenericDelegateHelper.net5.cs`.
 *
 * `.cswinrt` caches generated delegates per `IObjectReference` in a `ConditionalWeakTable`.
 * The Kotlin runtime already uses direct vtable invocation for most RCW helpers, but later
 * projection slices still need the same weakly-keyed memoization owner for per-reference
 * delegate/lambda adapters.
 */
internal object GenericDelegateHelper {
    private val delegateTable = WeakKeyStateMap<ComObjectReference, ConcurrentCacheMap<Int, Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> createDelegate(
        reference: ComObjectReference,
        offset: Int,
        factory: (ComObjectReference) -> T,
    ): T {
        val delegates = delegateTable.getOrPut(reference) { ConcurrentCacheMap() }
        return delegates.computeIfAbsent(offset) { factory(reference) } as T
    }
}
