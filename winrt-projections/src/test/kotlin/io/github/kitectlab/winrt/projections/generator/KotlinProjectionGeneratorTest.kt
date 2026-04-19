package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtActivationShape
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtParameterDefinition
import io.github.kitectlab.winrt.metadata.WinRtPropertyDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.runtime.Guid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinProjectionGeneratorTest {
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
        assertTrue(jsonObject, jsonObject.contains("private val _inner: InspectableReference"))
        assertTrue(jsonObject, jsonObject.contains("public fun getNamedString(name: String): String"))
        assertTrue(jsonObject, jsonObject.contains("public fun parse(json: String): JsonObject = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(jsonObject, jsonObject.contains("public fun tryParse(json: String): JsonObject = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(jsonObject, jsonObject.contains("public object StaticInterfaces"))
        assertTrue(jsonObject, jsonObject.contains("public const val IJSONOBJECTSTATICS: String = \"Windows.Data.Json.IJsonObjectStatics\""))
        assertTrue(jsonObject, jsonObject.contains("private val _defaultInterface: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION)"))

        assertTrue(jsonArray, jsonArray.contains("public class JsonArray internal constructor("))
        assertTrue(jsonArray, jsonArray.contains("public fun getStringAt(index: UInt): String"))
        assertTrue(jsonArray, jsonArray.contains("public fun create(): JsonArray = error(\"Not yet bound to winrt-runtime\")"))

        assertTrue(jsonValue, jsonValue.contains("public class JsonValue internal constructor("))
        assertTrue(jsonValue, jsonValue.contains("public fun stringify(): String"))
        assertTrue(jsonValue, jsonValue.contains("public fun createStringValue(`value`: String): JsonValue"))

        assertTrue(jsonError, jsonError.contains("public class JsonError internal constructor("))
        assertTrue(jsonError, jsonError.contains("public fun getJsonStatus(hResult: Int): JsonErrorStatus"))

        assertTrue(iJsonObject, iJsonObject.contains("public fun getNamedArray(name: String): JsonArray"))
        assertTrue(iJsonObject, iJsonObject.contains("public fun setNamedValue(name: String, `value`: JsonValue)"))

        assertTrue(iJsonValueStatics, iJsonValueStatics.contains("public fun createBooleanValue(`value`: Boolean): JsonValue"))
        assertTrue(iJsonValueStatics, iJsonValueStatics.contains("public fun createNumberValue(`value`: Double): JsonValue"))
        assertTrue(iJsonValueStatics, iJsonValueStatics.contains("public fun createStringValue(`value`: String): JsonValue"))
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
        assertTrue(file.contents.contains("public fun getNamedString(name: String): String = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(file.contents.contains("companion object"))
        assertTrue(file.contents.contains("public fun parse(json: String): JsonObject = error(\"Not yet bound to winrt-runtime\")"))
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
    fun planner_maps_all_metadata_type_kinds_into_declaration_plans() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Status", kind = WinRtTypeKind.Enum),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Point", kind = WinRtTypeKind.Struct),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "WidgetHandler", kind = WinRtTypeKind.Delegate),
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
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "WidgetHandler", kind = WinRtTypeKind.Delegate),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }

        assertTrue(filesByName.getValue("Status.kt").contents.contains("enum class Status"))
        assertTrue(filesByName.getValue("Point.kt").contents.contains("data class Point"))
        assertTrue(filesByName.getValue("WidgetHandler.kt").contents.contains("fun interface WidgetHandler"))
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
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("public val name: String"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("public fun addChanged(handler: WidgetHandler): Int"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("public fun removeChanged(token: Int)"))

        val widgetContents = filesByName.getValue("Widget.kt").contents
        assertTrue(widgetContents.contains("public class Widget internal constructor("))
        assertTrue(widgetContents.contains("private val _inner: InspectableReference"))
        assertTrue(widgetContents.contains("private val _defaultInterface: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("public constructor() : this(ActivationFactory.activate())"))
        assertTrue(widgetContents.contains("public val name: String"))
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
        assertTrue(widgetContents.contains("public fun acquireDefaultInterface(instance: InspectableReference): IUnknownReference"))
        assertTrue(widgetContents.contains("acquireInterface(instance, DEFAULT_INTERFACE_IID)"))
        assertTrue(widgetContents.contains("public fun create(): Widget = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("public val count: Int"))
        assertTrue(widgetContents.contains("public fun addChanged(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("public fun removeChanged(token: Int)"))
        assertTrue(widgetContents.contains("public fun addLoaded(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("public object ActivationFactory"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
        assertTrue(widgetContents.contains("public val FACTORY_INTERFACE_IID: Guid = Guid(\"44444444-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("public fun acquire(): IUnknownReference"))
        assertTrue(widgetContents.contains("io.github.kitectlab.winrt.runtime.ActivationFactory.get(RUNTIME_CLASS,"))
        assertTrue(widgetContents.contains("public fun activate(): InspectableReference"))
        assertTrue(widgetContents.contains("public object StaticInterfaces"))
        assertTrue(widgetContents.contains("public val IWIDGETSTATICS_IID: Guid = Guid(\"33333333-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("public fun iWidgetStatics(): IUnknownReference"))
        assertTrue(widgetContents.contains("io.github.kitectlab.winrt.runtime.ActivationFactory.get(Metadata.TYPE_NAME,"))
        assertTrue(widgetContents.contains("public object ComposableFactory"))
        assertTrue(widgetContents.contains("public const val DEFAULT_INTERFACE: String = \"Sample.Foundation.IWidget\""))
        assertTrue(widgetContents.contains("public val DEFAULT_INTERFACE_IID: Guid = Guid(\"22222222-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
        assertTrue(widgetContents.contains("public fun acquire(): IUnknownReference"))
        assertEquals(1, "companion object Metadata".toRegex().findAll(widgetContents).count())

        assertTrue(filesByName.getValue("WidgetStatics.kt").contents.contains("public class WidgetStatics"))
        assertTrue(filesByName.getValue("WidgetStatics.kt").contents.contains("static WinRT class shell"))
        assertTrue(filesByName.getValue("WidgetContract.kt").contents.contains("public enum class WidgetContract"))
        assertTrue(filesByName.getValue("WidgetContract.kt").contents.contains("api contract WinRT declaration shell"))
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
        assertTrue(widgetContents.contains("public var title: String"))
        assertTrue(widgetContents.contains("public val maxCount: Int"))
        assertTrue(widgetContents.contains("public fun addUpdated(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("public fun addReset(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
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
        assertTrue(widgetContents.contains("public fun refresh()"))
        assertTrue(widgetContents.contains("slot = Metadata.REFRESH_SLOT"))
        assertTrue(widgetContents.contains("public fun version(): Int"))
        assertTrue(widgetContents.contains("slot = Metadata.VERSION_SLOT"))
        assertTrue(widgetContents.contains("public fun isReady(): Boolean"))
        assertTrue(widgetContents.contains("slot = Metadata.ISREADY_SLOT"))
        assertTrue(widgetContents.contains("public fun label(): String"))
        assertTrue(widgetContents.contains("invokeAbi("))
        assertTrue(widgetContents.contains("return Arena.ofConfined().use { __arena ->"))
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
        assertTrue(widgetContents.contains("public fun getNamedString(name: String): String"))
        assertTrue(widgetContents.contains("HString.create(name).use { __nameAbi ->"))
        assertFalse(widgetContents.contains("-> {"))
        assertTrue(widgetContents.contains("Metadata.GETNAMEDSTRING_SLOT"))
        assertTrue(widgetContents.contains("public fun getStringAt(index: UInt): String"))
        assertTrue(widgetContents.contains("index.toInt()"))
        assertTrue(widgetContents.contains("Metadata.GETSTRINGAT_SLOT"))
        assertTrue(widgetContents.contains("public fun rename(name: String)"))
        assertTrue(widgetContents.contains("Metadata.RENAME_SLOT"))
        assertTrue(widgetContents.contains("public fun setSelectedIndex(index: UInt)"))
        assertTrue(widgetContents.contains("Metadata.SETSELECTEDINDEX_SLOT"))
        assertTrue(widgetContents.contains("public var title: String"))
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
        assertTrue(widgetContents.contains("public fun setReady(ready: Boolean)"))
        assertTrue(widgetContents.contains("if (ready) 1.toByte() else 0.toByte()"))
        assertTrue(widgetContents.contains("Metadata.SETREADY_SLOT"))
        assertTrue(widgetContents.contains("public fun createNumberValue("))
        assertTrue(widgetContents.contains("Double): WidgetValue"))
        assertTrue(widgetContents.contains("Metadata.CREATENUMBERVALUE_SLOT"))
        assertTrue(widgetContents.contains("WidgetValue.Metadata.wrap"))
        assertTrue(widgetContents.contains("public var ready: Boolean"))
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
        assertTrue(file.contents, file.contents.contains("import java.util.concurrent.CompletableFuture"))
        assertTrue(file.contents, file.contents.contains("public interface IWidgetCollection : Iterable<String>"))
        assertTrue(file.contents, file.contents.contains("public fun asReadOnly(): List<String>"))
        assertTrue(file.contents, file.contents.contains("public fun asMap(): MutableMap<String, Int>"))
        assertTrue(file.contents, file.contents.contains("public fun refreshAsync(): CompletableFuture<Unit>"))
        assertTrue(file.contents, file.contents.contains("public fun fetchAsync(): CompletableFuture<String>"))
        assertTrue(file.contents, file.contents.contains("public val sourceUri: URI"))
        assertTrue(file.contents, file.contents.contains("public val selection: Int?"))
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
        assertTrue(file.contents, file.contents.contains("public fun snapshot(): MutableList<Any?>"))
        assertTrue(file.contents, file.contents.contains("public val items: MutableList<Any?>"))
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
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "JsonErrorStatus", kind = WinRtTypeKind.Enum),
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "JsonValueType", kind = WinRtTypeKind.Enum),
                    ),
                ),
            ),
        )

}
