package io.github.composefluent.winrt.ide.hotreload

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import org.junit.Assert.*
import org.junit.Test

class WinRTKotlinSourceSnapshotTest {
    @Test fun refresh_timestamps_and_generated_outputs_do_not_change_the_compiled_source_snapshot() {
        val project = Files.createTempDirectory("source-snapshot")
        try {
            val file = project.resolve("src/Page.kt")
            Files.createDirectories(file.parent); Files.writeString(file, "class Page")
            val before = winRTKotlinSourceSnapshot(listOf(project), listOf(project))
            Files.setLastModifiedTime(file, FileTime.fromMillis(1))
            val generated = project.resolve("build/generated/Generated.kt")
            Files.createDirectories(generated.parent); Files.writeString(generated, "class Generated")
            assertEquals(before, winRTKotlinSourceSnapshot(listOf(project), listOf(project)))
            Files.writeString(file, "class Changed")
            assertNotEquals(before, winRTKotlinSourceSnapshot(listOf(project), listOf(project)))
            Files.delete(file)
            assertTrue(winRTKotlinSourceSnapshot(listOf(project), listOf(project)).isEmpty())
        } finally { project.toFile().deleteRecursively() }
    }

    @Test fun unsaved_document_contents_participate_in_the_snapshot() {
        val project = Files.createTempDirectory("source-document")
        try {
            val file = project.resolve("Page.kt"); Files.writeString(file, "class Page")
            val disk = winRTKotlinSourceSnapshot(listOf(project), listOf(project))
            val edited = winRTKotlinSourceSnapshot(listOf(project), listOf(project)) { "class Edited" }
            assertNotEquals(disk, edited)
        } finally { project.toFile().deleteRecursively() }
    }
}
