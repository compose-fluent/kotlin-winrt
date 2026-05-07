package io.github.composefluent.winrt.runtime

object WinRtStaticProjectionInterop {
    fun <T> staticGetArray(
        reference: IUnknownReference,
        slot: Int,
        marshaler: Marshaler<T>,
    ): List<T?> =
        staticGetArrayCore(reference, slot, marshaler) {
            emptyArray<Any>()
        }

    fun <T> staticGetArrayWithProjectedObject(
        reference: IUnknownReference,
        slot: Int,
        value: IWinRTObject,
        marshaler: Marshaler<T>,
    ): List<T?> =
        staticGetArrayCore(reference, slot, marshaler) {
            arrayOf<Any>(PlatformAbi.fromRawComPtr(value.nativeObject.pointer))
        }

    fun <T> staticCallProjectedRuntimeClassWithString(
        reference: IUnknownReference,
        slot: Int,
        value: String,
        wrap: (IInspectableReference) -> T,
    ): T =
        staticCallProjectedObjectWithString(reference, slot, value) { result ->
            wrap(result.asInspectable())
        }

    fun <T> staticCallProjectedInterfaceWithString(
        reference: IUnknownReference,
        slot: Int,
        value: String,
        wrap: (IUnknownReference) -> T,
    ): T =
        staticCallProjectedObjectWithString(reference, slot, value, wrap)

    private fun <T> staticGetArrayCore(
        reference: IUnknownReference,
        slot: Int,
        marshaler: Marshaler<T>,
        arguments: (NativeScope) -> Array<Any>,
    ): List<T?> =
        PlatformAbi.confinedScope().use { scope ->
            val lengthOut = PlatformAbi.allocateInt32Slot(scope)
            val dataOut = PlatformAbi.allocatePointerSlot(scope)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf<Any>(*arguments(scope), lengthOut, dataOut),
            )
            HResult(hr).requireSuccess()
            val length = PlatformAbi.readInt32(lengthOut)
            val data = PlatformAbi.readPointer(dataOut)
            try {
                marshaler.fromAbiArray(length, data) ?: emptyList()
            } finally {
                marshaler.disposeAbiArray(length, data)
            }
        }

    fun callUnit(
        reference: IUnknownReference,
        slot: Int,
        vararg args: Any,
    ) {
        val hr = ComVtableInvoker.invokeGenericArgs(
            instance = reference.pointer,
            slot = slot,
            args = args,
        )
        HResult(hr).requireSuccess()
    }

    fun callBoolean(
        reference: IUnknownReference,
        slot: Int,
        vararg args: Any,
    ): Boolean =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt8Slot(scope)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf(*args, resultOut),
            )
            HResult(hr).requireSuccess()
            PlatformAbi.readInt8(resultOut).toInt() != 0
        }

    fun callInt32(
        reference: IUnknownReference,
        slot: Int,
        vararg args: Any,
    ): Int =
        callScalar(reference, slot, args, PlatformAbi::allocateInt32Slot, PlatformAbi::readInt32)

    fun callUInt32(
        reference: IUnknownReference,
        slot: Int,
        vararg args: Any,
    ): UInt =
        callInt32(reference, slot, *args).toUInt()

    fun callInt64(
        reference: IUnknownReference,
        slot: Int,
        vararg args: Any,
    ): Long =
        callScalar(reference, slot, args, PlatformAbi::allocateInt64Slot, PlatformAbi::readInt64)

    fun callUInt64(
        reference: IUnknownReference,
        slot: Int,
        vararg args: Any,
    ): ULong =
        callInt64(reference, slot, *args).toULong()

    fun callFloat(
        reference: IUnknownReference,
        slot: Int,
        vararg args: Any,
    ): Float =
        callScalar(reference, slot, args, { scope -> PlatformAbi.allocateBytes(scope, 4) }, PlatformAbi::readFloat)

    fun callDouble(
        reference: IUnknownReference,
        slot: Int,
        vararg args: Any,
    ): Double =
        callScalar(reference, slot, args, PlatformAbi::allocateDoubleSlot, PlatformAbi::readDouble)

    fun <T> callProjectedRuntimeClass(
        reference: IUnknownReference,
        slot: Int,
        wrap: (IInspectableReference) -> T,
        vararg args: Any,
    ): T =
        getInspectablePointer(reference, slot, args) { result ->
            wrap(result.asInspectable())
        }

    fun <T> callProjectedInterface(
        reference: IUnknownReference,
        slot: Int,
        wrap: (IUnknownReference) -> T,
        vararg args: Any,
    ): T =
        getInspectablePointer(reference, slot, args, wrap)

    fun <T> getProjectedRuntimeClass(
        reference: IUnknownReference,
        slot: Int,
        wrap: (IInspectableReference) -> T,
    ): T =
        getInspectablePointer(reference, slot) { result ->
            wrap(result.asInspectable())
        }

    fun <T> getProjectedInterface(
        reference: IUnknownReference,
        slot: Int,
        wrap: (IUnknownReference) -> T,
    ): T =
        getInspectablePointer(reference, slot, wrap = wrap)

    private fun <T> getInspectablePointer(
        reference: IUnknownReference,
        slot: Int,
        args: Array<out Any> = emptyArray(),
        wrap: (IUnknownReference) -> T,
    ): T =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf(*args, resultOut),
            )
            HResult(hr).requireSuccess()
            val resultPointer = PlatformAbi.readPointer(resultOut)
            if (PlatformAbi.isNull(resultPointer)) {
                error("WINRT_E_NULL_ABI_RETURN")
            }
            val resultRef = IUnknownReference(PlatformAbi.toRawComPtr(resultPointer))
            wrap(resultRef)
        }

    private fun <T> staticCallProjectedObjectWithString(
        reference: IUnknownReference,
        slot: Int,
        value: String,
        wrap: (IUnknownReference) -> T,
    ): T =
        HString.createReference(value).use { valueAbi ->
            PlatformAbi.confinedScope().use { scope ->
                val resultOut = PlatformAbi.allocatePointerSlot(scope)
                val hr = ComVtableInvoker.invokeArgs(
                    instance = reference.pointer,
                    slot = slot,
                    arg0 = valueAbi.handle,
                    arg1 = resultOut,
                )
                HResult(hr).requireSuccess()
                val resultPointer = PlatformAbi.readPointer(resultOut)
                if (PlatformAbi.isNull(resultPointer)) {
                    error("WINRT_E_NULL_ABI_RETURN")
                }
                val resultRef = IUnknownReference(PlatformAbi.toRawComPtr(resultPointer))
                wrap(resultRef)
            }
        }

    private fun <T> callScalar(
        reference: IUnknownReference,
        slot: Int,
        args: Array<out Any>,
        allocate: (NativeScope) -> RawAddress,
        read: (RawAddress) -> T,
    ): T =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = allocate(scope)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf(*args, resultOut),
            )
            HResult(hr).requireSuccess()
            read(resultOut)
        }
}
