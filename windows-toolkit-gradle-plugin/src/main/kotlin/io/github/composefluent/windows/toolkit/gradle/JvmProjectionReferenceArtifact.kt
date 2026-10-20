package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun jvmProjectionReferenceArtifact(project: Project, taskName: String): Provider<Directory> =
    project.layout.buildDirectory.dir("classes/kotlin-winrt/reference/$taskName")

/**
 * CsWinRT's reference projection boundary, adapted to Kotlin/JVM. Use Kotlin's ABI producer
 * so inline bodies, Kotlin metadata and internal compiler bridge signatures remain coherent.
 * Both the independent declaration compilation and the implementation compilation use the
 * same ABI producer. Its output must never be used as a runtime artifact.
 */
internal fun configureJvmProjectionReferenceArtifact(
    project: Project,
    task: KotlinJvmCompile,
): Provider<Directory> = configureJvmReferenceArtifact(project, task, incremental = false)

/** Preserve unchanged ABI files across incremental business compilation, then prune deletions. */
internal fun configureJvmIncrementalReferenceArtifact(
    project: Project,
    task: KotlinJvmCompile,
): Provider<Directory> = configureJvmReferenceArtifact(project, task, incremental = true)

private fun configureJvmReferenceArtifact(
    project: Project,
    task: KotlinJvmCompile,
    incremental: Boolean,
): Provider<Directory> {
    val version = project.getKotlinPluginVersion()
    KotlinWinRTCompilerVersions.validate(version)
    val tools = project.configurations.maybeCreate("kotlinWinRTJvmAbiGenerator").apply {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
        defaultDependencies { dependencies ->
            dependencies.add(project.dependencies.create("org.jetbrains.kotlin:jvm-abi-gen-embeddable:$version"))
        }
    }
    val reference = jvmProjectionReferenceArtifact(project, task.name)
    task.pluginClasspath.from(tools)
    task.outputs.dir(reference).withPropertyName("winrtProjectionReference")
    // The ABI writer only writes current classes. The standalone projection is compiled as a
    // whole, so remove its previous reference inventory to avoid retaining deleted declarations.
    if (!incremental) {
        task.doFirst { GradleFileOperations.deleteDirectory(reference.get().asFile.toPath()) }
    } else {
        val implementation = task.destinationDirectory
        task.doLast {
            val root = reference.get().asFile
            val classes = implementation.get().asFile
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(root)
                if (!classes.resolve(relative).isFile) {
                    check(file.delete()) { "Cannot remove obsolete reference declaration: $file" }
                }
            }
        }
    }
    task.pluginOptions.add(reference.map { output ->
        CompilerPluginConfig().apply {
            addPluginArgument("org.jetbrains.kotlin.jvm.abi", FilesSubpluginOption("outputDir", listOf(output.asFile)))
        }
    })
    return reference
}
