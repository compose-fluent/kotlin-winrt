package io.github.kitectlab.winrt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

@OptIn(ExperimentalCompilerApi::class)
class KotlinWinRtCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "metadataIndex",
            valueDescription = "<path>",
            description = "Path to the kotlin-winrt metadata index used by authoring analysis.",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        if (option.optionName == "metadataIndex") {
            configuration.put(METADATA_INDEX_KEY, value)
        }
    }

    companion object {
        const val PLUGIN_ID: String = "io.github.kitectlab.winrt.compiler"
        val METADATA_INDEX_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt metadata index")
    }
}

@OptIn(ExperimentalCompilerApi::class)
class KotlinWinRtCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = KotlinWinRtCommandLineProcessor.PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(
            KotlinWinRtIrGenerationExtension(
                metadataIndexPath = configuration.get(KotlinWinRtCommandLineProcessor.METADATA_INDEX_KEY),
            ),
        )
    }
}

class KotlinWinRtIrGenerationExtension(
    private val metadataIndexPath: String?,
) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        if (metadataIndexPath.isNullOrBlank()) {
            return
        }
        // The compiler plugin is now the owner for post-analysis authoring transforms.
        // Source generation still runs before compile so generated type details are compiled
        // by the normal Kotlin task; this extension is the stable IR hook for the next slice.
    }
}
