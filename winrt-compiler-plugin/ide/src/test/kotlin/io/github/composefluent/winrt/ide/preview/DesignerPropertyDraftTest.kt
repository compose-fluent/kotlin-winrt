@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import junit.framework.TestCase

/** Source-authoring draft validation: concurrent PSI changes and duplicate focus events. */
class DesignerPropertyDraftTest : TestCase() {
    fun testInheritedEnumChoiceIsShownWithoutWritingALocalValue() {
        val property = DesignerProperty("HorizontalAlignment", "HorizontalAlignment", null, listOf("Left", "Center", "Right", "Stretch"), true, false)
        org.junit.Assert.assertEquals("Stretch", designerEffectiveChoice(property, "Microsoft.UI.Xaml.HorizontalAlignment.Stretch"))
        org.junit.Assert.assertEquals("Left", designerEffectiveChoice(property.copy(value = "Left"), "Stretch"))
        org.junit.Assert.assertNull(designerEffectiveChoice(property, "unavailable"))
        org.junit.Assert.assertNull(property.value)
    }
    fun testAutoDimensionsAndInheritedLiteralsAreShownWithoutBecomingLocalValues() {
        val bounds = io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds(0.0, 0.0, 800.0, 600.0)
        assertEquals("Auto (800)", designerPropertyPlaceholder(width(null), "NaN", bounds))
        assertEquals("400.0 (inherited)", designerPropertyPlaceholder(width(null), "400.0", bounds))
        assertEquals("Default", designerPropertyPlaceholder(width(null), "Unavailable: getter failed", bounds))
    }
    fun testRejectedSubmissionCanBeCorrectedWithoutDiscardingTheDraft() {
        val draft = DesignerPropertyDraft("100")
        draft.buffer.setTextAndPlaceCursorAtEnd("200")
        assertEquals("200", draft.submission(width("100")))
        draft.reject()
        assertEquals("200", draft.buffer.text.toString())
        draft.buffer.setTextAndPlaceCursorAtEnd("300")
        assertEquals("300", draft.submission(width("100")))
        draft.accept("300")
        assertFalse(draft.changed("300"))
    }
    private fun width(value: String?) = DesignerProperty("Width", "Double", value, emptyList(), true, false)
    fun testUntouchedDefaultDoesNotCreateLocalValue() {
        assertNull(DesignerPropertyDraft(null).submission(width(null)))
    }
    fun testEnterAndBlurSubmitOnlyOnceUntilSourceAcknowledges() {
        val draft = DesignerPropertyDraft("100")
        draft.buffer.setTextAndPlaceCursorAtEnd("200")
        assertEquals("200", draft.submission(width("100")))
        assertNull(draft.submission(width("100")))
        draft.accept("200")
        draft.buffer.setTextAndPlaceCursorAtEnd("300")
        assertEquals("300", draft.submission(width("200")))
    }
    fun testConcurrentSourceRemovalPreservesDraftAndBlocksOverwrite() {
        val draft = DesignerPropertyDraft("100")
        draft.buffer.setTextAndPlaceCursorAtEnd("200")
        draft.accept(null)
        assertEquals("200", draft.buffer.text.toString())
        assertTrue(draft.changed(null))
        assertNull(draft.submission(width(null)))
        draft.reload(null)
        assertEquals("", draft.buffer.text.toString())
        assertFalse(draft.changed(null))
    }
    fun testConcurrentSourceChangeBlocksOverwrite() {
        val draft = DesignerPropertyDraft("100")
        draft.buffer.setTextAndPlaceCursorAtEnd("200")
        draft.accept("150")
        assertNull(draft.submission(width("150")))
        assertEquals("200", draft.buffer.text.toString())
    }
    fun testCleanDraftFollowsSourceAndInvalidSizeDoesNotSubmit() {
        val draft = DesignerPropertyDraft("100")
        draft.accept("150")
        assertEquals("150", draft.buffer.text.toString())
        draft.buffer.setTextAndPlaceCursorAtEnd("-5")
        assertNull(draft.submission(width("150")))
    }
    private fun margin(value: String?) = DesignerProperty("Margin", "Thickness", value, emptyList(), true, false)
    fun testThicknessShorthandRemainsUnchangedUntilSideIsEdited() {
        val draft = DesignerThicknessDraft("8,12")
        assertEquals(listOf("8", "12", "8", "12"), draft.fields.map { it.text.toString() })
        assertNull(draft.submission(margin("8,12")))
        draft.fields[2].setTextAndPlaceCursorAtEnd("20")
        assertEquals("8,12,20,12", draft.submission(margin("8,12")))
        assertNull(draft.submission(margin("8,12")))
    }
    fun testThicknessSourceConflictPreservesEditedSide() {
        val draft = DesignerThicknessDraft("8")
        draft.fields[0].setTextAndPlaceCursorAtEnd("20")
        draft.accept(null)
        assertTrue(draft.draft.changed(null))
        assertEquals("20", draft.fields[0].text.toString())
        assertNull(draft.submission(margin(null)))
    }
    fun testUnsetThicknessDoesNotBecomeFourZerosOnFocusChange() {
        assertNull(DesignerThicknessDraft(null).submission(margin(null)))
    }
    fun testSourceAcknowledgementPreservesNewTyping() {
        val draft = DesignerPropertyDraft("100")
        draft.buffer.setTextAndPlaceCursorAtEnd("200")
        assertEquals("200", draft.submission(width("100")))
        draft.buffer.setTextAndPlaceCursorAtEnd("250")
        draft.accept("200")
        assertEquals("250", draft.buffer.text.toString())
        assertEquals("250", draft.submission(width("200")))
    }
    fun testEditingOneInheritedThicknessSidePreservesOtherEffectiveSides() {
        val draft = DesignerThicknessDraft(null, "8,12,16,20")
        assertNull(draft.submission(margin(null)))
        draft.fields[0].setTextAndPlaceCursorAtEnd("40")
        assertEquals("40,12,16,20", draft.submission(margin(null)))
    }
    fun testInheritedThicknessChangeDoesNotOverwriteUnsubmittedDraft() {
        val draft = DesignerThicknessDraft(null, "8")
        draft.fields[0].setTextAndPlaceCursorAtEnd("40")
        draft.accept(null, "12")
        assertTrue(draft.inheritedChanged)
        assertNull(draft.submission(margin(null)))
        draft.reload(null, "12")
        assertEquals(listOf("12", "12", "12", "12"), draft.fields.map { it.text.toString() })
    }
}
