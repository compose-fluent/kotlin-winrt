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
}
