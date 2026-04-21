package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal actual object PlatformValueProjectionInterop {
    actual fun referenceTypeHandle(value: Any, interfaceId: Guid): WinRtTypeHandle =
        WinRtTypeHandle(
            if (value is KClass<*>) KClass::class.typeDisplayName() else value::class.typeDisplayName(),
            interfaceId,
        )

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
