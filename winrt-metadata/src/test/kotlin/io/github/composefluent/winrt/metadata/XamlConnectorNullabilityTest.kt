package io.github.composefluent.winrt.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class XamlConnectorNullabilityTest {
    @Test
    fun absent_binding_connector_is_nullable_without_changing_abi_type() {
        // XamlCompiler CSharpPagePass2.tt returns null for pages without bindings.
        val method = WinRTMethodDefinition("GetBindingConnector", "Microsoft.UI.Xaml.Markup.IComponentConnector")
        val projected = method.withNullableReturnContract("Microsoft.UI.Xaml.Markup.IComponentConnector")
        assertEquals("Microsoft.UI.Xaml.Markup.IComponentConnector?", projected.returnTypeName)
        assertEquals(method.returnType, projected.returnType)
        assertEquals(method, method.withNullableReturnContract("Unrelated.IComponentConnector"))
    }
}
