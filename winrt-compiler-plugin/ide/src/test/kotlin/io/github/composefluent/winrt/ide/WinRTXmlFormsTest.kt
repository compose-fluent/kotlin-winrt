package io.github.composefluent.winrt.ide

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlFile
import io.github.composefluent.winrt.ide.resources.WinRTXmlForms
import io.github.composefluent.winrt.ide.templates.WinRTTemplateKind
import io.github.composefluent.winrt.ide.templates.WinRTTemplateOptions
import io.github.composefluent.winrt.ide.templates.WinRTTemplates

class WinRTXmlFormsTest : BasePlatformTestCase() {
    fun testManifestEditsPreserveUnknownXmlAndUndoWithTheSourceDocument() {
        val original = WinRTTemplates.module(WinRTTemplateOptions("app", "sample.app", WinRTTemplateKind.WinUIApplication, mingwX64 = false))
            .getValue("src/main/appxResources/AppxManifest.xml").toString(Charsets.UTF_8)
            .replace("</Package>", "<!--Keep this exact comment--><custom:Unknown xmlns:custom=\"urn:custom\" Attribute=\"keep\"/>\n</Package>")
        val xml = myFixture.addFileToProject("AppxManifest.xml", original) as XmlFile
        myFixture.configureFromExistingVirtualFile(xml.virtualFile)
        val display = WinRTXmlForms.snapshot(xml).fields.first { it.attribute == null && it.path.last().name == "DisplayName" }
        WinRTXmlForms.set(project, xml.virtualFile, display, "A & B")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("A & B", WinRTXmlForms.snapshot(xml).fields.first { it.id == display.id }.value)
        assertTrue(xml.text.contains("<!--Keep this exact comment--><custom:Unknown xmlns:custom=\"urn:custom\" Attribute=\"keep\"/>"))
        assertTrue(WinRTXmlForms.snapshot(xml).errors.isEmpty())
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(original, myFixture.editor.document.text)
        UndoManager.getInstance(project).redo(editor)
        assertEquals("A & B", WinRTXmlForms.snapshot(xml).fields.first { it.id == display.id }.value)
        WinRTXmlForms.addCapability(project, xml.virtualFile, "documentsLibrary", restricted = false)
        WinRTXmlForms.addExtension(project, xml.virtualFile, 0, "sampleapp", null)
        WinRTXmlForms.addExtension(project, xml.virtualFile, 0, "samplefiles", ".sample")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(xml.text.contains("windows.protocol"))
        assertTrue(xml.text.contains("windows.fileTypeAssociation"))
        assertTrue(xml.text.contains("documentsLibrary"))
        assertTrue(xml.text.contains("<custom:Unknown"))
    }

    fun testReswUsesTheExistingDocumentAndRejectsStaleOrDuplicateEdits() {
        val xml = myFixture.addFileToProject("Strings/en-US/Resources.resw", """<root><!--keep--><data name="Greeting" xml:space="preserve"><value>Hello<!--inside--></value><comment>translator note</comment></data><extra attr="keep"/></root>""") as XmlFile
        val field = WinRTXmlForms.snapshot(xml).fields.first { it.attribute == null && it.path.last().name == "value" }
        WinRTXmlForms.set(project, xml.virtualFile, field, "Hello & welcome")
        assertEquals("Hello & welcome", WinRTXmlForms.snapshot(xml).fields.first { it.id == field.id }.value)
        assertTrue(xml.text.contains("<!--inside-->"))
        assertTrue(xml.text.contains("<comment>translator note</comment>"))
        try { WinRTXmlForms.set(project, xml.virtualFile, field, "stale"); fail("Stale edits must be rejected") } catch (_: IllegalArgumentException) { }
        try { WinRTXmlForms.addResw(project, xml.virtualFile, "Greeting", "duplicate"); fail("Duplicate keys must be rejected") } catch (_: IllegalArgumentException) { }
        WinRTXmlForms.addResw(project, xml.virtualFile, "NewKey", "<new>")
        assertEquals("<new>", WinRTXmlForms.snapshot(xml).fields.first { it.attribute == null && it.path.last().name == "value" && it.path[1].index == 1 }.value)
        assertTrue(xml.text.contains("<!--keep-->"))
        assertTrue(xml.text.contains("<extra attr=\"keep\"/>"))
    }

    fun testTranslationChecksIncludeUnsavedKeys() {
        val root = java.nio.file.Files.createTempDirectory("winrt-resw-translations-")
        val files = listOf("en-US" to "Greeting", "de-DE" to "Farewell").map { (language, key) ->
            val file = root.resolve("Strings/$language/Resources.resw")
            java.nio.file.Files.createDirectories(file.parent)
            java.nio.file.Files.writeString(file, "<root><data name=\"$key\"><value>Hello</value></data></root>")
            file
        }
        val virtual = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(files.first())!!
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtual)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            document.setText(document.text.replace("</root>", "<data name=\"Farewell\"><value>Bye</value></data></root>"))
        }
        val entries = files.map { io.github.composefluent.winrt.ide.resources.WinRTResourceEntry(root.relativize(it).toString().replace('\\', '/'), it.toString(), ":resources") }
        val errors = io.github.composefluent.winrt.ide.resources.WinRTReswTranslations.inspect(project, entries)
        assertEquals(listOf("Strings/de-DE/Resources.resw: missing translation 'Greeting'"), errors)
        assertFalse(java.nio.file.Files.readString(files.first()).contains("Farewell"))
    }
}
