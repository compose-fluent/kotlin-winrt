package io.github.kitectlab.winrt.compiler

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KotlinWinRtAuthoringScannerCliTest {
    @Test
    fun scans_runtime_class_and_interface_authored_types_without_application_special_case() {
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
            """
            sample	App	sample.App	Microsoft.UI.Xaml.Application	Microsoft.UI.Xaml.IApplicationOverrides	Microsoft.UI.Xaml.IApplicationOverrides
            sample	StringableThing	sample.StringableThing		Windows.Foundation.IStringable
            """.trimIndent(),
            output.readText().trimEnd(),
        )
    }
}
