package io.github.kitectlab.winrt.runtime

internal expect object PlatformValueProjectionInterop {
    fun referenceTypeHandle(value: Any, interfaceId: Guid): WinRtTypeHandle

    fun createReferenceHost(interfaceId: Guid, value: Any): ManagedReferenceHost

    fun createReferenceArrayHost(interfaceId: Guid, value: Any): ManagedReferenceHost

    fun readReferenceValue(interfaceId: Guid, pointer: NativePointer): Any?

    fun readReferenceArrayValue(interfaceId: Guid, pointer: NativePointer): Array<Any?>?

    fun isPropertyValueCompatible(value: Any): Boolean

    fun createPropertyValueReference(value: Any): ComObjectReference

    fun readPropertyValue(pointer: NativePointer, propertyType: PropertyType): Any?

    fun readOwnedPropertyValue(pointer: NativePointer): Any?

    fun tryProjectBorrowedPropertyValue(pointer: NativePointer): Any?
}
