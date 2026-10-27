@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds

class DesignerStateEditingTest : BasePlatformTestCase() {
    private val group = DesignerStateGroup("ignored-transformed-path", "Sizes", listOf("Wide", "Narrow"))
    private fun base(setters: String = "", child: String = ""): DesignerSelection {
        val xml = myFixture.configureByText("State.xaml", """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
          <VisualStateManager.VisualStateGroups><VisualStateGroup x:Name="Sizes"><VisualState x:Name="Wide"><VisualState.Setters>$setters</VisualState.Setters></VisualState><VisualState x:Name="Narrow"/></VisualStateGroup></VisualStateManager.VisualStateGroups>
          <Button x:Name="Target" Width="100" Canvas.Left="10"/>$child</Grid>""") as XmlFile
        val tag = xml.rootTag!!.subTags[1]
        val props = listOf("Width", "Canvas.Left").map { DesignerProperty(it, "Double", tag.getAttributeValue(it), emptyList(), true, false, true) }
        return DesignerSelection(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(tag), tag.text, props,
            WinUIXamlVisualBounds(0.0, 0.0, 100.0, 40.0), "Canvas", tag.attributes.associate { it.name to it.value.orEmpty() })
    }
    fun testStateApplyResetAndUndoPreserveBaseAttributesAndOtherStates() {
        val original = base("""<Setter Target="Target.Width" Value="200"/>""")
        val selection = DesignerStateEditing.selection(original, group, "Wide")!!
        assertEquals("200", selection.properties.first().value)
        val before = myFixture.editor.document.text
        WinUIXamlDesignerEditing.apply(project, selection, mapOf("Width" to "300", "Canvas.Left" to "25"), "Record state layout")
        assertEquals("100", original.pointer.element!!.getAttributeValue("Width"))
        assertTrue(myFixture.editor.document.text.contains("Target.(Canvas.Left)"))
        assertTrue(myFixture.editor.document.text.contains("Value=\"300\""))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(before, myFixture.editor.document.text)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val refreshed = DesignerStateEditing.selection(original, group, "Wide")!!
        WinUIXamlDesignerEditing.apply(project, refreshed, mapOf("Width" to null), "Reset state width")
        assertFalse(myFixture.editor.document.text.contains("Target.Width"))
        assertEquals("100", original.pointer.element!!.getAttributeValue("Width"))
    }
    fun testComplexDuplicateAndNonDependencySettersCannotBeOverwritten() {
        listOf("""<Setter Target="Target.Width"><Setter.Value><x:Double>200</x:Double></Setter.Value></Setter>""",
            """<Setter Target="Target.Width" Value="200"/><Setter Target="Target.Width" Value="300"/>""").forEach { source ->
            val selection = DesignerStateEditing.selection(base(source), group, "Wide")!!
            val before = myFixture.editor.document.text
            assertTrue(runCatching { WinUIXamlDesignerEditing.apply(project, selection, mapOf("Width" to "400"), "Invalid override") }.isFailure)
            assertEquals(before, myFixture.editor.document.text)
        }
        val selection = DesignerStateEditing.selection(base().let { it.copy(properties = it.properties.map { p -> p.copy(dependencyProperty = false) }) }, group, "Wide")!!
        assertTrue(runCatching { WinUIXamlDesignerEditing.apply(project, selection, mapOf("Width" to "400"), "Invalid override") }.isFailure)
    }
    fun testNamescopeAndStaleStateAreRejected() {
        assertNull(DesignerStateEditing.selection(base(child = """<Button x:Name="Target"/>"""), group, "Wide"))
        assertNotNull(DesignerStateEditing.selection(base(child = """<Grid.Resources><DataTemplate x:Key="T"><Button x:Name="Target"/></DataTemplate></Grid.Resources>"""), group, "Wide"))
        val selection = DesignerStateEditing.selection(base(), group, "Wide")!!
        WriteCommandAction.runWriteCommandAction(project) { selection.state!!.pointer.element!!.setAttribute("Tag", "changed") }
        val before = myFixture.editor.document.text
        assertTrue(runCatching { WinUIXamlDesignerEditing.apply(project, selection, mapOf("Width" to "400"), "Stale override") }.isFailure)
        assertEquals(before, myFixture.editor.document.text)
    }
    fun testCreateGroupIsAtomicAndUndoable() {
        // SDK VisualStateManager collection ownership; no runtime projection change.
        val selected = base()
        val before = myFixture.editor.document.text
        DesignerStateAuthoring.addGroup(project, selected, "Interactions", "Pressed")
        assertTrue(myFixture.editor.document.text.contains("VisualStateManager.VisualStateGroups"))
        assertTrue(myFixture.editor.document.text.contains("x:Name=\"Interactions\""))
        assertTrue(myFixture.editor.document.text.contains("x:Name=\"Pressed\""))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(before, myFixture.editor.document.text)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val refreshed = selected.copy(text = selected.pointer.element!!.text)
        listOf("Target", "Wide", "invalid name").forEach { invalid ->
            assertTrue(runCatching { DesignerStateAuthoring.addGroup(project, refreshed, "Interactions", invalid) }.isFailure)
            assertEquals(before, myFixture.editor.document.text)
        }
    }
    fun testAddStatePreservesExistingSettersAndRejectsStaleGroups() {
        val xml = myFixture.addFileToProject("States.xaml", """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
          <VisualStateManager.VisualStateGroups><VisualStateGroup x:Name="Sizes"><VisualState x:Name="Wide"><VisualState.Setters><Setter Target="Target.Width" Value="300"/></VisualState.Setters></VisualState><VisualState x:Name="Narrow"/></VisualStateGroup></VisualStateManager.VisualStateGroups><Button x:Name="Target"/>
        </Grid>""") as XmlFile
        myFixture.configureFromExistingVirtualFile(xml.virtualFile)
        val before = xml.text
        DesignerStateAuthoring.addState(project, xml.virtualFile, group, "Medium")
        assertTrue(xml.text.contains("x:Name=\"Medium\""))
        assertTrue(xml.text.contains("Value=\"300\""))
        val updated = xml.text
        assertTrue(runCatching { DesignerStateAuthoring.addState(project, xml.virtualFile, group, "Large") }.isFailure)
        assertEquals(updated, xml.text)
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(before, myFixture.editor.document.text)
    }

}
