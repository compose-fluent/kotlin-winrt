package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTFieldDefinition
import io.github.composefluent.winrt.metadata.WinRTInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.runtime.Guid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataDrivenProjectionTest {
    @Test
    fun framework_view_inheritance_is_only_taken_from_metadata() {
        // CsWinRT code_writers.h write_interface delegates to write_type_inheritance.
        val core = WinRTTypeDefinition("Windows.ApplicationModel.Core", "IFrameworkView", WinRTTypeKind.Interface,
            iid = Guid("11111111-1111-1111-1111-111111111111"))
        val xaml = WinRTTypeDefinition("Microsoft.UI.Xaml", "IFrameworkView", WinRTTypeKind.Interface,
            iid = Guid("22222222-2222-2222-2222-222222222222"))
        fun render(type: WinRTTypeDefinition): String {
            val model = model(core, type)
            val plan = KotlinProjectionPlanner().plan(model).single { it.type.qualifiedName == type.qualifiedName }
            return KotlinProjectionRenderer().render(plan).contents
        }
        assertFalse(render(xaml).contains("windows.applicationmodel.core"))
        val inherited = render(xaml.copy(implementedInterfaces = listOf(WinRTInterfaceImplementationDefinition(core.qualifiedName))))
        assertTrue(inherited, inherited.contains("windows.applicationmodel.core"))
    }

    @Test
    fun struct_signatures_use_metadata_for_nested_fields_and_reject_unresolved_fields() {
        // CsWinRT GuidGenerator.GetSignature uses the actual field signatures, not a type-name fallback.
        val nested = WinRTTypeDefinition("Example", "Nested", WinRTTypeKind.Struct,
            fields = listOf(WinRTFieldDefinition("Value", "Int32")))
        val outer = WinRTTypeDefinition("Example", "Outer", WinRTTypeKind.Struct,
            fields = listOf(WinRTFieldDefinition("Value", "Example.Nested")))
        fun signature(vararg types: WinRTTypeDefinition): String? {
            val plan = KotlinProjectionPlanner().plan(model(*types)).single { it.type.name == "Outer" }
            return KotlinProjectionRenderer().nativeStructGuidSignature(plan)
        }
        assertEquals("struct(Example.Outer;struct(Example.Nested;i4))", signature(nested, outer))
        val missing = outer.copy(fields = listOf(WinRTFieldDefinition("Value", "Windows.Foundation.DateTime")))
        assertNull(signature(missing))
        val cyclic = outer.copy(fields = listOf(WinRTFieldDefinition("Value", "Example.Outer")))
        assertEquals("", model(cyclic).semanticHelpers().guidSignatureDescriptor(cyclic).signatureFragment)
    }

    private fun model(vararg types: WinRTTypeDefinition) = WinRTMetadataModel(
        types.toList().groupBy { it.namespace }.map { (namespace, definitions) -> WinRTNamespace(namespace, definitions) },
    )
}
