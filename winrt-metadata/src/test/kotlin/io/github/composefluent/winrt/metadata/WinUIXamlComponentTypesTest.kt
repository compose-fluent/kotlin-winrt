package io.github.composefluent.winrt.metadata

import org.junit.Assert.assertEquals
import org.junit.Test
import io.github.composefluent.winrt.runtime.XamlFramework

class WinUIXamlComponentTypesTest {
    // CsWinRT helpers.h / code_writers.h walk metadata inheritance and required interfaces.
    @Test
    fun discovers_indirect_contracts_without_name_or_namespace_heuristics() {
        val provider = "Microsoft.UI.Xaml.Markup.IXamlMetadataProvider"
        val types = listOf(
            type("Example.ProviderContract", WinRTTypeKind.Interface, interfaces = listOf(provider)),
            type("Example.ProviderBase", interfaces = listOf("Example.ProviderContract")),
            type("Windows.Custom.Provider", base = "Example.ProviderBase"),
            type("Example.XamlMetaDataProvider"),
            type("Example.XamlMetadataProvider"),
            type("Example.LegacyProvider", interfaces = listOf("Windows.UI.Xaml.Markup.IXamlMetadataProvider")),
            type("Microsoft.Custom.Resources", base = "Microsoft.UI.Xaml.ResourceDictionary"),
            type("Windows.Custom.DerivedResources", base = "Microsoft.Custom.Resources"),
            type("Example.ResourceDictionary"),
            type("Example.LegacyResources", base = "Windows.UI.Xaml.ResourceDictionary"),
            type("Example.CycleA", base = "Example.CycleB"),
            type("Example.CycleB", base = "Example.CycleA"),
        )
        val model = WinRTMetadataModel(types.groupBy { it.namespace }.map { (name, definitions) -> WinRTNamespace(name, definitions) })
        assertEquals(listOf("Example.ProviderBase", "Windows.Custom.Provider"), model.winUIXamlMetadataProviderTypes())
        assertEquals(listOf("Microsoft.Custom.Resources", "Windows.Custom.DerivedResources"), model.winUIXamlResourceDictionaryTypes())
        assertEquals(listOf("Example.LegacyProvider"), model.winUIXamlMetadataProviderTypes(XamlFramework.UWP))
        assertEquals(listOf("Example.LegacyResources"), model.winUIXamlResourceDictionaryTypes(XamlFramework.UWP))
    }

    private fun type(
        name: String,
        kind: WinRTTypeKind = WinRTTypeKind.RuntimeClass,
        base: String? = null,
        interfaces: List<String> = emptyList(),
    ) = WinRTTypeDefinition(
        namespace = name.substringBeforeLast('.'),
        name = name.substringAfterLast('.'),
        kind = kind,
        baseTypeName = base,
        implementedInterfaces = interfaces.map { WinRTInterfaceImplementationDefinition(it) },
    )
}
