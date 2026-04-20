@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.time.Duration
import kotlin.time.Instant

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

private const val TYPE_E_TYPEMISMATCH: Int = 0x80028CA0.toInt()
private const val DISP_E_OVERFLOW: Int = 0x8002000A.toInt()
private const val IREFERENCE_GENERIC_INTERFACE = "61C17706-2D65-11E0-9AE8-D48564015472"
private val enumSignaturePattern = Regex("^enum\\((.+);(i4|u4)\\)$")

private data class ManagedArrayBox(
    val elements: Array<*>,
    val adapter: WinRtValueAdapter<*>,
)

private data class WinRtEnumBoxingDescriptor(
    val enumType: Class<*>,
    val projectedTypeName: String,
    val propertyType: PropertyType,
    val nullableInterfaceId: Guid,
    val toAbiBits: (Any) -> Int,
    val fromAbiBits: (Int) -> Any,
)

internal class WinRtValueAdapter<T : Any>(
    val projectedClass: Class<*>,
    val nullableInterfaceId: Guid?,
    val referenceArrayInterfaceId: Guid?,
    val propertyType: PropertyType?,
    val propertyTypeArray: PropertyType?,
    val abiLayout: MemoryLayout,
    val isNumericScalar: Boolean = false,
    private val exactUnbox: (Any) -> T,
    private val propertyValueCoerce: (Any) -> T = exactUnbox,
    private val writeTransferredValue: (T, MemorySegment) -> Unit,
    private val readOwnedValue: (MemorySegment) -> T,
    private val disposeTransferredValue: (MemorySegment) -> Unit = {},
) {
    fun unboxExact(value: Any): T = exactUnbox(value)

    fun coercePropertyValue(value: Any): T = propertyValueCoerce(value)

    fun writeValue(value: Any, destination: MemorySegment) {
        writeTransferredValue(unboxExact(value), destination.reinterpret(abiLayout.byteSize()))
    }

    fun writeCoercedPropertyValue(value: Any, destination: MemorySegment) {
        writeTransferredValue(coercePropertyValue(value), destination.reinterpret(abiLayout.byteSize()))
    }

    fun readValue(source: MemorySegment): T = readOwnedValue(source)

    fun disposeValue(source: MemorySegment) {
        disposeTransferredValue(source)
    }

    fun createTransferredArray(elements: Array<*>): Pair<Int, MemorySegment> {
        val arena = Arena.ofShared()
        val data = arena.allocate(abiLayout.byteSize() * elements.size.toLong(), abiLayout.byteAlignment())
        return try {
            elements.forEachIndexed { index, element ->
                val slice = data.asSlice(index.toLong() * abiLayout.byteSize(), abiLayout.byteSize())
                if (element != null) {
                    writeTransferredValue(unboxExact(element), slice)
                } else {
                    slice.fill(0)
                }
            }
            TransferredArrayOwnership.transfer(data) {
                elements.indices.forEach { index ->
                    val slice = data.asSlice(index.toLong() * abiLayout.byteSize(), abiLayout.byteSize())
                    disposeTransferredValue(slice)
                }
                arena.close()
            }
            elements.size to data
        } catch (error: Throwable) {
            elements.indices.forEach { index ->
                val slice = data.asSlice(index.toLong() * abiLayout.byteSize(), abiLayout.byteSize())
                disposeTransferredValue(slice)
            }
            arena.close()
            throw error
        }
    }

    fun readOwnedArray(length: Int, data: MemorySegment): Array<Any?>? {
        if (data == MemorySegment.NULL) {
            return null
        }
        val readable = data.reinterpret(abiLayout.byteSize() * length.toLong())
        return Array(length) { index ->
            val slice = readable.asSlice(index.toLong() * abiLayout.byteSize(), abiLayout.byteSize())
            readOwnedValue(slice)
        }
    }

    fun disposeOwnedArray(length: Int, data: MemorySegment) {
        if (data == MemorySegment.NULL) {
            return
        }
        if (TransferredArrayOwnership.release(data)) {
            return
        }
        val readable = data.reinterpret(abiLayout.byteSize() * length.toLong())
        repeat(length) { index ->
            val slice = readable.asSlice(index.toLong() * abiLayout.byteSize(), abiLayout.byteSize())
            disposeTransferredValue(slice)
        }
    }
}

private object TransferredArrayOwnership {
    private val cleanups = ConcurrentCacheMap<Long, () -> Unit>()

    fun transfer(
        pointer: MemorySegment,
        cleanup: () -> Unit,
    ) {
        cleanups[pointer.address()] = cleanup
    }

    fun release(pointer: MemorySegment): Boolean =
        cleanups.remove(pointer.address())?.let {
            it()
            true
        } ?: false
}

private fun <T : Any> directValueAdapter(
    projectedClass: Class<*>,
    nullableInterfaceId: Guid?,
    referenceArrayInterfaceId: Guid?,
    propertyType: PropertyType?,
    propertyTypeArray: PropertyType?,
    abiLayout: MemoryLayout,
    isNumericScalar: Boolean = false,
    exactUnbox: (Any) -> T,
    propertyValueCoerce: (Any) -> T = exactUnbox,
    readOwnedValue: (MemorySegment) -> T,
    writeTransferredValue: (T, MemorySegment) -> Unit,
    disposeTransferredValue: (MemorySegment) -> Unit = {},
): WinRtValueAdapter<T> =
    WinRtValueAdapter(
        projectedClass = projectedClass,
        nullableInterfaceId = nullableInterfaceId,
        referenceArrayInterfaceId = referenceArrayInterfaceId,
        propertyType = propertyType,
        propertyTypeArray = propertyTypeArray,
        abiLayout = abiLayout,
        isNumericScalar = isNumericScalar,
        exactUnbox = exactUnbox,
        propertyValueCoerce = propertyValueCoerce,
        writeTransferredValue = writeTransferredValue,
        readOwnedValue = readOwnedValue,
        disposeTransferredValue = disposeTransferredValue,
    )

private fun <T : Any> pointerValueAdapter(
    projectedClass: Class<*>,
    nullableInterfaceId: Guid?,
    referenceArrayInterfaceId: Guid?,
    propertyType: PropertyType?,
    propertyTypeArray: PropertyType?,
    exactUnbox: (Any) -> T,
    propertyValueCoerce: (Any) -> T = exactUnbox,
    createPointer: (T) -> MemorySegment,
    readOwnedPointer: (MemorySegment) -> T,
    disposeOwnedPointer: (MemorySegment) -> Unit,
): WinRtValueAdapter<T> =
    directValueAdapter(
        projectedClass = projectedClass,
        nullableInterfaceId = nullableInterfaceId,
        referenceArrayInterfaceId = referenceArrayInterfaceId,
        propertyType = propertyType,
        propertyTypeArray = propertyTypeArray,
        abiLayout = ValueLayout.ADDRESS,
        exactUnbox = exactUnbox,
        propertyValueCoerce = propertyValueCoerce,
        readOwnedValue = { source -> readOwnedPointer(source.get(ValueLayout.ADDRESS, 0)) },
        writeTransferredValue = { value, destination ->
            destination.set(ValueLayout.ADDRESS, 0, createPointer(value))
        },
        disposeTransferredValue = { source ->
            val pointer = source.get(ValueLayout.ADDRESS, 0)
            if (pointer != MemorySegment.NULL) {
                disposeOwnedPointer(pointer)
            }
        },
    )

internal object WinRtValueBoxing {
    private val stringAdapter =
        pointerValueAdapter(
            projectedClass = String::class.java,
            nullableInterfaceId = IID.NullableString,
            referenceArrayInterfaceId = IID.IReferenceArrayOfString,
            propertyType = PropertyType.String,
            propertyTypeArray = PropertyType.StringArray,
            exactUnbox = { it as String },
            propertyValueCoerce = ::coerceString,
            createPointer = { value -> StringMarshaller.fromManaged(value)?.handle ?: MemorySegment.NULL },
            readOwnedPointer = StringMarshaller::fromAbi,
            disposeOwnedPointer = StringMarshaller::disposeAbi,
        )

    private val objectAdapter =
        pointerValueAdapter(
            projectedClass = Any::class.java,
            nullableInterfaceId = IID.NullableObject,
            referenceArrayInterfaceId = IID.IReferenceArrayOfObject,
            propertyType = null,
            propertyTypeArray = PropertyType.InspectableArray,
            exactUnbox = { it },
            createPointer = { value -> ComWrappersSupport.createCCWForObject(value, IID.IInspectable).useAndGetRef() },
            readOwnedPointer = ::unboxInspectablePointer,
            disposeOwnedPointer = { pointer -> IUnknownReference(pointer, IID.IInspectable).close() },
        )

    private val classAdapter =
        directValueAdapter<Class<*>>(
            projectedClass = Class::class.java,
            nullableInterfaceId = IID.NullableType,
            referenceArrayInterfaceId = IID.IReferenceArrayOfType,
            propertyType = null,
            propertyTypeArray = null,
            abiLayout = TypeProjection.ABI_LAYOUT,
            exactUnbox = { it as Class<*> },
            readOwnedValue = { source ->
                TypeProjection.fromAbi(source)
                    ?: throw WinRtInvalidCastException("Expected non-null projected Class value.", HResult(TYPE_E_TYPEMISMATCH))
            },
            writeTransferredValue = TypeProjection::copyTo,
            disposeTransferredValue = TypeProjection::disposeAbi,
        )

    private val exceptionAdapter =
        directValueAdapter(
            projectedClass = Exception::class.java,
            nullableInterfaceId = IID.NullableException,
            referenceArrayInterfaceId = IID.IReferenceArrayOfException,
            propertyType = null,
            propertyTypeArray = null,
            abiLayout = ValueLayout.JAVA_INT,
            exactUnbox = { it as Exception },
            readOwnedValue = { source -> ExceptionProjection.fromAbi(source.get(ValueLayout.JAVA_INT, 0)) },
            writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_INT, 0, ExceptionProjection.toAbi(value)) },
        )

    private val adapters: List<WinRtValueAdapter<*>> =
        listOf<WinRtValueAdapter<*>>(
            directValueAdapter(
                projectedClass = Byte::class.javaObjectType,
                nullableInterfaceId = IID.NullableSByte,
                referenceArrayInterfaceId = IID.IReferenceArrayOfSByte,
                propertyType = null,
                propertyTypeArray = null,
                abiLayout = ValueLayout.JAVA_BYTE,
                exactUnbox = { it as Byte },
                readOwnedValue = { source -> source.get(ValueLayout.JAVA_BYTE, 0) },
                writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_BYTE, 0, value) },
            ),
            directValueAdapter(
                projectedClass = UByte::class.java,
                nullableInterfaceId = IID.NullableByte,
                referenceArrayInterfaceId = IID.IReferenceArrayOfByte,
                propertyType = PropertyType.UInt8,
                propertyTypeArray = PropertyType.UInt8Array,
                abiLayout = ValueLayout.JAVA_BYTE,
                isNumericScalar = true,
                exactUnbox = { it as UByte },
                propertyValueCoerce = ::coerceUByte,
                readOwnedValue = { source -> source.get(ValueLayout.JAVA_BYTE, 0).toUByte() },
                writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_BYTE, 0, value.toByte()) },
            ),
            directValueAdapter(
                projectedClass = Short::class.javaObjectType,
                nullableInterfaceId = IID.NullableShort,
                referenceArrayInterfaceId = IID.IReferenceArrayOfInt16,
                propertyType = PropertyType.Int16,
                propertyTypeArray = PropertyType.Int16Array,
                abiLayout = ValueLayout.JAVA_SHORT,
                isNumericScalar = true,
                exactUnbox = { it as Short },
                propertyValueCoerce = ::coerceShort,
                readOwnedValue = { source -> source.get(ValueLayout.JAVA_SHORT, 0) },
                writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_SHORT, 0, value) },
            ),
            directValueAdapter(
                projectedClass = UShort::class.java,
                nullableInterfaceId = IID.NullableUShort,
                referenceArrayInterfaceId = IID.IReferenceArrayOfUInt16,
                propertyType = PropertyType.UInt16,
                propertyTypeArray = PropertyType.UInt16Array,
                abiLayout = ValueLayout.JAVA_SHORT,
                isNumericScalar = true,
                exactUnbox = { it as UShort },
                propertyValueCoerce = ::coerceUShort,
                readOwnedValue = { source -> source.get(ValueLayout.JAVA_SHORT, 0).toUShort() },
                writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_SHORT, 0, value.toShort()) },
            ),
            directValueAdapter(
                projectedClass = Int::class.javaObjectType,
                nullableInterfaceId = IID.NullableInt,
                referenceArrayInterfaceId = IID.IReferenceArrayOfInt32,
                propertyType = PropertyType.Int32,
                propertyTypeArray = PropertyType.Int32Array,
                abiLayout = ValueLayout.JAVA_INT,
                isNumericScalar = true,
                exactUnbox = { it as Int },
                propertyValueCoerce = ::coerceInt,
                readOwnedValue = { source -> source.get(ValueLayout.JAVA_INT, 0) },
                writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_INT, 0, value) },
            ),
            directValueAdapter(
                projectedClass = UInt::class.java,
                nullableInterfaceId = IID.NullableUInt,
                referenceArrayInterfaceId = IID.IReferenceArrayOfUInt32,
                propertyType = PropertyType.UInt32,
                propertyTypeArray = PropertyType.UInt32Array,
                abiLayout = ValueLayout.JAVA_INT,
                isNumericScalar = true,
                exactUnbox = { it as UInt },
                propertyValueCoerce = ::coerceUInt,
                readOwnedValue = { source -> source.get(ValueLayout.JAVA_INT, 0).toUInt() },
                writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_INT, 0, value.toInt()) },
            ),
            directValueAdapter(
                projectedClass = Long::class.javaObjectType,
                nullableInterfaceId = IID.NullableLong,
                referenceArrayInterfaceId = IID.IReferenceArrayOfInt64,
                propertyType = PropertyType.Int64,
                propertyTypeArray = PropertyType.Int64Array,
                abiLayout = ValueLayout.JAVA_LONG,
                isNumericScalar = true,
                exactUnbox = { it as Long },
                propertyValueCoerce = ::coerceLong,
                readOwnedValue = { source -> source.get(ValueLayout.JAVA_LONG, 0) },
                writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_LONG, 0, value) },
            ),
            directValueAdapter(
                projectedClass = ULong::class.java,
                nullableInterfaceId = IID.NullableULong,
                referenceArrayInterfaceId = IID.IReferenceArrayOfUInt64,
                propertyType = PropertyType.UInt64,
                propertyTypeArray = PropertyType.UInt64Array,
                abiLayout = ValueLayout.JAVA_LONG,
                isNumericScalar = true,
                exactUnbox = { it as ULong },
                propertyValueCoerce = ::coerceULong,
                readOwnedValue = { source -> source.get(ValueLayout.JAVA_LONG, 0).toULong() },
                writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_LONG, 0, value.toLong()) },
            ),
            directValueAdapter(
                projectedClass = Float::class.javaObjectType,
                nullableInterfaceId = IID.NullableFloat,
                referenceArrayInterfaceId = IID.IReferenceArrayOfSingle,
                propertyType = PropertyType.Single,
                propertyTypeArray = PropertyType.SingleArray,
                abiLayout = ValueLayout.JAVA_FLOAT,
                isNumericScalar = true,
                exactUnbox = { it as Float },
                propertyValueCoerce = ::coerceFloat,
                readOwnedValue = { source -> source.get(ValueLayout.JAVA_FLOAT, 0) },
                writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_FLOAT, 0, value) },
            ),
            directValueAdapter(
                projectedClass = Double::class.javaObjectType,
                nullableInterfaceId = IID.NullableDouble,
                referenceArrayInterfaceId = IID.IReferenceArrayOfDouble,
                propertyType = PropertyType.Double,
                propertyTypeArray = PropertyType.DoubleArray,
                abiLayout = ValueLayout.JAVA_DOUBLE,
                isNumericScalar = true,
                exactUnbox = { it as Double },
                propertyValueCoerce = ::coerceDouble,
                readOwnedValue = { source -> source.get(ValueLayout.JAVA_DOUBLE, 0) },
                writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_DOUBLE, 0, value) },
            ),
            directValueAdapter(
                projectedClass = Char::class.javaObjectType,
                nullableInterfaceId = IID.NullableChar,
                referenceArrayInterfaceId = IID.IReferenceArrayOfChar,
                propertyType = PropertyType.Char16,
                propertyTypeArray = PropertyType.Char16Array,
                abiLayout = AbiLayouts.CHAR16,
                exactUnbox = { it as Char },
                propertyValueCoerce = ::coerceChar,
                readOwnedValue = { source -> source.get(AbiLayouts.CHAR16, 0) },
                writeTransferredValue = { value, destination -> destination.set(AbiLayouts.CHAR16, 0, value) },
            ),
            directValueAdapter(
                projectedClass = Boolean::class.javaObjectType,
                nullableInterfaceId = IID.NullableBool,
                referenceArrayInterfaceId = IID.IReferenceArrayOfBoolean,
                propertyType = PropertyType.Boolean,
                propertyTypeArray = PropertyType.BooleanArray,
                abiLayout = ValueLayout.JAVA_BYTE,
                exactUnbox = { it as Boolean },
                propertyValueCoerce = ::coerceBoolean,
                readOwnedValue = { source -> source.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0 },
                writeTransferredValue = { value, destination -> destination.set(ValueLayout.JAVA_BYTE, 0, if (value) 1 else 0) },
            ),
            stringAdapter,
            directValueAdapter(
                projectedClass = Guid::class.java,
                nullableInterfaceId = IID.NullableGuid,
                referenceArrayInterfaceId = IID.IReferenceArrayOfGuid,
                propertyType = PropertyType.Guid,
                propertyTypeArray = PropertyType.GuidArray,
                abiLayout = AbiLayouts.GUID,
                exactUnbox = { it as Guid },
                propertyValueCoerce = ::coerceGuid,
                readOwnedValue = GuidMarshaller::readFrom,
                writeTransferredValue = GuidMarshaller::copyTo,
            ),
            directValueAdapter(
                projectedClass = Instant::class.java,
                nullableInterfaceId = IID.NullableDateTimeOffset,
                referenceArrayInterfaceId = IID.IReferenceArrayOfDateTimeOffset,
                propertyType = PropertyType.DateTime,
                propertyTypeArray = PropertyType.DateTimeArray,
                abiLayout = ValueLayout.JAVA_LONG,
                exactUnbox = { it as Instant },
                readOwnedValue = { source -> DateTimeProjection.fromAbi(source.get(ValueLayout.JAVA_LONG, 0)) },
                writeTransferredValue = DateTimeProjection::copyTo,
            ),
            directValueAdapter(
                projectedClass = Duration::class.java,
                nullableInterfaceId = IID.NullableTimeSpan,
                referenceArrayInterfaceId = IID.IReferenceArrayOfTimeSpan,
                propertyType = PropertyType.TimeSpan,
                propertyTypeArray = PropertyType.TimeSpanArray,
                abiLayout = ValueLayout.JAVA_LONG,
                exactUnbox = { it as Duration },
                readOwnedValue = { source -> TimeSpanProjection.fromAbi(source.get(ValueLayout.JAVA_LONG, 0)) },
                writeTransferredValue = TimeSpanProjection::copyTo,
            ),
            directValueAdapter(
                projectedClass = Point::class.java,
                nullableInterfaceId = IID.IReferenceOfPoint,
                referenceArrayInterfaceId = IID.IReferenceArrayOfPoint,
                propertyType = PropertyType.Point,
                propertyTypeArray = PropertyType.PointArray,
                abiLayout = Point.Metadata.ABI_LAYOUT,
                exactUnbox = { it as Point },
                readOwnedValue = Point.Metadata::fromAbi,
                writeTransferredValue = Point.Metadata::copyTo,
            ),
            directValueAdapter(
                projectedClass = Size::class.java,
                nullableInterfaceId = IID.IReferenceOfSize,
                referenceArrayInterfaceId = IID.IReferenceArrayOfSize,
                propertyType = PropertyType.Size,
                propertyTypeArray = PropertyType.SizeArray,
                abiLayout = Size.Metadata.ABI_LAYOUT,
                exactUnbox = { it as Size },
                readOwnedValue = Size.Metadata::fromAbi,
                writeTransferredValue = Size.Metadata::copyTo,
            ),
            directValueAdapter(
                projectedClass = Rect::class.java,
                nullableInterfaceId = IID.IReferenceOfRect,
                referenceArrayInterfaceId = IID.IReferenceArrayOfRect,
                propertyType = PropertyType.Rect,
                propertyTypeArray = PropertyType.RectArray,
                abiLayout = Rect.Metadata.ABI_LAYOUT,
                exactUnbox = { it as Rect },
                readOwnedValue = Rect.Metadata::fromAbi,
                writeTransferredValue = Rect.Metadata::copyTo,
            ),
            directValueAdapter(
                projectedClass = Matrix3x2::class.java,
                nullableInterfaceId = IID.IReferenceMatrix3x2,
                referenceArrayInterfaceId = IID.IReferenceArrayOfMatrix3x2,
                propertyType = null,
                propertyTypeArray = null,
                abiLayout = Matrix3x2.Metadata.ABI_LAYOUT,
                exactUnbox = { it as Matrix3x2 },
                readOwnedValue = Matrix3x2.Metadata::fromAbi,
                writeTransferredValue = Matrix3x2.Metadata::copyTo,
            ),
            directValueAdapter(
                projectedClass = Matrix4x4::class.java,
                nullableInterfaceId = IID.IReferenceMatrix4x4,
                referenceArrayInterfaceId = IID.IReferenceArrayOfMatrix4x4,
                propertyType = null,
                propertyTypeArray = null,
                abiLayout = Matrix4x4.Metadata.ABI_LAYOUT,
                exactUnbox = { it as Matrix4x4 },
                readOwnedValue = Matrix4x4.Metadata::fromAbi,
                writeTransferredValue = Matrix4x4.Metadata::copyTo,
            ),
            directValueAdapter(
                projectedClass = Plane::class.java,
                nullableInterfaceId = IID.IReferencePlane,
                referenceArrayInterfaceId = IID.IReferenceArrayOfPlane,
                propertyType = null,
                propertyTypeArray = null,
                abiLayout = Plane.Metadata.ABI_LAYOUT,
                exactUnbox = { it as Plane },
                readOwnedValue = Plane.Metadata::fromAbi,
                writeTransferredValue = Plane.Metadata::copyTo,
            ),
            directValueAdapter(
                projectedClass = Quaternion::class.java,
                nullableInterfaceId = IID.IReferenceQuaternion,
                referenceArrayInterfaceId = IID.IReferenceArrayOfQuaternion,
                propertyType = null,
                propertyTypeArray = null,
                abiLayout = Quaternion.Metadata.ABI_LAYOUT,
                exactUnbox = { it as Quaternion },
                readOwnedValue = Quaternion.Metadata::fromAbi,
                writeTransferredValue = Quaternion.Metadata::copyTo,
            ),
            directValueAdapter(
                projectedClass = Vector2::class.java,
                nullableInterfaceId = IID.IReferenceVector2,
                referenceArrayInterfaceId = IID.IReferenceArrayOfVector2,
                propertyType = null,
                propertyTypeArray = null,
                abiLayout = Vector2.Metadata.ABI_LAYOUT,
                exactUnbox = { it as Vector2 },
                readOwnedValue = Vector2.Metadata::fromAbi,
                writeTransferredValue = Vector2.Metadata::copyTo,
            ),
            directValueAdapter(
                projectedClass = Vector3::class.java,
                nullableInterfaceId = IID.IReferenceVector3,
                referenceArrayInterfaceId = IID.IReferenceArrayOfVector3,
                propertyType = null,
                propertyTypeArray = null,
                abiLayout = Vector3.Metadata.ABI_LAYOUT,
                exactUnbox = { it as Vector3 },
                readOwnedValue = Vector3.Metadata::fromAbi,
                writeTransferredValue = Vector3.Metadata::copyTo,
            ),
            directValueAdapter(
                projectedClass = Vector4::class.java,
                nullableInterfaceId = IID.IReferenceVector4,
                referenceArrayInterfaceId = IID.IReferenceArrayOfVector4,
                propertyType = null,
                propertyTypeArray = null,
                abiLayout = Vector4.Metadata.ABI_LAYOUT,
                exactUnbox = { it as Vector4 },
                readOwnedValue = Vector4.Metadata::fromAbi,
                writeTransferredValue = Vector4.Metadata::copyTo,
            ),
            classAdapter,
            exceptionAdapter,
            objectAdapter,
        )

    private val adaptersByClass = adapters.associateBy(WinRtValueAdapter<*>::projectedClass)
    private val adaptersByNullableIid = adapters.mapNotNull { adapter -> adapter.nullableInterfaceId?.let { it to adapter } }.toMap()
    private val adaptersByReferenceArrayIid = adapters.mapNotNull { adapter -> adapter.referenceArrayInterfaceId?.let { it to adapter } }.toMap()
    private val adaptersByPropertyType = adapters.mapNotNull { adapter -> adapter.propertyType?.let { it to adapter } }.toMap()
    private val adaptersByPropertyTypeArray = adapters.mapNotNull { adapter -> adapter.propertyTypeArray?.let { it to adapter } }.toMap()

    fun isPropertyValueCompatible(value: Any): Boolean =
        propertyTypeOf(value).let { it != PropertyType.OtherType && it != PropertyType.OtherTypeArray }

    fun adapterForReferenceInterface(interfaceId: Guid): WinRtValueAdapter<*>? = adaptersByNullableIid[interfaceId]

    fun adapterForReferenceArrayInterface(interfaceId: Guid): WinRtValueAdapter<*>? = adaptersByReferenceArrayIid[interfaceId]

    internal fun adapterForPropertyType(propertyType: PropertyType): WinRtValueAdapter<*>? = adaptersByPropertyType[propertyType]

    internal fun adapterForPropertyTypeArray(propertyType: PropertyType): WinRtValueAdapter<*>? = adaptersByPropertyTypeArray[propertyType]

    internal fun inspectableArrayAdapter(): WinRtValueAdapter<Any> = objectAdapter

    internal fun boxedRuntimeClassNameForType(type: Class<*>): String? {
        enumDescriptorForClass(type)?.let { descriptor ->
            return "Windows.Foundation.IReference`1<${descriptor.projectedTypeName}>"
        }
        primitiveArrayElementType(type)?.let { elementType ->
            val adapter = adapterForClass(elementType) ?: return null
            val interfaceId = adapter.referenceArrayInterfaceId ?: return null
            return boxedReferenceArrayRuntimeClassName(interfaceId, adapter)
        }
        if (type.isArray) {
            val adapter = adapterForClass(type.componentType) ?: return null
            val interfaceId = adapter.referenceArrayInterfaceId ?: return null
            return boxedReferenceArrayRuntimeClassName(interfaceId, adapter)
        }

        val adapter = adapterForClass(type) ?: return null
        val interfaceId = adapter.nullableInterfaceId ?: return null
        return boxedReferenceRuntimeClassName(interfaceId, adapter)
    }

    internal fun createInspectableBoxDefinition(value: Any): WinRtCcwDefinition? {
        val interfaceDefinitions =
            buildList {
                if (isPropertyValueCompatible(value)) {
                    add(
                        WinRtInspectableInterfaceDefinition(
                            interfaceId = IID.IPropertyValue,
                            methods = buildPropertyValueMethods(value),
                        ),
                    )
                }

                referenceArrayInterfaceIdForValue(value)?.let { interfaceId ->
                    add(buildReferenceArrayInterfaceDefinition(interfaceId, value))
                } ?: referenceInterfaceIdForValue(value)?.let { interfaceId ->
                    add(buildReferenceInterfaceDefinition(interfaceId, value))
                }
            }
        if (interfaceDefinitions.isEmpty()) {
            return null
        }

        val defaultInterfaceId =
            if (interfaceDefinitions.any { it.interfaceId == IID.IPropertyValue }) {
                IID.IPropertyValue
            } else {
                interfaceDefinitions.first().interfaceId
            }

        return WinRtCcwDefinition(
            interfaceDefinitions = interfaceDefinitions,
            defaultInterfaceId = defaultInterfaceId,
            runtimeClassName = boxedRuntimeClassNameForType(value.javaClass),
        )
    }

    fun propertyTypeOf(value: Any): PropertyType {
        val managedArray = normalizeManagedArray(value)
        if (managedArray != null) {
            val adapter = classifyPropertyValueAdapter(managedArray.elements.firstOrNull(), managedArray.adapter)
            return adapter.propertyTypeArray
                ?: if (managedArray.adapter == objectAdapter) {
                    PropertyType.InspectableArray
                } else {
                    PropertyType.OtherTypeArray
                }
        }

        if (isSupportedArrayValue(value)) {
            return PropertyType.OtherTypeArray
        }

        enumDescriptorForClass(value.javaClass)?.let { return it.propertyType }

        val adapter = classifyPropertyValue(value)
        if (adapter != null) {
            return adapter.propertyType ?: PropertyType.OtherType
        }
        return PropertyType.OtherType
    }

    fun isNumericScalar(value: Any): Boolean =
        normalizeManagedArray(value) == null &&
            (classifyPropertyValue(value)?.isNumericScalar == true || enumDescriptorForClass(value.javaClass) != null)

    fun writePropertyValue(
        expectedType: PropertyType,
        value: Any,
        destination: MemorySegment,
    ) {
        val enumDescriptor = enumDescriptorForClass(value.javaClass)
        if (enumDescriptor != null && enumDescriptor.propertyType == expectedType) {
            destination.reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, enumDescriptor.toAbiBits(value))
            return
        }

        val adapter = adaptersByPropertyType[expectedType]
        if (adapter != null) {
            adapter.writeCoercedPropertyValue(value, destination)
            return
        }

        throw WinRtInvalidCastException("Unsupported property value getter: $expectedType", HResult(TYPE_E_TYPEMISMATCH))
    }

    fun writePropertyValueArray(
        expectedType: PropertyType,
        value: Any,
        countOut: MemorySegment,
        dataOut: MemorySegment,
    ) {
        val box = normalizeManagedArray(value)
            ?: throw WinRtInvalidCastException("Value is not an array for $expectedType", HResult(TYPE_E_TYPEMISMATCH))
        val adapter =
            when (expectedType) {
                PropertyType.InspectableArray -> objectAdapter
                else -> adaptersByPropertyTypeArray[expectedType]
            } ?: throw WinRtInvalidCastException("Unsupported property value array getter: $expectedType", HResult(TYPE_E_TYPEMISMATCH))
        val coerced =
            box.elements.map { element ->
                if (element == null) {
                    null
                } else {
                    @Suppress("UNCHECKED_CAST")
                    (adapter as WinRtValueAdapter<Any>).coercePropertyValue(element)
                }
            }.toTypedArray()
        val (length, data) = adapter.createTransferredArray(coerced)
        countOut.set(ValueLayout.JAVA_INT, 0, length)
        dataOut.set(ValueLayout.ADDRESS, 0, data)
    }

    fun readReferenceValue(
        interfaceId: Guid,
        reference: WinRtReferenceReference,
    ): Any? {
        val adapter = adapterForReferenceInterface(interfaceId)
            ?: throw WinRtInvalidCastException("Unsupported IReference interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH))
        return reference.getValue(adapter)
    }

    fun readReferenceArrayValue(
        interfaceId: Guid,
        reference: WinRtReferenceArrayReference,
    ): Array<Any?>? {
        val adapter = adapterForReferenceArrayInterface(interfaceId)
            ?: throw WinRtInvalidCastException("Unsupported IReferenceArray interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH))
        return reference.getValue(adapter)
    }

    fun createReferenceHost(
        interfaceId: Guid,
        value: Any,
    ): WinRtInspectableComObject {
        return WinRtInspectableComObject(
            interfaceDefinitions = listOf(buildReferenceInterfaceDefinition(interfaceId, value)),
            runtimeClassName = boxedRuntimeClassNameForType(value.javaClass),
            managedValue = value,
        )
    }

    fun createReferenceArrayHost(
        interfaceId: Guid,
        value: Any,
    ): WinRtInspectableComObject {
        return WinRtInspectableComObject(
            interfaceDefinitions = listOf(buildReferenceArrayInterfaceDefinition(interfaceId, value)),
            runtimeClassName = boxedRuntimeClassNameForType(value.javaClass),
            managedValue = normalizeManagedArray(value),
        )
    }

    fun createPropertyValueHost(value: Any): WinRtInspectableComObject {
        return WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = IID.IPropertyValue,
                    methods = buildPropertyValueMethods(value),
                ),
            ),
            runtimeClassName = boxedRuntimeClassNameForType(value.javaClass),
            managedValue = value,
        )
    }

    internal fun tryProjectInspectable(
        inspectable: IInspectableReference,
        runtimeClassName: String? = inspectable.tryGetRuntimeClassName(),
    ): Any? {
        if (!runtimeClassName.isNullOrBlank()) {
            TypeNameSupport.findRcwTypeByNameCached(runtimeClassName)?.let { projectedType ->
                tryProjectInspectableAsType(inspectable, projectedType)?.let { return it }
            }
        }

        WinRtPropertyValueProjection.tryFromBorrowedAbi(inspectable.pointer)?.let { return it }

        adapters.firstNotNullOfOrNull { adapter ->
            val interfaceId = adapter.nullableInterfaceId ?: return@firstNotNullOfOrNull null
            queryReferencePointer(inspectable, interfaceId)?.use { reference ->
                WinRtReferenceReference(reference.pointer, interfaceId, preventReleaseOnDispose = true).getValue(adapter)
            }
        }?.let { return it }

        adapters.firstNotNullOfOrNull { adapter ->
            val interfaceId = adapter.referenceArrayInterfaceId ?: return@firstNotNullOfOrNull null
            queryReferencePointer(inspectable, interfaceId)?.use { reference ->
                WinRtReferenceArrayReference(reference.pointer, interfaceId, preventReleaseOnDispose = true).getValue(adapter)
            }
        }?.let { return it }

        return null
    }

    internal fun tryProjectBorrowedInspectable(pointer: MemorySegment): Any? {
        if (pointer == MemorySegment.NULL) {
            return null
        }
        val borrowed = IUnknownReference(pointer, IID.IInspectable, preventReleaseOnDispose = true)
        val inspectable =
            try {
                borrowed.asInspectable()
            } catch (_: Throwable) {
                borrowed.close()
                return null
            }
        return try {
            tryProjectInspectable(inspectable)
        } finally {
            inspectable.close()
        }
    }

    private fun buildReferenceInterfaceDefinition(
        interfaceId: Guid,
        value: Any,
    ): WinRtInspectableInterfaceDefinition {
        val adapter = adapterForReferenceInterface(interfaceId)
        val enumDescriptor = enumDescriptorForClass(value.javaClass)
        if (adapter == null && (enumDescriptor == null || enumDescriptor.nullableInterfaceId != interfaceId)) {
            throw WinRtInvalidCastException("Unsupported IReference interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH))
        }
        return WinRtInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = listOf(
                WinRtInspectableMethodDefinition(
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                ) { rawArgs ->
                    val destination =
                        rawArgs.singleOrNull() as? MemorySegment
                            ?: throw IllegalStateException("IReference host requires one out-argument.")
                    if (adapter != null) {
                        adapter.writeValue(value, destination)
                    } else {
                        destination.reinterpret(ValueLayout.JAVA_INT.byteSize()).set(
                            ValueLayout.JAVA_INT,
                            0,
                            requireNotNull(enumDescriptor).toAbiBits(value),
                        )
                    }
                    KnownHResults.S_OK.value
                },
            ),
        )
    }

    private fun buildReferenceArrayInterfaceDefinition(
        interfaceId: Guid,
        value: Any,
    ): WinRtInspectableInterfaceDefinition {
        val adapter = adapterForReferenceArrayInterface(interfaceId)
            ?: throw WinRtInvalidCastException("Unsupported IReferenceArray interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH))
        val box = normalizeManagedArray(value)
            ?: throw WinRtInvalidCastException("IReferenceArray host requires an array value.", HResult(TYPE_E_TYPEMISMATCH))
        return WinRtInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = listOf(
                WinRtInspectableMethodDefinition(
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                ) { rawArgs ->
                    if (rawArgs.size != 2) {
                        throw IllegalStateException("IReferenceArray host requires count and data out-arguments.")
                    }
                    val (length, data) = adapter.createTransferredArray(box.elements)
                    (rawArgs[0] as MemorySegment).reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, length)
                    (rawArgs[1] as MemorySegment).reinterpret(ValueLayout.ADDRESS.byteSize()).set(ValueLayout.ADDRESS, 0, data)
                    KnownHResults.S_OK.value
                },
            ),
        )
    }

    private fun buildPropertyValueMethods(value: Any): List<WinRtInspectableMethodDefinition> {
        val scalarGetters =
            listOf(
                PropertyType.UInt8,
                PropertyType.Int16,
                PropertyType.UInt16,
                PropertyType.Int32,
                PropertyType.UInt32,
                PropertyType.Int64,
                PropertyType.UInt64,
                PropertyType.Single,
                PropertyType.Double,
                PropertyType.Char16,
                PropertyType.Boolean,
                PropertyType.String,
                PropertyType.Guid,
                PropertyType.DateTime,
                PropertyType.TimeSpan,
                PropertyType.Point,
                PropertyType.Size,
                PropertyType.Rect,
            )
        val arrayGetters =
            listOf(
                PropertyType.UInt8Array,
                PropertyType.Int16Array,
                PropertyType.UInt16Array,
                PropertyType.Int32Array,
                PropertyType.UInt32Array,
                PropertyType.Int64Array,
                PropertyType.UInt64Array,
                PropertyType.SingleArray,
                PropertyType.DoubleArray,
                PropertyType.Char16Array,
                PropertyType.BooleanArray,
                PropertyType.StringArray,
                PropertyType.InspectableArray,
                PropertyType.GuidArray,
                PropertyType.DateTimeArray,
                PropertyType.TimeSpanArray,
                PropertyType.PointArray,
                PropertyType.SizeArray,
                PropertyType.RectArray,
            )
        return buildList {
            add(
                WinRtInspectableMethodDefinition(
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                ) { rawArgs ->
                    (rawArgs[0] as MemorySegment).reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, propertyTypeOf(value).code)
                    KnownHResults.S_OK.value
                },
            )
            add(
                WinRtInspectableMethodDefinition(
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                ) { rawArgs ->
                    (rawArgs[0] as MemorySegment).reinterpret(ValueLayout.JAVA_BYTE.byteSize()).set(
                        ValueLayout.JAVA_BYTE,
                        0,
                        if (isNumericScalar(value)) 1 else 0,
                    )
                    KnownHResults.S_OK.value
                },
            )
            scalarGetters.forEach { propertyType ->
                add(
                    WinRtInspectableMethodDefinition(
                        descriptor = FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                        ),
                    ) { rawArgs ->
                        writePropertyValue(propertyType, value, rawArgs[0] as MemorySegment)
                        KnownHResults.S_OK.value
                    },
                )
            }
            arrayGetters.forEach { propertyType ->
                add(
                    WinRtInspectableMethodDefinition(
                        descriptor = FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                        ),
                    ) { rawArgs ->
                        writePropertyValueArray(
                            propertyType,
                            value,
                            rawArgs[0] as MemorySegment,
                            rawArgs[1] as MemorySegment,
                        )
                        KnownHResults.S_OK.value
                    },
                )
            }
        }
    }

    private fun classifyPropertyValue(value: Any): WinRtValueAdapter<*>? {
        val adapter = adapterForValue(value)
        if (adapter?.propertyType != null) {
            return adapter
        }
        return null
    }

    private fun classifyPropertyValueAdapter(
        sampleElement: Any?,
        defaultAdapter: WinRtValueAdapter<*>,
    ): WinRtValueAdapter<*> =
        when {
            sampleElement == null -> defaultAdapter
            defaultAdapter == objectAdapter -> objectAdapter
            else -> adapterForValue(sampleElement) ?: defaultAdapter
        }

    private fun adapterForValue(value: Any): WinRtValueAdapter<*>? = adapterForClass(value.javaClass)

    private fun adapterForClass(type: Class<*>): WinRtValueAdapter<*>? =
        adaptersByClass[type]
            ?: if (Exception::class.java.isAssignableFrom(type)) {
                exceptionAdapter
            } else {
                null
            }

    private fun normalizeManagedArray(value: Any): ManagedArrayBox? =
        when (value) {
            is Array<*> -> {
                val componentType = value.javaClass.componentType ?: Any::class.java
                val adapter =
                    adapterForClass(componentType)
                        ?: if (componentType == Any::class.java) {
                            objectAdapter
                        } else {
                            null
                        }
                adapter?.let { ManagedArrayBox(value, it) }
            }
            is ByteArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[Byte::class.javaObjectType]!!)
            is UByteArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[UByte::class.java]!!)
            is ShortArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[Short::class.javaObjectType]!!)
            is UShortArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[UShort::class.java]!!)
            is IntArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[Int::class.javaObjectType]!!)
            is UIntArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[UInt::class.java]!!)
            is LongArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[Long::class.javaObjectType]!!)
            is ULongArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[ULong::class.java]!!)
            is FloatArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[Float::class.javaObjectType]!!)
            is DoubleArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[Double::class.javaObjectType]!!)
            is BooleanArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[Boolean::class.javaObjectType]!!)
            is CharArray -> ManagedArrayBox(value.toTypedArray(), adaptersByClass[Char::class.javaObjectType]!!)
            else -> null
        }

    private fun isSupportedArrayValue(value: Any): Boolean =
        when (value) {
            is Array<*>,
            is ByteArray,
            is UByteArray,
            is ShortArray,
            is UShortArray,
            is IntArray,
            is UIntArray,
            is LongArray,
            is ULongArray,
            is FloatArray,
            is DoubleArray,
            is BooleanArray,
            is CharArray,
            -> true

            else -> false
        }

    private fun primitiveArrayElementType(type: Class<*>): Class<*>? =
        when (type) {
            ByteArray::class.java -> Byte::class.javaObjectType
            UByteArray::class.java -> UByte::class.java
            ShortArray::class.java -> Short::class.javaObjectType
            UShortArray::class.java -> UShort::class.java
            IntArray::class.java -> Int::class.javaObjectType
            UIntArray::class.java -> UInt::class.java
            LongArray::class.java -> Long::class.javaObjectType
            ULongArray::class.java -> ULong::class.java
            FloatArray::class.java -> Float::class.javaObjectType
            DoubleArray::class.java -> Double::class.javaObjectType
            BooleanArray::class.java -> Boolean::class.javaObjectType
            CharArray::class.java -> Char::class.javaObjectType
            else -> null
        }

    private fun referenceInterfaceIdForValue(value: Any): Guid? =
        adapterForValue(value)?.nullableInterfaceId ?: enumDescriptorForClass(value.javaClass)?.nullableInterfaceId

    private fun referenceArrayInterfaceIdForValue(value: Any): Guid? =
        normalizeManagedArray(value)?.adapter?.referenceArrayInterfaceId

    private fun boxedReferenceRuntimeClassName(
        interfaceId: Guid,
        adapter: WinRtValueAdapter<*>,
    ): String {
        check(adapter.nullableInterfaceId == interfaceId)
        return "Windows.Foundation.IReference`1<${TypeNameSupport.getNameForType(adapter.projectedClass)}>"
    }

    private fun boxedReferenceArrayRuntimeClassName(
        interfaceId: Guid,
        adapter: WinRtValueAdapter<*>,
    ): String {
        check(adapter.referenceArrayInterfaceId == interfaceId)
        return "Windows.Foundation.IReferenceArray`1<${TypeNameSupport.getNameForType(adapter.projectedClass)}>"
    }

    private fun tryProjectInspectableAsType(
        inspectable: IInspectableReference,
        projectedType: Class<*>,
    ): Any? {
        enumDescriptorForClass(projectedType)?.let { descriptor ->
            return queryReferencePointer(inspectable, descriptor.nullableInterfaceId)?.use { reference ->
                readEnumReferenceValue(
                    WinRtReferenceReference(
                        reference.pointer,
                        descriptor.nullableInterfaceId,
                        preventReleaseOnDispose = true,
                    ),
                    descriptor,
                )
            }
        }

        if (projectedType.isArray || primitiveArrayElementType(projectedType) != null) {
            val elementType = primitiveArrayElementType(projectedType) ?: projectedType.componentType ?: return null
            val adapter = adapterForClass(elementType) ?: return null
            val interfaceId = adapter.referenceArrayInterfaceId ?: return null
            return queryReferencePointer(inspectable, interfaceId)?.use { reference ->
                WinRtReferenceArrayReference(reference.pointer, interfaceId, preventReleaseOnDispose = true).getValue(adapter)
            }
        }

        val adapter = adapterForClass(projectedType) ?: return null
        val interfaceId = adapter.nullableInterfaceId ?: return null
        return queryReferencePointer(inspectable, interfaceId)?.use { reference ->
            WinRtReferenceReference(reference.pointer, interfaceId, preventReleaseOnDispose = true).getValue(adapter)
        }
    }

    private fun queryReferencePointer(
        inspectable: IInspectableReference,
        interfaceId: Guid,
    ): ComObjectReference? = runCatching { inspectable.queryInterface(interfaceId).getOrThrow() }.getOrNull()

    private fun readEnumReferenceValue(
        reference: WinRtReferenceReference,
        descriptor: WinRtEnumBoxingDescriptor,
    ): Any =
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_INT)
            val hr = reference.invokeAbi(
                slot = 6,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            descriptor.fromAbiBits(resultOut.get(ValueLayout.JAVA_INT, 0))
        }

    private fun enumDescriptorForClass(
        type: Class<*>,
    ): WinRtEnumBoxingDescriptor? {
        if (!type.isEnum) {
            return null
        }

        val signature = runCatching { GuidGenerator.getSignature(type) }.getOrNull() ?: return null
        val match = enumSignaturePattern.matchEntire(signature) ?: return null
        val projectedTypeName = match.groupValues[1]
        val underlyingSignature = match.groupValues[2]
        val abiField = runCatching { type.getDeclaredField("abiValue") }.getOrNull() ?: return null
        abiField.isAccessible = true

        fun readBits(enumValue: Any): Int =
            when (val raw = abiField.get(enumValue)) {
                is Int -> raw
                is UInt -> raw.toInt()
                is Number -> raw.toInt()
                else -> throw WinRtInvalidCastException(
                    "Unsupported enum ABI backing value: ${raw?.javaClass?.name}",
                    HResult(TYPE_E_TYPEMISMATCH),
                )
            }

        val constants = type.enumConstants ?: return null

        return when (underlyingSignature) {
            "i4" ->
                WinRtEnumBoxingDescriptor(
                    enumType = type,
                    projectedTypeName = projectedTypeName,
                    propertyType = PropertyType.Int32,
                    nullableInterfaceId = ParameterizedInterfaceId.createFromSignature("pinterface({${IREFERENCE_GENERIC_INTERFACE.lowercase()}};$signature)"),
                    toAbiBits = ::readBits,
                    fromAbiBits = { abiValue ->
                        constants.firstOrNull { readBits(it) == abiValue }
                            ?: throw WinRtInvalidCastException(
                                "Unknown enum value $abiValue for ${type.name}.",
                                HResult(TYPE_E_TYPEMISMATCH),
                            )
                    },
                )

            "u4" ->
                WinRtEnumBoxingDescriptor(
                    enumType = type,
                    projectedTypeName = projectedTypeName,
                    propertyType = PropertyType.UInt32,
                    nullableInterfaceId = ParameterizedInterfaceId.createFromSignature("pinterface({${IREFERENCE_GENERIC_INTERFACE.lowercase()}};$signature)"),
                    toAbiBits = ::readBits,
                    fromAbiBits = { abiValue ->
                        constants.firstOrNull { readBits(it) == abiValue }
                            ?: throw WinRtInvalidCastException(
                                "Unknown enum value ${abiValue.toUInt()} for ${type.name}.",
                                HResult(TYPE_E_TYPEMISMATCH),
                            )
                    },
                )

            else -> null
        }
    }
}

internal class WinRtReferenceReference(
    pointer: MemorySegment,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : IUnknownReference(pointer, interfaceId, preventReleaseOnDispose = preventReleaseOnDispose) {
    fun getValue(adapter: WinRtValueAdapter<*>): Any? =
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(adapter.abiLayout)
            val hr = invokeAbi(
                slot = 6,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            try {
                adapter.readValue(resultOut)
            } finally {
                adapter.disposeValue(resultOut)
            }
        }
}

internal class WinRtReferenceArrayReference(
    pointer: MemorySegment,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : IUnknownReference(pointer, interfaceId, preventReleaseOnDispose = preventReleaseOnDispose) {
    fun getValue(adapter: WinRtValueAdapter<*>): Array<Any?>? =
        Arena.ofConfined().use { arena ->
            val countOut = arena.allocate(ValueLayout.JAVA_INT)
            val dataOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeAbi(
                slot = 6,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                countOut,
                dataOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            val length = countOut.get(ValueLayout.JAVA_INT, 0)
            val data = dataOut.get(ValueLayout.ADDRESS, 0)
            try {
                return adapter.readOwnedArray(length, data)
            } finally {
                adapter.disposeOwnedArray(length, data)
            }
        }
}

internal class WinRtPropertyValueReference(
    pointer: MemorySegment,
    preventReleaseOnDispose: Boolean = false,
) : IUnknownReference(pointer, IID.IPropertyValue, preventReleaseOnDispose = preventReleaseOnDispose) {
    fun type(): PropertyType =
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_INT)
            val hr = invokeAbi(
                slot = 6,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            PropertyType.fromCode(resultOut.get(ValueLayout.JAVA_INT, 0))
        }

    fun isNumericScalar(): Boolean =
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_BYTE)
            val hr = invokeAbi(
                slot = 7,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            resultOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0
        }

    fun getValue(): Any? {
        val propertyType = type()
        val scalarAdapter = WinRtValueBoxing.adapterForPropertyType(propertyType)
        if (scalarAdapter != null) {
            return Arena.ofConfined().use { arena ->
                val resultOut = arena.allocate(scalarAdapter.abiLayout)
                val slot = 8 + (propertyType.code - PropertyType.UInt8.code)
                val hr = invokeAbi(
                    slot = slot,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    resultOut,
                )
                WindowsRuntimePlatform.checkSucceeded(hr)
                try {
                    scalarAdapter.readValue(resultOut)
                } finally {
                    scalarAdapter.disposeValue(resultOut)
                }
            }
        }

        val arrayAdapter =
            when (propertyType) {
                PropertyType.InspectableArray -> WinRtValueBoxing.inspectableArrayAdapter()
                else -> WinRtValueBoxing.adapterForPropertyTypeArray(propertyType)
            }
        if (arrayAdapter != null) {
            return Arena.ofConfined().use { arena ->
                val countOut = arena.allocate(ValueLayout.JAVA_INT)
                val dataOut = arena.allocate(ValueLayout.ADDRESS)
                val slot = 26 + (propertyType.code - PropertyType.UInt8Array.code)
                val hr = invokeAbi(
                    slot = slot,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    countOut,
                    dataOut,
                )
                WindowsRuntimePlatform.checkSucceeded(hr)
                val length = countOut.get(ValueLayout.JAVA_INT, 0)
                val data = dataOut.get(ValueLayout.ADDRESS, 0)
                try {
                    arrayAdapter.readOwnedArray(length, data)
                } finally {
                    arrayAdapter.disposeOwnedArray(length, data)
                }
            }
        }

        return null
    }
}

internal object WinRtReferenceProjection {
    fun createMarshaler(
        value: Any?,
        interfaceId: Guid,
    ): WinRtProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, WinRtTypeHandle(value.javaClass.name, interfaceId))?.let { return it }
        val host = WinRtValueBoxing.createReferenceHost(interfaceId, value)
        return WinRtProjectionMarshaler.hosted(host, interfaceId)
    }

    fun fromManaged(
        value: Any?,
        interfaceId: Guid,
    ): MemorySegment =
        if (value == null) {
            MemorySegment.NULL
        } else {
            borrowedProjectionAbi(value, WinRtTypeHandle(value.javaClass.name, interfaceId))
                ?: WinRtValueBoxing.createReferenceHost(interfaceId, value).detachReference(interfaceId)
        }

    fun fromAbi(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): Any? =
        if (pointer == MemorySegment.NULL) {
            null
        } else {
            WinRtReferenceReference(pointer, interfaceId).use { reference ->
                WinRtValueBoxing.readReferenceValue(interfaceId, reference)
            }
        }
}

internal object WinRtReferenceArrayProjection {
    fun createMarshaler(
        value: Any?,
        interfaceId: Guid,
    ): WinRtProjectionMarshaler? {
        if (value == null) {
            return null
        }
        val host = WinRtValueBoxing.createReferenceArrayHost(interfaceId, value)
        return WinRtProjectionMarshaler.hosted(host, interfaceId)
    }

    fun fromManaged(
        value: Any?,
        interfaceId: Guid,
    ): MemorySegment =
        if (value == null) {
            MemorySegment.NULL
        } else {
            WinRtValueBoxing.createReferenceArrayHost(interfaceId, value).detachReference(interfaceId)
        }

    fun fromAbi(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): Array<Any?>? =
        if (pointer == MemorySegment.NULL) {
            null
        } else {
            WinRtReferenceArrayReference(pointer, interfaceId).use { reference ->
                WinRtValueBoxing.readReferenceArrayValue(interfaceId, reference)
            }
        }
}

internal object WinRtPropertyValueProjection {
    fun createMarshaler(value: Any?): WinRtProjectionMarshaler? {
        if (value == null || !WinRtValueBoxing.isPropertyValueCompatible(value)) {
            return null
        }
        val reference = ComWrappersSupport.createCCWForObject(value, IID.IPropertyValue)
        return WinRtProjectionMarshaler(
            abi = reference.pointer,
            ownedReference = reference,
        )
    }

    fun fromManaged(value: Any?): MemorySegment =
        if (value == null) {
            MemorySegment.NULL
        } else {
            if (WinRtValueBoxing.isPropertyValueCompatible(value)) {
                ComWrappersSupport.createCCWForObject(value, IID.IPropertyValue).useAndGetRef()
            } else {
                MemorySegment.NULL
            }
        }

    fun fromOwnedAbi(pointer: MemorySegment): Any? {
        if (pointer == MemorySegment.NULL) {
            return null
        }
        return WinRtPropertyValueReference(pointer).use { it.getValue() }
    }

    fun tryFromBorrowedAbi(pointer: MemorySegment): Any? {
        if (pointer == MemorySegment.NULL) {
            return null
        }
        val propertyValue =
            runCatching {
                IUnknownReference(pointer, IID.IInspectable, preventReleaseOnDispose = true)
                    .queryInterface(IID.IPropertyValue)
                    .getOrThrow()
            }.getOrNull() ?: return null
        return propertyValue.use { reference ->
            WinRtPropertyValueReference(reference.pointer, preventReleaseOnDispose = true).getValue()
        }
    }
}

private fun unboxInspectablePointer(pointer: MemorySegment): Any {
    WinRtInspectableComObject.findManagedValue(pointer)?.let { return it }
    WinRtValueBoxing.tryProjectBorrowedInspectable(pointer)?.let { return it }
    return ComWrappersSupport.createRcwForComObject(pointer)
        ?: WinRtInvalidCastException("Unable to project inspectable value.", HResult(TYPE_E_TYPEMISMATCH))
}

private fun coerceString(value: Any): String =
    when (value) {
        is String -> value
        is Guid -> value.toString()
        else -> throw WinRtInvalidCastException("Cannot coerce ${value.javaClass.name} to String.", HResult(TYPE_E_TYPEMISMATCH))
    }

private fun coerceGuid(value: Any): Guid =
    when (value) {
        is Guid -> value
        is String -> runCatching { Guid(value) }.getOrElse {
            throw WinRtInvalidCastException("Cannot parse Guid from '$value'.", HResult(TYPE_E_TYPEMISMATCH))
        }
        else -> throw WinRtInvalidCastException("Cannot coerce ${value.javaClass.name} to Guid.", HResult(TYPE_E_TYPEMISMATCH))
    }

private fun coerceBoolean(value: Any): Boolean =
    when (value) {
        is Boolean -> value
        else -> throw WinRtInvalidCastException("Cannot coerce ${value.javaClass.name} to Boolean.", HResult(TYPE_E_TYPEMISMATCH))
    }

private fun coerceChar(value: Any): Char =
    when (value) {
        is Char -> value
        else -> throw WinRtInvalidCastException("Cannot coerce ${value.javaClass.name} to Char.", HResult(TYPE_E_TYPEMISMATCH))
    }

private fun coerceUByte(value: Any): UByte = numericCoerce("UByte", value) { (it as Number).toLong().toUByte() }

private fun coerceShort(value: Any): Short = numericCoerce("Short", value) { (it as Number).toLong().toShort() }

private fun coerceUShort(value: Any): UShort = numericCoerce("UShort", value) { (it as Number).toLong().toUShort() }

private fun coerceInt(value: Any): Int = numericCoerce("Int", value) { (it as Number).toLong().toInt() }

private fun coerceUInt(value: Any): UInt = numericCoerce("UInt", value) { (it as Number).toLong().toUInt() }

private fun coerceLong(value: Any): Long = numericCoerce("Long", value) { (it as Number).toLong() }

private fun coerceULong(value: Any): ULong = numericCoerce("ULong", value) { (it as Number).toLong().toULong() }

private fun coerceFloat(value: Any): Float = numericCoerce("Float", value) { (it as Number).toDouble().toFloat() }

private fun coerceDouble(value: Any): Double = numericCoerce("Double", value) { (it as Number).toDouble() }

private fun <T> numericCoerce(
    label: String,
    value: Any,
    convert: (Any) -> T,
): T {
    val coercible =
        value is Byte ||
            value is UByte ||
            value is Short ||
            value is UShort ||
            value is Int ||
            value is UInt ||
            value is Long ||
            value is ULong ||
            value is Float ||
            value is Double
    if (!coercible) {
        throw WinRtInvalidCastException("Cannot coerce ${value.javaClass.name} to $label.", HResult(TYPE_E_TYPEMISMATCH))
    }
    return try {
        convert(value)
    } catch (_: Throwable) {
        throw WinRtInvalidCastException("Numeric coercion overflow for $label.", HResult(DISP_E_OVERFLOW))
    }
}
