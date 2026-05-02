package io.github.composefluent.winrt.runtime

internal const val TYPE_E_TYPEMISMATCH: Int = 0x80028CA0.toInt()

internal enum class PropertyType(val code: Int) {
    Empty(0x0),
    UInt8(0x1),
    Int16(0x2),
    UInt16(0x3),
    Int32(0x4),
    UInt32(0x5),
    Int64(0x6),
    UInt64(0x7),
    Single(0x8),
    Double(0x9),
    Char16(0xA),
    Boolean(0xB),
    String(0xC),
    Inspectable(0xD),
    DateTime(0xE),
    TimeSpan(0xF),
    Guid(0x10),
    Point(0x11),
    Size(0x12),
    Rect(0x13),
    OtherType(0x14),
    UInt8Array(0x401),
    Int16Array(0x402),
    UInt16Array(0x403),
    Int32Array(0x404),
    UInt32Array(0x405),
    Int64Array(0x406),
    UInt64Array(0x407),
    SingleArray(0x408),
    DoubleArray(0x409),
    Char16Array(0x40A),
    BooleanArray(0x40B),
    StringArray(0x40C),
    InspectableArray(0x40D),
    DateTimeArray(0x40E),
    TimeSpanArray(0x40F),
    GuidArray(0x410),
    PointArray(0x411),
    SizeArray(0x412),
    RectArray(0x413),
    OtherTypeArray(0x414),
    ;

    companion object {
        private val byCode = entries.associateBy(PropertyType::code)

        fun fromCode(code: Int): PropertyType =
            byCode[code] ?: throw WinRtInvalidCastException(
                "Unsupported Windows.Foundation.PropertyType value: $code",
                HResult(TYPE_E_TYPEMISMATCH),
            )
    }
}

internal class WinRtReferenceReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId, preventReleaseOnDispose = preventReleaseOnDispose) {
    fun <T> readValue(
        sizeBytes: Long,
        alignmentBytes: Long,
        readValue: (RawAddress) -> T,
        disposeValue: (RawAddress) -> Unit = {},
    ): T =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateBytes(scope, sizeBytes, alignmentBytes)
            comPtr.throwIfDisposed()
            val hr = ComVtableInvoker.invokeArgs(comPtr.raw, 6, resultOut)
            WinRtPlatformApi.checkSucceededRaw(hr)
            try {
                readValue(resultOut)
            } finally {
                disposeValue(resultOut)
            }
        }
}

internal class WinRtReferenceArrayReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId, preventReleaseOnDispose = preventReleaseOnDispose) {
    fun readValue(
        readArray: (Int, RawAddress) -> Array<Any?>?,
        disposeArray: (Int, RawAddress) -> Unit,
    ): Array<Any?>? =
        PlatformAbi.confinedScope().use { scope ->
            val countOut = PlatformAbi.allocateInt32Slot(scope)
            val dataOut = PlatformAbi.allocatePointerSlot(scope)
            comPtr.throwIfDisposed()
            val hr = ComVtableInvoker.invokeArgs(comPtr.raw, 6, countOut, dataOut)
            WinRtPlatformApi.checkSucceededRaw(hr)
            val length = PlatformAbi.readInt32(countOut)
            val data = PlatformAbi.readPointer(dataOut)
            try {
                readArray(length, data)
            } finally {
                disposeArray(length, data)
            }
        }
}

internal class WinRtPropertyValueReference(
    pointer: RawAddress,
    preventReleaseOnDispose: Boolean = false,
) : IUnknownReference(pointer.asRawComPtr(), IID.IPropertyValue, preventReleaseOnDispose = preventReleaseOnDispose) {
    fun type(): PropertyType =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt32Slot(scope)
            comPtr.throwIfDisposed()
            val hr = ComVtableInvoker.invokeArgs(comPtr.raw, 6, resultOut)
            WinRtPlatformApi.checkSucceededRaw(hr)
            PropertyType.fromCode(PlatformAbi.readInt32(resultOut))
        }

    fun isNumericScalar(): Boolean =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt8Slot(scope)
            comPtr.throwIfDisposed()
            val hr = ComVtableInvoker.invokeArgs(comPtr.raw, 7, resultOut)
            WinRtPlatformApi.checkSucceededRaw(hr)
            PlatformAbi.readInt8(resultOut).toInt() != 0
        }

    fun getValue(): Any? = ValueBoxingInterop.readPropertyValue(pointer.asRawAddress(), type())
}
