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
internal enum class WinAppVariantKind {
    Jvm,
    MingwX64,
}

internal data class WinAppVariant(
    val id: String,
    val kind: WinAppVariantKind,
    val targetName: String,
    val compilationName: String,
    val sourceSetName: String,
    val buildType: String?,
    val executableName: String?,
    val runtimeIdentifier: String,
)

/** Stable resource-variant identity shared by producers and consumers with different local names. */
internal fun WinAppVariant.appxResourceTargetIdentity(): String = when (kind) {
    WinAppVariantKind.Jvm -> appxResourceTargetIdentity(kind, compilationName)
    WinAppVariantKind.MingwX64 -> appxResourceTargetIdentity(kind, compilationName)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
internal fun WinAppVariant.compilationTaskNames(project: Project): Set<String> {
    val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
        ?: return setOf("compileKotlin")
    val compilation = kotlin.targets.getByName(targetName).compilations.getByName(compilationName)
    return (compilation.allAssociatedCompilations + compilation).map { it.compileTaskProvider.name }.toSet()
}

internal fun appxResourceTargetIdentity(
    kind: WinAppVariantKind,
    compilationName: String,
): String = when (kind) {
    WinAppVariantKind.Jvm -> "jvm:${compilationName.lowercase(Locale.ROOT)}"
    WinAppVariantKind.MingwX64 -> "mingw_x64:${compilationName.lowercase(Locale.ROOT)}"
}

internal fun resolveWinAppVariant(
    project: Project,
    options: NamedWinAppOptions,
): WinAppVariant {
    val candidates = discoverWinAppVariants(project)
    val variantId = options.variantName.orNull.orEmpty().trim()
    if (variantId.isBlank()) {
        throw GradleException(
            "Named WinApp '${options.name}' requires an explicit variantName. " +
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
internal val WinAppVariant.isDefaultApplicationVariant: Boolean
    get() = kind == WinAppVariantKind.MingwX64 || compilationName == KotlinCompilation.MAIN_COMPILATION_NAME

internal fun defaultWinAppVariants(project: Project): List<WinAppVariant> =
    discoverWinAppVariants(project).filter { it.isDefaultApplicationVariant }

internal fun jvmWinAppVariant(
    target: KotlinJvmTarget,
    compilation: KotlinCompilation<*>,
): WinAppVariant = WinAppVariant(
    id = "${target.name}:${compilation.name}",
    kind = WinAppVariantKind.Jvm,
    targetName = target.name,
    compilationName = compilation.name,
    sourceSetName = compilation.defaultSourceSet.name,
    buildType = null,
    executableName = null,
    runtimeIdentifier = currentWindowsRuntimeIdentifier(),
)

internal fun mingwWinAppVariant(
    target: KotlinNativeTarget,
    executable: Executable,
): WinAppVariant {
    val compilation = executable.compilation
    return WinAppVariant(
        id = "${target.name}:${compilation.name}:${executable.name}",
        kind = WinAppVariantKind.MingwX64,
        targetName = target.name,
        compilationName = compilation.name,
        sourceSetName = compilation.defaultSourceSet.name,
        buildType = executable.buildType.name,
        executableName = executable.name,
        runtimeIdentifier = "win-x64",
    )
}

internal fun discoverWinAppVariants(project: Project): List<WinAppVariant> {
    val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
    if (kotlin == null) {
        // A Kotlin/JVM or Java application has one logical JVM variant. The classpath is wired
        // by the existing Java/Kotlin plugin callbacks, so no synthetic native candidate is made.
        return listOf(
            WinAppVariant(
                id = "jvm:main",
                kind = WinAppVariantKind.Jvm,
                targetName = "jvm",
                compilationName = "main",
                sourceSetName = "main",
                buildType = null,
                executableName = null,
                runtimeIdentifier = currentWindowsRuntimeIdentifier(),
            ),
        )
    }

    val candidates = mutableListOf<WinAppVariant>()
    kotlin.targets.withType(KotlinJvmTarget::class.java).forEach { target ->
        target.compilations.forEach { compilation ->
            candidates += jvmWinAppVariant(target, compilation)
        }
    }
    kotlin.targets.withType(KotlinNativeTarget::class.java)
        .filter(KotlinNativeTarget::isMingwX64Target)
        .forEach { target ->
            target.binaries.withType(Executable::class.java).forEach { executable ->
                candidates += mingwWinAppVariant(target, executable)
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

private fun Iterable<WinAppVariant>.describe(): String =
    joinToString(System.lineSeparator()) { candidate ->
        "- ${candidate.id} (${candidate.kind.name}, sourceSet=${candidate.sourceSetName}" +
            candidate.buildType?.let { ", buildType=$it" }.orEmpty() +
            candidate.executableName?.let { ", executable=$it" }.orEmpty() + ")"
    }
