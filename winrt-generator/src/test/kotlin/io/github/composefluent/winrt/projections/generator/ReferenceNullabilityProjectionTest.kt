package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.runtime.Guid
import org.junit.Assert.*
import org.junit.Test

class ReferenceNullabilityProjectionTest {
    // CsWinRT code_writers.h writes type semantics; Marshalers.cs preserves null
    // interface/class/delegate pointers. No API names participate in this contract.
    private val reference = WinRTTypeDefinition("Example", "INode", WinRTTypeKind.Interface,
        iid = Guid("12345678-1234-1234-1234-123456789012"))
    private val value = WinRTTypeDefinition("Example", "Value", WinRTTypeKind.Struct,
        fields = listOf(WinRTFieldDefinition("Number", "Int32")))
    private val model = WinRTMetadataModel(listOf(WinRTNamespace("Example", listOf(reference, value))))

    @Test
    fun value_types_reference_types_and_generic_arguments_share_one_contract() {
        val renderer = KotlinProjectionRenderer(guidSignatureHelpers = model.semanticHelpers())
        assertEquals("example.INode?", renderer.resolveValueTypeName("Example.INode").toString())
        assertEquals("example.INode", renderer.resolveTypeName("Example.INode").toString())
        assertEquals("example.Value", renderer.resolveValueTypeName("Example.Value").toString())
        assertEquals("kotlin.String", renderer.resolveValueTypeName("String").toString())
        assertEquals("kotlin.Int", renderer.resolveValueTypeName("Int32").toString())
        assertEquals("T0", renderer.resolveValueTypeName("T0").toString())
        assertEquals("kotlin.Array<example.INode?>", renderer.resolveValueTypeName("Array<Example.INode>").toString())
        assertEquals("kotlin.collections.MutableList<example.INode?>",
            renderer.resolveTypeName("Windows.Foundation.Collections.IVector<Example.INode>").toString())
    }

    @Test
    fun arbitrary_methods_and_abi_bindings_preserve_nullable_references() {
        val method = WinRTMethodDefinition("RoundTrip", "Example.INode",
            parameters = listOf(WinRTParameterDefinition("value", "Example.INode")))
        val node = reference.copy(methods = listOf(method))
        val input = WinRTMetadataModel(listOf(WinRTNamespace("Example", listOf(node, value))))
        val plan = KotlinProjectionPlanner().plan(input).single { it.type.name == "INode" }
        val output = KotlinProjectionRenderer().render(plan).contents
        assertTrue(output, output.contains("roundTrip(`value`: INode?): INode?") || output.contains("roundTrip(value: INode?): INode?"))
        assertEquals("Example.INode", input.normalized().namespaces.single().types.first { it.name == "INode" }.methods.single().returnTypeName)
        val binding = KotlinProjectionPlanner().classifyAbiTypeBinding("Example.INode", "Example", mapOf(reference.qualifiedName to reference))
        assertTrue(binding.isNullableAbiReturn)
        assertEquals("Example.INode", binding.resolvedTypeName)
        assertEquals(reference.iid, binding.interfaceId)
    }
}
