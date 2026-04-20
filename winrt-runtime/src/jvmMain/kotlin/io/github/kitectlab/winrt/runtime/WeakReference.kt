package io.github.kitectlab.winrt.runtime

private val getWeakReferenceDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
)

private val resolveWeakReferenceDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
)

internal class WeakReferenceSourceReference(
    pointer: NativePointer,
    interfaceId: Guid = IID.IWeakReferenceSource,
) : IUnknownReference(pointer, interfaceId) {
    fun getWeakReference(): WeakReferenceReference? =
        NativeInterop.confinedScope().use { scope ->
            val resultOut = NativeInterop.allocatePointerSlot(scope)
            ExceptionHelpers.throwExceptionForHR(
                invokeAbi(
                    slot = 3,
                    descriptor = getWeakReferenceDescriptor,
                    resultOut,
                ),
                operation = "IWeakReferenceSource.GetWeakReference",
            )
            val resolvedPointer = NativeInterop.readPointer(resultOut)
            if (NativeInterop.isNull(resolvedPointer)) {
                null
            } else {
                WeakReferenceReference(resolvedPointer, IID.IWeakReference)
            }
        }
}

internal class WeakReferenceReference(
    pointer: NativePointer,
    interfaceId: Guid = IID.IWeakReference,
) : IUnknownReference(pointer, interfaceId) {
    fun resolve(interfaceId: Guid): IUnknownReference? =
        NativeInterop.confinedScope().use { scope ->
            val iidMemory = NativeInterop.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(iidMemory)
            val resultOut = NativeInterop.allocatePointerSlot(scope)
            ExceptionHelpers.throwExceptionForHR(
                invokeAbi(
                    slot = 3,
                    descriptor = resolveWeakReferenceDescriptor,
                    iidMemory,
                    resultOut,
                ),
                operation = "IWeakReference.Resolve",
            )
            val resolvedPointer = NativeInterop.readPointer(resultOut)
            if (NativeInterop.isNull(resolvedPointer)) {
                null
            } else {
                IUnknownReference(resolvedPointer, interfaceId)
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
            ComWrappersSupport.createRcwForComObject(resolved.pointer.asMemorySegment())
        }
}
