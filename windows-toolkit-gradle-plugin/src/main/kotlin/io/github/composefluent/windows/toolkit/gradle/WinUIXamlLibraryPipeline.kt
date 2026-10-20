package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Export inferred schema and compile typed accessors in the library's original module.
 * Only a stable registrar shell precedes type inference; no business source is recompiled.
 */
@OptIn(org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi::class)
internal fun configureWinUIXamlLibraryPipeline(
    project: Project,
    metadataManifest: Provider<RegularFile>,
    header: TaskProvider<GenerateWinUIXamlApplicationHeaderTask>,
) {
    val outputs = mutableListOf<Pair<TaskProvider<*>, Provider<org.gradle.api.file.Directory>>>()
    fun configure(business: org.gradle.api.Task): Pair<TaskProvider<GenerateWinUIXamlLibraryRegistrarTask>, Provider<List<String>>> {
        val suffix = business.name.removePrefix("compileKotlin").ifBlank { "Main" }
        val root = project.layout.buildDirectory.dir("generated/kotlin-winrt/xaml/library/$suffix")
        val registrar = project.tasks.register("generateWinRTXamlLibraryRegistrar$suffix", GenerateWinUIXamlLibraryRegistrarTask::class.java) { task ->
            task.assemblyName.set(header.flatMap { it.assemblyName })
            task.outputDirectory.set(root.map { it.dir("registrar") })
        }
        val options = project.provider {
            listOf("xamlLibraryOutput=${root.get().asFile.absolutePath}", "xamlLibraryAssembly=${header.get().assemblyName.get()}",
                "xamlLibraryReferences=${header.get().sourceOutputDirectory.file("references.txt").get().asFile.absolutePath}",
                "xamlLibraryCompile=true")
                .flatMap { listOf("-P", "plugin:io.github.composefluent.winrt.compiler:$it") }
        }
        business.inputs.file(metadataManifest)
        business.outputs.file(root.map { it.file("KotlinXaml.winmd") })
        business.outputs.dir(root.map { it.dir("kotlin-winrt-support") })
        business.dependsOn(header, registrar)
        outputs += project.tasks.named(business.name) to root
        return registrar to options
    }
    val nonJvmTargetTasks = nonJvmTargetCompileTaskNames(project)
    project.tasks.withType(KotlinJvmCompile::class.java).toList().filter {
        !it.name.contains("Test", true) && !it.name.startsWith("compileKotlinWinRT") && it.name !in nonJvmTargetTasks
    }.forEach { business ->
        val (registrar, options) = configure(business)
        business.source(registrar.flatMap { it.outputDirectory })
        business.compilerOptions.freeCompilerArgs.addAll(options)
        // The schema and its registrar describe all model classes, not just dirty files.
        (business as KotlinCompile).incremental = false
    }
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.targets?.withType(KotlinNativeTarget::class.java)
        ?.filter { it.konanTarget.name == "mingw_x64" }?.forEach { target ->
            val business = target.compilations.getByName("main").compileTaskProvider.get()
            val (registrar, options) = configure(business)
            business.source(registrar.flatMap { it.outputDirectory })
            business.compilerOptions.freeCompilerArgs.addAll(options)
        }
    outputs.firstOrNull()?.let { (business, root) ->
        project.tasks.withType(GenerateWinRTIdentityTask::class.java).matching {
            it.name == "generateWinRTIdentity"
        }.configureEach { task ->
            task.xamlSchemaFiles.from(business.map { root.get().file("KotlinXaml.winmd") })
            task.compilerSupportManifestFiles.from(business.map { root.get().file("kotlin-winrt-support/compiler-support.tsv") })
        }
    }
}
