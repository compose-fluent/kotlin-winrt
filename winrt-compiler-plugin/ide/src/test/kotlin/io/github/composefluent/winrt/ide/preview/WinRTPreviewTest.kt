package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.runtime.*
import io.github.composefluent.winrt.ide.hotreload.WinRTHotReloadState
import org.junit.Assert.*
import org.junit.Test
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

class WinRTPreviewTest {
    private val namespaces = """xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" """
    @Test fun visual_tree_selects_the_main_window_without_requiring_a_picker_and_preserves_an_explicit_choice() {
        val primary = WinRTXamlHotReloadRoot("sample.MainWindow", "MainWindow.xaml", "hash", 0, emptyList())
        val secondary = primary.copy(className = "sample.SettingsWindow", resourcePath = "SettingsWindow.xaml")
        val selection = WinRTVisualInspectionSelection()
        assertSame(primary, selection.resolve(listOf(secondary, primary)))
        selection.selectRoot(WinRTVisualInspectionSelection.key(secondary))
        selection.path.value = listOf(0)
        assertSame(secondary, selection.resolve(listOf(primary, secondary)))
        selection.reset()
        assertNull(selection.root.value)
        assertEquals(emptyList<Int>(), selection.path.value)
        assertSame(primary, selection.resolve(listOf(secondary, primary)))
    }
    @Test fun preview_state_uses_the_single_shared_protocol_model_without_compose_abi_fields() {
        // The development wire model belongs to winrt-runtime, also bundled
        // for isolated FIR metadata loading; it is not a Compose UI model.
        val snapshot = WinRTXamlVisualSnapshot(emptyList(), emptyList())
        assertSame(snapshot, WinRTHotReloadState(inspection = snapshot).inspection)
        assertFalse(WinRTXamlVisualSnapshot::class.java.declaredFields.any { it.name == "\$stable" })
    }
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
    @Test fun unavailable_containers_retain_visual_content_resources_and_namespace_scope() {
        val result = WinRTXamlDesignDocument.prepare("""<local:WindowEx $namespaces xmlns:local="using:sample" xmlns:sdk="using:Microsoft.UI.Xaml.Controls" xmlns:d="http://schemas.microsoft.com/expression/blend/2008" x:Class="sample.Window">
          <x:Properties><x:Property Name="Title" Type="x:String" DefaultValue="Window"/></x:Properties>
          <local:WindowEx.SystemBackdrop><local:Backdrop><local:Backdrop.Fallback><local:Tint/></local:Backdrop.Fallback></local:Backdrop></local:WindowEx.SystemBackdrop>
          <local:WindowEx.Content><Grid><local:Example x:Name="Example" Width="{x:Bind MissingWidth}">
            <local:Example.Resources><Style x:Key="LabelStyle" TargetType="sdk:TextBlock"/></local:Example.Resources>
            <local:Example.Example><Button x:Name="Action" Content="{x:Bind MissingTitle}" d:Content="Design button" Click="Clicked" x:FieldModifier="public"/></local:Example.Example>
            <local:Example.Options><TextBlock Text="Options" Style="{StaticResource LabelStyle}"/></local:Example.Options>
            <local:Example.Brush><SolidColorBrush Color="Red"/></local:Example.Brush>
          </local:Example></Grid></local:WindowEx.Content></local:WindowEx>""",
            sdkType = { uri, _ -> uri == "using:Microsoft.UI.Xaml.Controls" },
            visualType = { uri, type -> uri == io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalog.PRESENTATION && type in setOf("Grid", "Button", "TextBlock") }
        ) { _, type, member -> type == "Button" && member == "Click" }
        val document = parse(result.markup)
        val button = document.getElementsByTagNameNS("*", "Button").item(0) as org.w3c.dom.Element
        assertEquals("Design button", button.getAttribute("Content"))
        assertFalse(button.hasAttribute("Click"))
        assertFalse(button.hasAttributeNS(io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalog.XAML, "FieldModifier"))
        assertFalse(result.markup.contains("x:Bind"))
        assertEquals(0, document.getElementsByTagNameNS("*", "Properties").length)
        assertEquals(0, document.getElementsByTagNameNS("*", "SolidColorBrush").length)
        val resources = document.getElementsByTagNameNS("*", "Border.Resources").item(0) as org.w3c.dom.Element
        assertEquals("using:Microsoft.UI.Xaml.Controls", resources.lookupNamespaceURI("sdk"))
        assertEquals(2, document.getElementsByTagNameNS("*", "Border").length)
        assertEquals("The original Grid remains inside the design viewport", 2, document.getElementsByTagNameNS("*", "Grid").length)
        assertTrue(result.notes.any { it.contains("placeholder") })
    }
    @Test fun source_dictionaries_are_inlined_recursively_in_their_own_relative_scope() {
        val calls = mutableListOf<Pair<String, String>>()
        val result = WinRTXamlDesignDocument.prepare("""<Page $namespaces><Page.Resources><ResourceDictionary Source="../Styles/Colors.xaml"/></Page.Resources><TextBlock Foreground="{StaticResource Accent}"/></Page>""",
            packagePath = "UI/Page.xaml", resource = { uri, origin ->
                calls += uri to origin
                when (uri) {
                    "../Styles/Colors.xaml" -> WinRTXamlDesignResource("""<ResourceDictionary $namespaces x:Class="sample.Colors"><ResourceDictionary.MergedDictionaries><ResourceDictionary Source="Brushes.xaml"/></ResourceDictionary.MergedDictionaries></ResourceDictionary>""", "Styles/Colors.xaml")
                    "Brushes.xaml" -> WinRTXamlDesignResource("""<ResourceDictionary $namespaces><SolidColorBrush x:Key="Accent" Color="Tomato"/></ResourceDictionary>""", "Styles/Brushes.xaml")
                    else -> null
                }
            })
        assertEquals(listOf("../Styles/Colors.xaml" to "UI/Page.xaml", "Brushes.xaml" to "Styles/Colors.xaml"), calls)
        assertTrue(result.markup, result.markup.contains("Color=\"Tomato\""))
        assertFalse(result.markup, result.markup.contains("Source="))
        assertFalse(result.markup, result.markup.contains("x:Class"))
        assertTrue(runCatching { WinRTXamlDesignDocument.prepare("""<Page $namespaces><Page.Resources><ResourceDictionary Source="Missing.xaml"/></Page.Resources></Page>""", resource = { _, _ -> null }) }.isFailure)
        assertTrue(runCatching { WinRTXamlDesignDocument.prepare("""<Page $namespaces><Page.Resources><ResourceDictionary Source="Cycle.xaml"/></Page.Resources></Page>""", resource = { _, _ -> WinRTXamlDesignResource("""<ResourceDictionary $namespaces Source="Cycle.xaml"/>""", "Cycle.xaml") }) }.exceptionOrNull()!!.message!!.contains("cycle"))
    }
    @Test fun disabled_project_code_keeps_sdk_controls_design_children_and_dictionary_source() {
        val result = WinRTXamlDesignDocument.prepare("""<Page $namespaces xmlns:local="using:sample" xmlns:sdk="using:Microsoft.UI.Xaml.Controls" xmlns:d="http://schemas.microsoft.com/expression/blend/2008">
          <Page.Resources><ResourceDictionary><ResourceDictionary.MergedDictionaries><local:Colors/></ResourceDictionary.MergedDictionaries><local:Converter x:Key="Converter"/><Style x:Key="CustomStyle" TargetType="local:Custom"/></ResourceDictionary></Page.Resources>
          <StackPanel><sdk:TextBlock Text="SDK" local:Attached.Value="custom"/><local:Custom x:Name="Custom" Width="180" Margin="12"/>
          <TextBlock Text="{Binding Value, Converter={StaticResource Converter}}"/><ListView><d:ListView.Items><d:TextBlock Text="Sample item"/></d:ListView.Items></ListView></StackPanel></Page>""",
            resource = { uri, _ -> if (uri == "using:sample.Colors") WinRTXamlDesignResource("""<ResourceDictionary $namespaces x:Class="sample.Colors"><SolidColorBrush x:Key="Accent" Color="Blue"/></ResourceDictionary>""", "Colors.xaml") else null },
            sdkType = { uri, _ -> uri == "using:Microsoft.UI.Xaml.Controls" })
        val document = parse(result.markup)
        assertEquals(1, document.getElementsByTagNameNS("using:Microsoft.UI.Xaml.Controls", "TextBlock").length)
        val placeholder = document.getElementsByTagNameNS("*", "Border").item(0) as org.w3c.dom.Element
        assertEquals("Custom", placeholder.getAttributeNS(io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalog.XAML, "Name"))
        assertEquals("180", placeholder.getAttribute("Width"))
        assertTrue(result.markup, result.markup.contains("Text=\"Sample item\""))
        assertTrue(result.markup, result.markup.contains("Color=\"Blue\""))
        assertFalse(result.markup, result.markup.contains("local:Colors"))
        assertFalse(result.markup, result.markup.contains("{Binding Value"))
        assertFalse(result.markup, result.markup.contains("TargetType=\"local:Custom\""))
        assertFalse(result.markup, result.markup.contains("local:Attached.Value"))
        assertFalse(result.markup, result.markup.contains("x:Class"))
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
