package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DesignerStoryboardEditingTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl()
    // SDK keyframe source contract; no custom interpolation or runtime projection.
    private fun source(duplicate: Boolean = false): XmlFile {
        val storyboard = """<Storyboard x:Key="Fade"><DoubleAnimationUsingKeyFrames Storyboard.TargetName="Greeting" Storyboard.TargetProperty="Opacity"><SplineDoubleKeyFrame KeyTime="0:0:1" Value="0.4" KeySpline="0,0 1,1"/></DoubleAnimationUsingKeyFrames></Storyboard>"""
        return myFixture.configureByText("Motion.xaml", """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Grid.Resources>$storyboard${if (duplicate) storyboard else ""}</Grid.Resources><TextBlock x:Name="Greeting" Opacity="0.8"/></Grid>""") as XmlFile
    }
    fun testEditingPreservesSplineAndBaseValueAndSupportsUndo() {
        val xml = source()
        val before = xml.text
        val track = DesignerStoryboardEditing.tracks(xml, "Fade").single()
        DesignerStoryboardEditing.write(project, track, track.frames.single(), 2000, "0.6", "Linear")
        assertTrue(xml.text.contains("KeyTime=\"0:0:2.000\""))
        assertTrue(xml.text.contains("KeySpline=\"0,0 1,1\""))
        assertTrue(xml.text.contains("Opacity=\"0.8\""))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(before, xml.text)
    }
    fun testAddDeleteAndStaleEditsAreAtomic() {
        val xml = source()
        val initial = DesignerStoryboardEditing.tracks(xml, "Fade").single()
        DesignerStoryboardEditing.write(project, initial, null, 3000, "0.1", "Linear")
        val after = xml.text
        assertTrue(after.contains("LinearDoubleKeyFrame"))
        assertTrue(runCatching { DesignerStoryboardEditing.write(project, initial, null, 4000, "0.2", "Linear") }.isFailure)
        assertEquals(after, xml.text)
        val updated = DesignerStoryboardEditing.tracks(xml, "Fade").single()
        DesignerStoryboardEditing.write(project, updated, updated.frames.last(), 3000, "", "Linear", true)
        assertEquals(1, DesignerStoryboardEditing.tracks(xml, "Fade").single().frames.size)
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(after, xml.text)
    }
    fun testCreateTrackUsesSourceTargetAndUndoPreservesExistingResources() {
        val xml = source()
        val target = xml.rootTag!!.subTags.last()
        val selection = DesignerSelection(com.intellij.psi.SmartPointerManager.getInstance(project).createSmartPsiElementPointer(target), target.text,
            listOf(DesignerProperty("Opacity", "Double", "0.8", emptyList(), true, false, true)),
            io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds(0.0, 0.0, 10.0, 10.0), "Grid", emptyMap())
        val before = xml.text
        DesignerStoryboardEditing.createTrack(project, selection, "NewMotion", "Opacity", 1000, "0.3", false)
        assertEquals("Greeting · Opacity", DesignerStoryboardEditing.tracks(xml, "NewMotion").single().label)
        assertTrue(xml.text.contains("KeyTime=\"0:0:1.000\""))
        assertTrue(xml.text.contains("KeySpline=\"0,0 1,1\""))
        val after = xml.text
        assertTrue(runCatching { DesignerStoryboardEditing.createTrack(project, selection, "NewMotion", "Opacity", 2000, "0.5", false) }.isFailure)
        assertEquals(after, xml.text)
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(before, xml.text)
    }
    fun testFrameTimeEditingIsLosslessAndBounded() {
        assertEquals(2001, DesignerStoryboardEditing.milliseconds("0:00:02.0010000"))
        assertEquals(86400000, DesignerStoryboardEditing.milliseconds("1.00:00:00"))
        assertEquals(3600000, DesignerStoryboardEditing.milliseconds("1:0:0"))
        for (value in listOf("Uniform", "Paced", "0:0:0.0001", "25:00:00", "0:60:00", "99999999999999999999:00:00"))
            assertNull(value, DesignerStoryboardEditing.milliseconds(value))
        for (millis in listOf(0, 1, 12345, 3600123, 86400000))
            assertEquals(millis, DesignerStoryboardEditing.milliseconds(DesignerStoryboardEditing.time(millis)))
    }
    fun testForeignFrameCannotModifyAnotherTrack() {
        val xml = source()
        val target = xml.rootTag!!.subTags.last()
        val selection = DesignerSelection(com.intellij.psi.SmartPointerManager.getInstance(project).createSmartPsiElementPointer(target), target.text,
            listOf(DesignerProperty("Opacity", "Double", "0.8", emptyList(), true, false, true)),
            io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds(0.0, 0.0, 10.0, 10.0), "Grid", emptyMap())
        DesignerStoryboardEditing.createTrack(project, selection, "Other", "Opacity", 1000, "0.2", false)
        val track = DesignerStoryboardEditing.tracks(xml, "Fade").single()
        val foreign = DesignerStoryboardEditing.tracks(xml, "Other").single().frames.single()
        val before = xml.text
        for (remove in listOf(false, true)) {
            val result = runCatching { DesignerStoryboardEditing.write(project, track, foreign, 2000, "0.6", "Linear", remove) }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("belongs to this animation"))
            assertEquals(before, xml.text)
        }
    }
    fun testDuplicateResourceIdentityIsNotGuessed() {
        assertTrue(DesignerStoryboardEditing.tracks(source(true), "Fade").isEmpty())
    }
}
