package io.github.composefluent.winrt.runtime

object WinRtInstanceProjectionInterop {
    fun getBoolean(reference: ComObjectReference, slot: Int): Boolean =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt8Slot(scope)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf(resultOut),
            )
            HResult(hr).requireSuccess()
            PlatformAbi.readInt8(resultOut).toInt() != 0
        }

    fun getInt32(reference: ComObjectReference, slot: Int): Int =
        getScalar(reference, slot, PlatformAbi::allocateInt32Slot, PlatformAbi::readInt32)

    fun getUInt32(reference: ComObjectReference, slot: Int): UInt =
        getInt32(reference, slot).toUInt()

    fun getInt64(reference: ComObjectReference, slot: Int): Long =
        getScalar(reference, slot, PlatformAbi::allocateInt64Slot, PlatformAbi::readInt64)

    fun getUInt64(reference: ComObjectReference, slot: Int): ULong =
        getInt64(reference, slot).toULong()

    fun getFloat(reference: ComObjectReference, slot: Int): Float =
        getScalar(reference, slot, { scope -> PlatformAbi.allocateBytes(scope, 4) }, PlatformAbi::readFloat)

    fun getDouble(reference: ComObjectReference, slot: Int): Double =
        getScalar(reference, slot, PlatformAbi::allocateDoubleSlot, PlatformAbi::readDouble)

    fun <T> getProjectedRuntimeClass(
        reference: ComObjectReference,
        slot: Int,
        wrap: (IInspectableReference) -> T,
    ): T =
        getProjectedObject(reference, slot) { result ->
            wrap(result.asInspectable())
        }

    fun <T> getNullableProjectedRuntimeClass(
        reference: ComObjectReference,
        slot: Int,
        wrap: (IInspectableReference) -> T,
    ): T? =
        getNullableProjectedObject(reference, slot) { result ->
            wrap(result.asInspectable())
        }

    fun <T> getProjectedInterface(
        reference: ComObjectReference,
        slot: Int,
        wrap: (IUnknownReference) -> T,
    ): T =
        getProjectedObject(reference, slot, wrap)

    fun <T> getNullableProjectedInterface(
        reference: ComObjectReference,
        slot: Int,
        wrap: (IUnknownReference) -> T,
    ): T? =
        getNullableProjectedObject(reference, slot, wrap)

    private fun <T> getScalar(
        reference: ComObjectReference,
        slot: Int,
        allocate: (NativeScope) -> RawAddress,
        read: (RawAddress) -> T,
    ): T =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = allocate(scope)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf(resultOut),
            )
            HResult(hr).requireSuccess()
            read(resultOut)
        }

    private fun <T> getProjectedObject(
        reference: ComObjectReference,
        slot: Int,
        wrap: (IUnknownReference) -> T,
    ): T =
        getNullableProjectedObject(reference, slot, wrap) ?: error("WINRT_E_NULL_ABI_RETURN")

    private fun <T> getNullableProjectedObject(
        reference: ComObjectReference,
        slot: Int,
        wrap: (IUnknownReference) -> T,
    ): T? =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf(resultOut),
            )
            HResult(hr).requireSuccess()
            val resultPointer = PlatformAbi.readPointer(resultOut)
            if (PlatformAbi.isNull(resultPointer)) {
                return null
            }
            val resultRef = IUnknownReference(PlatformAbi.toRawComPtr(resultPointer))
            wrap(resultRef)
        }
}

object WinRtProjectionIntrinsic {
    fun getBoolean(reference: ComObjectReference, slot: Int): Boolean =
        intrinsicNotLowered("getBoolean", reference, slot)

    fun getInt32(reference: ComObjectReference, slot: Int): Int =
        intrinsicNotLowered("getInt32", reference, slot)

    fun getUInt32(reference: ComObjectReference, slot: Int): UInt =
        intrinsicNotLowered("getUInt32", reference, slot)

    fun getInt64(reference: ComObjectReference, slot: Int): Long =
        intrinsicNotLowered("getInt64", reference, slot)

    fun getUInt64(reference: ComObjectReference, slot: Int): ULong =
        intrinsicNotLowered("getUInt64", reference, slot)

    fun getFloat(reference: ComObjectReference, slot: Int): Float =
        intrinsicNotLowered("getFloat", reference, slot)

    fun getDouble(reference: ComObjectReference, slot: Int): Double =
        intrinsicNotLowered("getDouble", reference, slot)

    private fun intrinsicNotLowered(name: String, reference: ComObjectReference, slot: Int): Nothing =
        error("WinRtProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot")
}
