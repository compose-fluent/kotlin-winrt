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
            WinRTPropertyDefinition("Style", "Microsoft.UI.Xaml.Style", setterMethodName = "put_Style"),
            WinRTPropertyDefinition("WidthProperty", "Microsoft.UI.Xaml.DependencyProperty", isStatic = true),
        )),
        WinRTTypeDefinition("Microsoft.UI.Xaml.Controls", "Grid", WinRTTypeKind.RuntimeClass, properties = listOf(
            WinRTPropertyDefinition("Resources", "Microsoft.UI.Xaml.ResourceDictionary"),
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
    fun testCreateExtractStylePreservesExpressionsAndIsOneUndo() {
        val original = base("")
        WriteCommandAction.runWriteCommandAction(project) {
            original.pointer.element!!.setAttribute("Style", null)
            original.pointer.element!!.setAttribute("Height", "{Binding Height}")
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val selected = original.copy(text = original.pointer.element!!.text)
        val before = myFixture.editor.document.text
        DesignerStyleAuthoring.create(project, selected, "Extracted", true, catalog)
        // The atomic owner replacement invalidates old PSI pointers; the
        // designer resolves the selection again from the refreshed visual path.
        val tag = (myFixture.file as XmlFile).rootTag!!.subTags.last()
        assertNull(tag.getAttributeValue("Width"))
        assertEquals("{Binding Height}", tag.getAttributeValue("Height"))
        assertEquals("Target", tag.getAttributeValue("Name", WinUIXamlCatalog.XAML))
        assertEquals("{StaticResource Extracted}", tag.getAttributeValue("Style"))
        assertEquals("100", DesignerStyleEditing.selection(selected.copy(pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(tag), text = tag.text), catalog)!!.properties.single().value)
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(before, myFixture.editor.document.text)
    }
    fun testCreateStyleAddsMissingResourcesAndKeepsLocalValues() {
        val original = base("")
        WriteCommandAction.runWriteCommandAction(project) {
            original.pointer.element!!.setAttribute("Style", null)
            (myFixture.file as XmlFile).rootTag!!.subTags.first().delete()
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val tag = (myFixture.file as XmlFile).rootTag!!.subTags.single()
        val selected = original.copy(pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(tag), text = tag.text)
        val before = myFixture.editor.document.text
        DesignerStyleAuthoring.create(project, selected, "NewStyle", false, catalog)
        val root = (myFixture.file as XmlFile).rootTag!!
        assertEquals("Grid.Resources", root.subTags.first().localName)
        assertEquals("NewStyle", root.subTags.first().subTags.single().getAttributeValue("Key", WinUIXamlCatalog.XAML))
        assertEquals("100", root.subTags.last().getAttributeValue("Width"))
        assertEquals("{StaticResource NewStyle}", root.subTags.last().getAttributeValue("Style"))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(before, myFixture.editor.document.text)
    }
    fun testCreateStyleRejectsDuplicateKeyWithoutPartialEdits() {
        val original = base("")
        WriteCommandAction.runWriteCommandAction(project) { original.pointer.element!!.setAttribute("Style", null) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val selected = original.copy(text = original.pointer.element!!.text)
        val before = myFixture.editor.document.text
        assertTrue(runCatching { DesignerStyleAuthoring.create(project, selected, "Shared", true, catalog) }.isFailure)
        assertEquals(before, myFixture.editor.document.text)
    }
    fun testImplicitStyleUsesNearestSourceScopeAndPreservesNoExplicitReference() {
        val original = base("""<Setter Property="Width" Value="220"/>""")
        WriteCommandAction.runWriteCommandAction(project) {
            original.pointer.element!!.setAttribute("Style", null)
            (myFixture.file as XmlFile).rootTag!!.subTags.first().subTags.single().setAttribute("x:Key", null)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val selected = original.copy(text = original.pointer.element!!.text)
        val style = DesignerStyleEditing.selection(selected, catalog)!!
        assertEquals("Implicit Button style", style.style!!.label)
        assertEquals("220", style.properties.single().value)
        val before = myFixture.editor.document.text
        WinUIXamlDesignerEditing.apply(project, style, mapOf("Width" to "260"), "Edit implicit style")
        assertNull(original.pointer.element!!.getAttribute("Style"))
        assertEquals("100", original.pointer.element!!.getAttributeValue("Width"))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(before, myFixture.editor.document.text)
    }
    fun testImplicitStyleMergedDictionaryOrderAndExplicitNullSuppression() {
        val original = base("")
        WriteCommandAction.runWriteCommandAction(project) {
            original.pointer.element!!.setAttribute("Style", null)
            val resources = (myFixture.file as XmlFile).rootTag!!.subTags.first()
            resources.subTags.single().delete()
            resources.addSubTag(resources.createChildTag("ResourceDictionary", WinUIXamlCatalog.PRESENTATION,
                """<ResourceDictionary.MergedDictionaries><ResourceDictionary><Style TargetType="Button"><Setter Property="Width" Value="210"/></Style></ResourceDictionary><ResourceDictionary><Style TargetType="Button"><Setter Property="Width" Value="310"/></Style></ResourceDictionary></ResourceDictionary.MergedDictionaries>""", false), false)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val selected = original.copy(text = original.pointer.element!!.text)
        assertEquals("310", DesignerStyleEditing.selection(selected, catalog)!!.properties.single().value)
        WriteCommandAction.runWriteCommandAction(project) { original.pointer.element!!.setAttribute("Style", "{x:Null}") }
        assertNull(DesignerStyleEditing.selection(original.copy(text = original.pointer.element!!.text), catalog))
    }

    fun testThemeImplicitStyleEditsOnlySelectedDictionaryAndSupportsUndo() {
        // WinUI resources.md: current, reverse merged, selected theme; Default
        // is a fallback dictionary, not the name of an inferred OS theme.
        val original = base("")
        WriteCommandAction.runWriteCommandAction(project) {
            original.pointer.element!!.setAttribute("Style", null)
            val resources = (myFixture.file as XmlFile).rootTag!!.subTags.first()
            resources.subTags.single().delete()
            resources.addSubTag(resources.createChildTag("ResourceDictionary", WinUIXamlCatalog.PRESENTATION,
                """<ResourceDictionary.ThemeDictionaries><ResourceDictionary x:Key="Light"><Style TargetType="Button"><Setter Property="Width" Value="210"/></Style></ResourceDictionary><ResourceDictionary x:Key="Default"><Style TargetType="Button"><Setter Property="Width" Value="310"/></Style></ResourceDictionary></ResourceDictionary.ThemeDictionaries>""", false), false)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val selected = original.copy(text = original.pointer.element!!.text)
        assertNull(DesignerStyleEditing.selection(selected, catalog))
        assertEquals("310", DesignerStyleEditing.selection(selected, catalog, "Dark")!!.properties.single().value)
        val light = DesignerStyleEditing.selection(selected, catalog, "Light")!!
        assertEquals("210", light.properties.single().value)
        val before = myFixture.editor.document.text
        WinUIXamlDesignerEditing.apply(project, light, mapOf("Width" to "240"), "Edit light style")
        assertEquals("310", DesignerStyleEditing.selection(selected, catalog, "Dark")!!.properties.single().value)
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(before, myFixture.editor.document.text)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        WriteCommandAction.runWriteCommandAction(project) { original.pointer.element!!.setAttribute("RequestedTheme", "Dark") }
        assertNull(DesignerStyleEditing.selection(selected, catalog, "Light"))
        WriteCommandAction.runWriteCommandAction(project) {
            val theme = (myFixture.file as XmlFile).rootTag!!.subTags.first().subTags.single().subTags.single()
            theme.subTags.first().setAttribute("x:Key", "HighContrast")
        }
        assertNull(DesignerStyleEditing.selection(selected, catalog, "Light"))
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
