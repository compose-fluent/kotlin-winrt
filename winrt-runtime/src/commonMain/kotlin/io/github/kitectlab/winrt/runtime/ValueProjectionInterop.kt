package io.github.kitectlab.winrt.runtime

/**
 * Runtime projection surface corresponding to the `IReference<T>`, `IReferenceArray<T>`,
 * and `IPropertyValue` helper layer inside `.cswinrt/src/WinRT.Runtime/Projections/Nullable.cs`.
 *
 * The shared owner keeps the projection-facing API and `NativePointer` ABI surface in common code,
 * while the remaining value-classification and adapter tables stay behind target seams until the
 * broader `ValueBoxing` owner is fully migrated.
 */
internal object WinRtReferenceProjection {
    fun createMarshaler(
        value: Any?,
        interfaceId: Guid,
    ): WinRtProjectionMarshaler? {
        if (value == null) {
            return null
        }
        val typeHandle = PlatformValueProjectionInterop.referenceTypeHandle(value, interfaceId)
        borrowedProjectionMarshaler(value, typeHandle)?.let { return it }
        return WinRtProjectionMarshaler.hosted(
            host = createReferenceHost(interfaceId, value),
            interfaceId = interfaceId,
        )
    }

    fun fromManaged(
        value: Any?,
        interfaceId: Guid,
    ): NativePointer =
        if (value == null) {
            NativeInterop.nullPointer
        } else {
            val typeHandle = PlatformValueProjectionInterop.referenceTypeHandle(value, interfaceId)
            borrowedProjectionAbi(value, typeHandle)
                ?: run {
                    val host = createReferenceHost(interfaceId, value)
                    ManagedReferenceHostSupport.detachReference(
                        createReference = { host.createReference(interfaceId).pointer },
                        releaseManagedReference = host::releaseManagedReference,
                    )
                }
        }

    fun fromAbi(
        pointer: NativePointer,
        interfaceId: Guid,
    ): Any? =
        if (NativeInterop.isNull(pointer)) {
            null
        } else {
            PlatformValueProjectionInterop.readReferenceValue(interfaceId, pointer)
        }
}

internal object WinRtReferenceArrayProjection {
    fun createMarshaler(
        value: Any?,
        interfaceId: Guid,
    ): WinRtProjectionMarshaler? {
        if (value == null) {
            return null
        }
        return WinRtProjectionMarshaler.hosted(
            host = createReferenceArrayHost(interfaceId, value),
            interfaceId = interfaceId,
        )
    }

    fun fromManaged(
        value: Any?,
        interfaceId: Guid,
    ): NativePointer =
        if (value == null) {
            NativeInterop.nullPointer
        } else {
            val host = createReferenceArrayHost(interfaceId, value)
            ManagedReferenceHostSupport.detachReference(
                createReference = { host.createReference(interfaceId).pointer },
                releaseManagedReference = host::releaseManagedReference,
            )
        }

    fun fromAbi(
        pointer: NativePointer,
        interfaceId: Guid,
    ): Array<Any?>? =
        if (NativeInterop.isNull(pointer)) {
            null
        } else {
            PlatformValueProjectionInterop.readReferenceArrayValue(interfaceId, pointer)
        }
}

internal object WinRtPropertyValueProjection {
    fun createMarshaler(value: Any?): WinRtProjectionMarshaler? {
        if (value == null || !WinRtValueBoxing.isPropertyValueCompatible(value)) {
            return null
        }
        return WinRtProjectionMarshaler.owned(
            PlatformValueProjectionInterop.createPropertyValueReference(value),
        )
    }

    fun fromManaged(value: Any?): NativePointer =
        if (value == null || !WinRtValueBoxing.isPropertyValueCompatible(value)) {
            NativeInterop.nullPointer
        } else {
            PlatformValueProjectionInterop.createPropertyValueReference(value).useAndGetRef()
        }

    fun fromOwnedAbi(pointer: NativePointer): Any? =
        if (NativeInterop.isNull(pointer)) {
            null
        } else {
            PlatformValueProjectionInterop.readOwnedPropertyValue(pointer)
        }

    fun tryFromBorrowedAbi(pointer: NativePointer): Any? =
        if (NativeInterop.isNull(pointer)) {
            null
        } else {
            PlatformValueProjectionInterop.tryProjectBorrowedPropertyValue(pointer)
        }
}
