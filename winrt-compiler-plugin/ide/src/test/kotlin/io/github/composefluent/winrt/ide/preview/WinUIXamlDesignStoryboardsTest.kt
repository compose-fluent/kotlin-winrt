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
