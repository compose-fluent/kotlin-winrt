package io.github.composefluent.winrt.metadata

data class WinRtMetadataProjectionInventory(
    val namespaces: List<WinRtNamespaceProjectionInventory>,
    val namespaceAdditions: List<WinRtNamespaceAddition>,
    val baseTypeMappings: List<WinRtBaseTypeMapping>,
    val eventSourceMappings: List<WinRtEventSourceMapping>,
    val authoredMetadataTypeMappings: List<WinRtAuthoredMetadataTypeMapping>,
    val genericAbiInventory: WinRtGenericAbiInventory,
    val helperOutputs: WinRtProjectionHelperOutputInventory,
) {
    val projectionFileWritten: Boolean
        get() = namespaces.any(WinRtNamespaceProjectionInventory::projectionFileWritten)
}

data class WinRtProjectionHelperOutputInventory(
    val eventHelpersFileName: String = "WinRTEventHelpers.cs",
    val eventHelpersRequired: Boolean,
    val baseTypeMappingHelperRequired: Boolean,
    val abiDelegateInitializerRequired: Boolean,
    val abiDelegateAsyncStatusRequired: Boolean,
    val genericTypeInstantiationsHelperRequired: Boolean,
    val authoringMetadataTypeMappingHelperRequired: Boolean,
    val baseStringHelpersRequired: Boolean,
    val comInteropHelpersRequired: Boolean,
    val namespaceAdditionsRequired: Boolean,
) {
    val requiredHelperFileNames: List<String>
        get() = buildList {
            if (eventHelpersRequired) add(eventHelpersFileName)
            if (baseTypeMappingHelperRequired) add("WinRTBaseTypeMappingHelper.cs")
            if (abiDelegateInitializerRequired) add("WinRTAbiDelegateInitializer.cs")
            if (genericTypeInstantiationsHelperRequired) add("WinRTGenericTypeInstantiations.cs")
            if (authoringMetadataTypeMappingHelperRequired) add("AuthoringMetadataTypeMappingHelper.cs")
            if (namespaceAdditionsRequired) add("WinRTNamespaceAdditions.kt")
        }
}

data class WinRtNamespaceAddition(
    val namespace: String,
    val kind: WinRtNamespaceAdditionKind = WinRtNamespaceAdditionKind.SourceAddition,
)

enum class WinRtNamespaceAdditionKind {
    SourceAddition,
    ComInteropAdapter,
}

object WinRtNamespaceAdditions {
    val all: List<WinRtNamespaceAddition> = listOf(
        WinRtNamespaceAddition("Microsoft.UI.Xaml"),
        WinRtNamespaceAddition("Microsoft.UI.Xaml.Controls.Primitives"),
        WinRtNamespaceAddition("Microsoft.UI.Xaml.Media"),
        WinRtNamespaceAddition("Microsoft.UI.Xaml.Media.Animation"),
        WinRtNamespaceAddition("Microsoft.UI.Xaml.Media.Media3D"),
        WinRtNamespaceAddition("Windows.Foundation"),
        WinRtNamespaceAddition("Windows.Storage"),
        WinRtNamespaceAddition("Windows.Storage.Streams"),
        WinRtNamespaceAddition("Windows.UI"),
        WinRtNamespaceAddition("Windows.UI.Xaml"),
        WinRtNamespaceAddition("Windows.UI.Xaml.Controls.Primitives"),
        WinRtNamespaceAddition("Windows.UI.Xaml.Media"),
        WinRtNamespaceAddition("Windows.UI.Xaml.Media.Animation"),
        WinRtNamespaceAddition("Windows.UI.Xaml.Media.Media3D"),
        WinRtNamespaceAddition("WinRT.Interop", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.ApplicationModel.DataTransfer", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.ApplicationModel.DataTransfer.DragDrop.Core", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.Graphics.Display", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.Graphics.Printing", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.Media", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.Media.PlayTo", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.Security.Authentication.Web.Core", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.Security.Credentials.UI", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.UI.ApplicationSettings", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.UI.Input", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.UI.Input.Core", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.UI.Input.Spatial", WinRtNamespaceAdditionKind.ComInteropAdapter),
        WinRtNamespaceAddition("Windows.UI.ViewManagement", WinRtNamespaceAdditionKind.ComInteropAdapter),
    ).sortedBy(WinRtNamespaceAddition::namespace)

    fun forNamespaces(namespaces: Iterable<String>, filter: WinRtMetadataFilter): List<WinRtNamespaceAddition> {
        val namespaceSet = namespaces.toSet()
        return all.filter { addition ->
            addition.namespace in namespaceSet && filter.includes(addition.namespace)
        }
    }
}

data class WinRtNamespaceProjectionInventory(
    val namespace: String,
    val classes: List<WinRtTypeDefinition>,
    val interfaces: List<WinRtTypeDefinition>,
    val enums: List<WinRtTypeDefinition>,
    val structs: List<WinRtTypeDefinition>,
    val delegates: List<WinRtTypeDefinition>,
    val types: List<WinRtTypeDefinition>,
    val projectedTypes: List<WinRtProjectedTypeInventory>,
    val skippedTypes: List<WinRtSkippedTypeInventory>,
    val additionsIncluded: Boolean,
    val requiresAbi: Boolean,
) {
    val projectionFileWritten: Boolean
        get() = projectedTypes.isNotEmpty() || skippedTypes.any { it.reason == WinRtSkippedTypeReason.MappedType } || additionsIncluded
}

data class WinRtProjectedTypeInventory(
    val type: WinRtTypeDefinition,
    val category: WinRtProjectionCategory,
    val requiresAbi: Boolean,
    val writesMetadataTypeEntry: Boolean,
    val projectedAttributes: List<WinRtProjectedAttributeDescriptor> = emptyList(),
)

enum class WinRtSkippedTypeReason {
    Filtered,
    MappedType,
    ApiContract,
    Attribute,
    Unsupported,
}

data class WinRtSkippedTypeInventory(
    val type: WinRtTypeDefinition,
    val reason: WinRtSkippedTypeReason,
)

data class WinRtBaseTypeMapping(
    val typeName: String,
    val baseTypeName: String,
)

data class WinRtEventSourceMapping(
    val eventTypeName: String,
    val sourceOwnerTypeName: String,
    val sourceClassName: String,
)

data class WinRtAuthoredMetadataTypeMapping(
    val projectedTypeName: String,
    val metadataTypeName: String,
)

class WinRtMetadataProjectionInventoryBuilder private constructor(
    private val model: WinRtMetadataModel,
    private val context: WinRtMetadataProjectionContext,
) {
    private val helpers = model.semanticHelpers()
    private val typeClassifier = model.typeClassifier()
    private val typesByQualifiedName = buildTypesByQualifiedName(model)

    fun build(): WinRtMetadataProjectionInventory {
        val baseTypeMappings = linkedMapOf<String, WinRtBaseTypeMapping>()
        val eventSourceMappings = linkedMapOf<String, WinRtEventSourceMapping>()
        val authoredMetadataTypeMappings = linkedMapOf<String, WinRtAuthoredMetadataTypeMapping>()
        val namespaces = model.namespaces.map { namespace ->
            buildNamespace(namespace, baseTypeMappings, eventSourceMappings, authoredMetadataTypeMappings)
        }
        val genericAbiInventory = helpers.genericAbiInventory(context)
        val projectionFileWritten = namespaces.any(WinRtNamespaceProjectionInventory::projectionFileWritten)
        val namespaceAdditions = WinRtNamespaceAdditions.forNamespaces(
            namespaces.map(WinRtNamespaceProjectionInventory::namespace),
            context.additionFilter,
        )
        return WinRtMetadataProjectionInventory(
            namespaces = namespaces,
            namespaceAdditions = namespaceAdditions,
            baseTypeMappings = baseTypeMappings.values.sortedBy(WinRtBaseTypeMapping::typeName),
            eventSourceMappings = eventSourceMappings.values.sortedBy(WinRtEventSourceMapping::eventTypeName),
            authoredMetadataTypeMappings = authoredMetadataTypeMappings.values.sortedBy(WinRtAuthoredMetadataTypeMapping::projectedTypeName),
            genericAbiInventory = genericAbiInventory,
            helperOutputs = WinRtProjectionHelperOutputInventory(
                eventHelpersRequired = true,
                baseTypeMappingHelperRequired = baseTypeMappings.isNotEmpty(),
                abiDelegateInitializerRequired = context.target == WinRtMetadataTarget.NetStandard20 &&
                    genericAbiInventory.genericAbiDelegates.isNotEmpty(),
                abiDelegateAsyncStatusRequired = context.target == WinRtMetadataTarget.NetStandard20 &&
                    genericAbiInventory.genericAbiDelegates.isNotEmpty() &&
                    context.filter.includes("Windows.Foundation.AsyncStatus"),
                genericTypeInstantiationsHelperRequired = context.target != WinRtMetadataTarget.NetStandard20 &&
                    genericAbiInventory.genericTypeInstantiations.isNotEmpty(),
                authoringMetadataTypeMappingHelperRequired = context.component && authoredMetadataTypeMappings.isNotEmpty(),
                baseStringHelpersRequired = projectionFileWritten,
                comInteropHelpersRequired = projectionFileWritten && context.filter.includes("Windows"),
                namespaceAdditionsRequired = namespaceAdditions.isNotEmpty(),
            ),
        )
    }

    private fun buildNamespace(
        namespace: WinRtNamespace,
        baseTypeMappings: MutableMap<String, WinRtBaseTypeMapping>,
        eventSourceMappings: MutableMap<String, WinRtEventSourceMapping>,
        authoredMetadataTypeMappings: MutableMap<String, WinRtAuthoredMetadataTypeMapping>,
    ): WinRtNamespaceProjectionInventory {
        val projectedTypes = mutableListOf<WinRtProjectedTypeInventory>()
        val skippedTypes = mutableListOf<WinRtSkippedTypeInventory>()
        namespace.types.forEach { type ->
            if (!context.filter.includes(type)) {
                skippedTypes += WinRtSkippedTypeInventory(type, WinRtSkippedTypeReason.Filtered)
                return@forEach
            }
            val classification = typeClassifier.classify(type)
            if (classification.mappedType != null) {
                skippedTypes += WinRtSkippedTypeInventory(type, WinRtSkippedTypeReason.MappedType)
                return@forEach
            }
            if (helpers.isApiContractType(type)) {
                skippedTypes += WinRtSkippedTypeInventory(type, WinRtSkippedTypeReason.ApiContract)
                projectedTypes += projected(type, classification, requiresAbi = false, writesMetadataTypeEntry = false)
                return@forEach
            }
            if (helpers.isAttributeType(type)) {
                skippedTypes += WinRtSkippedTypeInventory(type, WinRtSkippedTypeReason.Attribute)
                projectedTypes += projected(type, classification, requiresAbi = true, writesMetadataTypeEntry = false)
                collectClassEventSources(type, eventSourceMappings)
                return@forEach
            }
            when (type.kind) {
                WinRtTypeKind.RuntimeClass -> {
                    projectedTypes += projected(type, classification, requiresAbi = true, writesMetadataTypeEntry = shouldWriteMetadataTypeEntry(type))
                    addBaseTypeEntry(type, baseTypeMappings)
                    addMetadataTypeEntry(type, authoredMetadataTypeMappings)
                    collectClassEventSources(type, eventSourceMappings)
                }

                WinRtTypeKind.Delegate -> {
                    projectedTypes += projected(type, classification, requiresAbi = true, writesMetadataTypeEntry = shouldWriteMetadataTypeEntry(type))
                    addMetadataTypeEntry(type, authoredMetadataTypeMappings)
                }

                WinRtTypeKind.Enum -> {
                    projectedTypes += projected(type, classification, requiresAbi = false, writesMetadataTypeEntry = shouldWriteMetadataTypeEntry(type))
                    addMetadataTypeEntry(type, authoredMetadataTypeMappings)
                }

                WinRtTypeKind.Interface -> {
                    projectedTypes += projected(type, classification, requiresAbi = true, writesMetadataTypeEntry = shouldWriteMetadataTypeEntry(type))
                    collectInterfaceEventSources(type, eventSourceMappings)
                    addMetadataTypeEntry(type, authoredMetadataTypeMappings)
                }

                WinRtTypeKind.Struct -> {
                    val requiresAbi = !helpers.isTypeBlittable(type)
                    projectedTypes += projected(type, classification, requiresAbi = requiresAbi, writesMetadataTypeEntry = shouldWriteMetadataTypeEntry(type))
                    addMetadataTypeEntry(type, authoredMetadataTypeMappings)
                }

                WinRtTypeKind.Unknown ->
                    skippedTypes += WinRtSkippedTypeInventory(type, WinRtSkippedTypeReason.Unsupported)
            }
        }
        return WinRtNamespaceProjectionInventory(
            namespace = namespace.name,
            classes = namespace.types.filter { it.kind == WinRtTypeKind.RuntimeClass },
            interfaces = namespace.types.filter { it.kind == WinRtTypeKind.Interface },
            enums = namespace.types.filter { it.kind == WinRtTypeKind.Enum },
            structs = namespace.types.filter { it.kind == WinRtTypeKind.Struct },
            delegates = namespace.types.filter { it.kind == WinRtTypeKind.Delegate },
            types = namespace.types,
            projectedTypes = projectedTypes.sortedBy { it.type.name },
            skippedTypes = skippedTypes.sortedBy { it.type.name },
            additionsIncluded = context.additionFilter.includes(namespace.name),
            requiresAbi = projectedTypes.any(WinRtProjectedTypeInventory::requiresAbi),
        )
    }

    private fun projected(
        type: WinRtTypeDefinition,
        classification: WinRtTypeClassificationDescriptor,
        requiresAbi: Boolean,
        writesMetadataTypeEntry: Boolean,
    ): WinRtProjectedTypeInventory =
        WinRtProjectedTypeInventory(
            type = type,
            category = classification.projectionCategory,
            requiresAbi = requiresAbi,
            writesMetadataTypeEntry = writesMetadataTypeEntry,
            projectedAttributes = type.projectedAttributes(enablePlatformAttributes = context.target != WinRtMetadataTarget.NetStandard20),
        )

    private fun addBaseTypeEntry(
        type: WinRtTypeDefinition,
        mappings: MutableMap<String, WinRtBaseTypeMapping>,
    ) {
        val baseType = type.baseType ?: return
        val baseName = baseType.typeName
        if (baseName == "System.Object" || baseName == "Any") return
        mappings[type.qualifiedName] = WinRtBaseTypeMapping(type.qualifiedName, baseName)
    }

    private fun addMetadataTypeEntry(
        type: WinRtTypeDefinition,
        mappings: MutableMap<String, WinRtAuthoredMetadataTypeMapping>,
    ) {
        if (!shouldWriteMetadataTypeEntry(type)) return
        mappings[type.qualifiedName] = WinRtAuthoredMetadataTypeMapping(
            projectedTypeName = type.qualifiedName,
            metadataTypeName = "ABI.${type.qualifiedName}",
        )
    }

    private fun shouldWriteMetadataTypeEntry(type: WinRtTypeDefinition): Boolean {
        if (!context.component) return false
        if (type.kind == WinRtTypeKind.RuntimeClass && helpers.isStatic(type)) return false
        if (type.kind == WinRtTypeKind.Interface && helpers.isExclusiveTo(type)) return false
        return type.kind in setOf(WinRtTypeKind.RuntimeClass, WinRtTypeKind.Interface, WinRtTypeKind.Delegate, WinRtTypeKind.Enum, WinRtTypeKind.Struct)
    }

    private fun collectClassEventSources(
        type: WinRtTypeDefinition,
        mappings: MutableMap<String, WinRtEventSourceMapping>,
    ) {
        type.implementedInterfaces.forEach { implementation ->
            resolveDefinition(implementation.interfaceType, type.namespace)?.let { interfaceType ->
                collectInterfaceEventSources(interfaceType, mappings, ownerType = type)
            }
        }
    }

    private fun collectInterfaceEventSources(
        type: WinRtTypeDefinition,
        mappings: MutableMap<String, WinRtEventSourceMapping>,
        ownerType: WinRtTypeDefinition = type,
    ) {
        type.events.forEach { event ->
            val eventType = event.delegateType.normalized().typeName
            mappings.putIfAbsent(
                eventType,
                WinRtEventSourceMapping(
                    eventTypeName = eventType,
                    sourceOwnerTypeName = ownerType.qualifiedName,
                    sourceClassName = escapeTypeNameForIdentifier("_EventSource_$eventType"),
                ),
            )
        }
    }

    private fun resolveDefinition(type: WinRtTypeRef, currentNamespace: String): WinRtTypeDefinition? {
        val qualifiedName = type.normalized().qualifiedName ?: return null
        return typesByQualifiedName[qualifiedName]
            ?: if ('.' !in qualifiedName) typesByQualifiedName["$currentNamespace.$qualifiedName"] else null
    }

    companion object {
        fun create(
            model: WinRtMetadataModel,
            context: WinRtMetadataProjectionContext,
        ): WinRtMetadataProjectionInventoryBuilder =
            WinRtMetadataProjectionInventoryBuilder(model.normalized(), context)
    }
}

fun WinRtMetadataModel.projectionInventory(
    context: WinRtMetadataProjectionContext,
): WinRtMetadataProjectionInventory =
    WinRtMetadataProjectionInventoryBuilder.create(this, context).build()

private fun escapeTypeNameForIdentifier(typeName: String): String =
    typeName.replace(Regex("""[\s:<>,.]"""), "_")
