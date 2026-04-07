package dev.winrt.winmd.plugin

import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
data class WinMdModel(
    val files: List<WinMdFile>,
    val namespaces: List<WinMdNamespace>,
)

@Serializable
data class WinMdFile(
    val path: String,
    val size: Long,
    val portableExecutable: Boolean,
)

@Serializable
data class WinMdNamespace(
    val name: String,
    val types: List<WinMdType>,
)

@Serializable
data class WinMdType(
    val namespace: String,
    val name: String,
    val kind: WinMdTypeKind,
    val guid: String? = null,
    val genericParameters: List<String> = emptyList(),
    val baseClass: String? = null,
    val defaultInterface: String? = null,
    val implementedInterfaces: List<String> = emptyList(),
    val baseInterfaces: List<String> = emptyList(),
    val activationKind: WinMdActivationKind = WinMdActivationKind.Factory,
    val hasActivatableAttribute: Boolean = false,
    val activatableFactoryInterfaces: List<String> = emptyList(),
    val staticInterfaces: List<String> = emptyList(),
    val composableInterfaces: List<WinMdComposableInterface> = emptyList(),
    val activationFunctionName: String = "activate",
    val fields: List<WinMdField> = emptyList(),
    val enumUnderlyingType: String? = null,
    val enumMembers: List<WinMdEnumMember> = emptyList(),
    val methods: List<WinMdMethod> = emptyList(),
    val properties: List<WinMdProperty> = emptyList(),
)

@Serializable
data class WinMdComposableInterface(
    val type: String,
    val visible: Boolean = false,
)

@Serializable
enum class WinMdActivationKind {
    Factory,
    Composable,
}

@Serializable
enum class WinMdTypeKind {
    Interface,
    Delegate,
    RuntimeClass,
    Struct,
    Enum,
}

@Serializable
data class WinMdMethod(
    val name: String,
    val returnType: String,
    val vtableIndex: Int? = null,
    val parameters: List<WinMdParameter> = emptyList(),
    val sourceInterface: String? = null,
) {
    val signatureKey: String
        get() = buildString {
            append(name)
            append('(')
            append(
                parameters.joinToString(",") { parameter ->
                    buildString {
                        append(parameter.type)
                        if (parameter.byRef) {
                            append('&')
                        }
                        if (parameter.isIn) {
                            append(":in")
                        }
                        if (parameter.isOut) {
                            append(":out")
                        }
                    }
                },
            )
            append("):")
            append(returnType)
        }
}

@Serializable
data class WinMdParameter(
    val name: String,
    val type: String,
    val byRef: Boolean = false,
    val isOut: Boolean = false,
    val isIn: Boolean = false,
)

@Serializable
data class WinMdProperty(
    val name: String,
    val type: String,
    val mutable: Boolean,
    val getterVtableIndex: Int? = null,
    val setterVtableIndex: Int? = null,
)

@Serializable
data class WinMdField(
    val name: String,
    val type: String,
)

@Serializable
data class WinMdEnumMember(
    val name: String,
    val value: Int,
)

object WinMdModelFactory {
    fun minimalModel(sourceFiles: List<Path>): WinMdModel {
        val fileInfo = sourceFiles.map { path ->
            val peInfo = PortableExecutableReader.inspect(path)
            WinMdFile(
                path = path.toString(),
                size = peInfo.size,
                portableExecutable = peInfo.isPortableExecutable,
            )
        }

        return WinMdModel(
            files = fileInfo,
            namespaces = listOf(
                WinMdNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinMdType(
                            namespace = "Windows.Foundation",
                            name = "IStringable",
                            kind = WinMdTypeKind.Interface,
                            guid = "96369f54-8eb6-48f0-abce-c1b211e627c3",
                            methods = listOf(
                                WinMdMethod(
                                    name = "ToString",
                                    returnType = "String",
                                    vtableIndex = 6,
                                ),
                            ),
                        ),
                        WinMdType(
                            namespace = "Windows.Foundation",
                            name = "Point",
                            kind = WinMdTypeKind.Struct,
                            fields = listOf(
                                WinMdField("X", "Float64"),
                                WinMdField("Y", "Float64"),
                            ),
                        ),
                        WinMdType(
                            namespace = "Windows.Foundation",
                            name = "AsyncStatus",
                            kind = WinMdTypeKind.Enum,
                            enumMembers = listOf(
                                WinMdEnumMember("Started", 0),
                                WinMdEnumMember("Completed", 1),
                                WinMdEnumMember("Canceled", 2),
                                WinMdEnumMember("Error", 3),
                            ),
                        ),
                    ),
                ),
                WinMdNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "ApplicationHighContrastAdjustment",
                            kind = WinMdTypeKind.Enum,
                        ),
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Application",
                            kind = WinMdTypeKind.RuntimeClass,
                            defaultInterface = "Microsoft.UI.Xaml.IApplication",
                            activationKind = WinMdActivationKind.Factory,
                            methods = listOf(
                                WinMdMethod("Start", "Unit", vtableIndex = 6),
                                WinMdMethod("GetLaunchCount", "UInt32", vtableIndex = 7),
                            ),
                        ),
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "ApplicationInitializationCallbackParams",
                            kind = WinMdTypeKind.RuntimeClass,
                            defaultInterface = "Microsoft.UI.Xaml.IApplicationInitializationCallbackParams",
                            activationKind = WinMdActivationKind.Factory,
                        ),
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "ApplicationTheme",
                            kind = WinMdTypeKind.Enum,
                        ),
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "ApplicationRequiresPointerMode",
                            kind = WinMdTypeKind.Enum,
                        ),
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IApplicationInitializationCallbackParams",
                            kind = WinMdTypeKind.Interface,
                            guid = "1b1906ea-5b7b-5876-81ab-7c2281ac3d20",
                        ),
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Window",
                            kind = WinMdTypeKind.RuntimeClass,
                            defaultInterface = "Microsoft.UI.Xaml.IWindow",
                            activationKind = WinMdActivationKind.Factory,
                            activationFunctionName = "activateInstance",
                            methods = listOf(
                                WinMdMethod("Activate", "Unit", vtableIndex = 13),
                            ),
                            properties = listOf(
                                WinMdProperty("Title", "String", mutable = true, getterVtableIndex = 6, setterVtableIndex = 7),
                                WinMdProperty("IsVisible", "Boolean", mutable = false, getterVtableIndex = 8),
                                WinMdProperty("CreatedAt", "DateTime", mutable = false, getterVtableIndex = 10),
                                WinMdProperty("Lifetime", "TimeSpan", mutable = false, getterVtableIndex = 11),
                                WinMdProperty("LastToken", "EventRegistrationToken", mutable = false, getterVtableIndex = 12),
                                WinMdProperty("StableId", "Guid", mutable = false, getterVtableIndex = 9),
                                WinMdProperty("OptionalTitle", "IReference<String>", mutable = false, getterVtableIndex = 14),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    fun metadataModel(sourceFiles: List<Path>): WinMdModel {
        return expandSpecializedInterfaceMembers(
            inferInterfaceSlots(WinMdMetadataReader.readModel(sourceFiles)),
        )
    }

    fun sampleSupplementalModel(): WinMdModel {
        return WinMdModel(
            files = emptyList(),
            namespaces = listOf(
                WinMdNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinMdType(
                            namespace = "Windows.Foundation",
                            name = "IStringable",
                            kind = WinMdTypeKind.Interface,
                            guid = "96369f54-8eb6-48f0-abce-c1b211e627c3",
                            methods = listOf(
                                WinMdMethod(
                                    name = "ToString",
                                    returnType = "String",
                                    vtableIndex = 6,
                                ),
                            ),
                        ),
                        WinMdType(
                            namespace = "Windows.Foundation",
                            name = "Point",
                            kind = WinMdTypeKind.Struct,
                            fields = listOf(
                                WinMdField("X", "Float64"),
                                WinMdField("Y", "Float64"),
                            ),
                        ),
                        WinMdType(
                            namespace = "Windows.Foundation",
                            name = "AsyncStatus",
                            kind = WinMdTypeKind.Enum,
                            enumMembers = listOf(
                                WinMdEnumMember("Started", 0),
                                WinMdEnumMember("Completed", 1),
                                WinMdEnumMember("Canceled", 2),
                                WinMdEnumMember("Error", 3),
                            ),
                        ),
                    ),
                ),
                WinMdNamespace(
                    name = "Windows.Data.Json",
                    types = listOf(
                        WinMdType(
                            namespace = "Windows.Data.Json",
                            name = "IJsonValue",
                            kind = WinMdTypeKind.Interface,
                            guid = "a3219ecb-f0b3-4dcd-beee-19d48cd3ed1e",
                            properties = listOf(
                                WinMdProperty(
                                    name = "ValueType",
                                    type = "Windows.Data.Json.JsonValueType",
                                    mutable = false,
                                    getterVtableIndex = 6,
                                ),
                            ),
                            methods = listOf(
                                WinMdMethod(
                                    name = "Get_ValueType",
                                    returnType = "Windows.Data.Json.JsonValueType",
                                    vtableIndex = 6,
                                ),
                                WinMdMethod(
                                    name = "Stringify",
                                    returnType = "String",
                                    vtableIndex = 7,
                                ),
                                WinMdMethod(
                                    name = "GetString",
                                    returnType = "String",
                                    vtableIndex = 8,
                                ),
                                WinMdMethod(
                                    name = "GetNumber",
                                    returnType = "Float64",
                                    vtableIndex = 9,
                                ),
                                WinMdMethod(
                                    name = "GetBoolean",
                                    returnType = "Boolean",
                                    vtableIndex = 10,
                                ),
                                WinMdMethod(
                                    name = "GetObject",
                                    returnType = "Windows.Data.Json.JsonObject",
                                    vtableIndex = 12,
                                ),
                                WinMdMethod(
                                    name = "GetArray",
                                    returnType = "Windows.Data.Json.JsonArray",
                                    vtableIndex = 11,
                                ),
                            ),
                        ),
                        WinMdType(
                            namespace = "Windows.Data.Json",
                            name = "IJsonArray",
                            kind = WinMdTypeKind.Interface,
                            guid = "08c1ddb6-0cbd-4a9a-b5d3-2f852dc37e81",
                            methods = listOf(
                                WinMdMethod(
                                    name = "GetObjectAt",
                                    returnType = "Windows.Data.Json.JsonObject",
                                    vtableIndex = 6,
                                    parameters = listOf(
                                        WinMdParameter("index", "UInt32"),
                                    ),
                                ),
                                WinMdMethod(
                                    name = "GetArrayAt",
                                    returnType = "Windows.Data.Json.JsonArray",
                                    vtableIndex = 7,
                                    parameters = listOf(
                                        WinMdParameter("index", "UInt32"),
                                    ),
                                ),
                                WinMdMethod(
                                    name = "GetStringAt",
                                    returnType = "String",
                                    vtableIndex = 8,
                                    parameters = listOf(
                                        WinMdParameter("index", "UInt32"),
                                    ),
                                ),
                                WinMdMethod(
                                    name = "GetNumberAt",
                                    returnType = "Float64",
                                    vtableIndex = 9,
                                    parameters = listOf(
                                        WinMdParameter("index", "UInt32"),
                                    ),
                                ),
                                WinMdMethod(
                                    name = "GetBooleanAt",
                                    returnType = "Boolean",
                                    vtableIndex = 10,
                                    parameters = listOf(
                                        WinMdParameter("index", "UInt32"),
                                    ),
                                ),
                            ),
                        ),
                        WinMdType(
                            namespace = "Windows.Data.Json",
                            name = "IJsonObject",
                            kind = WinMdTypeKind.Interface,
                            guid = "064e24dd-29c2-4f83-9ac1-9ee11578beb3",
                            methods = listOf(
                                WinMdMethod(
                                    name = "GetNamedValue",
                                    returnType = "Windows.Data.Json.IJsonValue",
                                    vtableIndex = 6,
                                    parameters = listOf(
                                        WinMdParameter("name", "String"),
                                    ),
                                ),
                                WinMdMethod(
                                    name = "GetNamedString",
                                    returnType = "String",
                                    vtableIndex = 10,
                                    parameters = listOf(
                                        WinMdParameter("name", "String"),
                                    ),
                                ),
                                WinMdMethod(
                                    name = "GetNamedObject",
                                    returnType = "Windows.Data.Json.JsonObject",
                                    vtableIndex = 8,
                                    parameters = listOf(
                                        WinMdParameter("name", "String"),
                                    ),
                                ),
                                WinMdMethod(
                                    name = "GetNamedArray",
                                    returnType = "Windows.Data.Json.JsonArray",
                                    vtableIndex = 9,
                                    parameters = listOf(
                                        WinMdParameter("name", "String"),
                                    ),
                                ),
                                WinMdMethod(
                                    name = "GetNamedNumber",
                                    returnType = "Float64",
                                    vtableIndex = 11,
                                    parameters = listOf(
                                        WinMdParameter("name", "String"),
                                    ),
                                ),
                                WinMdMethod(
                                    name = "GetNamedBoolean",
                                    returnType = "Boolean",
                                    vtableIndex = 12,
                                    parameters = listOf(
                                        WinMdParameter("name", "String"),
                                    ),
                                ),
                            ),
                        ),
                        WinMdType(
                            namespace = "Windows.Data.Json",
                            name = "JsonValueType",
                            kind = WinMdTypeKind.Enum,
                            enumMembers = listOf(
                                WinMdEnumMember("Null", 0),
                                WinMdEnumMember("Boolean", 1),
                                WinMdEnumMember("Number", 2),
                                WinMdEnumMember("String", 3),
                                WinMdEnumMember("Array", 4),
                                WinMdEnumMember("Object", 5),
                            ),
                        ),
                        WinMdType(
                            namespace = "Windows.Data.Json",
                            name = "JsonArray",
                            kind = WinMdTypeKind.RuntimeClass,
                            defaultInterface = "Windows.Data.Json.IJsonArray",
                        ),
                    ),
                ),
                WinMdNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Application",
                            kind = WinMdTypeKind.RuntimeClass,
                            defaultInterface = "Microsoft.UI.Xaml.IApplication",
                            activationKind = WinMdActivationKind.Factory,
                            methods = listOf(
                                WinMdMethod("Start", "Unit", vtableIndex = 6),
                                WinMdMethod("GetLaunchCount", "UInt32", vtableIndex = 7),
                            ),
                        ),
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "ApplicationTheme",
                            kind = WinMdTypeKind.Enum,
                        ),
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "ApplicationRequiresPointerMode",
                            kind = WinMdTypeKind.Enum,
                        ),
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "ApplicationInitializationCallbackParams",
                            kind = WinMdTypeKind.RuntimeClass,
                            defaultInterface = "Microsoft.UI.Xaml.IApplicationInitializationCallbackParams",
                            activationKind = WinMdActivationKind.Factory,
                        ),
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IApplicationInitializationCallbackParams",
                            kind = WinMdTypeKind.Interface,
                            guid = "1b1906ea-5b7b-5876-81ab-7c2281ac3d20",
                        ),
                        WinMdType(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Window",
                            kind = WinMdTypeKind.RuntimeClass,
                            defaultInterface = "Microsoft.UI.Xaml.IWindow",
                            activationKind = WinMdActivationKind.Factory,
                            activationFunctionName = "activateInstance",
                            methods = listOf(
                                WinMdMethod("Activate", "Unit", vtableIndex = 13),
                            ),
                            properties = listOf(
                                WinMdProperty("Title", "String", mutable = true, getterVtableIndex = 6, setterVtableIndex = 7),
                                WinMdProperty("IsVisible", "Boolean", mutable = false, getterVtableIndex = 8),
                                WinMdProperty("CreatedAt", "DateTime", mutable = false, getterVtableIndex = 10),
                                WinMdProperty("Lifetime", "TimeSpan", mutable = false, getterVtableIndex = 11),
                                WinMdProperty("LastToken", "EventRegistrationToken", mutable = false, getterVtableIndex = 12),
                                WinMdProperty("StableId", "Guid", mutable = false, getterVtableIndex = 9),
                                WinMdProperty("OptionalTitle", "IReference<String>", mutable = false, getterVtableIndex = 14),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    fun merge(primary: WinMdModel, supplemental: WinMdModel): WinMdModel {
        val mergedFiles = primary.files + supplemental.files
        val mergedNamespaces = (primary.namespaces + supplemental.namespaces)
            .groupBy(WinMdNamespace::name)
            .toSortedMap()
            .map { (namespaceName, namespaceGroup) ->
                val mergedTypes = namespaceGroup
                    .flatMap(WinMdNamespace::types)
                    .groupBy { "${it.namespace}.${it.name}" }
                    .map { (_, types) ->
                        types.drop(1).fold(types.first(), ::mergeType)
                    }
                    .sortedBy(WinMdType::name)
                WinMdNamespace(
                    name = namespaceName,
                    types = mergedTypes,
                )
            }

        return expandSpecializedInterfaceMembers(
            WinMdModel(
                files = mergedFiles,
                namespaces = mergedNamespaces,
            ),
        )
    }

    private fun inferInterfaceSlots(model: WinMdModel): WinMdModel {
        return model.copy(
            namespaces = model.namespaces.map { namespace ->
                namespace.copy(
                    types = namespace.types.map { type ->
                        if (type.kind != WinMdTypeKind.Interface) {
                            type
                        } else {
                            type.copy(
                                methods = type.methods.map { method ->
                                    if (method.vtableIndex != null) {
                                        method
                                    } else {
                                        method.copy(
                                            vtableIndex = InterfaceVtableResolver.inferMethodSlot(type, model, method),
                                        )
                                    }
                                },
                                properties = type.properties.map { property ->
                                    property.copy(
                                        getterVtableIndex = property.getterVtableIndex ?: type.methods
                                            .firstOrNull { it.name == "get_${property.name}" }
                                            ?.let { method -> InterfaceVtableResolver.inferMethodSlot(type, model, method) },
                                        setterVtableIndex = property.setterVtableIndex ?: type.methods
                                            .firstOrNull { it.name == "put_${property.name}" }
                                            ?.let { method -> InterfaceVtableResolver.inferMethodSlot(type, model, method) },
                                    )
                                },
                            )
                        }
                    },
                )
            },
        )
    }

    private fun mergeType(primary: WinMdType, supplemental: WinMdType): WinMdType {
        require(primary.namespace == supplemental.namespace && primary.name == supplemental.name) {
            "Cannot merge different types: ${primary.namespace}.${primary.name} vs ${supplemental.namespace}.${supplemental.name}"
        }

        val preferSupplementalJsonInterfaceSurface = supplemental.namespace == "Windows.Data.Json" &&
            supplemental.kind == WinMdTypeKind.Interface &&
            supplemental.methods.isNotEmpty()

        return primary.copy(
            guid = primary.guid ?: supplemental.guid,
            genericParameters = if (primary.genericParameters.isNotEmpty()) primary.genericParameters else supplemental.genericParameters,
            baseClass = primary.baseClass ?: supplemental.baseClass,
            defaultInterface = primary.defaultInterface ?: supplemental.defaultInterface,
            implementedInterfaces = if (primary.implementedInterfaces.isNotEmpty()) primary.implementedInterfaces else supplemental.implementedInterfaces,
            baseInterfaces = if (primary.baseInterfaces.isNotEmpty()) primary.baseInterfaces else supplemental.baseInterfaces,
            activationKind = primary.activationKind,
            hasActivatableAttribute = primary.hasActivatableAttribute || supplemental.hasActivatableAttribute,
            activatableFactoryInterfaces = if (primary.activatableFactoryInterfaces.isNotEmpty()) {
                primary.activatableFactoryInterfaces
            } else {
                supplemental.activatableFactoryInterfaces
            },
            staticInterfaces = if (primary.staticInterfaces.isNotEmpty()) primary.staticInterfaces else supplemental.staticInterfaces,
            composableInterfaces = if (primary.composableInterfaces.isNotEmpty()) {
                primary.composableInterfaces
            } else {
                supplemental.composableInterfaces
            },
            activationFunctionName = primary.activationFunctionName.takeIf { it != "activate" } ?: supplemental.activationFunctionName,
            fields = if (primary.fields.isNotEmpty()) primary.fields else supplemental.fields,
            enumUnderlyingType = primary.enumUnderlyingType ?: supplemental.enumUnderlyingType,
            enumMembers = if (primary.enumMembers.isNotEmpty()) primary.enumMembers else supplemental.enumMembers,
            methods = if (preferSupplementalJsonInterfaceSurface) supplemental.methods else mergeMethods(primary.methods, supplemental.methods),
            properties = if (preferSupplementalJsonInterfaceSurface) supplemental.properties else mergeProperties(primary.properties, supplemental.properties),
        )
    }

    private fun mergeMethods(primary: List<WinMdMethod>, supplemental: List<WinMdMethod>): List<WinMdMethod> {
        if (primary.isEmpty()) return supplemental
        if (supplemental.isEmpty()) return primary
        val supplementalBySignature = supplemental.associateBy(WinMdMethod::signatureKey)
        val mergedPrimary = primary.map { method ->
            supplementalBySignature[method.signatureKey]?.let { mergeMethod(method, it) } ?: method
        }
        val existingSignatures = primary.mapTo(linkedSetOf(), WinMdMethod::signatureKey)
        val appended = supplemental.filterNot { it.signatureKey in existingSignatures }
        return mergedPrimary + appended
    }

    private fun mergeMethod(primary: WinMdMethod, supplemental: WinMdMethod): WinMdMethod {
        return primary.copy(
            returnType = primary.returnType.takeIf { it != "UnknownType" } ?: supplemental.returnType,
            vtableIndex = primary.vtableIndex ?: supplemental.vtableIndex,
            parameters = if (primary.parameters.isNotEmpty()) primary.parameters else supplemental.parameters,
        )
    }

    private fun mergeRuntimeClassMethods(primary: List<WinMdMethod>, supplemental: List<WinMdMethod>): List<WinMdMethod> {
        if (primary.isEmpty()) return supplemental
        if (supplemental.isEmpty()) return primary
        val preferred = primary.asReversed().distinctBy(::runtimeMethodMergeKey).asReversed()
        val existingKeys = preferred.mapTo(linkedSetOf(), ::runtimeMethodMergeKey)
        val appended = supplemental.filterNot { runtimeMethodMergeKey(it) in existingKeys }
        return preferred + appended
    }

    private fun runtimeMethodMergeKey(method: WinMdMethod): String {
        return buildString {
            append(method.name)
            append('(')
            append(method.parameters.joinToString(",") { it.type })
            append(')')
        }
    }

    private fun mergeProperties(primary: List<WinMdProperty>, supplemental: List<WinMdProperty>): List<WinMdProperty> {
        if (primary.isEmpty()) return supplemental
        if (supplemental.isEmpty()) return primary
        val supplementalByName = supplemental.associateBy(WinMdProperty::name)
        val mergedPrimary = primary.map { property ->
            supplementalByName[property.name]?.let { mergeProperty(property, it) } ?: property
        }
        val existingNames = primary.mapTo(linkedSetOf(), WinMdProperty::name)
        val appended = supplemental.filterNot { it.name in existingNames }
        return mergedPrimary + appended
    }

    private fun mergeProperty(primary: WinMdProperty, supplemental: WinMdProperty): WinMdProperty {
        return primary.copy(
            type = primary.type.takeIf { it != "UnknownType" } ?: supplemental.type,
            mutable = primary.mutable || supplemental.mutable,
            getterVtableIndex = primary.getterVtableIndex ?: supplemental.getterVtableIndex,
            setterVtableIndex = primary.setterVtableIndex ?: supplemental.setterVtableIndex,
        )
    }

    private fun expandSpecializedInterfaceMembers(model: WinMdModel): WinMdModel {
        val typeIndex = model.namespaces
            .flatMap { namespace -> namespace.types.map { type -> "${type.namespace}.${type.name}" to type } }
            .toMap()
        val expanded = mutableMapOf<String, WinMdType>()

        fun expand(type: WinMdType): WinMdType {
            val qualifiedName = "${type.namespace}.${type.name}"
            return expanded.getOrPut(qualifiedName) {
                when (type.kind) {
                    WinMdTypeKind.Interface -> {
                        if (type.baseInterfaces.isEmpty()) {
                            type
                        } else {
                            val inheritedMethods = mutableListOf<WinMdMethod>()
                            val inheritedProperties = mutableListOf<WinMdProperty>()
                            type.baseInterfaces.forEach { baseInterface ->
                                val specialization = parseSpecializedType(baseInterface)
                                val baseType = typeIndex[specialization.rawType]?.let(::expand) ?: return@forEach
                                val substitutions = baseType.genericParameters.zip(specialization.arguments).toMap()
                                inheritedMethods += baseType.methods.map { substituteMethod(it, substitutions) }
                                inheritedProperties += baseType.properties.map { substituteProperty(it, substitutions) }
                            }
                            type.copy(
                                methods = pruneUnresolvedGenericMethods(mergeMethods(type.methods, inheritedMethods)),
                                properties = pruneUnresolvedGenericProperties(mergeProperties(type.properties, inheritedProperties)),
                            )
                        }
                    }
                    WinMdTypeKind.RuntimeClass -> {
                        val inheritedInterfaces = buildList {
                            addAll(type.implementedInterfaces.filterNot { it == type.defaultInterface })
                            addAll(type.baseInterfaces.filterNot { it == type.defaultInterface })
                            type.defaultInterface?.let(::add)
                        }.distinct()
                        if (inheritedInterfaces.isEmpty()) {
                            type
                        } else {
                            val inheritedMethods = mutableListOf<WinMdMethod>()
                            val inheritedProperties = mutableListOf<WinMdProperty>()
                            inheritedInterfaces.forEach { implementedInterface ->
                                val specialization = parseSpecializedType(implementedInterface)
                                val interfaceType = typeIndex[specialization.rawType]?.let(::expand) ?: return@forEach
                                val substitutions = interfaceType.genericParameters.zip(specialization.arguments).toMap()
                                inheritedMethods += interfaceType.methods.map { inheritedMethod ->
                                    substituteMethod(inheritedMethod, substitutions).copy(sourceInterface = implementedInterface)
                                }
                                inheritedProperties += interfaceType.properties.map { substituteProperty(it, substitutions) }
                            }
                            type.copy(
                                methods = pruneUnresolvedGenericMethods(mergeRuntimeClassMethods(inheritedMethods, type.methods)),
                                properties = pruneUnresolvedGenericProperties(mergeProperties(inheritedProperties, type.properties)),
                            )
                        }
                    }
                    else -> type
                }
            }
        }

        return model.copy(
            namespaces = model.namespaces.map { namespace ->
                namespace.copy(types = namespace.types.map(::expand))
            },
        )
    }

    private fun substituteMethod(method: WinMdMethod, substitutions: Map<String, String>): WinMdMethod {
        if (substitutions.isEmpty()) return method
        return method.copy(
            returnType = substituteType(method.returnType, substitutions),
            parameters = method.parameters.map { parameter ->
                parameter.copy(type = substituteType(parameter.type, substitutions))
            },
        )
    }

    private fun substituteProperty(property: WinMdProperty, substitutions: Map<String, String>): WinMdProperty {
        if (substitutions.isEmpty()) return property
        return property.copy(type = substituteType(property.type, substitutions))
    }

    private fun pruneUnresolvedGenericMethods(methods: List<WinMdMethod>): List<WinMdMethod> {
        return methods.filterNot { method ->
            containsGenericPlaceholder(method.returnType) ||
                method.parameters.any { parameter -> containsGenericPlaceholder(parameter.type) }
        }.ifEmpty { methods }
    }

    private fun pruneUnresolvedGenericProperties(properties: List<WinMdProperty>): List<WinMdProperty> {
        return properties.filterNot { property ->
            containsGenericPlaceholder(property.type)
        }.ifEmpty { properties }
    }

    private fun containsGenericPlaceholder(typeName: String): Boolean {
        return "ElementType0x13" in typeName || "ElementType0x1e" in typeName
    }

    private fun substituteType(typeName: String, substitutions: Map<String, String>): String {
        substitutions[typeName]?.let { return it }
        return when {
            typeName.endsWith("[]") -> substituteType(typeName.removeSuffix("[]"), substitutions) + "[]"
            '<' in typeName && typeName.endsWith(">") -> {
                val rawType = typeName.substringBefore('<')
                val argumentSource = typeName.substringAfter('<').removeSuffix(">")
                val rewrittenArguments = splitGenericArguments(argumentSource).joinToString(", ") { argument ->
                    substituteType(argument, substitutions)
                }
                "$rawType<$rewrittenArguments>"
            }
            else -> typeName
        }
    }

    private fun splitGenericArguments(source: String): List<String> {
        if (source.isBlank()) return emptyList()
        return buildList {
            var depth = 0
            var start = 0
            source.forEachIndexed { index, char ->
                when (char) {
                    '<' -> depth++
                    '>' -> depth--
                    ',' -> if (depth == 0) {
                        add(source.substring(start, index).trim())
                        start = index + 1
                    }
                }
            }
            add(source.substring(start).trim())
        }
    }

    private fun parseSpecializedType(typeName: String): SpecializedType {
        if ('<' !in typeName || !typeName.endsWith(">")) {
            return SpecializedType(typeName, emptyList())
        }
        return SpecializedType(
            rawType = typeName.substringBefore('<'),
            arguments = splitGenericArguments(typeName.substringAfter('<').removeSuffix(">")),
        )
    }

    private data class SpecializedType(
        val rawType: String,
        val arguments: List<String>,
    )
}
