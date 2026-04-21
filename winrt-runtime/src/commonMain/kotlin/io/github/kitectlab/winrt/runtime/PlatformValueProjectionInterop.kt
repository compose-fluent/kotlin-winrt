package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal expect object PlatformValueProjectionInterop {
    fun referenceTypeHandle(value: Any, interfaceId: Guid): WinRtTypeHandle

    fun createReferenceHost(interfaceId: Guid, value: Any): ManagedReferenceHost

    fun createReferenceArrayHost(interfaceId: Guid, value: Any): ManagedReferenceHost

    fun createReferenceInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition?

    fun createReferenceArrayInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition?

    fun readReferenceValue(interfaceId: Guid, pointer: NativePointer): Any?

    fun readReferenceArrayValue(interfaceId: Guid, pointer: NativePointer): Array<Any?>?

    fun isPropertyValueCompatible(value: Any): Boolean

    fun propertyTypeOf(value: Any): PropertyType

    fun isNumericScalar(value: Any): Boolean

    fun boxedRuntimeClassNameForType(type: KClass<*>): String?

    fun writePropertyValue(expectedType: PropertyType, value: Any, destination: NativePointer)

    fun writePropertyValueArray(expectedType: PropertyType, value: Any, countOut: NativePointer, dataOut: NativePointer)

    fun createPropertyValueReference(value: Any): ComObjectReference

    fun readPropertyValue(pointer: NativePointer, propertyType: PropertyType): Any?

    fun readOwnedPropertyValue(pointer: NativePointer): Any?

    fun tryProjectInspectableAsType(inspectable: IInspectableReference, projectedType: KClass<*>): Any?

    fun tryProjectInspectableReference(inspectable: IInspectableReference): Any?

    fun tryProjectInspectableReferenceArray(inspectable: IInspectableReference): Any?

    fun tryProjectBorrowedPropertyValue(pointer: NativePointer): Any?
}
