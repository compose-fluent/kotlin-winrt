package io.github.composefluent.winrt.metadata

enum class WinRTSpecialTypeFamily {
    Collection,
    BindableCollection,
    Async,
    Reference,
    EventHandler,
}

enum class WinRTCollectionInterfaceKind(
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

enum class WinRTBindableCollectionKind(
    val isReadOnly: Boolean,
    val isMutable: Boolean,
) {
    Iterable(isReadOnly = true, isMutable = false),
    VectorView(isReadOnly = true, isMutable = false),
    Vector(isReadOnly = false, isMutable = true),
}

enum class WinRTAsyncInterfaceKind {
    Info,
    Action,
    ActionWithProgress,
    Operation,
    OperationWithProgress,
}

enum class WinRTReferenceInterfaceKind {
    Reference,
    ReferenceArray,
}

enum class WinRTEventHandlerKind {
    EventHandler,
    TypedEventHandler,
    PropertyChangedEventHandler,
    NotifyCollectionChangedEventHandler,
    VectorChangedEventHandler,
    BindableVectorChangedEventHandler,
    MapChangedEventHandler,
    AsyncActionProgressHandler,
    AsyncOperationProgressHandler,
}

sealed interface WinRTSpecialTypeDescriptor {
    val family: WinRTSpecialTypeFamily
    val typeName: String
    val type: WinRTTypeRef
    val definitionQualifiedName: String?
    val definitionType: WinRTTypeDefinition?
    val typeArguments: List<WinRTResolvedTypeReference>
}

data class WinRTCollectionTypeDescriptor(
    override val typeName: String,
    override val type: WinRTTypeRef,
    override val definitionQualifiedName: String?,
    override val definitionType: WinRTTypeDefinition?,
    override val typeArguments: List<WinRTResolvedTypeReference>,
    val kind: WinRTCollectionInterfaceKind,
    val elementType: WinRTResolvedTypeReference? = null,
    val keyType: WinRTResolvedTypeReference? = null,
    val valueType: WinRTResolvedTypeReference? = null,
) : WinRTSpecialTypeDescriptor {
    override val family: WinRTSpecialTypeFamily = WinRTSpecialTypeFamily.Collection
}

data class WinRTBindableCollectionTypeDescriptor(
    override val typeName: String,
    override val type: WinRTTypeRef,
    override val definitionQualifiedName: String?,
    override val definitionType: WinRTTypeDefinition?,
    override val typeArguments: List<WinRTResolvedTypeReference>,
    val kind: WinRTBindableCollectionKind,
    val elementType: WinRTResolvedTypeReference? = null,
) : WinRTSpecialTypeDescriptor {
    override val family: WinRTSpecialTypeFamily = WinRTSpecialTypeFamily.BindableCollection
}

data class WinRTAsyncTypeDescriptor(
    override val typeName: String,
    override val type: WinRTTypeRef,
    override val definitionQualifiedName: String?,
    override val definitionType: WinRTTypeDefinition?,
    override val typeArguments: List<WinRTResolvedTypeReference>,
    val kind: WinRTAsyncInterfaceKind,
    val resultType: WinRTResolvedTypeReference? = null,
    val progressType: WinRTResolvedTypeReference? = null,
) : WinRTSpecialTypeDescriptor {
    override val family: WinRTSpecialTypeFamily = WinRTSpecialTypeFamily.Async
}

data class WinRTReferenceTypeDescriptor(
    override val typeName: String,
    override val type: WinRTTypeRef,
    override val definitionQualifiedName: String?,
    override val definitionType: WinRTTypeDefinition?,
    override val typeArguments: List<WinRTResolvedTypeReference>,
    val kind: WinRTReferenceInterfaceKind,
    val valueType: WinRTResolvedTypeReference?,
) : WinRTSpecialTypeDescriptor {
    override val family: WinRTSpecialTypeFamily = WinRTSpecialTypeFamily.Reference
}

data class WinRTEventHandlerTypeDescriptor(
    override val typeName: String,
    override val type: WinRTTypeRef,
    override val definitionQualifiedName: String?,
    override val definitionType: WinRTTypeDefinition?,
    override val typeArguments: List<WinRTResolvedTypeReference>,
    val kind: WinRTEventHandlerKind,
    val senderType: WinRTResolvedTypeReference? = null,
    val eventArgsType: WinRTResolvedTypeReference? = null,
    val elementType: WinRTResolvedTypeReference? = null,
    val keyType: WinRTResolvedTypeReference? = null,
    val valueType: WinRTResolvedTypeReference? = null,
    val resultType: WinRTResolvedTypeReference? = null,
    val progressType: WinRTResolvedTypeReference? = null,
) : WinRTSpecialTypeDescriptor {
    override val family: WinRTSpecialTypeFamily = WinRTSpecialTypeFamily.EventHandler
}

class WinRTMetadataSpecialTypeResolver private constructor(
    private val typesByQualifiedName: Map<String, WinRTTypeDefinition>,
) {
    fun resolveType(type: WinRTTypeDefinition): WinRTSpecialTypeDescriptor? =
        resolveType(WinRTTypeRef.named(type.qualifiedName), type.namespace)

    fun resolveType(
        type: WinRTTypeRef,
        currentNamespace: String,
    ): WinRTSpecialTypeDescriptor? {
        val resolvedType = resolveTypeReference(type, currentNamespace, typesByQualifiedName)
        val qualifiedName = resolvedType.definitionQualifiedName ?: resolvedType.type.qualifiedName ?: return null
        val resolvedArguments = resolvedType.type.typeArguments.map { argument ->
            resolveTypeReference(argument, currentNamespace, typesByQualifiedName)
        }
        COLLECTION_TYPES[qualifiedName]?.let { kind ->
            return buildCollectionDescriptor(kind, resolvedType, resolvedArguments)
        }
        BINDABLE_COLLECTION_TYPES[qualifiedName]?.let { kind ->
            return WinRTBindableCollectionTypeDescriptor(
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
            return WinRTReferenceTypeDescriptor(
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
        fun create(model: WinRTMetadataModel): WinRTMetadataSpecialTypeResolver =
            WinRTMetadataSpecialTypeResolver(buildTypesByQualifiedName(model))
    }
}

fun WinRTMetadataModel.specialTypeResolver(): WinRTMetadataSpecialTypeResolver =
    WinRTMetadataSpecialTypeResolver.create(this)

private fun buildCollectionDescriptor(
    kind: WinRTCollectionInterfaceKind,
    resolvedType: WinRTResolvedTypeReference,
    resolvedArguments: List<WinRTResolvedTypeReference>,
): WinRTCollectionTypeDescriptor =
    when (kind) {
        WinRTCollectionInterfaceKind.Iterable,
        WinRTCollectionInterfaceKind.Iterator,
        WinRTCollectionInterfaceKind.VectorView,
        WinRTCollectionInterfaceKind.Vector,
        -> WinRTCollectionTypeDescriptor(
            typeName = resolvedType.displayName,
            type = resolvedType.type,
            definitionQualifiedName = resolvedType.definitionQualifiedName,
            definitionType = resolvedType.definitionType,
            typeArguments = resolvedArguments,
            kind = kind,
            elementType = resolvedArguments.singleOrNull(),
        )

        WinRTCollectionInterfaceKind.MapView,
        WinRTCollectionInterfaceKind.Map,
        WinRTCollectionInterfaceKind.KeyValuePair,
        -> WinRTCollectionTypeDescriptor(
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
    kind: WinRTAsyncInterfaceKind,
    resolvedType: WinRTResolvedTypeReference,
    resolvedArguments: List<WinRTResolvedTypeReference>,
): WinRTAsyncTypeDescriptor =
    when (kind) {
        WinRTAsyncInterfaceKind.Info,
        WinRTAsyncInterfaceKind.Action,
        -> WinRTAsyncTypeDescriptor(
            typeName = resolvedType.displayName,
            type = resolvedType.type,
            definitionQualifiedName = resolvedType.definitionQualifiedName,
            definitionType = resolvedType.definitionType,
            typeArguments = resolvedArguments,
            kind = kind,
        )

        WinRTAsyncInterfaceKind.ActionWithProgress ->
            WinRTAsyncTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                progressType = resolvedArguments.singleOrNull(),
            )

        WinRTAsyncInterfaceKind.Operation ->
            WinRTAsyncTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                resultType = resolvedArguments.singleOrNull(),
            )

        WinRTAsyncInterfaceKind.OperationWithProgress ->
            WinRTAsyncTypeDescriptor(
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
    kind: WinRTEventHandlerKind,
    resolvedType: WinRTResolvedTypeReference,
    resolvedArguments: List<WinRTResolvedTypeReference>,
): WinRTEventHandlerTypeDescriptor =
    when (kind) {
        WinRTEventHandlerKind.EventHandler ->
            WinRTEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                eventArgsType = resolvedArguments.singleOrNull(),
            )

        WinRTEventHandlerKind.TypedEventHandler ->
            WinRTEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                senderType = resolvedArguments.getOrNull(0),
                eventArgsType = resolvedArguments.getOrNull(1),
            )

        WinRTEventHandlerKind.VectorChangedEventHandler ->
            WinRTEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                elementType = resolvedArguments.singleOrNull(),
            )

        WinRTEventHandlerKind.BindableVectorChangedEventHandler ->
            WinRTEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                elementType = bindableElementType(),
            )

        WinRTEventHandlerKind.MapChangedEventHandler ->
            WinRTEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                keyType = resolvedArguments.getOrNull(0),
                valueType = resolvedArguments.getOrNull(1),
            )

        WinRTEventHandlerKind.AsyncActionProgressHandler ->
            WinRTEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                progressType = resolvedArguments.singleOrNull(),
            )

        WinRTEventHandlerKind.AsyncOperationProgressHandler ->
            WinRTEventHandlerTypeDescriptor(
                typeName = resolvedType.displayName,
                type = resolvedType.type,
                definitionQualifiedName = resolvedType.definitionQualifiedName,
                definitionType = resolvedType.definitionType,
                typeArguments = resolvedArguments,
                kind = kind,
                resultType = resolvedArguments.getOrNull(0),
                progressType = resolvedArguments.getOrNull(1),
            )

        WinRTEventHandlerKind.PropertyChangedEventHandler,
        WinRTEventHandlerKind.NotifyCollectionChangedEventHandler,
        -> WinRTEventHandlerTypeDescriptor(
            typeName = resolvedType.displayName,
            type = resolvedType.type,
            definitionQualifiedName = resolvedType.definitionQualifiedName,
            definitionType = resolvedType.definitionType,
            typeArguments = resolvedArguments,
            kind = kind,
        )
    }

private fun bindableElementType(): WinRTResolvedTypeReference =
    WinRTResolvedTypeReference(
        type = WinRTTypeRef.named("System.Object"),
        displayName = "System.Object",
        definitionQualifiedName = "System.Object",
        definitionType = null,
    )

private val COLLECTION_TYPES = mapOf(
    "Windows.Foundation.Collections.IIterable" to WinRTCollectionInterfaceKind.Iterable,
    "Windows.Foundation.Collections.IIterator" to WinRTCollectionInterfaceKind.Iterator,
    "Windows.Foundation.Collections.IVectorView" to WinRTCollectionInterfaceKind.VectorView,
    "Windows.Foundation.Collections.IVector" to WinRTCollectionInterfaceKind.Vector,
    "Windows.Foundation.Collections.IMapView" to WinRTCollectionInterfaceKind.MapView,
    "Windows.Foundation.Collections.IMap" to WinRTCollectionInterfaceKind.Map,
    "Windows.Foundation.Collections.IKeyValuePair" to WinRTCollectionInterfaceKind.KeyValuePair,
)

private val BINDABLE_COLLECTION_TYPES = mapOf(
    "Microsoft.UI.Xaml.Interop.IBindableIterable" to WinRTBindableCollectionKind.Iterable,
    "Microsoft.UI.Xaml.Interop.IBindableVectorView" to WinRTBindableCollectionKind.VectorView,
    "Microsoft.UI.Xaml.Interop.IBindableVector" to WinRTBindableCollectionKind.Vector,
    "Windows.UI.Xaml.Interop.IBindableIterable" to WinRTBindableCollectionKind.Iterable,
    "Windows.UI.Xaml.Interop.IBindableVectorView" to WinRTBindableCollectionKind.VectorView,
    "Windows.UI.Xaml.Interop.IBindableVector" to WinRTBindableCollectionKind.Vector,
)

private val ASYNC_TYPES = mapOf(
    "Windows.Foundation.IAsyncInfo" to WinRTAsyncInterfaceKind.Info,
    "Windows.Foundation.IAsyncAction" to WinRTAsyncInterfaceKind.Action,
    "Windows.Foundation.IAsyncActionWithProgress" to WinRTAsyncInterfaceKind.ActionWithProgress,
    "Windows.Foundation.IAsyncOperation" to WinRTAsyncInterfaceKind.Operation,
    "Windows.Foundation.IAsyncOperationWithProgress" to WinRTAsyncInterfaceKind.OperationWithProgress,
)

private val REFERENCE_TYPES = mapOf(
    "Windows.Foundation.IReference" to WinRTReferenceInterfaceKind.Reference,
    "Windows.Foundation.IReferenceArray" to WinRTReferenceInterfaceKind.ReferenceArray,
)

private val EVENT_HANDLER_TYPES = mapOf(
    "Windows.Foundation.EventHandler" to WinRTEventHandlerKind.EventHandler,
    "System.EventHandler" to WinRTEventHandlerKind.EventHandler,
    "Windows.Foundation.TypedEventHandler" to WinRTEventHandlerKind.TypedEventHandler,
    "Windows.Foundation.Collections.VectorChangedEventHandler" to WinRTEventHandlerKind.VectorChangedEventHandler,
    "Microsoft.UI.Xaml.Interop.BindableVectorChangedEventHandler" to WinRTEventHandlerKind.BindableVectorChangedEventHandler,
    "Windows.UI.Xaml.Interop.BindableVectorChangedEventHandler" to WinRTEventHandlerKind.BindableVectorChangedEventHandler,
    "Windows.Foundation.Collections.MapChangedEventHandler" to WinRTEventHandlerKind.MapChangedEventHandler,
    "Windows.Foundation.AsyncActionProgressHandler" to WinRTEventHandlerKind.AsyncActionProgressHandler,
    "Windows.Foundation.AsyncOperationProgressHandler" to WinRTEventHandlerKind.AsyncOperationProgressHandler,
    "Microsoft.UI.Xaml.Data.PropertyChangedEventHandler" to WinRTEventHandlerKind.PropertyChangedEventHandler,
    "Windows.UI.Xaml.Data.PropertyChangedEventHandler" to WinRTEventHandlerKind.PropertyChangedEventHandler,
    "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventHandler" to WinRTEventHandlerKind.NotifyCollectionChangedEventHandler,
    "Windows.UI.Xaml.Interop.NotifyCollectionChangedEventHandler" to WinRTEventHandlerKind.NotifyCollectionChangedEventHandler,
)

fun winRTEventHandlerKindForTypeName(typeName: String): WinRTEventHandlerKind? =
    EVENT_HANDLER_TYPES[typeName.trim().substringBefore('<').removeSuffix("?")]
