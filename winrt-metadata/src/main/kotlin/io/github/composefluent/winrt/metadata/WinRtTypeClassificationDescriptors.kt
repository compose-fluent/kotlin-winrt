package io.github.composefluent.winrt.metadata

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

fun isWinRtObjectTypeName(typeName: String): Boolean =
    when (typeName.trim().substringBefore('<').removeSuffix("?")) {
        "Any", "Object", "System.Object" -> true
        else -> false
    }

fun isWinRtVoidTypeName(typeName: String): Boolean =
    when (typeName.trim().substringBefore('<').removeSuffix("?")) {
        "Unit", "Void", "System.Void" -> true
        else -> false
    }

fun isWinRtGuidTypeName(typeName: String): Boolean =
    when (typeName.trim().substringBefore('<').removeSuffix("?")) {
        "Guid", "System.Guid" -> true
        else -> false
    }

fun isWinRtTypeTypeName(typeName: String): Boolean =
    when (typeName.trim().substringBefore('<').removeSuffix("?")) {
        "Type", "System.Type" -> true
        else -> false
    }

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
): WinRtProjectionCategory {
    val systemBaseKind = winRtTypeKindForSystemBaseTypeName(rawTypeName)
    return when {
        isWinRtVoidTypeName(rawTypeName) -> WinRtProjectionCategory.Unit
        winRtFundamentalTypeForName(rawTypeName) == WinRtFundamentalType.String -> WinRtProjectionCategory.String
        isWinRtFundamentalTypeName(rawTypeName) -> WinRtProjectionCategory.Fundamental
        isWinRtObjectTypeName(rawTypeName) -> WinRtProjectionCategory.Object
        isWinRtGuidTypeName(rawTypeName) -> WinRtProjectionCategory.Guid
        isWinRtTypeTypeName(rawTypeName) -> WinRtProjectionCategory.Type
        systemBaseKind != null -> systemBaseKind.projectionCategory()
        resolvedType?.isApiContract == true && resolvedType.kind == WinRtTypeKind.Struct -> WinRtProjectionCategory.ApiContract
        resolvedType?.isAttributeType == true && resolvedType.kind == WinRtTypeKind.RuntimeClass -> WinRtProjectionCategory.Attribute
        resolvedType != null -> resolvedType.kind.projectionCategory()

        else -> WinRtProjectionCategory.Unknown
    }
}

private fun WinRtTypeKind.projectionCategory(): WinRtProjectionCategory =
    when (this) {
        WinRtTypeKind.Enum -> WinRtProjectionCategory.Enum
        WinRtTypeKind.Struct -> WinRtProjectionCategory.Struct
        WinRtTypeKind.Interface -> WinRtProjectionCategory.Interface
        WinRtTypeKind.Delegate -> WinRtProjectionCategory.Delegate
        WinRtTypeKind.RuntimeClass -> WinRtProjectionCategory.RuntimeClass
        WinRtTypeKind.Unknown -> WinRtProjectionCategory.Unknown
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

private val MAPPED_TYPES: Map<String, WinRtMappedTypeDescriptor> = listOf(
    mapped("Microsoft.UI.Xaml.CornerRadiusHelper"),
    mapped("Microsoft.UI.Xaml.DurationHelper"),
    mapped("Microsoft.UI.Xaml.GridLengthHelper"),
    mapped("Microsoft.UI.Xaml.ICornerRadiusHelper"),
    mapped("Microsoft.UI.Xaml.ICornerRadiusHelperStatics"),
    mapped("Microsoft.UI.Xaml.IDurationHelper"),
    mapped("Microsoft.UI.Xaml.IDurationHelperStatics"),
    mapped("Microsoft.UI.Xaml.IGridLengthHelper"),
    mapped("Microsoft.UI.Xaml.IGridLengthHelperStatics"),
    mapped("Microsoft.UI.Xaml.IThicknessHelper"),
    mapped("Microsoft.UI.Xaml.IThicknessHelperStatics"),
    mapped("Microsoft.UI.Xaml.ThicknessHelper"),
    mapped("Microsoft.UI.Xaml.Controls.Primitives.GeneratorPositionHelper"),
    mapped("Microsoft.UI.Xaml.Controls.Primitives.IGeneratorPositionHelper"),
    mapped("Microsoft.UI.Xaml.Controls.Primitives.IGeneratorPositionHelperStatics"),
    mapped("Microsoft.UI.Xaml.Data.DataErrorsChangedEventArgs", "System.ComponentModel", "DataErrorsChangedEventArgs"),
    mapped("Microsoft.UI.Xaml.Media.IMatrixHelper"),
    mapped("Microsoft.UI.Xaml.Media.IMatrixHelperStatics"),
    mapped("Microsoft.UI.Xaml.Media.MatrixHelper"),
    mapped("Microsoft.UI.Xaml.Media.Animation.IKeyTimeHelper"),
    mapped("Microsoft.UI.Xaml.Media.Animation.IKeyTimeHelperStatics"),
    mapped("Microsoft.UI.Xaml.Media.Animation.IRepeatBehaviorHelper"),
    mapped("Microsoft.UI.Xaml.Media.Animation.IRepeatBehaviorHelperStatics"),
    mapped("Microsoft.UI.Xaml.Media.Animation.KeyTimeHelper"),
    mapped("Microsoft.UI.Xaml.Media.Animation.RepeatBehaviorHelper"),
    mapped("Microsoft.UI.Xaml.Media.Media3D.IMatrix3DHelper"),
    mapped("Microsoft.UI.Xaml.Media.Media3D.IMatrix3DHelperStatics"),
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
    mapped("Windows.UI.Xaml.CornerRadiusHelper"),
    mapped("Windows.UI.Xaml.DurationHelper"),
    mapped("Windows.UI.Xaml.GridLengthHelper"),
    mapped("Windows.UI.Xaml.ICornerRadiusHelper"),
    mapped("Windows.UI.Xaml.ICornerRadiusHelperStatics"),
    mapped("Windows.UI.Xaml.IDurationHelper"),
    mapped("Windows.UI.Xaml.IDurationHelperStatics"),
    mapped("Windows.UI.Xaml.IGridLengthHelper"),
    mapped("Windows.UI.Xaml.IGridLengthHelperStatics"),
    mapped("Windows.UI.Xaml.IThicknessHelper"),
    mapped("Windows.UI.Xaml.IThicknessHelperStatics"),
    mapped("Windows.UI.Xaml.ThicknessHelper"),
    mapped("Microsoft.UI.Xaml.IXamlServiceProvider", "System", "IServiceProvider"),
    mapped("Windows.UI.Xaml.IXamlServiceProvider", "System", "IServiceProvider"),
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
    mapped("Windows.UI.Xaml.Media.MatrixHelper"),
    mapped("Windows.UI.Xaml.Media.Animation.IKeyTimeHelper"),
    mapped("Windows.UI.Xaml.Media.Animation.IKeyTimeHelperStatics"),
    mapped("Windows.UI.Xaml.Media.Animation.IRepeatBehaviorHelper"),
    mapped("Windows.UI.Xaml.Media.Animation.IRepeatBehaviorHelperStatics"),
    mapped("Windows.UI.Xaml.Media.Animation.KeyTimeHelper"),
    mapped("Windows.UI.Xaml.Media.Animation.RepeatBehaviorHelper"),
    mapped("Windows.UI.Xaml.Media.Media3D.IMatrix3DHelper"),
    mapped("Windows.UI.Xaml.Media.Media3D.IMatrix3DHelperStatics"),
    mapped("Windows.UI.Xaml.Media.Media3D.Matrix3DHelper"),
).associateBy(WinRtMappedTypeDescriptor::abiQualifiedName)

private val MAPPED_TYPES_BY_NAMESPACE: Map<String, List<WinRtMappedTypeDescriptor>> =
    MAPPED_TYPES.values
        .groupBy(WinRtMappedTypeDescriptor::abiNamespace)
        .mapValues { (_, types) -> types.sortedBy(WinRtMappedTypeDescriptor::abiName) }
