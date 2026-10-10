package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/** Publishes WinApp's resolved package roots for runtime staging; never restores packages. */
@DisableCachingByDefault(because = "Records machine-specific package paths from the WinApp restore")
abstract class ResolveWinRTRuntimeNuGetPackagesTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val winAppRestoreLockFiles: ConfigurableFileCollection

    @TaskAction
    fun resolve() {
        val specs = nugetPackages.get() + dependencyIdentityFiles.files.flatMap(::readNuGetPackages)
        val roots = readWinAppRestoredPackageRoots(
            projectionRestoreLockFiles(winAppRestoreLockFiles.files), specs,
        )
        writeResolvedRuntimeNuGetPackages(outputFile.get().asFile.toPath(), roots)
    }
}
internal fun writeResolvedRuntimeNuGetPackages(output: Path, packageRoots: List<Path>) {
    Files.createDirectories(output.parent)
    val normalizedRoots = packageRoots
        .map { it.toAbsolutePath().normalize().toString() }
        .distinctBy { it.lowercase() }
    Files.writeString(
        output,
        buildString {
            appendLine("{")
            appendLine("  \"model\": \"winrt-runtime-nuget-packages\",")
            appendLine("  \"packageRoots\": ${normalizedRoots.toJsonArray()}")
            appendLine("}")
        },
    )
}

internal fun readResolvedRuntimeNuGetPackageRoots(manifestFile: java.io.File): List<String> {
    val content = manifestFile.takeIf { it.isFile }?.readText().orEmpty()
    require(readJsonString(content, "model") == "winrt-runtime-nuget-packages") {
        "Resolved WinRT runtime NuGet package manifest '${manifestFile.absolutePath}' has malformed model."
    }
    return readRequiredJsonStringArrayField(content, "packageRoots", manifestFile)
}

private fun readJsonString(content: String, name: String): String? =
    Regex(""""${Regex.escape(name)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .find(content)
        ?.groupValues
        ?.get(1)
        ?.decodeJsonString()

private fun readRequiredJsonStringArrayField(
    content: String,
    name: String,
    manifestFile: java.io.File,
): List<String> {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        .find(content)
        ?: throw IllegalArgumentException(
            "Resolved WinRT runtime NuGet package manifest '${manifestFile.absolutePath}' is missing $name.",
    )
    return readJsonStringArray(match.groupValues[1])
}

private fun readJsonStringArray(content: String): List<String> =
    Regex(""""((?:\\.|[^"\\])*)"""")
        .findAll(content)
        .map { it.groupValues[1].decodeJsonString() }
        .toList()

private fun String.decodeJsonString(): String =
    replace("\\\"", "\"").replace("\\\\", "\\")
