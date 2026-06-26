package io.github.composefluent.winrt.metadata

data class WinRTResolvedDeclaredInterfaceDescriptor(
    val interfaceName: String,
    val interfaceType: WinRTTypeRef,
    val definitionQualifiedName: String? = interfaceType.qualifiedName,
    val definitionType: WinRTTypeDefinition? = null,
    val isDefault: Boolean = false,
    val isOverridable: Boolean = false,
    val isProtected: Boolean = false,
    val isExclusiveTo: Boolean = definitionType?.isExclusiveTo ?: false,
)

data class WinRTTypeLookup(
    val qualifiedTypeName: String,
    val type: WinRTTypeDefinition,
    val canonicalType: WinRTResolvedTypeReference,
    val declaredInterfaces: List<WinRTResolvedDeclaredInterfaceDescriptor> = emptyList(),
    val declaredInterfacesByName: Map<String, WinRTResolvedDeclaredInterfaceDescriptor> = emptyMap(),
    val interfaceClosure: WinRTInterfaceClosureDescriptor? = null,
    val runtimeClassClosure: WinRTRuntimeClassClosureDescriptor? = null,
    val defaultInterface: WinRTRuntimeClassInterfaceDescriptor? = null,
    val methodsByName: Map<String, List<WinRTMethodDefinition>> = emptyMap(),
    val methodsBySignatureKey: Map<String, WinRTMethodDefinition> = emptyMap(),
    val propertiesByName: Map<String, WinRTPropertyDefinition> = emptyMap(),
    val propertiesBySignatureKey: Map<String, WinRTPropertyDefinition> = emptyMap(),
    val eventsByName: Map<String, WinRTEventDefinition> = emptyMap(),
    val eventsBySignatureKey: Map<String, WinRTEventDefinition> = emptyMap(),
) {
    fun declaredInterface(interfaceName: String): WinRTResolvedDeclaredInterfaceDescriptor? =
        declaredInterfacesByName[interfaceName]

    fun methodOverloads(name: String): List<WinRTMethodDefinition> = methodsByName[name].orEmpty()

    fun method(signatureKey: String): WinRTMethodDefinition? = methodsBySignatureKey[signatureKey]

    fun property(name: String): WinRTPropertyDefinition? = propertiesByName[name]

    fun propertyBySignature(signatureKey: String): WinRTPropertyDefinition? = propertiesBySignatureKey[signatureKey]

    fun event(name: String): WinRTEventDefinition? = eventsByName[name]

    fun eventBySignature(signatureKey: String): WinRTEventDefinition? = eventsBySignatureKey[signatureKey]
}

class WinRTMetadataLookupIndex private constructor(
    val model: WinRTMetadataModel,
    private val namespacesByName: Map<String, WinRTNamespace>,
    private val typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    private val typeLookupsByQualifiedName: Map<String, WinRTTypeLookup>,
) {
    fun namespace(name: String): WinRTNamespace? = namespacesByName[name]

    fun type(qualifiedName: String): WinRTTypeDefinition? = typesByQualifiedName[qualifiedName]

    fun typeLookup(qualifiedName: String): WinRTTypeLookup? = typeLookupsByQualifiedName[qualifiedName]

    fun typeLookup(type: WinRTTypeDefinition): WinRTTypeLookup? = typeLookup(type.qualifiedName)

    fun typeLookup(
        type: WinRTTypeRef,
        currentNamespace: String,
    ): WinRTTypeLookup? =
        canonicalType(type, currentNamespace).definitionQualifiedName?.let(typeLookupsByQualifiedName::get)

    fun canonicalType(
        type: WinRTTypeRef,
        currentNamespace: String,
    ): WinRTResolvedTypeReference =
        resolveTypeReference(type, currentNamespace, typesByQualifiedName)

    fun canonicalType(
        typeName: String,
        currentNamespace: String,
    ): WinRTResolvedTypeReference =
        canonicalType(WinRTTypeRef.fromDisplayName(typeName), currentNamespace)

    companion object {
        fun create(model: WinRTMetadataModel): WinRTMetadataLookupIndex {
            val normalizedModel = model.normalized()
            val typesByQualifiedName = buildTypesByQualifiedName(normalizedModel)
            val closureResolver = normalizedModel.closureResolver()
            val namespacesByName = linkedMapOf<String, WinRTNamespace>()
            normalizedModel.namespaces.forEach { namespace ->
                namespacesByName.putIfAbsent(namespace.name, namespace)
            }
            val typeLookupsByQualifiedName = linkedMapOf<String, WinRTTypeLookup>()
            normalizedModel.namespaces.forEach { namespace ->
                namespace.types.forEach { type ->
                    typeLookupsByQualifiedName[type.qualifiedName] =
                        createTypeLookup(type, typesByQualifiedName, closureResolver)
                }
            }
            return WinRTMetadataLookupIndex(
                model = normalizedModel,
                namespacesByName = namespacesByName,
                typesByQualifiedName = typesByQualifiedName,
                typeLookupsByQualifiedName = typeLookupsByQualifiedName,
            )
        }
    }
}

fun WinRTMetadataModel.lookupIndex(): WinRTMetadataLookupIndex = WinRTMetadataLookupIndex.create(this)

private fun createTypeLookup(
    type: WinRTTypeDefinition,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    closureResolver: WinRTMetadataClosureResolver,
): WinRTTypeLookup {
    val canonicalType = resolveTypeReference(WinRTTypeRef.named(type.qualifiedName), type.namespace, typesByQualifiedName)
    val declaredInterfaces = resolveDeclaredInterfaces(type, typesByQualifiedName)
    val interfaceClosure =
        if (type.kind == WinRTTypeKind.Interface) {
            closureResolver.resolveInterface(type)
        } else {
            null
        }
    val runtimeClassClosure =
        if (type.kind == WinRTTypeKind.RuntimeClass) {
            closureResolver.resolveRuntimeClass(type)
        } else {
            null
        }
    return WinRTTypeLookup(
        qualifiedTypeName = type.qualifiedName,
        type = type,
        canonicalType = canonicalType,
        declaredInterfaces = declaredInterfaces,
        declaredInterfacesByName = declaredInterfaces.associateByTo(linkedMapOf(), WinRTResolvedDeclaredInterfaceDescriptor::interfaceName),
        interfaceClosure = interfaceClosure,
        runtimeClassClosure = runtimeClassClosure,
        defaultInterface = runtimeClassClosure?.instanceInterfaces?.firstOrNull(WinRTRuntimeClassInterfaceDescriptor::isDefault),
        methodsByName = indexMethodsByName(type.methods),
        methodsBySignatureKey = indexMethodsBySignature(type.methods),
        propertiesByName = type.properties.associateByTo(linkedMapOf(), WinRTPropertyDefinition::name),
        propertiesBySignatureKey = indexPropertiesBySignature(type.properties),
        eventsByName = type.events.associateByTo(linkedMapOf(), WinRTEventDefinition::name),
        eventsBySignatureKey = indexEventsBySignature(type.events),
    )
}

private fun resolveDeclaredInterfaces(
    type: WinRTTypeDefinition,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): List<WinRTResolvedDeclaredInterfaceDescriptor> =
    type.implementedInterfaces.map { implemented ->
        val resolvedInterface = resolveTypeReference(implemented.interfaceType, type.namespace, typesByQualifiedName)
        WinRTResolvedDeclaredInterfaceDescriptor(
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
    methods: List<WinRTMethodDefinition>,
): Map<String, List<WinRTMethodDefinition>> =
    buildMap {
        methods.forEach { method ->
            put(method.name, get(method.name).orEmpty() + method)
        }
    }

private fun indexMethodsBySignature(
    methods: List<WinRTMethodDefinition>,
): Map<String, WinRTMethodDefinition> =
    methods.associateByTo(linkedMapOf(), WinRTMethodDefinition::signatureKey)

private fun indexPropertiesBySignature(
    properties: List<WinRTPropertyDefinition>,
): Map<String, WinRTPropertyDefinition> =
    properties.associateByTo(linkedMapOf(), WinRTPropertyDefinition::signatureKey)

private fun indexEventsBySignature(
    events: List<WinRTEventDefinition>,
): Map<String, WinRTEventDefinition> =
    events.associateByTo(linkedMapOf(), WinRTEventDefinition::signatureKey)
