package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTActivationShape
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
            assertTrue(source, source.contains("_activationFactory.activateInstance(Metadata.DEFAULT_INTERFACE_IID$hint)"))
        }
    }
}
