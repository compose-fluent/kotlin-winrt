package io.github.kitectlab.winrt.runtime

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
    pointer: NativePointer,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : IUnknownReference(pointer, interfaceId, preventReleaseOnDispose = preventReleaseOnDispose) {
    fun <T> readValue(
        sizeBytes: Long,
        alignmentBytes: Long,
        readValue: (NativePointer) -> T,
        disposeValue: (NativePointer) -> Unit = {},
    ): T =
        NativeInterop.confinedScope().use { scope ->
            val resultOut = NativeInterop.allocateBytes(scope, sizeBytes, alignmentBytes)
            val hr = invokeAbi(
                slot = 6,
                descriptor = NativeFunctionDescriptor.of(
                    NativeValueLayout.JAVA_INT,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            try {
                readValue(resultOut)
            } finally {
                disposeValue(resultOut)
            }
        }
}

internal class WinRtReferenceArrayReference(
    pointer: NativePointer,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : IUnknownReference(pointer, interfaceId, preventReleaseOnDispose = preventReleaseOnDispose) {
    fun readValue(
        readArray: (Int, NativePointer) -> Array<Any?>?,
        disposeArray: (Int, NativePointer) -> Unit,
    ): Array<Any?>? =
        NativeInterop.confinedScope().use { scope ->
            val countOut = NativeInterop.allocateInt32Slot(scope)
            val dataOut = NativeInterop.allocatePointerSlot(scope)
            val hr = invokeAbi(
                slot = 6,
                descriptor = NativeFunctionDescriptor.of(
                    NativeValueLayout.JAVA_INT,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                countOut,
                dataOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            val length = NativeInterop.readInt32(countOut)
            val data = NativeInterop.readPointer(dataOut)
            try {
                readArray(length, data)
            } finally {
                disposeArray(length, data)
            }
        }
}

internal class WinRtPropertyValueReference(
    pointer: NativePointer,
    preventReleaseOnDispose: Boolean = false,
) : IUnknownReference(pointer, IID.IPropertyValue, preventReleaseOnDispose = preventReleaseOnDispose) {
    fun type(): PropertyType =
        NativeInterop.confinedScope().use { scope ->
            val resultOut = NativeInterop.allocateInt32Slot(scope)
            val hr = invokeAbi(
                slot = 6,
                descriptor = NativeFunctionDescriptor.of(
                    NativeValueLayout.JAVA_INT,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            PropertyType.fromCode(NativeInterop.readInt32(resultOut))
        }

    fun isNumericScalar(): Boolean =
        NativeInterop.confinedScope().use { scope ->
            val resultOut = NativeInterop.allocateInt8Slot(scope)
            val hr = invokeAbi(
                slot = 7,
                descriptor = NativeFunctionDescriptor.of(
                    NativeValueLayout.JAVA_INT,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            NativeInterop.readInt8(resultOut).toInt() != 0
        }

    fun getValue(): Any? = PlatformValueProjectionInterop.readPropertyValue(pointer, type())
}

