@file:OptIn(
    kotlin.experimental.ExperimentalNativeApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlin.native.ref.createCleaner
import kotlin.native.ref.WeakReference as NativeManagedWeakReference
import platform.windows.CRITICAL_SECTION
import platform.windows.DeleteCriticalSection
import platform.windows.EnterCriticalSection
import platform.windows.InitializeCriticalSection
import platform.windows.LeaveCriticalSection

internal actual class PlatformManagedWeakReference<T : Any> actual constructor(target: T?) {
    private var delegate: NativeManagedWeakReference<T>? = target?.let(::NativeManagedWeakReference)

    actual fun get(): T? = delegate?.get()

    actual fun set(target: T?) {
        delegate = target?.let(::NativeManagedWeakReference)
    }
}

internal actual class PlatformWeakReferenceLock actual constructor() {
    private val delegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { PlatformLock() }

    actual fun enter() {
        delegate.value.enter()
    }

    actual fun exit() {
        delegate.value.exit()
    }
}

internal actual class PlatformLock actual constructor() {
    private val section = nativeHeap.alloc<CRITICAL_SECTION>().ptr

    @Suppress("unused")
    private val cleaner = createCleaner(section) { pointer ->
        DeleteCriticalSection(pointer)
        nativeHeap.free(pointer.rawValue)
    }

    init {
        InitializeCriticalSection(section)
    }

    actual fun enter() {
        EnterCriticalSection(section)
    }

    actual fun exit() {
        LeaveCriticalSection(section)
    }
}
