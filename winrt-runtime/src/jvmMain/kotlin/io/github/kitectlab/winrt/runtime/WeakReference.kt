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

class WeakReference<T : Any>(
    target: T? = null,
) {
    private val lock = Any()
    private var managedWeakReference = java.lang.ref.WeakReference(target)
    private var nativeWeakReference: WeakReferenceReference? = createNativeWeakReference(target)

    fun setTarget(target: T?) {
        synchronized(lock) {
            managedWeakReference = java.lang.ref.WeakReference(target)
            nativeWeakReference?.close()
            nativeWeakReference = createNativeWeakReference(target)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun tryGetTarget(): T? {
        synchronized(lock) {
            managedWeakReference.get()?.let { return it }
            val resolved = nativeWeakReference
                ?.resolve(IID.IUnknown)
                ?.use { ComWrappersSupport.createRcwForComObject(it.pointer) as? T }
            if (resolved != null) {
                managedWeakReference = java.lang.ref.WeakReference(resolved)
            }
            return resolved
        }
    }

    private fun createNativeWeakReference(target: T?): WeakReferenceReference? {
        if (target == null) {
            return null
        }
        val unwrapped = ComWrappersSupport.tryUnwrapObject(target) ?: return null
        return unwrapped.use {
            val weakReferenceSource = unwrapped.queryInterface(IID.IWeakReferenceSource).getOrNull() ?: return null
            weakReferenceSource.use { WeakReferenceSourceReference(it.pointer, IID.IWeakReferenceSource).getWeakReference() }
        }
    }
}
