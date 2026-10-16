@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.native.identityHashCode

// Native has no allocation-free object monitor. A bounded set of locks avoids
// allocating a CRITICAL_SECTION and Cleaner for every projected reference/state.
@PublishedApi
internal object ObjectReferenceLocks {
    private val locks = Array(64) { PlatformLock() }

    fun forOwner(owner: Any): PlatformLock {
        val hash = owner.identityHashCode()
        return locks[(hash xor (hash ushr 16)) and (locks.size - 1)]
    }
}

internal actual inline fun <T> withObjectReferenceLock(owner: Any, action: () -> T): T =
    ObjectReferenceLocks.forOwner(owner).withLock(action)
