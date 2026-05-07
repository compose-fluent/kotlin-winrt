package io.github.composefluent.winrt.runtime

object WinRtInstanceProjectionInterop {
    fun invokeUnit(reference: ComObjectReference, slot: Int) {
        val hr = ComVtableInvoker.invoke(instance = reference.pointer, slot = slot)
        HResult(hr).requireSuccess()
    }

    fun getString(reference: ComObjectReference, slot: Int): String =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf(resultOut),
            )
            HResult(hr).requireSuccess()
            HString.fromHandle(PlatformAbi.readPointer(resultOut), owner = true).use(HString::toKString)
        }

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

    fun <T> getStruct(reference: ComObjectReference, slot: Int, adapter: NativeStructAdapter<T>): T =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateBytes(scope, adapter.layout.sizeBytes)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf(resultOut),
            )
            HResult(hr).requireSuccess()
            try {
                adapter.read(resultOut)
            } finally {
                adapter.disposeAbi(resultOut)
            }
        }

    fun <T> getArray(reference: ComObjectReference, slot: Int, marshaler: Marshaler<T>): List<T?> =
        PlatformAbi.confinedScope().use { scope ->
            val lengthOut = PlatformAbi.allocateInt32Slot(scope)
            val dataOut = PlatformAbi.allocatePointerSlot(scope)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf(lengthOut, dataOut),
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

    fun <T> setStruct(reference: ComObjectReference, slot: Int, value: T, adapter: NativeStructAdapter<T>) {
        PlatformAbi.confinedScope().use { scope ->
            val valueAbi = PlatformAbi.allocateBytes(scope, adapter.layout.sizeBytes)
            adapter.write(value, valueAbi)
            try {
                val hr = ComVtableInvoker.invokeGenericArgs(
                    instance = reference.pointer,
                    slot = slot,
                    args = arrayOf(valueAbi),
                )
                HResult(hr).requireSuccess()
            } finally {
                adapter.disposeAbi(valueAbi)
            }
        }
    }

    fun setString(reference: ComObjectReference, slot: Int, value: String) {
        HString.createReference(value).use { marshaler ->
            invokeUnit(reference, slot, marshaler.handle)
        }
    }

    fun setBoolean(reference: ComObjectReference, slot: Int, value: Boolean) {
        invokeUnit(reference, slot, if (value) 1.toByte() else 0.toByte())
    }

    fun setInt32(reference: ComObjectReference, slot: Int, value: Int) {
        invokeUnit(reference, slot, value)
    }

    fun setUInt32(reference: ComObjectReference, slot: Int, value: UInt) {
        invokeUnit(reference, slot, value.toInt())
    }

    fun setInt64(reference: ComObjectReference, slot: Int, value: Long) {
        invokeUnit(reference, slot, value)
    }

    fun setUInt64(reference: ComObjectReference, slot: Int, value: ULong) {
        invokeUnit(reference, slot, value.toLong())
    }

    fun setFloat(reference: ComObjectReference, slot: Int, value: Float) {
        invokeUnit(reference, slot, value)
    }

    fun setDouble(reference: ComObjectReference, slot: Int, value: Double) {
        invokeUnit(reference, slot, value)
    }

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

    private fun invokeUnit(reference: ComObjectReference, slot: Int, argument: Any) {
        val hr = ComVtableInvoker.invokeGenericArgs(
            instance = reference.pointer,
            slot = slot,
            args = arrayOf(argument),
        )
        HResult(hr).requireSuccess()
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
    fun invokeUnit(reference: ComObjectReference, slot: Int): Unit =
        intrinsicNotLowered("invokeUnit", reference, slot)

    fun getString(reference: ComObjectReference, slot: Int): String =
        intrinsicNotLowered("getString", reference, slot)

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

    fun <T> getStruct(reference: ComObjectReference, slot: Int, adapter: NativeStructAdapter<T>): T =
        intrinsicNotLowered("getStruct", reference, slot, adapter)

    fun <T> getArray(reference: ComObjectReference, slot: Int, marshaler: Marshaler<T>): List<T?> =
        intrinsicNotLowered("getArray", reference, slot, marshaler)

    fun <T> setStruct(reference: ComObjectReference, slot: Int, value: T, adapter: NativeStructAdapter<T>): Unit =
        intrinsicNotLowered("setStruct", reference, slot, value)

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

    private fun intrinsicNotLowered(name: String, reference: ComObjectReference, slot: Int): Nothing =
        error("WinRtProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot")

    private fun intrinsicNotLowered(name: String, reference: ComObjectReference, slot: Int, value: Any?): Nothing =
        error("WinRtProjectionIntrinsic.$name was not lowered for ${reference.pointer} slot $slot value $value")
}
