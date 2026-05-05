package io.github.composefluent.winrt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.net.URLClassLoader

@OptIn(ExperimentalCompilerApi::class)
class KotlinWinRtCompilerPluginTest {
    @Test
    fun command_line_processor_stores_metadata_index_option() {
        val configuration = CompilerConfiguration()
        val processor = KotlinWinRtCommandLineProcessor()
        processor.processOption(
            CliOption(
                optionName = "metadataIndex",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/kotlin-winrt/metadata-index.tsv",
            configuration,
        )

        assertEquals(
            "build/kotlin-winrt/metadata-index.tsv",
            configuration.get(KotlinWinRtCommandLineProcessor.METADATA_INDEX_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "typeIndexOutput",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/classes/kotlin/main/kotlin-winrt/type-index.tsv",
            configuration,
        )
        assertEquals(
            "build/classes/kotlin/main/kotlin-winrt/type-index.tsv",
            configuration.get(KotlinWinRtCommandLineProcessor.TYPE_INDEX_OUTPUT_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "compilerSupportManifest",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/generated/kotlin-winrt/kotlin-winrt-support/compiler-support.tsv",
            configuration,
        )
        assertEquals(
            "build/generated/kotlin-winrt/kotlin-winrt-support/compiler-support.tsv",
            configuration.get(KotlinWinRtCommandLineProcessor.COMPILER_SUPPORT_MANIFEST_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "compilerSupportClassOutputDirectory",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/classes/kotlin/main",
            configuration,
        )
        assertEquals(
            "build/classes/kotlin/main",
            configuration.get(KotlinWinRtCommandLineProcessor.COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY),
        )
    }

    @Test
    fun registrar_installs_ir_generation_extension_for_k2_path() {
        val storage = CompilerPluginRegistrar.ExtensionStorage()
        val registrar = KotlinWinRtCompilerPluginRegistrar()

        with(registrar) {
            storage.registerExtensions(CompilerConfiguration())
        }

        assertTrue(registrar.supportsK2)
        assertTrue(storage.registeredExtensions[IrGenerationExtension].orEmpty().any { extension ->
            extension is KotlinWinRtIrGenerationExtension
        })
    }

    @Test
    fun projection_type_index_records_map_kotlin_projection_names_to_winrt_metadata() {
        val record = projectionTypeIndexRecordForSourceType(
            sourceTypeName = "microsoft.ui.xaml.Window",
            winRtTypes = mapOf(
                "Microsoft.UI.Xaml.Window" to IndexedWinRtType(
                    qualifiedName = "Microsoft.UI.Xaml.Window",
                    kind = "RuntimeClass",
                    overridableInterfaces = emptyList(),
                    baseTypeName = "Microsoft.UI.Xaml.Controls.ContentControl",
                ),
            ),
        )

        assertEquals(
            "microsoft.ui.xaml.Window\tMicrosoft.UI.Xaml.Window\tRuntimeClass\tMicrosoft.UI.Xaml.Controls.ContentControl",
            record?.render(),
        )
    }

    @Test
    fun compiler_support_manifest_records_generated_support_tables() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.projections.support.WinRTProjectionRegistrar	projection-registrar.tsv	12
            event-source	io.github.composefluent.winrt.projections.support.WinRTEventProjectionHelpers	io/github/composefluent/winrt/projections/support/WinRTEventProjectionHelpers.kt	3
            """.trimIndent() + "\n",
        )

        val entries = readCompilerSupportManifest(manifest)

        assertEquals(2, entries.size)
        assertEquals("projection-registrar", entries[0].kind)
        assertEquals("io.github.composefluent.winrt.projections.support.WinRTProjectionRegistrar", entries[0].className)
        assertEquals("projection-registrar.tsv", entries[0].sourceFile)
        assertEquals(12, entries[0].entries)
        assertEquals("event-source", entries[1].kind)
    }

    @Test
    fun compiler_support_manifest_writes_class_artifact() {
        val outputDirectory = Files.createTempDirectory("kotlin-winrt-support-class-")
        writeCompilerSupportManifestClass(
            entries = listOf(
                KotlinWinRtCompilerSupportManifestEntry(
                    kind = "projection-registrar",
                    className = "io.github.composefluent.winrt.projections.support.WinRTProjectionRegistrar",
                    sourceFile = "projection-registrar.tsv",
                    entries = 12,
                ),
                KotlinWinRtCompilerSupportManifestEntry(
                    kind = "event-source",
                    className = "io.github.composefluent.winrt.projections.support.WinRTEventProjectionHelpers",
                    sourceFile = "io/github/composefluent/winrt/projections/support/WinRTEventProjectionHelpers.kt",
                    entries = 3,
                ),
            ),
            outputDirectory = outputDirectory,
        )

        URLClassLoader(arrayOf(outputDirectory.toUri().toURL()), null).use { classLoader ->
            val klass = Class.forName(
                "io.github.composefluent.winrt.projections.support.WinRTCompilerSupportManifest",
                false,
                classLoader,
            )
            assertEquals(2, klass.getField("ENTRY_COUNT").getInt(null))
            assertEquals(12, klass.getField("PROJECTION_REGISTRAR_ENTRIES").getInt(null))
            assertEquals(3, klass.getField("EVENT_SOURCE_ENTRIES").getInt(null))
        }
    }

    @Test
    fun projection_registrar_input_writes_class_artifact() {
        val input = Files.createTempFile("kotlin-winrt-projection-registrar-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("kotlinClassName", "projectedTypeName", "kind", "baseTypeName", "metadataClassName"),
                listOf("java.lang.String", "Sample.Foundation.Widget", "RuntimeClass", "Sample.Foundation.WidgetBase", ""),
                listOf("java.lang.Integer", "Sample.Foundation.IWidget", "Interface", "", ""),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )
        val outputDirectory = Files.createTempDirectory("kotlin-winrt-registrar-class-")

        val entries = readProjectionRegistrarEntries(input)
        writeProjectionRegistrarClass(entries, outputDirectory)

        URLClassLoader(arrayOf(outputDirectory.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            val klass = Class.forName(
                "io.github.composefluent.winrt.projections.support.WinRTProjectionRegistrar",
                false,
                classLoader,
            )
            assertEquals("register", klass.getDeclaredMethod("register").name)
        }
    }
}
