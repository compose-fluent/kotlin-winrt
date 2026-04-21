@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Instant

private const val DISP_E_OVERFLOW: Int = 0x8002000A.toInt()
private const val IREFERENCE_GENERIC_INTERFACE = "61C17706-2D65-11E0-9AE8-D48564015472"
private val enumSignaturePattern = Regex("^enum\\((.+);(i4|u4)\\)$")

private data class ManagedArrayBox(
    val elements: Array<*>,
    val adapter: WinRtValueAdapter<*>,
)

private data class WinRtEnumBoxingDescriptor(
    val enumType: KClass<*>,
    val projectedTypeName: String,
    val propertyType: PropertyType,
    val nullableInterfaceId: Guid,
    val toAbiBits: (Any) -> Int,
    val fromAbiBits: (Int) -> Any,
)

internal class WinRtValueAdapter<T : Any>(
    val projectedClass: KClass<*>,
    val nullableInterfaceId: Guid?,
    val referenceArrayInterfaceId: Guid?,
    val propertyType: PropertyType?,
    val propertyTypeArray: PropertyType?,
    val abiLayout: MemoryLayout,
    val isNumericScalar: Boolean = false,
    private val exactUnbox: (Any) -> T,
    private val propertyValueCoerce: (Any) -> T = exactUnbox,
    private val writeTransferredValue: (T, NativePointer) -> Unit,
    private val readOwnedValue: (NativePointer) -> T,
    private val disposeTransferredValue: (NativePointer) -> Unit = {},
) {
    fun unboxExact(value: Any): T = exactUnbox(value)

    fun coercePropertyValue(value: Any): T = propertyValueCoerce(value)

    fun writeValue(value: Any, destination: NativePointer) {
        writeTransferredValue(unboxExact(value), NativeInterop.slice(destination, 0, abiLayout.byteSize()))
    }

    fun writeCoercedPropertyValue(value: Any, destination: NativePointer) {
        writeTransferredValue(coercePropertyValue(value), NativeInterop.slice(destination, 0, abiLayout.byteSize()))
    }

    fun readValue(source: NativePointer): T = readOwnedValue(source)

    fun disposeValue(source: NativePointer) {
        disposeTransferredValue(source)
    }

    fun createTransferredArray(elements: Array<*>): Pair<Int, NativePointer> {
        val arena = Arena.ofShared()
        val data = arena.allocate(abiLayout.byteSize() * elements.size.toLong(), abiLayout.byteAlignment()).asNativePointer()
        return try {
            elements.forEachIndexed { index, element ->
                val slice = NativeInterop.slice(data, index.toLong() * abiLayout.byteSize(), abiLayout.byteSize())
                if (element != null) {
                    writeTransferredValue(unboxExact(element), slice)
                } else {
                    slice.asMemorySegment().fill(0)
                }
            }
            TransferredArrayOwnership.transfer(data) {
                elements.indices.forEach { index ->
                    val slice = NativeInterop.slice(data, index.toLong() * abiLayout.byteSize(), abiLayout.byteSize())
                    disposeTransferredValue(slice)
                }
                arena.close()
            }
            elements.size to data
        } catch (error: Throwable) {
            elements.indices.forEach { index ->
                val slice = NativeInterop.slice(data, index.toLong() * abiLayout.byteSize(), abiLayout.byteSize())
                disposeTransferredValue(slice)
            }
            arena.close()
            throw error
        }
    }

    fun readOwnedArray(length: Int, data: NativePointer): Array<Any?>? {
        if (NativeInterop.isNull(data)) {
            return null
        }
        val readable = data.asMemorySegment().reinterpret(abiLayout.byteSize() * length.toLong()).asNativePointer()
        return Array(length) { index ->
            val slice = NativeInterop.slice(readable, index.toLong() * abiLayout.byteSize(), abiLayout.byteSize())
            readOwnedValue(slice)
        }
    }

    fun disposeOwnedArray(length: Int, data: NativePointer) {
        if (NativeInterop.isNull(data)) {
            return
        }
        if (TransferredArrayOwnership.release(data)) {
            return
        }
        val readable = data.asMemorySegment().reinterpret(abiLayout.byteSize() * length.toLong()).asNativePointer()
        repeat(length) { index ->
            val slice = NativeInterop.slice(readable, index.toLong() * abiLayout.byteSize(), abiLayout.byteSize())
            disposeTransferredValue(slice)
        }
    }
}

private object TransferredArrayOwnership {
    private val cleanups = ConcurrentCacheMap<Long, () -> Unit>()

    fun transfer(
        pointer: NativePointer,
        cleanup: () -> Unit,
    ) {
        cleanups[NativeInterop.pointerKey(pointer)] = cleanup
    }

    fun release(pointer: NativePointer): Boolean =
        cleanups.remove(NativeInterop.pointerKey(pointer))?.let {
            it()
            true
        } ?: false
}

private fun <T : Any> directValueAdapter(
    projectedClass: KClass<*>,
    nullableInterfaceId: Guid?,
    referenceArrayInterfaceId: Guid?,
    propertyType: PropertyType?,
    propertyTypeArray: PropertyType?,
    abiLayout: MemoryLayout,
    isNumericScalar: Boolean = false,
    exactUnbox: (Any) -> T,
    propertyValueCoerce: (Any) -> T = exactUnbox,
    readOwnedValue: (NativePointer) -> T,
    writeTransferredValue: (T, NativePointer) -> Unit,
    disposeTransferredValue: (NativePointer) -> Unit = {},
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
    projectedClass: KClass<*>,
    nullableInterfaceId: Guid?,
    referenceArrayInterfaceId: Guid?,
    propertyType: PropertyType?,
    propertyTypeArray: PropertyType?,
    exactUnbox: (Any) -> T,
    propertyValueCoerce: (Any) -> T = exactUnbox,
    createPointer: (T) -> NativePointer,
    readOwnedPointer: (NativePointer) -> T,
    disposeOwnedPointer: (NativePointer) -> Unit,
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
        readOwnedValue = { source -> readOwnedPointer(NativeInterop.readPointer(source)) },
        writeTransferredValue = { value, destination ->
            NativeInterop.writePointer(destination, createPointer(value))
        },
        disposeTransferredValue = { source ->
            val pointer = NativeInterop.readPointer(source)
            if (!NativeInterop.isNull(pointer)) {
                disposeOwnedPointer(pointer)
            }
        },
    )

internal object WinRtValueBoxing {
    private val stringAdapter =
        pointerValueAdapter(
            projectedClass = String::class,
            nullableInterfaceId = IID.NullableString,
            referenceArrayInterfaceId = IID.IReferenceArrayOfString,
            propertyType = PropertyType.String,
            propertyTypeArray = PropertyType.StringArray,
            exactUnbox = { it as String },
            propertyValueCoerce = ::coerceString,
            createPointer = { value -> StringMarshaller.fromManaged(value)?.handle ?: NativeInterop.nullPointer },
            readOwnedPointer = { pointer -> StringMarshaller.fromAbi(pointer.asMemorySegment()) },
            disposeOwnedPointer = { pointer -> StringMarshaller.disposeAbi(pointer.asMemorySegment()) },
        )

    private val objectAdapter =
        pointerValueAdapter(
            projectedClass = Any::class,
            nullableInterfaceId = IID.NullableObject,
            referenceArrayInterfaceId = IID.IReferenceArrayOfObject,
            propertyType = null,
            propertyTypeArray = PropertyType.InspectableArray,
            exactUnbox = { it },
            createPointer = { value -> ComWrappersSupport.createCCWForObject(value, IID.IInspectable).useAndGetRef() },
            readOwnedPointer = { pointer -> unboxInspectablePointer(pointer.asMemorySegment()) },
            disposeOwnedPointer = { pointer -> IUnknownReference(pointer, IID.IInspectable).close() },
        )

    private val classAdapter =
        directValueAdapter<KClass<*>>(
            projectedClass = KClass::class,
            nullableInterfaceId = IID.NullableType,
            referenceArrayInterfaceId = IID.IReferenceArrayOfType,
            propertyType = null,
            propertyTypeArray = null,
            abiLayout = NativeLayoutsJvmCompat.TYPE_NAME,
            exactUnbox = { it as KClass<*> },
            readOwnedValue = { source ->
                TypeProjection.fromAbi(source)
                    ?: throw WinRtInvalidCastException("Expected non-null projected KClass value.", HResult(TYPE_E_TYPEMISMATCH))
            },
            writeTransferredValue = TypeProjection::copyTo,
            disposeTransferredValue = TypeProjection::disposeAbi,
        )

    private val exceptionAdapter =
        directValueAdapter(
            projectedClass = Exception::class,
            nullableInterfaceId = IID.NullableException,
            referenceArrayInterfaceId = IID.IReferenceArrayOfException,
            propertyType = null,
            propertyTypeArray = null,
            abiLayout = ValueLayout.JAVA_INT,
            exactUnbox = { it as Exception },
            readOwnedValue = { source -> ExceptionProjection.fromAbi(source.asMemorySegment().get(ValueLayout.JAVA_INT, 0)) },
            writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_INT, 0, ExceptionProjection.toAbi(value)) },
        )

    private val adapters: List<WinRtValueAdapter<*>> =
        listOf<WinRtValueAdapter<*>>(
            directValueAdapter(
                projectedClass = Byte::class,
                nullableInterfaceId = IID.NullableSByte,
                referenceArrayInterfaceId = IID.IReferenceArrayOfSByte,
                propertyType = null,
                propertyTypeArray = null,
                abiLayout = ValueLayout.JAVA_BYTE,
                exactUnbox = { it as Byte },
                readOwnedValue = { source -> source.asMemorySegment().get(ValueLayout.JAVA_BYTE, 0) },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_BYTE, 0, value) },
            ),
            directValueAdapter(
                projectedClass = UByte::class,
                nullableInterfaceId = IID.NullableByte,
                referenceArrayInterfaceId = IID.IReferenceArrayOfByte,
                propertyType = PropertyType.UInt8,
                propertyTypeArray = PropertyType.UInt8Array,
                abiLayout = ValueLayout.JAVA_BYTE,
                isNumericScalar = true,
                exactUnbox = { it as UByte },
                propertyValueCoerce = ::coerceUByte,
                readOwnedValue = { source -> source.asMemorySegment().get(ValueLayout.JAVA_BYTE, 0).toUByte() },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_BYTE, 0, value.toByte()) },
            ),
            directValueAdapter(
                projectedClass = Short::class,
                nullableInterfaceId = IID.NullableShort,
                referenceArrayInterfaceId = IID.IReferenceArrayOfInt16,
                propertyType = PropertyType.Int16,
                propertyTypeArray = PropertyType.Int16Array,
                abiLayout = ValueLayout.JAVA_SHORT,
                isNumericScalar = true,
                exactUnbox = { it as Short },
                propertyValueCoerce = ::coerceShort,
                readOwnedValue = { source -> source.asMemorySegment().get(ValueLayout.JAVA_SHORT, 0) },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_SHORT, 0, value) },
            ),
            directValueAdapter(
                projectedClass = UShort::class,
                nullableInterfaceId = IID.NullableUShort,
                referenceArrayInterfaceId = IID.IReferenceArrayOfUInt16,
                propertyType = PropertyType.UInt16,
                propertyTypeArray = PropertyType.UInt16Array,
                abiLayout = ValueLayout.JAVA_SHORT,
                isNumericScalar = true,
                exactUnbox = { it as UShort },
                propertyValueCoerce = ::coerceUShort,
                readOwnedValue = { source -> source.asMemorySegment().get(ValueLayout.JAVA_SHORT, 0).toUShort() },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_SHORT, 0, value.toShort()) },
            ),
            directValueAdapter(
                projectedClass = Int::class,
                nullableInterfaceId = IID.NullableInt,
                referenceArrayInterfaceId = IID.IReferenceArrayOfInt32,
                propertyType = PropertyType.Int32,
                propertyTypeArray = PropertyType.Int32Array,
                abiLayout = ValueLayout.JAVA_INT,
                isNumericScalar = true,
                exactUnbox = { it as Int },
                propertyValueCoerce = ::coerceInt,
                readOwnedValue = { source -> source.asMemorySegment().get(ValueLayout.JAVA_INT, 0) },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_INT, 0, value) },
            ),
            directValueAdapter(
                projectedClass = UInt::class,
                nullableInterfaceId = IID.NullableUInt,
                referenceArrayInterfaceId = IID.IReferenceArrayOfUInt32,
                propertyType = PropertyType.UInt32,
                propertyTypeArray = PropertyType.UInt32Array,
                abiLayout = ValueLayout.JAVA_INT,
                isNumericScalar = true,
                exactUnbox = { it as UInt },
                propertyValueCoerce = ::coerceUInt,
                readOwnedValue = { source -> source.asMemorySegment().get(ValueLayout.JAVA_INT, 0).toUInt() },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_INT, 0, value.toInt()) },
            ),
            directValueAdapter(
                projectedClass = Long::class,
                nullableInterfaceId = IID.NullableLong,
                referenceArrayInterfaceId = IID.IReferenceArrayOfInt64,
                propertyType = PropertyType.Int64,
                propertyTypeArray = PropertyType.Int64Array,
                abiLayout = ValueLayout.JAVA_LONG,
                isNumericScalar = true,
                exactUnbox = { it as Long },
                propertyValueCoerce = ::coerceLong,
                readOwnedValue = { source -> source.asMemorySegment().get(ValueLayout.JAVA_LONG, 0) },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_LONG, 0, value) },
            ),
            directValueAdapter(
                projectedClass = ULong::class,
                nullableInterfaceId = IID.NullableULong,
                referenceArrayInterfaceId = IID.IReferenceArrayOfUInt64,
                propertyType = PropertyType.UInt64,
                propertyTypeArray = PropertyType.UInt64Array,
                abiLayout = ValueLayout.JAVA_LONG,
                isNumericScalar = true,
                exactUnbox = { it as ULong },
                propertyValueCoerce = ::coerceULong,
                readOwnedValue = { source -> source.asMemorySegment().get(ValueLayout.JAVA_LONG, 0).toULong() },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_LONG, 0, value.toLong()) },
            ),
            directValueAdapter(
                projectedClass = Float::class,
                nullableInterfaceId = IID.NullableFloat,
                referenceArrayInterfaceId = IID.IReferenceArrayOfSingle,
                propertyType = PropertyType.Single,
                propertyTypeArray = PropertyType.SingleArray,
                abiLayout = ValueLayout.JAVA_FLOAT,
                isNumericScalar = true,
                exactUnbox = { it as Float },
                propertyValueCoerce = ::coerceFloat,
                readOwnedValue = { source -> source.asMemorySegment().get(ValueLayout.JAVA_FLOAT, 0) },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_FLOAT, 0, value) },
            ),
            directValueAdapter(
                projectedClass = Double::class,
                nullableInterfaceId = IID.NullableDouble,
                referenceArrayInterfaceId = IID.IReferenceArrayOfDouble,
                propertyType = PropertyType.Double,
                propertyTypeArray = PropertyType.DoubleArray,
                abiLayout = ValueLayout.JAVA_DOUBLE,
                isNumericScalar = true,
                exactUnbox = { it as Double },
                propertyValueCoerce = ::coerceDouble,
                readOwnedValue = { source -> source.asMemorySegment().get(ValueLayout.JAVA_DOUBLE, 0) },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_DOUBLE, 0, value) },
            ),
            directValueAdapter(
                projectedClass = Char::class,
                nullableInterfaceId = IID.NullableChar,
                referenceArrayInterfaceId = IID.IReferenceArrayOfChar,
                propertyType = PropertyType.Char16,
                propertyTypeArray = PropertyType.Char16Array,
                abiLayout = NativeLayoutsJvmCompat.CHAR16,
                exactUnbox = { it as Char },
                propertyValueCoerce = ::coerceChar,
                readOwnedValue = { source -> source.asMemorySegment().get(NativeLayoutsJvmCompat.CHAR16, 0) },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(NativeLayoutsJvmCompat.CHAR16, 0, value) },
            ),
            directValueAdapter(
                projectedClass = Boolean::class,
                nullableInterfaceId = IID.NullableBool,
                referenceArrayInterfaceId = IID.IReferenceArrayOfBoolean,
                propertyType = PropertyType.Boolean,
                propertyTypeArray = PropertyType.BooleanArray,
                abiLayout = ValueLayout.JAVA_BYTE,
                exactUnbox = { it as Boolean },
                propertyValueCoerce = ::coerceBoolean,
                readOwnedValue = { source -> source.asMemorySegment().get(ValueLayout.JAVA_BYTE, 0).toInt() != 0 },
                writeTransferredValue = { value, destination -> destination.asMemorySegment().set(ValueLayout.JAVA_BYTE, 0, if (value) 1 else 0) },
            ),
            stringAdapter,
            directValueAdapter(
                projectedClass = Guid::class,
                nullableInterfaceId = IID.NullableGuid,
                referenceArrayInterfaceId = IID.IReferenceArrayOfGuid,
                propertyType = PropertyType.Guid,
                propertyTypeArray = PropertyType.GuidArray,
                abiLayout = NativeLayoutsJvmCompat.GUID,
                exactUnbox = { it as Guid },
                propertyValueCoerce = ::coerceGuid,
                readOwnedValue = GuidMarshaller::readFrom,
                writeTransferredValue = GuidMarshaller::copyTo,
            ),
            directValueAdapter(
                projectedClass = Instant::class,
                nullableInterfaceId = IID.NullableDateTimeOffset,
                referenceArrayInterfaceId = IID.IReferenceArrayOfDateTimeOffset,
                propertyType = PropertyType.DateTime,
                propertyTypeArray = PropertyType.DateTimeArray,
                abiLayout = ValueLayout.JAVA_LONG,
                exactUnbox = { it as Instant },
                readOwnedValue = { source -> DateTimeProjection.fromAbi(source.asMemorySegment().get(ValueLayout.JAVA_LONG, 0)) },
                writeTransferredValue = { value, destination ->
                    DateTimeProjection.copyTo(value, destination)
                },
            ),
            directValueAdapter(
                projectedClass = Duration::class,
                nullableInterfaceId = IID.NullableTimeSpan,
                referenceArrayInterfaceId = IID.IReferenceArrayOfTimeSpan,
                propertyType = PropertyType.TimeSpan,
                propertyTypeArray = PropertyType.TimeSpanArray,
                abiLayout = ValueLayout.JAVA_LONG,
                exactUnbox = { it as Duration },
                readOwnedValue = { source -> TimeSpanProjection.fromAbi(source.asMemorySegment().get(ValueLayout.JAVA_LONG, 0)) },
                writeTransferredValue = { value, destination ->
                    TimeSpanProjection.copyTo(value, destination)
                },
            ),
            directValueAdapter(
                projectedClass = Point::class,
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
                projectedClass = Size::class,
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
                projectedClass = Rect::class,
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
                projectedClass = Matrix3x2::class,
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
                projectedClass = Matrix4x4::class,
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
                projectedClass = Plane::class,
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
                projectedClass = Quaternion::class,
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
                projectedClass = Vector2::class,
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
                projectedClass = Vector3::class,
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
                projectedClass = Vector4::class,
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

    internal fun boxedRuntimeClassNameForType(type: KClass<*>): String? {
        enumDescriptorForClass(type)?.let { descriptor ->
            return WinRtReferenceTypeNames.boxedReference(descriptor.projectedTypeName)
        }
        WinRtTypeClassifier.primitiveArrayElementType(type)?.let { elementType ->
            val adapter = adapterForClass(elementType) ?: return null
            val interfaceId = adapter.referenceArrayInterfaceId ?: return null
            return boxedReferenceArrayRuntimeClassName(interfaceId, adapter)
        }
        if (isArrayKClass(type)) {
            val adapter = arrayElementType(type)?.let(::adapterForClass) ?: return null
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
            runtimeClassName = boxedRuntimeClassNameForType(value::class),
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

        enumDescriptorForClass(value::class)?.let { return it.propertyType }

        val adapter = classifyPropertyValue(value)
        if (adapter != null) {
            return adapter.propertyType ?: PropertyType.OtherType
        }
        return PropertyType.OtherType
    }

    fun isNumericScalar(value: Any): Boolean =
        normalizeManagedArray(value) == null &&
            (classifyPropertyValue(value)?.isNumericScalar == true || enumDescriptorForClass(value::class) != null)

    fun writePropertyValue(
        expectedType: PropertyType,
        value: Any,
        destination: MemorySegment,
    ) {
        val enumDescriptor = enumDescriptorForClass(value::class)
        if (enumDescriptor != null && enumDescriptor.propertyType == expectedType) {
            destination.reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, enumDescriptor.toAbiBits(value))
            return
        }

        val adapter = adaptersByPropertyType[expectedType]
        if (adapter != null) {
            adapter.writeCoercedPropertyValue(value, destination.asNativePointer())
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
        dataOut.set(ValueLayout.ADDRESS, 0, data.asMemorySegment())
    }

    fun readReferenceValue(
        interfaceId: Guid,
        reference: WinRtReferenceReference,
    ): Any? {
        val adapter = adapterForReferenceInterface(interfaceId)
            ?: throw WinRtInvalidCastException("Unsupported IReference interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH))
        return reference.readValue(
            sizeBytes = adapter.abiLayout.byteSize(),
            alignmentBytes = adapter.abiLayout.byteAlignment(),
            readValue = adapter::readValue,
            disposeValue = adapter::disposeValue,
        )
    }

    fun readReferenceArrayValue(
        interfaceId: Guid,
        reference: WinRtReferenceArrayReference,
    ): Array<Any?>? {
        val adapter = adapterForReferenceArrayInterface(interfaceId)
            ?: throw WinRtInvalidCastException("Unsupported IReferenceArray interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH))
        return reference.readValue(
            readArray = adapter::readOwnedArray,
            disposeArray = adapter::disposeOwnedArray,
        )
    }

    fun createReferenceHost(
        interfaceId: Guid,
        value: Any,
    ): WinRtInspectableComObject {
        return WinRtInspectableComObject(
            interfaceDefinitions = listOf(buildReferenceInterfaceDefinition(interfaceId, value)),
            runtimeClassName = boxedRuntimeClassNameForType(value::class),
            managedValue = value,
        )
    }

    fun createReferenceArrayHost(
        interfaceId: Guid,
        value: Any,
    ): WinRtInspectableComObject {
        return WinRtInspectableComObject(
            interfaceDefinitions = listOf(buildReferenceArrayInterfaceDefinition(interfaceId, value)),
            runtimeClassName = boxedRuntimeClassNameForType(value::class),
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
            runtimeClassName = boxedRuntimeClassNameForType(value::class),
            managedValue = value,
        )
    }

    internal fun tryProjectInspectable(
        inspectable: IInspectableReference,
        runtimeClassName: String? = inspectable.tryGetRuntimeClassName(),
    ): Any? {
        if (!runtimeClassName.isNullOrBlank()) {
            TypeNameSupport.findRcwKClassByNameCached(runtimeClassName)?.let { projectedType ->
                tryProjectInspectableAsType(inspectable, projectedType)?.let { return it }
            }
        }

        WinRtPropertyValueProjection.tryFromBorrowedAbi(inspectable.pointer)?.let { return it }

        adapters.firstNotNullOfOrNull { adapter ->
            val interfaceId = adapter.nullableInterfaceId ?: return@firstNotNullOfOrNull null
            queryReferencePointer(inspectable, interfaceId)?.use { reference ->
                WinRtReferenceReference(reference.pointer, interfaceId, preventReleaseOnDispose = true).readValue(
                    sizeBytes = adapter.abiLayout.byteSize(),
                    alignmentBytes = adapter.abiLayout.byteAlignment(),
                    readValue = adapter::readValue,
                    disposeValue = adapter::disposeValue,
                )
            }
        }?.let { return it }

        adapters.firstNotNullOfOrNull { adapter ->
            val interfaceId = adapter.referenceArrayInterfaceId ?: return@firstNotNullOfOrNull null
            queryReferencePointer(inspectable, interfaceId)?.use { reference ->
                WinRtReferenceArrayReference(reference.pointer, interfaceId, preventReleaseOnDispose = true).readValue(
                    readArray = adapter::readOwnedArray,
                    disposeArray = adapter::disposeOwnedArray,
                )
            }
        }?.let { return it }

        return null
    }

    internal fun tryProjectBorrowedInspectable(pointer: NativePointer): Any? {
        if (NativeInterop.isNull(pointer)) {
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

    internal fun tryProjectBorrowedInspectable(pointer: MemorySegment): Any? =
        tryProjectBorrowedInspectable(pointer.asNativePointer())

    private fun buildReferenceInterfaceDefinition(
        interfaceId: Guid,
        value: Any,
    ): WinRtInspectableInterfaceDefinition {
        val adapter = adapterForReferenceInterface(interfaceId)
        val enumDescriptor = enumDescriptorForClass(value::class)
        if (adapter == null && (enumDescriptor == null || enumDescriptor.nullableInterfaceId != interfaceId)) {
            throw WinRtInvalidCastException("Unsupported IReference interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH))
        }
        return WinRtInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = listOf(
                WinRtInspectableMethodDefinition(
                    descriptor = NativeFunctionDescriptor.of(
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                ) { rawArgs ->
                    val destination =
                        rawArgs.singleOrNull() as? NativePointer
                            ?: throw IllegalStateException("IReference host requires one out-argument.")
                    if (adapter != null) {
                        requireNotNull(adapter).writeValue(value, destination)
                    } else {
                        NativeInterop.writeInt32(destination, requireNotNull(enumDescriptor).toAbiBits(value))
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
                    descriptor = NativeFunctionDescriptor.of(
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                ) { rawArgs ->
                    if (rawArgs.size != 2) {
                        throw IllegalStateException("IReferenceArray host requires count and data out-arguments.")
                    }
                    val (length, data) = adapter.createTransferredArray(box.elements)
                    NativeInterop.writeInt32(rawArgs[0] as NativePointer, length)
                    NativeInterop.writePointer(rawArgs[1] as NativePointer, data)
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
                    descriptor = NativeFunctionDescriptor.of(
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                ) { rawArgs ->
                    NativeInterop.writeInt32(rawArgs[0] as NativePointer, propertyTypeOf(value).code)
                    KnownHResults.S_OK.value
                },
            )
            add(
                WinRtInspectableMethodDefinition(
                    descriptor = NativeFunctionDescriptor.of(
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                ) { rawArgs ->
                    NativeInterop.writeInt8(rawArgs[0] as NativePointer, if (isNumericScalar(value)) 1 else 0)
                    KnownHResults.S_OK.value
                },
            )
            scalarGetters.forEach { propertyType ->
                add(
                    WinRtInspectableMethodDefinition(
                        descriptor = NativeFunctionDescriptor.of(
                            NativeValueLayout.JAVA_INT,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
                        ),
                    ) { rawArgs ->
                        writePropertyValue(propertyType, value, (rawArgs[0] as NativePointer).asMemorySegment())
                        KnownHResults.S_OK.value
                    },
                )
            }
            arrayGetters.forEach { propertyType ->
                add(
                    WinRtInspectableMethodDefinition(
                        descriptor = NativeFunctionDescriptor.of(
                            NativeValueLayout.JAVA_INT,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
                        ),
                    ) { rawArgs ->
                        writePropertyValueArray(
                            propertyType,
                            value,
                            (rawArgs[0] as NativePointer).asMemorySegment(),
                            (rawArgs[1] as NativePointer).asMemorySegment(),
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

    private fun adapterForValue(value: Any): WinRtValueAdapter<*>? = adapterForClass(value::class)

    private fun adapterForClass(type: KClass<*>): WinRtValueAdapter<*>? =
        adaptersByClass[type]
            ?: if (platformIsAssignableFrom(Exception::class, type)) {
                exceptionAdapter
            } else {
                null
            }

    private fun normalizeManagedArray(value: Any): ManagedArrayBox? =
        when (value) {
            is Array<*> -> {
                val componentType = platformArrayElementType(value::class) ?: Any::class
                val adapter =
                    adapterForClass(componentType)
                        ?: if (componentType == Any::class) {
                            objectAdapter
                        } else {
                            null
                        }
                adapter?.let { ManagedArrayBox(value, it) }
            }
            else -> normalizePrimitiveManagedArray(value)
        }

    private fun isSupportedArrayValue(value: Any): Boolean = isArrayKClass(value::class)

    private fun normalizePrimitiveManagedArray(value: Any): ManagedArrayBox? {
        val elementType = WinRtTypeClassifier.primitiveArrayElementType(value::class) ?: return null
        val adapter = adaptersByClass[elementType] ?: return null
        val boxedElements = WinRtTypeClassifier.boxPrimitiveArray(value) ?: return null
        return ManagedArrayBox(boxedElements, adapter)
    }

    private fun referenceInterfaceIdForValue(value: Any): Guid? =
        adapterForValue(value)?.nullableInterfaceId ?: enumDescriptorForClass(value::class)?.nullableInterfaceId

    private fun referenceArrayInterfaceIdForValue(value: Any): Guid? =
        normalizeManagedArray(value)?.adapter?.referenceArrayInterfaceId

    private fun boxedReferenceRuntimeClassName(
        interfaceId: Guid,
        adapter: WinRtValueAdapter<*>,
    ): String {
        check(adapter.nullableInterfaceId == interfaceId)
        return WinRtReferenceTypeNames.boxedReference(TypeNameSupport.getNameForType(adapter.projectedClass))
    }

    private fun boxedReferenceArrayRuntimeClassName(
        interfaceId: Guid,
        adapter: WinRtValueAdapter<*>,
    ): String {
        check(adapter.referenceArrayInterfaceId == interfaceId)
        return WinRtReferenceTypeNames.boxedReferenceArray(TypeNameSupport.getNameForType(adapter.projectedClass))
    }

    private fun tryProjectInspectableAsType(
        inspectable: IInspectableReference,
        projectedType: KClass<*>,
    ): Any? {
        enumDescriptorForClass(projectedType)?.let { descriptor ->
            return queryReferencePointer(inspectable, descriptor.nullableInterfaceId)?.use { reference ->
                readEnumReferenceValue(
                    WinRtReferenceReference(
                        reference.pointer.asMemorySegment(),
                        descriptor.nullableInterfaceId,
                        preventReleaseOnDispose = true,
                    ),
                    descriptor,
                )
            }
        }

        if (isArrayKClass(projectedType) || WinRtTypeClassifier.primitiveArrayElementType(projectedType) != null) {
            val elementType = WinRtTypeClassifier.primitiveArrayElementType(projectedType) ?: arrayElementType(projectedType) ?: return null
            val adapter = adapterForClass(elementType) ?: return null
            val interfaceId = adapter.referenceArrayInterfaceId ?: return null
            return queryReferencePointer(inspectable, interfaceId)?.use { reference ->
                WinRtReferenceArrayReference(reference.pointer, interfaceId, preventReleaseOnDispose = true).readValue(
                    readArray = adapter::readOwnedArray,
                    disposeArray = adapter::disposeOwnedArray,
                )
            }
        }

        val adapter = adapterForClass(projectedType) ?: return null
        val interfaceId = adapter.nullableInterfaceId ?: return null
        return queryReferencePointer(inspectable, interfaceId)?.use { reference ->
            WinRtReferenceReference(reference.pointer, interfaceId, preventReleaseOnDispose = true).readValue(
                sizeBytes = adapter.abiLayout.byteSize(),
                alignmentBytes = adapter.abiLayout.byteAlignment(),
                readValue = adapter::readValue,
                disposeValue = adapter::disposeValue,
            )
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
        NativeInterop.confinedScope().use { scope ->
            val resultOut = NativeInterop.allocateInt32Slot(scope)
            val hr = reference.invokeAbi(
                slot = 6,
                descriptor = NativeFunctionDescriptor.of(
                    NativeValueLayout.JAVA_INT,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            descriptor.fromAbiBits(NativeInterop.readInt32(resultOut))
        }

    private fun enumDescriptorForClass(
        type: KClass<*>,
    ): WinRtEnumBoxingDescriptor? {
        if (!platformIsEnumType(type)) {
            return null
        }

        val registeredType = type.registeredWinRtType() ?: return null
        val signature = runCatching { GuidGenerator.getSignature(type) }.getOrNull() ?: return null
        val match = enumSignaturePattern.matchEntire(signature) ?: return null
        val projectedTypeName = match.groupValues[1]
        val underlyingSignature = match.groupValues[2]
        val enumAbiValue = registeredType.enumAbiValue as? (Any) -> Int
        if (enumAbiValue == null) {
            return null
        }

        fun readBits(enumValue: Any): Int =
            enumAbiValue(enumValue)

        val constants = platformEnumConstants(type) ?: return null

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
                                "Unknown enum value $abiValue for ${type.typeDisplayName()}.",
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
                                "Unknown enum value ${abiValue.toUInt()} for ${type.typeDisplayName()}.",
                                HResult(TYPE_E_TYPEMISMATCH),
                            )
                    },
                )

            else -> null
        }
    }
}

private fun isArrayKClass(type: KClass<*>): Boolean = platformArrayElementType(type) != null

private fun arrayElementType(type: KClass<*>): KClass<*>? = platformArrayElementType(type)

internal fun WinRtReferenceReference(
    pointer: MemorySegment,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
): WinRtReferenceReference =
    WinRtReferenceReference(pointer.asNativePointer(), interfaceId, preventReleaseOnDispose)

internal fun WinRtReferenceArrayReference(
    pointer: MemorySegment,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
): WinRtReferenceArrayReference =
    WinRtReferenceArrayReference(pointer.asNativePointer(), interfaceId, preventReleaseOnDispose)

internal fun WinRtPropertyValueReference(
    pointer: MemorySegment,
    preventReleaseOnDispose: Boolean = false,
): WinRtPropertyValueReference =
    WinRtPropertyValueReference(pointer.asNativePointer(), preventReleaseOnDispose)

private fun unboxInspectablePointer(pointer: MemorySegment): Any {
    WinRtInspectableComObject.findManagedValue(pointer.asNativePointer())?.let { return it }
    WinRtValueBoxing.tryProjectBorrowedInspectable(pointer)?.let { return it }
    return ComWrappersSupport.createRcwForComObject(pointer.asNativePointer())
        ?: WinRtInvalidCastException("Unable to project inspectable value.", HResult(TYPE_E_TYPEMISMATCH))
}

private fun coerceString(value: Any): String =
    when (value) {
        is String -> value
        is Guid -> value.toString()
        else -> throw WinRtInvalidCastException("Cannot coerce ${value::class.typeDisplayName()} to String.", HResult(TYPE_E_TYPEMISMATCH))
    }

private fun coerceGuid(value: Any): Guid =
    when (value) {
        is Guid -> value
        is String -> runCatching { Guid(value) }.getOrElse {
            throw WinRtInvalidCastException("Cannot parse Guid from '$value'.", HResult(TYPE_E_TYPEMISMATCH))
        }
        else -> throw WinRtInvalidCastException("Cannot coerce ${value::class.typeDisplayName()} to Guid.", HResult(TYPE_E_TYPEMISMATCH))
    }

private fun coerceBoolean(value: Any): Boolean =
    when (value) {
        is Boolean -> value
        else -> throw WinRtInvalidCastException("Cannot coerce ${value::class.typeDisplayName()} to Boolean.", HResult(TYPE_E_TYPEMISMATCH))
    }

private fun coerceChar(value: Any): Char =
    when (value) {
        is Char -> value
        else -> throw WinRtInvalidCastException("Cannot coerce ${value::class.typeDisplayName()} to Char.", HResult(TYPE_E_TYPEMISMATCH))
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
        throw WinRtInvalidCastException("Cannot coerce ${value::class.typeDisplayName()} to $label.", HResult(TYPE_E_TYPEMISMATCH))
    }
    return try {
        convert(value)
    } catch (_: Throwable) {
        throw WinRtInvalidCastException("Numeric coercion overflow for $label.", HResult(DISP_E_OVERFLOW))
    }
}
