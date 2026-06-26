package io.github.composefluent.winrt.metadata

data class WinRTMetadataProjectionInventory(
    val namespaces: List<WinRTNamespaceProjectionInventory>,
    val namespaceAdditions: List<WinRTNamespaceAddition>,
    val baseTypeMappings: List<WinRTBaseTypeMapping>,
    val eventSourceMappings: List<WinRTEventSourceMapping>,
    val authoredMetadataTypeMappings: List<WinRTAuthoredMetadataTypeMapping>,
    val genericAbiInventory: WinRTGenericAbiInventory,
    val helperOutputs: WinRTProjectionHelperOutputInventory,
) {
    val projectionFileWritten: Boolean
        get() = namespaces.any(WinRTNamespaceProjectionInventory::projectionFileWritten)
}

data class WinRTProjectionHelperOutputInventory(
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

data class WinRTNamespaceAddition(
    val namespace: String,
    val kind: WinRTNamespaceAdditionKind = WinRTNamespaceAdditionKind.SourceAddition,
    val sourceFiles: List<String> = defaultNamespaceAdditionSourceFiles(namespace, kind),
)

enum class WinRTNamespaceAdditionKind {
    SourceAddition,
    ComInteropAdapter,
}

object WinRTNamespaceAdditions {
    val all: List<WinRTNamespaceAddition> = listOf(
        WinRTNamespaceAddition("Microsoft.UI.Xaml"),
        WinRTNamespaceAddition("Microsoft.UI.Xaml.Controls.Primitives"),
        WinRTNamespaceAddition("Microsoft.UI.Xaml.Media"),
        WinRTNamespaceAddition("Microsoft.UI.Xaml.Media.Animation"),
        WinRTNamespaceAddition("Microsoft.UI.Xaml.Media.Media3D"),
        WinRTNamespaceAddition("Windows.Foundation"),
        WinRTNamespaceAddition("Windows.Storage"),
        WinRTNamespaceAddition("Windows.Storage.Streams"),
        WinRTNamespaceAddition("Windows.UI"),
        WinRTNamespaceAddition("Windows.UI.Xaml"),
        WinRTNamespaceAddition("Windows.UI.Xaml.Controls.Primitives"),
        WinRTNamespaceAddition("Windows.UI.Xaml.Media"),
        WinRTNamespaceAddition("Windows.UI.Xaml.Media.Animation"),
        WinRTNamespaceAddition("Windows.UI.Xaml.Media.Media3D"),
        WinRTNamespaceAddition("WinRT.Interop", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.ApplicationModel.DataTransfer", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.ApplicationModel.DataTransfer.DragDrop.Core", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.Graphics.Display", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.Graphics.Printing", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.Media", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.Media.PlayTo", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.Security.Authentication.Web.Core", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.Security.Credentials.UI", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.UI.ApplicationSettings", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.UI.Input", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.UI.Input.Core", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.UI.Input.Spatial", WinRTNamespaceAdditionKind.ComInteropAdapter),
        WinRTNamespaceAddition("Windows.UI.ViewManagement", WinRTNamespaceAdditionKind.ComInteropAdapter),
    ).sortedBy(WinRTNamespaceAddition::namespace)

    fun forNamespaces(namespaces: Iterable<String>, filter: WinRTMetadataFilter): List<WinRTNamespaceAddition> {
        val namespaceSet = namespaces.toSet()
        return all.filter { addition ->
            addition.namespace in namespaceSet && filter.includes(addition.namespace)
        }
    }
}

private fun defaultNamespaceAdditionSourceFiles(
    namespace: String,
    kind: WinRTNamespaceAdditionKind,
): List<String> =
    when (kind) {
        WinRTNamespaceAdditionKind.SourceAddition -> namespaceSourceAdditionFiles(namespace)
        WinRTNamespaceAdditionKind.ComInteropAdapter -> listOf("interop/$namespace.kt")
    }

private fun namespaceSourceAdditionFiles(namespace: String): List<String> {
    val base = "strings/additions/$namespace"
    val commonFiles = when (namespace) {
        "Windows.Foundation" -> listOf(
            "AsyncInfo.kt",
            "AsyncInfoIdGenerator.kt",
            "ExceptionDispatchHelper.kt",
            "ITaskAwareAsyncInfo.kt",
            "TaskToAsyncActionAdapter.kt",
            "TaskToAsyncActionWithProgressAdapter.kt",
            "TaskToAsyncInfoAdapter.kt",
            "TaskToAsyncOperationAdapter.kt",
            "TaskToAsyncOperationWithProgressAdapter.kt",
            "Windows.Foundation.kt",
            "Windows.Foundation.SR.kt",
        )
        "Windows.Storage.Streams" -> listOf(
            "IBufferByteAccess.kt",
            "IMarshal.kt",
            "NetFxToWinRTStreamAdapter.kt",
            "StreamOperationAsyncResult.kt",
            "StreamOperationsImplementation.kt",
            "StreamTaskAdaptersImplementation.kt",
            "Windows.Storage.Streams.SR.kt",
            "WindowsRuntimeBuffer.kt",
            "WindowsRuntimeBufferExtensions.kt",
            "WindowsRuntimeMarshal.kt",
            "WindowsRuntimeStreamExtensions.kt",
            "WinRTIOHelper.kt",
            "WinRTToNetFxStreamAdapter.kt",
        )
        "Windows.Storage" -> listOf(
            "HANDLE_ACCESS_OPTIONS.kt",
            "HANDLE_CREATION_OPTIONS.kt",
            "HANDLE_OPTIONS.kt",
            "HANDLE_SHARING_OPTION.kt",
            "IStorageFolderHandleAccess.kt",
            "IStorageItemHandleAccess.kt",
            "Windows.Storage.SR.kt",
            "WindowsRuntimeStorageExtensions.kt",
        )
        "Microsoft.UI.Xaml", "Windows.UI.Xaml" -> listOf(
            "$namespace.kt",
            "$namespace.SR.kt",
        )
        "Windows.UI.Xaml.Controls.Primitives" -> return listOf(
            "strings/additions/Windows.UI.Xaml.Controls/Windows.UI.Xaml.Controls.Primitives.kt",
        )
        else -> listOf("$namespace.kt")
    }
    return commonFiles.map { fileName -> "$base/$fileName" }
}

data class WinRTNamespaceProjectionInventory(
    val namespace: String,
    val classes: List<WinRTTypeDefinition>,
    val interfaces: List<WinRTTypeDefinition>,
    val enums: List<WinRTTypeDefinition>,
    val structs: List<WinRTTypeDefinition>,
    val delegates: List<WinRTTypeDefinition>,
    val types: List<WinRTTypeDefinition>,
    val projectedTypes: List<WinRTProjectedTypeInventory>,
    val skippedTypes: List<WinRTSkippedTypeInventory>,
    val additionsIncluded: Boolean,
    val requiresAbi: Boolean,
) {
    val projectionFileWritten: Boolean
        get() = projectedTypes.isNotEmpty() || skippedTypes.any { it.reason == WinRTSkippedTypeReason.MappedType } || additionsIncluded
}

data class WinRTProjectedTypeInventory(
    val type: WinRTTypeDefinition,
    val category: WinRTProjectionCategory,
    val requiresAbi: Boolean,
    val writesMetadataTypeEntry: Boolean,
    val projectedAttributes: List<WinRTProjectedAttributeDescriptor> = emptyList(),
)

enum class WinRTSkippedTypeReason {
    Filtered,
    MappedType,
    ApiContract,
    Attribute,
    Unsupported,
}

data class WinRTSkippedTypeInventory(
    val type: WinRTTypeDefinition,
    val reason: WinRTSkippedTypeReason,
)

data class WinRTBaseTypeMapping(
    val typeName: String,
    val baseTypeName: String,
)

data class WinRTEventSourceMapping(
    val eventTypeName: String,
    val sourceOwnerTypeName: String,
    val sourceClassName: String,
)

data class WinRTAuthoredMetadataTypeMapping(
    val projectedTypeName: String,
    val metadataTypeName: String,
)

class WinRTMetadataProjectionInventoryBuilder private constructor(
    private val model: WinRTMetadataModel,
    private val context: WinRTMetadataProjectionContext,
) {
    private val helpers = model.semanticHelpers()
    private val typeClassifier = model.typeClassifier()
    private val typesByQualifiedName = buildTypesByQualifiedName(model)

    fun build(): WinRTMetadataProjectionInventory {
        val baseTypeMappings = linkedMapOf<String, WinRTBaseTypeMapping>()
        val eventSourceMappings = linkedMapOf<String, WinRTEventSourceMapping>()
        val authoredMetadataTypeMappings = linkedMapOf<String, WinRTAuthoredMetadataTypeMapping>()
        val namespaces = model.namespaces.map { namespace ->
            buildNamespace(namespace, baseTypeMappings, eventSourceMappings, authoredMetadataTypeMappings)
        }
        val genericAbiInventory = helpers.genericAbiInventory(context)
        val projectionFileWritten = namespaces.any(WinRTNamespaceProjectionInventory::projectionFileWritten)
        val namespaceAdditions = WinRTNamespaceAdditions.forNamespaces(
            namespaces.map(WinRTNamespaceProjectionInventory::namespace),
            context.additionFilter,
        )
        return WinRTMetadataProjectionInventory(
            namespaces = namespaces,
            namespaceAdditions = namespaceAdditions,
            baseTypeMappings = baseTypeMappings.values.sortedBy(WinRTBaseTypeMapping::typeName),
            eventSourceMappings = eventSourceMappings.values.sortedBy(WinRTEventSourceMapping::eventTypeName),
            authoredMetadataTypeMappings = authoredMetadataTypeMappings.values.sortedBy(WinRTAuthoredMetadataTypeMapping::projectedTypeName),
            genericAbiInventory = genericAbiInventory,
            helperOutputs = WinRTProjectionHelperOutputInventory(
                eventHelpersRequired = true,
                baseTypeMappingHelperRequired = baseTypeMappings.isNotEmpty(),
                abiDelegateInitializerRequired = context.target == WinRTMetadataTarget.NetStandard20 &&
                    genericAbiInventory.genericAbiDelegates.isNotEmpty(),
                abiDelegateAsyncStatusRequired = context.target == WinRTMetadataTarget.NetStandard20 &&
                    genericAbiInventory.genericAbiDelegates.isNotEmpty() &&
                    context.filter.includes("Windows.Foundation.AsyncStatus"),
                genericTypeInstantiationsHelperRequired = context.target != WinRTMetadataTarget.NetStandard20 &&
                    genericAbiInventory.genericTypeInstantiations.isNotEmpty(),
                authoringMetadataTypeMappingHelperRequired = context.component && authoredMetadataTypeMappings.isNotEmpty(),
                baseStringHelpersRequired = projectionFileWritten,
                comInteropHelpersRequired = projectionFileWritten && context.filter.includes("Windows"),
                namespaceAdditionsRequired = namespaceAdditions.isNotEmpty(),
            ),
        )
    }

    private fun buildNamespace(
        namespace: WinRTNamespace,
        baseTypeMappings: MutableMap<String, WinRTBaseTypeMapping>,
        eventSourceMappings: MutableMap<String, WinRTEventSourceMapping>,
        authoredMetadataTypeMappings: MutableMap<String, WinRTAuthoredMetadataTypeMapping>,
    ): WinRTNamespaceProjectionInventory {
        val projectedTypes = mutableListOf<WinRTProjectedTypeInventory>()
        val skippedTypes = mutableListOf<WinRTSkippedTypeInventory>()
        namespace.types.forEach { type ->
            if (!context.filter.includes(type)) {
                skippedTypes += WinRTSkippedTypeInventory(type, WinRTSkippedTypeReason.Filtered)
                return@forEach
            }
            val classification = typeClassifier.classify(type)
            if (classification.mappedType != null) {
                skippedTypes += WinRTSkippedTypeInventory(type, WinRTSkippedTypeReason.MappedType)
                return@forEach
            }
            if (helpers.isApiContractType(type)) {
                skippedTypes += WinRTSkippedTypeInventory(type, WinRTSkippedTypeReason.ApiContract)
                projectedTypes += projected(type, classification, requiresAbi = false, writesMetadataTypeEntry = false)
                return@forEach
            }
            if (helpers.isAttributeType(type)) {
                skippedTypes += WinRTSkippedTypeInventory(type, WinRTSkippedTypeReason.Attribute)
                projectedTypes += projected(type, classification, requiresAbi = true, writesMetadataTypeEntry = false)
                collectClassEventSources(type, eventSourceMappings)
                return@forEach
            }
            when (type.kind) {
                WinRTTypeKind.RuntimeClass -> {
                    projectedTypes += projected(type, classification, requiresAbi = true, writesMetadataTypeEntry = shouldWriteMetadataTypeEntry(type))
                    addBaseTypeEntry(type, baseTypeMappings)
                    addMetadataTypeEntry(type, authoredMetadataTypeMappings)
                    collectClassEventSources(type, eventSourceMappings)
                }

                WinRTTypeKind.Delegate -> {
                    projectedTypes += projected(type, classification, requiresAbi = true, writesMetadataTypeEntry = shouldWriteMetadataTypeEntry(type))
                    addMetadataTypeEntry(type, authoredMetadataTypeMappings)
                }

                WinRTTypeKind.Enum -> {
                    projectedTypes += projected(type, classification, requiresAbi = false, writesMetadataTypeEntry = shouldWriteMetadataTypeEntry(type))
                    addMetadataTypeEntry(type, authoredMetadataTypeMappings)
                }

                WinRTTypeKind.Interface -> {
                    projectedTypes += projected(type, classification, requiresAbi = true, writesMetadataTypeEntry = shouldWriteMetadataTypeEntry(type))
                    collectInterfaceEventSources(type, eventSourceMappings)
                    addMetadataTypeEntry(type, authoredMetadataTypeMappings)
                }

                WinRTTypeKind.Struct -> {
                    val requiresAbi = !helpers.isTypeBlittable(type)
                    projectedTypes += projected(type, classification, requiresAbi = requiresAbi, writesMetadataTypeEntry = shouldWriteMetadataTypeEntry(type))
                    addMetadataTypeEntry(type, authoredMetadataTypeMappings)
                }

                WinRTTypeKind.Unknown ->
                    skippedTypes += WinRTSkippedTypeInventory(type, WinRTSkippedTypeReason.Unsupported)
            }
        }
        return WinRTNamespaceProjectionInventory(
            namespace = namespace.name,
            classes = namespace.types.filter { it.kind == WinRTTypeKind.RuntimeClass },
            interfaces = namespace.types.filter { it.kind == WinRTTypeKind.Interface },
            enums = namespace.types.filter { it.kind == WinRTTypeKind.Enum },
            structs = namespace.types.filter { it.kind == WinRTTypeKind.Struct },
            delegates = namespace.types.filter { it.kind == WinRTTypeKind.Delegate },
            types = namespace.types,
            projectedTypes = projectedTypes.sortedBy { it.type.name },
            skippedTypes = skippedTypes.sortedBy { it.type.name },
            additionsIncluded = context.additionFilter.includes(namespace.name),
            requiresAbi = projectedTypes.any(WinRTProjectedTypeInventory::requiresAbi),
        )
    }

    private fun projected(
        type: WinRTTypeDefinition,
        classification: WinRTTypeClassificationDescriptor,
        requiresAbi: Boolean,
        writesMetadataTypeEntry: Boolean,
    ): WinRTProjectedTypeInventory =
        WinRTProjectedTypeInventory(
            type = type,
            category = classification.projectionCategory,
            requiresAbi = requiresAbi,
            writesMetadataTypeEntry = writesMetadataTypeEntry,
            projectedAttributes = type.projectedAttributes(enablePlatformAttributes = context.target != WinRTMetadataTarget.NetStandard20),
        )

    private fun addBaseTypeEntry(
        type: WinRTTypeDefinition,
        mappings: MutableMap<String, WinRTBaseTypeMapping>,
    ) {
        val baseType = type.baseType ?: return
        val baseName = baseType.typeName
        if (isWinRTObjectTypeName(baseName)) return
        mappings[type.qualifiedName] = WinRTBaseTypeMapping(type.qualifiedName, baseName)
    }

    private fun addMetadataTypeEntry(
        type: WinRTTypeDefinition,
        mappings: MutableMap<String, WinRTAuthoredMetadataTypeMapping>,
    ) {
        if (!shouldWriteMetadataTypeEntry(type)) return
        mappings[type.qualifiedName] = WinRTAuthoredMetadataTypeMapping(
            projectedTypeName = type.qualifiedName,
            metadataTypeName = "ABI.${type.qualifiedName}",
        )
    }

    private fun shouldWriteMetadataTypeEntry(type: WinRTTypeDefinition): Boolean {
        if (!context.component) return false
        if (type.kind == WinRTTypeKind.RuntimeClass && helpers.isStatic(type)) return false
        if (type.kind == WinRTTypeKind.Interface && helpers.isExclusiveTo(type)) return false
        return type.kind in setOf(WinRTTypeKind.RuntimeClass, WinRTTypeKind.Interface, WinRTTypeKind.Delegate, WinRTTypeKind.Enum, WinRTTypeKind.Struct)
    }

    private fun collectClassEventSources(
        type: WinRTTypeDefinition,
        mappings: MutableMap<String, WinRTEventSourceMapping>,
    ) {
        type.implementedInterfaces.forEach { implementation ->
            resolveDefinition(implementation.interfaceType, type.namespace)?.let { interfaceType ->
                collectInterfaceEventSources(interfaceType, mappings, ownerType = type)
            }
        }
    }

    private fun collectInterfaceEventSources(
        type: WinRTTypeDefinition,
        mappings: MutableMap<String, WinRTEventSourceMapping>,
        ownerType: WinRTTypeDefinition = type,
    ) {
        type.events.forEach { event ->
            val eventType = event.delegateType.normalized().typeName
            mappings.putIfAbsent(
                eventType,
                WinRTEventSourceMapping(
                    eventTypeName = eventType,
                    sourceOwnerTypeName = ownerType.qualifiedName,
                    sourceClassName = escapeTypeNameForIdentifier("_EventSource_$eventType"),
                ),
            )
        }
    }

    private fun resolveDefinition(type: WinRTTypeRef, currentNamespace: String): WinRTTypeDefinition? {
        val qualifiedName = type.normalized().qualifiedName ?: return null
        return typesByQualifiedName[qualifiedName]
            ?: if ('.' !in qualifiedName) typesByQualifiedName["$currentNamespace.$qualifiedName"] else null
    }

    companion object {
        fun create(
            model: WinRTMetadataModel,
            context: WinRTMetadataProjectionContext,
        ): WinRTMetadataProjectionInventoryBuilder =
            WinRTMetadataProjectionInventoryBuilder(model.normalized(), context)
    }
}

fun WinRTMetadataModel.projectionInventory(
    context: WinRTMetadataProjectionContext,
): WinRTMetadataProjectionInventory =
    WinRTMetadataProjectionInventoryBuilder.create(this, context).build()

private fun escapeTypeNameForIdentifier(typeName: String): String =
    typeName.replace(Regex("""[\s:<>,.]"""), "_")
