package io.github.composefluent.winrt.ide.fir

import io.github.composefluent.winrt.compiler.xaml.XamlFirRegistrar
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import java.nio.file.Files
import java.nio.file.Path

/** Frontend-only replacement for the build plugin; never writes compiler outputs. */
@OptIn(ExperimentalCompilerApi::class)
class WinRTIdeCompilerPlugin : CompilerPluginRegistrar() {
    override val pluginId: String = "io.github.composefluent.winrt.compiler"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val text = configuration[IDE_DECLARATIONS] ?: configuration[DECLARATIONS_PATH]?.let { path ->
            Path.of(path).takeIf(Files::isRegularFile)?.let(Files::readString)
        } ?: return
        FirExtensionRegistrarAdapter.registerExtension(XamlFirRegistrar(WinRTXamlDeclarations.parse(text)))
    }

    // The Kotlin plugin isolates compiler plugin classes. The IDE configuration
    // provider invokes these methods on that instance, without crossing model/key identities.
    fun declarationsPath(configuration: CompilerConfiguration): String? = configuration[DECLARATIONS_PATH]
    fun overrideDeclarations(configuration: CompilerConfiguration, text: String) {
        configuration.put(IDE_DECLARATIONS, text)
    }

    companion object {
        private val DECLARATIONS_PATH = CompilerConfigurationKey<String>("WinRT IDE XAML declarations path")
        private val IDE_DECLARATIONS = CompilerConfigurationKey<String>("WinRT IDE live XAML declarations")
        internal fun setPath(configuration: CompilerConfiguration, path: String) = configuration.put(DECLARATIONS_PATH, path)
    }
}

@OptIn(ExperimentalCompilerApi::class)
class WinRTIdeCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "io.github.composefluent.winrt.compiler"
    // Accept build options for transport compatibility. Only the declarations
    // input is relevant to frontend analysis; outputs are deliberately ignored.
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        "xamlDeclarations", "xamlSemanticOutput", "xamlReferences", "xamlReferencesFile", "xamlApplicationHeader",
        "xamlImplementation", "xamlLibraryOutput", "xamlLibraryAssembly", "xamlLibraryReferences", "metadataIndex", "typeIndexOutput",
        "authoredCandidatesOutput", "authoredMetadataOutput", "authoredWinmdOutput", "authoredHostManifestOutput",
        "authoringAssemblyName", "authoringTargetArtifactName", "projectionSupportOwnerArtifactName",
        "compilerSupportManifest", "compilerSupportClassOutputDirectory", "projectionSupportMode",
    ).map { CliOption(it, "<value>", "Build option accepted by the WinRT IDE adapter", required = false, allowMultipleOccurrences = true) }

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        if (option.optionName == "xamlDeclarations") WinRTIdeCompilerPlugin.setPath(configuration, value)
    }
}
