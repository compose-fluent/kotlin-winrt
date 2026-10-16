@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.native.identityHashCode
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.windows.AcquireSRWLockExclusive
import platform.windows.InitializeSRWLock
import platform.windows.ReleaseSRWLockExclusive
import platform.windows.SRWLOCK

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

// CsWinRT publishes independently constructed dictionaries with Interlocked.
// Native's public AtomicReference API needs a wrapper per field; retain nullable
// fields instead of adding two eagerly allocated wrappers to every object state.
// This process-lifetime 8-byte lock only protects nullable-field publication;
// callers must never allocate, invoke callbacks, or recursively enter this scope.
@PublishedApi
internal object ObjectReferenceCachePublication {
    val lock = nativeHeap.alloc<SRWLOCK>().ptr.also { InitializeSRWLock(it) }
}

internal actual inline fun <T> withObjectReferenceCachePublication(owner: Any, action: () -> T): T {
    val lock = ObjectReferenceCachePublication.lock
    AcquireSRWLockExclusive(lock)
    try {
        return action()
    } finally {
        ReleaseSRWLockExclusive(lock)
    }
}
