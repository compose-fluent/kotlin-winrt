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
    options: NamedWinRTApplicationOptions,
): WinRTApplicationVariant {
    val candidates = discoverWinRTApplicationVariants(project)
    val variantId = options.variantName.orNull.orEmpty().trim()
    if (variantId.isBlank()) {
        throw GradleException(
            "Named Kotlin/WinRT application '${options.name}' requires an explicit variantName. " +
                "Use a full Kotlin variant ID, such as 'desktop:main' or 'mingwX64:main:releaseExecutable'. " +
                "Available candidates:${System.lineSeparator()}${candidates.describe()}",
        )
    }
    return candidates.singleOrNull { candidate -> candidate.id.equals(variantId, ignoreCase = true) }
        ?: throw GradleException(
            "No unique Kotlin/WinRT variant '$variantId' exists for application '${options.name}'. " +
                "Available candidates:${System.lineSeparator()}${candidates.describe()}",
        )
}

// CsWinRT's application projects leave Configuration/Platform ownership to the build system
// (.cswinrt/src/Samples/AuthoringDemo/WinUI3CppApp). Here KGP owns targets and executable build types.
internal val WinRTApplicationVariant.isDefaultApplicationVariant: Boolean
    get() = kind == WinRTApplicationVariantKind.MingwX64 || compilationName == KotlinCompilation.MAIN_COMPILATION_NAME

internal fun defaultWinRTApplicationVariants(project: Project): List<WinRTApplicationVariant> =
    discoverWinRTApplicationVariants(project).filter { it.isDefaultApplicationVariant }

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

private fun Iterable<WinRTApplicationVariant>.describe(): String =
    joinToString(System.lineSeparator()) { candidate ->
        "- ${candidate.id} (${candidate.kind.name}, sourceSet=${candidate.sourceSetName}" +
            candidate.buildType?.let { ", buildType=$it" }.orEmpty() +
            candidate.executableName?.let { ", executable=$it" }.orEmpty() + ")"
    }
