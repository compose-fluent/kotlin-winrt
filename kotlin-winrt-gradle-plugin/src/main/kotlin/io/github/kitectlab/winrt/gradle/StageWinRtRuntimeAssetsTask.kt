package io.github.kitectlab.winrt.gradle

import io.github.kitectlab.winrt.metadata.WinRtNuGetPackageIdentity
import io.github.kitectlab.winrt.metadata.WinRtNuGetPackageResolver
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

abstract class StageWinRtRuntimeAssetsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @get:Input
    abstract val runtimeAssets: ListProperty<String>

    @get:Input
    abstract val nugetGlobalPackagesRoots: ListProperty<String>

    @get:Input
    abstract val useNuGetCliGlobalPackages: Property<Boolean>

    @get:Input
    abstract val nugetExecutable: Property<String>

    @get:Input
    abstract val nugetCliVersion: Property<String>

    @get:Internal
    abstract val nugetCliCacheDirectory: DirectoryProperty

    @get:Input
    abstract val restoreNuGetPackages: Property<Boolean>

    @get:Input
    abstract val runtimeIdentifier: org.gradle.api.provider.Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredMetadataFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredHostManifestFiles: ConfigurableFileCollection

    @TaskAction
    fun stage() {
        val outputRoot = outputDirectory.get().asFile.toPath()
        Files.createDirectories(outputRoot)
        (runtimeAssets.get() + dependencyIdentityFiles.files.flatMap(::readRuntimeAssets))
            .map(Path::of)
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (source.isRegularFile()) {
                    copyFile(source, outputRoot.resolve(source.name))
                }
            }
        (authoredMetadataFiles.files.map { it.absolutePath } + dependencyIdentityFiles.files.flatMap(::readAuthoredMetadata))
            .map(Path::of)
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (source.isRegularFile()) {
                    copyFile(source, outputRoot.resolve(source.name))
                }
            }
        (authoredHostManifestFiles.files.map { it.absolutePath } + dependencyIdentityFiles.files.flatMap(::readAuthoredHostManifests))
            .map(Path::of)
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (source.isRegularFile()) {
                    copyFile(source, outputRoot.resolve(source.name))
                }
            }
        val identities = (nugetPackages.get() + dependencyIdentityFiles.files.flatMap(::readNuGetPackages))
            .map(::parseNuGetPackageIdentity)
            .distinctBy { "${it.normalizedPackageId.lowercase()}:${it.normalizedVersion.lowercase()}" }
        val roots = WinRtNuGetPackageResolver.globalPackagesRoots(
            explicitRoots = nugetGlobalPackagesRoots.get().map(Path::of),
            nugetLocalsOutput = nugetCliGlobalPackagesOutput(),
        )
        val resolvedPackages = resolveNuGetPackages(identities, roots)
        val rid = runtimeIdentifier.get()
        resolvedPackages.forEach { resolved ->
            stageTopLevelDlls(resolved.packageRoot, outputRoot)
            stageRuntimeNativeDlls(resolved.packageRoot.resolve("runtimes").resolve(rid).resolve("native"), outputRoot)
            if (resolved.identity.isWindowsAppSdkPackage()) {
                stageWindowsAppSdkVersionInfo(resolved.packageRoot, outputRoot)
                stageWindowsAppSdkLiftedRegistrations(resolved.packageRoot, outputRoot)
                stageWindowsAppSdkFrameworkAssets(
                    resolved.packageRoot.resolve("runtimes-framework").resolve(rid).resolve("native"),
                    outputRoot,
                )
            }
        }
        stageResourcesPriAlias(outputRoot)
    }

    private fun resolveNuGetPackages(
        identities: List<WinRtNuGetPackageIdentity>,
        roots: List<Path>,
    ): List<io.github.kitectlab.winrt.metadata.WinRtNuGetResolvedPackage> {
        val resolvedFromRoots = mutableListOf<io.github.kitectlab.winrt.metadata.WinRtNuGetResolvedPackage>()
        val missingIdentities = mutableListOf<WinRtNuGetPackageIdentity>()
        identities.forEach { identity ->
            runCatching {
                WinRtNuGetPackageResolver.resolveClosure(identity, roots)
            }.onSuccess { resolvedFromRoots += it }
                .onFailure { missingIdentities += identity }
        }
        val restoredPackages = if (restoreNuGetPackages.get() && missingIdentities.isNotEmpty()) {
            restoreNuGetPackages(missingIdentities).map(WinRtNuGetPackageResolver::resolvePackageRoot)
        } else {
            emptyList()
        }
        require(missingIdentities.isEmpty() || restoredPackages.isNotEmpty()) {
            "NuGet packages are missing from the configured NuGet cache and restoreNuGetPackages is false: ${missingIdentities.joinToString()}"
        }
        return (resolvedFromRoots + restoredPackages)
            .distinctBy { it.packageRoot.toAbsolutePath().normalize().toString().lowercase() }
    }

    private fun restoreNuGetPackages(
        identities: List<WinRtNuGetPackageIdentity>,
    ): List<Path> {
        val installRoot = temporaryDir.toPath().resolve("nuget-install")
        Files.createDirectories(installRoot)
        identities.forEach { identity ->
            nuGetCli().run(
                arguments = listOf(
                    "install",
                    identity.normalizedPackageId,
                    "-Version",
                    identity.normalizedVersion,
                    "-NonInteractive",
                    "-OutputDirectory",
                    installRoot.toString(),
                ),
                workingDirectory = installRoot,
                description = "install $identity",
            )
        }
        return Files.list(installRoot).use { stream ->
            stream.asSequence()
                .filter { it.isDirectory() }
                .sortedBy { it.name.lowercase() }
                .toList()
        }
    }

    private fun stageTopLevelDlls(packageRoot: Path, outputRoot: Path) {
        Files.list(packageRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".dll", ignoreCase = true) }
                .forEach { source -> copyFile(source, outputRoot.resolve(source.name)) }
        }
    }

    private fun stageRuntimeNativeDlls(nativeRoot: Path, outputRoot: Path) {
        if (!nativeRoot.isDirectory()) {
            return
        }
        Files.walk(nativeRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".dll", ignoreCase = true) }
                .forEach { source -> copyFile(source, outputRoot.resolve(source.name)) }
        }
    }

    private fun stageWindowsAppSdkFrameworkAssets(nativeRoot: Path, outputRoot: Path) {
        if (!nativeRoot.isDirectory()) {
            return
        }
        Files.walk(nativeRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() }
                .forEach { source -> copyFile(source, outputRoot.resolve(source.relativeTo(nativeRoot))) }
        }
    }

    private fun stageWindowsAppSdkVersionInfo(packageRoot: Path, outputRoot: Path) {
        val versionInfo = packageRoot.resolve("include").resolve("WindowsAppSDK-VersionInfo.h")
        if (versionInfo.isRegularFile()) {
            copyFile(versionInfo, outputRoot.resolve("include").resolve(versionInfo.name))
        }
    }

    private fun stageWindowsAppSdkLiftedRegistrations(packageRoot: Path, outputRoot: Path) {
        Files.walk(packageRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.equals("LiftedWinRTClassRegistrations.xml", ignoreCase = true) }
                .forEach { source -> copyFile(source, outputRoot.resolve(source.name)) }
        }
    }

    private fun stageResourcesPriAlias(outputRoot: Path) {
        val controlsPri = outputRoot.resolve("Microsoft.UI.Xaml.Controls.pri")
        if (controlsPri.isRegularFile()) {
            copyFile(controlsPri, outputRoot.resolve("resources.pri"))
        }
    }

    private fun copyFile(source: Path, target: Path) {
        Files.createDirectories(target.parent)
        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun nugetCliGlobalPackagesOutput(): String? {
        if (!useNuGetCliGlobalPackages.get()) {
            return null
        }
        return runCatching {
            nuGetCli().run(
                arguments = listOf("locals", "global-packages", "-list"),
                description = "locate global-packages",
            ).output
        }.getOrElse { error ->
            logger.info("NuGet CLI global-packages lookup failed: ${error.message}")
            null
        }
    }

    private fun nuGetCli(): NuGetCliSupport = NuGetCliSupport(
        executable = nugetExecutable.get(),
        cliVersion = nugetCliVersion.get(),
        cliCacheDirectory = nugetCliCacheDirectory.get().asFile.toPath(),
        logger = logger,
    )
}

private fun WinRtNuGetPackageIdentity.isWindowsAppSdkPackage(): Boolean =
    normalizedPackageId.equals("Microsoft.WindowsAppSDK", ignoreCase = true) ||
        normalizedPackageId.startsWith("Microsoft.WindowsAppSDK.", ignoreCase = true)

internal fun currentWindowsRuntimeIdentifier(): String {
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        "aarch64" in arch || "arm64" in arch -> "win-arm64"
        "x86" in arch && "64" !in arch -> "win-x86"
        else -> "win-x64"
    }
}

internal fun parseNuGetPackageIdentity(spec: String): WinRtNuGetPackageIdentity {
    val separator = spec.lastIndexOf('@')
    require(separator > 0 && separator < spec.lastIndex) {
        "NuGet package must use '<id>@<version>' format: $spec"
    }
    return WinRtNuGetPackageIdentity(
        packageId = spec.substring(0, separator),
        version = spec.substring(separator + 1),
    )
}

internal fun readNuGetPackages(identityFile: java.io.File): List<String> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val match = Regex(""""nugetPackages"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(content) ?: return emptyList()
    return readJsonStringArray(match.groupValues[1])
}

internal fun readRuntimeAssets(identityFile: java.io.File): List<String> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val match = Regex(""""runtimeAssets"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(content) ?: return emptyList()
    return readJsonStringArray(match.groupValues[1])
}

internal fun readAuthoredMetadata(identityFile: java.io.File): List<String> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val match = Regex(""""authoredMetadata"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(content) ?: return emptyList()
    return readJsonStringArray(match.groupValues[1])
}

internal fun readAuthoredHostManifests(identityFile: java.io.File): List<String> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val match = Regex(""""authoredHostManifests"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(content) ?: return emptyList()
    return readJsonStringArray(match.groupValues[1])
}

private fun readJsonStringArray(content: String): List<String> =
    Regex(""""((?:\\.|[^"\\])*)"""")
        .findAll(content)
        .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
        .toList()
