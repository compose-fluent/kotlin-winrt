package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtActivationShape
import io.github.kitectlab.winrt.metadata.WinRtEnumMemberDefinition
import io.github.kitectlab.winrt.metadata.WinRtIntegralType
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
import kotlin.io.path.isRegularFile

class KotlinProjectionGeneratorTest {
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
        assertTrue(jsonObject, jsonObject.contains("private val _inner: InspectableReference"))
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
        assertTrue(jsonObject, jsonObject.contains("private val _defaultInterface: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION)"))

        assertTrue(jsonArray, jsonArray.contains("public class JsonArray internal constructor("))
        assertTrue(jsonArray, jsonArray.contains("fun getStringAt(index: UInt): String"))
        assertTrue(jsonArray, jsonArray.contains("fun create(): JsonArray = error(\"Not yet bound to winrt-runtime\")"))

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
        assertTrue(file.contents.contains("private val _inner: InspectableReference"))
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
                                    parameters = listOf(WinRtParameterDefinition("sender", "IWidget")),
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
        assertTrue(filesByName.getValue("Point.kt").contents.contains("data class Point"))
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
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("fun addChanged(handler: WidgetHandler): Int"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("fun removeChanged(token: Int)"))

        val widgetContents = filesByName.getValue("Widget.kt").contents
        assertTrue(widgetContents.contains("public class Widget internal constructor("))
        assertTrue(widgetContents.contains("private val _inner: InspectableReference"))
        assertTrue(widgetContents.contains("private val _defaultInterface: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("ActivationFactory.activateInstance(Metadata.TYPE_NAME)"))
        assertTrue(widgetContents.contains("val name: String"))
        assertTrue(widgetContents.contains("companion object Metadata"))
        assertTrue(widgetContents.contains("internal fun acquireInterface(instance: InspectableReference, iid: Guid): IUnknownReference"))
        assertTrue(widgetContents.contains("internal fun wrap(instance: InspectableReference): Widget = Widget(instance)"))
        assertTrue(widgetContents.contains("internal const val CREATE_METHOD_ROW_ID: Int = 20"))
        assertTrue(widgetContents.contains("internal const val NAME_GETTER_METHOD_ROW_ID: Int = 21"))
        assertTrue(widgetContents.contains("internal const val COUNT_GETTER_METHOD_ROW_ID: Int = 22"))
        assertTrue(widgetContents.contains("internal const val CHANGED_ADD_METHOD_ROW_ID: Int = 23"))
        assertTrue(widgetContents.contains("internal const val CHANGED_REMOVE_METHOD_ROW_ID: Int = 24"))
        assertTrue(widgetContents.contains("internal const val LOADED_ADD_METHOD_ROW_ID: Int = 25"))
        assertTrue(widgetContents.contains("internal const val LOADED_REMOVE_METHOD_ROW_ID: Int = 26"))
        assertTrue(widgetContents.contains("fun acquireDefaultInterface(instance: InspectableReference): IUnknownReference"))
        assertTrue(widgetContents.contains("acquireInterface(instance, DEFAULT_INTERFACE_IID)"))
        assertTrue(widgetContents.contains("fun create(): Widget = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("val count: Int"))
        assertTrue(widgetContents.contains("fun addChanged(handler: WidgetHandler): Int"))
        assertTrue(widgetContents.contains("fun addChanged(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("fun removeChanged(token: Int)"))
        assertTrue(widgetContents.contains("fun addLoaded(handler: WidgetHandler): Int"))
        assertTrue(widgetContents.contains("fun addLoaded(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("public object ActivationFactory"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
        assertTrue(widgetContents.contains("val FACTORY_INTERFACE_IID: Guid = Guid(\"44444444-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("fun acquire(): IUnknownReference"))
        assertTrue(widgetContents.contains("io.github.kitectlab.winrt.runtime.ActivationFactory.get(RUNTIME_CLASS,"))
        assertTrue(widgetContents.contains("fun activate(): InspectableReference"))
        assertTrue(widgetContents.contains("public object StaticInterfaces"))
        assertTrue(widgetContents.contains("val IWIDGETSTATICS_IID: Guid = Guid(\"33333333-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("fun iWidgetStatics(): IUnknownReference"))
        assertTrue(widgetContents.contains("io.github.kitectlab.winrt.runtime.ActivationFactory.get(Metadata.TYPE_NAME,"))
        assertTrue(widgetContents.contains("public object ComposableFactory"))
        assertTrue(widgetContents.contains("public const val DEFAULT_INTERFACE: String = \"Sample.Foundation.IWidget\""))
        assertTrue(widgetContents.contains("val DEFAULT_INTERFACE_IID: Guid = Guid(\"22222222-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
        assertTrue(widgetContents.contains("fun acquire(): IUnknownReference"))
        assertEquals(1, "companion object Metadata".toRegex().findAll(widgetContents).count())

        assertTrue(filesByName.getValue("WidgetStatics.kt").contents.contains("public class WidgetStatics"))
        assertTrue(filesByName.getValue("WidgetStatics.kt").contents.contains("static WinRT class shell"))
        assertTrue(filesByName.getValue("WidgetContract.kt").contents.contains("public enum class WidgetContract"))
        assertTrue(filesByName.getValue("WidgetContract.kt").contents.contains("api contract WinRT declaration shell"))
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
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "parse",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                    isStatic = true,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                    isStatic = true,
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
        assertTrue(widgetContents.contains("val count: Int"))
        assertTrue(widgetContents.contains("internal val STATIC_COUNT_GETTER_SLOT: Int = IWidgetStatics.Metadata.COUNT_GETTER_SLOT"))
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
    fun generator_rejects_struct_and_non_unit_delegate_member_marshaling_until_cswinrt_parity_exists() {
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

        val error = runCatching { KotlinProjectionGenerator().generate(model) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("Struct(Sample.Foundation.Point)") || error.message.orEmpty().contains("Delegate(Sample.Foundation.WidgetHandler)"))
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
        assertTrue(widgetContents.contains("fun addUpdated(handler: WidgetHandler): Int"))
        assertTrue(widgetContents.contains("WinRtDelegateBridge.createUnitDelegate"))
        assertTrue(widgetContents.contains("parameterKinds = listOf(WinRtDelegateValueKind.HSTRING)"))
        assertTrue(widgetContents.contains("handler(__args[0] as String)"))
        assertTrue(widgetContents.contains("__handlerHandle.createReference().use { __handlerAbi ->"))
        assertFalse(widgetContents.contains("fun setHandler(handler: WidgetHandler) = error(\"Not yet bound to winrt-runtime\")"))
        assertFalse(widgetContents.contains("fun addUpdated(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
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

        assertTrue(
            file.contents.contains("public sealed class WidgetAttribute : Annotation") ||
                file.contents.contains("public sealed class WidgetAttribute : kotlin.Annotation"),
        )
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
        assertTrue(widgetContents.contains("private val _inner: InspectableReference"))
        assertTrue(widgetContents.contains("private val _iWidgetExtra: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("internal const val TITLE_GETTER_SLOT_OWNER_INTERFACE: String = \"Sample.Foundation.IWidget\""))
        assertTrue(widgetContents.contains("internal const val TITLE_GETTER_SLOT_OWNER_CACHE: String = \"_defaultInterface\""))
        assertTrue(widgetContents.contains("internal val TITLE_GETTER_SLOT: Int = IWidget.Metadata.TITLE_GETTER_SLOT"))
        assertTrue(widgetContents.contains("var title: String"))
        assertTrue(widgetContents.contains("val maxCount: Int"))
        assertTrue(widgetContents.contains("fun addUpdated(handler: WidgetHandler): Int"))
        assertTrue(widgetContents.contains("fun addUpdated(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("fun addReset(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
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
        assertTrue(widgetContents.contains("return PlatformAbi.confinedScope().use { __scope ->"))
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
        assertTrue(widgetContents.contains("if (ready) 1 else 0"))
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

        assertTrue(file.contents, file.contents.contains("import java.net.URI"))
        assertTrue(file.contents, file.contents.contains("import io.github.kitectlab.winrt.runtime.WinRtAsyncActionReference"))
        assertTrue(file.contents, file.contents.contains("import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationReference"))
        assertTrue(file.contents, file.contents.contains("public interface IWidgetCollection : Iterable<String>"))
        assertTrue(file.contents, file.contents.contains("fun asReadOnly(): List<String>"))
        assertTrue(file.contents, file.contents.contains("fun asMap(): MutableMap<String, Int>"))
        assertTrue(file.contents, file.contents.contains("fun refreshAsync(): WinRtAsyncActionReference"))
        assertTrue(file.contents, file.contents.contains("fun fetchAsync(): WinRtAsyncOperationReference<String>"))
        assertTrue(file.contents, file.contents.contains("val sourceUri: URI"))
        assertTrue(file.contents, file.contents.contains("val selection: Int?"))
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
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("12345678-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refreshAsync", returnTypeName = "Windows.Foundation.IAsyncAction", methodRowId = 6),
                                WinRtMethodDefinition(name = "fetchAsync", returnTypeName = "Windows.Foundation.IAsyncOperation<String>", methodRowId = 7),
                                WinRtMethodDefinition(name = "refreshWithProgressAsync", returnTypeName = "Windows.Foundation.IAsyncActionWithProgress<Int>", methodRowId = 8),
                                WinRtMethodDefinition(name = "fetchWithProgressAsync", returnTypeName = "Windows.Foundation.IAsyncOperationWithProgress<String, UInt>", methodRowId = 9),
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

        assertTrue(iterableContents, iterableContents.contains("Iterable<String> by __iNameIterableIterableCollection"))
        assertTrue(iterableContents, iterableContents.contains("override fun iterator(): Iterator<String>"))
        assertTrue(iterableContents, iterableContents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.CURRENT_GETTER_SLOT"))
        assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.HASCURRENT_GETTER_SLOT"))
        assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.MOVENEXT_SLOT"))

        assertTrue(listContents, listContents.contains("List<String> by __iNameListVectorViewCollection"))
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

        assertTrue(mapContents, mapContents.contains("Map<String, Int> by __iNameMapMapViewCollection"))
        assertTrue(mapContents, mapContents.contains("AbstractMap"))
        assertTrue(mapContents, mapContents.contains("IMapView.Metadata.LOOKUP_SLOT"))
        assertTrue(mapContents, mapContents.contains("IMapView.Metadata.HASKEY_SLOT"))
        assertTrue(mapContents, mapContents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(mapContents, mapContents.contains("IIterator.Metadata.CURRENT_GETTER_SLOT"))
        assertTrue(mapContents, mapContents.contains("IKeyValuePair.Metadata.KEY_GETTER_SLOT"))
        assertTrue(mapContents, mapContents.contains("IKeyValuePair.Metadata.VALUE_GETTER_SLOT"))
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

        assertTrue(contents, contents.contains("MutableList<String> by __iNameVectorVectorCollection"))
        assertTrue(contents, contents.contains("AbstractMutableList<String>()"))
        assertFalse(contents, contents.contains("Iterable<String> by __iNameVectorIterableCollection"))
        assertTrue(contents, contents.contains("IVector.Metadata.GETAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.SETAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.INSERTAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.REMOVEAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.APPEND_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.CLEAR_SLOT"))
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

        assertTrue(contents, contents.contains("MutableMap<String, Int> by __iNameMapMapCollection"))
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
