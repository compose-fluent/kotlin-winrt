package io.github.composefluent.winrt.ide

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.templates.WinRTTemplateWriter
import java.nio.file.Files

class WinRTTemplateWriterTest : BasePlatformTestCase() {
    fun testModuleCreationPreservesSettingsDocumentAndSupportsUndo() {
        val root = Files.createTempDirectory("winrt-wizard-")
        try {
            val settings = root.resolve("settings.gradle.kts")
            Files.writeString(settings, "// Keep this comment\nrootProject.name = \"existing\"\n")
            val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(settings))
            val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
            val editor = FileEditorManager.getInstance(project).openFile(file, true).first()
            WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "// Unsaved user edit\n") }
            val original = document.text
            WinRTTemplateWriter.create(project, root.resolve("controls"), mapOf("build.gradle.kts" to "plugins {}\n".toByteArray()), root)
            assertEquals(original + "\ninclude(\":controls\")\n", document.text)
            assertTrue(Files.exists(root.resolve("controls/build.gradle.kts")))
            UndoManager.getInstance(project).undo(editor)
            assertEquals(original, document.text)
            assertFalse(Files.exists(root.resolve("controls/build.gradle.kts")))
            UndoManager.getInstance(project).redo(editor)
            assertTrue(Files.exists(root.resolve("controls/build.gradle.kts")))
            FileEditorManager.getInstance(project).closeFile(file)
        } finally {
            check(root.fileName.toString().startsWith("winrt-wizard-"))
            root.toFile().deleteRecursively()
        }
    }

    fun testExistingFilesAndOutsideModulesAreRejectedWithoutWrites() {
        val root = Files.createTempDirectory("winrt-wizard-")
        try {
            Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"existing\"\n")
            Files.writeString(root.resolve("build.gradle.kts"), "// User build\n")
            try {
                WinRTTemplateWriter.create(project, root, mapOf("build.gradle.kts" to byteArrayOf()))
                fail("Existing files must be preserved")
            } catch (_: IllegalArgumentException) { }
            assertEquals("// User build\n", Files.readString(root.resolve("build.gradle.kts")))
            try {
                WinRTTemplateWriter.create(project, root.resolve("../outside"), mapOf("build.gradle.kts" to byteArrayOf()), root)
                fail("Outside modules must be rejected")
            } catch (_: IllegalArgumentException) { }
        } finally {
            check(root.fileName.toString().startsWith("winrt-wizard-"))
            root.toFile().deleteRecursively()
        }
    }
}
