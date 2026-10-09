package io.github.composefluent.winrt.metadata

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WinRTXamlNamespacesTest {
    @Test fun header_discovers_attached_and_binding_types_in_conditional_namespaces() {
        // XAMLC DirectUISchemaContext.DirectUI2010Paths is the namespace owner; this is
        // only the same application-header discovery previously owned by the source scanner.
        val source = Files.createTempFile("xaml-types-", ".xaml")
        Files.writeString(source, """<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
            xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" xmlns:local="using:sample"
            xmlns:conditional="using:sample?IsApiContractPresent(Windows.Foundation.UniversalApiContract,8)">
            <conditional:View x:DataType="local:Feature" local:Owner.Value="{x:Bind local:Model.Value}" Text="Before" />
            </Page>""".trimIndent())
        val references = WinRTXamlNamespaces.typeReferences(source)
        assertTrue(listOf("sample.View") in references)
        assertTrue(listOf("sample.Owner") in references)
        assertTrue(listOf("sample.Model") in references)
        assertTrue(listOf("sample.Feature") in references)
        assertTrue(references.any { "Microsoft.UI.Xaml.Controls.Page" in it })
        Files.writeString(source, Files.readString(source).replace("Before", "After"))
        assertEquals(references, WinRTXamlNamespaces.typeReferences(source))
        Files.writeString(source, "<Page invalid")
        assertTrue(runCatching { WinRTXamlNamespaces.typeReferences(source) }.isFailure)
    }
}
