package io.github.kitectlab.winrt.runtime

/**
 * Runtime projection surface corresponding to the `IReference<T>`, `IReferenceArray<T>`,
 * and `IPropertyValue` helper layer inside `.cswinrt/src/WinRT.Runtime/Projections/Nullable.cs`.
 *
 * The shared owner keeps the projection-facing API and `RawAddress` ABI surface in common code,
 * while the remaining value-classification and adapter tables stay behind target seams until the
 * broader `ValueBoxing` owner is fully migrated.
 */
object WinRtReferenceProjection {
    fun createMarshaler(
        value: Any?,
        interfaceId: Guid,
    ): WinRtProjectionMarshaler? {
        if (value == null) {
            return null
        }
        val typeHandle = ValueBoxingInterop.referenceTypeHandle(value, interfaceId)
        borrowedProjectionMarshaler(value, typeHandle)?.let { return it }
        return WinRtProjectionMarshaler.hosted(
            host = createReferenceHost(interfaceId, value),
            interfaceId = interfaceId,
        )
    }

    fun fromManaged(
        value: Any?,
        interfaceId: Guid,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            val typeHandle = ValueBoxingInterop.referenceTypeHandle(value, interfaceId)
            borrowedProjectionAbi(value, typeHandle)
                ?: run {
                    val host = createReferenceHost(interfaceId, value)
                    ManagedReferenceHostSupport.detachReference(
                        createReference = { host.createReference(interfaceId).pointer.asRawAddress() },
                        releaseManagedReference = host::releaseManagedReference,
                    )
                }
        }

    fun fromAbi(
        pointer: RawAddress,
        interfaceId: Guid,
    ): Any? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            ValueBoxingInterop.readReferenceValue(interfaceId, pointer)
        }
}

object WinRtReferenceArrayProjection {
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
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            val host = createReferenceArrayHost(interfaceId, value)
            ManagedReferenceHostSupport.detachReference(
                createReference = { host.createReference(interfaceId).pointer.asRawAddress() },
                releaseManagedReference = host::releaseManagedReference,
            )
        }

    fun fromAbi(
        pointer: RawAddress,
        interfaceId: Guid,
    ): Array<Any?>? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            ValueBoxingInterop.readReferenceArrayValue(interfaceId, pointer)
        }
}

internal object WinRtPropertyValueProjection {
    fun createMarshaler(value: Any?): WinRtProjectionMarshaler? {
        if (value == null || !WinRtValueBoxing.isPropertyValueCompatible(value)) {
            return null
        }
        return WinRtProjectionMarshaler.owned(
            ValueBoxingInterop.createPropertyValueReference(value),
        )
    }

    fun fromManaged(value: Any?): RawAddress =
        if (value == null || !WinRtValueBoxing.isPropertyValueCompatible(value)) {
            PlatformAbi.nullPointer
        } else {
            ValueBoxingInterop.createPropertyValueReference(value).useAndGetRef()
        }

    fun fromOwnedAbi(pointer: RawAddress): Any? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            ValueBoxingInterop.readOwnedPropertyValue(pointer)
        }

    fun tryFromBorrowedAbi(pointer: RawAddress): Any? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            ValueBoxingInterop.tryProjectBorrowedPropertyValue(pointer)
        }
}
