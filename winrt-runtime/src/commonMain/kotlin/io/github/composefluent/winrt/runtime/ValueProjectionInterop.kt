package io.github.composefluent.winrt.runtime

/**
 * Runtime projection surface corresponding to the `IReference<T>`, `IReferenceArray<T>`,
 * and `IPropertyValue` helper layer inside `.cswinrt/src/WinRT.Runtime/Projections/Nullable.cs`.
 *
 * The shared owner keeps the projection-facing API and `RawAddress` ABI surface in common code,
 * while the remaining value-classification and adapter tables stay behind target seams until the
 * broader `ValueBoxing` owner is fully migrated.
 */
object WinRTReferenceProjection {
    fun createMarshaler(
        value: Any?,
        interfaceId: Guid,
    ): WinRTProjectionMarshaler? {
        if (value == null) {
            return null
        }
        if (WinRTValueBoxing.isDirectReferenceValue(value, interfaceId)) {
            return WinRTProjectionMarshaler.hosted(
                host = createReferenceMarshalerHost(interfaceId, value),
                interfaceId = interfaceId,
            )
        }
        val typeHandle = ValueBoxingInterop.referenceTypeHandle(value, interfaceId)
        borrowedProjectionMarshaler(value, typeHandle)?.let { return it }
        return WinRTProjectionMarshaler.hosted(
            host = createReferenceMarshalerHost(interfaceId, value),
            interfaceId = interfaceId,
        )
    }

    fun fromManaged(
        value: Any?,
        interfaceId: Guid,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else if (WinRTValueBoxing.isDirectReferenceValue(value, interfaceId)) {
            createReferenceHost(interfaceId, value).detachReference(interfaceId)
        } else {
            val typeHandle = ValueBoxingInterop.referenceTypeHandle(value, interfaceId)
            borrowedProjectionAbi(value, typeHandle)
                ?: createReferenceHost(interfaceId, value).detachReference(interfaceId)
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

object WinRTReferenceProjectionInterop {
    @Suppress("UNCHECKED_CAST")
    fun <T> getReferenceValue(
        reference: ComObjectReference,
        slot: Int,
        interfaceId: Guid,
    ): T =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hr = ComVtableInvoker.invokeArgs(
                instance = reference.pointer,
                slot = slot,
                arg0 = resultOut,
            )
            HResult(hr).requireSuccess()
            val result = PlatformAbi.readPointer(resultOut)
            try {
                WinRTReferenceProjection.fromAbi(result, interfaceId) as T
            } finally {
                if (!PlatformAbi.isNull(result)) {
                    IUnknownReference(result.asRawComPtr()).close()
                }
            }
        }

    fun setReferenceValue(
        reference: ComObjectReference,
        slot: Int,
        value: Any?,
        interfaceId: Guid,
    ) {
        if (value == null) {
            val hr = ComVtableInvoker.invokeArgs(
                instance = reference.pointer,
                slot = slot,
                arg0 = PlatformAbi.nullPointer,
            )
            HResult(hr).requireSuccess()
            return
        }

        // A property setter consumes this ABI pointer synchronously.  Keep the temporary CCW
        // alive for the call, but do not manufacture an owned ComObjectReference only to pass
        // the pointer across the same stack frame.  If the callee retains the argument it must
        // AddRef it, which keeps the host alive after the temporary baseline is released here.
        if (!WinRTValueBoxing.isDirectReferenceValue(value, interfaceId)) {
            val typeHandle = ValueBoxingInterop.referenceTypeHandle(value, interfaceId)
            borrowedProjectionAbi(value, typeHandle)?.let { borrowedAbi ->
                val hr = ComVtableInvoker.invokeArgs(
                    instance = reference.pointer,
                    slot = slot,
                    arg0 = borrowedAbi,
                )
                HResult(hr).requireSuccess()
                return
            }
        }

        val host = createReferenceMarshalerHost(interfaceId, value)
        try {
            val hr = ComVtableInvoker.invokeArgs(
                instance = reference.pointer,
                slot = slot,
                arg0 = host.borrowCachedInterfacePointer(interfaceId),
            )
            HResult(hr).requireSuccess()
        } finally {
            host.close()
        }
    }
}

object WinRTReferenceArrayProjection {
    fun createMarshaler(
        value: Any?,
        interfaceId: Guid,
    ): WinRTProjectionMarshaler? {
        if (value == null) {
            return null
        }
        return WinRTProjectionMarshaler.hosted(
            host = createReferenceArrayMarshalerHost(interfaceId, value),
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
            createReferenceArrayHost(interfaceId, value).detachReference(interfaceId)
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


object WinRTPropertyValueProjection {
    fun createMarshaler(value: Any?): WinRTProjectionMarshaler? {
        if (value == null || !WinRTValueBoxing.isPropertyValueCompatible(value)) {
            return null
        }
        return WinRTProjectionMarshaler.owned(
            ValueBoxingInterop.createPropertyValueReference(value),
        )
    }

    fun fromManaged(value: Any?): RawAddress =
        if (value == null || !WinRTValueBoxing.isPropertyValueCompatible(value)) {
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
