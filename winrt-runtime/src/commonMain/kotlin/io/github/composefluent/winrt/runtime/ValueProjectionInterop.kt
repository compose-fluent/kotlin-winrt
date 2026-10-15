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
                // CsWinRT exposes IPropertyValue alongside every IReference<T> CCW.  WinUI
                // dependency-property validation uses that sibling interface for nullable
                // scalar inputs such as Boolean; omitting it makes the native setter reject the
                // otherwise valid IReference pointer.
                host = createReferenceMarshalerHost(
                    interfaceId = interfaceId,
                    value = value,
                    includePropertyValueInterface = true,
                ),
                interfaceId = interfaceId,
            )
        }
        val typeHandle = ValueBoxingInterop.referenceTypeHandle(value, interfaceId)
        borrowedProjectionMarshaler(value, typeHandle)?.let { return it }
        return WinRTProjectionMarshaler.hosted(
            host = createReferenceMarshalerHost(
                interfaceId = interfaceId,
                value = value,
                includePropertyValueInterface = true,
            ),
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

    /**
     * Reads an IReference<T> using its statically known ABI layout and value codec.
     * The caller retains ownership of [pointer]; [disposeValue] releases only the
     * ABI value returned by get_Value, including when [readValue] throws.
     *
     * Mirrors CsWinRT 3 WindowsRuntimeValueTypeMarshaller.UnboxToManaged<T>:
     * generated calls do not need the dynamic IID-to-value-adapter lookup.
     */
    inline fun <T> fromAbi(
        pointer: RawAddress,
        sizeBytes: Long,
        alignmentBytes: Long,
        readValue: (RawAddress) -> T,
        disposeValue: (RawAddress) -> Unit = {},
    ): T? {
        if (PlatformAbi.isNull(pointer)) return null
        return WinRTReferenceProjection.readValue(pointer, sizeBytes, alignmentBytes, readValue, disposeValue)
    }

    @PublishedApi
    internal inline fun <T> readValue(
        pointer: RawAddress,
        sizeBytes: Long,
        alignmentBytes: Long,
        readValue: (RawAddress) -> T,
        disposeValue: (RawAddress) -> Unit,
    ): T =
        acquireNativeStructScratchFrame(
            sizeBytes = sizeBytes,
            alignmentBytes = alignmentBytes,
            // A successful get_Value initializes the entire value. Failed calls
            // do not transfer an output value to the caller.
            clear = false,
        ).use { frame ->
            val resultOut = frame.pointer
            val hr = ComVtableInvoker.invokeArgs(pointer.asRawComPtr(), 6, resultOut)
            WinRTPlatformApi.checkSucceededRaw(hr)
            try {
                readValue(resultOut)
            } finally {
                disposeValue(resultOut)
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

object WinRTReferenceProjectionInterop {
    @Suppress("UNCHECKED_CAST")
    fun <T> getReferenceValue(
        reference: ComObjectReference,
        slot: Int,
        interfaceId: Guid,
    ): T =
        acquireNativeScalarScratchFrame().use { frame ->
            val resultOut = frame.pointer
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
                    disposeComAbi(result)
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

        // WinUI dependency-property setters validate boxed values through
        // IPropertyValue, so use the full CsWinRT-compatible host shape here.
        val host = createReferenceMarshalerHost(
            interfaceId = interfaceId,
            value = value,
            includePropertyValueInterface = true,
        )
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
