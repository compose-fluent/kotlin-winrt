package io.github.kitectlab.winrt.runtime

internal actual object PlatformValueProjectionInterop {
    actual fun referenceTypeHandle(value: Any, interfaceId: Guid): WinRtTypeHandle =
        WinRtTypeHandle(value::class.qualifiedName ?: value::class.toString(), interfaceId)

    actual fun createReferenceHost(interfaceId: Guid, value: Any): ManagedReferenceHost =
        WinRtValueBoxing.createReferenceHost(interfaceId, value)

    actual fun createReferenceArrayHost(interfaceId: Guid, value: Any): ManagedReferenceHost =
        WinRtValueBoxing.createReferenceArrayHost(interfaceId, value)

    actual fun readReferenceValue(interfaceId: Guid, pointer: NativePointer): Any? =
        WinRtReferenceReference(pointer, interfaceId).use { reference ->
            WinRtValueBoxing.readReferenceValue(interfaceId, reference)
        }

    actual fun readReferenceArrayValue(interfaceId: Guid, pointer: NativePointer): Array<Any?>? =
        WinRtReferenceArrayReference(pointer, interfaceId).use { reference ->
            WinRtValueBoxing.readReferenceArrayValue(interfaceId, reference)
        }

    actual fun isPropertyValueCompatible(value: Any): Boolean =
        WinRtValueBoxing.isPropertyValueCompatible(value)

    actual fun createPropertyValueReference(value: Any): ComObjectReference =
        ComWrappersSupport.createCCWForObject(value, IID.IPropertyValue)

    actual fun readOwnedPropertyValue(pointer: NativePointer): Any? =
        WinRtPropertyValueReference(pointer).use { it.getValue() }

    actual fun tryProjectBorrowedPropertyValue(pointer: NativePointer): Any? {
        val propertyValue =
            runCatching {
                IUnknownReference(pointer, IID.IInspectable, preventReleaseOnDispose = true)
                    .queryInterface(IID.IPropertyValue)
                    .getOrThrow()
            }.getOrNull() ?: return null
        return propertyValue.use { reference ->
            WinRtPropertyValueReference(reference.pointer, preventReleaseOnDispose = true).getValue()
        }
    }
}
