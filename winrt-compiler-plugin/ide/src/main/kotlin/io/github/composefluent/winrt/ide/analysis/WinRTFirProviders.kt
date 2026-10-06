package io.github.composefluent.winrt.ide.analysis

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.fir.extensions.KotlinBundledFirCompilerPluginProvider
import org.jetbrains.kotlin.idea.fir.extensions.KotlinFirCompilerPluginConfigurationForIdeProvider
import java.nio.file.Path

private const val ADAPTER_CLASS = "io.github.composefluent.winrt.ide.fir.WinRTIdeCompilerPlugin"

/** A constant-time path substitution; the IDE never loads the user's IR plugin. */
class WinRTBundledFirProvider : KotlinBundledFirCompilerPluginProvider {
    override fun provideBundledPluginJar(project: Project, userSuppliedPluginJar: Path): Path? {
        if (!COMPILER_JAR.matches(userSuppliedPluginJar.fileName.toString())) return null
        return PluginManagerCore.getPlugin(PluginId.getId("io.github.composefluent.winrt.ide"))
            ?.pluginPath?.resolve("lib/kotlin-winrt-ide-fir.jar")
    }

    companion object {
        private val COMPILER_JAR = Regex("(?:kotlin-)?winrt-compiler-plugin(?:-[^/\\\\]+)?\\.jar")
    }
}

@OptIn(ExperimentalCompilerApi::class)
class WinRTFirConfigurationProvider : KotlinFirCompilerPluginConfigurationForIdeProvider {
    private val selectedRegistrar = ThreadLocal<CompilerPluginRegistrar>()

    override fun isConfigurationProviderForCompilerPlugin(registrar: CompilerPluginRegistrar): Boolean {
        if (registrar.javaClass.name != ADAPTER_CLASS) return false
        // The 262 API selects the provider and immediately requests configuration
        // on the same thread, but omits the registrar from the second method.
        selectedRegistrar.set(registrar)
        return true
    }

    override fun provideCompilerConfigurationWithCustomOptions(original: CompilerConfiguration): CompilerConfiguration {
        val registrar = selectedRegistrar.get()
        selectedRegistrar.remove()
        val configuration = original.copy()
        if (registrar == null) return configuration
        val path = registrar.javaClass.getMethod("declarationsPath", CompilerConfiguration::class.java)
            .invoke(registrar, original) as? String ?: return configuration
        val text = ProjectManager.getInstance().openProjects.asSequence()
            .mapNotNull { it.service<WinRTXamlSnapshotService>().currentText(path) }.firstOrNull() ?: return configuration
        registrar.javaClass.getMethod("overrideDeclarations", CompilerConfiguration::class.java, String::class.java)
            .invoke(registrar, configuration, text)
        return configuration
    }
}
