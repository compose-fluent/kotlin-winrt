package io.github.kitectlab.winrt.metadata

enum class WinRtSpecialTypeFamily {
    Collection,
    BindableCollection,
    Async,
    Reference,
    EventHandler,
}

enum class WinRtCollectionInterfaceKind(
    val isReadOnly: Boolean,
    val isMutable: Boolean,
) {
    Iterable(isReadOnly = true, isMutable = false),
    Iterator(isReadOnly = true, isMutable = false),
    VectorView(isReadOnly = true, isMutable = false),
    Vector(isReadOnly = false, isMutable = true),
    MapView(isReadOnly = true, isMutable = false),
    Map(isReadOnly = false, isMutable = true),
    KeyValuePair(isReadOnly = true, isMutable = false),
}

enum class WinRtBindableCollectionKind(
    val isMutable: Boolean,
) {
    Iterable(isMutable = false),
    Vector(isMutable = true),
}

enum class WinRtAsyncInterfaceKind {
    Info,
    Action,
    ActionWithProgress,
    Operation,
    OperationWithProgress,
}

enum class WinRtReferenceInterfaceKind {
    Reference,
    ReferenceArray,
}

enum class WinRtEventHandlerKind {
    EventHandler,
    TypedEventHandler,
    PropertyChangedEventHandler,
    NotifyCollectionChangedEventHandler,
    VectorChangedEventHandler,
    MapChangedEventHandler,
    AsyncActionProgressHandler,
    AsyncOperationProgressHandler,
}

sealed interface WinRtSpecialTypeDescriptor {
    val family: WinRtSpecialTypeFamily
    val typeName: String
    val type: WinRtTypeRef
    val definitionQualifiedName: String?
    val definitionType: WinRtTypeDefinition?
    val typeArguments: List<WinRtResolvedTypeReference>
}

data class WinRtCollectionTypeDescriptor(
    override val typeName: String,
    override val type: WinRtTypeRef,
    override val definitionQualifiedName: String?,
    override val definitionType: WinRtTypeDefinition?,
    override val typeArguments: List<WinRtResolvedTypeReference>,
    val kind: WinRtCollectionInterfaceKind,
    val elementType: WinRtResolvedTypeReference? = null,
    val keyType: WinRtResolvedTypeReference? = null,
    val valueType: WinRtResolvedTypeReference? = null,
) : WinRtSpecialTypeDescriptor {
    override val family: WinRtSpecialTypeFamily = WinRtSpecialTypeFamily.Collection
}

data class WinRtBindableCollectionTypeDescriptor(
    override val typeName: String,
    override val type: WinRtTypeRef,
    override val definitionQualifiedName: String?,
    override val definitionType: WinRtTypeDefinition?,
    override val typeArguments: List<WinRtResolvedTypeReference>,
    val kind: WinRtBindableCollectionKind,
    val elementType: WinRtResolvedTypeReference? = null,
) : WinRtSpecialTypeDescriptor {
    override val family: WinRtSpecialTypeFamily = WinRtSpecialTypeFamily.BindableCollection
}

data class WinRtAsyncTypeDescriptor(
    override val typeName: String,
    override val type: WinRtTypeRef,
    override val definitionQualifiedName: String?,
    override val definitionType: WinRtTypeDefinition?,
    override val typeArguments: List<WinRtResolvedTypeReference>,
    val kind: WinRtAsyncInterfaceKind,
    val resultType: WinRtResolvedTypeReference? = null,
    val progressType: WinRtResolvedTypeReference? = null,
) : WinRtSpecialTypeDescriptor {
    override val family: WinRtSpecialTypeFamily = WinRtSpecialTypeFamily.Async
}

data class WinRtReferenceTypeDescriptor(
    override val typeName: String,
    override val type: WinRtTypeRef,
    override val definitionQualifiedName: String?,
    override val definitionType: WinRtTypeDefinition?,
    override val typeArguments: List<WinRtResolvedTypeReference>,
    val kind: WinRtReferenceInterfaceKind,
    val valueType: WinRtResolvedTypeReference?,
) : WinRtSpecialTypeDescriptor {
    override val family: WinRtSpecialTypeFamily = WinRtSpecialTypeFamily.Reference
}

data class WinRtEventHandlerTypeDescriptor(
    override val typeName: String,
    override val type: WinRtTypeRef,
    override val definitionQualifiedName: String?,
    override val definitionType: WinRtTypeDefinition?,
    override val typeArguments: List<WinRtResolvedTypeReference>,
    val kind: WinRtEventHandlerKind,
    val senderType: WinRtResolvedTypeReference? = null,
    val eventArgsType: WinRtResolvedTypeReference? = null,
    val elementType: WinRtResolvedTypeReference? = null,
    val keyType: WinRtResolvedTypeReference? = null,
    val valueType: WinRtResolvedTypeReference? = null,
    val resultType: WinRtResolvedTypeReference? = null,
    val progressType: WinRtResolvedTypeReference? = null,
) : WinRtSpecialTypeDescriptor {
    override val family: WinRtSpecialTypeFamily = WinRtSpecialTypeFamily.EventHandler
}

class WinRtMetadataSpecialTypeResolver private constructor(
    private val typesByQualifiedName: Map<String, WinRtTypeDefinition>,
) {
    fun resolveType(type: WinRtTypeDefinition): WinRtSpecialTypeDescriptor? =
        resolveType(WinRtTypeRef.named(type.qualifiedName), type.namespace)

    fun resolveType(
        type: WinRtTypeRef,
        currentNamespace: String,
    ): WinRtSpecialTypeDescriptor? {
        val resolvedType = resolveTypeReference(type, currentNamespace, typesByQualifiedName)
        val qualifiedName = resolvedType.definitionQualifiedName ?: resolvedType.type.qualifiedName ?: return null
        val resolvedArguments = resolvedType.type.typeArguments.map { argument ->
            resolveTypeReference(argument, currentNamespace, typesByQualifiedName)
        }
        COLLECTION_TYPES[qualifiedName]?.let { kind ->
            return buildCollectionDescriptor(kind, resolvedType, resolvedArguments)
        }
        BINDABLE_COLLECTION_TYPES[qualifiedName]?.let { kind ->
            return WinRtBindableCollectionTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                elementType = bindableElementType(),
            )
        }
        ASYNC_TYPES[qualifiedName]?.let { kind ->
            return buildAsyncDescriptor(kind, resolvedType, resolvedArguments)
        }
        REFERENCE_TYPES[qualifiedName]?.let { kind ->
            return WinRtReferenceTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                valueType = resolvedArguments.singleOrNull(),
            )
        }
        EVENT_HANDLER_TYPES[qualifiedName]?.let { kind ->
            return buildEventHandlerDescriptor(kind, resolvedType, resolvedArguments)
        }
        return null
    }

    companion object {
        fun create(model: WinRtMetadataModel): WinRtMetadataSpecialTypeResolver =
            WinRtMetadataSpecialTypeResolver(buildTypesByQualifiedName(model))
    }
}

fun WinRtMetadataModel.specialTypeResolver(): WinRtMetadataSpecialTypeResolver =
    WinRtMetadataSpecialTypeResolver.create(this)

private fun buildCollectionDescriptor(
    kind: WinRtCollectionInterfaceKind,
    resolvedType: WinRtResolvedTypeReference,
    resolvedArguments: List<WinRtResolvedTypeReference>,
): WinRtCollectionTypeDescriptor =
    when (kind) {
        WinRtCollectionInterfaceKind.Iterable,
        WinRtCollectionInterfaceKind.Iterator,
        WinRtCollectionInterfaceKind.VectorView,
        WinRtCollectionInterfaceKind.Vector,
        -> WinRtCollectionTypeDescriptor(
            typeName = resolvedType.displayName,
            type = resolvedType.type,
            definitionQualifiedName = resolvedType.definitionQualifiedName,
            definitionType = resolvedType.definitionType,
            typeArguments = resolvedArguments,
            kind = kind,
            elementType = resolvedArguments.singleOrNull(),
        )

        WinRtCollectionInterfaceKind.MapView,
        WinRtCollectionInterfaceKind.Map,
        WinRtCollectionInterfaceKind.KeyValuePair,
        -> WinRtCollectionTypeDescriptor(
            typeName = resolvedType.displayName,
            type = resolvedType.type,
            definitionQualifiedName = resolvedType.definitionQualifiedName,
            definitionType = resolvedType.definitionType,
            typeArguments = resolvedArguments,
            kind = kind,
            keyType = resolvedArguments.getOrNull(0),
            valueType = resolvedArguments.getOrNull(1),
        )
    }

private fun buildAsyncDescriptor(
    kind: WinRtAsyncInterfaceKind,
    resolvedType: WinRtResolvedTypeReference,
    resolvedArguments: List<WinRtResolvedTypeReference>,
): WinRtAsyncTypeDescriptor =
    when (kind) {
        WinRtAsyncInterfaceKind.Info,
        WinRtAsyncInterfaceKind.Action,
        -> WinRtAsyncTypeDescriptor(
            typeName = resolvedType.displayName,
            type = resolvedType.type,
            definitionQualifiedName = resolvedType.definitionQualifiedName,
            definitionType = resolvedType.definitionType,
            typeArguments = resolvedArguments,
            kind = kind,
        )

        WinRtAsyncInterfaceKind.ActionWithProgress ->
            WinRtAsyncTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                progressType = resolvedArguments.singleOrNull(),
            )

        WinRtAsyncInterfaceKind.Operation ->
            WinRtAsyncTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                resultType = resolvedArguments.singleOrNull(),
            )

        WinRtAsyncInterfaceKind.OperationWithProgress ->
            WinRtAsyncTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                resultType = resolvedArguments.getOrNull(0),
                progressType = resolvedArguments.getOrNull(1),
            )
    }

private fun buildEventHandlerDescriptor(
    kind: WinRtEventHandlerKind,
    resolvedType: WinRtResolvedTypeReference,
    resolvedArguments: List<WinRtResolvedTypeReference>,
): WinRtEventHandlerTypeDescriptor =
    when (kind) {
        WinRtEventHandlerKind.EventHandler ->
            WinRtEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                eventArgsType = resolvedArguments.singleOrNull(),
            )

        WinRtEventHandlerKind.TypedEventHandler ->
            WinRtEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                senderType = resolvedArguments.getOrNull(0),
                eventArgsType = resolvedArguments.getOrNull(1),
            )

        WinRtEventHandlerKind.VectorChangedEventHandler ->
            WinRtEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                elementType = resolvedArguments.singleOrNull(),
            )

        WinRtEventHandlerKind.MapChangedEventHandler ->
            WinRtEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                keyType = resolvedArguments.getOrNull(0),
                valueType = resolvedArguments.getOrNull(1),
            )

        WinRtEventHandlerKind.AsyncActionProgressHandler ->
            WinRtEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                progressType = resolvedArguments.singleOrNull(),
            )

        WinRtEventHandlerKind.AsyncOperationProgressHandler ->
            WinRtEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                resultType = resolvedArguments.getOrNull(0),
                progressType = resolvedArguments.getOrNull(1),
            )

        WinRtEventHandlerKind.PropertyChangedEventHandler,
        WinRtEventHandlerKind.NotifyCollectionChangedEventHandler,
        -> WinRtEventHandlerTypeDescriptor(
            typeName = resolvedType.displayName,
            type = resolvedType.type,
            definitionQualifiedName = resolvedType.definitionQualifiedName,
            definitionType = resolvedType.definitionType,
            typeArguments = resolvedArguments,
            kind = kind,
        )
    }

private fun bindableElementType(): WinRtResolvedTypeReference =
    WinRtResolvedTypeReference(
        type = WinRtTypeRef.named("System.Object"),
        displayName = "System.Object",
        definitionQualifiedName = "System.Object",
        definitionType = null,
    )

private val COLLECTION_TYPES = mapOf(
    "Windows.Foundation.Collections.IIterable" to WinRtCollectionInterfaceKind.Iterable,
    "Windows.Foundation.Collections.IIterator" to WinRtCollectionInterfaceKind.Iterator,
    "Windows.Foundation.Collections.IVectorView" to WinRtCollectionInterfaceKind.VectorView,
    "Windows.Foundation.Collections.IVector" to WinRtCollectionInterfaceKind.Vector,
    "Windows.Foundation.Collections.IMapView" to WinRtCollectionInterfaceKind.MapView,
    "Windows.Foundation.Collections.IMap" to WinRtCollectionInterfaceKind.Map,
    "Windows.Foundation.Collections.IKeyValuePair" to WinRtCollectionInterfaceKind.KeyValuePair,
)

private val BINDABLE_COLLECTION_TYPES = mapOf(
    "Microsoft.UI.Xaml.Interop.IBindableIterable" to WinRtBindableCollectionKind.Iterable,
    "Microsoft.UI.Xaml.Interop.IBindableVector" to WinRtBindableCollectionKind.Vector,
    "Windows.UI.Xaml.Interop.IBindableIterable" to WinRtBindableCollectionKind.Iterable,
    "Windows.UI.Xaml.Interop.IBindableVector" to WinRtBindableCollectionKind.Vector,
)

private val ASYNC_TYPES = mapOf(
    "Windows.Foundation.IAsyncInfo" to WinRtAsyncInterfaceKind.Info,
    "Windows.Foundation.IAsyncAction" to WinRtAsyncInterfaceKind.Action,
    "Windows.Foundation.IAsyncActionWithProgress" to WinRtAsyncInterfaceKind.ActionWithProgress,
    "Windows.Foundation.IAsyncOperation" to WinRtAsyncInterfaceKind.Operation,
    "Windows.Foundation.IAsyncOperationWithProgress" to WinRtAsyncInterfaceKind.OperationWithProgress,
)

private val REFERENCE_TYPES = mapOf(
    "Windows.Foundation.IReference" to WinRtReferenceInterfaceKind.Reference,
    "Windows.Foundation.IReferenceArray" to WinRtReferenceInterfaceKind.ReferenceArray,
)

private val EVENT_HANDLER_TYPES = mapOf(
    "Windows.Foundation.EventHandler" to WinRtEventHandlerKind.EventHandler,
    "System.EventHandler" to WinRtEventHandlerKind.EventHandler,
    "Windows.Foundation.TypedEventHandler" to WinRtEventHandlerKind.TypedEventHandler,
    "Windows.Foundation.Collections.VectorChangedEventHandler" to WinRtEventHandlerKind.VectorChangedEventHandler,
    "Windows.Foundation.Collections.MapChangedEventHandler" to WinRtEventHandlerKind.MapChangedEventHandler,
    "Windows.Foundation.AsyncActionProgressHandler" to WinRtEventHandlerKind.AsyncActionProgressHandler,
    "Windows.Foundation.AsyncOperationProgressHandler" to WinRtEventHandlerKind.AsyncOperationProgressHandler,
    "Microsoft.UI.Xaml.Data.PropertyChangedEventHandler" to WinRtEventHandlerKind.PropertyChangedEventHandler,
    "Windows.UI.Xaml.Data.PropertyChangedEventHandler" to WinRtEventHandlerKind.PropertyChangedEventHandler,
    "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventHandler" to WinRtEventHandlerKind.NotifyCollectionChangedEventHandler,
    "Windows.UI.Xaml.Interop.NotifyCollectionChangedEventHandler" to WinRtEventHandlerKind.NotifyCollectionChangedEventHandler,
)
