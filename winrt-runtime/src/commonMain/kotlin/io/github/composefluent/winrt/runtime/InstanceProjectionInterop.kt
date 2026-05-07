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
}
