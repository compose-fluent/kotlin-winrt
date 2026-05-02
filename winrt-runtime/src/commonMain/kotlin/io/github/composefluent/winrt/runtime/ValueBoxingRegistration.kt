package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

private const val IREFERENCE_GENERIC_INTERFACE = "61C17706-2D65-11E0-9AE8-D48564015472"
private const val IREFERENCE_ARRAY_GENERIC_INTERFACE = "61C17707-2D65-11E0-9AE8-D48564015472"

/**
 * Registration entry used by generated WinRT structs to provide `IReference<T>`,
 * `IReferenceArray<T>`, and `IPropertyValue` support without making those structs
 * built-in runtime public model types.
 */
object WinRtValueBoxingRegistration {
    fun <T : Any> registerStruct(
        type: KClass<T>,
        projectedTypeName: String,
        signature: String,
        adapter: NativeStructAdapter<T>,
        arrayType: KClass<*>? = null,
    ) {
        val known = knownStructBoxingDescriptors[projectedTypeName]
        val nullableInterfaceId =
            known?.nullableInterfaceId ?: ParameterizedInterfaceId.createFromSignature(
                "pinterface({${IREFERENCE_GENERIC_INTERFACE.lowercase()}};$signature)",
            )
        val referenceArrayInterfaceId =
            known?.referenceArrayInterfaceId ?: ParameterizedInterfaceId.createFromSignature(
                "pinterface({${IREFERENCE_ARRAY_GENERIC_INTERFACE.lowercase()}};$signature)",
            )
        val metadata = WinRtValueTypeMetadata(
            projectedClass = type,
            nullableInterfaceId = nullableInterfaceId,
            referenceArrayInterfaceId = referenceArrayInterfaceId,
            propertyType = known?.propertyType,
            propertyTypeArray = known?.propertyTypeArray,
        )
        ValueBoxingMetadata.registerDescriptor(metadata)
        ValueBoxingInterop.registerAdapter(
            directValueAdapter(
                projectedClass = type,
                nullableInterfaceId = nullableInterfaceId,
                referenceArrayInterfaceId = referenceArrayInterfaceId,
                propertyType = known?.propertyType,
                propertyTypeArray = known?.propertyTypeArray,
                abiLayout = adapter.layout.abiLayout,
                exactUnbox = { value ->
                    if (!type.isInstance(value)) {
                        throw WinRtInvalidCastException(
                            "Expected projected value assignable to ${type.typeDisplayName()}.",
                            HResult(TYPE_E_TYPEMISMATCH),
                        )
                    }
                    @Suppress("UNCHECKED_CAST")
                    value as T
                },
                readOwnedValue = adapter::read,
                writeTransferredValue = adapter::write,
            ),
        )
        Projections.registerCustomAbiTypeMapping(
            publicType = type,
            helperType = type,
            abiTypeName = projectedTypeName,
        )
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = type,
            projectedTypeName = projectedTypeName,
            helperType = type,
            signature = signature,
            isWindowsRuntimeType = true,
        )
        arrayType?.let { TypeNameSupport.registerReferenceArrayType(type, it) }
    }
}

private data class KnownStructBoxingDescriptor(
    val nullableInterfaceId: Guid,
    val referenceArrayInterfaceId: Guid,
    val propertyType: PropertyType?,
    val propertyTypeArray: PropertyType?,
)

private val knownStructBoxingDescriptors: Map<String, KnownStructBoxingDescriptor> =
    mapOf(
        "Windows.Foundation.Point" to KnownStructBoxingDescriptor(IID.IReferenceOfPoint, IID.IReferenceArrayOfPoint, PropertyType.Point, PropertyType.PointArray),
        "Windows.Foundation.Size" to KnownStructBoxingDescriptor(IID.IReferenceOfSize, IID.IReferenceArrayOfSize, PropertyType.Size, PropertyType.SizeArray),
        "Windows.Foundation.Rect" to KnownStructBoxingDescriptor(IID.IReferenceOfRect, IID.IReferenceArrayOfRect, PropertyType.Rect, PropertyType.RectArray),
        "Windows.Foundation.Numerics.Matrix3x2" to KnownStructBoxingDescriptor(IID.IReferenceMatrix3x2, IID.IReferenceArrayOfMatrix3x2, null, null),
        "Windows.Foundation.Numerics.Matrix4x4" to KnownStructBoxingDescriptor(IID.IReferenceMatrix4x4, IID.IReferenceArrayOfMatrix4x4, null, null),
        "Windows.Foundation.Numerics.Plane" to KnownStructBoxingDescriptor(IID.IReferencePlane, IID.IReferenceArrayOfPlane, null, null),
        "Windows.Foundation.Numerics.Quaternion" to KnownStructBoxingDescriptor(IID.IReferenceQuaternion, IID.IReferenceArrayOfQuaternion, null, null),
        "Windows.Foundation.Numerics.Vector2" to KnownStructBoxingDescriptor(IID.IReferenceVector2, IID.IReferenceArrayOfVector2, null, null),
        "Windows.Foundation.Numerics.Vector3" to KnownStructBoxingDescriptor(IID.IReferenceVector3, IID.IReferenceArrayOfVector3, null, null),
        "Windows.Foundation.Numerics.Vector4" to KnownStructBoxingDescriptor(IID.IReferenceVector4, IID.IReferenceArrayOfVector4, null, null),
    )
