package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DesignerStateDeletionTest : BasePlatformTestCase() {
    private val group = DesignerStateGroup("source", "Sizes", listOf("Wide", "Narrow"))
    private fun source(extra: String = ""): XmlFile {
        val xml = myFixture.addFileToProject("States.xaml", """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
          <VisualStateManager.VisualStateGroups><VisualStateGroup x:Name="Sizes">
            <VisualStateGroup.Transitions><VisualTransition From="Wide" To="Narrow"/><VisualTransition To="Narrow"/></VisualStateGroup.Transitions>
            <VisualState x:Name="Wide"><VisualState.Setters><Setter Target="Target.Width" Value="300"/></VisualState.Setters></VisualState><VisualState x:Name="Narrow"/>
          </VisualStateGroup></VisualStateManager.VisualStateGroups><Button x:Name="Target" Width="100"/>$extra</Grid>""") as XmlFile
        myFixture.configureFromExistingVirtualFile(xml.virtualFile)
        return xml
    }
    fun testDeleteStateAndOwnedTransitionsIsOneUndoableChange() {
        val xml = source()
        val before = xml.text
        val plan = DesignerStateDeletion.prepare(project, xml.virtualFile, group, "Wide")
        assertEquals(1, plan.transitions)
        plan.apply(project)
        assertFalse(xml.text.contains("x:Name=\"Wide\""))
        assertFalse(xml.text.contains("From=\"Wide\""))
        assertTrue(xml.text.contains("To=\"Narrow\""))
        assertTrue(xml.text.contains("Width=\"100\""))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(before, myFixture.editor.document.text)
    }
    fun testExternalReferencesPreventRemoval() {
        val xml = source("""<Binding ElementName="Wide"/>""")
        val before = xml.text
        assertTrue(runCatching { DesignerStateDeletion.prepare(project, xml.virtualFile, group, "Wide") }.exceptionOrNull()?.message.orEmpty().contains("Still referenced"))
        assertEquals(before, xml.text)
        assertTrue(runCatching { DesignerStateDeletion.prepare(project, xml.virtualFile, group, null) }.isFailure)
    }
    fun testChangedProjectInvalidatesDeletionAndLastStateLeavesEditableGroup() {
        val xml = source()
        val plan = DesignerStateDeletion.prepare(project, xml.virtualFile, group, "Wide")
        WriteCommandAction.runWriteCommandAction(project) { xml.rootTag!!.setAttribute("Tag", "changed") }
        assertTrue(runCatching { plan.apply(project) }.isFailure)
        DesignerStateDeletion.prepare(project, xml.virtualFile, group, "Wide").apply(project)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        DesignerStateDeletion.prepare(project, xml.virtualFile, group.copy(states = listOf("Narrow")), "Narrow").apply(project)
        val empty = WinUIXamlDesignStates.groups(xml.text).single()
        assertEquals("Sizes", empty.label)
        assertTrue(empty.states.isEmpty())
        DesignerStateAuthoring.addState(project, xml.virtualFile, empty, "Restored")
        assertTrue(xml.text.contains("x:Name=\"Restored\""))
    }
}
