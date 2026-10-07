package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.runtime.*
import org.junit.Assert.*
import org.junit.Test
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

class WinRTPreviewTest {
    private val namespaces = """xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" """
    @Test fun compiled_bindings_and_events_use_design_values_without_losing_runtime_resource_references() {
        val result = WinRTXamlDesignDocument.prepare("""<Page $namespaces xmlns:d="http://schemas.microsoft.com/expression/blend/2008" x:Class="sample.Page">
          <StackPanel><Button x:Name="Action" Content="{x:Bind Title}" d:Content="Design title" Click="Clicked" Style="{StaticResource SubtleButtonStyle}"/>
          <TextBlock Foreground="{ThemeResource TextFillColorPrimaryBrush}" Text="{Binding Message}"/></StackPanel></Page>""") { _, type, member -> type == "Button" && member == "Click" }
        val document = parse(result.markup)
        val button = document.getElementsByTagNameNS("*", "Button").item(0) as org.w3c.dom.Element
        assertEquals("Design title", button.getAttribute("Content"))
        assertFalse(button.hasAttribute("Click"))
        assertEquals("{StaticResource SubtleButtonStyle}", button.getAttribute("Style"))
        assertTrue(result.markup.contains("{ThemeResource TextFillColorPrimaryBrush}"))
        assertTrue(result.markup.contains("{Binding Message}"))
        assertFalse(result.markup.contains("x:Class"))
        assertEquals(2, result.notes.size)
    }
    @Test fun application_resources_and_relative_dictionaries_keep_their_original_package_scope() {
        val result = WinRTXamlDesignDocument.prepare("""<Page $namespaces><Page.Resources><ResourceDictionary Source="../Styles/Page.xaml"/></Page.Resources><TextBlock/></Page>""",
            """<Application $namespaces xmlns:local="using:sample"><Application.Resources><ResourceDictionary><ResourceDictionary.MergedDictionaries>
            <local:Resources/><ResourceDictionary Source="../Common/App.xaml"/></ResourceDictionary.MergedDictionaries></ResourceDictionary></Application.Resources></Application>""",
            "UI/Pages/Page.xaml", "UI/App.xaml")
        assertTrue(result.markup, result.markup.contains("ms-appx:///UI/Styles/Page.xaml"))
        assertTrue(result.markup, result.markup.contains("ms-appx:///Common/App.xaml"))
        assertTrue(result.markup, result.markup.contains("xmlns:local=\"using:sample\""))
        assertEquals(1, parse(result.markup).getElementsByTagNameNS("*", "Grid.Resources").length)
    }
    @Test fun window_content_is_rendered_without_constructing_its_codebehind_and_unsafe_xml_is_rejected() {
        val result = WinRTXamlDesignDocument.prepare("""<Window $namespaces x:Class="sample.Window" Title="Window"><Window.Content><Grid><TextBlock Text="Hello"/></Grid></Window.Content></Window>""")
        assertEquals(0, parse(result.markup).getElementsByTagNameNS("*", "Window").length)
        assertEquals(1, parse(result.markup).getElementsByTagNameNS("*", "TextBlock").length)
        assertTrue(runCatching { WinRTXamlDesignDocument.prepare("""<!DOCTYPE Page [<!ENTITY secret SYSTEM "file:///unavailable">]><Page $namespaces>&secret;</Page>""") }.isFailure)
    }
    @OptIn(ExperimentalJewelApi::class)
    @Test fun preview_pixels_preserve_premultiplied_alpha_and_hit_testing_selects_the_deepest_visible_node() {
        val image = bgraImage(WinRTXamlVisualImage(2, 1, byteArrayOf(0, 0, -1, -1, 0, -128, 0, -128)))
        assertEquals(0xffff0000.toInt(), image.getRGB(0, 0))
        assertEquals(0x8000ff00.toInt(), image.getRGB(1, 0))
        fun node(path: List<Int>, bounds: WinRTXamlVisualBounds) = WinRTXamlVisualNode(path, "Control", "", bounds)
        val bounds = WinRTXamlVisualBounds(0.0, 0.0, 100.0, 100.0)
        val nodes = listOf(node(emptyList(), bounds), node(listOf(0), bounds), node(listOf(0, 0), WinRTXamlVisualBounds(10.0, 10.0, 30.0, 20.0)))
        assertEquals(listOf(0, 0), WinRTVisualInspectionSelection.hit(nodes, 20.0, 20.0)!!.path)
        assertEquals(listOf(0), WinRTVisualInspectionSelection.hit(nodes, 80.0, 80.0)!!.path)
        assertNull(WinRTVisualInspectionSelection.hit(nodes, 101.0, 0.0))
        val tree = visualInspectionTree(nodes).walkDepthFirst().toList()
        assertEquals(nodes.map { it.path }, tree.map { it.id })
        assertEquals(listOf(0), tree.last().parent!!.id)
    }
    private fun parse(value: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(InputSource(StringReader(value)))
}
