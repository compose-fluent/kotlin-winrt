package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRtNuGetPackageIdentity
import io.github.composefluent.winrt.metadata.WinRtNuGetPackageResolver
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.asSequence

abstract class ResolveWinRtRuntimeNuGetPackagesTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val existingPackageContentFiles: ConfigurableFileCollection

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

    @TaskAction
    fun resolve() {
        val identities = (nugetPackages.get() + dependencyIdentityFiles.files.flatMap(::readNuGetPackages))
            .map(::parseNuGetPackageIdentity)
            .distinctBy { it.nuGetIdentityKey() }
        val resolvedPackages = resolveNuGetPackagesFromModeledInputs(
            identities = identities,
            modeledPackageRoots = existingPackageContentFiles.files.map { it.toPath() },
        ) ?: resolveFromConfiguredRoots(identities)
        writeResolvedRuntimeNuGetPackages(outputFile.get().asFile.toPath(), resolvedPackages.map { it.packageRoot })
    }

    private fun resolveFromConfiguredRoots(
        identities: List<WinRtNuGetPackageIdentity>,
    ): List<io.github.composefluent.winrt.metadata.WinRtNuGetResolvedPackage> {
        val roots = WinRtNuGetPackageResolver.globalPackagesRoots(
            explicitRoots = nugetGlobalPackagesRoots.get().map(Path::of),
            nugetLocalsOutput = nugetCliGlobalPackagesOutput(),
        )
        val resolvedFromRoots = mutableListOf<io.github.composefluent.winrt.metadata.WinRtNuGetResolvedPackage>()
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

internal fun resolveNuGetPackagesFromModeledInputs(
    identities: List<WinRtNuGetPackageIdentity>,
    modeledPackageRoots: List<Path>,
): List<io.github.composefluent.winrt.metadata.WinRtNuGetResolvedPackage>? {
    if (identities.isEmpty() || modeledPackageRoots.isEmpty()) {
        return null
    }
    val packagesByIdentity = modeledPackageRoots
        .asSequence()
        .filter { it.isDirectory() }
        .mapNotNull { root ->
            runCatching { WinRtNuGetPackageResolver.resolvePackageRoot(root) }.getOrNull()
        }
        .associateBy { it.identity.nuGetIdentityKey() }
    if (packagesByIdentity.isEmpty()) {
        return null
    }
    val queue = ArrayDeque(identities)
    val visited = linkedSetOf<String>()
    val resolved = mutableListOf<io.github.composefluent.winrt.metadata.WinRtNuGetResolvedPackage>()
    while (queue.isNotEmpty()) {
        val identity = queue.removeFirst()
        val key = identity.nuGetIdentityKey()
        if (!visited.add(key)) {
            continue
        }
        val resolvedPackage = packagesByIdentity[key] ?: return null
        resolved += resolvedPackage
        resolvedPackage.dependencies.forEach(queue::add)
    }
    return resolved
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
    return readJsonStringArrayField(content, "packageRoots")
}

private fun WinRtNuGetPackageIdentity.nuGetIdentityKey(): String =
    "${normalizedPackageId.lowercase()}:${normalizedVersion.lowercase()}"
