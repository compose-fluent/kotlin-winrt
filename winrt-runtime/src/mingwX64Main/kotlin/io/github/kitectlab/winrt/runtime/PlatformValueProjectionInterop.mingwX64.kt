package io.github.kitectlab.winrt.runtime

internal actual object PlatformValueProjectionInterop {
    actual fun referenceTypeHandle(value: Any, interfaceId: Guid): WinRtTypeHandle =
        WinRtTypeHandle(value::class.qualifiedName ?: "kotlin.Any", interfaceId)

    actual fun createReferenceHost(interfaceId: Guid, value: Any): ManagedReferenceHost =
        throw NotImplementedError("IReference<T> host creation is not implemented for mingwX64 yet.")

    actual fun createReferenceArrayHost(interfaceId: Guid, value: Any): ManagedReferenceHost =
        throw NotImplementedError("IReferenceArray<T> host creation is not implemented for mingwX64 yet.")

    actual fun readReferenceValue(interfaceId: Guid, pointer: NativePointer): Any? =
        throw NotImplementedError("IReference<T> projection is not implemented for mingwX64 yet.")

    actual fun readReferenceArrayValue(interfaceId: Guid, pointer: NativePointer): Array<Any?>? =
        throw NotImplementedError("IReferenceArray<T> projection is not implemented for mingwX64 yet.")

    actual fun isPropertyValueCompatible(value: Any): Boolean = false

    actual fun createPropertyValueReference(value: Any): ComObjectReference =
        throw NotImplementedError("IPropertyValue host creation is not implemented for mingwX64 yet.")

    actual fun readPropertyValue(pointer: NativePointer, propertyType: PropertyType): Any? =
        throw NotImplementedError("IPropertyValue projection is not implemented for mingwX64 yet.")

    actual fun readOwnedPropertyValue(pointer: NativePointer): Any? =
        throw NotImplementedError("IPropertyValue projection is not implemented for mingwX64 yet.")

    actual fun tryProjectBorrowedPropertyValue(pointer: NativePointer): Any? = null
}
