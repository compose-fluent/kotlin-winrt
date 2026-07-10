@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

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

    actual fun <R> withLock(block: () -> R): R {
        EnterCriticalSection(section)
        try {
            return block()
        } finally {
            LeaveCriticalSection(section)
        }
    }
}

internal actual class NativeWeakReferenceHandle internal constructor(
    val reference: WeakReferenceReference,
) : AutoCloseable {
    @Suppress("unused")
    private val cleaner = createCleaner(reference) { it.close() }

    actual override fun close() {
        reference.close()
    }
}

internal actual object WeakReferenceInterop {
    actual fun tryCreateNativeWeakReference(target: Any): NativeWeakReferenceHandle? {
        val unwrapped = ComWrappersSupport.tryUnwrapObject(target) ?: return null
        return unwrapped.use {
            val weakReferenceSource = unwrapped.queryInterface(IID.IWeakReferenceSource).getOrNull() ?: return null
            weakReferenceSource.use {
                WeakReferenceSourceReference(it.pointer.asRawAddress(), IID.IWeakReferenceSource)
                    .getWeakReference()
                    ?.let(::NativeWeakReferenceHandle)
            }
        }
    }

    actual fun resolveNativeWeakReference(reference: NativeWeakReferenceHandle): Any? =
        reference.reference.resolve(IID.IUnknown)?.use { resolved ->
            ComWrappersSupport.createRcwForComObject(resolved.pointer.asRawAddress())
        }
}
