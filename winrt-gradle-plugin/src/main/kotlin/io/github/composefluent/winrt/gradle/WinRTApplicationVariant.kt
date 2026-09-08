package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.util.Locale

/** The only native application target currently supported by the packaging pipeline. */
internal enum class WinRTApplicationVariantKind {
    Jvm,
    MingwX64,
}

internal data class WinRTApplicationVariant(
    val id: String,
    val kind: WinRTApplicationVariantKind,
    val targetName: String,
    val compilationName: String,
    val sourceSetName: String,
    val buildType: String?,
    val executableName: String?,
    val runtimeIdentifier: String,
)

/** Stable resource-variant identity shared by producers and consumers with different local names. */
internal fun WinRTApplicationVariant.appxResourceTargetIdentity(): String = when (kind) {
    WinRTApplicationVariantKind.Jvm -> appxResourceTargetIdentity(kind, compilationName)
    WinRTApplicationVariantKind.MingwX64 -> appxResourceTargetIdentity(kind, compilationName)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
internal fun WinRTApplicationVariant.compilationTaskNames(project: Project): Set<String> {
    val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
        ?: return setOf("compileKotlin")
    val compilation = kotlin.targets.getByName(targetName).compilations.getByName(compilationName)
    return (compilation.allAssociatedCompilations + compilation).map { it.compileTaskProvider.name }.toSet()
}

internal fun appxResourceTargetIdentity(
    kind: WinRTApplicationVariantKind,
    compilationName: String,
): String = when (kind) {
    WinRTApplicationVariantKind.Jvm -> "jvm:${compilationName.lowercase(Locale.ROOT)}"
    WinRTApplicationVariantKind.MingwX64 -> "mingw_x64:${compilationName.lowercase(Locale.ROOT)}"
}

internal fun resolveWinRTApplicationVariant(
    project: Project,
    options: WinRTApplicationOptions,
): WinRTApplicationVariant {
    val filtered = matchingWinRTApplicationVariants(project, options)
    if (filtered.size == 1) {
        return filtered.single()
    }
    throw GradleException(
        "Kotlin/WinRT application variant selection is ambiguous for ${options.selectorDescription()}. " +
        "Set application.variantName/targetName (and nativeExecutableName for multiple executables). " +
            "Matching candidates:${System.lineSeparator()}${filtered.describe()}",
    )
}

/**
 * Returns every Kotlin application variant selected by [options]. Default application packaging
 * expands this list into one task graph per variant; selecting exactly one remains a requirement
 * only for an explicitly named application.
 */
internal fun matchingWinRTApplicationVariants(
    project: Project,
    options: WinRTApplicationOptions,
): List<WinRTApplicationVariant> {
    val candidates = discoverWinRTApplicationVariants(project)
    if (candidates.isEmpty()) {
        throw GradleException(
            "No supported Kotlin/WinRT application variant was found. " +
                "Declare a Kotlin/JVM target or a mingwX64 executable before configuring winRT.application.",
        )
    }
    val filtered = candidates.filter { candidate -> candidate.matches(options) }
    if (filtered.isNotEmpty()) {
        return filtered
    }
    throw GradleException(
        "No Kotlin/WinRT application variant matches ${options.selectorDescription()}. " +
            "Available candidates:${System.lineSeparator()}${candidates.describe()}",
    )
}

internal fun findMatchingWinRTApplicationVariants(
    project: Project,
    options: WinRTApplicationOptions,
): List<WinRTApplicationVariant> =
    discoverWinRTApplicationVariants(project).filter { candidate -> candidate.matches(options) }

internal fun jvmWinRTApplicationVariant(
    target: KotlinJvmTarget,
    compilation: KotlinCompilation<*>,
): WinRTApplicationVariant = WinRTApplicationVariant(
    id = "${target.name}:${compilation.name}",
    kind = WinRTApplicationVariantKind.Jvm,
    targetName = target.name,
    compilationName = compilation.name,
    sourceSetName = compilation.defaultSourceSet.name,
    buildType = null,
    executableName = null,
    runtimeIdentifier = currentWindowsRuntimeIdentifier(),
)

internal fun mingwWinRTApplicationVariant(
    target: KotlinNativeTarget,
    executable: Executable,
): WinRTApplicationVariant {
    val compilation = executable.compilation
    return WinRTApplicationVariant(
        id = "${target.name}:${compilation.name}:${executable.name}",
        kind = WinRTApplicationVariantKind.MingwX64,
        targetName = target.name,
        compilationName = compilation.name,
        sourceSetName = compilation.defaultSourceSet.name,
        buildType = executable.buildType.name,
        executableName = executable.name,
        runtimeIdentifier = "win-x64",
    )
}

internal fun discoverWinRTApplicationVariants(project: Project): List<WinRTApplicationVariant> {
    val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
    if (kotlin == null) {
        // A Kotlin/JVM or Java application has one logical JVM variant. The classpath is wired
        // by the existing Java/Kotlin plugin callbacks, so no synthetic native candidate is made.
        return listOf(
            WinRTApplicationVariant(
                id = "jvm:main",
                kind = WinRTApplicationVariantKind.Jvm,
                targetName = "jvm",
                compilationName = "main",
                sourceSetName = "main",
                buildType = null,
                executableName = null,
                runtimeIdentifier = currentWindowsRuntimeIdentifier(),
            ),
        )
    }

    val candidates = mutableListOf<WinRTApplicationVariant>()
    kotlin.targets.withType(KotlinJvmTarget::class.java).forEach { target ->
        target.compilations.forEach { compilation ->
            candidates += jvmWinRTApplicationVariant(target, compilation)
        }
    }
    kotlin.targets.withType(KotlinNativeTarget::class.java)
        .filter(KotlinNativeTarget::isMingwX64Target)
        .forEach { target ->
            target.binaries.withType(Executable::class.java).forEach { executable ->
                candidates += mingwWinRTApplicationVariant(target, executable)
            }
        }
    return candidates.sortedBy { it.id.lowercase() }
}

internal fun String.toSafeDirectoryName(): String =
    map { character ->
        if (character.isLetterOrDigit() || character == '-' || character == '_' || character == '.') {
            character
        } else {
            '_'
        }
    }.joinToString("").trim('_').ifBlank { "default" }

internal fun WinRTApplicationVariant.matches(options: WinRTApplicationOptions): Boolean {
    val variantSelector = options.variantName.orNull.orEmpty().trim()
    val targetSelector = options.targetName.orNull.orEmpty().trim()
    val requestedKind = options.targetKind.orNull ?: WinRTApplicationTargetKind.Auto
    val requestedBuildType = options.nativeBuildType.orNull.orEmpty().trim()
    val requestedExecutable = options.nativeExecutableName.orNull.orEmpty().trim()
    val requestedCompilation = options.compilationName.orNull.orEmpty().trim().ifBlank { "main" }
    return (variantSelector.isBlank() || id.equals(variantSelector, ignoreCase = true)) &&
        (targetSelector.isBlank() || targetName.equals(targetSelector, ignoreCase = true)) &&
        (requestedKind == WinRTApplicationTargetKind.Auto || kind.matches(requestedKind)) &&
        compilationName.equals(requestedCompilation, ignoreCase = true) &&
        (kind != WinRTApplicationVariantKind.MingwX64 ||
            requestedBuildType.isBlank() || buildType.equals(requestedBuildType, ignoreCase = true)) &&
        (requestedExecutable.isBlank() || executableName.equals(requestedExecutable, ignoreCase = true))
}

private fun WinRTApplicationOptions.selectorDescription(): String {
    val variantSelector = variantName.orNull.orEmpty().trim()
    val targetSelector = targetName.orNull.orEmpty().trim()
    val requestedKind = targetKind.orNull ?: WinRTApplicationTargetKind.Auto
    val requestedBuildType = nativeBuildType.orNull.orEmpty().trim()
    val requestedExecutable = nativeExecutableName.orNull.orEmpty().trim()
    val requestedCompilation = compilationName.orNull.orEmpty().trim().ifBlank { "main" }
    return listOfNotNull(
        variantSelector.takeIf(String::isNotBlank)?.let { "variant='$it'" },
        targetSelector.takeIf(String::isNotBlank)?.let { "target='$it'" },
        "kind=${requestedKind.name}",
        "compilation='$requestedCompilation'",
        requestedBuildType.takeIf { requestedKind != WinRTApplicationTargetKind.Jvm && it.isNotBlank() }
            ?.let { "buildType='$it'" },
        requestedExecutable.takeIf(String::isNotBlank)?.let { "executable='$it'" },
    ).joinToString(", ")
}

private fun Iterable<WinRTApplicationVariant>.describe(): String =
    joinToString(System.lineSeparator()) { candidate ->
        "- ${candidate.id} (${candidate.kind.name}, sourceSet=${candidate.sourceSetName}" +
            candidate.buildType?.let { ", buildType=$it" }.orEmpty() +
            candidate.executableName?.let { ", executable=$it" }.orEmpty() + ")"
    }

private fun WinRTApplicationVariantKind.matches(kind: WinRTApplicationTargetKind): Boolean = when (kind) {
    WinRTApplicationTargetKind.Auto -> true
    WinRTApplicationTargetKind.Jvm -> this == WinRTApplicationVariantKind.Jvm
    WinRTApplicationTargetKind.MingwX64 -> this == WinRTApplicationVariantKind.MingwX64
}
