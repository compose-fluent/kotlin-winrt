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

    fun getString(reference: ComObjectReference, slot: Int): String {
        val frame = acquireNativeScalarScratchFrame(clear = false)
        try {
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, frame))
                .requireSuccess("WinRT getString")
            return frame.consumeOwnedHString()
        } finally {
            frame.close()
        }
    }

    fun getBoolean(reference: ComObjectReference, slot: Int): Boolean =
        acquireNativeScalarScratchFrame(clear = false).use { frame ->
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, frame))
                .requireSuccess("WinRT getBoolean")
            BooleanMarshaller.fromAbi(frame.readInt8())
        }

    fun getNoExceptionBoolean(reference: ComObjectReference, slot: Int): Boolean =
        acquireNativeScalarScratchFrame().use { frame ->
            ComVtableInvoker.invokeArgs(reference.pointer, slot, frame)
            BooleanMarshaller.fromAbi(frame.readInt8())
        }

    fun getInt32(reference: ComObjectReference, slot: Int): Int =
        acquireNativeScalarScratchFrame(clear = false).use { frame ->
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, frame))
                .requireSuccess("WinRT getInt32")
            frame.readInt32()
        }

    fun getUInt32(reference: ComObjectReference, slot: Int): UInt =
        acquireNativeScalarScratchFrame(clear = false).use { frame ->
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, frame))
                .requireSuccess("WinRT getUInt32")
            frame.readInt32().toUInt()
        }

    fun getInt64(reference: ComObjectReference, slot: Int): Long =
        acquireNativeScalarScratchFrame(clear = false).use { frame ->
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, frame))
                .requireSuccess("WinRT getInt64")
            frame.readInt64()
        }

    fun getUInt64(reference: ComObjectReference, slot: Int): ULong =
        acquireNativeScalarScratchFrame(clear = false).use { frame ->
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, frame))
                .requireSuccess("WinRT getUInt64")
            frame.readInt64().toULong()
        }

    fun getFloat(reference: ComObjectReference, slot: Int): Float =
        acquireNativeScalarScratchFrame(clear = false).use { frame ->
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, frame))
                .requireSuccess("WinRT getFloat")
            frame.readFloat()
        }

    fun getDouble(reference: ComObjectReference, slot: Int): Double =
        acquireNativeScalarScratchFrame(clear = false).use { frame ->
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, frame))
                .requireSuccess("WinRT getDouble")
            frame.readDouble()
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
        acquireInitializedNativeHStringReferenceFrame(value).use { frame ->
            HResult(ComVtableInvoker.invokeArgs(reference.pointer, slot, frame.handle))
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
