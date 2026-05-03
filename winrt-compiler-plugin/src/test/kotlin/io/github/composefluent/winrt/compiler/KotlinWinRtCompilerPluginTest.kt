package io.github.composefluent.winrt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
