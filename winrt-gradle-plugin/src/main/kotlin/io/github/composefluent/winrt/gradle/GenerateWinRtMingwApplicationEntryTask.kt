package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

abstract class GenerateWinRtMingwApplicationEntryTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val mainClass: Property<String>

    @TaskAction
    fun generate() {
        val mainFunction = nativeMainFunctionName(mainClass.get())
        val source = outputDirectory.get().asFile.toPath()
            .resolve("io/github/composefluent/winrt/application/WinRtMingwApplicationEntry.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, mingwApplicationEntrySource(mainFunction))
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

private fun mingwApplicationEntrySource(mainFunctionName: String): String {
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
            WinRtWindowsAppSdkBootstrap.initialize()
            userMain()
        }
    """.trimIndent() + "\n"
}
