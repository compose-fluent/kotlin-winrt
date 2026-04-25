package io.github.kitectlab.winrt.metadata

enum class WinRtProjectionCategory {
    Unit,
    Fundamental,
    String,
    Object,
    Guid,
    Type,
    Enum,
    Struct,
    Interface,
    Delegate,
    RuntimeClass,
    Attribute,
    ApiContract,
    GenericTypeParameter,
    MethodTypeParameter,
    Array,
    Unknown,
}

data class WinRtMappedTypeDescriptor(
    val abiNamespace: String,
    val abiName: String,
    val mappedNamespace: String? = null,
    val mappedName: String? = null,
    val requiresMarshaling: Boolean = false,
    val hasCustomMembersOutput: Boolean = false,
) {
    val abiQualifiedName: String
        get() = "$abiNamespace.$abiName"

    val mappedQualifiedName: String?
        get() = mappedNamespace?.let { namespace -> "$namespace.$mappedName" }

    val isXamlAlias: Boolean
        get() = abiNamespace.startsWith("Windows.UI.Xaml") || abiNamespace.startsWith("Microsoft.UI.Xaml")
}

data class WinRtTypeClassificationDescriptor(
    val typeName: String,
    val type: WinRtTypeRef,
    val definitionQualifiedName: String? = null,
    val definitionType: WinRtTypeDefinition? = null,
    val projectionCategory: WinRtProjectionCategory,
    val abiCategory: WinRtAbiTypeCategory,
    val mappedType: WinRtMappedTypeDescriptor? = null,
    val specialType: WinRtSpecialTypeDescriptor? = null,
    val isProjectionInternal: Boolean = definitionType?.isProjectionInternal ?: false,
    val isApiContract: Boolean = definitionType?.isApiContract ?: false,
    val isAttributeType: Boolean = definitionType?.isAttributeType ?: false,
    val isMappedType: Boolean = mappedType != null,
    val requiresMarshaling: Boolean = mappedType?.requiresMarshaling ?: false,
    val hasCustomMembersOutput: Boolean = mappedType?.hasCustomMembersOutput ?: false,
    val isXamlAlias: Boolean = mappedType?.isXamlAlias ?: false,
    val isBlittable: Boolean = definitionType?.isBlittable ?: false,
)

class WinRtMetadataTypeClassifier private constructor(
    private val typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    private val specialTypeResolver: WinRtMetadataSpecialTypeResolver,
) {
    fun mappedTypesInNamespace(abiNamespace: String): List<WinRtMappedTypeDescriptor> =
        MAPPED_TYPES_BY_NAMESPACE[abiNamespace].orEmpty()

    fun mappedType(
        abiNamespace: String,
        abiName: String,
    ): WinRtMappedTypeDescriptor? = MAPPED_TYPES["$abiNamespace.$abiName"]

    fun classify(type: WinRtTypeDefinition): WinRtTypeClassificationDescriptor =
        classify(WinRtTypeRef.named(type.qualifiedName), type.namespace)

    fun classify(
        type: WinRtTypeRef,
        currentNamespace: String,
    ): WinRtTypeClassificationDescriptor {
        val normalizedType = type.normalized()
        if (normalizedType.kind == WinRtTypeRefKind.Array) {
            return WinRtTypeClassificationDescriptor(
                typeName = normalizedType.typeName,
                type = normalizedType,
                projectionCategory = WinRtProjectionCategory.Array,
                abiCategory = WinRtAbiTypeCategory.Array,
            )
        }
        if (normalizedType.kind == WinRtTypeRefKind.GenericTypeParameter) {
            return WinRtTypeClassificationDescriptor(
                typeName = normalizedType.typeName,
                type = normalizedType,
                projectionCategory = WinRtProjectionCategory.GenericTypeParameter,
                abiCategory = WinRtAbiTypeCategory.GenericTypeParameter,
            )
        }
        if (normalizedType.kind == WinRtTypeRefKind.MethodTypeParameter) {
            return WinRtTypeClassificationDescriptor(
                typeName = normalizedType.typeName,
                type = normalizedType,
                projectionCategory = WinRtProjectionCategory.MethodTypeParameter,
                abiCategory = WinRtAbiTypeCategory.MethodTypeParameter,
            )
        }
        if (normalizedType.kind == WinRtTypeRefKind.Unknown) {
            return WinRtTypeClassificationDescriptor(
                typeName = normalizedType.typeName,
                type = normalizedType,
                projectionCategory = WinRtProjectionCategory.Unknown,
                abiCategory = WinRtAbiTypeCategory.Unknown,
            )
        }

        val resolvedType = resolveTypeReference(normalizedType, currentNamespace, typesByQualifiedName)
        val rawTypeName = resolvedType.definitionQualifiedName ?: normalizedType.qualifiedName ?: normalizedType.typeName
        val mappedType = MAPPED_TYPES[rawTypeName]
        val specialType = specialTypeResolver.resolveType(normalizedType, currentNamespace)
        val projectionCategory = projectionCategoryFor(rawTypeName, resolvedType.definitionType)
        return WinRtTypeClassificationDescriptor(
            typeName = resolvedType.displayName,
            type = resolvedType.type,
            definitionQualifiedName = resolvedType.definitionQualifiedName,
            definitionType = resolvedType.definitionType,
            projectionCategory = projectionCategory,
            abiCategory = projectionCategory.toAbiCategory(),
            mappedType = mappedType,
            specialType = specialType,
        )
    }

    companion object {
        fun create(model: WinRtMetadataModel): WinRtMetadataTypeClassifier {
            val typesByQualifiedName = buildTypesByQualifiedName(model)
            return WinRtMetadataTypeClassifier(
                typesByQualifiedName = typesByQualifiedName,
                specialTypeResolver = model.specialTypeResolver(),
            )
        }
    }
}

fun WinRtMetadataModel.typeClassifier(): WinRtMetadataTypeClassifier =
    WinRtMetadataTypeClassifier.create(this)

internal fun WinRtProjectionCategory.toAbiCategory(): WinRtAbiTypeCategory =
    when (this) {
        WinRtProjectionCategory.Unit -> WinRtAbiTypeCategory.Unit
        WinRtProjectionCategory.Fundamental -> WinRtAbiTypeCategory.Fundamental
        WinRtProjectionCategory.String -> WinRtAbiTypeCategory.String
        WinRtProjectionCategory.Object -> WinRtAbiTypeCategory.Object
        WinRtProjectionCategory.Guid -> WinRtAbiTypeCategory.Guid
        WinRtProjectionCategory.Type -> WinRtAbiTypeCategory.Type
        WinRtProjectionCategory.Enum -> WinRtAbiTypeCategory.Enum
        WinRtProjectionCategory.Struct -> WinRtAbiTypeCategory.Struct
        WinRtProjectionCategory.Interface -> WinRtAbiTypeCategory.Interface
        WinRtProjectionCategory.Delegate -> WinRtAbiTypeCategory.Delegate
        WinRtProjectionCategory.RuntimeClass -> WinRtAbiTypeCategory.RuntimeClass
        WinRtProjectionCategory.Attribute -> WinRtAbiTypeCategory.RuntimeClass
        WinRtProjectionCategory.ApiContract -> WinRtAbiTypeCategory.Struct
        WinRtProjectionCategory.GenericTypeParameter -> WinRtAbiTypeCategory.GenericTypeParameter
        WinRtProjectionCategory.MethodTypeParameter -> WinRtAbiTypeCategory.MethodTypeParameter
        WinRtProjectionCategory.Array -> WinRtAbiTypeCategory.Array
        WinRtProjectionCategory.Unknown -> WinRtAbiTypeCategory.Unknown
    }

private fun projectionCategoryFor(
    rawTypeName: String,
    resolvedType: WinRtTypeDefinition?,
): WinRtProjectionCategory =
    when {
        rawTypeName == "Unit" -> WinRtProjectionCategory.Unit
        rawTypeName in FUNDAMENTAL_TYPE_NAMES -> WinRtProjectionCategory.Fundamental
        rawTypeName == "String" -> WinRtProjectionCategory.String
        rawTypeName == "Any" || rawTypeName == "System.Object" -> WinRtProjectionCategory.Object
        rawTypeName == "Guid" || rawTypeName == "System.Guid" -> WinRtProjectionCategory.Guid
        rawTypeName == "Type" || rawTypeName == "System.Type" -> WinRtProjectionCategory.Type
        resolvedType?.isApiContract == true && resolvedType.kind == WinRtTypeKind.Struct -> WinRtProjectionCategory.ApiContract
        resolvedType?.isAttributeType == true && resolvedType.kind == WinRtTypeKind.RuntimeClass -> WinRtProjectionCategory.Attribute
        resolvedType != null -> when (resolvedType.kind) {
            WinRtTypeKind.Enum -> WinRtProjectionCategory.Enum
            WinRtTypeKind.Struct -> WinRtProjectionCategory.Struct
            WinRtTypeKind.Interface -> WinRtProjectionCategory.Interface
            WinRtTypeKind.Delegate -> WinRtProjectionCategory.Delegate
            WinRtTypeKind.RuntimeClass -> WinRtProjectionCategory.RuntimeClass
            WinRtTypeKind.Unknown -> WinRtProjectionCategory.Unknown
        }

        else -> WinRtProjectionCategory.Unknown
    }

private fun mapped(
    abiQualifiedName: String,
    mappedNamespace: String? = null,
    mappedName: String? = null,
    requiresMarshaling: Boolean = false,
    hasCustomMembersOutput: Boolean = false,
): WinRtMappedTypeDescriptor {
    val abiNamespace = abiQualifiedName.substringBeforeLast('.', "")
    val abiName = abiQualifiedName.substringAfterLast('.')
    return WinRtMappedTypeDescriptor(
        abiNamespace = abiNamespace,
        abiName = abiName,
        mappedNamespace = mappedNamespace,
        mappedName = mappedName,
        requiresMarshaling = requiresMarshaling,
        hasCustomMembersOutput = hasCustomMembersOutput,
    )
}

private val FUNDAMENTAL_TYPE_NAMES = setOf(
    "Boolean",
    "Char",
    "Byte",
    "UByte",
    "Short",
    "UShort",
    "Int",
    "UInt",
    "Long",
    "ULong",
    "Float",
    "Double",
)

private val MAPPED_TYPES: Map<String, WinRtMappedTypeDescriptor> = listOf(
    mapped("Microsoft.UI.Xaml.CornerRadius", "Microsoft.UI.Xaml", "CornerRadius"),
    mapped("Microsoft.UI.Xaml.CornerRadiusHelper"),
    mapped("Microsoft.UI.Xaml.Duration", "Microsoft.UI.Xaml", "Duration"),
    mapped("Microsoft.UI.Xaml.DurationHelper"),
    mapped("Microsoft.UI.Xaml.DurationType", "Microsoft.UI.Xaml", "DurationType"),
    mapped("Microsoft.UI.Xaml.GridLength", "Microsoft.UI.Xaml", "GridLength"),
    mapped("Microsoft.UI.Xaml.GridLengthHelper"),
    mapped("Microsoft.UI.Xaml.GridUnitType", "Microsoft.UI.Xaml", "GridUnitType"),
    mapped("Microsoft.UI.Xaml.ICornerRadiusHelper"),
    mapped("Microsoft.UI.Xaml.ICornerRadiusHelperStatics"),
    mapped("Microsoft.UI.Xaml.IDurationHelper"),
    mapped("Microsoft.UI.Xaml.IDurationHelperStatics"),
    mapped("Microsoft.UI.Xaml.IGridLengthHelper"),
    mapped("Microsoft.UI.Xaml.IGridLengthHelperStatics"),
    mapped("Microsoft.UI.Xaml.IThicknessHelper"),
    mapped("Microsoft.UI.Xaml.IThicknessHelperStatics"),
    mapped("Microsoft.UI.Xaml.Thickness", "Microsoft.UI.Xaml", "Thickness"),
    mapped("Microsoft.UI.Xaml.ThicknessHelper"),
    mapped("Microsoft.UI.Xaml.Controls.Primitives.GeneratorPosition", "Microsoft.UI.Xaml.Controls.Primitives", "GeneratorPosition"),
    mapped("Microsoft.UI.Xaml.Controls.Primitives.GeneratorPositionHelper"),
    mapped("Microsoft.UI.Xaml.Controls.Primitives.IGeneratorPositionHelper"),
    mapped("Microsoft.UI.Xaml.Controls.Primitives.IGeneratorPositionHelperStatics"),
    mapped("Microsoft.UI.Xaml.Data.DataErrorsChangedEventArgs", "System.ComponentModel", "DataErrorsChangedEventArgs"),
    mapped("Microsoft.UI.Xaml.Media.IMatrixHelper"),
    mapped("Microsoft.UI.Xaml.Media.IMatrixHelperStatics"),
    mapped("Microsoft.UI.Xaml.Media.Matrix", "Microsoft.UI.Xaml.Media", "Matrix"),
    mapped("Microsoft.UI.Xaml.Media.MatrixHelper"),
    mapped("Microsoft.UI.Xaml.Media.Animation.IKeyTimeHelper"),
    mapped("Microsoft.UI.Xaml.Media.Animation.IKeyTimeHelperStatics"),
    mapped("Microsoft.UI.Xaml.Media.Animation.IRepeatBehaviorHelper"),
    mapped("Microsoft.UI.Xaml.Media.Animation.IRepeatBehaviorHelperStatics"),
    mapped("Microsoft.UI.Xaml.Media.Animation.KeyTime", "Microsoft.UI.Xaml.Media.Animation", "KeyTime"),
    mapped("Microsoft.UI.Xaml.Media.Animation.KeyTimeHelper"),
    mapped("Microsoft.UI.Xaml.Media.Animation.RepeatBehavior", "Microsoft.UI.Xaml.Media.Animation", "RepeatBehavior"),
    mapped("Microsoft.UI.Xaml.Media.Animation.RepeatBehaviorHelper"),
    mapped("Microsoft.UI.Xaml.Media.Animation.RepeatBehaviorType", "Microsoft.UI.Xaml.Media.Animation", "RepeatBehaviorType"),
    mapped("Microsoft.UI.Xaml.Media.Media3D.IMatrix3DHelper"),
    mapped("Microsoft.UI.Xaml.Media.Media3D.IMatrix3DHelperStatics"),
    mapped("Microsoft.UI.Xaml.Media.Media3D.Matrix3D", "Microsoft.UI.Xaml.Media.Media3D", "Matrix3D"),
    mapped("Microsoft.UI.Xaml.Media.Media3D.Matrix3DHelper"),
    mapped("WinRT.Interop.HWND", "System", "IntPtr"),
    mapped("WinRT.Interop.ProjectionInternalAttribute"),
    mapped("Windows.Foundation.DateTime", "System", "DateTimeOffset", requiresMarshaling = true),
    mapped("Windows.Foundation.EventHandler", "System", "EventHandler"),
    mapped("Windows.Foundation.EventRegistrationToken", "WinRT", "EventRegistrationToken"),
    mapped("Windows.Foundation.HResult", "System", "Exception", requiresMarshaling = true),
    mapped("Windows.Foundation.IClosable", "System", "IDisposable", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Windows.Foundation.IPropertyValue", "Windows.Foundation", "IPropertyValue", requiresMarshaling = true),
    mapped("Windows.Foundation.IReference", "System", "Nullable", requiresMarshaling = true),
    mapped("Windows.Foundation.IReferenceArray", "Windows.Foundation", "IReferenceArray", requiresMarshaling = true),
    mapped("Windows.Foundation.Point", "Windows.Foundation", "Point"),
    mapped("Windows.Foundation.Rect", "Windows.Foundation", "Rect"),
    mapped("Windows.Foundation.Size", "Windows.Foundation", "Size"),
    mapped("Windows.Foundation.TimeSpan", "System", "TimeSpan", requiresMarshaling = true),
    mapped("Windows.Foundation.Uri", "System", "Uri", requiresMarshaling = true),
    mapped("Windows.Foundation.Collections.IIterable", "System.Collections.Generic", "IEnumerable`1", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Windows.Foundation.Collections.IIterator", "System.Collections.Generic", "IEnumerator`1", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Windows.Foundation.Collections.IKeyValuePair", "System.Collections.Generic", "KeyValuePair`2", requiresMarshaling = true),
    mapped("Windows.Foundation.Collections.IMap", "System.Collections.Generic", "IDictionary`2", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Windows.Foundation.Collections.IMapView", "System.Collections.Generic", "IReadOnlyDictionary`2", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Windows.Foundation.Collections.IVector", "System.Collections.Generic", "IList`1", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Windows.Foundation.Collections.IVectorView", "System.Collections.Generic", "IReadOnlyList`1", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Windows.Foundation.Metadata.AttributeTargets", "System", "AttributeTargets"),
    mapped("Windows.Foundation.Metadata.AttributeUsageAttribute", "System", "AttributeUsageAttribute"),
    mapped("Windows.Foundation.Numerics.Matrix3x2", "System.Numerics", "Matrix3x2"),
    mapped("Windows.Foundation.Numerics.Matrix4x4", "System.Numerics", "Matrix4x4"),
    mapped("Windows.Foundation.Numerics.Plane", "System.Numerics", "Plane"),
    mapped("Windows.Foundation.Numerics.Quaternion", "System.Numerics", "Quaternion"),
    mapped("Windows.Foundation.Numerics.Vector2", "System.Numerics", "Vector2"),
    mapped("Windows.Foundation.Numerics.Vector3", "System.Numerics", "Vector3"),
    mapped("Windows.Foundation.Numerics.Vector4", "System.Numerics", "Vector4"),
    mapped("Windows.UI.Color", "Windows.UI", "Color"),
    mapped("Windows.UI.Xaml.CornerRadius", "Windows.UI.Xaml", "CornerRadius"),
    mapped("Windows.UI.Xaml.CornerRadiusHelper"),
    mapped("Windows.UI.Xaml.Duration", "Windows.UI.Xaml", "Duration"),
    mapped("Windows.UI.Xaml.DurationHelper"),
    mapped("Windows.UI.Xaml.DurationType", "Windows.UI.Xaml", "DurationType"),
    mapped("Windows.UI.Xaml.GridLength", "Windows.UI.Xaml", "GridLength"),
    mapped("Windows.UI.Xaml.GridLengthHelper"),
    mapped("Windows.UI.Xaml.GridUnitType", "Windows.UI.Xaml", "GridUnitType"),
    mapped("Windows.UI.Xaml.ICornerRadiusHelper"),
    mapped("Windows.UI.Xaml.ICornerRadiusHelperStatics"),
    mapped("Windows.UI.Xaml.IDurationHelper"),
    mapped("Windows.UI.Xaml.IDurationHelperStatics"),
    mapped("Windows.UI.Xaml.IGridLengthHelper"),
    mapped("Windows.UI.Xaml.IGridLengthHelperStatics"),
    mapped("Windows.UI.Xaml.IThicknessHelper"),
    mapped("Windows.UI.Xaml.IThicknessHelperStatics"),
    mapped("Windows.UI.Xaml.Thickness", "Windows.UI.Xaml", "Thickness"),
    mapped("Windows.UI.Xaml.ThicknessHelper"),
    mapped("Microsoft.UI.Xaml.IXamlServiceProvider", "System", "IServiceProvider"),
    mapped("Windows.UI.Xaml.IXamlServiceProvider", "System", "IServiceProvider"),
    mapped("Windows.UI.Xaml.Controls.Primitives.GeneratorPosition", "Windows.UI.Xaml.Controls.Primitives", "GeneratorPosition"),
    mapped("Windows.UI.Xaml.Controls.Primitives.GeneratorPositionHelper"),
    mapped("Windows.UI.Xaml.Controls.Primitives.IGeneratorPositionHelper"),
    mapped("Windows.UI.Xaml.Controls.Primitives.IGeneratorPositionHelperStatics"),
    mapped("Windows.UI.Xaml.Data.DataErrorsChangedEventArgs", "System.ComponentModel", "DataErrorsChangedEventArgs"),
    mapped("Microsoft.UI.Xaml.Data.INotifyDataErrorInfo", "System.ComponentModel", "INotifyDataErrorInfo", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Windows.UI.Xaml.Data.INotifyDataErrorInfo", "System.ComponentModel", "INotifyDataErrorInfo", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Microsoft.UI.Xaml.Data.INotifyPropertyChanged", "System.ComponentModel", "INotifyPropertyChanged"),
    mapped("Windows.UI.Xaml.Data.INotifyPropertyChanged", "System.ComponentModel", "INotifyPropertyChanged"),
    mapped("Microsoft.UI.Xaml.Data.PropertyChangedEventArgs", "System.ComponentModel", "PropertyChangedEventArgs"),
    mapped("Windows.UI.Xaml.Data.PropertyChangedEventArgs", "System.ComponentModel", "PropertyChangedEventArgs"),
    mapped("Microsoft.UI.Xaml.Data.PropertyChangedEventHandler", "System.ComponentModel", "PropertyChangedEventHandler"),
    mapped("Windows.UI.Xaml.Data.PropertyChangedEventHandler", "System.ComponentModel", "PropertyChangedEventHandler"),
    mapped("Microsoft.UI.Xaml.Input.ICommand", "System.Windows.Input", "ICommand", requiresMarshaling = true),
    mapped("Windows.UI.Xaml.Input.ICommand", "System.Windows.Input", "ICommand", requiresMarshaling = true),
    mapped("Microsoft.UI.Xaml.Interop.IBindableIterable", "System.Collections", "IEnumerable", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Windows.UI.Xaml.Interop.IBindableIterable", "System.Collections", "IEnumerable", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Microsoft.UI.Xaml.Interop.IBindableVector", "System.Collections", "IList", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Windows.UI.Xaml.Interop.IBindableVector", "System.Collections", "IList", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Microsoft.UI.Xaml.Interop.INotifyCollectionChanged", "System.Collections.Specialized", "INotifyCollectionChanged", requiresMarshaling = true),
    mapped("Windows.UI.Xaml.Interop.INotifyCollectionChanged", "System.Collections.Specialized", "INotifyCollectionChanged", requiresMarshaling = true),
    mapped("Microsoft.UI.Xaml.Interop.NotifyCollectionChangedAction", "System.Collections.Specialized", "NotifyCollectionChangedAction"),
    mapped("Windows.UI.Xaml.Interop.NotifyCollectionChangedAction", "System.Collections.Specialized", "NotifyCollectionChangedAction"),
    mapped("Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventArgs", "System.Collections.Specialized", "NotifyCollectionChangedEventArgs", requiresMarshaling = true),
    mapped("Windows.UI.Xaml.Interop.NotifyCollectionChangedEventArgs", "System.Collections.Specialized", "NotifyCollectionChangedEventArgs", requiresMarshaling = true),
    mapped("Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventHandler", "System.Collections.Specialized", "NotifyCollectionChangedEventHandler", requiresMarshaling = true),
    mapped("Windows.UI.Xaml.Interop.NotifyCollectionChangedEventHandler", "System.Collections.Specialized", "NotifyCollectionChangedEventHandler", requiresMarshaling = true),
    mapped("Windows.UI.Xaml.Interop.TypeKind", "Windows.UI.Xaml.Interop", "TypeKind", requiresMarshaling = true),
    mapped("Windows.UI.Xaml.Interop.TypeName", "System", "Type", requiresMarshaling = true),
    mapped("Windows.UI.Xaml.Media.IMatrixHelper"),
    mapped("Windows.UI.Xaml.Media.IMatrixHelperStatics"),
    mapped("Windows.UI.Xaml.Media.Matrix", "Windows.UI.Xaml.Media", "Matrix"),
    mapped("Windows.UI.Xaml.Media.MatrixHelper"),
    mapped("Windows.UI.Xaml.Media.Animation.IKeyTimeHelper"),
    mapped("Windows.UI.Xaml.Media.Animation.IKeyTimeHelperStatics"),
    mapped("Windows.UI.Xaml.Media.Animation.IRepeatBehaviorHelper"),
    mapped("Windows.UI.Xaml.Media.Animation.IRepeatBehaviorHelperStatics"),
    mapped("Windows.UI.Xaml.Media.Animation.KeyTime", "Windows.UI.Xaml.Media.Animation", "KeyTime"),
    mapped("Windows.UI.Xaml.Media.Animation.KeyTimeHelper"),
    mapped("Windows.UI.Xaml.Media.Animation.RepeatBehavior", "Windows.UI.Xaml.Media.Animation", "RepeatBehavior"),
    mapped("Windows.UI.Xaml.Media.Animation.RepeatBehaviorHelper"),
    mapped("Windows.UI.Xaml.Media.Animation.RepeatBehaviorType", "Windows.UI.Xaml.Media.Animation", "RepeatBehaviorType"),
    mapped("Windows.UI.Xaml.Media.Media3D.IMatrix3DHelper"),
    mapped("Windows.UI.Xaml.Media.Media3D.IMatrix3DHelperStatics"),
    mapped("Windows.UI.Xaml.Media.Media3D.Matrix3D", "Windows.UI.Xaml.Media.Media3D", "Matrix3D"),
    mapped("Windows.UI.Xaml.Media.Media3D.Matrix3DHelper"),
).associateBy(WinRtMappedTypeDescriptor::abiQualifiedName)

private val MAPPED_TYPES_BY_NAMESPACE: Map<String, List<WinRtMappedTypeDescriptor>> =
    MAPPED_TYPES.values
        .groupBy(WinRtMappedTypeDescriptor::abiNamespace)
        .mapValues { (_, types) -> types.sortedBy(WinRtMappedTypeDescriptor::abiName) }
