package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinJvmFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Model-only libraries export schema after type inference, before their ordinary compilation.
 * Their metadata identity publishes schema and registrar names; executable accessors stay in
 * the normal JAR/KLIB. No WinRT authoring ABI or XamlCompiler installation is needed.
 */
@OptIn(org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi::class)
internal fun configureWinRTXamlLibraryPipeline(
    project: Project,
    metadataIndex: Provider<RegularFile>,
    metadataManifest: Provider<RegularFile>,
    header: TaskProvider<GenerateWinRTXamlApplicationHeaderTask>,
    compilerPluginClasspath: FileCollection,
) {
    val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
    val outputs = mutableListOf<Pair<org.gradle.api.tasks.TaskProvider<*>, Provider<org.gradle.api.file.Directory>>>()
    fun options(root: Provider<org.gradle.api.file.Directory>) = project.provider {
        listOf("metadataIndex=${metadataIndex.get().asFile.absolutePath}",
            "xamlLibraryOutput=${root.get().asFile.absolutePath}", "xamlLibraryAssembly=${header.get().assemblyName.get()}",
            "xamlLibraryReferences=${header.get().sourceOutputDirectory.file("references.txt").get().asFile.absolutePath}")
            .flatMap { listOf("-P", "plugin:io.github.composefluent.winrt.compiler:$it") }
    }
    val kotlinApi = project.plugins.getPlugin(KotlinApiPlugin::class.java) as KotlinJvmFactory
    project.tasks.withType(KotlinJvmCompile::class.java).toList().filter {
        !it.name.contains("Test", true) && !it.name.startsWith("compileKotlinWinRT")
    }.forEach { business ->
        val suffix = business.name.removePrefix("compileKotlin")
        val root = project.layout.buildDirectory.dir("generated/kotlin-winrt/xaml/library/$suffix")
        val inputSources = project.provider { business.sources.files.filterNot { it.toPath().startsWith(root.get().asFile.toPath()) } }
        val compilerOptions = kotlinApi.createCompilerJvmOptions().apply {
            apiVersion.set(business.compilerOptions.apiVersion)
            languageVersion.set(business.compilerOptions.languageVersion)
            optIn.set(business.compilerOptions.optIn)
            jvmTarget.set(business.compilerOptions.jvmTarget)
            jvmDefault.set(business.compilerOptions.jvmDefault)
            moduleName.set("${project.name}-xaml-library-$suffix")
            freeCompilerArgs.set(business.compilerOptions.freeCompilerArgs.map(::withoutKotlinWinRTCompilerPluginOptions))
            freeCompilerArgs.addAll(options(root))
        }
        val semantic = kotlinApi.registerKotlinJvmCompileTask("compileKotlinWinRTXamlLibrarySemantic$suffix", compilerOptions)
        semantic.configure { task ->
            task.group = "kotlin-winrt"
            task.description = "Exports Kotlin library XAML schema and inferred property accessors."
            task.source(inputSources)
            task.libraries.from(business.libraries)
            task.friendPaths.from(business.friendPaths)
            task.pluginClasspath.from(compilerPluginClasspath)
            task.multiPlatformEnabled.set(business.multiPlatformEnabled)
            (task as KotlinCompile).apply {
                multiplatformStructure.fragments.set((business as KotlinCompile).multiplatformStructure.fragments)
                multiplatformStructure.refinesEdges.set(business.multiplatformStructure.refinesEdges)
                multiplatformStructure.defaultFragmentName.set(business.multiplatformStructure.defaultFragmentName)
                incremental = false
            }
            task.destinationDirectory.set(project.layout.buildDirectory.dir("intermediates/kotlin-winrt/xaml/library/$suffix/classes"))
            task.inputs.file(metadataManifest)
            task.outputs.dir(root)
            task.dependsOn(header, project.tasks.matching { it.name == "ksp" + business.name.removePrefix("compile") })
        }
        business.source(semantic.map { root.get().dir("src") })
        outputs += semantic to root
    }
    kotlin?.targets?.withType(KotlinNativeTarget::class.java)?.filter { it.konanTarget.name == "mingw_x64" }?.forEach { target ->
        val main = target.compilations.getByName("main")
        val business = main.compileTaskProvider.get()
        val suffix = business.name.removePrefix("compileKotlin")
        val root = project.layout.buildDirectory.dir("generated/kotlin-winrt/xaml/library/$suffix")
        val sources = project.provider { business.sources.files.filterNot { it.toPath().startsWith(root.get().asFile.toPath()) } }
        val commonSources = project.provider { business.commonSources.files.filterNot { it.toPath().startsWith(root.get().asFile.toPath()) } }
        val compilation = target.compilations.create("winRTXamlLibrarySemantic")
        compilation.defaultSourceSet.kotlin.setSrcDirs(emptyList<String>())
        compilation.compileDependencyFiles = main.compileDependencyFiles
        main.associatedCompilations.forEach(compilation::associateWith)
        val semantic = compilation.compileTaskProvider
        semantic.configure { task ->
            task.group = "kotlin-winrt"
            task.description = "Exports Native Kotlin library XAML schema; its temporary KLIB is never packaged."
            task.source(sources)
            task.commonSources.from(commonSources)
            task.compilerPluginClasspath = project.files(task.compilerPluginClasspath, compilerPluginClasspath)
            task.compilerOptions.apply {
                apiVersion.set(business.compilerOptions.apiVersion)
                languageVersion.set(business.compilerOptions.languageVersion)
                optIn.set(business.compilerOptions.optIn)
                moduleName.set("${project.name}-xaml-library-$suffix")
                freeCompilerArgs.set(business.compilerOptions.freeCompilerArgs.map(::withoutKotlinWinRTCompilerPluginOptions))
                freeCompilerArgs.addAll(options(root))
            }
            task.multiplatformStructure.fragments.set(business.multiplatformStructure.fragments)
            task.multiplatformStructure.refinesEdges.set(business.multiplatformStructure.refinesEdges)
            task.multiplatformStructure.defaultFragmentName.set(business.multiplatformStructure.defaultFragmentName)
            task.destinationDirectory.set(project.layout.buildDirectory.dir("intermediates/kotlin-winrt/xaml/library/$suffix/klib"))
            task.produceUnpackagedKlib.set(false)
            task.inputs.file(metadataManifest)
            task.outputs.dir(root)
            task.dependsOn(header, project.tasks.matching { it.name == "ksp" + business.name.removePrefix("compile") })
        }
        business.source(semantic.map { root.get().dir("src") })
        outputs += semantic to root
    }
    // Publish one authoritative API schema. The other target still generates and compiles
    // its own callbacks, and consumers import registrars from the published identity.
    outputs.firstOrNull()?.let { (semantic, root) ->
        project.tasks.withType(GenerateWinRTIdentityTask::class.java).configureEach { task ->
            task.xamlSchemaFiles.from(semantic.map { root.get().file("KotlinXaml.winmd") })
            task.compilerSupportManifestFiles.from(semantic.map { root.get().file("kotlin-winrt-support/compiler-support.tsv") })
        }
    }
}
