package io.github.kitectlab.winrt.compiler

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KotlinWinRtAuthoringScannerCliTest {
    @Test
    fun scans_runtime_class_interface_and_nonpublic_candidates_for_k2_validation() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.Application
            import io.github.kitectlab.winrt.projections.windows.foundation.IStringable

            class App : Application()

            class StringableThing : IStringable

            internal class InternalStringableThing : IStringable

            interface StringableContract : IStringable
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Application	RuntimeClass	Microsoft.UI.Xaml.IApplicationOverrides
            Microsoft.UI.Xaml.IApplicationOverrides	Interface	
            Windows.Foundation.IStringable	Interface	
            """.trimIndent(),
        )

        KotlinWinRtAuthoringScannerCli.main(
            arrayOf(
                "--metadata-index",
                metadataIndex.toString(),
                "--output",
                output.toString(),
                "--source-root",
                root.toString(),
            ),
        )

        assertEquals(
            listOf(
                "sample\tApp\tsample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides",
                "sample\tInternalStringableThing\tsample.InternalStringableThing\t\tWindows.Foundation.IStringable\t",
                "sample\tStringableContract\tsample.StringableContract\t\tWindows.Foundation.IStringable\t",
                "sample\tStringableThing\tsample.StringableThing\t\tWindows.Foundation.IStringable",
            ).joinToString("\n"),
            output.readText().trimEnd(),
        )
    }
}
