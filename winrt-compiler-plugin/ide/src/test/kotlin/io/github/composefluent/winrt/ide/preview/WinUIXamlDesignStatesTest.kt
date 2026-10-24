package io.github.composefluent.winrt.ide.preview

import org.junit.Assert.*
import org.junit.Test

class WinUIXamlDesignStatesTest {
    private val markup = """<UserControl xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Grid>
        <VisualStateManager.VisualStateGroups><VisualStateGroup x:Name="SizeStates">
            <VisualState x:Name="Wide"><VisualState.StateTriggers><AdaptiveTrigger MinWindowWidth="600"/></VisualState.StateTriggers><VisualState.Setters><Setter Target="Caption.Text" Value="Wide"/></VisualState.Setters></VisualState>
            <VisualState x:Name="Narrow"><VisualState.StateTriggers><AdaptiveTrigger MinWindowWidth="1"/></VisualState.StateTriggers><Storyboard x:Name="Motion"/></VisualState>
        </VisualStateGroup><VisualStateGroup x:Name="Other"><VisualState x:Name="On"><VisualState.StateTriggers><StateTrigger IsActive="True"/></VisualState.StateTriggers></VisualState></VisualStateGroup></VisualStateManager.VisualStateGroups>
        <TextBlock x:Name="Caption" Text="Base"/>
    </Grid></UserControl>"""

    @Test fun selectingStateUsesSdkTriggersAndRetainsSettersStoryboardsAndOtherGroups() {
        val groups = WinUIXamlDesignStates.groups(markup)
        assertEquals(listOf("SizeStates", "Other"), groups.map { it.label })
        assertEquals(listOf("Wide", "Narrow"), groups.first().states)
        val forced = WinUIXamlDesignStates.select(markup, mapOf(groups.first().key to "Narrow"))
        assertFalse(forced.contains("AdaptiveTrigger"))
        assertTrue(forced.contains("IsActive=\"false\""))
        assertTrue(forced.contains("IsActive=\"true\""))
        assertTrue(forced.contains("IsActive=\"True\""))
        assertTrue(forced.contains("Target=\"Caption.Text\""))
        assertTrue(forced.contains("Storyboard"))
        assertEquals(groups, WinUIXamlDesignStates.groups(forced))
        assertEquals(markup, WinUIXamlDesignStates.select(markup, emptyMap()))
    }
    @Test fun removedOrRenamedStateCannotSelectADifferentGroupSilently() {
        val key = WinUIXamlDesignStates.groups(markup).first().key
        assertTrue(runCatching { WinUIXamlDesignStates.select(markup, mapOf(key to "Removed")) }.isFailure)
        assertTrue(runCatching { WinUIXamlDesignStates.select(markup.replace("SizeStates", "NewStates"), mapOf(key to "Wide")) }.isFailure)
        assertTrue(WinUIXamlDesignStates.groups(markup.replace("x:Name=\"Narrow\"", "x:Name=\"Wide\"")).none { it.label == "SizeStates" })
    }
    @Test fun externalEntitiesCannotEnterStatePreview() {
        assertTrue(runCatching { WinUIXamlDesignStates.groups("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///secret'>]><x>&e;</x>") }.isFailure)
    }
}
