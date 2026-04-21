package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal actual object PlatformValueProjectionInterop {
    actual fun referenceTypeHandle(value: Any, interfaceId: Guid): WinRtTypeHandle =
        WinRtTypeHandle(
            if (value is KClass<*>) KClass::class.typeDisplayName() else value::class.typeDisplayName(),
            interfaceId,
        )

    actual fun createReferenceInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition? =
        WinRtValueBoxing.createReferenceInterfaceDefinition(value)

    actual fun createReferenceArrayInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition? =
        WinRtValueBoxing.createReferenceArrayInterfaceDefinition(value)

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

    actual fun propertyTypeOf(value: Any): PropertyType =
        WinRtValueBoxing.propertyTypeOf(value)

    actual fun isNumericScalar(value: Any): Boolean =
        WinRtValueBoxing.isNumericScalar(value)

    actual fun boxedRuntimeClassNameForType(type: KClass<*>): String? =
        WinRtValueBoxing.boxedRuntimeClassNameForType(type)

    actual fun writePropertyValue(expectedType: PropertyType, value: Any, destination: NativePointer) {
        WinRtValueBoxing.writePropertyValue(expectedType, value, destination.asMemorySegment())
    }

    actual fun writePropertyValueArray(expectedType: PropertyType, value: Any, countOut: NativePointer, dataOut: NativePointer) {
        WinRtValueBoxing.writePropertyValueArray(expectedType, value, countOut.asMemorySegment(), dataOut.asMemorySegment())
    }

    actual fun createPropertyValueReference(value: Any): ComObjectReference =
        createPropertyValueHost(value).createPrimaryReference()

    actual fun readPropertyValue(pointer: NativePointer, propertyType: PropertyType): Any? {
        val scalarAdapter = WinRtValueBoxing.adapterForPropertyType(propertyType)
        if (scalarAdapter != null) {
            return NativeInterop.confinedScope().use { scope ->
                val resultOut = NativeInterop.allocateBytes(scope, scalarAdapter.abiLayout.byteSize(), scalarAdapter.abiLayout.byteAlignment())
                val slot = 8 + (propertyType.code - PropertyType.UInt8.code)
                val hr =
                    IUnknownReference(pointer, IID.IPropertyValue, preventReleaseOnDispose = true).invokeAbi(
                        slot = slot,
                        descriptor = NativeFunctionDescriptor.of(
                            NativeValueLayout.JAVA_INT,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
                        ),
                        resultOut,
                    )
                WinRtPlatformApi.checkSucceededRaw(hr)
                try {
                    scalarAdapter.readValue(resultOut)
                } finally {
                    scalarAdapter.disposeValue(resultOut)
                }
            }
        }

        val arrayAdapter =
            when (propertyType) {
                PropertyType.InspectableArray -> WinRtValueBoxing.inspectableArrayAdapter()
                else -> WinRtValueBoxing.adapterForPropertyTypeArray(propertyType)
            }
        if (arrayAdapter != null) {
            return NativeInterop.confinedScope().use { scope ->
                val countOut = NativeInterop.allocateInt32Slot(scope)
                val dataOut = NativeInterop.allocatePointerSlot(scope)
                val slot = 26 + (propertyType.code - PropertyType.UInt8Array.code)
                val hr =
                    IUnknownReference(pointer, IID.IPropertyValue, preventReleaseOnDispose = true).invokeAbi(
                        slot = slot,
                        descriptor = NativeFunctionDescriptor.of(
                            NativeValueLayout.JAVA_INT,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
                        ),
                        countOut,
                        dataOut,
                    )
                WinRtPlatformApi.checkSucceededRaw(hr)
                val length = NativeInterop.readInt32(countOut)
                val data = NativeInterop.readPointer(dataOut)
                try {
                    arrayAdapter.readOwnedArray(length, data)
                } finally {
                    arrayAdapter.disposeOwnedArray(length, data)
                }
            }
        }

        return null
    }

    actual fun readOwnedPropertyValue(pointer: NativePointer): Any? =
        WinRtPropertyValueReference(pointer).use { it.getValue() }

    actual fun tryProjectInspectableAsType(inspectable: IInspectableReference, projectedType: KClass<*>): Any? =
        WinRtValueBoxing.tryProjectInspectableAsType(inspectable, projectedType)

    actual fun tryProjectInspectableReference(inspectable: IInspectableReference): Any? =
        WinRtValueBoxing.tryProjectInspectableReference(inspectable)

    actual fun tryProjectInspectableReferenceArray(inspectable: IInspectableReference): Any? =
        WinRtValueBoxing.tryProjectInspectableReferenceArray(inspectable)

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
