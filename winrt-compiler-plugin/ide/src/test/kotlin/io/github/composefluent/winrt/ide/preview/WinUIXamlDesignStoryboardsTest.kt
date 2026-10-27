package io.github.composefluent.winrt.ide.preview

import org.junit.Assert.*
import org.junit.Test

class WinUIXamlDesignStoryboardsTest {
    @Test fun unnamed_resource_owner_gets_a_stable_collision_free_design_only_name() {
        val markup = """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Border x:Name="_KotlinDesignerStoryboardOwner0"/><UserControl><UserControl.Resources><Storyboard x:Key="Motion"/></UserControl.Resources></UserControl></Grid>"""
        val prepared = WinUIXamlDesignStoryboards.prepare(markup)
        assertEquals("_KotlinDesignerStoryboardOwner1", prepared.storyboards.single().owner)
        assertTrue(prepared.markup.contains("x:Name=\"_KotlinDesignerStoryboardOwner1\""))
        assertEquals(prepared, WinUIXamlDesignStoryboards.prepare(markup))
        assertFalse(markup.contains("Owner1"))
    }
    @Test fun template_resources_are_offered_only_for_realized_source_instances() {
        val marker = io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol.PREVIEW_SOURCE_KEY
        val markup = """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Grid.Resources><DataTemplate x:Key="Row"><Grid x:Name="Repeated"><Grid.Resources><x:String x:Key="$marker">template-source</x:String><Storyboard x:Key="Fade"/></Grid.Resources></Grid></DataTemplate></Grid.Resources></Grid>"""
        val prepared = WinUIXamlDesignStoryboards.prepare(markup)
        assertTrue(prepared.realized(emptyList()).isEmpty())
        fun node(path: List<Int>, name: String, source: String) = io.github.composefluent.winrt.runtime.WinUIXamlVisualNode(
            path, "Grid", name, io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds(0.0, 0.0, 1.0, 1.0), sourceId = source)
        val clocks = prepared.realized(listOf(node(emptyList(), "Root", "root"), node(listOf(0), "Repeated", "template-source"), node(listOf(1), "Repeated", "template-source"), node(listOf(2), "Repeated", "different-template")))
        assertEquals(2, clocks.size)
        assertNotEquals(clocks[0].id, clocks[1].id)
        assertEquals(listOf(0), clocks[0].instancePath)
        assertEquals(listOf(1), clocks[1].instancePath)
        assertTrue(clocks.all { it.owner.isEmpty() && it.key == "Fade" })
    }
    @Test fun resource_clocks_keep_exact_owners_and_exclude_ambiguous_namescopes() {
        val markup = """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
            <Grid.Resources><Storyboard x:Key="RootMotion"/><ControlTemplate x:Key="Template"><Grid x:Name="Hidden"><Grid.Resources><Storyboard x:Key="HiddenMotion"/></Grid.Resources></Grid></ControlTemplate></Grid.Resources>
            <Border x:Name="Panel"><Border.Resources><ResourceDictionary><Storyboard x:Name="Motion"/></ResourceDictionary></Border.Resources></Border>
            <Grid x:Name="Duplicate"><Grid.Resources><Storyboard x:Key="Invalid"/></Grid.Resources></Grid><Grid x:Name="Duplicate"/>
        </Grid>"""
        assertEquals(listOf(DesignerStoryboard("", "RootMotion"), DesignerStoryboard("Panel", "Motion")), WinUIXamlDesignStoryboards.discover(markup))
        assertTrue(runCatching { WinUIXamlDesignStoryboards.discover("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///secret'>]><x>&e;</x>") }.isFailure)
    }
}
