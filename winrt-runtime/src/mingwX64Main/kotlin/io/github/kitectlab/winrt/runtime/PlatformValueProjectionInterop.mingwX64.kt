package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal actual object PlatformValueProjectionInterop {
    actual fun referenceTypeHandle(value: Any, interfaceId: Guid): WinRtTypeHandle =
        WinRtTypeHandle(value::class.qualifiedName ?: "kotlin.Any", interfaceId)

    actual fun createReferenceInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition? =
        throw NotImplementedError("IReference<T> host definition is not implemented for mingwX64 yet.")

    actual fun createReferenceArrayInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition? =
        throw NotImplementedError("IReferenceArray<T> host definition is not implemented for mingwX64 yet.")

    actual fun readReferenceValue(interfaceId: Guid, pointer: NativePointer): Any? =
        throw NotImplementedError("IReference<T> projection is not implemented for mingwX64 yet.")

    actual fun readReferenceArrayValue(interfaceId: Guid, pointer: NativePointer): Array<Any?>? =
        throw NotImplementedError("IReferenceArray<T> projection is not implemented for mingwX64 yet.")

    actual fun isPropertyValueCompatible(value: Any): Boolean = false

    actual fun propertyTypeOf(value: Any): PropertyType =
        throw NotImplementedError("Property value classification is not implemented for mingwX64 yet.")

    actual fun isNumericScalar(value: Any): Boolean =
        throw NotImplementedError("Property value classification is not implemented for mingwX64 yet.")

    actual fun boxedRuntimeClassNameForType(type: KClass<*>): String? =
        throw NotImplementedError("Boxed runtime-class-name lookup is not implemented for mingwX64 yet.")

    actual fun writePropertyValue(expectedType: PropertyType, value: Any, destination: NativePointer) {
        throw NotImplementedError("IPropertyValue host ABI writing is not implemented for mingwX64 yet.")
    }

    actual fun writePropertyValueArray(expectedType: PropertyType, value: Any, countOut: NativePointer, dataOut: NativePointer) {
        throw NotImplementedError("IPropertyValue array host ABI writing is not implemented for mingwX64 yet.")
    }

    actual fun createPropertyValueReference(value: Any): ComObjectReference =
        throw NotImplementedError("IPropertyValue host creation is not implemented for mingwX64 yet.")

    actual fun readPropertyValue(pointer: NativePointer, propertyType: PropertyType): Any? =
        throw NotImplementedError("IPropertyValue projection is not implemented for mingwX64 yet.")

    actual fun readOwnedPropertyValue(pointer: NativePointer): Any? =
        throw NotImplementedError("IPropertyValue projection is not implemented for mingwX64 yet.")

    actual fun tryProjectInspectableAsType(inspectable: IInspectableReference, projectedType: KClass<*>): Any? =
        throw NotImplementedError("Inspectable boxed projection is not implemented for mingwX64 yet.")

    actual fun tryProjectInspectableReference(inspectable: IInspectableReference): Any? =
        throw NotImplementedError("Inspectable boxed projection is not implemented for mingwX64 yet.")

    actual fun tryProjectInspectableReferenceArray(inspectable: IInspectableReference): Any? =
        throw NotImplementedError("Inspectable boxed projection is not implemented for mingwX64 yet.")

    actual fun tryProjectBorrowedPropertyValue(pointer: NativePointer): Any? = null
}
