package io.github.composefluent.winrt.ide.analysis

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import org.jetbrains.kotlin.cli.common.arguments.copyBean
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import java.nio.file.Path

/** Gradle configures the business target's compiler, but shared KMP source sets
 * have their own IDE facets. Give those facets the same XAMLC declaration input
 * and the IDE's FIR-only adapter, without changing a Gradle compilation or IR.
 */
internal object WinRTFirModuleConfiguration {
    private const val DECLARATIONS = "plugin:io.github.composefluent.winrt.compiler:xamlDeclarations="

    fun configure(project: Project, models: List<WinRTModuleData>, provider: IdeModifiableModelsProvider? = null) {
        val adapter = winRTFirAdapterJar()?.toString() ?: return
        val modules = provider?.modules ?: ModuleManager.getInstance(project).modules
        var changed = false
        modules.filterNot { it.isDisposed }.forEach { module ->
            val facet = provider?.getModifiableFacetModel(module)?.allFacets?.filterIsInstance<KotlinFacet>()?.singleOrNull()
                ?: KotlinFacet.get(module) ?: return@forEach
            val roots = provider?.getModifiableRootModel(module)?.sourceRoots
                ?: ModuleRootManager.getInstance(module).sourceRoots
            val compilation = models.flatMap { it.xamlCompilations }.filter { candidate ->
                roots.any { root -> candidate.sourceRoots.any { source -> sameRoot(root.path, source) } }
            }.sortedBy { it.taskName }.firstOrNull() ?: return@forEach
            val settings = facet.configuration.settings
            val original = settings.compilerArguments ?: return@forEach
            // A target facet already supplied by KGP keeps its build options and
            // original jar; the bundled provider substitutes only its frontend.
            val paths = original.pluginClasspaths.orEmpty().toList()
            val updatedPaths = if (paths.any { Path.of(it).fileName.toString().contains("winrt-compiler-plugin") ||
                    Path.of(it).fileName.toString() == "kotlin-winrt-ide-fir.jar" }) paths else paths + adapter
            val options = original.pluginOptions.orEmpty().toList()
            val updatedOptions = options.filterNot { it.startsWith(DECLARATIONS) } + (DECLARATIONS + compilation.declarationsFile)
            if (paths == updatedPaths && options == updatedOptions) return@forEach
            val updated = copyBean(original).apply {
                pluginClasspaths = updatedPaths.toTypedArray()
                pluginOptions = updatedOptions.toTypedArray()
            }
            settings.compilerArguments = updated
            settings.updateMergedArguments()
            if (provider == null) FacetManager.getInstance(module).facetConfigurationChanged(facet)
            changed = true
        }
        if (changed) project.messageBus.syncPublisher(KotlinCompilerSettingsListener.TOPIC).settingsChanged(null, models)
    }

    fun restore(project: Project, models: List<WinRTModuleData>) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && project.service<io.github.composefluent.winrt.ide.project.WinRTProjectService>().modules.value == models)
                ApplicationManager.getApplication().runWriteAction { configure(project, models) }
        }
    }

    private fun sameRoot(left: String, right: String) = runCatching {
        Path.of(left).toAbsolutePath().normalize() == Path.of(right).toAbsolutePath().normalize()
    }.getOrDefault(false)
}
