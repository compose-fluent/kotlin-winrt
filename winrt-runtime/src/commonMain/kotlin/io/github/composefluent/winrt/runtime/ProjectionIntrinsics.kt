package io.github.composefluent.winrt.runtime

object WinRtProjectionIntrinsic {
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
        intrinsicNotLowered("getString", reference, slot)

    fun getBoolean(reference: ComObjectReference, slot: Int): Boolean =
        intrinsicNotLowered("getBoolean", reference, slot)

    fun getNoExceptionBoolean(reference: ComObjectReference, slot: Int): Boolean =
        intrinsicNotLowered("getNoExceptionBoolean", reference, slot)

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
        intrinsicNotLowered("setString", reference, slot, value)

    fun setBoolean(reference: ComObjectReference, slot: Int, value: Boolean): Unit =
        intrinsicNotLowered("setBoolean", reference, slot, value)

    fun setInt32(reference: ComObjectReference, slot: Int, value: Int): Unit =
        intrinsicNotLowered("setInt32", reference, slot, value)

    fun setUInt32(reference: ComObjectReference, slot: Int, value: UInt): Unit =
        intrinsicNotLowered("setUInt32", reference, slot, value)

    fun setInt64(reference: ComObjectReference, slot: Int, value: Long): Unit =
        intrinsicNotLowered("setInt64", reference, slot, value)

    fun setUInt64(reference: ComObjectReference, slot: Int, value: ULong): Unit =
        intrinsicNotLowered("setUInt64", reference, slot, value)

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
        error("WinRtProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot")

    private fun intrinsicNotLowered(name: String, reference: ComObjectReference, slot: Int, value: Any?): Nothing =
        error("WinRtProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot value $value")

    private fun intrinsicNotLowered(
        name: String,
        reference: ComObjectReference,
        slot: Int,
        vararg values: Any?,
    ): Nothing =
        error("WinRtProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot values ${values.toList()}")

    private fun staticIntrinsicNotLowered(name: String, reference: IUnknownReference, slot: Int): Nothing =
        error("WinRtProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot")

    private fun staticIntrinsicNotLowered(name: String, reference: IUnknownReference, slot: Int, value: Any?): Nothing =
        error("WinRtProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot value $value")
}
