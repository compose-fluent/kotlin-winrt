package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.authoring.readAuthoringMetadataIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KotlinWinRtAuthoringScannerCliTest {
    @Test
    fun rejects_missing_authoring_metadata_index() {
        val missingIndex = Files.createTempDirectory("kotlin-winrt-missing-authoring-index-")
            .resolve("metadata-index.tsv")

        val error = runCatching { readAuthoringMetadataIndex(missingIndex) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt authoring metadata index"),
        )
    }

    @Test
    fun rejects_malformed_authoring_metadata_index_rows() {
        val metadataIndex = Files.createTempFile("kotlin-winrt-malformed-metadata-index-", ".tsv")
        metadataIndex.writeText("Microsoft.UI.Xaml.Application\n")

        val error = runCatching { readAuthoringMetadataIndex(metadataIndex) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("authoring metadata index row 1"),
        )
    }

    @Test
    fun rejects_duplicate_authoring_metadata_index_rows() {
        val metadataIndex = Files.createTempFile("kotlin-winrt-duplicate-metadata-index-", ".tsv")
        metadataIndex.writeText(
            """
            Windows.Foundation.IStringable	Interface
            Windows.Foundation.IStringable	RuntimeClass
            """.trimIndent(),
        )

        val error = runCatching { readAuthoringMetadataIndex(metadataIndex) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("duplicates type Windows.Foundation.IStringable"),
        )
    }

    @Test
    fun rejects_blank_authoring_metadata_index_list_elements() {
        val metadataIndex = Files.createTempFile("kotlin-winrt-blank-list-metadata-index-", ".tsv")
        metadataIndex.writeText(
            "Microsoft.UI.Xaml.Application\tRuntimeClass\tMicrosoft.UI.Xaml.IApplicationOverrides;;Microsoft.UI.Xaml.IApplicationOverrides2\n",
        )

        val error = runCatching { readAuthoringMetadataIndex(metadataIndex) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("authoring metadata index row 1"),
        )
    }

    @Test
    fun rejects_unknown_authoring_metadata_index_kinds() {
        val metadataIndex = Files.createTempFile("kotlin-winrt-unknown-kind-metadata-index-", ".tsv")
        metadataIndex.writeText("Windows.Foundation.IStringable\tNotAWinRtKind\n")

        val error = runCatching { readAuthoringMetadataIndex(metadataIndex) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("authoring metadata index row 1"),
        )
    }

    @Test
    fun rejects_authoring_metadata_index_rows_with_extra_columns() {
        val metadataIndex = Files.createTempFile("kotlin-winrt-extra-column-metadata-index-", ".tsv")
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\t\t\textra\n")

        val error = runCatching { readAuthoringMetadataIndex(metadataIndex) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("authoring metadata index row 1"),
        )
    }

    @Test
    fun rejects_missing_authoring_source_roots() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-missing-root-")
        val missingSourceRoot = root.resolve("missing")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\n")

        val error = runCatching {
            KotlinWinRtAuthoringScannerCli.main(
                arrayOf(
                    "--metadata-index",
                    metadataIndex.toString(),
                    "--output",
                    output.toString(),
                    "--source-root",
                    missingSourceRoot.toString(),
                ),
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("authoring scanner source root"),
        )
    }

    @Test
    fun rejects_authoring_scanner_arguments_without_values() {
        val error = runCatching {
            KotlinWinRtAuthoringScannerCli.main(arrayOf("--source-root"))
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("--source-root requires a path value"),
        )
    }

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
    fun rejects_nested_authored_runtime_class_candidates() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-nested-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import windows.foundation.IStringable

            class Container {
                class NestedStringableThing : IStringable
            }
            """.trimIndent(),
        )
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\n")

        val error = runCatching {
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
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("nested authored runtime classes are not supported"),
        )
    }

    @Test
    fun skips_existing_projection_metadata_types() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-projection-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package windows.foundation

            import sample.IShape

            class IStringable : IShape
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Windows.Foundation.IStringable	Interface
            Sample.IShape	Interface
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

        assertEquals("", output.readText())
    }

    @Test
    fun rejects_generic_authored_runtime_class_candidates() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-generic-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import windows.foundation.IStringable

            class GenericStringableThing<T> : IStringable
            """.trimIndent(),
        )
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\n")

        val error = runCatching {
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
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("must not be generic"),
        )
    }

    @Test
    fun rejects_unsealed_authored_runtime_class_candidates() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-unsealed-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import windows.foundation.IStringable

            open class OpenStringableThing : IStringable
            """.trimIndent(),
        )
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\n")

        val error = runCatching {
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
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("must be final"),
        )
    }

    @Test
    fun rejects_duplicate_authored_type_candidates() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-duplicate-scan-")
        val sourceRootA = root.resolve("a")
        val sourceRootB = root.resolve("b")
        Files.createDirectories(sourceRootA)
        Files.createDirectories(sourceRootB)
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        val source = """
            package sample

            import windows.foundation.IStringable

            class StringableThing : IStringable
        """.trimIndent()
        sourceRootA.resolve("Sample.kt").writeText(source)
        sourceRootB.resolve("Sample.kt").writeText(source)
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\n")

        val error = runCatching {
            KotlinWinRtAuthoringScannerCli.main(
                arrayOf(
                    "--metadata-index",
                    metadataIndex.toString(),
                    "--output",
                    output.toString(),
                    "--source-root",
                    sourceRootA.toString(),
                    "--source-root",
                    sourceRootB.toString(),
                ),
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("duplicate authored type candidates: sample.StringableThing"),
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
    fun scans_authored_runtime_class_annotation_metadata_without_runtime_reflection() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-annotation-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import io.github.composefluent.winrt.runtime.WinRtAuthoredRuntimeClass

            @WinRtAuthoredRuntimeClass(
                baseClassName = "Microsoft.UI.Xaml.Controls.ContentControl",
                interfaceNames = ["Windows.Foundation.IStringable"],
                overridableInterfaceNames = ["Microsoft.UI.Xaml.Controls.IContentControlOverrides"],
            )
            class LocalContentControl
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Controls.ContentControl	RuntimeClass	Microsoft.UI.Xaml.Controls.IContentControlOverrides
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
            "sample\tLocalContentControl\tsample.LocalContentControl\tMicrosoft.UI.Xaml.Controls.ContentControl\tMicrosoft.UI.Xaml.Controls.IContentControlOverrides;Windows.Foundation.IStringable\tMicrosoft.UI.Xaml.Controls.IContentControlOverrides\ttrue",
            output.readText().trimEnd(),
        )
    }

    @Test
    fun scans_authored_runtime_class_annotation_projection_package_metadata_names() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-annotation-projection-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import io.github.composefluent.winrt.runtime.WinRtAuthoredRuntimeClass

            @WinRtAuthoredRuntimeClass(
                baseClassName = "microsoft.ui.xaml.controls.ContentControl",
                interfaceNames = ["windows.foundation.IStringable"],
                overridableInterfaceNames = ["microsoft.ui.xaml.controls.IContentControlOverrides"],
            )
            class LocalContentControl
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Controls.ContentControl	RuntimeClass	Microsoft.UI.Xaml.Controls.IContentControlOverrides
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
            "sample\tLocalContentControl\tsample.LocalContentControl\tMicrosoft.UI.Xaml.Controls.ContentControl\tMicrosoft.UI.Xaml.Controls.IContentControlOverrides;Windows.Foundation.IStringable\tMicrosoft.UI.Xaml.Controls.IContentControlOverrides\ttrue",
            output.readText().trimEnd(),
        )
    }

    @Test
    fun rejects_authored_runtime_class_annotation_unknown_metadata_type() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-annotation-missing-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            @io.github.composefluent.winrt.runtime.WinRtAuthoredRuntimeClass(
                interfaceNames = ["Sample.MissingInterface"],
            )
            class LocalShape
            """.trimIndent(),
        )
        metadataIndex.writeText("Sample.IShapeOverrides\tInterface\n")

        val error = runCatching {
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
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("annotation references unknown WinRT metadata type Sample.MissingInterface"),
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
            Microsoft.UI.Xaml.UIElement	RuntimeClass	Microsoft.UI.Xaml.IUIElementOverrides	Object
            Microsoft.UI.Xaml.Automation.Peers.AutomationPeer	RuntimeClass	Microsoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides	Object
            Object	RuntimeClass	Sample.IObjectOverrides
            Microsoft.UI.Xaml.IFrameworkElementOverrides	Interface
            Microsoft.UI.Xaml.IUIElementOverrides	Interface
            Microsoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides	Interface
            Sample.IObjectOverrides	Interface
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

    @Test
    fun rejects_authoring_metadata_index_missing_inherited_base_type() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-missing-base-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import microsoft.ui.xaml.controls.Grid

            class SampleHostPanel : Grid()
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Controls.Grid	RuntimeClass		Microsoft.UI.Xaml.Controls.Panel
            """.trimIndent(),
        )

        val error = runCatching {
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
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains(
                "references missing base type Microsoft.UI.Xaml.Controls.Panel",
            ),
        )
    }
}
