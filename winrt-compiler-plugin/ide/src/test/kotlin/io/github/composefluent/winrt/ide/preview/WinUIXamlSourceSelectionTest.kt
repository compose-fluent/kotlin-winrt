package io.github.composefluent.winrt.ide.preview

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds
import io.github.composefluent.winrt.runtime.WinUIXamlVisualNode

/** IDE PSI/visual-tree navigation; no new runtime projection or template contract. */
class WinUIXamlSourceSelectionTest : BasePlatformTestCase() {
    private fun node(path: List<Int>, type: String, name: String = "") =
        WinUIXamlVisualNode(path, "Windows.UI.Xaml.Controls.$type", name, WinUIXamlVisualBounds(0.0, 0.0, 100.0, 40.0))
    private fun xml(body: String) = myFixture.configureByText("Selection.xaml",
        """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">$body</Grid>""") as XmlFile

    fun testNamedControlsResolveBothDirectionsAmongRepeatedTypes() {
        val file = xml("""<Button x:Name="First"/><Button x:Name="Second"/>""")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "Button", "First"), node(listOf(1), "Button", "Second"))
        val tag = file.rootTag!!.subTags[1]
        assertEquals(nodes[2], WinUIXamlSourceSelection.node(tag, nodes))
        assertSame(tag, WinUIXamlSourceSelection.tag(file, nodes[2], nodes, emptyList()))
    }

    fun testUnnamedRepeatedControlsDoNotGuessButNamedParentNarrowsSelection() {
        val file = xml("""<StackPanel x:Name="Left"><Button/></StackPanel><StackPanel x:Name="Right"><Button/></StackPanel>""")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "StackPanel", "Left"),
            node(listOf(0, 0), "Button"), node(listOf(1), "StackPanel", "Right"), node(listOf(1, 0), "Button"))
        assertEquals(nodes[4], WinUIXamlSourceSelection.node(file.rootTag!!.subTags[1].subTags[0], nodes))
        val ambiguous = xml("<Button/><Button/>")
        assertNull(WinUIXamlSourceSelection.node(ambiguous.rootTag!!.subTags[0], nodes))
    }

    fun testButtonTemplateTextNavigatesToButtonInsteadOfUnrelatedPageText() {
        val file = xml("<TextBlock Text=\"Title\"/><Button Content=\"Action\"/>")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "TextBlock"),
            node(listOf(1), "Button"), node(listOf(1, 0), "ContentPresenter"),
            node(listOf(1, 0, 0), "TextBlock"))
        assertEquals(nodes[1], WinUIXamlSourceSelection.node(file.rootTag!!.subTags[0], nodes))
        assertSame(file.rootTag!!.subTags[0], WinUIXamlSourceSelection.tag(file, nodes[1], nodes, emptyList()))
        assertSame(file.rootTag!!.subTags[1],
            WinUIXamlSourceSelection.tag(file, nodes.last(), nodes, emptyList()))
    }

    fun testTemplateNamesDoNotHijackPageSelection() {
        val file = xml("""<Grid.Resources><ControlTemplate x:Key="Template"><Button x:Name="Inside"/></ControlTemplate></Grid.Resources><Button x:Name="Actual"/>""")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "Button", "Inside"), node(listOf(1), "Button", "Actual"))
        val inside = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).first { it.getAttributeValue("x:Name") == "Inside" }
        assertNull(WinUIXamlSourceSelection.node(inside, nodes))
        assertSame(file.rootTag, WinUIXamlSourceSelection.tag(file, nodes[1], nodes, emptyList()))
    }

    fun testOrderedUnnamedSiblingsResolveOnlyWhenWholeVisualSequenceMatches() {
        val file = xml("<Grid.Children><Button/><Button/><TextBlock/></Grid.Children>")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "Button"), node(listOf(1), "Button"), node(listOf(2), "TextBlock"))
        val tags = file.rootTag!!.subTags.single().subTags
        assertEquals(nodes[1], WinUIXamlSourceSelection.node(tags[0], nodes))
        assertEquals(nodes[2], WinUIXamlSourceSelection.node(tags[1], nodes))
        assertNull(WinUIXamlSourceSelection.node(tags[1], nodes + node(listOf(3), "Button")))
    }
    fun testDependencyInstanceScopesRepeatedNamesAndResolvesAuthoredRoot() {
        val file = myFixture.configureByText("GreetingControl.xaml", """<UserControl
            xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
            xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="dependency.GreetingControl">
            <TextBlock x:Name="Caption"/>
        </UserControl>""") as XmlFile
        val first = node(listOf(0), "UserControl").copy(typeName = "dependency.GreetingControl")
        val second = first.copy(path = listOf(1))
        val nodes = listOf(node(emptyList(), "Grid"), first, node(listOf(0, 0), "TextBlock", "Caption"),
            second, node(listOf(1, 0), "TextBlock", "Caption"))
        assertSame(file.rootTag, WinUIXamlSourceSelection.tag(file, second, nodes, listOf(1)))
        assertSame(file.rootTag!!.subTags.single(), WinUIXamlSourceSelection.tag(file, nodes.last(), nodes, listOf(1)))
        assertNull(WinUIXamlSourceSelection.tag(file, nodes[2], nodes, listOf(1)))
    }

    fun testSameShortNameInDifferentDependenciesDoesNotSelectTheWrongControl() {
        val file = xml("""<a:Card xmlns:a="using:one.controls"/><b:Card xmlns:b="using:two.controls"/>""")
        val first = node(listOf(0), "Card").copy(typeName = "one.controls.Card")
        val second = first.copy(path = listOf(1), typeName = "two.controls.Card")
        val nodes = listOf(node(emptyList(), "Grid"), first, second)
        assertEquals(first, WinUIXamlSourceSelection.node(file.rootTag!!.subTags[0], nodes))
        assertEquals(second, WinUIXamlSourceSelection.node(file.rootTag!!.subTags[1], nodes))
        assertNull(WinUIXamlSourceSelection.node(file.rootTag!!.subTags[0], listOf(nodes[0], second)))
    }

    fun testInlineControlTemplateNamesAreScopedToTheirControlInstance() {
        val file = xml("""<Button x:Name="First"><Button.Template><ControlTemplate TargetType="Button">
            <TextBlock x:Name="Caption"/>
        </ControlTemplate></Button.Template></Button>
        <Button x:Name="Second"><Button.Template><ControlTemplate TargetType="Button">
            <TextBlock x:Name="Caption"/>
        </ControlTemplate></Button.Template></Button>""")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "Button", "First"),
            node(listOf(0, 0), "TextBlock", "Caption"), node(listOf(1), "Button", "Second"),
            node(listOf(1, 0), "TextBlock", "Caption"))
        val captions = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).filter { it.localName == "TextBlock" }
        assertEquals(nodes[2], WinUIXamlSourceSelection.node(captions[0], nodes))
        assertEquals(nodes[4], WinUIXamlSourceSelection.node(captions[1], nodes))
        assertSame(captions[1], WinUIXamlSourceSelection.tag(file, nodes[4], nodes, emptyList()))
        assertNull(WinUIXamlSourceSelection.node(captions[1], nodes + nodes[4].copy(path = listOf(1, 1))))
    }

    fun testSharedControlTemplateSelectsTheClickedInstanceWithoutGuessing() {
        val file = xml("""<Grid.Resources><ControlTemplate x:Key="Shared" TargetType="Button"><StackPanel><TextBlock x:Name="Caption"/><TextBlock Text="Second"/></StackPanel></ControlTemplate></Grid.Resources>
            <Button x:Name="First" Template="{StaticResource Shared}"/><Button x:Name="Second" Template="{StaticResource Shared}"/>""")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "Button", "First"),
            node(listOf(0, 0), "StackPanel"), node(listOf(0, 0, 0), "TextBlock", "Caption"), node(listOf(0, 0, 1), "TextBlock"),
            node(listOf(1), "Button", "Second"), node(listOf(1, 0), "StackPanel"),
            node(listOf(1, 0, 0), "TextBlock", "Caption"), node(listOf(1, 0, 1), "TextBlock"))
        val parts = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).filter { it.localName == "TextBlock" }
        assertNull("Caret navigation cannot choose between shared instances", WinUIXamlSourceSelection.node(parts[0], nodes))
        assertSame(parts[0], WinUIXamlSourceSelection.tag(file, nodes[3], nodes, emptyList()))
        assertSame(parts[0], WinUIXamlSourceSelection.tag(file, nodes[7], nodes, emptyList()))
        assertSame(parts[1], WinUIXamlSourceSelection.tag(file, nodes[8], nodes, emptyList()))
        assertNull(WinUIXamlSourceSelection.node(parts[0], nodes, listOf(2), file))
    }

    fun testExternalSharedTemplateEditWritesItsDictionaryAndUndoRestoresIt() {
        val dictionary = myFixture.addFileToProject("Shared.xaml", """<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><ControlTemplate x:Key="Shared" TargetType="Button"><TextBlock x:Name="Caption" Width="100"/></ControlTemplate></ResourceDictionary>""") as XmlFile
        val file = myFixture.addFileToProject("Consumer.xaml", """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Grid.Resources><ResourceDictionary><ResourceDictionary.MergedDictionaries><ResourceDictionary Source="Shared.xaml"/></ResourceDictionary.MergedDictionaries></ResourceDictionary></Grid.Resources><Button x:Name="First" Template="{StaticResource Shared}"/><Button x:Name="Second" Template="{StaticResource Shared}"/></Grid>""") as XmlFile
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "Button", "First"), node(listOf(0, 0), "TextBlock", "Caption"), node(listOf(1), "Button", "Second"), node(listOf(1, 0), "TextBlock", "Caption"))
        val part = PsiTreeUtil.findChildrenOfType(dictionary, XmlTag::class.java).single { it.localName == "TextBlock" }
        assertSame(part, WinUIXamlSourceSelection.tag(file, nodes.last(), nodes, emptyList()))
        val target = DesignerSelection(com.intellij.psi.SmartPointerManager.getInstance(project).createSmartPsiElementPointer(part), part.text,
            listOf(DesignerProperty("Width", "Double", "100", emptyList(), true, false)), nodes.last().bounds, "ControlTemplate", mapOf("Width" to "100"))
        myFixture.openFileInEditor(dictionary.virtualFile)
        val before = dictionary.text
        val consumerBefore = file.text
        assertTrue(runCatching { WinUIXamlDesignerEditing.applyBatch(project, listOf(target to mapOf("Width" to "120"), target to mapOf("Width" to "140")), "Edit shared instances") }.isFailure)
        assertEquals(before, com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(dictionary)!!.text)
        WinUIXamlDesignerEditing.apply(project, target, mapOf("Width" to "160"), "Edit shared template")
        assertEquals("160", part.getAttributeValue("Width"))
        assertEquals(consumerBefore, file.text)
        myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_UNDO)
        assertEquals(before, com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(dictionary)!!.text)
    }
    fun testStyleTemplateUsesDerivedSetterBeforeBasedOnAndLocalTemplateBeforeStyle() {
        val file = xml("""<Grid.Resources>
            <ControlTemplate x:Key="BaseTemplate"><TextBlock x:Name="BasePart"/></ControlTemplate>
            <Style x:Key="Base" TargetType="Button"><Setter Property="Template" Value="{StaticResource BaseTemplate}"/></Style>
            <Style x:Key="Derived" TargetType="Button" BasedOn="{StaticResource Base}"><Style.Setters><Setter Property="Template"><Setter.Value><ControlTemplate><TextBlock x:Name="DerivedPart"/></ControlTemplate></Setter.Value></Setter></Style.Setters></Style>
            <Style x:Key="Inherited" TargetType="Button" BasedOn="{StaticResource Base}"/>
            </Grid.Resources>
            <Button x:Name="One" Style="{StaticResource Derived}"/>
            <Button x:Name="Two" Style="{StaticResource Inherited}"/>
            <Button x:Name="Three" Style="{StaticResource Derived}" Template="{StaticResource BaseTemplate}"/>""")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "Button", "One"), node(listOf(0, 0), "TextBlock", "DerivedPart"),
            node(listOf(1), "Button", "Two"), node(listOf(1, 0), "TextBlock", "BasePart"), node(listOf(2), "Button", "Three"), node(listOf(2, 0), "TextBlock", "BasePart"))
        val tags = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).filter { it.localName == "TextBlock" }
        assertSame(tags[1], WinUIXamlSourceSelection.tag(file, nodes[2], nodes, emptyList()))
        assertSame(tags[0], WinUIXamlSourceSelection.tag(file, nodes[4], nodes, emptyList()))
        assertSame(tags[0], WinUIXamlSourceSelection.tag(file, nodes[6], nodes, emptyList()))
    }

    fun testCyclicStyleAndUnresolvedLocalTemplateDoNotGuessStyleTemplate() {
        val file = xml("""<Grid.Resources><Style x:Key="Cycle" TargetType="Button" BasedOn="{StaticResource Cycle}"/>
            <Style x:Key="HasTemplate" TargetType="Button"><Setter Property="Template"><Setter.Value><ControlTemplate><TextBlock x:Name="Part"/></ControlTemplate></Setter.Value></Setter></Style></Grid.Resources>
            <Button x:Name="One" Style="{StaticResource Cycle}"/><Button x:Name="Two" Style="{StaticResource HasTemplate}" Template="{Binding Template}"/>""")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "Button", "One"), node(listOf(0, 0), "TextBlock", "Part"), node(listOf(1), "Button", "Two"), node(listOf(1, 0), "TextBlock", "Part"))
        val part = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).single { it.localName == "TextBlock" }
        assertNull(WinUIXamlSourceSelection.node(part, nodes, nodes[2].path, file))
        assertNull(WinUIXamlSourceSelection.node(part, nodes, nodes[4].path, file))
    }
    fun testImplicitTemplateUsesSelectedApplicationThemeAndLocalScope() {
        val catalog = io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog(io.github.composefluent.winrt.metadata.WinRTMetadataModel(listOf(
            io.github.composefluent.winrt.metadata.WinRTNamespace("Microsoft.UI.Xaml.Controls", listOf(
                io.github.composefluent.winrt.metadata.WinRTTypeDefinition("Microsoft.UI.Xaml.Controls", "Button", io.github.composefluent.winrt.metadata.WinRTTypeKind.RuntimeClass)
            )))).normalized())
        val application = myFixture.addFileToProject("App.xaml", """<Application xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Application.Resources><ResourceDictionary><ResourceDictionary.ThemeDictionaries>
            <ResourceDictionary x:Key="Light"><Style TargetType="Button"><Setter Property="Template"><Setter.Value><ControlTemplate><TextBlock x:Name="LightPart"/></ControlTemplate></Setter.Value></Setter></Style></ResourceDictionary>
            <ResourceDictionary x:Key="Dark"><Style TargetType="Button"><Setter Property="Template"><Setter.Value><ControlTemplate><TextBlock x:Name="DarkPart"/></ControlTemplate></Setter.Value></Setter></Style></ResourceDictionary>
            </ResourceDictionary.ThemeDictionaries></ResourceDictionary></Application.Resources></Application>""") as XmlFile
        val file = xml("""<Button x:Name="One"/><StackPanel><StackPanel.Resources><Style TargetType="Button"><Setter Property="Template"><Setter.Value><ControlTemplate><TextBlock x:Name="LocalPart"/></ControlTemplate></Setter.Value></Setter></Style></StackPanel.Resources><Button x:Name="Two"/></StackPanel>""")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "Button", "One"), node(listOf(0, 0), "TextBlock", "LightPart"),
            node(listOf(1), "StackPanel"), node(listOf(1, 0), "Button", "Two"), node(listOf(1, 0, 0), "TextBlock", "LocalPart"))
        val context = WinUIXamlSourceSelection.Context(catalog, "Light", application)
        val parts = PsiTreeUtil.findChildrenOfType(application, XmlTag::class.java).filter { it.localName == "TextBlock" }
        assertSame(parts[0], WinUIXamlSourceSelection.tag(file, nodes[2], nodes, emptyList(), context))
        val darkNodes = nodes.map { if (it == nodes[2]) it.copy(name = "DarkPart") else it }
        assertSame(parts[1], WinUIXamlSourceSelection.tag(file, darkNodes[2], darkNodes, emptyList(), context.copy(theme = "Dark")))
        val local = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).single { it.localName == "TextBlock" }
        assertSame(local, WinUIXamlSourceSelection.tag(file, nodes.last(), nodes, emptyList(), context))
        assertFalse(WinUIXamlSourceSelection.sourceTags(file, context.copy(application = null)).contains(parts[0]))
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            file.rootTag!!.subTags.first().setAttribute("Style", "{x:Null}")
        }
        assertFalse("A changed local style must invalidate memoized implicit-template ownership",
            WinUIXamlSourceSelection.sourceTags(file, context).contains(parts[0]))
    }
    fun testSharedTemplateRespectsResourceShadowing() {
        val file = xml("""<Grid.Resources><ControlTemplate x:Key="Shared" TargetType="Button"><TextBlock x:Name="Outer"/></ControlTemplate></Grid.Resources>
            <StackPanel><StackPanel.Resources><ControlTemplate x:Key="Shared" TargetType="Button"><TextBlock x:Name="Inner"/></ControlTemplate></StackPanel.Resources>
                <Button x:Name="Consumer" Template="{StaticResource Shared}"/>
            </StackPanel>""")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "StackPanel"),
            node(listOf(0, 0), "Button", "Consumer"), node(listOf(0, 0, 0), "TextBlock", "Inner"))
        val parts = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).filter { it.localName == "TextBlock" }
        assertNull(WinUIXamlSourceSelection.node(parts[0], nodes, nodes.last().path, file))
        assertSame(parts[1], WinUIXamlSourceSelection.tag(file, nodes.last(), nodes, emptyList()))
    }

    fun testUnnamedInlineTemplatePartsRequireExactRealizedSiblingSequence() {
        val file = xml("""<Button x:Name="First"><Button.Template><ControlTemplate TargetType="Button"><StackPanel><TextBlock Text="One"/><TextBlock Text="Two"/></StackPanel></ControlTemplate></Button.Template></Button>""")
        val nodes = listOf(node(emptyList(), "Grid"), node(listOf(0), "Button", "First"),
            node(listOf(0, 0), "StackPanel"), node(listOf(0, 0, 0), "TextBlock"), node(listOf(0, 0, 1), "TextBlock"))
        val parts = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).filter { it.localName == "TextBlock" }
        assertEquals(nodes[3], WinUIXamlSourceSelection.node(parts[0], nodes))
        assertEquals(nodes[4], WinUIXamlSourceSelection.node(parts[1], nodes))
        assertSame(parts[1], WinUIXamlSourceSelection.tag(file, nodes[4], nodes, emptyList()))
        assertNull(WinUIXamlSourceSelection.node(parts[1], nodes + node(listOf(0, 0, 2), "TextBlock")))
        assertNull(WinUIXamlSourceSelection.node(parts[1], nodes.map { if (it.path == listOf(0, 0)) it.copy(typeName = "Windows.UI.Xaml.Controls.ContentPresenter") else it }))
    }

}
