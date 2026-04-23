package io.github.kitectlab.winrt.metadata

data class WinRtResolvedDeclaredInterfaceDescriptor(
    val interfaceName: String,
    val interfaceType: WinRtTypeRef,
    val definitionQualifiedName: String? = interfaceType.qualifiedName,
    val definitionType: WinRtTypeDefinition? = null,
    val isDefault: Boolean = false,
    val isOverridable: Boolean = false,
    val isProtected: Boolean = false,
    val isExclusiveTo: Boolean = definitionType?.isExclusiveTo ?: false,
)

data class WinRtTypeLookup(
    val qualifiedTypeName: String,
    val type: WinRtTypeDefinition,
    val canonicalType: WinRtResolvedTypeReference,
    val declaredInterfaces: List<WinRtResolvedDeclaredInterfaceDescriptor> = emptyList(),
    val declaredInterfacesByName: Map<String, WinRtResolvedDeclaredInterfaceDescriptor> = emptyMap(),
    val interfaceClosure: WinRtInterfaceClosureDescriptor? = null,
    val runtimeClassClosure: WinRtRuntimeClassClosureDescriptor? = null,
    val defaultInterface: WinRtRuntimeClassInterfaceDescriptor? = null,
    val methodsByName: Map<String, List<WinRtMethodDefinition>> = emptyMap(),
    val methodsBySignatureKey: Map<String, WinRtMethodDefinition> = emptyMap(),
    val propertiesByName: Map<String, WinRtPropertyDefinition> = emptyMap(),
    val propertiesBySignatureKey: Map<String, WinRtPropertyDefinition> = emptyMap(),
    val eventsByName: Map<String, WinRtEventDefinition> = emptyMap(),
    val eventsBySignatureKey: Map<String, WinRtEventDefinition> = emptyMap(),
) {
    fun declaredInterface(interfaceName: String): WinRtResolvedDeclaredInterfaceDescriptor? =
        declaredInterfacesByName[interfaceName]

    fun methodOverloads(name: String): List<WinRtMethodDefinition> = methodsByName[name].orEmpty()

    fun method(signatureKey: String): WinRtMethodDefinition? = methodsBySignatureKey[signatureKey]

    fun property(name: String): WinRtPropertyDefinition? = propertiesByName[name]

    fun propertyBySignature(signatureKey: String): WinRtPropertyDefinition? = propertiesBySignatureKey[signatureKey]

    fun event(name: String): WinRtEventDefinition? = eventsByName[name]

    fun eventBySignature(signatureKey: String): WinRtEventDefinition? = eventsBySignatureKey[signatureKey]
}

class WinRtMetadataLookupIndex private constructor(
    val model: WinRtMetadataModel,
    private val namespacesByName: Map<String, WinRtNamespace>,
    private val typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    private val typeLookupsByQualifiedName: Map<String, WinRtTypeLookup>,
) {
    fun namespace(name: String): WinRtNamespace? = namespacesByName[name]

    fun type(qualifiedName: String): WinRtTypeDefinition? = typesByQualifiedName[qualifiedName]

    fun typeLookup(qualifiedName: String): WinRtTypeLookup? = typeLookupsByQualifiedName[qualifiedName]

    fun typeLookup(type: WinRtTypeDefinition): WinRtTypeLookup? = typeLookup(type.qualifiedName)

    fun typeLookup(
        type: WinRtTypeRef,
        currentNamespace: String,
    ): WinRtTypeLookup? =
        canonicalType(type, currentNamespace).definitionQualifiedName?.let(typeLookupsByQualifiedName::get)

    fun canonicalType(
        type: WinRtTypeRef,
        currentNamespace: String,
    ): WinRtResolvedTypeReference =
        resolveTypeReference(type, currentNamespace, typesByQualifiedName)

    fun canonicalType(
        typeName: String,
        currentNamespace: String,
    ): WinRtResolvedTypeReference =
        canonicalType(WinRtTypeRef.fromDisplayName(typeName), currentNamespace)

    companion object {
        fun create(model: WinRtMetadataModel): WinRtMetadataLookupIndex {
            val normalizedModel = model.normalized()
            val typesByQualifiedName = buildTypesByQualifiedName(normalizedModel)
            val closureResolver = normalizedModel.closureResolver()
            val namespacesByName = linkedMapOf<String, WinRtNamespace>()
            normalizedModel.namespaces.forEach { namespace ->
                namespacesByName.putIfAbsent(namespace.name, namespace)
            }
            val typeLookupsByQualifiedName = linkedMapOf<String, WinRtTypeLookup>()
            normalizedModel.namespaces.forEach { namespace ->
                namespace.types.forEach { type ->
                    typeLookupsByQualifiedName[type.qualifiedName] =
                        createTypeLookup(type, typesByQualifiedName, closureResolver)
                }
            }
            return WinRtMetadataLookupIndex(
                model = normalizedModel,
                namespacesByName = namespacesByName,
                typesByQualifiedName = typesByQualifiedName,
                typeLookupsByQualifiedName = typeLookupsByQualifiedName,
            )
        }
    }
}

fun WinRtMetadataModel.lookupIndex(): WinRtMetadataLookupIndex = WinRtMetadataLookupIndex.create(this)

private fun createTypeLookup(
    type: WinRtTypeDefinition,
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    closureResolver: WinRtMetadataClosureResolver,
): WinRtTypeLookup {
    val canonicalType = resolveTypeReference(WinRtTypeRef.named(type.qualifiedName), type.namespace, typesByQualifiedName)
    val declaredInterfaces = resolveDeclaredInterfaces(type, typesByQualifiedName)
    val interfaceClosure =
        if (type.kind == WinRtTypeKind.Interface) {
            closureResolver.resolveInterface(type)
        } else {
            null
        }
    val runtimeClassClosure =
        if (type.kind == WinRtTypeKind.RuntimeClass) {
            closureResolver.resolveRuntimeClass(type)
        } else {
            null
        }
    return WinRtTypeLookup(
        qualifiedTypeName = type.qualifiedName,
        type = type,
        canonicalType = canonicalType,
        declaredInterfaces = declaredInterfaces,
        declaredInterfacesByName = declaredInterfaces.associateByTo(linkedMapOf(), WinRtResolvedDeclaredInterfaceDescriptor::interfaceName),
        interfaceClosure = interfaceClosure,
        runtimeClassClosure = runtimeClassClosure,
        defaultInterface = runtimeClassClosure?.instanceInterfaces?.firstOrNull(WinRtRuntimeClassInterfaceDescriptor::isDefault),
        methodsByName = indexMethodsByName(type.methods),
        methodsBySignatureKey = indexMethodsBySignature(type.methods),
        propertiesByName = type.properties.associateByTo(linkedMapOf(), WinRtPropertyDefinition::name),
        propertiesBySignatureKey = indexPropertiesBySignature(type.properties),
        eventsByName = type.events.associateByTo(linkedMapOf(), WinRtEventDefinition::name),
        eventsBySignatureKey = indexEventsBySignature(type.events),
    )
}

private fun resolveDeclaredInterfaces(
    type: WinRtTypeDefinition,
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
): List<WinRtResolvedDeclaredInterfaceDescriptor> =
    type.implementedInterfaces.map { implemented ->
        val resolvedInterface = resolveTypeReference(implemented.interfaceType, type.namespace, typesByQualifiedName)
        WinRtResolvedDeclaredInterfaceDescriptor(
            interfaceName = resolvedInterface.displayName,
            interfaceType = resolvedInterface.type,
            definitionQualifiedName = resolvedInterface.definitionQualifiedName,
            definitionType = resolvedInterface.definitionType,
            isDefault = implemented.isDefault,
            isOverridable = implemented.isOverridable,
            isProtected = implemented.isProtected,
            isExclusiveTo = resolvedInterface.definitionType?.isExclusiveTo ?: false,
        )
    }

private fun indexMethodsByName(
    methods: List<WinRtMethodDefinition>,
): Map<String, List<WinRtMethodDefinition>> =
    buildMap {
        methods.forEach { method ->
            put(method.name, get(method.name).orEmpty() + method)
        }
    }

private fun indexMethodsBySignature(
    methods: List<WinRtMethodDefinition>,
): Map<String, WinRtMethodDefinition> =
    methods.associateByTo(linkedMapOf(), WinRtMethodDefinition::signatureKey)

private fun indexPropertiesBySignature(
    properties: List<WinRtPropertyDefinition>,
): Map<String, WinRtPropertyDefinition> =
    properties.associateByTo(linkedMapOf(), WinRtPropertyDefinition::signatureKey)

private fun indexEventsBySignature(
    events: List<WinRtEventDefinition>,
): Map<String, WinRtEventDefinition> =
    events.associateByTo(linkedMapOf(), WinRtEventDefinition::signatureKey)
