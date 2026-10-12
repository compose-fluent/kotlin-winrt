package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

/** Native exports schema and executable IR in one compilation; XAMLC validates and emits resources afterward. */
@OptIn(org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi::class)
internal fun configureWinUiXamlNativePipeline(
    project: Project,
    metadataManifest: Provider<RegularFile>,
    metadataIndex: Provider<RegularFile>,
    compilerPluginClasspath: FileCollection,
    configure: (CompileWinUiXamlTask) -> Unit,
    compilationInputs: (String, Provider<List<File>>) -> WinUiXamlCompilationInputs,
) {
    val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    kotlin.targets.withType(KotlinNativeTarget::class.java)
        .filter { it.konanTarget.name == "mingw_x64" }.forEach { target ->
            target.compilations.toList().filter { it.name == "main" }.forEach { compilation ->
                val business = compilation.compileTaskProvider.get()
                val suffix = business.name.removePrefix("compileKotlin")
                val sourceRoots = project.provider { winUiXamlCompilationSourceRoots(project, compilation) }
                if (sourceRoots.get().none { root -> root.isDirectory && root.walkTopDown().any { it.isFile && it.extension.equals("xaml", true) } }) return@forEach
                val (applicationHeader, declarations) = compilationInputs(suffix, sourceRoots)
                val semanticRoot = project.layout.buildDirectory.dir("intermediates/kotlin-winrt/xaml/$suffix/semantic")
                val symbols = semanticRoot.map { it.file("symbols.json") }
                business.compilerOptions.freeCompilerArgs.addAll(project.provider {
                    listOf("xamlDeclarations=${declarations.get().declarationsFile.get().asFile.absolutePath}",
                            "xamlSemanticOutput=${symbols.get().asFile.absolutePath}", "xamlCompile=true",
                        "xamlApplicationHeader=${applicationHeader.get().outputFile.get().asFile.absolutePath}",
                        "xamlReferencesFile=${declarations.get().outputDirectory.file("references.txt").get().asFile.absolutePath}")
                        .flatMap { listOf("-P", "plugin:io.github.composefluent.winrt.compiler:$it") }
                })
                business.inputs.file(declarations.flatMap { it.declarationsFile })
                business.inputs.file(metadataManifest)
                business.outputs.file(symbols)
                business.outputs.file(semanticRoot.map { it.file("KotlinXaml.winmd") })
                business.dependsOn(declarations)
                val implementation = project.tasks.register("compileWinRTXaml$suffix", CompileWinUiXamlTask::class.java) { task ->
                    configure(task)
                    task.sourceRoots.setFrom(sourceRoots)
                    task.semanticSymbols.set(symbols)
                    task.semanticWinmd.set(semanticRoot.map { it.file("KotlinXaml.winmd") })
                    task.outputDirectory.set(project.layout.buildDirectory.dir("generated/kotlin-winrt/xaml/$suffix/final"))
                    task.dependsOn(business)
                    val exportedSymbols = symbols.get().asFile
                    task.onlyIf("Kotlin XAML semantic export exists") { exportedSymbols.isFile }
                }
                val sourceGeneration = project.tasks.matching { it.name == "ksp" + business.name.removePrefix("compile") }
                business.dependsOn(sourceGeneration)
                implementation.configure { it.dependsOn(sourceGeneration) }
                business.finalizedBy(implementation)
                configureWinUiXamlPackageResources(project, sourceRoots, implementation, business.name, WinAppVariantKind.MingwX64,
                    compilation.defaultSourceSet.name)
            }
        }
}

internal fun configureWinUiXamlPackageResources(
    project: Project,
    sourceRoots: Provider<List<File>>,
    implementation: TaskProvider<CompileWinUiXamlTask>,
    compilationTaskName: String,
    kind: WinAppVariantKind,
    sourceSetName: String? = null,
) {
    // Library variants publish the same compiled XAML payload consumed by the
    // application's existing AppX resource dependency graph and makepri stage.
    val artifactTaskName = sourceSetName?.let { "packageAppxResources" + it.replaceFirstChar(Char::uppercaseChar) }
        ?: "packageAppxResources"
    project.tasks.withType(GenerateAppxResourcesArtifactTask::class.java).matching { it.name == artifactTaskName }.configureEach { task ->
        val compiled = implementation.flatMap { it.outputDirectory.dir("compiled") }
        task.resourceRoots.add(compiled.map { it.asFile.absolutePath })
        task.resourceInputs.from(compiled)
        task.excludedSourcePaths.addAll(implementation.map { it.inputXamlFiles.files.map { source -> source.absolutePath } })
        task.dependsOn(implementation)
    }
    val applicationVariants = discoverWinAppVariants(project).filter {
        it.kind == kind && compilationTaskName in it.compilationTaskNames(project)
    }.map { it.id }.toSet()
    project.tasks.withType(StageWinAppPackageTask::class.java).configureEach { task ->
        if (task.applicationVariant.get() in applicationVariants) {
            task.projectPriLayoutFiles.from(implementation.flatMap { it.outputDirectory.dir("compiled") })
            task.projectPriTargetPaths.putAll(implementation.flatMap { it.outputDirectory.dir("compiled") }
                .map { mapOf(it.asFile.absolutePath to "") })
            task.projectPriExcludedFromBuildPaths.addAll(sourceRoots.map { roots ->
                roots.filter(File::isDirectory).flatMap { root ->
                    root.walkTopDown().filter { it.isFile && it.extension.equals("xaml", true) }
                        .map { it.absolutePath }.toList()
                }
            })
            task.dependsOn(implementation)
        }
    }
}
