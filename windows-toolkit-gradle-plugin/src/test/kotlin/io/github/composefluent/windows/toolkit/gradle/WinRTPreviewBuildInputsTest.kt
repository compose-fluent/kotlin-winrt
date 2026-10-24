package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs
import java.nio.file.Files
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows

class WinRTPreviewBuildInputsTest {
    @Test fun resolved_input_manifest_roundtrips_paths_and_rejects_old_or_relative_records() {
        val root = Files.createTempDirectory("preview-input-manifest")
        try {
            val manifest = root.resolve(WinRTPreviewBuildInputs.FILE_NAME)
            val artifact = root.resolve("external library.klib")
            WinRTPreviewBuildInputs.write(manifest, listOf(artifact, artifact))
            assertEquals(listOf(artifact), WinRTPreviewBuildInputs.read(manifest))
            Files.writeString(manifest, "old-format")
            assertThrows(IllegalArgumentException::class.java) { WinRTPreviewBuildInputs.read(manifest) }
            Files.writeString(manifest, "kotlin-winrt-preview-inputs-v1\ncmVsYXRpdmUua2xpYg==")
            assertThrows(IllegalArgumentException::class.java) { WinRTPreviewBuildInputs.read(manifest) }
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
