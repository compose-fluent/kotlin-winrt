package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtActivationShape
import io.github.kitectlab.winrt.metadata.WinRtEnumMemberDefinition
import io.github.kitectlab.winrt.metadata.WinRtFieldDefinition
import io.github.kitectlab.winrt.metadata.WinRtIntegralType
import io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtParameterDefinition
import io.github.kitectlab.winrt.metadata.WinRtPropertyDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.metadata.WinRtMetadataLoader
import io.github.kitectlab.winrt.runtime.Guid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.isRegularFile

class KotlinProjectionGeneratorTest {
    @Test
    fun generate_to_skips_unchanged_files_and_removes_stale_outputs() {
        val outputRoot = Files.createTempDirectory("kotlin-winrt-generator-incremental-")
        val fullModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IFirst",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ISecond",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                        ),
                    ),
                ),
            ),
        )
        val narrowedModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IFirst",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                    ),
                ),
            ),
        )
        val generator = KotlinProjectionGenerator()

        val first = generator.generateTo(fullModel, outputRoot)
        val second = generator.generateTo(fullModel, outputRoot)
        val narrowed = generator.generateTo(narrowedModel, outputRoot)

        assertEquals(2, first.renderedFiles)
        assertEquals(2, first.writtenFiles)
        assertEquals(0, first.unchangedFiles)
        assertEquals(0, first.deletedStaleFiles)
        assertEquals(2, second.renderedFiles)
        assertEquals(0, second.writtenFiles)
        assertEquals(2, second.unchangedFiles)
        assertEquals(0, second.deletedStaleFiles)
        assertEquals(1, narrowed.renderedFiles)
        assertEquals(0, narrowed.writtenFiles)
        assertEquals(1, narrowed.unchangedFiles)
        assertEquals(1, narrowed.deletedStaleFiles)
        assertTrue(outputRoot.resolve("io/github/kitectlab/winrt/projections/sample/foundation/IFirst.kt").isRegularFile())
        assertFalse(outputRoot.resolve("io/github/kitectlab/winrt/projections/sample/foundation/ISecond.kt").isRegularFile())
    }

    @Test
    fun generator_reproduces_cswinrt_json_value_function_calls_surface_from_real_winmd() {
        // Mirrors .cswinrt/src/Tests/FunctionalTests/JsonValueFunctionCalls/Program.cs.
        val filesByName = generateWindowsDataJsonFromInstalledWinmd()

        val jsonValue = filesByName.getValue("JsonValue.kt").contents
        val iJsonValue = filesByName.getValue("IJsonValue.kt").contents

        assertTrue(jsonValue, jsonValue.contains("IStringable"))
        assertTrue(jsonValue, jsonValue.contains("fun CreateNumberValue(input: Double): JsonValue"))
        assertTrue(jsonValue, jsonValue.contains("fun GetNumber(): Double"))
        assertTrue(jsonValue, jsonValue.contains("fun ToString(): String"))
        assertTrue(iJsonValue, iJsonValue.contains("fun GetNumber(): Double"))
    }

    @Test
    fun generator_reproduces_cswinrt_json_api_compat_surface_from_real_winmd() {
        // Mirrors the Windows.Data.Json block in .cswinrt/src/Tests/UnitTest/ApiCompatTests.cs.
        val filesByName = generateWindowsDataJsonFromInstalledWinmd()

        val jsonObject = filesByName.getValue("JsonObject.kt").contents
        val iJsonObject = filesByName.getValue("IJsonObject.kt").contents
        val jsonArray = filesByName.getValue("JsonArray.kt").contents
        val iJsonValue = filesByName.getValue("IJsonValue.kt").contents

        assertTrue(jsonObject, jsonObject.contains("fun Parse(input: String): JsonObject"))
        assertTrue(jsonObject, jsonObject.contains("fun GetNamedString(name: String, defaultValue: String): String"))
        assertTrue(jsonObject, jsonObject.contains("fun GetNamedValue(name: String): JsonValue"))
        assertTrue(jsonObject, jsonObject.contains("fun GetNamedBoolean(name: String, defaultValue: Boolean): Boolean"))
        assertTrue(jsonObject, jsonObject.contains("fun GetNamedArray(name: String, defaultValue: JsonArray): JsonArray"))
        assertTrue(iJsonObject, iJsonObject.contains("fun GetNamedArray(name: String): JsonArray"))
        assertTrue(jsonArray, jsonArray.contains("public constructor()"))
        assertTrue(iJsonValue, iJsonValue.contains("val valueType: JsonValueType"))
        assertTrue(iJsonValue, iJsonValue.contains("fun GetObject(): JsonObject"))
    }

    @Test
    fun generator_reproduces_windows_data_json_smoke_slice_declaration_set() {
        val filesByName = KotlinProjectionGenerator()
            .generate(windowsDataJsonProjectionModel())
            .associateBy { it.relativePath.substringAfterLast('/') }

        assertEquals(
            listOf(
                "IJsonArray.kt",
                "IJsonArrayStatics.kt",
                "IJsonErrorStatics.kt",
                "IJsonObject.kt",
                "IJsonObjectStatics.kt",
                "IJsonValue.kt",
                "IJsonValueStatics.kt",
                "JsonArray.kt",
                "JsonError.kt",
                "JsonErrorStatus.kt",
                "JsonObject.kt",
                "JsonValue.kt",
                "JsonValueType.kt",
            ),
            filesByName.keys.sorted(),
        )

        val jsonObject = filesByName.getValue("JsonObject.kt").contents
        val jsonArray = filesByName.getValue("JsonArray.kt").contents
        val jsonValue = filesByName.getValue("JsonValue.kt").contents
        val jsonError = filesByName.getValue("JsonError.kt").contents
        val iJsonObject = filesByName.getValue("IJsonObject.kt").contents
        val iJsonValueStatics = filesByName.getValue("IJsonValueStatics.kt").contents

        assertTrue(jsonObject, jsonObject.contains("public class JsonObject internal constructor("))
        assertTrue(jsonObject, jsonObject.contains("IJsonObject"))
        assertTrue(jsonObject, jsonObject.contains("IWinRTObject"))
        assertTrue(jsonObject, jsonObject.contains("private val _inner: IInspectableReference"))
        assertTrue(jsonObject, jsonObject.contains("override val nativeObject: ComObjectReference"))
        assertTrue(jsonObject, jsonObject.contains("fun getNamedString(name: String): String"))
        assertTrue(jsonObject, jsonObject.contains("fun setNamedValue(name: String, `value`: JsonValue)"))
        assertTrue(jsonObject, jsonObject.contains("(value as IWinRTObject).nativeObject.pointer"))
        assertTrue(jsonObject, jsonObject.contains("fun parse(json: String): JsonObject"))
        assertFalse(jsonObject, jsonObject.contains("fun parse(json: String): JsonObject = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(jsonObject, jsonObject.contains("HString.create(json).use { __jsonAbi ->"))
        assertTrue(jsonObject, jsonObject.contains("internal val STATIC_PARSE_SLOT: Int = IJsonObjectStatics.Metadata.PARSE_SLOT"))
        assertTrue(jsonObject, jsonObject.contains("public object StaticInterfaces"))
        assertTrue(jsonObject, jsonObject.contains("public const val IJSONOBJECTSTATICS: String = \"Windows.Data.Json.IJsonObjectStatics\""))
        assertTrue(jsonObject, jsonObject.contains("private val _iJsonObjectStatics: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(jsonObject, jsonObject.contains("private val _defaultInterface: ComObjectReference"))

        assertTrue(jsonArray, jsonArray.contains("public class JsonArray internal constructor("))
        assertTrue(jsonArray, jsonArray.contains("fun getStringAt(index: UInt): String"))
        assertTrue(jsonArray, jsonArray.contains("fun create(): JsonArray"))
        assertTrue(jsonArray, jsonArray.contains("Metadata.wrap("))
        assertTrue(jsonArray, jsonArray.contains("ActivationFactory.activate()"))

        assertTrue(jsonValue, jsonValue.contains("public class JsonValue internal constructor("))
        assertTrue(jsonValue, jsonValue.contains("fun stringify(): String"))
        assertTrue(jsonValue, jsonValue.contains("fun createStringValue(`value`: String): JsonValue"))
        assertTrue(jsonValue, jsonValue.contains("internal val STATIC_CREATESTRINGVALUE_SLOT: Int ="))
        assertTrue(jsonValue, jsonValue.contains("IJsonValueStatics.Metadata.CREATESTRINGVALUE_SLOT"))
        assertTrue(jsonValue, jsonValue.contains("JsonValueType.Metadata.fromAbi"))
        assertFalse(jsonValue, jsonValue.contains("JsonValueType.Metadata.wrap"))

        assertTrue(jsonError, jsonError.contains("public class JsonError internal constructor("))
        assertTrue(jsonError, jsonError.contains("fun getJsonStatus(hResult: Int): JsonErrorStatus"))
        assertTrue(jsonError, jsonError.contains("JsonErrorStatus.Metadata.fromAbi"))

        assertTrue(iJsonObject, iJsonObject.contains("fun getNamedArray(name: String): JsonArray"))
        assertTrue(iJsonObject, iJsonObject.contains("fun setNamedValue(name: String, `value`: JsonValue)"))

        assertTrue(iJsonValueStatics, iJsonValueStatics.contains("fun createBooleanValue(`value`: Boolean): JsonValue"))
        assertTrue(iJsonValueStatics, iJsonValueStatics.contains("fun createNumberValue(`value`: Double): JsonValue"))
        assertTrue(iJsonValueStatics, iJsonValueStatics.contains("fun createStringValue(`value`: String): JsonValue"))
    }

    @Test
    fun plans_deterministic_projection_paths_from_metadata_model() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Data.Json",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "JsonValue", kind = WinRtTypeKind.RuntimeClass),
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "IJsonValue", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "JsonValueType", kind = WinRtTypeKind.Enum),
                    ),
                ),
            ),
        )

        val plans = KotlinProjectionPlanner().plan(model)

        assertEquals(
            listOf(
                "io/github/kitectlab/winrt/projections/windows/data/json/IJsonValue.kt",
                "io/github/kitectlab/winrt/projections/windows/data/json/JsonValue.kt",
                "io/github/kitectlab/winrt/projections/windows/data/json/JsonValueType.kt",
            ),
            plans.map { it.relativePath },
        )
        assertEquals(
            listOf(
                KotlinProjectionDeclarationKind.Interface,
                KotlinProjectionDeclarationKind.Class,
                KotlinProjectionDeclarationKind.Enum,
            ),
            plans.map { it.declarationKind },
        )
    }

    @Test
    fun renders_runtime_class_and_static_methods_from_metadata_shape() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Data.Json",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonObject",
                            kind = WinRtTypeKind.RuntimeClass,
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Windows.Data.Json.IJsonObjectStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getNamedString",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                ),
                                WinRtMethodDefinition(
                                    name = "parse",
                                    returnTypeName = "JsonObject",
                                    parameters = listOf(WinRtParameterDefinition("json", "String")),
                                    isStatic = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val file = KotlinProjectionGenerator().generate(model).single()
        println(file.contents)

        assertEquals("io/github/kitectlab/winrt/projections/windows/data/json/JsonObject.kt", file.relativePath)
        assertTrue(file.contents.contains("package io.github.kitectlab.winrt.projections.windows.`data`.json"))
        assertTrue(file.contents.contains("public class JsonObject internal constructor("))
        assertTrue(file.contents.contains("private val _inner: IInspectableReference"))
        assertTrue(file.contents.contains("fun getNamedString(name: String): String = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(file.contents.contains("companion object"))
        assertTrue(file.contents.contains("fun parse(json: String): JsonObject = error(\"Not yet bound to winrt-runtime\")"))
        assertFalse(file.contents.contains("JsonValueType"))
    }

    @Test
    fun planner_carries_metadata_contract_fields_for_later_generator_passes() {
        val interfaceIid = Guid("11111111-2222-3333-4444-555555555555")
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = interfaceIid,
                            methods = listOf(WinRtMethodDefinition(name = "ping", returnTypeName = "Unit")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("66666666-7777-8888-9999-000000000000"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IComposableWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("aaaaaaaa-7777-8888-9999-000000000000"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                                composableFactoryInterfaceName = "Sample.Foundation.IComposableWidgetFactory",
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "create", returnTypeName = "Widget", isStatic = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val plans = KotlinProjectionPlanner().plan(model)
        val interfacePlan = plans.first { it.type.name == "IWidget" }
        val classPlan = plans.first { it.type.name == "Widget" }

        assertEquals(interfaceIid, interfacePlan.interfaceIid)
        assertEquals("Sample.Foundation.IWidget", classPlan.defaultInterfaceName)
        assertEquals(interfaceIid, classPlan.defaultInterfaceIid)
        assertEquals(listOf("Sample.Foundation.IWidgetStatics"), classPlan.staticInterfaceNames)
        assertEquals(
            listOf(
                KotlinProjectionInterfaceBinding(
                    qualifiedName = "Sample.Foundation.IWidgetStatics",
                    iid = Guid("66666666-7777-8888-9999-000000000000"),
                ),
            ),
            classPlan.staticInterfaceBindings,
        )
        assertTrue(classPlan.implementedInterfaceBindings.isEmpty())
        assertEquals("Sample.Foundation.IComposableWidgetFactory", classPlan.composableFactoryInterfaceName)
        assertEquals(Guid("aaaaaaaa-7777-8888-9999-000000000000"), classPlan.composableFactoryInterfaceIid)
        assertEquals(
            listOf(KotlinProjectionCompanionKind.Metadata),
            interfacePlan.companionKinds,
        )
        assertEquals(
            listOf(
                KotlinProjectionCompanionKind.Metadata,
                KotlinProjectionCompanionKind.ActivationFactory,
                KotlinProjectionCompanionKind.StaticInterfaces,
                KotlinProjectionCompanionKind.ComposableFactory,
            ),
            classPlan.companionKinds,
        )
    }

    @Test
    fun planner_consumes_metadata_handoff_descriptors_for_abi_emission() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetChangedHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("99999999-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("message", "String")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getTitle",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("class", "String")),
                                    methodRowId = 9,
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.Foundation.WidgetChangedHandler",
                                    addMethodRowId = 10,
                                    removeMethodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getTitle",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("class", "String")),
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.Foundation.WidgetChangedHandler",
                                    addMethodName = "add_Changed",
                                    removeMethodName = "remove_Changed",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val plansByName = KotlinProjectionPlanner().plan(model).associateBy { it.type.name }
        val interfacePlan = plansByName.getValue("IWidget")
        val classPlan = plansByName.getValue("Widget")
        val getTitleSlot = interfacePlan.abiSlotBindings.single { it.constantName == "GETTITLE_SLOT" }
        val getTitleBinding = classPlan.instanceMemberBindings.single { it.bindingName == "GETTITLE_SLOT" }
        val eventDescriptor = classPlan.eventInvokeDescriptors.single { it.eventName == "Changed" }

        assertEquals(6, getTitleSlot.slot)
        assertEquals("getTitle", getTitleSlot.descriptor?.methodName)
        assertEquals("String", getTitleBinding.signatureDescriptor?.projectionReturnTypeName)
        assertEquals("IntPtr", getTitleBinding.signatureDescriptor?.abiReturnTypeName)
        assertEquals("`class`", getTitleBinding.signatureDescriptor?.parameters?.single()?.escapedName)
        assertTrue(getTitleBinding.marshalerPlanDescriptor?.requiresDispose == true)
        assertEquals("Sample.Foundation.WidgetChangedHandler", eventDescriptor.delegateTypeName)
        assertEquals("Invoke", eventDescriptor.invokeMethodName)
        assertTrue(classPlan.typeDeclarationDescriptor.writesWrapperDeclaration)
        assertEquals("WidgetActivationFactory", classPlan.factorySurfaceDescriptor?.activationFactoryCacheName)
        assertEquals(listOf("Sample_Foundation_IWidgetCache"), classPlan.objectReferenceSurfaceDescriptor?.objectReferenceNames)
        assertEquals(emptyList<String>(), classPlan.requiredInterfaceAugmentationDescriptor?.requiredInterfaceNames)
        assertEquals(emptyList<String>(), classPlan.moduleActivationAndAuthoringDescriptor?.moduleActivationFactoryEntries)

        val generatedWidget = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.endsWith("/Widget.kt") }
            .contents
        assertTrue(generatedWidget, generatedWidget.contains("internal const val WRITES_WRAPPER_DECLARATION: Boolean = true"))
        assertTrue(generatedWidget, generatedWidget.contains("internal const val FACTORY_CACHE_NAME: String = \"WidgetActivationFactory\""))
        assertTrue(generatedWidget, generatedWidget.contains("internal val OBJECT_REFERENCE_NAMES: List<String> = listOf(\"Sample_Foundation_IWidgetCache\")"))
        assertTrue(generatedWidget, generatedWidget.contains("internal val REQUIRED_INTERFACE_NAMES: List<String> = listOf()"))
    }

    @Test
    fun planner_maps_all_metadata_type_kinds_into_declaration_plans() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Status", kind = WinRtTypeKind.Enum),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Point", kind = WinRtTypeKind.Struct),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(
                                        WinRtParameterDefinition("title", "String"),
                                        WinRtParameterDefinition("count", "Int"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val plans = KotlinProjectionPlanner().plan(model)

        assertEquals(
            listOf(
                KotlinProjectionDeclarationKind.Struct,
                KotlinProjectionDeclarationKind.Enum,
                KotlinProjectionDeclarationKind.Delegate,
            ),
            plans.map { it.declarationKind },
        )
        assertEquals(
            listOf(
                "Point",
                "Status",
                "WidgetHandler",
            ),
            plans.map { it.type.name },
        )
        assertTrue(plans.all { it.companionKinds.isEmpty() })
    }

    @Test
    fun generator_projects_system_object_as_winrt_reference_not_kotlin_any() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IObjectSource",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetObject",
                                    returnTypeName = "System.Object",
                                    parameters = listOf(WinRtParameterDefinition("input", "System.Object")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model).single().contents

        assertTrue(contents, contents.contains("fun GetObject(input: IInspectableReference): IInspectableReference"))
        assertFalse(contents, contents.contains("fun GetObject(input: Any): Any"))
    }

    @Test
    fun planner_tracks_cswinrt_declaration_ownership_traits() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetAttribute",
                            kind = WinRtTypeKind.RuntimeClass,
                            baseTypeName = "System.Attribute",
                            isAttributeType = true,
                            isSealedType = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetOverrides",
                            kind = WinRtTypeKind.Interface,
                            isExclusiveTo = true,
                            iid = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "InternalContract",
                            kind = WinRtTypeKind.Interface,
                            isProjectionInternal = true,
                            iid = Guid("11111111-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetContract",
                            kind = WinRtTypeKind.Struct,
                            isApiContract = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetStatics",
                            kind = WinRtTypeKind.RuntimeClass,
                            isStaticType = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                        ),
                    ),
                ),
            ),
        )

        val plansByName = KotlinProjectionPlanner().plan(model).associateBy { it.type.name }

        assertEquals(
            listOf(KotlinProjectionSpecializationKind.AttributeClass),
            plansByName.getValue("WidgetAttribute").specializationKinds,
        )
        assertEquals(
            listOf(KotlinProjectionModifier.Sealed),
            plansByName.getValue("WidgetAttribute").modifiers,
        )
        assertTrue(plansByName.getValue("WidgetAttribute").companionKinds.isEmpty())

        assertEquals(KotlinProjectionVisibility.Internal, plansByName.getValue("IWidgetOverrides").visibility)
        assertEquals(
            listOf(KotlinProjectionSpecializationKind.ExclusiveInterface),
            plansByName.getValue("IWidgetOverrides").specializationKinds,
        )
        assertTrue(plansByName.getValue("IWidgetOverrides").companionKinds.isEmpty())

        assertEquals(KotlinProjectionVisibility.Internal, plansByName.getValue("InternalContract").visibility)
        assertEquals(
            listOf(
                KotlinProjectionSpecializationKind.ProjectionInternal,
            ),
            plansByName.getValue("InternalContract").specializationKinds,
        )
        assertEquals(
            listOf(KotlinProjectionCompanionKind.Metadata),
            plansByName.getValue("InternalContract").companionKinds,
        )

        assertEquals(
            listOf(KotlinProjectionSpecializationKind.ApiContract),
            plansByName.getValue("WidgetContract").specializationKinds,
        )
        assertEquals(KotlinProjectionDeclarationKind.Enum, plansByName.getValue("WidgetContract").declarationKind)
        assertTrue(plansByName.getValue("WidgetContract").companionKinds.isEmpty())

        assertEquals(
            listOf(KotlinProjectionSpecializationKind.StaticClass),
            plansByName.getValue("WidgetStatics").specializationKinds,
        )
        assertEquals(
            listOf(KotlinProjectionModifier.Static),
            plansByName.getValue("WidgetStatics").modifiers,
        )
        assertTrue(plansByName.getValue("WidgetStatics").companionKinds.isEmpty())

        assertEquals(
            listOf(KotlinProjectionModifier.Sealed),
            plansByName.getValue("Widget").modifiers,
        )
    }

    @Test
    fun generator_renders_shells_for_non_class_declarations() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Status", kind = WinRtTypeKind.Enum),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Point", kind = WinRtTypeKind.Struct),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(
                                        WinRtParameterDefinition("title", "String"),
                                        WinRtParameterDefinition("count", "Int"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }

        assertTrue(filesByName.getValue("Status.kt").contents.contains("enum class Status"))
        assertTrue(filesByName.getValue("Point.kt").contents.contains("class Point"))
        assertTrue(filesByName.getValue("WidgetHandler.kt").contents.contains("fun interface WidgetHandler"))
        assertTrue(filesByName.getValue("WidgetHandler.kt").contents.contains("public operator fun invoke(title: String, count: Int): Boolean"))
    }

    @Test
    fun renderer_uses_visibility_modifiers_and_metadata_companion_shells() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IInternalContract",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            isProjectionInternal = true,
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name", getterMethodRowId = 5),
                            ),
                            events = listOf(
                                WinRtEventDefinition(name = "Changed", delegateTypeName = "Sample.Foundation.WidgetHandler", addMethodRowId = 9, removeMethodRowId = 10),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-2222-3333-4444-555555555555"),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Loaded",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodRowId = 11,
                                    removeMethodRowId = 12,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("44444444-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "CreateInstance",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(
                                        WinRtParameterDefinition("baseInterface", "System.Object"),
                                        WinRtParameterDefinition("innerInterface", "System.Object"),
                                    ),
                                    methodRowId = 13,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                                composableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "create",
                                    returnTypeName = "Widget",
                                    isStatic = true,
                                    methodRowId = 20,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name", getterMethodRowId = 21),
                                WinRtPropertyDefinition(name = "Count", typeName = "Int", isStatic = true, getterMethodName = "get_Count", getterMethodRowId = 22),
                            ),
                            events = listOf(
                                WinRtEventDefinition(name = "Changed", delegateTypeName = "Sample.Foundation.WidgetHandler", addMethodRowId = 23, removeMethodRowId = 24),
                                WinRtEventDefinition(name = "Loaded", delegateTypeName = "Sample.Foundation.WidgetHandler", isStatic = true, addMethodRowId = 25, removeMethodRowId = 26),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetStatics",
                            kind = WinRtTypeKind.RuntimeClass,
                            isStaticType = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("55555555-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetContract",
                            kind = WinRtTypeKind.Struct,
                            isApiContract = true,
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }

        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal interface IInternalContract"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("companion object Metadata"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("IID: Guid = Guid(\"11111111-2222-3333-4444-555555555555\")"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val NAME_GETTER_METHOD_ROW_ID: Int = 5"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val CHANGED_ADD_METHOD_ROW_ID: Int = 9"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val CHANGED_REMOVE_METHOD_ROW_ID: Int = 10"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val NAME_GETTER_SLOT: Int = 6"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val CHANGED_ADD_SLOT: Int = 7"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val CHANGED_REMOVE_SLOT: Int = 8"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("val name: String"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("val changed: WinRtEvent<WidgetHandler>"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("fun addChanged(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("fun removeChanged(token: EventRegistrationToken)"))

        val widgetContents = filesByName.getValue("Widget.kt").contents
        assertTrue(widgetContents.contains("public class Widget internal constructor("))
        assertTrue(widgetContents.contains("private val _inner: IInspectableReference"))
        assertTrue(widgetContents.contains("private val _defaultInterface: ComObjectReference"))
        assertTrue(widgetContents.contains("ActivationFactory.activateInstance(Metadata.TYPE_NAME)"))
        assertTrue(widgetContents.contains("val name: String"))
        assertTrue(widgetContents.contains("companion object Metadata"))
        assertTrue(widgetContents.contains("internal fun acquireInterface(instance: IInspectableReference, iid: Guid): IUnknownReference"))
        assertTrue(widgetContents.contains("internal fun wrap(instance: IInspectableReference): Widget = Widget(instance)"))
        assertTrue(widgetContents.contains("public constructor() : this(ComposableFactory.createInstance())"))
        assertTrue(widgetContents.contains("internal const val CREATE_METHOD_ROW_ID: Int = 20"))
        assertTrue(widgetContents.contains("internal const val NAME_GETTER_METHOD_ROW_ID: Int = 21"))
        assertTrue(widgetContents.contains("internal const val COUNT_GETTER_METHOD_ROW_ID: Int = 22"))
        assertTrue(widgetContents.contains("internal const val CHANGED_ADD_METHOD_ROW_ID: Int = 23"))
        assertTrue(widgetContents.contains("internal const val CHANGED_REMOVE_METHOD_ROW_ID: Int = 24"))
        assertTrue(widgetContents.contains("internal const val LOADED_ADD_METHOD_ROW_ID: Int = 25"))
        assertTrue(widgetContents.contains("internal const val LOADED_REMOVE_METHOD_ROW_ID: Int = 26"))
        assertTrue(widgetContents.contains("fun acquireDefaultInterface(instance: IInspectableReference): IUnknownReference"))
        assertTrue(widgetContents.contains("acquireInterface(instance, DEFAULT_INTERFACE_IID)"))
        assertTrue(widgetContents.contains("fun create(): Widget"))
        assertTrue(widgetContents.contains("Metadata.wrap("))
        assertTrue(widgetContents.contains("ActivationFactory.activate()"))
        assertTrue(widgetContents.contains("val count: Int"))
        assertTrue(widgetContents.contains("val changed: WinRtEvent<WidgetHandler> by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("WinRtEvent(::addChanged, ::removeChanged)"))
        assertTrue(widgetContents.contains("fun addChanged(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(widgetContents.contains("fun removeChanged(token: EventRegistrationToken)"))
        assertTrue(widgetContents.contains("fun addLoaded(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(widgetContents.contains("val loaded: WinRtEvent<WidgetHandler> by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("\"Sample.Foundation.IWidgetStatics\""))
        assertTrue(widgetContents.contains("StaticInterfaces.iWidgetStatics()"))
        assertTrue(widgetContents.contains("fun addLoaded(handler: WidgetHandler): EventRegistrationToken ="))
        assertTrue(widgetContents.contains("loaded.add(handler)"))
        assertTrue(widgetContents.contains("public object ActivationFactory"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
        assertTrue(widgetContents.contains("val FACTORY_INTERFACE_IID: Guid = Guid(\"44444444-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("fun acquire(): IUnknownReference"))
        assertTrue(widgetContents.contains("io.github.kitectlab.winrt.runtime.ActivationFactory.get(RUNTIME_CLASS,"))
        assertTrue(widgetContents.contains("fun activate(): IInspectableReference"))
        assertTrue(widgetContents.contains("public object StaticInterfaces"))
        assertTrue(widgetContents.contains("val IWIDGETSTATICS_IID: Guid = Guid(\"33333333-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("fun iWidgetStatics(): IUnknownReference"))
        assertTrue(widgetContents.contains("io.github.kitectlab.winrt.runtime.ActivationFactory.get(Metadata.TYPE_NAME,"))
        assertTrue(widgetContents.contains("public object ComposableFactory"))
        assertTrue(widgetContents.contains("public const val DEFAULT_INTERFACE: String = \"Sample.Foundation.IWidget\""))
        assertTrue(widgetContents.contains("val DEFAULT_INTERFACE_IID: Guid = Guid(\"22222222-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
        assertTrue(widgetContents.contains("fun acquire(): IUnknownReference"))
        assertTrue(widgetContents.contains("fun createInstance(): IInspectableReference"))
        assertTrue(widgetContents.contains("IWidgetFactory.Metadata.CREATEINSTANCE_SLOT"))
        assertEquals(1, "companion object Metadata".toRegex().findAll(widgetContents).count())

        val widgetFactoryContents = filesByName.getValue("IWidgetFactory.kt").contents
        assertTrue(widgetFactoryContents.contains("internal const val CREATEINSTANCE_METHOD_ROW_ID: Int = 13"))
        assertTrue(widgetFactoryContents.contains("internal const val CREATEINSTANCE_SLOT: Int = 6"))

        assertTrue(filesByName.getValue("WidgetStatics.kt").contents.contains("public class WidgetStatics"))
        assertTrue(filesByName.getValue("WidgetStatics.kt").contents.contains("static WinRT class shell"))
        assertTrue(filesByName.getValue("WidgetContract.kt").contents.contains("public enum class WidgetContract"))
        assertTrue(filesByName.getValue("WidgetContract.kt").contents.contains("api contract WinRT declaration shell"))
    }

    @Test
    fun generator_emits_single_native_projection_wrapper_for_method_only_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICalculator",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Add",
                                    returnTypeName = "Int",
                                    parameters = listOf(
                                        WinRtParameterDefinition("left", "Int"),
                                        WinRtParameterDefinition("right", "Int"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model).single().contents

        assertTrue(contents.contains("TYPE_HANDLE: WinRtTypeHandle"))
        assertTrue(contents.contains("WinRtTypeHandle("))
        assertTrue(contents.contains("private class NativeProjection("))
        assertTrue(contents.contains("internal fun wrap(instance: IUnknownReference): ICalculator = NativeProjection(instance)"))
        assertFalse(contents.contains("return object : ICalculator, IWinRTObject"))
    }

    @Test
    fun generator_routes_native_projection_events_through_event_source_table() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "EventHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("33333333-2222-3333-4444-555555555555"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("sender", "System.Object"),
                                        WinRtParameterDefinition("args", "T0"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICalculator",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Reset",
                                    returnTypeName = "Unit",
                                    methodRowId = 6,
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Windows.Foundation.EventHandler<Int>",
                                    addMethodRowId = 7,
                                    removeMethodRowId = 8,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model).single { it.relativePath.endsWith("ICalculator.kt") }.contents

        assertTrue(contents.contains("private class NativeProjection("))
        assertTrue(contents.contains("WinRTEventProjectionHelpers.createEventSource("))
        assertTrue(contents.contains("\"Windows.Foundation.EventHandler<Int>\""))
        assertTrue(contents.contains("\"Sample.Foundation.ICalculator\""))
        assertTrue(contents.contains("ICalculator.Metadata.CHANGED_ADD_SLOT"))
        assertTrue(contents.contains(".add(handler)"))
        assertTrue(contents.contains(".remove(token)"))
    }

    @Test
    fun generator_merges_required_interface_property_accessors_across_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameReader",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                    getterMethodRowId = 1,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameWriter",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    setterMethodName = "put_Name",
                                    setterMethodRowId = 1,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NamedObject",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameReader",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.INameReader", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.INameWriter"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.endsWith("NamedObject.kt") }
            .contents

        assertEquals(1, "override var name: String".toRegex().findAll(contents).count())
        assertTrue(contents.contains("INameReader.Metadata.NAME_GETTER_SLOT"))
        assertTrue(contents.contains("INameWriter.Metadata.NAME_SETTER_SLOT"))
    }

    @Test
    fun generator_binds_static_methods_and_properties_through_static_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "parse",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                    methodRowId = 10,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                    getterMethodRowId = 11,
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Windows.Foundation.EventHandler<Int>",
                                    addMethodRowId = 13,
                                    removeMethodRowId = 14,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics2",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    setterMethodName = "put_Count",
                                    setterMethodRowId = 12,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf(
                                    "Sample.Foundation.IWidgetStatics",
                                    "Sample.Foundation.IWidgetStatics2",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("private val _iWidgetStatics: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("fun iWidgetStatics(): IUnknownReference"))
        assertTrue(widgetContents.contains("fun parse(`value`: String): Widget"))
        assertTrue(widgetContents.contains("ComVtableInvoker.invokeArgs"))
        assertTrue(widgetContents.contains("StaticInterfaces.iWidgetStatics().pointer"))
        assertTrue(widgetContents.contains("internal val STATIC_PARSE_SLOT: Int = IWidgetStatics.Metadata.PARSE_SLOT"))
        assertTrue(widgetContents.contains("var count: Int"))
        assertTrue(widgetContents.contains("internal val STATIC_COUNT_GETTER_SLOT: Int = IWidgetStatics.Metadata.COUNT_GETTER_SLOT"))
        assertTrue(widgetContents.contains("internal val STATIC_COUNT_SETTER_SLOT: Int = IWidgetStatics2.Metadata.COUNT_SETTER_SLOT"))
        assertTrue(widgetContents.contains("val changed: WinRtEvent<EventHandlerCallback<Int>> by"))
        assertTrue(widgetContents.contains("lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("WinRTEventProjectionHelpers.createEventSource("))
        assertTrue(widgetContents.contains("\"Windows.Foundation.EventHandler<Int>\""))
        assertTrue(widgetContents.contains("\"Sample.Foundation.IWidgetStatics\""))
        assertTrue(widgetContents.contains("StaticInterfaces.iWidgetStatics()"))
        assertTrue(widgetContents.contains("STATIC_CHANGED_ADD_SLOT"))
    }

    @Test
    fun generator_binds_projected_object_parameters_via_iwinrtobject() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetValue",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetValue",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetValue",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetValue", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setValue",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "Sample.Foundation.WidgetValue")),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "setNamedValue",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("name", "String"),
                                        WinRtParameterDefinition("value", "Sample.Foundation.WidgetValue"),
                                    ),
                                    methodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setValue",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "Sample.Foundation.WidgetValue")),
                                ),
                                WinRtMethodDefinition(
                                    name = "setNamedValue",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("name", "String"),
                                        WinRtParameterDefinition("value", "Sample.Foundation.WidgetValue"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("IWidget"))
        assertTrue(widgetContents.contains("IWinRTObject"))
        assertTrue(widgetContents.contains("override val nativeObject: ComObjectReference"))
        assertTrue(widgetContents.contains("fun setValue(`value`: WidgetValue)"))
        assertTrue(widgetContents.contains("(value as IWinRTObject).nativeObject.pointer"))
        assertTrue(widgetContents.contains("fun setNamedValue(name: String, `value`: WidgetValue)"))
        assertTrue(widgetContents.contains("HString.create(name).use { __nameAbi ->"))
    }

    @Test
    fun generator_binds_struct_and_non_unit_delegate_member_marshaling() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "location", returnTypeName = "Sample.Foundation.Point", methodRowId = 10),
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetHandler")),
                                    methodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "location", returnTypeName = "Sample.Foundation.Point"),
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetHandler")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("fun location(): Point"))
        assertTrue(widgetContents.contains("Point.Metadata.fromAbi"))
        assertTrue(widgetContents.contains("fun setHandler(handler: WidgetHandler)"))
        assertTrue(widgetContents.contains("WinRtDelegateBridge.createDelegate"))
        assertTrue(widgetContents.contains("WinRtDelegateValueKind.BOOLEAN"))
        assertTrue(widgetContents.contains("handler(__args[0] as String)"))
        assertFalse(widgetContents.contains("fun location(): Point = error(\"Not yet bound to winrt-runtime\")"))
        assertFalse(widgetContents.contains("fun setHandler(handler: WidgetHandler) = error(\"Not yet bound to winrt-runtime\")"))
    }

    @Test
    fun generator_binds_generated_windows_foundation_struct_getters_and_setters() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Single"),
                                WinRtFieldDefinition("Y", "Single"),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Graphics",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Graphics",
                            name = "IAdvancedColorInfo",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "RedPrimary",
                                    typeName = "Windows.Foundation.Point",
                                    getterMethodName = "get_RedPrimary",
                                    getterMethodRowId = 10,
                                    setterMethodName = "put_RedPrimary",
                                    setterMethodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Graphics",
                            name = "AdvancedColorInfo",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Graphics.IAdvancedColorInfo",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    "Sample.Graphics.IAdvancedColorInfo",
                                    isDefault = true,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "RedPrimary",
                                    typeName = "Windows.Foundation.Point",
                                    getterMethodName = "get_RedPrimary",
                                    setterMethodName = "put_RedPrimary",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val contents = filesByName.getValue("AdvancedColorInfo.kt").contents
        val pointContents = filesByName.getValue("Point.kt").contents

        assertTrue(contents, contents.contains("override var redPrimary: Point"))
        assertTrue(contents, contents.contains("PlatformAbi.allocateBytes(__scope, Point.Metadata.layout.sizeBytes)"))
        assertTrue(contents, contents.contains("return Point.Metadata.fromAbi(__resultOut)"))
        assertTrue(contents, contents.contains("Point.Metadata.copyTo(value, __valueAbi)"))
        assertTrue(pointContents, pointContents.contains("Metadata.register()"))
        assertTrue(pointContents, pointContents.contains("WinRtValueBoxingRegistration.registerStruct("))
        assertTrue(pointContents, pointContents.contains("Point::class"))
        assertTrue(pointContents, pointContents.contains("\"struct(Windows.Foundation.Point;f4;f4)\""))
        assertTrue(pointContents, pointContents.contains("emptyArray<Point>()::class"))
    }

    @Test
    fun generator_binds_unit_delegate_parameters_for_methods_and_events() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetHandler")),
                                    methodRowId = 10,
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Updated",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodRowId = 11,
                                    removeMethodRowId = 12,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetHandler")),
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Updated",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodRowId = 11,
                                    removeMethodRowId = 12,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("fun setHandler(handler: WidgetHandler)"))
        assertTrue(widgetContents.contains("fun addUpdated(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(widgetContents.contains("WinRtDelegateBridge.createDelegate"))
        assertTrue(widgetContents.contains("parameterKinds = listOf(WinRtDelegateValueKind.HSTRING)"))
        assertTrue(widgetContents.contains("WinRtDelegateValueKind.UNIT"))
        assertTrue(widgetContents.contains("handler(__args[0] as String)"))
        assertTrue(widgetContents.contains("__handlerHandle.createReference().use { __handlerAbi ->"))
        assertFalse(widgetContents.contains("fun setHandler(handler: WidgetHandler) = error(\"Not yet bound to winrt-runtime\")"))
        assertFalse(widgetContents.contains("fun addUpdated(handler: WidgetHandler): EventRegistrationToken = error(\"Not yet bound to winrt-runtime\")"))
    }

    @Test
    fun generator_projects_delegate_from_abi_wrappers_and_delegate_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getHandler",
                                    returnTypeName = "Sample.Foundation.WidgetHandler",
                                    methodRowId = 10,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getHandler",
                                    returnTypeName = "Sample.Foundation.WidgetHandler",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val widgetContents = filesByName.getValue("Widget.kt").contents
        val delegateContents = filesByName.getValue("WidgetHandler.kt").contents

        assertTrue(delegateContents.contains("internal val DESCRIPTOR: WinRtDelegateDescriptor"))
        assertTrue(delegateContents.contains("WinRtDelegateReference.fromAbi(pointer, DESCRIPTOR)"))
        assertTrue(delegateContents.contains("override val nativeObject: ComObjectReference"))
        assertTrue(delegateContents.contains("override fun invoke("))
        assertTrue(delegateContents.contains("): Boolean"))
        assertTrue(delegateContents.contains("__native.invoke(listOf("))
        assertTrue(delegateContents.contains("as Boolean"))
        assertTrue(widgetContents.contains("return WidgetHandler.Metadata.fromAbi(PlatformAbi.readPointer(__resultOut))"))
        assertFalse(widgetContents.contains("fun getHandler(): WidgetHandler = error(\"Not yet bound to winrt-runtime\")"))
    }

    @Test
    fun generator_rejects_delegates_without_a_single_invoke_method() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                        ),
                    ),
                ),
            ),
        )

        val error = runCatching { KotlinProjectionGenerator().generate(model) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("must expose exactly one Invoke method"))
    }

    @Test
    fun generator_emits_interface_abi_slots_after_inherited_interface_segments() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetBase",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "ping", returnTypeName = "Unit", methodRowId = 10),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetBase"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Title", typeName = "String", getterMethodName = "get_Title", getterMethodRowId = 20),
                            ),
                            events = listOf(
                                WinRtEventDefinition(name = "Updated", delegateTypeName = "Sample.Foundation.WidgetHandler", addMethodRowId = 21, removeMethodRowId = 22),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val baseContents = filesByName.getValue("IWidgetBase.kt").contents
        val widgetContents = filesByName.getValue("IWidget.kt").contents

        assertTrue(baseContents.contains("internal const val PING_SLOT: Int = 6"))
        assertTrue(widgetContents.contains("internal const val TITLE_GETTER_SLOT: Int = 7"))
        assertTrue(widgetContents.contains("internal const val UPDATED_ADD_SLOT: Int = 8"))
        assertTrue(widgetContents.contains("internal const val UPDATED_REMOVE_SLOT: Int = 9"))
    }

    @Test
    fun renderer_resolves_cross_namespace_projection_supertypes() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetBase",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IWidgetView",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidgetBase",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "WidgetView",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetBase",
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }

        val interfaceContents = filesByName.getValue("IWidgetView.kt").contents
        val classContents = filesByName.getValue("WidgetView.kt").contents

        assertTrue(interfaceContents.contains("import io.github.kitectlab.winrt.projections.sample.foundation.IWidgetBase"))
        assertTrue(interfaceContents.contains("interface IWidgetView : IWidgetBase"))
        assertTrue(classContents.contains("import io.github.kitectlab.winrt.projections.sample.foundation.IWidgetBase"))
        assertTrue(classContents.contains("class WidgetView internal constructor("))
        assertTrue(classContents.contains(") : IWidgetBase"))
    }

    @Test
    fun renderer_uses_specialized_attribute_shell_builder() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetAttribute",
                            kind = WinRtTypeKind.RuntimeClass,
                            isAttributeType = true,
                            isSealedType = true,
                        ),
                    ),
                ),
            ),
        )

        val file = KotlinProjectionGenerator().generate(model).single()
        println(file.contents)

        assertTrue(file.contents.contains("public annotation class WidgetAttribute"))
        assertTrue(file.contents.contains("attribute WinRT class shell"))
    }

    @Test
    fun generator_emits_deterministic_shell_files_for_equivalent_metadata() {
        val left = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "WidgetView",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                staticInterfaceNames = listOf("Sample.UI.IWidgetViewStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "create", returnTypeName = "WidgetView", isStatic = true),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                    ),
                ),
            ),
        )
        val right = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "WidgetView",
                            kind = WinRtTypeKind.RuntimeClass,
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.UI.IWidgetViewStatics"),
                                isActivatable = true,
                            ),
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            methods = listOf(
                                WinRtMethodDefinition(name = "create", returnTypeName = "WidgetView", isStatic = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val leftFiles = KotlinProjectionGenerator().generate(left).associateBy(KotlinProjectionFile::relativePath)
        val rightFiles = KotlinProjectionGenerator().generate(right).associateBy(KotlinProjectionFile::relativePath)

        assertEquals(leftFiles.keys, rightFiles.keys)
        assertEquals(leftFiles.mapValues { it.value.contents }, rightFiles.mapValues { it.value.contents })
    }

    @Test
    fun generator_emits_member_surfaces_for_runtime_class_and_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Title", typeName = "String", getterMethodName = "get_Title", setterMethodName = "set_Title"),
                            ),
                            events = listOf(
                                WinRtEventDefinition(name = "Updated", delegateTypeName = "Sample.Foundation.WidgetHandler"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetExtra",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetExtra"),
                            ),
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                                composableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Title", typeName = "String", getterMethodName = "get_Title", setterMethodName = "set_Title"),
                                WinRtPropertyDefinition(name = "MaxCount", typeName = "Int", isStatic = true, getterMethodName = "get_MaxCount"),
                            ),
                            events = listOf(
                                WinRtEventDefinition(name = "Updated", delegateTypeName = "Sample.Foundation.WidgetHandler"),
                                WinRtEventDefinition(name = "Reset", delegateTypeName = "Sample.Foundation.WidgetHandler", isStatic = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val widgetContents = filesByName.getValue("Widget.kt").contents

        assertTrue(widgetContents.contains("class Widget internal constructor("))
        assertTrue(widgetContents.contains(": IWidget"))
        assertTrue(widgetContents.contains("IWidgetExtra"))
        assertTrue(widgetContents.contains("private val _inner: IInspectableReference"))
        assertTrue(widgetContents.contains("private val _iWidgetExtra: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("internal const val TITLE_GETTER_SLOT_OWNER_INTERFACE: String = \"Sample.Foundation.IWidget\""))
        assertTrue(widgetContents.contains("internal const val TITLE_GETTER_SLOT_OWNER_CACHE: String = \"_defaultInterface\""))
        assertTrue(widgetContents.contains("internal val TITLE_GETTER_SLOT: Int = IWidget.Metadata.TITLE_GETTER_SLOT"))
        assertTrue(widgetContents.contains("var title: String"))
        assertTrue(widgetContents.contains("val maxCount: Int"))
        assertTrue(widgetContents.contains("fun addUpdated(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(widgetContents.contains("fun addReset(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
    }

    @Test
    fun generator_emits_runtime_member_binding_descriptors_from_default_and_extra_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetBase",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name", getterMethodRowId = 10),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetBase"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetExtra",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refresh", returnTypeName = "Unit", methodRowId = 20),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetExtra"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refresh", returnTypeName = "Unit"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("internal const val NAME_GETTER_SLOT_OWNER_INTERFACE: String = \"Sample.Foundation.IWidget\""))
        assertTrue(widgetContents.contains("internal const val NAME_GETTER_SLOT_OWNER_CACHE: String = \"_defaultInterface\""))
        assertTrue(widgetContents.contains("internal val NAME_GETTER_SLOT: Int = IWidgetBase.Metadata.NAME_GETTER_SLOT"))
        assertTrue(widgetContents.contains("internal const val REFRESH_SLOT_OWNER_INTERFACE: String = \"Sample.Foundation.IWidgetExtra\""))
        assertTrue(widgetContents.contains("internal const val REFRESH_SLOT_OWNER_CACHE: String = \"_iWidgetExtra\""))
        assertTrue(widgetContents.contains("internal val REFRESH_SLOT: Int = IWidgetExtra.Metadata.REFRESH_SLOT"))
    }

    @Test
    fun generator_binds_simple_runtime_getters_and_no_arg_methods() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refresh", returnTypeName = "Unit", methodRowId = 10),
                                WinRtMethodDefinition(name = "version", returnTypeName = "Int", methodRowId = 11),
                                WinRtMethodDefinition(name = "isReady", returnTypeName = "Boolean", methodRowId = 12),
                                WinRtMethodDefinition(name = "label", returnTypeName = "String", methodRowId = 13),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name", getterMethodRowId = 14),
                                WinRtPropertyDefinition(name = "Count", typeName = "Int", getterMethodName = "get_Count", getterMethodRowId = 15),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refresh", returnTypeName = "Unit"),
                                WinRtMethodDefinition(name = "version", returnTypeName = "Int"),
                                WinRtMethodDefinition(name = "isReady", returnTypeName = "Boolean"),
                                WinRtMethodDefinition(name = "label", returnTypeName = "String"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name"),
                                WinRtPropertyDefinition(name = "Count", typeName = "Int", getterMethodName = "get_Count"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertFalse(widgetContents.contains("WinRtAbiMarshalers"))
        assertTrue(widgetContents.contains("fun refresh()"))
        assertTrue(widgetContents.contains("Metadata.REFRESH_SLOT"))
        assertTrue(widgetContents.contains("fun version(): Int"))
        assertTrue(widgetContents.contains("Metadata.VERSION_SLOT"))
        assertTrue(widgetContents.contains("fun isReady(): Boolean"))
        assertTrue(widgetContents.contains("Metadata.ISREADY_SLOT"))
        assertTrue(widgetContents.contains("fun label(): String"))
        assertTrue(widgetContents.contains("ComVtableInvoker.invoke"))
        assertFalse(widgetContents.contains("invokeAbi("))
        assertTrue(widgetContents.contains("PlatformAbi.confinedScope().use { __scope ->"))
        assertTrue(widgetContents.contains("HString.fromHandle("))
        assertTrue(widgetContents.contains("Metadata.LABEL_SLOT"))
        assertTrue(widgetContents.contains("Metadata.NAME_GETTER_SLOT"))
        assertTrue(widgetContents.contains("Metadata.COUNT_GETTER_SLOT"))
    }

    @Test
    fun generator_binds_single_parameter_string_and_uint32_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getNamedString",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "getStringAt",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("index", "UInt")),
                                    methodRowId = 11,
                                ),
                                WinRtMethodDefinition(
                                    name = "rename",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                    methodRowId = 12,
                                ),
                                WinRtMethodDefinition(
                                    name = "setSelectedIndex",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("index", "UInt")),
                                    methodRowId = 13,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Title",
                                    typeName = "String",
                                    getterMethodName = "get_Title",
                                    getterMethodRowId = 14,
                                    setterMethodName = "put_Title",
                                    setterMethodRowId = 15,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getNamedString",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                ),
                                WinRtMethodDefinition(
                                    name = "getStringAt",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("index", "UInt")),
                                ),
                                WinRtMethodDefinition(
                                    name = "rename",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                ),
                                WinRtMethodDefinition(
                                    name = "setSelectedIndex",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("index", "UInt")),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Title",
                                    typeName = "String",
                                    getterMethodName = "get_Title",
                                    setterMethodName = "put_Title",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertFalse(widgetContents.contains("WinRtAbiMarshalers"))
        assertTrue(widgetContents.contains("fun getNamedString(name: String): String"))
        assertTrue(widgetContents.contains("HString.create(name).use { __nameAbi ->"))
        assertFalse(widgetContents.contains("-> {"))
        assertTrue(widgetContents.contains("Metadata.GETNAMEDSTRING_SLOT"))
        assertTrue(widgetContents.contains("fun getStringAt(index: UInt): String"))
        assertTrue(widgetContents.contains("index.toInt()"))
        assertTrue(widgetContents.contains("Metadata.GETSTRINGAT_SLOT"))
        assertTrue(widgetContents.contains("fun rename(name: String)"))
        assertTrue(widgetContents.contains("Metadata.RENAME_SLOT"))
        assertTrue(widgetContents.contains("fun setSelectedIndex(index: UInt)"))
        assertTrue(widgetContents.contains("Metadata.SETSELECTEDINDEX_SLOT"))
        assertTrue(widgetContents.contains("var title: String"))
        assertTrue(widgetContents.contains("Metadata.TITLE_SETTER_SLOT"))
    }

    @Test
    fun generator_binds_single_parameter_boolean_and_double_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setReady",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("ready", "Boolean")),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "createNumberValue",
                                    returnTypeName = "Sample.Foundation.WidgetValue",
                                    parameters = listOf(WinRtParameterDefinition("value", "Double")),
                                    methodRowId = 11,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Ready",
                                    typeName = "Boolean",
                                    getterMethodName = "get_Ready",
                                    getterMethodRowId = 12,
                                    setterMethodName = "put_Ready",
                                    setterMethodRowId = 13,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetValue",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetValue",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetValue", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetValue",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setReady",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("ready", "Boolean")),
                                ),
                                WinRtMethodDefinition(
                                    name = "createNumberValue",
                                    returnTypeName = "Sample.Foundation.WidgetValue",
                                    parameters = listOf(WinRtParameterDefinition("value", "Double")),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Ready",
                                    typeName = "Boolean",
                                    getterMethodName = "get_Ready",
                                    setterMethodName = "put_Ready",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertFalse(widgetContents.contains("WinRtAbiMarshalers"))
        assertTrue(widgetContents.contains("fun setReady(ready: Boolean)"))
        assertTrue(widgetContents.contains("if (ready) 1.toByte() else 0.toByte()"))
        assertTrue(widgetContents.contains("Metadata.SETREADY_SLOT"))
        assertTrue(widgetContents.contains("fun createNumberValue("))
        assertTrue(widgetContents.contains("Double): WidgetValue"))
        assertTrue(widgetContents.contains("Metadata.CREATENUMBERVALUE_SLOT"))
        assertTrue(widgetContents.contains("WidgetValue.Metadata.wrap"))
        assertTrue(widgetContents.contains("var ready: Boolean"))
        assertTrue(widgetContents.contains("Metadata.READY_SETTER_SLOT"))
    }

    @Test
    fun generator_applies_cswinrt_collection_async_and_custom_type_mappings() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetCollection",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("12345678-2222-3333-4444-555555555555"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IIterable<String>",
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "asReadOnly",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<String>",
                                ),
                                WinRtMethodDefinition(
                                    name = "asMap",
                                    returnTypeName = "Windows.Foundation.Collections.IMap<String, Int>",
                                ),
                                WinRtMethodDefinition(
                                    name = "refreshAsync",
                                    returnTypeName = "Windows.Foundation.IAsyncAction",
                                ),
                                WinRtMethodDefinition(
                                    name = "fetchAsync",
                                    returnTypeName = "Windows.Foundation.IAsyncOperation<String>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "SourceUri",
                                    typeName = "Windows.Foundation.Uri",
                                    getterMethodName = "get_SourceUri",
                                ),
                                WinRtPropertyDefinition(
                                    name = "Selection",
                                    typeName = "Windows.Foundation.IReference<Int>",
                                    getterMethodName = "get_Selection",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val file = KotlinProjectionGenerator().generate(model).single()

        assertFalse(file.contents, file.contents.contains("import java.net.URI"))
        assertTrue(file.contents, file.contents.contains("import io.github.kitectlab.winrt.runtime.WinRtUri"))
        assertTrue(file.contents, file.contents.contains("import io.github.kitectlab.winrt.runtime.WinRtAsyncActionReference"))
        assertTrue(file.contents, file.contents.contains("import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationReference"))
        assertTrue(file.contents, file.contents.contains("public interface IWidgetCollection : Iterable<String>"))
        assertTrue(file.contents, file.contents.contains("fun asReadOnly(): List<String>"))
        assertTrue(file.contents, file.contents.contains("fun asMap(): MutableMap<String, Int>"))
        assertTrue(file.contents, file.contents.contains("fun refreshAsync(): WinRtAsyncActionReference"))
        assertTrue(file.contents, file.contents.contains("fun fetchAsync(): WinRtAsyncOperationReference<String>"))
        assertTrue(file.contents, file.contents.contains("val sourceUri: WinRtUri"))
        assertTrue(file.contents, file.contents.contains("val selection: Int?"))
    }

    @Test
    fun generator_hands_custom_mapped_member_call_modes_to_companions_and_support_plan() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            genericParameterCount = 1,
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val iterableContents = filesByName.getValue("IIterable.kt").contents
        val shapePlanContents = filesByName.getValue("WinRTTypeShapeWriterPlan.kt").contents

        assertTrue(iterableContents, iterableContents.contains("internal val CUSTOM_MAPPED_MEMBER_PLANS: List<String>"))
        assertTrue(iterableContents, iterableContents.contains("internal const val CUSTOM_MAPPED_MEMBER_CALL_MODE: String = \"static-abi\""))
        assertTrue(iterableContents, iterableContents.contains("internal const val CUSTOM_MAPPED_MEMBER_EXPLICIT: Boolean = true"))
        assertTrue(iterableContents, iterableContents.contains("internal const val CUSTOM_MAPPED_MEMBER_PRIVATE: Boolean = false"))
        assertTrue(shapePlanContents, shapePlanContents.contains("mappedCallMode = \"static-abi\""))
        assertTrue(shapePlanContents, shapePlanContents.contains("mappedExplicit = true"))
        assertTrue(shapePlanContents, shapePlanContents.contains("mappedPrivate = false"))
    }

    @Test
    fun generator_emits_kmp_metadata_structs_instead_of_dotnet_value_type_aliases() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Float"),
                                WinRtFieldDefinition("Y", "Float"),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation.Numerics",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Numerics",
                            name = "Vector3",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Float"),
                                WinRtFieldDefinition("Y", "Float"),
                                WinRtFieldDefinition("Z", "Float"),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Media",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media",
                            name = "Matrix",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("M11", "Double"),
                                WinRtFieldDefinition("M12", "Double"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media",
                            name = "MatrixHelper",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }

        assertTrue(filesByName.getValue("Point.kt").contents.contains("public class Point("))
        assertTrue(filesByName.getValue("Vector3.kt").contents.contains("public class Vector3("))
        assertTrue(filesByName.getValue("Matrix.kt").contents.contains("public class Matrix("))
        assertFalse(filesByName.containsKey("MatrixHelper.kt"))
        assertEquals(null, mappedTypeByAbiName("Windows.Foundation.Point"))
        assertEquals(null, mappedTypeByAbiName("Windows.Foundation.Numerics.Vector3"))
        assertEquals(null, mappedTypeByAbiName("Microsoft.UI.Xaml.Media.Matrix"))
    }

    @Test
    fun generator_uses_runtime_backed_cswinrt_system_mapped_type_names() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "EventHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555551"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("sender", "System.Object"),
                                        WinRtParameterDefinition("args", "T0"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "HResult",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("value", "Int")),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "ISystemMappedSurface",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555552"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "lastFailure",
                                    returnTypeName = "Windows.Foundation.HResult",
                                ),
                                WinRtMethodDefinition(
                                    name = "command",
                                    returnTypeName = "Microsoft.UI.Xaml.Input.ICommand",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "PropertyChanged",
                                    typeName = "Microsoft.UI.Xaml.Data.INotifyPropertyChanged",
                                    getterMethodName = "get_PropertyChanged",
                                ),
                                WinRtPropertyDefinition(
                                    name = "CollectionChanged",
                                    typeName = "Windows.UI.Xaml.Interop.INotifyCollectionChanged",
                                    getterMethodName = "get_CollectionChanged",
                                ),
                                WinRtPropertyDefinition(
                                    name = "CollectionChangedArgs",
                                    typeName = "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventArgs",
                                    getterMethodName = "get_CollectionChangedArgs",
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Windows.Foundation.EventHandler<Int>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val file = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.endsWith("ISystemMappedSurface.kt") }

        assertTrue(file.contents, file.contents.contains("import io.github.kitectlab.winrt.runtime.EventHandlerCallback"))
        assertTrue(file.contents, file.contents.contains("import io.github.kitectlab.winrt.runtime.WinRtCommand"))
        assertTrue(file.contents, file.contents.contains("import io.github.kitectlab.winrt.runtime.WinRtCollectionChangedNotifier"))
        assertTrue(file.contents, file.contents.contains("import io.github.kitectlab.winrt.runtime.WinRtNotifyCollectionChangedEventArgs"))
        assertTrue(file.contents, file.contents.contains("import io.github.kitectlab.winrt.runtime.WinRtPropertyChangedNotifier"))
        assertFalse(file.contents, file.contents.contains("import java.time"))
        assertTrue(file.contents, file.contents.contains("fun lastFailure(): Exception"))
        assertTrue(file.contents, file.contents.contains("fun command(): WinRtCommand"))
        assertTrue(file.contents, file.contents.contains("val propertyChanged: WinRtPropertyChangedNotifier"))
        assertTrue(file.contents, file.contents.contains("val collectionChanged: WinRtCollectionChangedNotifier"))
        assertTrue(file.contents, file.contents.contains("val collectionChangedArgs: WinRtNotifyCollectionChangedEventArgs"))
        assertTrue(file.contents, file.contents.contains("fun addChanged(handler: EventHandlerCallback<Int>): EventRegistrationToken"))
    }

    @Test
    fun generator_binds_custom_system_struct_abi_through_runtime_marshaler_facade() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "DateTime",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("universalTime", "Long")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "TimeSpan",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("duration", "Long")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "HResult",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("value", "Int")),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IClock",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555553"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "now",
                                    returnTypeName = "Windows.Foundation.DateTime",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setDelay",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("delay", "Windows.Foundation.TimeSpan")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "setFailure",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("error", "Windows.Foundation.HResult")),
                                    methodRowId = 8,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Clock",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IClock",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "now",
                                    returnTypeName = "Windows.Foundation.DateTime",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setDelay",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("delay", "Windows.Foundation.TimeSpan")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "setFailure",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("error", "Windows.Foundation.HResult")),
                                    methodRowId = 8,
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IClock",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("IClock.kt").contents
        val classContents = filesByName.getValue("Clock.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("import kotlin.time.Duration"))
        assertTrue(interfaceContents, interfaceContents.contains("import kotlin.time.Instant"))
        assertTrue(interfaceContents, interfaceContents.contains("fun now(): Instant"))
        assertTrue(interfaceContents, interfaceContents.contains("fun setDelay(delay: Duration)"))
        assertTrue(interfaceContents, interfaceContents.contains("fun setFailure(error: Exception)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.dateTimeFromAbi(__resultOut)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.copyTimeSpanTo(delay, __delayAbi)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.copyHResultTo(error, __errorAbi)"))
        assertFalse(classContents, classContents.contains(".Metadata.fromAbi(__resultOut)"))
        assertFalse(classContents, classContents.contains(".Metadata.copyTo(delay"))
        assertFalse(classContents, classContents.contains(".Metadata.copyTo(error"))
    }

    @Test
    fun generator_binds_xaml_type_name_to_kclass_through_runtime_marshaler_facade() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Xaml",
                            name = "ITypeHost",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555552"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "currentType",
                                    returnTypeName = "Windows.UI.Xaml.Interop.TypeName",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setCurrentType",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("type", "Windows.UI.Xaml.Interop.TypeName")),
                                    methodRowId = 7,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Xaml",
                            name = "TypeHost",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Xaml.ITypeHost",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "currentType",
                                    returnTypeName = "Windows.UI.Xaml.Interop.TypeName",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setCurrentType",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("type", "Windows.UI.Xaml.Interop.TypeName")),
                                    methodRowId = 7,
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Xaml.ITypeHost",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("ITypeHost.kt").contents
        val classContents = filesByName.getValue("TypeHost.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("import kotlin.reflect.KClass"))
        assertTrue(interfaceContents, interfaceContents.contains("fun currentType(): KClass<*>?"))
        assertTrue(interfaceContents, interfaceContents.contains("fun setCurrentType(type: KClass<*>?)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.typeNameFromAbi(__resultOut)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.disposeTypeNameAbi(__resultOut)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.copyTypeNameTo(type, __typeAbi)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.disposeTypeNameAbi(__typeAbi)"))
        assertFalse(classContents, classContents.contains("TypeName.Metadata"))
    }

    @Test
    fun generator_covers_short_abi_argument_lists_without_sample_specific_overloads() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.FastAbi",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "IShape",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555551"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setEnabled",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("enabled", "Boolean")),
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setOffset",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("offset", "Short")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "setOpacity",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("opacity", "Float")),
                                    methodRowId = 8,
                                ),
                                WinRtMethodDefinition(
                                    name = "configure",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("enabled", "Boolean"),
                                        WinRtParameterDefinition("offset", "Short"),
                                        WinRtParameterDefinition("opacity", "Float"),
                                    ),
                                    methodRowId = 9,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "Shape",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.FastAbi.IShape",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setEnabled",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("enabled", "Boolean")),
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setOffset",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("offset", "Short")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "setOpacity",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("opacity", "Float")),
                                    methodRowId = 8,
                                ),
                                WinRtMethodDefinition(
                                    name = "configure",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("enabled", "Boolean"),
                                        WinRtParameterDefinition("offset", "Short"),
                                        WinRtParameterDefinition("opacity", "Float"),
                                    ),
                                    methodRowId = 9,
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.FastAbi.IShape",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val shapeContents = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.endsWith("/Shape.kt") }
            .contents

        assertTrue(shapeContents, shapeContents.contains("arg0 = if (enabled) 1.toByte() else 0.toByte()"))
        assertTrue(shapeContents, shapeContents.contains("ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer"))
        assertTrue(shapeContents, shapeContents.contains("Metadata.SETOFFSET_SLOT"))
        assertTrue(shapeContents, shapeContents.contains("Metadata.SETOPACITY_SLOT"))
        assertTrue(shapeContents, shapeContents.contains("Metadata.CONFIGURE_SLOT"))
        assertTrue(shapeContents, shapeContents.contains("arg0 = offset"))
        assertTrue(shapeContents, shapeContents.contains("arg0 = opacity"))
        assertTrue(shapeContents, shapeContents.contains("arg1 = offset"))
        assertFalse(shapeContents, shapeContents.contains("invokeGenericArgs"))
    }

    @Test
    fun generator_hands_fast_abi_class_slots_to_metadata_companion() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.FastAbi",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555561"),
                            methods = listOf(
                                WinRtMethodDefinition("get_Name", "String", isSpecialName = true, methodRowId = 6),
                                WinRtMethodDefinition("set_Name", "Unit", parameters = listOf(WinRtParameterDefinition("value", "String")), isSpecialName = true, methodRowId = 7),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                    setterMethodName = "set_Name",
                                    getterMethodRowId = 6,
                                    setterMethodRowId = 7,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "IWidgetOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555562"),
                            isExclusiveTo = true,
                            customAttributes = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtCustomAttributeDefinition(
                                    typeName = "Windows.Foundation.Metadata.ExclusiveToAttribute",
                                    fixedArguments = listOf(
                                        io.github.kitectlab.winrt.metadata.WinRtCustomAttributeValue.TypeValue("Sample.FastAbi.Widget"),
                                    ),
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition("get_Mode", "Int", isSpecialName = true, methodRowId = 8),
                                WinRtMethodDefinition("set_Mode", "Unit", parameters = listOf(WinRtParameterDefinition("value", "Int")), isSpecialName = true, methodRowId = 9),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Mode",
                                    typeName = "Int",
                                    getterMethodName = "get_Mode",
                                    setterMethodName = "set_Mode",
                                    getterMethodRowId = 8,
                                    setterMethodRowId = 9,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            isFastAbi = true,
                            defaultInterfaceName = "Sample.FastAbi.IWidget",
                            methods = listOf(
                                WinRtMethodDefinition("get_Mode", "Int", isSpecialName = true, methodRowId = 8),
                                WinRtMethodDefinition("set_Mode", "Unit", parameters = listOf(WinRtParameterDefinition("value", "Int")), isSpecialName = true, methodRowId = 9),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Mode",
                                    typeName = "Int",
                                    getterMethodName = "get_Mode",
                                    setterMethodName = "set_Mode",
                                    getterMethodRowId = 8,
                                    setterMethodRowId = 9,
                                ),
                            ),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IWidget", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IWidgetOverrides"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val widgetContents = filesByName.getValue("Widget.kt").contents
        val defaultInterfaceContents = filesByName.getValue("IWidget.kt").contents
        val exclusiveInterfaceContents = filesByName.getValue("IWidgetOverrides.kt").contents

        assertTrue(widgetContents, widgetContents.contains("internal val FAST_ABI_INTERFACE_SLOTS: List<String>"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidget|default=true|start=6|count=2|hierarchyOffset=0|next=8"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidgetOverrides|default=false|start=8|count=2|hierarchyOffset=0|next=10"))
        assertTrue(widgetContents, widgetContents.contains("internal val FAST_ABI_PROPERTY_SLOTS: List<String>"))
        assertTrue(widgetContents, widgetContents.contains("Name|start=6|get=6|set=7"))
        assertTrue(widgetContents, widgetContents.contains("Mode|start=8|get=8|set=9"))
        assertTrue(widgetContents, widgetContents.contains("internal val OBJECT_REFERENCE_NAMES: List<String> = listOf(\"Sample_FastAbi_IWidgetCache\")"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidget|cache=Sample_FastAbi_IWidgetCache|default=true|skip=|inner=false|defaultObjRef=false|hierarchy=|defaultObjRefSlot=|generic=false"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidgetOverrides|cache=Sample_FastAbi_IWidgetOverridesCache|default=false|skip=fast-abi-non-default-exclusive"))
        assertTrue(widgetContents, widgetContents.contains("internal const val MODE_GETTER_SLOT_OWNER_CACHE: String = \"_defaultInterface\""))
        assertFalse(widgetContents, widgetContents.contains("private val _iWidgetOverrides"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("internal val FAST_ABI_INTERFACE_SLOTS: List<String>"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("Sample.FastAbi.IWidget|default=true|start=6|count=2|hierarchyOffset=0|next=8"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("Sample.FastAbi.IWidgetOverrides|default=false|start=8|count=2|hierarchyOffset=0|next=10"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("internal const val NAME_GETTER_SLOT: Int = 6"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("internal const val NAME_SETTER_SLOT: Int = 7"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("internal val FAST_ABI_INTERFACE_SLOTS: List<String>"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("Sample.FastAbi.IWidget|default=true|start=6|count=2|hierarchyOffset=0|next=8"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("Sample.FastAbi.IWidgetOverrides|default=false|start=8|count=2|hierarchyOffset=0|next=10"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("internal const val MODE_GETTER_SLOT: Int = 8"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("internal const val MODE_SETTER_SLOT: Int = 9"))
    }

    @Test
    fun generator_consumes_cswinrt_object_reference_cache_plans() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.FastAbi",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "IDefaultExclusive",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555571"),
                            isExclusiveTo = true,
                            customAttributes = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtCustomAttributeDefinition(
                                    typeName = "Windows.Foundation.Metadata.ExclusiveToAttribute",
                                    fixedArguments = listOf(
                                        io.github.kitectlab.winrt.metadata.WinRtCustomAttributeValue.TypeValue("Sample.FastAbi.FastDefaultExclusiveWidget"),
                                    ),
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition("Start", "Unit", methodRowId = 10),
                                WinRtMethodDefinition("Stop", "Unit", methodRowId = 11),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "IGeneric",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555572"),
                            genericParameterCount = 1,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "FastDefaultExclusiveWidget",
                            kind = WinRtTypeKind.RuntimeClass,
                            isFastAbi = true,
                            isSealedType = false,
                            defaultInterfaceName = "Sample.FastAbi.IDefaultExclusive",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IDefaultExclusive", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IGeneric<String>"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.endsWith("/FastDefaultExclusiveWidget.kt") }
            .contents

        assertTrue(widgetContents, widgetContents.contains("private val _defaultInterface: ComObjectReference"))
        assertTrue(widgetContents, widgetContents.contains("getDefaultInterfaceObjectReference(8)"))
        assertTrue(widgetContents, widgetContents.contains("initializeBySourceType("))
        assertFalse(widgetContents, widgetContents.contains("initializeDependencies(entry) { }"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IDefaultExclusive|cache=Sample_FastAbi_IDefaultExclusiveCache|default=true|skip=|inner=false|defaultObjRef=true|hierarchy=0|defaultObjRefSlot=8|generic=false"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IGeneric<String>|cache=Sample_FastAbi_IGeneric_String_Cache|default=false|skip=|inner=false|defaultObjRef=false|hierarchy=|defaultObjRefSlot=|generic=true"))
    }

    @Test
    fun generator_binds_custom_object_mapped_abi_through_runtime_marshaler_facade() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IUriCommandHost",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555554"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "sourceUri",
                                    returnTypeName = "Windows.Foundation.Uri",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setSourceUri",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("sourceUri", "Windows.Foundation.Uri")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "command",
                                    returnTypeName = "Microsoft.UI.Xaml.Input.ICommand",
                                    methodRowId = 8,
                                ),
                                WinRtMethodDefinition(
                                    name = "setCommand",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("command", "Microsoft.UI.Xaml.Input.ICommand")),
                                    methodRowId = 9,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "UriCommandHost",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.UI.IUriCommandHost",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "sourceUri",
                                    returnTypeName = "Windows.Foundation.Uri",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setSourceUri",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("sourceUri", "Windows.Foundation.Uri")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "command",
                                    returnTypeName = "Microsoft.UI.Xaml.Input.ICommand",
                                    methodRowId = 8,
                                ),
                                WinRtMethodDefinition(
                                    name = "setCommand",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("command", "Microsoft.UI.Xaml.Input.ICommand")),
                                    methodRowId = 9,
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.UI.IUriCommandHost",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("IUriCommandHost.kt").contents
        val classContents = filesByName.getValue("UriCommandHost.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("fun sourceUri(): WinRtUri"))
        assertTrue(interfaceContents, interfaceContents.contains("fun command(): WinRtCommand"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.uriFromAbi(PlatformAbi.readPointer(__resultOut))"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.objectFromAbi(PlatformAbi.readPointer(__resultOut),"))
        assertTrue(classContents, classContents.contains("WinRtTypeHandle(\"io.github.kitectlab.winrt.runtime.WinRtCommand\""))
        assertTrue(classContents, classContents.contains("Guid(\"E5AF3542-CA67-4081-995B-709DD13792DF\")), WinRtCommand::class)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.createObjectReference(sourceUri,"))
        assertTrue(classContents, classContents.contains("Guid(\"9E365E57-48B2-4160-956F-C7385120BBFC\")).use { __sourceUriAbi ->"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.createObjectReference(command,"))
        assertTrue(classContents, classContents.contains("Guid(\"E5AF3542-CA67-4081-995B-709DD13792DF\")).use { __commandAbi ->"))
        assertFalse(classContents, classContents.contains("sourceUri as IWinRTObject"))
        assertFalse(classContents, classContents.contains("command as IWinRTObject"))
    }

    @Test
    fun generator_emits_runtime_backed_async_abi_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IStream",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("12345678-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refreshAsync", returnTypeName = "Windows.Foundation.IAsyncAction", methodRowId = 6),
                                WinRtMethodDefinition(name = "fetchAsync", returnTypeName = "Windows.Foundation.IAsyncOperation<String>", methodRowId = 7),
                                WinRtMethodDefinition(name = "fetchStreamAsync", returnTypeName = "Windows.Foundation.IAsyncOperation<Sample.Foundation.IStream>", methodRowId = 8),
                                WinRtMethodDefinition(name = "refreshWithProgressAsync", returnTypeName = "Windows.Foundation.IAsyncActionWithProgress<Int>", methodRowId = 9),
                                WinRtMethodDefinition(name = "fetchWithProgressAsync", returnTypeName = "Windows.Foundation.IAsyncOperationWithProgress<String, UInt>", methodRowId = 10),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refreshAsync", returnTypeName = "Windows.Foundation.IAsyncAction"),
                                WinRtMethodDefinition(name = "fetchAsync", returnTypeName = "Windows.Foundation.IAsyncOperation<String>"),
                                WinRtMethodDefinition(name = "fetchStreamAsync", returnTypeName = "Windows.Foundation.IAsyncOperation<Sample.Foundation.IStream>"),
                                WinRtMethodDefinition(name = "refreshWithProgressAsync", returnTypeName = "Windows.Foundation.IAsyncActionWithProgress<Int>"),
                                WinRtMethodDefinition(name = "fetchWithProgressAsync", returnTypeName = "Windows.Foundation.IAsyncOperationWithProgress<String, UInt>"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertFalse(widgetContents.contains("CompletableFuture"))
        assertTrue(widgetContents.contains("fun refreshAsync(): WinRtAsyncActionReference"))
        assertTrue(widgetContents.contains("return WinRtAsyncActionReference(PlatformAbi.readPointer(__resultOut))"))
        assertTrue(widgetContents.contains("fun fetchAsync(): WinRtAsyncOperationReference<String>"))
        assertTrue(widgetContents.contains("interfaceId = WinRtAsyncOperationReference.interfaceId(WinRtTypeSignature.string())"))
        assertTrue(widgetContents.contains("completedHandlerInterfaceId ="))
        assertTrue(widgetContents.contains("WinRtAsyncOperationReference.completedHandlerInterfaceId(WinRtTypeSignature.string())"))
        assertTrue(widgetContents.contains("ComVtableInvoker.invokeArgs(__operation.pointer,"))
        assertTrue(widgetContents.contains("WinRtAsyncOperationVftblSlots.GetResults, __operationResultOut)"))
        assertTrue(widgetContents.contains("HString.fromHandle(PlatformAbi.readPointer(__operationResultOut), owner = true).use"))
        assertTrue(widgetContents.contains("it.toKString()"))
        assertTrue(widgetContents.contains("fun fetchStreamAsync(): WinRtAsyncOperationReference<IStream>"))
        assertTrue(widgetContents.contains("WinRtTypeSignature.guid(IStream.Metadata.IID)"))
        assertTrue(widgetContents.contains("IStream.Metadata.wrap(IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__operationResultOut))))"))
        assertTrue(widgetContents.contains("fun refreshWithProgressAsync(): WinRtAsyncActionWithProgressReference<Int>"))
        assertTrue(widgetContents.contains("WinRtAsyncActionWithProgressReference.interfaceId(WinRtTypeSignature.int32())"))
        assertTrue(widgetContents.contains("WinRtAsyncActionWithProgressReference.progressHandlerInterfaceId(WinRtTypeSignature.int32())"))
        assertTrue(widgetContents.contains("WinRtAsyncActionWithProgressReference.completedHandlerInterfaceId(WinRtTypeSignature.int32())"))
        assertTrue(widgetContents.contains("fun fetchWithProgressAsync(): WinRtAsyncOperationWithProgressReference<String, UInt>"))
        assertTrue(widgetContents.contains("WinRtAsyncOperationWithProgressReference.interfaceId("))
        assertTrue(widgetContents.contains("WinRtAsyncOperationWithProgressReference.progressHandlerInterfaceId("))
        assertTrue(widgetContents.contains("WinRtAsyncOperationWithProgressReference.completedHandlerInterfaceId("))
        assertTrue(widgetContents.contains("WinRtTypeSignature.string()"))
        assertTrue(widgetContents.contains("WinRtTypeSignature.uint32()"))
        assertTrue(widgetContents.contains("WinRtAsyncOperationWithProgressVftblSlots.GetResults, __operationResultOut)"))
    }

    @Test
    fun generator_applies_winui_bindable_collection_mappings() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IBindableItemsView",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("87654321-2222-3333-4444-555555555555"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Microsoft.UI.Xaml.Interop.IBindableIterable",
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "snapshot",
                                    returnTypeName = "Microsoft.UI.Xaml.Interop.IBindableVector",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Items",
                                    typeName = "Microsoft.UI.Xaml.Interop.IBindableVector",
                                    getterMethodName = "get_Items",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val file = KotlinProjectionGenerator().generate(model).single()

        assertTrue(file.contents, file.contents.contains("public interface IBindableItemsView : Iterable<Any?>"))
        assertTrue(file.contents, file.contents.contains("fun snapshot(): MutableList<Any?>"))
        assertTrue(file.contents, file.contents.contains("val items: MutableList<Any?>"))
    }

    @Test
    fun generator_projects_read_only_iterable_and_vector_view_runtime_surfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555551"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IIterable<String>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameList",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555552"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IVectorView<String>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NameIterable",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameIterable",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.INameIterable",
                                    isDefault = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NameList",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameList",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.INameList",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val files = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val iterableContents = files.getValue("NameIterable.kt").contents
        val listContents = files.getValue("NameList.kt").contents

        assertTrue(iterableContents, iterableContents.contains("Iterable<String>,"))
        assertTrue(iterableContents, iterableContents.contains("override fun iterator(): Iterator<String>"))
        assertTrue(iterableContents, iterableContents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.CURRENT_GETTER_SLOT"))
        assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.HASCURRENT_GETTER_SLOT"))
        assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.MOVENEXT_SLOT"))

        assertTrue(listContents, listContents.contains("List<String>,"))
        assertTrue(listContents, listContents.contains("override val size: Int"))
        assertTrue(listContents, listContents.contains("override fun get(index: Int): String"))
        assertTrue(listContents, listContents.contains("AbstractList"))
        assertTrue(listContents, listContents.contains("IVectorView.Metadata.GETAT_SLOT"))
        assertTrue(listContents, listContents.contains("IVectorView.Metadata.SIZE_GETTER_SLOT"))
    }

    @Test
    fun generator_projects_read_only_map_view_runtime_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555553"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IMapView<String, Int>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NameMap",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameMap",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.INameMap",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val mapContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("NameMap.kt")
            .contents

        assertTrue(mapContents, mapContents.contains("Map<String, Int>,"))
        assertTrue(mapContents, mapContents.contains("AbstractMap"))
        assertTrue(mapContents, mapContents.contains("IMapView.Metadata.LOOKUP_SLOT"))
        assertTrue(mapContents, mapContents.contains("IMapView.Metadata.HASKEY_SLOT"))
        assertTrue(mapContents, mapContents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(mapContents, mapContents.contains("IIterator.Metadata.CURRENT_GETTER_SLOT"))
        assertTrue(mapContents, mapContents.contains("IKeyValuePair.Metadata.KEY_GETTER_SLOT"))
        assertTrue(mapContents, mapContents.contains("IKeyValuePair.Metadata.VALUE_GETTER_SLOT"))
    }

    @Test
    fun generator_substitutes_generic_collection_closure_arguments() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IKeyValuePair",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            genericParameterCount = 2,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            genericParameterCount = 1,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IMapView",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            genericParameterCount = 2,
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IKeyValuePair<T0, T1>>",
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IObjectMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("44444444-4444-4444-4444-444444444444"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IMapView<String, System.Object>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ObjectMap",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IObjectMap",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IObjectMap",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }

        val mapViewInterfaceContents = filesByName.getValue("IMapView.kt").contents
        assertTrue(mapViewInterfaceContents, mapViewInterfaceContents.contains("public interface IMapView<T0, T1>"))
        assertFalse(mapViewInterfaceContents, mapViewInterfaceContents.contains("import T0"))
        assertFalse(mapViewInterfaceContents, mapViewInterfaceContents.contains("import T1"))

        val iterableInterfaceContents = filesByName.getValue("IIterable.kt").contents
        assertTrue(iterableInterfaceContents, iterableInterfaceContents.contains("public interface IIterable<T0>"))
        assertFalse(iterableInterfaceContents, iterableInterfaceContents.contains("import T0"))

        val mapContents = filesByName
            .getValue("ObjectMap.kt")
            .contents

        assertTrue(mapContents, mapContents.contains("Map<String, IInspectableReference>,"))
        assertTrue(mapContents, mapContents.contains("Map.Entry<String, IInspectableReference>"))
        assertTrue(mapContents, mapContents.contains("IKeyValuePair.Metadata.KEY_GETTER_SLOT"))
        assertTrue(mapContents, mapContents.contains("IKeyValuePair.Metadata.VALUE_GETTER_SLOT"))
        assertFalse(mapContents, mapContents.contains("T0"))
        assertFalse(mapContents, mapContents.contains("T1"))
        assertFalse(mapContents, mapContents.contains("Unsupported"))
    }

    @Test
    fun generator_suppresses_redundant_collection_surfaces_for_runtime_class_mappings() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IKeyValuePair",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            genericParameterCount = 2,
                            properties = listOf(
                                WinRtPropertyDefinition("Key", "T0", getterMethodName = "get_Key"),
                                WinRtPropertyDefinition("Value", "T1", getterMethodName = "get_Value"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            genericParameterCount = 1,
                            methods = listOf(WinRtMethodDefinition("First", "Windows.Foundation.Collections.IIterator<T0>")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            genericParameterCount = 1,
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IIterable<T0>")),
                            methods = listOf(
                                WinRtMethodDefinition("GetAt", "T0", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition("SetAt", "Unit", parameters = listOf(WinRtParameterDefinition("index", "UInt"), WinRtParameterDefinition("value", "T0"))),
                                WinRtMethodDefinition("InsertAt", "Unit", parameters = listOf(WinRtParameterDefinition("index", "UInt"), WinRtParameterDefinition("value", "T0"))),
                                WinRtMethodDefinition("RemoveAt", "Unit", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition("Append", "Unit", parameters = listOf(WinRtParameterDefinition("value", "T0"))),
                                WinRtMethodDefinition("Clear", "Unit"),
                            ),
                            properties = listOf(WinRtPropertyDefinition("Size", "UInt", getterMethodName = "get_Size")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("44444444-4444-4444-4444-444444444444"),
                            genericParameterCount = 2,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IKeyValuePair<T0, T1>>"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition("Lookup", "T1", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("HasKey", "Boolean", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("Insert", "Boolean", parameters = listOf(WinRtParameterDefinition("key", "T0"), WinRtParameterDefinition("value", "T1"))),
                                WinRtMethodDefinition("Remove", "Unit", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("Clear", "Unit"),
                            ),
                            properties = listOf(WinRtPropertyDefinition("Size", "UInt", getterMethodName = "get_Size")),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IStringVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("55555555-5555-5555-5555-555555555555"),
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IVector<String>")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "StringVector",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IStringVector",
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IStringVector", isDefault = true)),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IStringIntMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("66666666-6666-6666-6666-666666666666"),
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IMap<String, Int>")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "StringIntMap",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IStringIntMap",
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IStringIntMap", isDefault = true)),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val vectorContents = filesByName.getValue("StringVector.kt").contents
        val mapContents = filesByName.getValue("StringIntMap.kt").contents

        assertTrue(vectorContents, vectorContents.contains("MutableList<String>,"))
        assertTrue(vectorContents, vectorContents.contains("__iStringVectorVectorCollection"))
        assertFalse(vectorContents, vectorContents.contains("__iStringVectorIterableCollection"))
        assertFalse(vectorContents, vectorContents.contains("Iterable<String>,"))
        assertFalse(vectorContents, vectorContents.contains("fun First("))
        assertFalse(vectorContents, vectorContents.contains("fun GetAt("))
        assertFalse(vectorContents, vectorContents.contains("val Size"))

        assertTrue(mapContents, mapContents.contains("MutableMap<String, Int>,"))
        assertTrue(mapContents, mapContents.contains("__iStringIntMapMapCollection"))
        assertFalse(mapContents, mapContents.contains("__iStringIntMapIterableCollection"))
        assertFalse(mapContents, mapContents.contains("Iterable<Map.Entry<String, Int>>,"))
        assertFalse(mapContents, mapContents.contains("fun First("))
        assertFalse(mapContents, mapContents.contains("fun Lookup("))
        assertFalse(mapContents, mapContents.contains("fun HasKey("))
        assertFalse(mapContents, mapContents.contains("val Size"))
    }

    @Test
    fun generator_emits_cswinrt_writer_support_handoffs_when_enabled() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "EventHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555551"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("sender", "System.Object"),
                                        WinRtParameterDefinition("args", "T0"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IReference",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555552"),
                            genericParameterCount = 1,
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Value",
                                    typeName = "T0",
                                    getterMethodName = "get_Value",
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555553"),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Windows.Foundation.EventHandler<Int>",
                                    addMethodName = "add_Changed",
                                    removeMethodName = "remove_Changed",
                                ),
                                WinRtEventDefinition(
                                    name = "Ticked",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodName = "add_Ticked",
                                    removeMethodName = "remove_Ticked",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("value", "Int"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                            ),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidget",
                                    isDefault = true,
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "boxed",
                                    returnTypeName = "Windows.Foundation.IReference<Int>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555554"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "CreateInstance",
                                    returnTypeName = "Sample.Foundation.Widget",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }

        assertTrue(filesByName.getValue("WinRTGenericAbiRegistry.kt").contents.contains("_get_Value_Int"))
        assertTrue(filesByName.getValue("WinRTGenericAbiRegistry.kt").contents.contains("Windows.Foundation.IReference<Int>"))
        assertTrue(filesByName.getValue("WinRTGenericAbiRegistry.kt").contents.contains("fun registerAbiDelegates"))
        val genericTypeInstantiations = filesByName.getValue("WinRTGenericTypeInstantiations.kt").contents
        assertTrue(genericTypeInstantiations.contains("Windows_Foundation_IReference_Int"))
        assertTrue(genericTypeInstantiations.contains("fun initializeAll"))
        assertTrue(genericTypeInstantiations.contains("fun initializeBySourceType(sourceType: String)"))
        assertTrue(genericTypeInstantiations.contains("internal data class GenericTypeInstantiationRuntimeBinding"))
        assertTrue(genericTypeInstantiations.contains("fun installRuntimeBinding(binding: GenericTypeInstantiationRuntimeBinding)"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationRuntime.bindRcwHelpers"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationRuntime.bindVtableFunctions"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationRuntime.bindPropertyAccessors"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationRuntime.bindDelegateCcwInvoke"))
        assertTrue(genericTypeInstantiations.contains("runtimeBinding.initRcwHelpers(entry, entry.rcwFunctions)"))
        assertTrue(genericTypeInstantiations.contains("runtimeBinding.initVtableFunctions(entry, entry.vtableFunctions)"))
        assertTrue(genericTypeInstantiations.contains("runtimeBinding.initPropertyAccessors(entry, entry.propertyAccessors)"))
        assertTrue(genericTypeInstantiations.contains("runtimeBinding.initDelegateCcwInvoke(entry)"))
        assertTrue(genericTypeInstantiations.contains("private fun registerGenericInstantiation(entry: GenericTypeInstantiationEntry)"))
        val eventProjectionHelpers = filesByName.getValue("WinRTEventProjectionHelpers.kt").contents
        assertTrue(eventProjectionHelpers.contains("sourceClass = \"EventHandlerEventSource\""))
        assertTrue(eventProjectionHelpers.contains("usesSharedEventHandlerSource = true"))
        assertTrue(eventProjectionHelpers.contains("\"EventHandlerEventSource\" -> eventHandlerEventSourceFactoryFor(entry)"))
        assertTrue(eventProjectionHelpers.contains("fun eventHandlerEventSourceFactoryFor(entry: EventSourceEntry): io.github.kitectlab.winrt.runtime.WinRtEventSourceFactory?"))
        assertTrue(eventProjectionHelpers.contains("Windows.Foundation.EventHandler<Int>\" -> { obj, index ->"))
        assertTrue(eventProjectionHelpers.contains("EventHandlerEventSource<kotlin.Int>"))
        assertTrue(eventProjectionHelpers.contains("internal class _EventSource_Sample_Foundation_WidgetHandler"))
        assertTrue(eventProjectionHelpers.contains("EventSource<io.github.kitectlab.winrt.projections.sample.foundation.WidgetHandler>"))
        assertTrue(eventProjectionHelpers.contains("handler.invoke(__args[0] as kotlin.Int)"))
        assertTrue(eventProjectionHelpers.contains("\"_EventSource_Sample_Foundation_WidgetHandler\" -> { obj, index -> _EventSource_Sample_Foundation_WidgetHandler(obj, index) }"))
        assertTrue(eventProjectionHelpers.contains("fun installEventSources()"))
        assertTrue(eventProjectionHelpers.contains("WinRTGenericTypeInstantiations.initializeBySourceType(entry.eventType)"))
        assertTrue(eventProjectionHelpers.contains("WinRtEventSourceRuntime.registerEventSource"))
        assertTrue(eventProjectionHelpers.contains("eventSourceFactory = eventSourceFactoryFor(entry)"))
        assertTrue(eventProjectionHelpers.contains("fun createEventSource("))
        assertTrue(eventProjectionHelpers.contains("WinRtEventSourceRuntime.createEventSource("))
        assertTrue(eventProjectionHelpers.contains("fun installEventSources(install: (EventSourceEntry) -> Unit)"))
        assertTrue(filesByName.getValue("WinRTAbiImplementationPlan.kt").contents.contains("Sample.Foundation.IWidget"))
        assertTrue(filesByName.getValue("WinRTAbiImplementationPlan.kt").contents.contains("fun installAbiImplementations"))
        val typeShapeWriterPlan = filesByName.getValue("WinRTTypeShapeWriterPlan.kt").contents
        assertTrue(typeShapeWriterPlan.contains("HELPER_OUTPUTS"))
        assertTrue(typeShapeWriterPlan.contains("WinRTNamespaceAdditions.kt"))
        assertTrue(typeShapeWriterPlan.contains("fun registerBaseTypeMappings"))
        assertTrue(typeShapeWriterPlan.contains("deferredAuthoringFactoryMembers = listOf(\"Sample.Foundation.IWidgetFactory\")"))
        assertTrue(typeShapeWriterPlan.contains("deferredModuleActivationEntries = listOf(\"Sample.Foundation.Widget\")"))
        assertTrue(typeShapeWriterPlan.contains("fun deferredAuthoringFactoryEntries(): List<Pair<String, String>>"))
        assertFalse(typeShapeWriterPlan.contains("installModuleActivationFactories"))
        assertFalse(typeShapeWriterPlan.contains("moduleActivationEntries"))
        assertTrue(filesByName.getValue("WinRTNamespaceAdditions.kt").contents.contains("Windows.Foundation"))
        assertTrue(filesByName.getValue("WinRTNamespaceAdditions.kt").contents.contains("fun installNamespaceAdditions"))
        assertFalse(filesByName.getValue("WinRTNamespaceAdditions.kt").contents.contains("sourceFiles"))
    }

    @Test
    fun generator_projects_mutable_vector_runtime_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555557"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IVector<String>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NameVector",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameVector",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.INameVector",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("NameVector.kt")
            .contents

        assertTrue(contents, contents.contains("MutableList<String>,"))
        assertTrue(contents, contents.contains("override fun set(index: Int, element: String): String"))
        assertTrue(contents, contents.contains("override fun add(index: Int, element: String)"))
        assertTrue(contents, contents.contains("AbstractMutableList<String>()"))
        assertFalse(contents, contents.contains("Iterable<String> by __iNameVectorIterableCollection"))
        assertTrue(contents, contents.contains("IVector.Metadata.GETAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.SETAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.INSERTAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.REMOVEAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.APPEND_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.CLEAR_SLOT"))
        assertTrue(contents, contents.contains("REQUIRED_MAPPED_HELPER_PLANS"))
        assertTrue(contents, contents.contains("Windows.Foundation.Collections.IVector<String>|IList|idic"))
        assertTrue(contents, contents.contains("removeGeneric=System.Collections.Generic.IEnumerable<String>"))
    }

    @Test
    fun generator_projects_mapped_vector_method_and_property_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IVectorProvider",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555558"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "items",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<String>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "MutableItems",
                                    typeName = "Windows.Foundation.Collections.IVector<String>",
                                    getterMethodName = "get_MutableItems",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "VectorProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IVectorProvider",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "items",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<String>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "MutableItems",
                                    typeName = "Windows.Foundation.Collections.IVector<String>",
                                    getterMethodName = "get_MutableItems",
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IVectorProvider",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("VectorProvider.kt")
            .contents

        assertTrue(contents, contents.contains("fun items(): MutableList<String>"))
        assertTrue(contents, contents.contains("val mutableItems: MutableList<String>"))
        assertTrue(contents, contents.contains("return object : AbstractMutableList<String>(), MutableList<String>, IWinRTObject"))
        assertTrue(contents, contents.contains("PlatformAbi.readPointer(__resultOut)"))
        assertTrue(contents, contents.contains("IVector.Metadata.GETAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.SETAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.INSERTAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.REMOVEAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.APPEND_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.CLEAR_SLOT"))
    }

    @Test
    fun generator_projects_mutable_map_runtime_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555559"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IMap<String, Int>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NameMap",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameMap",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.INameMap",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("NameMap.kt")
            .contents

        assertTrue(contents, contents.contains("MutableMap<String, Int>,"))
        assertTrue(contents, contents.contains("override fun put(key: String, value: Int): Int?"))
        assertTrue(contents, contents.contains("override fun remove(key: String): Int?"))
        assertTrue(contents, contents.contains("AbstractMutableMap<String, Int>()"))
        assertFalse(contents, contents.contains("Iterable<Map.Entry<String, Int>>"))
        assertTrue(contents, contents.contains("IMap.Metadata.HASKEY_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.LOOKUP_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.INSERT_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.REMOVE_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.CLEAR_SLOT"))
        assertTrue(contents, contents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(contents, contents.contains("IKeyValuePair.Metadata.KEY_GETTER_SLOT"))
    }

    @Test
    fun generator_projects_mapped_map_method_and_property_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IMapProvider",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555560"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "items",
                                    returnTypeName = "Windows.Foundation.Collections.IMap<String, Int>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "MutableItems",
                                    typeName = "Windows.Foundation.Collections.IMap<String, Int>",
                                    getterMethodName = "get_MutableItems",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "MapProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IMapProvider",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "items",
                                    returnTypeName = "Windows.Foundation.Collections.IMap<String, Int>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "MutableItems",
                                    typeName = "Windows.Foundation.Collections.IMap<String, Int>",
                                    getterMethodName = "get_MutableItems",
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IMapProvider",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("MapProvider.kt")
            .contents

        assertTrue(contents, contents.contains("fun items(): MutableMap<String, Int>"))
        assertTrue(contents, contents.contains("val mutableItems: MutableMap<String, Int>"))
        assertTrue(contents, contents.contains("return object : AbstractMutableMap<String, Int>(), MutableMap<String, Int>, IWinRTObject"))
        assertTrue(contents, contents.contains("AbstractMutableSet<MutableMap.MutableEntry<String, Int>>()"))
        assertTrue(contents, contents.contains("IMap.Metadata.HASKEY_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.LOOKUP_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.INSERT_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.REMOVE_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.CLEAR_SLOT"))
        assertTrue(contents, contents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(contents, contents.contains("IKeyValuePair.Metadata.VALUE_GETTER_SLOT"))
    }

    @Test
    fun generator_projects_mapped_collection_method_and_property_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICollectionProvider",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555554"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "names",
                                    returnTypeName = "Windows.Foundation.Collections.IIterable<String>",
                                ),
                                WinRtMethodDefinition(
                                    name = "readOnlyNames",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<String>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "NameMap",
                                    typeName = "Windows.Foundation.Collections.IMapView<String, Int>",
                                    getterMethodName = "get_NameMap",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "CollectionProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.ICollectionProvider",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "names",
                                    returnTypeName = "Windows.Foundation.Collections.IIterable<String>",
                                ),
                                WinRtMethodDefinition(
                                    name = "readOnlyNames",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<String>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "NameMap",
                                    typeName = "Windows.Foundation.Collections.IMapView<String, Int>",
                                    getterMethodName = "get_NameMap",
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.ICollectionProvider",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("CollectionProvider.kt")
            .contents

        assertTrue(contents, contents.contains("fun names(): Iterable<String>"))
        assertTrue(contents, contents.contains("fun readOnlyNames(): List<String>"))
        assertTrue(contents, contents.contains("val nameMap: Map<String, Int>"))
        assertTrue(contents, contents.contains("PlatformAbi.readPointer(__resultOut)"))
        assertTrue(contents, contents.contains("return object : Iterable<String>, IWinRTObject"))
        assertTrue(contents, contents.contains("return object : AbstractList<String>(), List<String>, IWinRTObject"))
        assertTrue(contents, contents.contains("return object : AbstractMap<String, Int>(), Map<String, Int>, IWinRTObject"))
        assertTrue(contents, contents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(contents, contents.contains("IVectorView.Metadata.GETAT_SLOT"))
        assertTrue(contents, contents.contains("IMapView.Metadata.LOOKUP_SLOT"))
        assertTrue(contents, contents.contains("IKeyValuePair.Metadata.KEY_GETTER_SLOT"))
    }

    @Test
    fun generator_projects_interface_mapped_collection_return_element_binding() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetProvider",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555556"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "widgets",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Sample.Foundation.IWidget>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetProvider",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "widgets",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Sample.Foundation.IWidget>",
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidgetProvider",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("WidgetProvider.kt")
            .contents

        assertTrue(contents, contents.contains("fun widgets(): List<IWidget>"))
        assertTrue(contents, contents.contains("return object : AbstractList<IWidget>(), List<IWidget>, IWinRTObject"))
        assertTrue(contents, contents.contains("IWidget.Metadata.wrap"))
    }

    @Test
    fun generator_marshals_projected_interface_collection_parameters() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetSink",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555556"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setWidgets",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            "widgets",
                                            "Windows.Foundation.Collections.IIterable<Sample.Foundation.IWidget>",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetSink",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetSink",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setWidgets",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            "widgets",
                                            "Windows.Foundation.Collections.IIterable<Sample.Foundation.IWidget>",
                                        ),
                                    ),
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidgetSink",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("WidgetSink.kt")
            .contents

        assertTrue(contents, contents.contains("fun setWidgets(widgets: Iterable<IWidget>)"))
        assertTrue(contents, contents.contains("WinRtIterableProjection.createMarshaler(widgets"))
        assertTrue(contents, contents.contains("WinRtReferenceValueAdapter<IWidget>"))
        assertTrue(contents, contents.contains("ComVtableInvoker.invokeArgs"))
    }

    @Test
    fun generator_marshals_winui_bindable_collection_parameters_and_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IBindableHost",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("87654321-2222-3333-4444-555555555551"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "snapshot",
                                    returnTypeName = "Windows.UI.Xaml.Interop.IBindableVectorView",
                                ),
                                WinRtMethodDefinition(
                                    name = "setItems",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("items", "Microsoft.UI.Xaml.Interop.IBindableVector"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "BindableHost",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.UI.IBindableHost",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "snapshot",
                                    returnTypeName = "Windows.UI.Xaml.Interop.IBindableVectorView",
                                ),
                                WinRtMethodDefinition(
                                    name = "setItems",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("items", "Microsoft.UI.Xaml.Interop.IBindableVector"),
                                    ),
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.UI.IBindableHost",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("BindableHost.kt")
            .contents

        assertTrue(contents, contents.contains("fun snapshot(): List<Any?>"))
        assertTrue(contents, contents.contains("WinRtBindableVectorViewProjection.fromAbi"))
        assertTrue(contents, contents.contains("fun setItems(items: MutableList<Any?>)"))
        assertTrue(contents, contents.contains("WinRtBindableVectorProjection.createMarshaler(items)!!.use"))
    }

    @Test
    fun generator_projects_required_notify_data_error_info_helper_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Data",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Data",
                            name = "INotifyDataErrorInfo",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("0ee6c2cc-273e-567d-bc0a-1dd87ee51eba"),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IValidatedObject",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555563"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Data.INotifyDataErrorInfo"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ValidatedObject",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IValidatedObject",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IValidatedObject", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("ValidatedObject.kt")
            .contents

        assertTrue(contents, contents.contains("WinRtDataErrorInfo,"))
        assertTrue(contents, contents.contains("WinRtDataErrorInfoProjection.fromAbi(_inner)"))
        assertTrue(contents, contents.contains("override val hasErrors: Boolean"))
        assertTrue(contents, contents.contains("override fun getErrors(propertyName: String?): Iterable<Any?>?"))
        assertTrue(contents, contents.contains("override fun addErrorsChanged(handler: WinRtDataErrorsChangedHandler)"))
        assertTrue(contents, contents.contains("override fun removeErrorsChanged(handler: WinRtDataErrorsChangedHandler)"))
        assertFalse(contents, contents.contains("INotifyDataErrorInfo.Metadata.IID"))
    }

    @Test
    fun generator_projects_required_closable_helper_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IClosable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("30d5a829-7fa4-4026-83bb-d75bae4ea99e"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "Close", returnTypeName = "Unit", methodRowId = 6),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IClosableOwner",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555564"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Windows.Foundation.IClosable"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ClosableOwner",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IClosableOwner",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IClosableOwner", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("ClosableOwner.kt")
            .contents

        assertTrue(contents, contents.contains("AutoCloseable,"))
        assertTrue(contents, contents.contains("override fun close()"))
        assertTrue(contents, contents.contains("WinRtClosableObject(_inner).close()"))
        assertFalse(contents, contents.contains("IClosable.Metadata.IID"))
    }

    @Test
    fun generator_projects_required_bindable_vector_helper_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Interop",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Interop",
                            name = "IBindableIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555571"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Interop",
                            name = "IBindableVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555572"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Interop.IBindableIterable"),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBindableOwner",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555573"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Interop.IBindableVector"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "BindableOwner",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IBindableOwner",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IBindableOwner", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("BindableOwner.kt")
            .contents

        assertTrue(contents, contents.contains("MutableList<Any?>,"))
        assertTrue(contents, contents.contains("override fun `set`(index: Int, element: Any?): Any?"))
        assertTrue(contents, contents.contains("override fun add(index: Int, element: Any?)"))
        assertTrue(contents, contents.contains("WinRtBindableVectorProjection"))
        assertFalse(contents, contents.contains("Iterable<Any?>,"))
    }

    @Test
    fun generator_projects_required_iterator_helper_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterator",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555581"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(name = "MoveNext", returnTypeName = "Boolean", methodRowId = 8),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Current",
                                    typeName = "T0",
                                    getterMethodName = "get_Current",
                                    getterMethodRowId = 6,
                                ),
                                WinRtPropertyDefinition(
                                    name = "HasCurrent",
                                    typeName = "Boolean",
                                    getterMethodName = "get_HasCurrent",
                                    getterMethodRowId = 7,
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IStringIteratorOwner",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555582"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IIterator<String>"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "StringIteratorOwner",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IStringIteratorOwner",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IStringIteratorOwner", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("StringIteratorOwner.kt")
            .contents

        assertTrue(contents, contents.contains("Iterator<String>,"))
        assertTrue(contents, contents.contains("override fun hasNext(): Boolean"))
        assertTrue(contents, contents.contains("override fun next(): String"))
        assertTrue(contents, contents.contains("IIterator.Metadata.CURRENT_GETTER_SLOT"))
        assertTrue(contents, contents.contains("IIterator.Metadata.HASCURRENT_GETTER_SLOT"))
        assertTrue(contents, contents.contains("IIterator.Metadata.MOVENEXT_SLOT"))
        assertTrue(contents, contents.contains("Metadata.acquireInterface(_inner, IIterator.Metadata.IID)"))
        assertFalse(contents, contents.contains("override val current: String"))
        assertFalse(contents, contents.contains("override val hasCurrent: Boolean"))
        assertFalse(contents, contents.contains("override fun MoveNext"))
    }

    @Test
    fun generator_substitutes_required_interface_generic_closure_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBox",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555561"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(name = "current", returnTypeName = "T0", methodRowId = 6),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "BoxValue",
                                    typeName = "T0",
                                    getterMethodName = "get_BoxValue",
                                    getterMethodRowId = 7,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IStringBox",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555562"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IBox<String>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "StringBox",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IStringBox",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IStringBox",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("StringBox.kt")
            .contents

        assertTrue(contents, contents.contains("override fun current(): String"))
        assertTrue(contents, contents.contains("override val boxValue: String"))
        assertTrue(contents, contents.contains("Metadata.acquireInterface(_inner, IBox.Metadata.IID)"))
        assertTrue(contents, contents.contains("_iBox"))
        assertFalse(contents, contents.contains("T0"))
    }

    @Test
    fun planner_rejects_interface_surface_without_iid() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            methods = listOf(WinRtMethodDefinition(name = "ping", returnTypeName = "Unit")),
                        ),
                    ),
                ),
            ),
        )

        val error = runCatching { KotlinProjectionPlanner().plan(model) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("requires interface Sample.Foundation.IWidget to carry metadata IID"))
    }

    @Test
    fun generator_rejects_static_runtime_surface_without_static_interface_metadata() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "create",
                                    returnTypeName = "Widget",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                    isStatic = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val error = runCatching { KotlinProjectionGenerator().generate(model) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("requires runtime class Sample.Foundation.Widget to carry static interface metadata"))
    }

    private fun windowsDataJsonProjectionModel(): WinRtMetadataModel =
        WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Data.Json",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonValue",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("A3219FD4-B4C3-42C6-9EE6-8DCD1C3FBE9A"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "stringify", returnTypeName = "String"),
                                WinRtMethodDefinition(name = "getString", returnTypeName = "String"),
                                WinRtMethodDefinition(name = "getNumber", returnTypeName = "Double"),
                                WinRtMethodDefinition(name = "getBoolean", returnTypeName = "Boolean"),
                                WinRtMethodDefinition(name = "getArray", returnTypeName = "Windows.Data.Json.JsonArray"),
                                WinRtMethodDefinition(name = "getObject", returnTypeName = "Windows.Data.Json.JsonObject"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "ValueType",
                                    typeName = "Windows.Data.Json.JsonValueType",
                                    getterMethodName = "get_ValueType",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonValueStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("5F6B544A-2F53-48E1-91A3-F78B50A6345C"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                                WinRtMethodDefinition(name = "createBooleanValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "Boolean"))),
                                WinRtMethodDefinition(name = "createNumberValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "Double"))),
                                WinRtMethodDefinition(name = "createStringValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "String"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonValue",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                            defaultInterfaceName = "Windows.Data.Json.IJsonValue",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Windows.Data.Json.IJsonValue", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Windows.Data.Json.IJsonValueStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "stringify", returnTypeName = "String"),
                                WinRtMethodDefinition(name = "getString", returnTypeName = "String"),
                                WinRtMethodDefinition(name = "getNumber", returnTypeName = "Double"),
                                WinRtMethodDefinition(name = "getBoolean", returnTypeName = "Boolean"),
                                WinRtMethodDefinition(name = "getArray", returnTypeName = "Windows.Data.Json.JsonArray"),
                                WinRtMethodDefinition(name = "getObject", returnTypeName = "Windows.Data.Json.JsonObject"),
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                                WinRtMethodDefinition(name = "createBooleanValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "Boolean")), isStatic = true),
                                WinRtMethodDefinition(name = "createNumberValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "Double")), isStatic = true),
                                WinRtMethodDefinition(name = "createStringValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "String")), isStatic = true),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "ValueType", typeName = "Windows.Data.Json.JsonValueType", getterMethodName = "get_ValueType"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonObject",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("064E24DD-29C2-4F83-9AC1-9EE11578BEB3"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IMap<String, Windows.Data.Json.IJsonValue>"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "setNamedValue", returnTypeName = "Unit", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("value", "Windows.Data.Json.JsonValue"))),
                                WinRtMethodDefinition(name = "getNamedObject", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedArray", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedString", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedString", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("defaultValue", "String"))),
                                WinRtMethodDefinition(name = "getNamedNumber", returnTypeName = "Double", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedBoolean", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedBoolean", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("defaultValue", "Boolean"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonObjectStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("2289F159-54DE-45D8-ABCC-22603FA066A0"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonObject",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                            defaultInterfaceName = "Windows.Data.Json.IJsonObject",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Windows.Data.Json.IJsonObject", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Windows.Data.Json.IJsonObjectStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "setNamedValue", returnTypeName = "Unit", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("value", "Windows.Data.Json.JsonValue"))),
                                WinRtMethodDefinition(name = "getNamedObject", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedArray", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedString", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedString", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("defaultValue", "String"))),
                                WinRtMethodDefinition(name = "getNamedNumber", returnTypeName = "Double", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedBoolean", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedBoolean", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("defaultValue", "Boolean"))),
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonArray",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("08C1D4F8-B5C6-46F7-9D5A-0CC4C8FA1BBA"),
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IVector<Windows.Data.Json.IJsonValue>"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "getObjectAt", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getArrayAt", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getStringAt", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getNumberAt", returnTypeName = "Double", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getBooleanAt", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonArrayStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("DB1434A9-E164-499F-93E2-8A8F49BB90BA"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonArray",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                            defaultInterfaceName = "Windows.Data.Json.IJsonArray",
                            implementedInterfaces = listOf(
                                io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition("Windows.Data.Json.IJsonArray", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                staticInterfaceNames = listOf("Windows.Data.Json.IJsonArrayStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "getObjectAt", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getArrayAt", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getStringAt", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getNumberAt", returnTypeName = "Double", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getBooleanAt", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "create", returnTypeName = "Windows.Data.Json.JsonArray", isStatic = true),
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonErrorStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("FE616766-BF27-4064-87B7-6563BB11CE2E"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "getJsonStatus", returnTypeName = "Windows.Data.Json.JsonErrorStatus", parameters = listOf(WinRtParameterDefinition("hResult", "Int"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonError",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Windows.Data.Json.IJsonErrorStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "getJsonStatus", returnTypeName = "Windows.Data.Json.JsonErrorStatus", parameters = listOf(WinRtParameterDefinition("hResult", "Int")), isStatic = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonErrorStatus",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("Unknown", 0u),
                                WinRtEnumMemberDefinition("InvalidJsonString", 1u),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonValueType",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("Null", 0u),
                                WinRtEnumMemberDefinition("Boolean", 1u),
                                WinRtEnumMemberDefinition("Number", 2u),
                                WinRtEnumMemberDefinition("String", 3u),
                                WinRtEnumMemberDefinition("Array", 4u),
                                WinRtEnumMemberDefinition("Object", 5u),
                            ),
                        ),
                    ),
                ),
            ),
        )

    private fun generateWindowsDataJsonFromInstalledWinmd(): Map<String, KotlinProjectionFile> {
        val windowsWinmd = runCatching { KotlinProjectionGeneratorOptions.locateWindowsWinmd() }.getOrNull()
        assumeTrue("Windows SDK Windows.winmd is required for CsWinRT parity fixture.", windowsWinmd?.isRegularFile() == true)
        val model = WinRtMetadataLoader
            .load(windowsWinmd!!)
            .filterToWindowsDataJson()
        return KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
    }

    private fun WinRtMetadataModel.filterToWindowsDataJson(): WinRtMetadataModel =
        WinRtMetadataModel(namespaces.filter { it.name == "Windows.Data.Json" }).normalized()

}
