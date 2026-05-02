@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

// ---------------------------------------------------------------------------
// WinRtValueAdapter — per-type ABI read/write/array marshalling adapter.
// ---------------------------------------------------------------------------

internal class WinRtValueAdapter<T : Any>(
    val projectedClass: KClass<*>,
    val nullableInterfaceId: Guid?,
    val referenceArrayInterfaceId: Guid?,
    val propertyType: PropertyType?,
    val propertyTypeArray: PropertyType?,
    val abiLayout: NativeAbiLayout,
    val isNumericScalar: Boolean = false,
    private val exactUnbox: (Any) -> T,
    private val propertyValueCoerce: (Any) -> T = exactUnbox,
    private val writeTransferredValue: (T, RawAddress) -> Unit,
    private val readOwnedValue: (RawAddress) -> T,
    private val disposeTransferredValue: (RawAddress) -> Unit = {},
) {
    fun unboxExact(value: Any): T = exactUnbox(value)

    fun coercePropertyValue(value: Any): T = propertyValueCoerce(value)

    fun writeValue(value: Any, destination: RawAddress) {
        writeTransferredValue(unboxExact(value), PlatformAbi.slice(destination, 0, abiLayout.byteSize))
    }

    fun writeCoercedPropertyValue(value: Any, destination: RawAddress) {
        writeTransferredValue(coercePropertyValue(value), PlatformAbi.slice(destination, 0, abiLayout.byteSize))
    }

    fun readValue(source: RawAddress): T = readOwnedValue(source)

    fun disposeValue(source: RawAddress) {
        disposeTransferredValue(source)
    }

    fun createTransferredArray(elements: Array<*>): Pair<Int, RawAddress> {
        val owned = PlatformAbi.allocateBytesOwned(
            sizeBytes = abiLayout.byteSize * elements.size.toLong(),
            alignmentBytes = abiLayout.byteAlignment,
        )
        val data = owned.pointer
        return try {
            elements.forEachIndexed { index, element ->
                val slice = PlatformAbi.slice(data, index.toLong() * abiLayout.byteSize, abiLayout.byteSize)
                if (element != null) {
                    writeTransferredValue(unboxExact(element), slice)
                } else {
                    PlatformAbi.zeroBytes(slice, abiLayout.byteSize)
                }
            }
            TransferredArrayOwnership.transfer(data) {
                elements.indices.forEach { index ->
                    val slice = PlatformAbi.slice(data, index.toLong() * abiLayout.byteSize, abiLayout.byteSize)
                    disposeTransferredValue(slice)
                }
                owned.close()
            }
            elements.size to data
        } catch (error: Throwable) {
            elements.indices.forEach { index ->
                val slice = PlatformAbi.slice(data, index.toLong() * abiLayout.byteSize, abiLayout.byteSize)
                disposeTransferredValue(slice)
            }
            owned.close()
            throw error
        }
    }

    fun readOwnedArray(length: Int, data: RawAddress): Array<Any?>? {
        if (PlatformAbi.isNull(data)) return null
        return Array(length) { index ->
            val slice = PlatformAbi.slice(data, index.toLong() * abiLayout.byteSize, abiLayout.byteSize)
            readOwnedValue(slice)
        }
    }

    fun disposeOwnedArray(length: Int, data: RawAddress) {
        if (PlatformAbi.isNull(data)) return
        if (TransferredArrayOwnership.release(data)) return
        repeat(length) { index ->
            val slice = PlatformAbi.slice(data, index.toLong() * abiLayout.byteSize, abiLayout.byteSize)
            disposeTransferredValue(slice)
        }
    }
}

private object TransferredArrayOwnership {
    private val cleanups = ConcurrentCacheMap<Long, () -> Unit>()

    fun transfer(pointer: RawAddress, cleanup: () -> Unit) {
        cleanups[PlatformAbi.pointerKey(pointer)] = cleanup
    }

    fun release(pointer: RawAddress): Boolean =
        cleanups.remove(PlatformAbi.pointerKey(pointer))?.let { it(); true } ?: false
}

internal fun <T : Any> directValueAdapter(
    projectedClass: KClass<*>,
    nullableInterfaceId: Guid?,
    referenceArrayInterfaceId: Guid?,
    propertyType: PropertyType?,
    propertyTypeArray: PropertyType?,
    abiLayout: NativeAbiLayout,
    isNumericScalar: Boolean = false,
    exactUnbox: (Any) -> T,
    propertyValueCoerce: (Any) -> T = exactUnbox,
    readOwnedValue: (RawAddress) -> T,
    writeTransferredValue: (T, RawAddress) -> Unit,
    disposeTransferredValue: (RawAddress) -> Unit = {},
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

internal fun <T : Any> pointerValueAdapter(
    projectedClass: KClass<*>,
    nullableInterfaceId: Guid?,
    referenceArrayInterfaceId: Guid?,
    propertyType: PropertyType?,
    propertyTypeArray: PropertyType?,
    exactUnbox: (Any) -> T,
    propertyValueCoerce: (Any) -> T = exactUnbox,
    createPointer: (T) -> RawAddress,
    readOwnedPointer: (RawAddress) -> T,
    disposeOwnedPointer: (RawAddress) -> Unit,
): WinRtValueAdapter<T> =
    directValueAdapter(
        projectedClass = projectedClass,
        nullableInterfaceId = nullableInterfaceId,
        referenceArrayInterfaceId = referenceArrayInterfaceId,
        propertyType = propertyType,
        propertyTypeArray = propertyTypeArray,
        abiLayout = NativeAbiLayout.ADDRESS,
        exactUnbox = exactUnbox,
        propertyValueCoerce = propertyValueCoerce,
        readOwnedValue = { source -> readOwnedPointer(PlatformAbi.readPointer(source)) },
        writeTransferredValue = { value, destination ->
            PlatformAbi.writePointer(destination, createPointer(value))
        },
        disposeTransferredValue = { source ->
            val pointer = PlatformAbi.readPointer(source)
            if (!PlatformAbi.isNull(pointer)) disposeOwnedPointer(pointer)
        },
    )

// ---------------------------------------------------------------------------
// ValueBoxingInterop — adapter registry and IReference/IPropertyValue I/O.
// ---------------------------------------------------------------------------

internal object ValueBoxingInterop {
    private val stringAdapter =
        pointerValueAdapter(
            projectedClass = String::class,
            nullableInterfaceId = IID.NullableString,
            referenceArrayInterfaceId = IID.IReferenceArrayOfString,
            propertyType = PropertyType.String,
            propertyTypeArray = PropertyType.StringArray,
            exactUnbox = { it as String },
            propertyValueCoerce = ::coerceString,
            createPointer = { value -> NativeStringMarshaller.fromManaged(value)?.handle ?: PlatformAbi.nullPointer },
            readOwnedPointer = { pointer -> NativeStringMarshaller.fromAbi(pointer) },
            disposeOwnedPointer = { pointer -> NativeStringMarshaller.disposeAbi(pointer) },
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
            readOwnedPointer = ::unboxInspectablePointer,
            disposeOwnedPointer = { pointer -> IUnknownReference(pointer.asRawComPtr(), IID.IInspectable).close() },
        )

    private val classAdapter =
        directValueAdapter<KClass<*>>(
            projectedClass = KClass::class,
            nullableInterfaceId = IID.NullableType,
            referenceArrayInterfaceId = IID.IReferenceArrayOfType,
            propertyType = null,
            propertyTypeArray = null,
            abiLayout = NativeAbiLayout.TYPE_NAME,
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
            abiLayout = NativeAbiLayout.INT32,
            exactUnbox = { it as Exception },
            readOwnedValue = { source -> ExceptionProjection.fromAbi(PlatformAbi.readInt32(source)) },
            writeTransferredValue = { value, destination -> PlatformAbi.writeInt32(destination, ExceptionProjection.toAbi(value)) },
        )

    private val dynamicAdaptersByClass = ConcurrentCacheMap<KClass<*>, WinRtValueAdapter<*>>()
    private val dynamicAdaptersByNullableIid = ConcurrentCacheMap<Guid, WinRtValueAdapter<*>>()
    private val dynamicAdaptersByReferenceArrayIid = ConcurrentCacheMap<Guid, WinRtValueAdapter<*>>()
    private val dynamicAdaptersByPropertyType = ConcurrentCacheMap<PropertyType, WinRtValueAdapter<*>>()
    private val dynamicAdaptersByPropertyTypeArray = ConcurrentCacheMap<PropertyType, WinRtValueAdapter<*>>()

    private val builtInAdapters: List<WinRtValueAdapter<*>> =
        listOf<WinRtValueAdapter<*>>(
            directValueAdapter(
                projectedClass = Byte::class,
                nullableInterfaceId = IID.NullableSByte,
                referenceArrayInterfaceId = IID.IReferenceArrayOfSByte,
                propertyType = null,
                propertyTypeArray = null,
                abiLayout = NativeAbiLayout.INT8,
                exactUnbox = { it as Byte },
                readOwnedValue = { source -> PlatformAbi.readInt8(source) },
                writeTransferredValue = { value, destination -> PlatformAbi.writeInt8(destination, value) },
            ),
            directValueAdapter(
                projectedClass = UByte::class,
                nullableInterfaceId = IID.NullableByte,
                referenceArrayInterfaceId = IID.IReferenceArrayOfByte,
                propertyType = PropertyType.UInt8,
                propertyTypeArray = PropertyType.UInt8Array,
                abiLayout = NativeAbiLayout.INT8,
                isNumericScalar = true,
                exactUnbox = { it as UByte },
                propertyValueCoerce = ::coerceUByte,
                readOwnedValue = { source -> PlatformAbi.readInt8(source).toUByte() },
                writeTransferredValue = { value, destination -> PlatformAbi.writeInt8(destination, value.toByte()) },
            ),
            directValueAdapter(
                projectedClass = Short::class,
                nullableInterfaceId = IID.NullableShort,
                referenceArrayInterfaceId = IID.IReferenceArrayOfInt16,
                propertyType = PropertyType.Int16,
                propertyTypeArray = PropertyType.Int16Array,
                abiLayout = NativeAbiLayout.INT16,
                isNumericScalar = true,
                exactUnbox = { it as Short },
                propertyValueCoerce = ::coerceShort,
                readOwnedValue = { source -> PlatformAbi.readInt16(source) },
                writeTransferredValue = { value, destination -> PlatformAbi.writeInt16(destination, value) },
            ),
            directValueAdapter(
                projectedClass = UShort::class,
                nullableInterfaceId = IID.NullableUShort,
                referenceArrayInterfaceId = IID.IReferenceArrayOfUInt16,
                propertyType = PropertyType.UInt16,
                propertyTypeArray = PropertyType.UInt16Array,
                abiLayout = NativeAbiLayout.INT16,
                isNumericScalar = true,
                exactUnbox = { it as UShort },
                propertyValueCoerce = ::coerceUShort,
                readOwnedValue = { source -> PlatformAbi.readInt16(source).toUShort() },
                writeTransferredValue = { value, destination -> PlatformAbi.writeInt16(destination, value.toShort()) },
            ),
            directValueAdapter(
                projectedClass = Int::class,
                nullableInterfaceId = IID.NullableInt,
                referenceArrayInterfaceId = IID.IReferenceArrayOfInt32,
                propertyType = PropertyType.Int32,
                propertyTypeArray = PropertyType.Int32Array,
                abiLayout = NativeAbiLayout.INT32,
                isNumericScalar = true,
                exactUnbox = { it as Int },
                propertyValueCoerce = ::coerceInt,
                readOwnedValue = { source -> PlatformAbi.readInt32(source) },
                writeTransferredValue = { value, destination -> PlatformAbi.writeInt32(destination, value) },
            ),
            directValueAdapter(
                projectedClass = UInt::class,
                nullableInterfaceId = IID.NullableUInt,
                referenceArrayInterfaceId = IID.IReferenceArrayOfUInt32,
                propertyType = PropertyType.UInt32,
                propertyTypeArray = PropertyType.UInt32Array,
                abiLayout = NativeAbiLayout.INT32,
                isNumericScalar = true,
                exactUnbox = { it as UInt },
                propertyValueCoerce = ::coerceUInt,
                readOwnedValue = { source -> PlatformAbi.readInt32(source).toUInt() },
                writeTransferredValue = { value, destination -> PlatformAbi.writeInt32(destination, value.toInt()) },
            ),
            directValueAdapter(
                projectedClass = Long::class,
                nullableInterfaceId = IID.NullableLong,
                referenceArrayInterfaceId = IID.IReferenceArrayOfInt64,
                propertyType = PropertyType.Int64,
                propertyTypeArray = PropertyType.Int64Array,
                abiLayout = NativeAbiLayout.INT64,
                isNumericScalar = true,
                exactUnbox = { it as Long },
                propertyValueCoerce = ::coerceLong,
                readOwnedValue = { source -> PlatformAbi.readInt64(source) },
                writeTransferredValue = { value, destination -> PlatformAbi.writeInt64(destination, value) },
            ),
            directValueAdapter(
                projectedClass = ULong::class,
                nullableInterfaceId = IID.NullableULong,
                referenceArrayInterfaceId = IID.IReferenceArrayOfUInt64,
                propertyType = PropertyType.UInt64,
                propertyTypeArray = PropertyType.UInt64Array,
                abiLayout = NativeAbiLayout.INT64,
                isNumericScalar = true,
                exactUnbox = { it as ULong },
                propertyValueCoerce = ::coerceULong,
                readOwnedValue = { source -> PlatformAbi.readInt64(source).toULong() },
                writeTransferredValue = { value, destination -> PlatformAbi.writeInt64(destination, value.toLong()) },
            ),
            directValueAdapter(
                projectedClass = Float::class,
                nullableInterfaceId = IID.NullableFloat,
                referenceArrayInterfaceId = IID.IReferenceArrayOfSingle,
                propertyType = PropertyType.Single,
                propertyTypeArray = PropertyType.SingleArray,
                abiLayout = NativeAbiLayout.FLOAT32,
                isNumericScalar = true,
                exactUnbox = { it as Float },
                propertyValueCoerce = ::coerceFloat,
                readOwnedValue = { source -> PlatformAbi.readFloat(source) },
                writeTransferredValue = { value, destination -> PlatformAbi.writeFloat(destination, value) },
            ),
            directValueAdapter(
                projectedClass = Double::class,
                nullableInterfaceId = IID.NullableDouble,
                referenceArrayInterfaceId = IID.IReferenceArrayOfDouble,
                propertyType = PropertyType.Double,
                propertyTypeArray = PropertyType.DoubleArray,
                abiLayout = NativeAbiLayout.FLOAT64,
                isNumericScalar = true,
                exactUnbox = { it as Double },
                propertyValueCoerce = ::coerceDouble,
                readOwnedValue = { source -> PlatformAbi.readDouble(source) },
                writeTransferredValue = { value, destination -> PlatformAbi.writeDouble(destination, value) },
            ),
            directValueAdapter(
                projectedClass = Char::class,
                nullableInterfaceId = IID.NullableChar,
                referenceArrayInterfaceId = IID.IReferenceArrayOfChar,
                propertyType = PropertyType.Char16,
                propertyTypeArray = PropertyType.Char16Array,
                abiLayout = NativeAbiLayout.CHAR16,
                exactUnbox = { it as Char },
                propertyValueCoerce = ::coerceChar,
                readOwnedValue = { source -> PlatformAbi.readChar16(source) },
                writeTransferredValue = { value, destination -> PlatformAbi.writeChar16(destination, value) },
            ),
            directValueAdapter(
                projectedClass = Boolean::class,
                nullableInterfaceId = IID.NullableBool,
                referenceArrayInterfaceId = IID.IReferenceArrayOfBoolean,
                propertyType = PropertyType.Boolean,
                propertyTypeArray = PropertyType.BooleanArray,
                abiLayout = NativeAbiLayout.INT8,
                exactUnbox = { it as Boolean },
                propertyValueCoerce = ::coerceBoolean,
                readOwnedValue = { source -> PlatformAbi.readInt8(source).toInt() != 0 },
                writeTransferredValue = { value, destination -> PlatformAbi.writeInt8(destination, if (value) 1 else 0) },
            ),
            stringAdapter,
            directValueAdapter(
                projectedClass = Guid::class,
                nullableInterfaceId = IID.NullableGuid,
                referenceArrayInterfaceId = IID.IReferenceArrayOfGuid,
                propertyType = PropertyType.Guid,
                propertyTypeArray = PropertyType.GuidArray,
                abiLayout = NativeAbiLayout.GUID,
                exactUnbox = { it as Guid },
                propertyValueCoerce = ::coerceGuid,
                readOwnedValue = GuidMarshaller::readFrom,
                writeTransferredValue = GuidMarshaller::copyTo,
            ),
            directValueAdapter(
                projectedClass = kotlin.time.Instant::class,
                nullableInterfaceId = IID.NullableDateTimeOffset,
                referenceArrayInterfaceId = IID.IReferenceArrayOfDateTimeOffset,
                propertyType = PropertyType.DateTime,
                propertyTypeArray = PropertyType.DateTimeArray,
                abiLayout = NativeAbiLayout.INT64,
                exactUnbox = { it as kotlin.time.Instant },
                readOwnedValue = { source -> DateTimeProjection.fromAbi(PlatformAbi.readInt64(source)) },
                writeTransferredValue = { value, destination -> PlatformAbi.writeInt64(destination, DateTimeProjection.toAbi(value)) },
            ),
            directValueAdapter(
                projectedClass = kotlin.time.Duration::class,
                nullableInterfaceId = IID.NullableTimeSpan,
                referenceArrayInterfaceId = IID.IReferenceArrayOfTimeSpan,
                propertyType = PropertyType.TimeSpan,
                propertyTypeArray = PropertyType.TimeSpanArray,
                abiLayout = NativeAbiLayout.INT64,
                exactUnbox = { it as kotlin.time.Duration },
                readOwnedValue = { source -> TimeSpanProjection.fromAbi(PlatformAbi.readInt64(source)) },
                writeTransferredValue = { value, destination -> PlatformAbi.writeInt64(destination, TimeSpanProjection.toAbi(value)) },
            ),
            classAdapter,
            exceptionAdapter,
            objectAdapter,
        )

    private val builtInAdaptersByNullableIid =
        builtInAdapters.mapNotNull { adapter -> adapter.nullableInterfaceId?.let { it to adapter } }.toMap()
    private val builtInAdaptersByReferenceArrayIid =
        builtInAdapters.mapNotNull { adapter -> adapter.referenceArrayInterfaceId?.let { it to adapter } }.toMap()
    private val builtInAdaptersByPropertyType =
        builtInAdapters.mapNotNull { adapter -> adapter.propertyType?.let { it to adapter } }.toMap()
    private val builtInAdaptersByPropertyTypeArray =
        builtInAdapters.mapNotNull { adapter -> adapter.propertyTypeArray?.let { it to adapter } }.toMap()

    internal fun registerAdapter(adapter: WinRtValueAdapter<*>) {
        dynamicAdaptersByClass[adapter.projectedClass] = adapter
        adapter.nullableInterfaceId?.let { dynamicAdaptersByNullableIid[it] = adapter }
        adapter.referenceArrayInterfaceId?.let { dynamicAdaptersByReferenceArrayIid[it] = adapter }
        adapter.propertyType?.let { dynamicAdaptersByPropertyType[it] = adapter }
        adapter.propertyTypeArray?.let { dynamicAdaptersByPropertyTypeArray[it] = adapter }
    }

    internal fun clearDynamicAdaptersForTests() {
        dynamicAdaptersByClass.clear()
        dynamicAdaptersByNullableIid.clear()
        dynamicAdaptersByReferenceArrayIid.clear()
        dynamicAdaptersByPropertyType.clear()
        dynamicAdaptersByPropertyTypeArray.clear()
    }

    internal fun adapterForReferenceInterface(interfaceId: Guid): WinRtValueAdapter<*>? =
        dynamicAdaptersByNullableIid[interfaceId] ?: builtInAdaptersByNullableIid[interfaceId]

    internal fun adapterForReferenceArrayInterface(interfaceId: Guid): WinRtValueAdapter<*>? =
        dynamicAdaptersByReferenceArrayIid[interfaceId] ?: builtInAdaptersByReferenceArrayIid[interfaceId]

    internal fun adapterForPropertyType(propertyType: PropertyType): WinRtValueAdapter<*>? =
        dynamicAdaptersByPropertyType[propertyType] ?: builtInAdaptersByPropertyType[propertyType]

    internal fun adapterForPropertyTypeArray(propertyType: PropertyType): WinRtValueAdapter<*>? =
        dynamicAdaptersByPropertyTypeArray[propertyType] ?: builtInAdaptersByPropertyTypeArray[propertyType]

    internal fun inspectableArrayAdapter(): WinRtValueAdapter<Any> = objectAdapter

    internal fun writePropertyValue(expectedType: PropertyType, value: Any, destination: RawAddress) {
        val enumDescriptor = ValueBoxingMetadata.enumMetadataForClass(value::class)
        if (enumDescriptor != null && enumDescriptor.propertyType == expectedType) {
            PlatformAbi.writeInt32(destination, enumDescriptor.toAbiBits(value))
            return
        }
        val adapter = adapterForPropertyType(expectedType)
            ?: throw WinRtInvalidCastException("Unsupported property value getter: $expectedType", HResult(TYPE_E_TYPEMISMATCH))
        adapter.writeCoercedPropertyValue(value, destination)
    }

    fun writePropertyValueArray(
        expectedType: PropertyType,
        value: Any,
        countOut: RawAddress,
        dataOut: RawAddress,
    ) {
        val boxedElements = ValueBoxingMetadata.normalizedManagedArrayElements(value)
            ?: throw WinRtInvalidCastException("Value is not an array for $expectedType", HResult(TYPE_E_TYPEMISMATCH))
        val adapter =
            when (expectedType) {
                PropertyType.InspectableArray -> objectAdapter
                else -> adapterForPropertyTypeArray(expectedType)
            } ?: throw WinRtInvalidCastException("Unsupported property value array getter: $expectedType", HResult(TYPE_E_TYPEMISMATCH))
        val coerced = boxedElements.map { element ->
            if (element == null) {
                null
            } else {
                @Suppress("UNCHECKED_CAST")
                (adapter as WinRtValueAdapter<Any>).coercePropertyValue(element)
            }
        }.toTypedArray()
        val (length, data) = adapter.createTransferredArray(coerced)
        PlatformAbi.writeInt32(countOut, length)
        PlatformAbi.writePointer(dataOut, data)
    }

    fun readReferenceValue(interfaceId: Guid, pointer: RawAddress): Any? =
        WinRtReferenceReference(pointer, interfaceId).use { readReferenceValue(interfaceId, it) }

    fun readReferenceArrayValue(interfaceId: Guid, pointer: RawAddress): Array<Any?>? =
        WinRtReferenceArrayReference(pointer, interfaceId).use { readReferenceArrayValue(interfaceId, it) }

    internal fun readReferenceValue(interfaceId: Guid, reference: WinRtReferenceReference): Any? {
        val adapter = adapterForReferenceInterface(interfaceId)
            ?: throw WinRtInvalidCastException("Unsupported IReference interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH))
        return reference.readValue(
            sizeBytes = adapter.abiLayout.byteSize,
            alignmentBytes = adapter.abiLayout.byteAlignment,
            readValue = adapter::readValue,
            disposeValue = adapter::disposeValue,
        )
    }

    internal fun readReferenceArrayValue(interfaceId: Guid, reference: WinRtReferenceArrayReference): Array<Any?>? {
        val adapter = adapterForReferenceArrayInterface(interfaceId)
            ?: throw WinRtInvalidCastException("Unsupported IReferenceArray interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH))
        return reference.readValue(
            readArray = adapter::readOwnedArray,
            disposeArray = adapter::disposeOwnedArray,
        )
    }

    fun createReferenceInterfaceDefinition(interfaceId: Guid, value: Any): WinRtInspectableInterfaceDefinition {
        val adapter = adapterForReferenceInterface(interfaceId)
        val enumDescriptor = ValueBoxingMetadata.enumMetadataForClass(value::class)
        if (adapter == null && (enumDescriptor == null || enumDescriptor.nullableInterfaceId != interfaceId)) {
            throw WinRtInvalidCastException("Unsupported IReference interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH))
        }
        return WinRtInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = listOf(
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                ) { rawArgs ->
                    val destination = rawArgs.singleOrNull() as? RawAddress
                        ?: throw IllegalStateException("IReference host requires one out-argument.")
                    if (adapter != null) {
                        adapter.writeValue(value, destination)
                    } else {
                        PlatformAbi.writeInt32(destination, requireNotNull(enumDescriptor).toAbiBits(value))
                    }
                    KnownHResults.S_OK.value
                },
            ),
        )
    }

    fun createReferenceArrayInterfaceDefinition(interfaceId: Guid, value: Any): WinRtInspectableInterfaceDefinition {
        val adapter = adapterForReferenceArrayInterface(interfaceId)
            ?: throw WinRtInvalidCastException("Unsupported IReferenceArray interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH))
        val boxedElements = ValueBoxingMetadata.normalizedManagedArrayElements(value)
            ?: throw WinRtInvalidCastException("IReferenceArray host requires an array value.", HResult(TYPE_E_TYPEMISMATCH))
        return WinRtInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = listOf(
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { rawArgs ->
                    if (rawArgs.size != 2) {
                        throw IllegalStateException("IReferenceArray host requires count and data out-arguments.")
                    }
                    val (length, data) = adapter.createTransferredArray(boxedElements)
                    PlatformAbi.writeInt32(rawArgs[0] as RawAddress, length)
                    PlatformAbi.writePointer(rawArgs[1] as RawAddress, data)
                    KnownHResults.S_OK.value
                },
            ),
        )
    }

    fun referenceTypeHandle(value: Any, interfaceId: Guid): WinRtTypeHandle =
        WinRtTypeHandle(value::class.typeDisplayName(), interfaceId)

    fun createPropertyValueReference(value: Any): ComObjectReference =
        createPropertyValueHost(value).createPrimaryReference()

    fun readPropertyValue(pointer: RawAddress, propertyType: PropertyType): Any? {
        val scalarAdapter = adapterForPropertyType(propertyType)
        if (scalarAdapter != null) {
            return PlatformAbi.confinedScope().use { scope ->
                val resultOut = PlatformAbi.allocateBytes(scope, scalarAdapter.abiLayout.byteSize, scalarAdapter.abiLayout.byteAlignment)
                val slot = 8 + (propertyType.code - PropertyType.UInt8.code)
                val propertyValue = IUnknownReference(pointer.asRawComPtr(), IID.IPropertyValue, preventReleaseOnDispose = true)
                val hr =
                    propertyValue.use {
                        it.comPtr.throwIfDisposed()
                        ComVtableInvoker.invokeArgs(it.comPtr.raw, slot, resultOut)
                    }
                WinRtPlatformApi.checkSucceededRaw(hr)
                try {
                    scalarAdapter.readValue(resultOut)
                } finally {
                    scalarAdapter.disposeValue(resultOut)
                }
            }
        }
        val arrayAdapter = when (propertyType) {
            PropertyType.InspectableArray -> inspectableArrayAdapter()
            else -> adapterForPropertyTypeArray(propertyType)
        }
        if (arrayAdapter != null) {
            return PlatformAbi.confinedScope().use { scope ->
                val countOut = PlatformAbi.allocateInt32Slot(scope)
                val dataOut = PlatformAbi.allocatePointerSlot(scope)
                val slot = 26 + (propertyType.code - PropertyType.UInt8Array.code)
                val propertyValue = IUnknownReference(pointer.asRawComPtr(), IID.IPropertyValue, preventReleaseOnDispose = true)
                val hr =
                    propertyValue.use {
                        it.comPtr.throwIfDisposed()
                        ComVtableInvoker.invokeArgs(it.comPtr.raw, slot, countOut, dataOut)
                    }
                WinRtPlatformApi.checkSucceededRaw(hr)
                val length = PlatformAbi.readInt32(countOut)
                val data = PlatformAbi.readPointer(dataOut)
                try {
                    arrayAdapter.readOwnedArray(length, data)
                } finally {
                    arrayAdapter.disposeOwnedArray(length, data)
                }
            }
        }
        return null
    }

    fun readOwnedPropertyValue(pointer: RawAddress): Any? =
        WinRtPropertyValueReference(pointer).use { it.getValue() }

    fun tryProjectBorrowedPropertyValue(pointer: RawAddress): Any? {
        val propertyValue = runCatching {
            IUnknownReference(pointer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true)
                .queryInterface(IID.IPropertyValue)
                .getOrThrow()
        }.getOrNull() ?: return null
        return propertyValue.use { reference ->
            WinRtPropertyValueReference(reference.pointer.asRawAddress(), preventReleaseOnDispose = true).getValue()
        }
    }
}
