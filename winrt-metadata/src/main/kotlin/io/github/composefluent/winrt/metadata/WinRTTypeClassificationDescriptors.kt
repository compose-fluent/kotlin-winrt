package io.github.composefluent.winrt.metadata

enum class WinRTProjectionCategory {
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

data class WinRTMappedTypeDescriptor(
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

data class WinRTTypeClassificationDescriptor(
    val typeName: String,
    val type: WinRTTypeRef,
    val definitionQualifiedName: String? = null,
    val definitionType: WinRTTypeDefinition? = null,
    val projectionCategory: WinRTProjectionCategory,
    val abiCategory: WinRTAbiTypeCategory,
    val mappedType: WinRTMappedTypeDescriptor? = null,
    val specialType: WinRTSpecialTypeDescriptor? = null,
    val isProjectionInternal: Boolean = definitionType?.isProjectionInternal ?: false,
    val isApiContract: Boolean = definitionType?.isApiContract ?: false,
    val isAttributeType: Boolean = definitionType?.isAttributeType ?: false,
    val isMappedType: Boolean = mappedType != null,
    val requiresMarshaling: Boolean = mappedType?.requiresMarshaling ?: false,
    val hasCustomMembersOutput: Boolean = mappedType?.hasCustomMembersOutput ?: false,
    val isXamlAlias: Boolean = mappedType?.isXamlAlias ?: false,
    val isBlittable: Boolean = definitionType?.isBlittable ?: false,
)

class WinRTMetadataTypeClassifier private constructor(
    private val typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    private val specialTypeResolver: WinRTMetadataSpecialTypeResolver,
) {
    fun mappedTypesInNamespace(abiNamespace: String): List<WinRTMappedTypeDescriptor> =
        MAPPED_TYPES_BY_NAMESPACE[abiNamespace].orEmpty()

    fun mappedType(
        abiNamespace: String,
        abiName: String,
    ): WinRTMappedTypeDescriptor? = MAPPED_TYPES["$abiNamespace.$abiName"]

    fun classify(type: WinRTTypeDefinition): WinRTTypeClassificationDescriptor =
        classify(WinRTTypeRef.named(type.qualifiedName), type.namespace)

    fun classify(
        type: WinRTTypeRef,
        currentNamespace: String,
    ): WinRTTypeClassificationDescriptor {
        val normalizedType = type.normalized()
        if (normalizedType.kind == WinRTTypeRefKind.Array) {
            return WinRTTypeClassificationDescriptor(
                typeName = normalizedType.typeName,
                type = normalizedType,
                projectionCategory = WinRTProjectionCategory.Array,
                abiCategory = WinRTAbiTypeCategory.Array,
            )
        }
        if (normalizedType.kind == WinRTTypeRefKind.GenericTypeParameter) {
            return WinRTTypeClassificationDescriptor(
                typeName = normalizedType.typeName,
                type = normalizedType,
                projectionCategory = WinRTProjectionCategory.GenericTypeParameter,
                abiCategory = WinRTAbiTypeCategory.GenericTypeParameter,
            )
        }
        if (normalizedType.kind == WinRTTypeRefKind.MethodTypeParameter) {
            return WinRTTypeClassificationDescriptor(
                typeName = normalizedType.typeName,
                type = normalizedType,
                projectionCategory = WinRTProjectionCategory.MethodTypeParameter,
                abiCategory = WinRTAbiTypeCategory.MethodTypeParameter,
            )
        }
        if (normalizedType.kind == WinRTTypeRefKind.Unknown) {
            return WinRTTypeClassificationDescriptor(
                typeName = normalizedType.typeName,
                type = normalizedType,
                projectionCategory = WinRTProjectionCategory.Unknown,
                abiCategory = WinRTAbiTypeCategory.Unknown,
            )
        }

        val resolvedType = resolveTypeReference(normalizedType, currentNamespace, typesByQualifiedName)
        val rawTypeName = resolvedType.definitionQualifiedName ?: normalizedType.qualifiedName ?: normalizedType.typeName
        val mappedType = MAPPED_TYPES[rawTypeName]
        val specialType = specialTypeResolver.resolveType(normalizedType, currentNamespace)
        val projectionCategory = projectionCategoryFor(rawTypeName, resolvedType.definitionType)
        return WinRTTypeClassificationDescriptor(
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
        fun create(model: WinRTMetadataModel): WinRTMetadataTypeClassifier {
            val typesByQualifiedName = buildTypesByQualifiedName(model)
            return WinRTMetadataTypeClassifier(
                typesByQualifiedName = typesByQualifiedName,
                specialTypeResolver = model.specialTypeResolver(),
            )
        }
    }
}

fun WinRTMetadataModel.typeClassifier(): WinRTMetadataTypeClassifier =
    WinRTMetadataTypeClassifier.create(this)

fun isWinRTObjectTypeName(typeName: String): Boolean =
    when (typeName.trim().substringBefore('<').removeSuffix("?")) {
        "Any", "Object", "System.Object" -> true
        else -> false
    }

fun isWinRTVoidTypeName(typeName: String): Boolean =
    when (typeName.trim().substringBefore('<').removeSuffix("?")) {
        "Unit", "Void", "System.Void" -> true
        else -> false
    }

fun isWinRTGuidTypeName(typeName: String): Boolean =
    when (typeName.trim().substringBefore('<').removeSuffix("?")) {
        "Guid", "System.Guid" -> true
        else -> false
    }

fun isWinRTTypeTypeName(typeName: String): Boolean =
    when (typeName.trim().substringBefore('<').removeSuffix("?")) {
        "Type", "System.Type" -> true
        else -> false
    }

internal fun WinRTProjectionCategory.toAbiCategory(): WinRTAbiTypeCategory =
    when (this) {
        WinRTProjectionCategory.Unit -> WinRTAbiTypeCategory.Unit
        WinRTProjectionCategory.Fundamental -> WinRTAbiTypeCategory.Fundamental
        WinRTProjectionCategory.String -> WinRTAbiTypeCategory.String
        WinRTProjectionCategory.Object -> WinRTAbiTypeCategory.Object
        WinRTProjectionCategory.Guid -> WinRTAbiTypeCategory.Guid
        WinRTProjectionCategory.Type -> WinRTAbiTypeCategory.Type
        WinRTProjectionCategory.Enum -> WinRTAbiTypeCategory.Enum
        WinRTProjectionCategory.Struct -> WinRTAbiTypeCategory.Struct
        WinRTProjectionCategory.Interface -> WinRTAbiTypeCategory.Interface
        WinRTProjectionCategory.Delegate -> WinRTAbiTypeCategory.Delegate
        WinRTProjectionCategory.RuntimeClass -> WinRTAbiTypeCategory.RuntimeClass
        WinRTProjectionCategory.Attribute -> WinRTAbiTypeCategory.RuntimeClass
        WinRTProjectionCategory.ApiContract -> WinRTAbiTypeCategory.Struct
        WinRTProjectionCategory.GenericTypeParameter -> WinRTAbiTypeCategory.GenericTypeParameter
        WinRTProjectionCategory.MethodTypeParameter -> WinRTAbiTypeCategory.MethodTypeParameter
        WinRTProjectionCategory.Array -> WinRTAbiTypeCategory.Array
        WinRTProjectionCategory.Unknown -> WinRTAbiTypeCategory.Unknown
    }

private fun projectionCategoryFor(
    rawTypeName: String,
    resolvedType: WinRTTypeDefinition?,
): WinRTProjectionCategory {
    val systemBaseKind = winRTTypeKindForSystemBaseTypeName(rawTypeName)
    return when {
        isWinRTVoidTypeName(rawTypeName) -> WinRTProjectionCategory.Unit
        winRTFundamentalTypeForName(rawTypeName) == WinRTFundamentalType.String -> WinRTProjectionCategory.String
        isWinRTFundamentalTypeName(rawTypeName) -> WinRTProjectionCategory.Fundamental
        isWinRTObjectTypeName(rawTypeName) -> WinRTProjectionCategory.Object
        isWinRTGuidTypeName(rawTypeName) -> WinRTProjectionCategory.Guid
        isWinRTTypeTypeName(rawTypeName) -> WinRTProjectionCategory.Type
        systemBaseKind != null -> systemBaseKind.projectionCategory()
        resolvedType?.isApiContract == true && resolvedType.kind == WinRTTypeKind.Struct -> WinRTProjectionCategory.ApiContract
        resolvedType?.isAttributeType == true && resolvedType.kind == WinRTTypeKind.RuntimeClass -> WinRTProjectionCategory.Attribute
        resolvedType != null -> resolvedType.kind.projectionCategory()

        else -> WinRTProjectionCategory.Unknown
    }
}

private fun WinRTTypeKind.projectionCategory(): WinRTProjectionCategory =
    when (this) {
        WinRTTypeKind.Enum -> WinRTProjectionCategory.Enum
        WinRTTypeKind.Struct -> WinRTProjectionCategory.Struct
        WinRTTypeKind.Interface -> WinRTProjectionCategory.Interface
        WinRTTypeKind.Delegate -> WinRTProjectionCategory.Delegate
        WinRTTypeKind.RuntimeClass -> WinRTProjectionCategory.RuntimeClass
        WinRTTypeKind.Unknown -> WinRTProjectionCategory.Unknown
    }

private fun mapped(
    abiQualifiedName: String,
    mappedNamespace: String? = null,
    mappedName: String? = null,
    requiresMarshaling: Boolean = false,
    hasCustomMembersOutput: Boolean = false,
): WinRTMappedTypeDescriptor {
    val abiNamespace = abiQualifiedName.substringBeforeLast('.', "")
    val abiName = abiQualifiedName.substringAfterLast('.')
    return WinRTMappedTypeDescriptor(
        abiNamespace = abiNamespace,
        abiName = abiName,
        mappedNamespace = mappedNamespace,
        mappedName = mappedName,
        requiresMarshaling = requiresMarshaling,
        hasCustomMembersOutput = hasCustomMembersOutput,
    )
}

private val MAPPED_TYPES: Map<String, WinRTMappedTypeDescriptor> = listOf(
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
    mapped("Microsoft.UI.Xaml.Interop.IBindableVectorView", "System.Collections", "IList", requiresMarshaling = true, hasCustomMembersOutput = true),
    mapped("Windows.UI.Xaml.Interop.IBindableVectorView", "System.Collections", "IList", requiresMarshaling = true, hasCustomMembersOutput = true),
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
).associateBy(WinRTMappedTypeDescriptor::abiQualifiedName)

private val MAPPED_TYPES_BY_NAMESPACE: Map<String, List<WinRTMappedTypeDescriptor>> =
    MAPPED_TYPES.values
        .groupBy(WinRTMappedTypeDescriptor::abiNamespace)
        .mapValues { (_, types) -> types.sortedBy(WinRTMappedTypeDescriptor::abiName) }
