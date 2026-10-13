package io.github.composefluent.winrt.metadata

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.Comparator
import io.github.composefluent.winrt.runtime.XamlFramework

class WinUiXamlSchemaMetadataTest {
    @Test
    fun uwp_schema_uses_the_sdk_content_attribute_and_versioned_connector_interfaces() {
        // CsWinRT helpers.h preserves WUX names; Windows SDK markup.h separates IComponentConnector2.
        val output = Files.createTempFile("uwp-xaml-schema-", ".winmd")
        try {
            val name = "Sample.Page"
            WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Schema",
                listOf(WinUiXamlApplicationTypeDescriptor(name, "Windows.UI.Xaml.Controls.Page",
                    XamlFramework.Uwp.xamlComponentInterfaceNames)),
                mapOf(name to WinUiXamlApplicationTypeMembers(contentProperty = "Body")), output,
                mapOf("Windows.UI.Xaml.Markup.ContentPropertyAttribute" to "Windows.Foundation.UniversalApiContract",
                    "Windows.UI.Xaml.Controls.Page" to "Windows.Foundation.UniversalApiContract",
                    "Windows.UI.Xaml.Markup.IComponentConnector" to "Windows.Foundation.UniversalApiContract",
                    "Windows.UI.Xaml.Markup.IComponentConnector2" to "Windows.Foundation.UniversalApiContract"),
                framework = XamlFramework.Uwp)
            val type = WinRTMetadataLoader.load(listOf(output)).namespaces.single().types.single()
            assertEquals(XamlFramework.Uwp.xamlComponentInterfaceNames, type.implementedInterfaces.map { it.interfaceName })
            assertTrue(type.customAttributes.any { it.typeName == "Windows.UI.Xaml.Markup.ContentPropertyAttribute" })
            assertFalse(type.customAttributes.any { it.typeName.startsWith("Microsoft.UI.Xaml") })
        } finally { Files.deleteIfExists(output) }
    }

    @Test
    fun enum_schema_round_trips_as_an_enum_with_literal_values() {
        // .cswinrt/src/Authoring/WinRT.SourceGenerator/WinRTTypeWriter.cs:
        // AddComponentType(symbol) + VisitEnumDeclaration preserve System.Enum.
        val directory = Files.createTempDirectory("winrt-xaml-enum-")
        try {
            val output = directory.resolve("Schema.winmd")
            WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Schema", listOf(
                WinUiXamlApplicationTypeDescriptor("Sample.Mode", enumEntries = listOf("First", "Second")),
            ), emptyMap(), output, emptyMap())
            val type = WinRTMetadataLoader.load(listOf(output)).namespaces.single().types.single()
            assertEquals(WinRTTypeKind.Enum, type.kind)
            assertEquals("System.Enum", type.baseTypeName)
            assertEquals(WinRTIntegralType.Int32, type.enumUnderlyingType)
            assertEquals(listOf("First", "Second"), type.enumMembers.map { it.name })
            assertEquals(listOf(0uL, 1uL), type.fields.filter { it.isLiteral }.map { it.constantValueBits })
            assertFalse(type.activation.isActivatable)
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
