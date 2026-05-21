package io.github.composefluent.winrt.compiler

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KotlinWinRtAuthoringScannerCliTest {
    @Test
    fun scans_public_runtime_class_and_interface_candidates() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import microsoft.ui.xaml.Application
            import windows.foundation.IStringable

            class App : Application()

            class StringableThing : IStringable

            internal class InternalStringableThing : IStringable

            private class PrivateStringableThing : IStringable

            private class PrivateContainer {
                class NestedStringableThing : IStringable
            }

            interface StringableContract : IStringable
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Application	RuntimeClass	Microsoft.UI.Xaml.IApplicationOverrides
            Microsoft.UI.Xaml.Controls.ContentControl	RuntimeClass	Microsoft.UI.Xaml.Controls.IContentControlOverrides
            Microsoft.UI.Xaml.IApplicationOverrides	Interface	
            Microsoft.UI.Xaml.Controls.IContentControlOverrides	Interface
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
                "sample\tApp\tsample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides\ttrue",
                "sample\tInternalStringableThing\tsample.InternalStringableThing\t\tWindows.Foundation.IStringable\t\tfalse",
                "sample\tStringableContract\tsample.StringableContract\t\tWindows.Foundation.IStringable\t\ttrue",
                "sample\tStringableThing\tsample.StringableThing\t\tWindows.Foundation.IStringable\t\ttrue",
            ).joinToString("\n"),
            output.readText().trimEnd(),
        )
    }

    @Test
    fun scans_internal_winui_runtime_class_for_local_type_details() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-scan-internal-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import microsoft.ui.xaml.controls.ContentControl

            internal class RootContentControl : ContentControl()

            private class PrivateContentControl : ContentControl()
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Controls.ContentControl	RuntimeClass	Microsoft.UI.Xaml.Controls.IContentControlOverrides
            Microsoft.UI.Xaml.Controls.IContentControlOverrides	Interface
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
            "sample\tRootContentControl\tsample.RootContentControl\tMicrosoft.UI.Xaml.Controls.ContentControl\tMicrosoft.UI.Xaml.Controls.IContentControlOverrides\tMicrosoft.UI.Xaml.Controls.IContentControlOverrides\tfalse",
            output.readText().trimEnd(),
        )
    }

    @Test
    fun scans_inherited_winui_overridable_interfaces_for_grid_subclass() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-scan-grid-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import microsoft.ui.xaml.automation.peers.AutomationPeer
            import microsoft.ui.xaml.controls.Grid

            class SampleHostPanel : Grid() {
                override fun onCreateAutomationPeer(): AutomationPeer =
                    SampleAutomationPeer()
            }

            class SampleAutomationPeer : AutomationPeer()
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Controls.Grid	RuntimeClass		Microsoft.UI.Xaml.Controls.Panel
            Microsoft.UI.Xaml.Controls.Panel	RuntimeClass		Microsoft.UI.Xaml.FrameworkElement
            Microsoft.UI.Xaml.FrameworkElement	RuntimeClass	Microsoft.UI.Xaml.IFrameworkElementOverrides	Microsoft.UI.Xaml.UIElement
            Microsoft.UI.Xaml.UIElement	RuntimeClass	Microsoft.UI.Xaml.IUIElementOverrides	System.Object
            Microsoft.UI.Xaml.Automation.Peers.AutomationPeer	RuntimeClass	Microsoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides	System.Object
            Microsoft.UI.Xaml.IFrameworkElementOverrides	Interface
            Microsoft.UI.Xaml.IUIElementOverrides	Interface
            Microsoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides	Interface
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
                "sample\tSampleAutomationPeer\tsample.SampleAutomationPeer\tMicrosoft.UI.Xaml.Automation.Peers.AutomationPeer\tMicrosoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides\tMicrosoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides\ttrue",
                "sample\tSampleHostPanel\tsample.SampleHostPanel\tMicrosoft.UI.Xaml.Controls.Grid\tMicrosoft.UI.Xaml.IFrameworkElementOverrides;Microsoft.UI.Xaml.IUIElementOverrides\tMicrosoft.UI.Xaml.IFrameworkElementOverrides;Microsoft.UI.Xaml.IUIElementOverrides\ttrue",
            ).joinToString("\n"),
            output.readText().trimEnd(),
        )
    }
}
