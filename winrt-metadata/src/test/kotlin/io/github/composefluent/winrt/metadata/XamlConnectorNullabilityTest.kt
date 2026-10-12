package io.github.composefluent.winrt.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class XamlConnectorNullabilityTest {
    @Test
    fun absent_binding_connector_is_nullable_without_changing_abi_type() {
        // XamlCompiler CSharpPagePass2.tt returns null for pages without bindings.
        val method = WinRTMethodDefinition("GetBindingConnector", "Microsoft.UI.Xaml.Markup.IComponentConnector")
        val connector = WinRTTypeDefinition("Microsoft.UI.Xaml.Markup", "IComponentConnector", WinRTTypeKind.Interface)
        val unrelated = WinRTTypeDefinition("Unrelated", "IComponentConnector", WinRTTypeKind.Interface)
        val definitions = listOf(connector, unrelated).associateBy { it.qualifiedName }
        assertEquals(true, method.returnType.isNullableReferenceProjection("", definitions))
        assertEquals(true, WinRTTypeRef.named(unrelated.qualifiedName).isNullableReferenceProjection("", definitions))
        assertEquals(method.returnTypeName, method.normalized().returnTypeName)
    }
}
