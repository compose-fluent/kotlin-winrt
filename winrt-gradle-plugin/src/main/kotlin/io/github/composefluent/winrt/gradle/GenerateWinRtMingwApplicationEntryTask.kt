package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

abstract class GenerateWinRtMingwApplicationEntryTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val legacyOutputDirectories: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val mainClass: Property<String>

    @get:Input
    abstract val packageMode: Property<String>

    @TaskAction
    fun generate() {
        val outputRoot = outputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        legacyOutputDirectories.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .filterNot(outputRoot::equals)
            .forEach(GradleFileOperations::deleteDirectory)
        GradleFileOperations.cleanDirectory(outputRoot)
        val mainClassValue = mainClass.orNull.orEmpty()
        if (mainClassValue.isBlank()) {
            return
        }
        val mainFunction = nativeMainFunctionName(mainClassValue)
        val unpackaged = packageMode.get() == WinRtApplicationPackageMode.Unpackaged.name
        val source = outputRoot
            .resolve("io/github/composefluent/winrt/application/WinRtMingwApplicationEntry.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, mingwApplicationEntrySource(mainFunction, unpackaged))
    }
}

internal const val KOTLIN_WINRT_MINGW_APPLICATION_ENTRY_POINT: String =
    "io.github.composefluent.winrt.application.main"

private fun nativeMainFunctionName(mainClass: String): String {
    val normalized = mainClass.trim()
    if (normalized.isBlank()) {
        throw GradleException("Kotlin/WinRT mingw application entry requires an application mainClass.")
    }
    if (normalized.endsWith(".MainKt")) {
        return normalized.removeSuffix(".MainKt") + ".main"
    }
    return normalized
}

private fun mingwApplicationEntrySource(mainFunctionName: String, unpackaged: Boolean): String {
    val userMainPackage = mainFunctionName.substringBeforeLast('.', missingDelimiterValue = "")
    val userMainName = mainFunctionName.substringAfterLast('.')
    if (userMainPackage.isBlank() || userMainName.isBlank()) {
        throw GradleException(
            "Kotlin/WinRT mingw application main '$mainFunctionName' must be a fully qualified top-level function.",
        )
    }
    return """
        package io.github.composefluent.winrt.application

        import io.github.composefluent.winrt.runtime.WinRtWindowsAppSdkBootstrap
        import $userMainPackage.$userMainName as userMain

        fun main() {
            WinRtWindowsAppSdkBootstrap.initializeApplicationHost(unpackaged = $unpackaged).use {
                userMain()
            }
        }
    """.trimIndent() + "\n"
}
