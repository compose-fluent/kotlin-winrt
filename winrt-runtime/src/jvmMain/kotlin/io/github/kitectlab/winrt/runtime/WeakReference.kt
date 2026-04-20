package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal class WeakReferenceSourceReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IWeakReferenceSource,
) : IUnknownReference(pointer, interfaceId) {
    fun getWeakReference(): WeakReferenceReference? {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            ExceptionHelpers.throwExceptionForHR(
                invokeAbi(
                    slot = 3,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    resultOut,
                ),
                operation = "IWeakReferenceSource.GetWeakReference",
            )
            val pointer = resultOut.get(ValueLayout.ADDRESS, 0)
            return if (pointer == MemorySegment.NULL) {
                null
            } else {
                WeakReferenceReference(pointer, IID.IWeakReference)
            }
        }
    }
}

internal class WeakReferenceReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IWeakReference,
) : IUnknownReference(pointer, interfaceId) {
    fun resolve(interfaceId: Guid): IUnknownReference? {
        Arena.ofConfined().use { arena ->
            val iidMemory = arena.allocate(AbiLayouts.GUID)
            interfaceId.writeTo(iidMemory)
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            ExceptionHelpers.throwExceptionForHR(
                invokeAbi(
                    slot = 3,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    iidMemory,
                    resultOut,
                ),
                operation = "IWeakReference.Resolve",
            )
            val pointer = resultOut.get(ValueLayout.ADDRESS, 0)
            return if (pointer == MemorySegment.NULL) {
                null
            } else {
                IUnknownReference(pointer, interfaceId)
            }
        }
    }
}

internal actual class PlatformManagedWeakReference<T : Any> actual constructor(target: T?) {
    private var delegate = java.lang.ref.WeakReference(target)

    actual fun get(): T? = delegate.get()

    actual fun set(target: T?) {
        delegate = java.lang.ref.WeakReference(target)
    }
}

internal actual class PlatformLock actual constructor() {
    private val monitor = Any()

    actual fun <R> withLock(block: () -> R): R = synchronized(monitor) { block() }
}

internal actual class NativeWeakReferenceHandle internal constructor(
    val reference: WeakReferenceReference,
) : AutoCloseable {
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
                WeakReferenceSourceReference(it.pointer, IID.IWeakReferenceSource)
                    .getWeakReference()
                    ?.let(::NativeWeakReferenceHandle)
            }
        }
    }

    actual fun resolveNativeWeakReference(reference: NativeWeakReferenceHandle): Any? =
        reference.reference.resolve(IID.IUnknown)?.use { resolved ->
            ComWrappersSupport.createRcwForComObject(resolved.pointer)
        }
}
