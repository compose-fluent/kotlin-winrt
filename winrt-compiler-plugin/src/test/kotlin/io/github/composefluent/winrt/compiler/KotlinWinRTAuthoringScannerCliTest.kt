package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringCandidateFile
import io.github.composefluent.winrt.compiler.authoring.readAuthoringMetadataIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KotlinWinRTAuthoringScannerCliTest {
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
        metadataIndex.writeText("Windows.Foundation.IStringable\tNotAWinRTKind\n")

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
            KotlinWinRTAuthoringScannerCli.main(
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
            KotlinWinRTAuthoringScannerCli.main(arrayOf("--source-root"))
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("--source-root requires a path value"),
        )
    }

    @Test
    fun scans_public_runtime_class_candidates() {
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

        KotlinWinRTAuthoringScannerCli.main(
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
                "sample\tApp\tsample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides\ttrue\t\t",
                "sample\tInternalStringableThing\tsample.InternalStringableThing\t\tWindows.Foundation.IStringable\t\tfalse\t\t",
                "sample\tStringableThing\tsample.StringableThing\t\tWindows.Foundation.IStringable\t\ttrue",
            ).joinToString("\n"),
            output.readText().trimEnd(),
        )
    }

    @Test
    fun scans_leaf_runtime_class_candidates_through_source_application_base() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-indirect-application-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Base.kt").writeText(
            """
            package sample

            import microsoft.ui.xaml.Application

            internal open class ComposeApplicationBase : Application()
            """.trimIndent(),
        )
        root.resolve("App.kt").writeText(
            """
            package sample

            internal class WinUIXamlApplication : ComposeApplicationBase()
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Application	RuntimeClass	Microsoft.UI.Xaml.IApplicationOverrides
            Microsoft.UI.Xaml.IApplicationOverrides	Interface
            """.trimIndent(),
        )

        KotlinWinRTAuthoringScannerCli.main(
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
            "sample\tWinUIXamlApplication\tsample.WinUIXamlApplication\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides\tfalse",
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
            KotlinWinRTAuthoringScannerCli.main(
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

        KotlinWinRTAuthoringScannerCli.main(
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
    fun keeps_source_authored_type_when_metadata_index_already_contains_same_runtime_class() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-source-runtime-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package Sample

            import microsoft.ui.xaml.Application

            class App : Application()
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Application	RuntimeClass	Microsoft.UI.Xaml.IApplicationOverrides	System.Object
            Microsoft.UI.Xaml.IApplicationOverrides	Interface
            Sample.App	RuntimeClass	Microsoft.UI.Xaml.IApplicationOverrides	Microsoft.UI.Xaml.Application
            """.trimIndent(),
        )

        KotlinWinRTAuthoringScannerCli.main(
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
            "Sample\tApp\tSample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides\ttrue\t\t\n",
            output.readText(),
        )
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
            KotlinWinRTAuthoringScannerCli.main(
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
    fun rejects_non_class_authored_runtime_class_candidates() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-interface-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import windows.foundation.IStringable

            interface StringableContract : IStringable
            """.trimIndent(),
        )
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\n")

        val error = runCatching {
            KotlinWinRTAuthoringScannerCli.main(
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
            error.message.orEmpty().contains("must be a concrete Kotlin class"),
        )
    }

    @Test
    fun rejects_value_class_authored_runtime_class_candidates() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-value-class-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import windows.foundation.IStringable

            @JvmInline
            value class ValueStringableThing(val value: String) : IStringable
            """.trimIndent(),
        )
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\n")

        val error = runCatching {
            KotlinWinRTAuthoringScannerCli.main(
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
            error.message.orEmpty().contains("must not be a Kotlin value class"),
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
            KotlinWinRTAuthoringScannerCli.main(
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
    fun rejects_public_authored_runtime_class_candidates_without_default_constructor() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-constructor-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import windows.foundation.IStringable

            class StringableThing(private val value: String) : IStringable
            """.trimIndent(),
        )
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\n")

        val error = runCatching {
            KotlinWinRTAuthoringScannerCli.main(
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
            error.message.orEmpty().contains("must declare an accessible zero-argument constructor for default activation"),
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
            KotlinWinRTAuthoringScannerCli.main(
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

        KotlinWinRTAuthoringScannerCli.main(
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

            import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

            @WinRTAuthoredRuntimeClass(
                baseClassName = "Microsoft.UI.Xaml.Controls.ContentControl",
                interfaceNames = ["Windows.Foundation.IStringable"],
                overridableInterfaceNames = ["Microsoft.UI.Xaml.Controls.IContentControlOverrides"],
                activatableFactoryInterfaceName = "Sample.IWidgetFactory",
                staticFactoryInterfaceNames = ["Sample.IWidgetStatics"],
            )
            class LocalContentControl
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Controls.ContentControl	RuntimeClass	Microsoft.UI.Xaml.Controls.IContentControlOverrides
            Microsoft.UI.Xaml.Controls.IContentControlOverrides	Interface
            Sample.IWidgetFactory	Interface
            Sample.IWidgetStatics	Interface
            Windows.Foundation.IStringable	Interface
            """.trimIndent(),
        )

        KotlinWinRTAuthoringScannerCli.main(
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
            "sample\tLocalContentControl\tsample.LocalContentControl\tMicrosoft.UI.Xaml.Controls.ContentControl\tMicrosoft.UI.Xaml.Controls.IContentControlOverrides;Windows.Foundation.IStringable\tMicrosoft.UI.Xaml.Controls.IContentControlOverrides\ttrue\tSample.IWidgetFactory\tSample.IWidgetStatics",
            output.readText().trimEnd(),
        )
    }

    @Test
    fun scans_multiple_authored_runtime_class_annotations_with_projection_style_metadata_names() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-multiple-annotation-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

            @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IClosable"])
            class NativeClosableThing

            @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
            class NativeStringableThing

            @WinRTAuthoredRuntimeClass(
                interfaceNames = ["windows.data.json.IJsonValue"],
                staticFactoryInterfaceNames = ["windows.data.json.IJsonValueStatics"],
            )
            class NativeJsonValueThing
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Windows.Data.Json.IJsonValue	Interface
            Windows.Data.Json.IJsonValueStatics	Interface
            Windows.Foundation.IClosable	Interface
            Windows.Foundation.IStringable	Interface
            """.trimIndent(),
        )

        KotlinWinRTAuthoringScannerCli.main(
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
                KotlinWinRTAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "NativeClosableThing",
                    sourceTypeName = "sample.NativeClosableThing",
                    winRTBaseClassName = null,
                    winRTInterfaceNames = listOf("Windows.Foundation.IClosable"),
                    overridableInterfaceNames = emptyList(),
                ),
                KotlinWinRTAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "NativeJsonValueThing",
                    sourceTypeName = "sample.NativeJsonValueThing",
                    winRTBaseClassName = null,
                    winRTInterfaceNames = listOf("Windows.Data.Json.IJsonValue"),
                    overridableInterfaceNames = emptyList(),
                    staticFactoryInterfaceNames = listOf("Windows.Data.Json.IJsonValueStatics"),
                ),
                KotlinWinRTAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "NativeStringableThing",
                    sourceTypeName = "sample.NativeStringableThing",
                    winRTBaseClassName = null,
                    winRTInterfaceNames = listOf("Windows.Foundation.IStringable"),
                    overridableInterfaceNames = emptyList(),
                ),
            ),
            KotlinWinRTAuthoringCandidateFile.read(output),
        )
    }

    @Test
    fun scans_fixture_shaped_authored_runtime_class_annotations() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-fixture-shaped-annotation-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass
            import io.github.composefluent.winrt.runtime.AsyncInfo
            import io.github.composefluent.winrt.runtime.EventRegistrationToken
            import io.github.composefluent.winrt.runtime.EventRegistrationTokenTable
            import io.github.composefluent.winrt.runtime.PlatformAbi
            import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference
            import io.github.composefluent.winrt.runtime.WinRTAsyncResultWriter
            import io.github.composefluent.winrt.runtime.WinRTTypeSignature
            import windows.data.json.JsonArray
            import windows.data.json.JsonObject
            import windows.data.json.JsonValue
            import windows.data.json.JsonValueType
            import windows.foundation.collections.MapChangedEventHandler
            import windows.storage.streams.ByteOrder
            import windows.storage.streams.IBuffer
            import windows.storage.streams.IInputStream
            import windows.storage.streams.InputStreamOptions
            import windows.storage.streams.UnicodeEncoding
            import kotlin.time.Duration
            import kotlin.time.Instant

            @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IClosable"])
            class NativeClosableThing {
                fun close() = Unit
            }

            @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
            class NativeStringableThing {
                override fun toString(): String = "NativeStringableThing"
            }

            @WinRTAuthoredRuntimeClass(
                interfaceNames = ["windows.data.json.IJsonValue"],
                staticFactoryInterfaceNames = ["windows.data.json.IJsonValueStatics"],
            )
            class NativeJsonValueThing private constructor(
                private val value: String,
            ) {
                constructor() : this("NativeJsonValueThing")
                val valueType: JsonValueType
                    get() = JsonValueType.String

                fun stringify(): String = "\"${'$'}value\""

                fun getString(): String = value

                companion object {
                    fun parse(input: String): NativeJsonValueThing = NativeJsonValueThing(input)
                }
            }
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Windows.Data.Json.IJsonValue	Interface
            Windows.Data.Json.IJsonValueStatics	Interface
            Windows.Foundation.IClosable	Interface
            Windows.Foundation.IStringable	Interface
            """.trimIndent(),
        )

        KotlinWinRTAuthoringScannerCli.main(
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
                "sample.NativeClosableThing",
                "sample.NativeJsonValueThing",
                "sample.NativeStringableThing",
            ),
            KotlinWinRTAuthoringCandidateFile.read(output).map { candidate -> candidate.sourceTypeName },
        )
    }

    @Test
    fun scans_fixture_shaped_annotations_next_to_inherited_winui_authored_class() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-fixture-source-root-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("NativeClosableThing.kt").writeText(
            """
            package sample

            import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass
            import io.github.composefluent.winrt.runtime.AsyncInfo
            import io.github.composefluent.winrt.runtime.EventRegistrationToken
            import io.github.composefluent.winrt.runtime.EventRegistrationTokenTable
            import io.github.composefluent.winrt.runtime.PlatformAbi
            import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference
            import io.github.composefluent.winrt.runtime.WinRTAsyncResultWriter
            import io.github.composefluent.winrt.runtime.WinRTTypeSignature
            import windows.data.json.JsonArray
            import windows.data.json.JsonObject
            import windows.data.json.JsonValue
            import windows.data.json.JsonValueType
            import windows.foundation.collections.MapChangedEventHandler
            import windows.storage.streams.ByteOrder
            import windows.storage.streams.IBuffer
            import windows.storage.streams.IInputStream
            import windows.storage.streams.InputStreamOptions
            import windows.storage.streams.UnicodeEncoding
            import kotlin.time.Duration
            import kotlin.time.Instant

            @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IClosable"])
            class NativeClosableThing {
                fun close() = Unit
            }

            @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
            class NativeStringableThing {
                override fun toString(): String = "NativeStringableThing"
            }

            @WinRTAuthoredRuntimeClass(
                interfaceNames = ["windows.data.json.IJsonValue"],
                staticFactoryInterfaceNames = ["windows.data.json.IJsonValueStatics"],
            )
            class NativeJsonValueThing private constructor(
                private val value: String,
            ) {
                constructor() : this("NativeJsonValueThing")
                val valueType: JsonValueType
                    get() = JsonValueType.String

                fun stringify(): String = "\"${'$'}value\""

                fun getString(): String = value

                fun getNumber(): Double = 42.5

                fun getBoolean(): Boolean = true

                fun getArray(): JsonArray = JsonArray()

                fun getObject(): JsonObject = JsonObject()

                companion object {
                    fun parse(input: String): NativeJsonValueThing = NativeJsonValueThing(input)

                    fun tryParse(input: String, result: JsonValue): Boolean = input.isNotEmpty() && result.getString().isNotEmpty()

                    fun createBooleanValue(input: Boolean): NativeJsonValueThing = NativeJsonValueThing(input.toString())

                    fun createNumberValue(input: Double): NativeJsonValueThing = NativeJsonValueThing(input.toString())

                    fun createStringValue(input: String): NativeJsonValueThing = NativeJsonValueThing(input)
                }
            }

            @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.storage.streams.IDataReader"])
            class NativeDataReaderThing {
                val unconsumedBufferLength: UInt
                    get() = 4u

                var unicodeEncoding: UnicodeEncoding = UnicodeEncoding.Utf8

                var byteOrder: ByteOrder = ByteOrder.LittleEndian

                var inputStreamOptions: InputStreamOptions = InputStreamOptions.Metadata.None

                fun readByte(): UByte = 0x41u

                fun readBytes(value: Array<UByte>) {
                    val bytes = arrayOf(0x57u, 0x69u, 0x6Eu, 0x52u).map { it.toUByte() }
                    value.indices.forEach { index ->
                        value[index] = bytes.getOrElse(index) { 0u.toUByte() }
                    }
                }

                fun readBuffer(length: UInt): IBuffer {
                    throw UnsupportedOperationException("NativeDataReaderThing does not expose readBuffer.")
                }

                fun readBoolean(): Boolean = true

                fun readGuid() = io.github.composefluent.winrt.runtime.Guid("11111111-2222-3333-4444-555555555555")

                fun readInt16(): Short = 16

                fun readInt32(): Int = 32

                fun readInt64(): Long = 64

                fun readUInt16(): UShort = 16u

                fun readUInt32(): UInt = 32u

                fun readUInt64(): ULong = 64u

                fun readSingle(): Float = 1.25f

                fun readDouble(): Double = 2.5

                fun readString(codeUnitCount: UInt): String = "WinR".take(codeUnitCount.toInt())

                fun readDateTime(): Instant =
                    Instant.fromEpochSeconds(1_700_000_000L, 123_456_700)

                fun readTimeSpan(): Duration =
                    Duration.parse("PT1H2M3.4567S")

                fun loadAsync(count: UInt): WinRTAsyncOperationReference<UInt> =
                    AsyncInfo.fromResult(
                        result = count.coerceAtMost(unconsumedBufferLength),
                        resultSignature = WinRTTypeSignature.uint32(),
                        resultWriter = WinRTAsyncResultWriter { value, resultOut ->
                            PlatformAbi.writeInt32(resultOut, value.toInt())
                        },
                    )

                fun detachBuffer(): IBuffer {
                    throw UnsupportedOperationException("NativeDataReaderThing does not expose detachBuffer.")
                }

                fun detachStream(): IInputStream {
                    throw UnsupportedOperationException("NativeDataReaderThing does not expose detachStream.")
                }
            }

            @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.collections.IPropertySet"])
            class NativePropertySetThing {
                private val values = linkedMapOf<String, Any?>("existing" to "value")
                private val mapChangedHandlers =
                    EventRegistrationTokenTable.create<MapChangedEventHandler<String, Any?>>()

                val size: UInt
                    get() = values.size.toUInt()

                fun lookup(key: String): Any? = values.getValue(key)

                fun hasKey(key: String): Boolean = values.containsKey(key)

                fun getView(): Map<String, Any?> = values.toMap()

                fun iterator(): Iterator<Map.Entry<String, Any?>> =
                    values.entries.map { entry -> object : Map.Entry<String, Any?> {
                        override val key: String = entry.key
                        override val value: Any? = entry.value
                    } }.iterator()

                fun insert(key: String, value: Any?): Boolean {
                    val replaced = values.containsKey(key)
                    values[key] = value
                    return replaced
                }

                fun remove(key: String) {
                    values.remove(key)
                }

                fun clear() {
                    values.clear()
                }

                fun addMapChanged(handler: MapChangedEventHandler<String, Any?>): EventRegistrationToken =
                    mapChangedHandlers.addEventHandler(handler)

                fun removeMapChanged(token: EventRegistrationToken) {
                    mapChangedHandlers.removeEventHandler(token)
                }
            }
            """.trimIndent(),
        )
        root.resolve("NativeContentControlThing.kt").writeText(
            """
            package sample

            import microsoft.ui.xaml.controls.ContentControl

            class NativeContentControlThing : ContentControl() {
                init {
                    content = "NativeContentControlThing"
                }
            }
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Controls.ContentControl	RuntimeClass	Microsoft.UI.Xaml.Controls.IContentControlOverrides	Microsoft.UI.Xaml.Controls.Control
            Microsoft.UI.Xaml.Controls.Control	RuntimeClass	Microsoft.UI.Xaml.Controls.IControlOverrides	Microsoft.UI.Xaml.FrameworkElement
            Microsoft.UI.Xaml.FrameworkElement	RuntimeClass	Microsoft.UI.Xaml.IFrameworkElementOverrides	Microsoft.UI.Xaml.UIElement
            Microsoft.UI.Xaml.UIElement	RuntimeClass	Microsoft.UI.Xaml.IUIElementOverrides	Object
            Microsoft.UI.Xaml.Controls.IContentControlOverrides	Interface
            Microsoft.UI.Xaml.Controls.IControlOverrides	Interface
            Microsoft.UI.Xaml.IFrameworkElementOverrides	Interface
            Microsoft.UI.Xaml.IUIElementOverrides	Interface
            Object	RuntimeClass
            Windows.Data.Json.IJsonValue	Interface
            Windows.Data.Json.IJsonValueStatics	Interface
            Windows.Foundation.Collections.IPropertySet	Interface
            Windows.Foundation.IClosable	Interface
            Windows.Foundation.IStringable	Interface
            Windows.Storage.Streams.IDataReader	Interface
            sample.NativeContentControlThing	RuntimeClass	Microsoft.UI.Xaml.Controls.IContentControlOverrides;Microsoft.UI.Xaml.Controls.IControlOverrides;Microsoft.UI.Xaml.IFrameworkElementOverrides;Microsoft.UI.Xaml.IUIElementOverrides	Microsoft.UI.Xaml.Controls.ContentControl
            """.trimIndent(),
        )

        KotlinWinRTAuthoringScannerCli.main(
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
                "sample.NativeClosableThing",
                "sample.NativeContentControlThing",
                "sample.NativeDataReaderThing",
                "sample.NativeJsonValueThing",
                "sample.NativePropertySetThing",
                "sample.NativeStringableThing",
            ),
            KotlinWinRTAuthoringCandidateFile.read(output).map { candidate -> candidate.sourceTypeName },
        )
    }

    @Test
    fun scans_native_component_fixture_authored_runtime_class_annotations() {
        val root = Path.of("..", "winrt-authoring", "native-component-fixture", "src", "commonMain", "kotlin")
            .toAbsolutePath()
            .normalize()
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Controls.ContentControl	RuntimeClass	Microsoft.UI.Xaml.Controls.IContentControlOverrides	Microsoft.UI.Xaml.Controls.Control
            Microsoft.UI.Xaml.Controls.Control	RuntimeClass	Microsoft.UI.Xaml.Controls.IControlOverrides	Microsoft.UI.Xaml.FrameworkElement
            Microsoft.UI.Xaml.FrameworkElement	RuntimeClass	Microsoft.UI.Xaml.IFrameworkElementOverrides	Microsoft.UI.Xaml.UIElement
            Microsoft.UI.Xaml.UIElement	RuntimeClass	Microsoft.UI.Xaml.IUIElementOverrides	Object
            Microsoft.UI.Xaml.Controls.IContentControlOverrides	Interface
            Microsoft.UI.Xaml.Controls.IControlOverrides	Interface
            Microsoft.UI.Xaml.IFrameworkElementOverrides	Interface
            Microsoft.UI.Xaml.IUIElementOverrides	Interface
            Object	RuntimeClass
            Windows.Data.Json.IJsonValue	Interface
            Windows.Data.Json.IJsonValueStatics	Interface
            Windows.Foundation.Collections.IPropertySet	Interface
            Windows.Foundation.IClosable	Interface
            Windows.Foundation.IStringable	Interface
            Windows.Storage.Streams.IDataReader	Interface
            sample.NativeContentControlThing	RuntimeClass	Microsoft.UI.Xaml.Controls.IContentControlOverrides;Microsoft.UI.Xaml.Controls.IControlOverrides;Microsoft.UI.Xaml.IFrameworkElementOverrides;Microsoft.UI.Xaml.IUIElementOverrides	Microsoft.UI.Xaml.Controls.ContentControl
            """.trimIndent(),
        )

        KotlinWinRTAuthoringScannerCli.main(
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
                "sample.NativeClosableThing",
                "sample.NativeContentControlThing",
                "sample.NativeDataReaderThing",
                "sample.NativeJsonValueThing",
                "sample.NativePropertySetThing",
                "sample.NativeStringableThing",
            ),
            KotlinWinRTAuthoringCandidateFile.read(output).map { candidate -> candidate.sourceTypeName },
        )
    }

    @Test
    fun scans_authored_runtime_class_annotation_positional_metadata() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-annotation-positional-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

            @WinRTAuthoredRuntimeClass(
                "Microsoft.UI.Xaml.Controls.ContentControl",
                ["Windows.Foundation.IStringable"],
                ["Microsoft.UI.Xaml.Controls.IContentControlOverrides"],
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

        KotlinWinRTAuthoringScannerCli.main(
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
    fun ignores_unresolved_local_authored_runtime_class_annotation_names() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-local-annotation-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            annotation class WinRTAuthoredRuntimeClass(
                val interfaceNames: Array<String> = [],
            )

            @WinRTAuthoredRuntimeClass(interfaceNames = ["Windows.Foundation.IStringable"])
            class LocalShape
            """.trimIndent(),
        )
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\n")

        KotlinWinRTAuthoringScannerCli.main(
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
    fun rejects_authored_runtime_class_annotation_unknown_metadata_type() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-annotation-missing-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            @io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass(
                interfaceNames = ["Sample.MissingInterface"],
            )
            class LocalShape
            """.trimIndent(),
        )
        metadataIndex.writeText("Sample.IShapeOverrides\tInterface\n")

        val error = runCatching {
            KotlinWinRTAuthoringScannerCli.main(
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
    fun rejects_authored_runtime_class_annotation_metadata_kind_mismatches() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-annotation-kind-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            @io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass(
                interfaceNames = ["Microsoft.UI.Xaml.Application"],
            )
            class LocalShape
            """.trimIndent(),
        )
        metadataIndex.writeText(
            """
            Microsoft.UI.Xaml.Application	RuntimeClass	Microsoft.UI.Xaml.IApplicationOverrides
            Microsoft.UI.Xaml.IApplicationOverrides	Interface
            """.trimIndent(),
        )

        val error = runCatching {
            KotlinWinRTAuthoringScannerCli.main(
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
            error.message.orEmpty().contains("annotation interfaceNames must reference WinRT interfaces"),
        )
    }

    @Test
    fun rejects_authored_runtime_class_annotation_metadata_resolved_only_by_source_imports() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-annotation-import-only-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import io.github.composefluent.winrt.projections.windows.foundation.IStringable
            import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

            @WinRTAuthoredRuntimeClass(interfaceNames = ["IStringable"])
            class LocalShape
            """.trimIndent(),
        )
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\n")

        val error = runCatching {
            KotlinWinRTAuthoringScannerCli.main(
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
            error.message.orEmpty().contains("annotation references unknown WinRT metadata type IStringable"),
        )
    }

    @Test
    fun rejects_blank_authored_runtime_class_annotation_metadata_elements() {
        val root = Files.createTempDirectory("kotlin-winrt-authoring-annotation-blank-scan-")
        val metadataIndex = Files.createTempFile("kotlin-winrt-metadata-index-", ".tsv")
        val output = Files.createTempFile("kotlin-winrt-authoring-candidates-", ".tsv")
        root.resolve("Sample.kt").writeText(
            """
            package sample

            import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

            @WinRTAuthoredRuntimeClass(interfaceNames = [""])
            class LocalShape
            """.trimIndent(),
        )
        metadataIndex.writeText("Windows.Foundation.IStringable\tInterface\n")

        val error = runCatching {
            KotlinWinRTAuthoringScannerCli.main(
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
            error.message.orEmpty().contains("annotation references unknown WinRT metadata type "),
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

        KotlinWinRTAuthoringScannerCli.main(
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
                "sample\tSampleAutomationPeer\tsample.SampleAutomationPeer\tMicrosoft.UI.Xaml.Automation.Peers.AutomationPeer\tMicrosoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides\tMicrosoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides\ttrue\t\t",
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
            KotlinWinRTAuthoringScannerCli.main(
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
