package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Path
import java.io.File
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

private val KOTLIN_WINRT_PLUGIN_GENERATED_SOURCE_OWNERS = setOf(
    "kotlin-winrt",
    "kotlin-winrt-authoring",
    "kotlin-winrt-application-entry",
    "kotlin-winrt-native-authoring-host",
)

/** Identifies plugin-owned generated source families independent of their per-variant suffix. */
internal fun isKotlinWindowsToolkitPluginOwnedAuthoringSourceRoot(path: Path): Boolean {
    val components = path.toAbsolutePath().normalize().iterator().asSequence().toList()
    return components.zipWithNext().any { (parent, child) ->
        parent.toString().equals("generated", ignoreCase = true) &&
            KOTLIN_WINRT_PLUGIN_GENERATED_SOURCE_OWNERS.any { owner ->
                child.toString().equals(owner, ignoreCase = true)
            }
    }
}

/** CsWinRT's CompilationProvider scope, adapted to KMP's original source fragments. */
internal fun winRTMainSourceSets(project: Project): Set<KotlinSourceSet> {
    val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return emptySet()
    return kotlin.targets.filter { it is KotlinJvmTarget || it is KotlinNativeTarget && it.konanTarget.name == "mingw_x64" }
        .flatMap { it.compilations.filter { compilation -> compilation.name == "main" }.flatMap { compilation -> compilation.allKotlinSourceSets } }
        .toSet()
}

internal fun winRTSourceRootOwners(project: Project): Map<String, String> = buildMap {
    winRTMainSourceSets(project).sortedBy { it.name }.forEach { sourceSet ->
        sourceSet.kotlin.srcDirs.filterNot { isKotlinWindowsToolkitPluginOwnedAuthoringSourceRoot(it.toPath()) }.forEach { root ->
            val path = root.toPath().toAbsolutePath().normalize().toString()
            val previous = put(path, sourceSet.name)
            require(previous == null || previous == sourceSet.name) { "Kotlin source root $path belongs to both $previous and ${sourceSet.name}" }
        }
    }
}

internal fun winRTXamlCompilationSourceRoots(project: Project, compilation: KotlinCompilation<*>): List<File> =
    (compilation.allKotlinSourceSets.flatMap { it.kotlin.srcDirs + it.resources.srcDirs } +
        appxResourceRoots(project, listOf(compilation.defaultSourceSet.name)).map(Path::toFile))
    .filterNot { isKotlinWindowsToolkitPluginOwnedAuthoringSourceRoot(it.toPath()) }
    .distinctBy { it.toPath().toAbsolutePath().normalize() }
