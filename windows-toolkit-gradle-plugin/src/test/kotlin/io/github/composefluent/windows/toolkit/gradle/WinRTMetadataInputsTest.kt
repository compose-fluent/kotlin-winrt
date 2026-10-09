package io.github.composefluent.windows.toolkit.gradle

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class WinRTMetadataInputsTest {
    @Test
    fun discovers_contracts_and_manifests_without_including_sdk_tools_or_libraries() {
        // .cswinrt/nuget/Microsoft.Windows.CsWinRT.targets: CsWinRTInputs.
        val root = Files.createTempDirectory("winrt-sdk-inputs")
        fun file(path: String) = root.resolve(path).also {
            Files.createDirectories(it.parent)
            Files.writeString(it, "fixture")
        }.toFile()
        val expected = setOf(
            file("Platforms/UAP/10.0.26100.0/Platform.xml"),
            file("References/10.0.26100.0/Windows.Foundation/1.0.0.0/Windows.Foundation.winmd"),
            file("Extension SDKs/Extension/1.0/SDKManifest.xml"),
            file("Extension SDKs/Extension/1.0/References/Extension.WinMD"),
        )
        file("bin/x64/tools.winmd")
        file("Include/ignored.h")
        file("Lib/x64/ignored.lib")
        file("References/ignored.dll")
        try {
            assertEquals(expected, windowsSdkMetadataFiles(root).toSet())
            val newer = file("Platforms/UAP/10.0.28000.0/Platform.xml")
            assertEquals(expected + newer, windowsSdkMetadataFiles(root).toSet())
            Files.delete(newer.toPath())
            assertEquals(expected, windowsSdkMetadataFiles(root).toSet())
        } finally {
            GradleFileOperations.deleteDirectory(root)
        }
    }
}
