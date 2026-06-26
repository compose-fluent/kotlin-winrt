package io.github.composefluent.winrt.runtime

object WinRTProjectionIntrinsic {
    fun <T> staticGetArray(
        reference: IUnknownReference,
        slot: Int,
        marshaler: Marshaler<T>,
    ): List<T?> =
        staticIntrinsicNotLowered("staticGetArray", reference, slot)

    fun <T> staticGetArrayWithProjectedObject(
        reference: IUnknownReference,
        slot: Int,
        value: IWinRTObject,
        marshaler: Marshaler<T>,
    ): List<T?> =
        staticIntrinsicNotLowered("staticGetArrayWithProjectedObject", reference, slot, value)

    fun <T> staticCallProjectedRuntimeClassWithString(
        reference: IUnknownReference,
        slot: Int,
        value: String,
        wrap: (IInspectableReference) -> T,
    ): T =
        staticIntrinsicNotLowered("staticCallProjectedRuntimeClassWithString", reference, slot, value)

    fun <T> staticCallProjectedInterfaceWithString(
        reference: IUnknownReference,
        slot: Int,
        value: String,
        wrap: (IUnknownReference) -> T,
    ): T =
        staticIntrinsicNotLowered("staticCallProjectedInterfaceWithString", reference, slot, value)

    fun <T> callProjectedRuntimeClass(
        reference: ComObjectReference,
        slot: Int,
        abiShape: String,
        wrap: (IInspectableReference) -> T,
        vararg arguments: Any?,
    ): T =
        intrinsicNotLowered("callProjectedRuntimeClass", reference, slot, abiShape, wrap, *arguments)

    fun <T> callProjectedInterface(
        reference: ComObjectReference,
        slot: Int,
        abiShape: String,
        wrap: (IUnknownReference) -> T,
        vararg arguments: Any?,
    ): T =
        intrinsicNotLowered("callProjectedInterface", reference, slot, abiShape, wrap, *arguments)

    fun callObject(
        reference: ComObjectReference,
        slot: Int,
        abiShape: String,
        vararg arguments: Any?,
    ): Any? =
        intrinsicNotLowered("callObject", reference, slot, abiShape, *arguments)

    fun getString(reference: ComObjectReference, slot: Int): String =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, resultOut))
                .requireSuccess("WinRT getString")
            HString.fromHandle(PlatformAbi.readPointer(resultOut), owner = true).use(HString::toKString)
        }

    fun getBoolean(reference: ComObjectReference, slot: Int): Boolean =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt8Slot(scope)
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, resultOut))
                .requireSuccess("WinRT getBoolean")
            BooleanMarshaller.fromAbi(PlatformAbi.readInt8(resultOut))
        }

    fun getNoExceptionBoolean(reference: ComObjectReference, slot: Int): Boolean =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt8Slot(scope)
            ComVtableInvoker.invokeArgs(reference.pointer, slot, resultOut)
            BooleanMarshaller.fromAbi(PlatformAbi.readInt8(resultOut))
        }

    fun getInt32(reference: ComObjectReference, slot: Int): Int =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt32Slot(scope)
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, resultOut))
                .requireSuccess("WinRT getInt32")
            PlatformAbi.readInt32(resultOut)
        }

    fun getUInt32(reference: ComObjectReference, slot: Int): UInt =
        getInt32(reference, slot).toUInt()

    fun getInt64(reference: ComObjectReference, slot: Int): Long =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt64Slot(scope)
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, resultOut))
                .requireSuccess("WinRT getInt64")
            PlatformAbi.readInt64(resultOut)
        }

    fun getUInt64(reference: ComObjectReference, slot: Int): ULong =
        getInt64(reference, slot).toULong()

    fun getFloat(reference: ComObjectReference, slot: Int): Float =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateBytes(scope, sizeBytes = 4, alignmentBytes = 4)
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, resultOut))
                .requireSuccess("WinRT getFloat")
            PlatformAbi.readFloat(resultOut)
        }

    fun getDouble(reference: ComObjectReference, slot: Int): Double =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateDoubleSlot(scope)
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, resultOut))
                .requireSuccess("WinRT getDouble")
            PlatformAbi.readDouble(resultOut)
        }

    fun <T> getStruct(reference: ComObjectReference, slot: Int, adapter: NativeStructAdapter<T>): T =
        intrinsicNotLowered("getStruct", reference, slot, adapter)

    fun <T> callStruct(
        reference: ComObjectReference,
        slot: Int,
        abiShape: String,
        adapter: NativeStructAdapter<T>,
        vararg arguments: Any?,
    ): T =
        intrinsicNotLowered("callStruct", reference, slot, abiShape, adapter, *arguments)

    fun <T> getArray(reference: ComObjectReference, slot: Int, marshaler: Marshaler<T>): List<T?> =
        intrinsicNotLowered("getArray", reference, slot, marshaler)

    fun <T> setStruct(reference: ComObjectReference, slot: Int, value: T, adapter: NativeStructAdapter<T>): Unit =
        intrinsicNotLowered("setStruct", reference, slot, value)

    fun <T> getProjectedRuntimeClass(
        reference: ComObjectReference,
        slot: Int,
        wrap: (IInspectableReference) -> T,
    ): T =
        intrinsicNotLowered("getProjectedRuntimeClass", reference, slot, wrap)

    fun <T> getNullableProjectedRuntimeClass(
        reference: ComObjectReference,
        slot: Int,
        wrap: (IInspectableReference) -> T,
    ): T? =
        intrinsicNotLowered("getNullableProjectedRuntimeClass", reference, slot, wrap)

    fun <T> getProjectedInterface(
        reference: ComObjectReference,
        slot: Int,
        wrap: (IUnknownReference) -> T,
    ): T =
        intrinsicNotLowered("getProjectedInterface", reference, slot, wrap)

    fun <T> getNullableProjectedInterface(
        reference: ComObjectReference,
        slot: Int,
        wrap: (IUnknownReference) -> T,
    ): T? =
        intrinsicNotLowered("getNullableProjectedInterface", reference, slot, wrap)

    fun setString(reference: ComObjectReference, slot: Int, value: String): Unit =
        HString.create(value).use { hString ->
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, hString.handle))
                .requireSuccess("WinRT setString")
        }

    fun setBoolean(reference: ComObjectReference, slot: Int, value: Boolean): Unit =
        intrinsicNotLowered("setBoolean", reference, slot, value)

    fun setInt32(reference: ComObjectReference, slot: Int, value: Int) {
        HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, value))
            .requireSuccess("WinRT setInt32")
    }

    fun setUInt32(reference: ComObjectReference, slot: Int, value: UInt) {
        HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, value))
            .requireSuccess("WinRT setUInt32")
    }

    fun setInt64(reference: ComObjectReference, slot: Int, value: Long) {
        HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, value))
            .requireSuccess("WinRT setInt64")
    }

    fun setUInt64(reference: ComObjectReference, slot: Int, value: ULong) {
        HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, value.toLong()))
            .requireSuccess("WinRT setUInt64")
    }

    fun setFloat(reference: ComObjectReference, slot: Int, value: Float): Unit =
        intrinsicNotLowered("setFloat", reference, slot, value)

    fun setDouble(reference: ComObjectReference, slot: Int, value: Double): Unit =
        intrinsicNotLowered("setDouble", reference, slot, value)

    fun callUnit(
        reference: ComObjectReference,
        slot: Int,
        abiShape: String,
        vararg arguments: Any?,
    ): Unit =
        intrinsicNotLowered("callUnit", reference, slot, abiShape, *arguments)

    fun callBoolean(
        reference: ComObjectReference,
        slot: Int,
        abiShape: String,
        vararg arguments: Any?,
    ): Boolean =
        intrinsicNotLowered("callBoolean", reference, slot, abiShape, *arguments)

    fun <T> callScalar(
        reference: ComObjectReference,
        slot: Int,
        returnShape: String,
        abiShape: String,
        vararg arguments: Any?,
    ): T =
        intrinsicNotLowered("callScalar", reference, slot, returnShape, abiShape, *arguments)

    private fun intrinsicNotLowered(name: String, reference: ComObjectReference, slot: Int): Nothing =
        error("WinRTProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot")

    private fun intrinsicNotLowered(name: String, reference: ComObjectReference, slot: Int, value: Any?): Nothing =
        error("WinRTProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot value $value")

    private fun intrinsicNotLowered(
        name: String,
        reference: ComObjectReference,
        slot: Int,
        vararg values: Any?,
    ): Nothing =
        error("WinRTProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot values ${values.toList()}")

    private fun staticIntrinsicNotLowered(name: String, reference: IUnknownReference, slot: Int): Nothing =
        error("WinRTProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot")

    private fun staticIntrinsicNotLowered(name: String, reference: IUnknownReference, slot: Int, value: Any?): Nothing =
        error("WinRTProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot value $value")
}
