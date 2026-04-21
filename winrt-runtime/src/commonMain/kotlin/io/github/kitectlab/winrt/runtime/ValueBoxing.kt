package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal object WinRtValueBoxing {
    fun boxedRuntimeClassNameForType(type: KClass<*>): String? =
        ValueBoxingMetadata.boxedRuntimeClassNameForType(type)

    fun isPropertyValueCompatible(value: Any): Boolean =
        ValueBoxingMetadata.isPropertyValueCompatible(value)

    fun propertyTypeOf(value: Any): PropertyType =
        ValueBoxingMetadata.propertyTypeOf(value)

    fun isNumericScalar(value: Any): Boolean =
        ValueBoxingMetadata.isNumericScalar(value)

    fun createReferenceInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition? =
        PlatformValueProjectionInterop.createReferenceInterfaceDefinition(value)

    fun createReferenceArrayInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition? =
        PlatformValueProjectionInterop.createReferenceArrayInterfaceDefinition(value)

    fun readReferenceValue(interfaceId: Guid, pointer: NativePointer): Any? =
        PlatformValueProjectionInterop.readReferenceValue(interfaceId, pointer)

    fun readReferenceArrayValue(interfaceId: Guid, pointer: NativePointer): Array<Any?>? =
        PlatformValueProjectionInterop.readReferenceArrayValue(interfaceId, pointer)

    fun writePropertyValue(expectedType: PropertyType, value: Any, destination: NativePointer) {
        PlatformValueProjectionInterop.writePropertyValue(expectedType, value, destination)
    }

    fun writePropertyValueArray(expectedType: PropertyType, value: Any, countOut: NativePointer, dataOut: NativePointer) {
        PlatformValueProjectionInterop.writePropertyValueArray(expectedType, value, countOut, dataOut)
    }

    fun tryProjectInspectableAsType(inspectable: IInspectableReference, projectedType: KClass<*>): Any? =
        PlatformValueProjectionInterop.tryProjectInspectableAsType(inspectable, projectedType)

    fun tryProjectInspectableReference(inspectable: IInspectableReference): Any? =
        PlatformValueProjectionInterop.tryProjectInspectableReference(inspectable)

    fun tryProjectInspectableReferenceArray(inspectable: IInspectableReference): Any? =
        PlatformValueProjectionInterop.tryProjectInspectableReferenceArray(inspectable)
}
