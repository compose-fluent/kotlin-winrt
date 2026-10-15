package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTActivationShape
import io.github.composefluent.winrt.metadata.WinRTParameterDefinition
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTAttributedFactoryShape
import io.github.composefluent.winrt.metadata.WinRTAttributedFactoryKind
import io.github.composefluent.winrt.metadata.WinRTAvailabilityMetadata
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.WinRTObjectReferenceMarshalingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationMarshalingMetadataTest {
    @Test
    fun activations_use_only_the_classifications_recognized_by_cswinrt() {
        // .cswinrt/src/cswinrt/helpers.h:get_marshaling_type_name
        val expected = mapOf(
            null to WinRTObjectReferenceMarshalingType.Unknown,
            1L to WinRTObjectReferenceMarshalingType.Unknown,
            2L to WinRTObjectReferenceMarshalingType.Agile,
            3L to WinRTObjectReferenceMarshalingType.Standard,
            99L to WinRTObjectReferenceMarshalingType.Unknown,
        )
        for ((metadataValue, type) in expected) {
            val model = WinRTMetadataModel(namespaces = listOf(WinRTNamespace("Sample", listOf(
                WinRTTypeDefinition(
                    namespace = "Sample", name = "IWidget", kind = WinRTTypeKind.Interface,
                    iid = Guid("b790b14f-88a1-4e02-851a-fbc4a912769a"),
                ),
                WinRTTypeDefinition(
                    namespace = "Sample", name = "Widget", kind = WinRTTypeKind.RuntimeClass,
                    isSealedType = true, defaultInterfaceName = "Sample.IWidget",
                    activation = WinRTActivationShape(isActivatable = true),
                    availability = WinRTAvailabilityMetadata(marshalingBehavior = metadataValue),
                ),
            ))))
            val plan = KotlinProjectionPlanner().plan(model).single { it.type.name == "Widget" }
            assertEquals(type, plan.activationMarshalingType)
            val source = KotlinProjectionGenerator().generate(model)
                .single { it.relativePath.endsWith("/Widget.kt") }.contents.replace(Regex("\\s+"), " ")
            val hint = if (type == WinRTObjectReferenceMarshalingType.Unknown) "" else ", WinRTObjectReferenceMarshalingType.${type.name}"
            assertTrue(source, source.contains("_activationFactory.activateDefaultInstance(Metadata.DEFAULT_INTERFACE_IID$hint)"))
        }
    }

    @Test
    fun composable_factories_forward_the_same_runtime_class_marshaling_hint() {
        // CsWinRT 3 code_writers.h:write_composable_constructors passes the
        // class's get_marshaling_type_name result along with its default IID.
        for ((metadataValue, type) in listOf(
            null to WinRTObjectReferenceMarshalingType.Unknown,
            2L to WinRTObjectReferenceMarshalingType.Agile,
            3L to WinRTObjectReferenceMarshalingType.Standard,
        )) {
            val model = WinRTMetadataModel(namespaces = listOf(WinRTNamespace("Sample", listOf(
                WinRTTypeDefinition(
                    namespace = "Sample", name = "IWidget", kind = WinRTTypeKind.Interface,
                    iid = Guid("6d10e9a1-cb4f-4014-b7ba-cf785ea37e29"),
                ),
                WinRTTypeDefinition(
                    namespace = "Sample", name = "IWidgetFactory", kind = WinRTTypeKind.Interface,
                    iid = Guid("02e9c32c-886c-40bf-933f-14bf1ab07609"),
                    methods = listOf(WinRTMethodDefinition(
                        name = "CreateInstance", returnTypeName = "Sample.Widget",
                        parameters = listOf(
                            WinRTParameterDefinition("baseInterface", "Object"),
                            WinRTParameterDefinition("innerInterface", "Object"),
                        ),
                    )),
                ),
                WinRTTypeDefinition(
                    namespace = "Sample", name = "Widget", kind = WinRTTypeKind.RuntimeClass,
                    isSealedType = true, defaultInterfaceName = "Sample.IWidget",
                    activation = WinRTActivationShape(isActivatable = true, factories = listOf(
                        WinRTAttributedFactoryShape("Sample.IWidgetFactory", WinRTAttributedFactoryKind.Composable, isVisible = true),
                    )),
                    availability = WinRTAvailabilityMetadata(marshalingBehavior = metadataValue),
                ),
            ))))
            val source = KotlinProjectionGenerator().generate(model)
                .single { it.relativePath.endsWith("/Widget.kt") }.contents.replace(Regex("\\s+"), " ")
            val hint = if (type == WinRTObjectReferenceMarshalingType.Unknown) "" else ", WinRTObjectReferenceMarshalingType.${type.name}"
            assertTrue(source, source.contains("attachComposableFactoryResult(__factoryResult, DEFAULT_INTERFACE_IID$hint)"))
        }
    }
}
