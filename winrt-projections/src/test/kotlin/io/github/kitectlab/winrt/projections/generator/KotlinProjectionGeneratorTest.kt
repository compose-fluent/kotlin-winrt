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
        assertTrue(file.contents.contains("public class JsonObject"))
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
        assertEquals(listOf("Sample.Foundation.IWidgetStatics"), classPlan.staticInterfaceNames)
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
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name"),
                            ),
                            events = listOf(
                                WinRtEventDefinition(name = "Changed", delegateTypeName = "Sample.Foundation.WidgetHandler"),
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
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name"),
                                WinRtPropertyDefinition(name = "Count", typeName = "Int", isStatic = true, getterMethodName = "get_Count"),
                            ),
                            events = listOf(
                                WinRtEventDefinition(name = "Changed", delegateTypeName = "Sample.Foundation.WidgetHandler"),
                                WinRtEventDefinition(name = "Loaded", delegateTypeName = "Sample.Foundation.WidgetHandler", isStatic = true),
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
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("public val name: String"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("public fun addChanged(handler: WidgetHandler): Int"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("public fun removeChanged(token: Int)"))

        val widgetContents = filesByName.getValue("Widget.kt").contents
        assertTrue(widgetContents.contains("public sealed class Widget : IWidget"))
        assertTrue(widgetContents.contains("public val name: String"))
        assertTrue(widgetContents.contains("companion object Metadata"))
        assertTrue(widgetContents.contains("public fun create(): Widget = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("public val count: Int"))
        assertTrue(widgetContents.contains("public fun addChanged(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("public fun removeChanged(token: Int)"))
        assertTrue(widgetContents.contains("public fun addLoaded(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("public object ActivationFactory"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
        assertTrue(widgetContents.contains("public object StaticInterfaces"))
        assertTrue(widgetContents.contains("public object ComposableFactory"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
        assertEquals(1, "companion object Metadata".toRegex().findAll(widgetContents).count())

        assertTrue(filesByName.getValue("WidgetStatics.kt").contents.contains("public class WidgetStatics"))
        assertTrue(filesByName.getValue("WidgetStatics.kt").contents.contains("static WinRT class shell"))
        assertTrue(filesByName.getValue("WidgetContract.kt").contents.contains("public enum class WidgetContract"))
        assertTrue(filesByName.getValue("WidgetContract.kt").contents.contains("api contract WinRT declaration shell"))
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
        assertTrue(classContents.contains("class WidgetView : IWidgetBase"))
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

        assertTrue(widgetContents.contains("class Widget : IWidget, IWidgetExtra"))
        assertTrue(widgetContents.contains("public var title: String"))
        assertTrue(widgetContents.contains("public val maxCount: Int"))
        assertTrue(widgetContents.contains("public fun addUpdated(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("public fun addReset(handler: WidgetHandler): Int = error(\"Not yet bound to winrt-runtime\")"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
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
}
