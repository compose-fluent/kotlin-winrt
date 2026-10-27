@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds

class DesignerObjectEditingTest : BasePlatformTestCase() {
    private fun selection(): DesignerSelection {
        val file = myFixture.configureByText("Object.xaml", """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Border Tag="preserve"><Border.Background><LinearGradientBrush StartPoint="0,0" EndPoint="1,1"><GradientStop Color="Red" Offset="0"/><GradientStop Color="Blue" Offset="1"/></LinearGradientBrush></Border.Background><TextBlock Text="Child"/></Border></Grid>""") as XmlFile
        val tag = file.rootTag!!.subTags.single()
        return DesignerSelection(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(tag), tag.text,
            listOf(DesignerProperty("Background", "Microsoft.UI.Xaml.Media.Brush", null, emptyList(), true, true)),
            WinUIXamlVisualBounds(0.0, 0.0, 100.0, 100.0), "Grid", emptyMap())
    }
    fun testObjectReplacementPreservesSiblingsAndUndoRestoresNestedCollection() {
        val selected = selection()
        val property = selected.properties.single()
        val before = myFixture.editor.document.text
        val value = DesignerObjectEditing.inspect(selected, property)
        assertEquals(3, value.nodes.size)
        DesignerObjectEditing.replace(selected, property, """<SolidColorBrush Color="Green"/>""")
        assertTrue(myFixture.editor.document.text.contains("Color=\"Green\""))
        assertTrue(myFixture.editor.document.text.contains("Tag=\"preserve\""))
        assertTrue(myFixture.editor.document.text.contains("Text=\"Child\""))
        assertFalse(myFixture.editor.document.text.contains("LinearGradientBrush"))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(before, myFixture.editor.document.text)
    }
    fun testInvalidAndStaleObjectChangesAreRejectedWithoutPartialWrites() {
        val selected = selection()
        val property = selected.properties.single()
        val before = myFixture.editor.document.text
        for (text in listOf("<SolidColorBrush>", "<SolidColorBrush/><", "<!DOCTYPE x><SolidColorBrush/>")) {
            assertTrue(runCatching { DesignerObjectEditing.replace(selected, property, text) }.isFailure)
            assertEquals(before, myFixture.editor.document.text)
        }
        DesignerObjectEditing.replace(selected, property, "<SolidColorBrush Color=\"Red\"/>")
        val changed = myFixture.editor.document.text
        assertTrue(runCatching { DesignerObjectEditing.replace(selected, property, null) }.isFailure)
        assertEquals(changed, myFixture.editor.document.text)
    }
}
