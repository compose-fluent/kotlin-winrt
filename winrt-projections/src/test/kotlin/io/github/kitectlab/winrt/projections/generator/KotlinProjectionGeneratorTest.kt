package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtActivationShape
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtParameterDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
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

        assertEquals("io/github/kitectlab/winrt/projections/windows/data/json/JsonObject.kt", file.relativePath)
        assertTrue(file.contents.contains("package io.github.kitectlab.winrt.projections.windows.data.json"))
        assertTrue(file.contents.contains("class JsonObject"))
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
