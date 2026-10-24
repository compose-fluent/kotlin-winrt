package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog
import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds

class DesignerStyleEditingTest : BasePlatformTestCase() {
    // XAMLC IsDependencyProperty and CsWinRT class member ownership: only target
    // type DPs belong in shared Style setters; derived-only properties do not.
    private val catalog = WinUIXamlCatalog(WinRTMetadataModel(listOf(WinRTNamespace("Microsoft.UI.Xaml.Controls", listOf(
        WinRTTypeDefinition("Microsoft.UI.Xaml.Controls", "Button", WinRTTypeKind.RuntimeClass, properties = listOf(
            WinRTPropertyDefinition("Width", "Double", setterMethodName = "put_Width"),
            WinRTPropertyDefinition("WidthProperty", "Microsoft.UI.Xaml.DependencyProperty", isStatic = true),
        )),
    )))).normalized())
    private fun base(setters: String, inline: Boolean = false): DesignerSelection {
        val style = """<Style TargetType="Button" ${if (inline) "" else "x:Key=\"Shared\""}>$setters</Style>"""
        val xml = myFixture.configureByText("Styles.xaml", """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
          ${if (inline) "" else "<Grid.Resources>$style</Grid.Resources>"}
          <Button x:Name="Target" Width="100" ${if (inline) "" else "Style=\"{StaticResource Shared}\""}>${if (inline) "<Button.Style>$style</Button.Style>" else ""}</Button></Grid>""") as XmlFile
        val tag = xml.rootTag!!.subTags.last()
        return DesignerSelection(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(tag), tag.text,
            listOf("Width", "DerivedOnly").map { DesignerProperty(it, "Double", tag.getAttributeValue(it), emptyList(), true, false, true) },
            WinUIXamlVisualBounds(0.0, 0.0, 100.0, 40.0), "Grid", tag.attributes.associate { it.name to it.value.orEmpty() })
    }
    fun testResourceStyleEditsAreUndoableAndPreserveLocalPrecedence() {
        val base = base("""<Setter Property="Width" Value="200"/>""")
        val selection = DesignerStyleEditing.selection(base, catalog)!!
        assertEquals(listOf("Width"), selection.properties.map { it.name })
        assertEquals("200", selection.properties.single().value)
        val before = myFixture.editor.document.text
        WinUIXamlDesignerEditing.apply(project, selection, mapOf("Width" to "300"), "Edit shared style")
        assertEquals("100", base.pointer.element!!.getAttributeValue("Width"))
        assertTrue(myFixture.editor.document.text.contains("Value=\"300\""))
        assertEquals("{StaticResource Shared}", base.pointer.element!!.getAttributeValue("Style"))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(before, myFixture.editor.document.text)
    }
    fun testInlineExplicitSetterCollectionSupportsResetAndAdd() {
        val original = base("""<Style.Setters><Setter Property="Width" Value="200"/></Style.Setters>""", true)
        WinUIXamlDesignerEditing.apply(project, DesignerStyleEditing.selection(original, catalog)!!, mapOf("Width" to null), "Reset style width")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertFalse(myFixture.editor.document.text.contains("Property=\"Width\""))
        val refreshed = original.copy(text = original.pointer.element!!.text)
        WinUIXamlDesignerEditing.apply(project, DesignerStyleEditing.selection(refreshed, catalog)!!, mapOf("Width" to "240"), "Add style width")
        assertTrue(myFixture.editor.document.text.contains("Value=\"240\""))
        assertEquals("100", original.pointer.element!!.getAttributeValue("Width"))
    }
    fun testComplexDuplicateAndStaleStyleEditsAreRejected() {
        listOf("""<Setter Property="Width"><Setter.Value><x:Double>200</x:Double></Setter.Value></Setter>""",
            """<Setter Property="Width" Value="200"/><Setter Property="Width" Value="300"/>""").forEach { setters ->
            val selected = DesignerStyleEditing.selection(base(setters), catalog)!!
            val before = myFixture.editor.document.text
            assertTrue(runCatching { WinUIXamlDesignerEditing.apply(project, selected, mapOf("Width" to "400"), "Invalid style edit") }.isFailure)
            assertEquals(before, myFixture.editor.document.text)
        }
        val selected = DesignerStyleEditing.selection(base(""), catalog)!!
        WriteCommandAction.runWriteCommandAction(project) { selected.style!!.pointer.element!!.setAttribute("BasedOn", "{StaticResource Other}") }
        val before = myFixture.editor.document.text
        assertTrue(runCatching { WinUIXamlDesignerEditing.apply(project, selected, mapOf("Width" to "400"), "Stale style edit") }.isFailure)
        assertEquals(before, myFixture.editor.document.text)
    }
}
