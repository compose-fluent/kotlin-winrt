package io.github.kitectlab.winrt.gradle

import io.github.kitectlab.winrt.metadata.WinRtMetadataLoader
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtMetadataSource
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtNuGetPackageIdentity
import io.github.kitectlab.winrt.metadata.WinRtNuGetPackageResolver
import io.github.kitectlab.winrt.projections.generator.KotlinProjectionGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.asSequence

abstract class GenerateWinRtProjectionsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val metadataInputs: ListProperty<String>

    @get:Input
    abstract val includeNamespaces: ListProperty<String>

    @get:Input
    abstract val includeTypes: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val includeWindowsSdkExtensions: Property<Boolean>

    @get:Input
    abstract val nugetExecutable: Property<String>

    @get:Input
    abstract val restoreNuGetPackages: Property<Boolean>

    @get:Input
    abstract val useNuGetCliGlobalPackages: Property<Boolean>

    @get:Input
    abstract val nugetGlobalPackagesRoots: ListProperty<String>

    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @TaskAction
    fun generate() {
        val sources = metadataSources()
        val model = WinRtMetadataLoader.loadSources(sources).filterProjectionSurface(
            namespaces = includeNamespaces.get().toSet(),
            types = includeTypes.get().toSet(),
        )
        val files = KotlinProjectionGenerator(emitSupportFiles = true).generate(model)
        val outputRoot = outputDirectory.get().asFile.toPath()
        files.forEach { file ->
            val target = outputRoot.resolve(file.relativePath)
            Files.createDirectories(target.parent)
            Files.writeString(target, file.contents)
        }
    }

    private fun metadataSources(): List<WinRtMetadataSource> {
        val explicitSources = metadataInputs.get().map(WinRtMetadataSource::parse)
        val sdkSource = if (windowsSdkVersion.isPresent || includeWindowsSdkExtensions.get()) {
            listOf(
                WinRtMetadataSource.windowsSdk(
                    version = windowsSdkVersion.orNull,
                    includeExtensions = includeWindowsSdkExtensions.get(),
                ),
            )
        } else {
            emptyList()
        }
        val explicitNuGetRoots = nugetGlobalPackagesRoots.get().map(Path::of)
        val cliNuGetRoots = nugetCliGlobalPackagesRoots()
        val packageIdentities = nugetPackages.get().map(::parseNuGetPackageIdentity)
        val nugetRoots = explicitNuGetRoots + cliNuGetRoots
        val availableNuGetIdentities = packageIdentities.filter { identity ->
            isNuGetPackageAvailable(identity, nugetRoots)
        }
        val missingNuGetIdentities = packageIdentities - availableNuGetIdentities.toSet()
        val restoredPackageDirectories = if (restoreNuGetPackages.get()) {
            restoreNuGetPackages(missingNuGetIdentities)
        } else {
            emptyList()
        }
        require(missingNuGetIdentities.isEmpty() || restoredPackageDirectories.isNotEmpty()) {
            "NuGet packages are missing from the configured NuGet cache and restoreNuGetPackages is false: ${missingNuGetIdentities.joinToString()}"
        }
        val resolvedNuGetSources = availableNuGetIdentities.map { identity ->
            WinRtMetadataSource.nugetPackage(
                packageId = identity.normalizedPackageId,
                version = identity.normalizedVersion,
                globalPackagesRoots = nugetRoots,
            )
        }
        val restoredNuGetSources = restoredPackageDirectories.map(WinRtMetadataSource::nugetPackage)
        return (explicitSources + sdkSource + resolvedNuGetSources + restoredNuGetSources).ifEmpty {
            listOf(WinRtMetadataSource.windowsSdk())
        }
    }

    private fun isNuGetPackageAvailable(
        identity: WinRtNuGetPackageIdentity,
        globalPackagesRoots: List<Path>,
    ): Boolean {
        val roots = WinRtNuGetPackageResolver.globalPackagesRoots(explicitRoots = globalPackagesRoots)
        return runCatching {
            WinRtNuGetPackageResolver.packageRoot(identity, roots)
        }.isSuccess
    }

    private fun restoreNuGetPackages(
        packageIdentities: List<WinRtNuGetPackageIdentity>,
    ): List<Path> {
        if (packageIdentities.isEmpty()) {
            return emptyList()
        }

        val installRoot = temporaryDir.toPath().resolve("nuget-install")
        Files.createDirectories(installRoot)
        packageIdentities.forEach { identity ->
            runNuGetInstall(identity, installRoot)
        }
        return discoverInstalledPackages(installRoot)
    }

    private fun runNuGetInstall(
        identity: WinRtNuGetPackageIdentity,
        installRoot: Path,
    ) {
        val command = listOf(
            nugetExecutable.get(),
            "install",
            identity.normalizedPackageId,
            "-Version",
            identity.normalizedVersion,
            "-NonInteractive",
            "-OutputDirectory",
            installRoot.toString(),
        )
        val processBuilder = ProcessBuilder(command)
            .directory(installRoot.toFile())
            .redirectErrorStream(true)
        val process = runCatching { processBuilder.start() }.getOrElse { error ->
            throw GradleException("Failed to start Microsoft NuGet CLI '${nugetExecutable.get()}'.", error)
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException(
                "Microsoft NuGet CLI install failed with exit code $exitCode for $identity:$LINE_SEPARATOR$output",
            )
        }
    }

    private fun discoverInstalledPackages(installRoot: Path): List<Path> =
        Files.list(installRoot).use { stream ->
            stream.asSequence()
                .filter { it.isDirectory() }
                .sortedBy { it.name.lowercase() }
                .toList()
        }

    private fun parseNuGetPackageIdentity(spec: String): WinRtNuGetPackageIdentity {
        val separator = spec.lastIndexOf('@')
        require(separator > 0 && separator < spec.lastIndex) {
            "NuGet package must use '<id>@<version>' format: $spec"
        }
        return WinRtNuGetPackageIdentity(
            packageId = spec.substring(0, separator),
            version = spec.substring(separator + 1),
        )
    }

    private fun nugetCliGlobalPackagesRoots(): List<Path> {
        if (!useNuGetCliGlobalPackages.get()) {
            return emptyList()
        }
        return runCatching {
            val process = ProcessBuilder(nugetExecutable.get(), "locals", "global-packages", "-list")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.info("NuGet CLI global-packages lookup exited with $exitCode: $output")
                emptyList()
            } else {
                WinRtNuGetPackageResolver.parseNuGetGlobalPackagesOutput(output)
            }
        }.getOrElse { error ->
            logger.info("NuGet CLI global-packages lookup failed: ${error.message}")
            emptyList()
        }
    }
}

private val LINE_SEPARATOR: String = System.lineSeparator()

private fun WinRtMetadataModel.filterProjectionSurface(
    namespaces: Set<String>,
    types: Set<String>,
): WinRtMetadataModel =
    if (namespaces.isEmpty() && types.isEmpty()) {
        this
    } else {
        WinRtMetadataModel(
            this.namespaces.mapNotNull { namespace ->
                val namespaceTypes = namespace.types.filter { type ->
                    namespace.name in namespaces || type.qualifiedName in types
                }
                namespaceTypes.takeIf { it.isNotEmpty() }?.let { WinRtNamespace(namespace.name, it) }
            },
        ).normalized()
    }
