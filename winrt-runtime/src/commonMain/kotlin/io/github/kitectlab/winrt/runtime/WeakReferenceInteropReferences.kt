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

/**
 * Shared ABI-facing weak-reference wrappers corresponding to the COM reference flow in
 * `.cswinrt/src/WinRT.Runtime/WeakReference.netstandard2.0.cs`.
 *
 * Managed weak-reference storage and object re-projection stay behind platform seams; the
 * raw `IWeakReferenceSource` / `IWeakReference` call shapes themselves are target-agnostic.
 */
internal class WeakReferenceSourceReference(
    pointer: NativePointer,
    interfaceId: Guid = IID.IWeakReferenceSource,
) : IUnknownReference(pointer, interfaceId) {
    fun getWeakReference(): WeakReferenceReference? =
        NativeInterop.confinedScope().use { scope ->
            val resultOut = NativeInterop.allocatePointerSlot(scope)
            HResult(
                invokeAbi(
                    slot = 3,
                    descriptor = getWeakReferenceDescriptor,
                    resultOut,
                ),
            ).requireSuccess("IWeakReferenceSource.GetWeakReference")
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
            HResult(
                invokeAbi(
                    slot = 3,
                    descriptor = resolveWeakReferenceDescriptor,
                    iidMemory,
                    resultOut,
                ),
            ).requireSuccess("IWeakReference.Resolve")
            val resolvedPointer = NativeInterop.readPointer(resultOut)
            if (NativeInterop.isNull(resolvedPointer)) {
                null
            } else {
                IUnknownReference(resolvedPointer, interfaceId)
            }
        }
}
