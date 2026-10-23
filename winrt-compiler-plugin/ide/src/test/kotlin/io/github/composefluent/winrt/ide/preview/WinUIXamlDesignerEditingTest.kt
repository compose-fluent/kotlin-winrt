package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds

/** IDE source-authoring contract: metadata owns setters; PSI owns undo and source
 * expressions. No runtime or generated projection policy is introduced here. */
class WinUIXamlDesignerEditingTest : BasePlatformTestCase() {
    private fun selection(attributes: String = "", parent: String = "Grid"): DesignerSelection {
        val xml = myFixture.configureByText("Designer.xaml", "<$parent xmlns=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\"><Button $attributes/></$parent>") as XmlFile
        val tag = xml.rootTag!!.subTags.single()
        val properties = listOf("Width", "Height", "Canvas.Left", "Canvas.Top").map {
            DesignerProperty(it, "Double", tag.getAttributeValue(it), emptyList(), true, false)
        } + listOf(DesignerProperty("Margin", "Thickness", tag.getAttributeValue("Margin"), emptyList(), true, false),
            DesignerProperty("HorizontalAlignment", "HorizontalAlignment", tag.getAttributeValue("HorizontalAlignment"), listOf("Left", "Right", "Center", "Stretch"), true, false),
            DesignerProperty("VerticalAlignment", "VerticalAlignment", tag.getAttributeValue("VerticalAlignment"), listOf("Top", "Bottom", "Center", "Stretch"), true, false))
        return DesignerSelection(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(tag), tag.text, properties,
            WinUIXamlVisualBounds(20.0, 30.0, 100.0, 40.0), parent, tag.attributes.associate { it.name to it.value.orEmpty() })
    }
    fun testOneGestureIsOneUndoableSourceEdit() {
        val target = selection()
        WinUIXamlDesignerEditing.apply(project, target, mapOf("Width" to "140", "Height" to "60"), "Resize XAML element")
        assertTrue(myFixture.editor.document.text.contains("Width=\"140\""))
        assertTrue(myFixture.editor.document.text.contains("Height=\"60\""))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertFalse(myFixture.editor.document.text.contains("Width="))
        assertFalse(myFixture.editor.document.text.contains("Height="))
        myFixture.performEditorAction(IdeActions.ACTION_REDO)
        assertTrue(myFixture.editor.document.text.contains("Width=\"140\""))
    }
    fun testStaleSourceAndReadOnlyPropertiesCannotBeOverwritten() {
        val target = selection("Width=\"100\"")
        WriteCommandAction.runWriteCommandAction(project) { target.pointer.element!!.setAttribute("Width", "200") }
        assertNotNull(runCatching { WinUIXamlDesignerEditing.apply(project, target, mapOf("Width" to "300"), "Set width") }.exceptionOrNull())
        assertEquals("200", target.pointer.element!!.getAttributeValue("Width"))
        val current = selection().let { it.copy(properties = it.properties.map { p -> p.copy(writable = false) }) }
        assertNotNull(runCatching { WinUIXamlDesignerEditing.apply(project, current, mapOf("Width" to "300"), "Set width") }.exceptionOrNull())
        assertNull(current.pointer.element!!.getAttributeValue("Width"))
    }
    fun testCanvasMoveUsesOffsetsAndGridMovePreservesStretchSize() {
        assertEquals(mapOf("Canvas.Left" to "25", "Canvas.Top" to "28"),
            WinUIXamlDesignerEditing.layout(selection("Canvas.Left=\"20\" Canvas.Top=\"30\"", "Canvas"), DesignerTransform(5.0, -2.0)))
        assertEquals("15,18,25,42", WinUIXamlDesignerEditing.layout(selection("Margin=\"10,20,30,40\""), DesignerTransform(5.0, -2.0))["Margin"])
    }
    fun testResizeHonorsRightBottomAlignmentAndCornerConstraints() {
        val target = selection("Margin=\"10,20,30,40\" HorizontalAlignment=\"Right\" VerticalAlignment=\"Bottom\"")
        val resized = WinUIXamlDesignerEditing.layout(target, DesignerTransform(0.0, 0.0, 20.0, 10.0, true))
        assertEquals("120", resized["Width"]); assertEquals("50", resized["Height"])
        assertEquals("10,20,10,30", resized["Margin"])
        val clamped = designerTransform(0 to 0, 300.0, 300.0, target.bounds)
        assertEquals(99.0, clamped.x); assertEquals(39.0, clamped.y)
        assertEquals(-99.0, clamped.width); assertEquals(-39.0, clamped.height)
    }
    fun testBindingsAndPropertyElementsArePreservedByLayoutEditing() {
        val bound = selection("Margin=\"{Binding Position}\"")
        assertNotNull(runCatching { WinUIXamlDesignerEditing.layout(bound, DesignerTransform(1.0, 1.0)) }.exceptionOrNull())
        assertEquals("{Binding Position}", bound.pointer.element!!.getAttributeValue("Margin"))
        val element = selection().let { it.copy(properties = it.properties.map { p -> if (p.name == "Width") p.copy(elementValue = true) else p }) }
        assertNotNull(runCatching { WinUIXamlDesignerEditing.apply(project, element, mapOf("Width" to "10"), "Set width") }.exceptionOrNull())
        assertNull(element.pointer.element!!.getAttributeValue("Width"))
    }
    fun testPropertyValidationAndResetKeepXamlExpressions() {
        val target = selection("Width=\"{Binding PreferredWidth}\"")
        val width = target.properties.first { it.name == "Width" }
        assertNull(WinUIXamlDesignerEditing.validate(width, "Auto"))
        assertNull(WinUIXamlDesignerEditing.validate(width, "{Binding Width}"))
        assertNotNull(WinUIXamlDesignerEditing.validate(width, "-5"))
        assertNotNull(WinUIXamlDesignerEditing.validate(width, "NaN"))
        WinUIXamlDesignerEditing.apply(project, target, mapOf("Width" to null), "Reset Width")
        assertNull(target.pointer.element!!.getAttributeValue("Width"))
    }

    fun testBatchValidationIsAtomicAndBatchUndoRestoresEveryElement() {
        val first = selection("Width=\"100\"")
        val tag = first.pointer.element!!
        val secondTag = WriteCommandAction.writeCommandAction(project).compute<com.intellij.psi.xml.XmlTag, RuntimeException> { tag.parentTag!!.addSubTag(tag.copy() as com.intellij.psi.xml.XmlTag, false) }
        val second = first.copy(pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(secondTag), text = secondTag.text)
        val invalid = second.copy(properties = second.properties.map { it.copy(writable = false) })
        assertNotNull(runCatching { WinUIXamlDesignerEditing.applyBatch(project,
            listOf(first to mapOf("Width" to "200"), invalid to mapOf("Width" to "300")), "Batch width") }.exceptionOrNull())
        assertEquals("100", tag.getAttributeValue("Width"))
        WinUIXamlDesignerEditing.applyBatch(project, listOf(first to mapOf("Width" to "200"), second to mapOf("Width" to "300")), "Batch width")
        assertEquals("200", tag.getAttributeValue("Width")); assertEquals("300", secondTag.getAttributeValue("Width"))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(2, Regex("Width=\"100\"").findAll(myFixture.editor.document.text).count())
    }

    fun testAlignmentUsesLastSelectionAndRejectsFlowContainers() {
        val first = selection("Canvas.Left=\"20\" Canvas.Top=\"30\"", "Canvas")
        val secondTag = WriteCommandAction.writeCommandAction(project).compute<com.intellij.psi.xml.XmlTag, RuntimeException> { first.pointer.element!!.parentTag!!.addSubTag(first.pointer.element!!.copy() as com.intellij.psi.xml.XmlTag, false) }
        val second = first.copy(pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(secondTag),
            bounds = WinUIXamlVisualBounds(80.0, 90.0, 100.0, 40.0))
        val edits = designerAlign(listOf(first, second), DesignerAlignment.LEFT)
        assertEquals("80", edits.single().second["Canvas.Left"])
        assertEquals("30", edits.single().second["Canvas.Top"])
        val flow = selection(parent = "StackPanel")
        assertNotNull(runCatching { designerAlign(listOf(flow, flow), DesignerAlignment.TOP) }.exceptionOrNull())
    }

    fun testSnapUsesDipEdgesAndCentersAndKeepsSmallResizeValid() {
        val bounds = WinUIXamlVisualBounds(10.0, 20.0, 100.0, 40.0)
        val neighbor = WinUIXamlVisualBounds(150.0, 80.0, 100.0, 40.0)
        val snap = designerSnap(bounds, DesignerTransform(37.0, 17.0), listOf(neighbor), 4.0)
        assertEquals(40.0, snap.change.x, 0.001)
        assertEquals(20.0, snap.change.y, 0.001)
        assertEquals(150.0, snap.vertical!!, 0.001)
        assertEquals(80.0, snap.horizontal!!, 0.001)
        val far = designerSnap(bounds, DesignerTransform(1.0, 1.0), listOf(neighbor), 2.0)
        assertNull(far.vertical); assertNull(far.horizontal)
        val resize = designerSnap(bounds, DesignerTransform(0.0, 0.0, 38.0, 0.0, true), listOf(neighbor), 3.0)
        assertEquals(40.0, resize.change.width, 0.001)
        assertEquals(0.0, resize.change.height, 0.001)
    }

    fun testResourceExpressionsAndNestedPropertyElementsAreNotFlattenedByBatchEdit() {
        val target = selection("Width=\"{StaticResource PreferredWidth}\" Margin=\"{Binding Position}\"")
        assertNotNull(runCatching { WinUIXamlDesignerEditing.layout(target, DesignerTransform(0.0, 0.0, 20.0, 10.0, true)) }.exceptionOrNull())
        assertTrue(target.pointer.element!!.text.contains("{StaticResource PreferredWidth}"))
        assertTrue(target.pointer.element!!.text.contains("{Binding Position}"))
    }
}
