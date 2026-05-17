package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRtNuGetPackageIdentity
import io.github.composefluent.winrt.metadata.WinRtNuGetPackageResolver
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

    @get:Input
    abstract val generateProjectPri: Property<Boolean>

    @get:Input
    abstract val projectPriIndexName: Property<String>

    @get:Input
    abstract val projectPriFallbackIndexName: Property<String>

    @get:Input
    abstract val projectPriInitialPath: Property<String>

    @get:Input
    abstract val projectPriDefaultLanguage: Property<String>

    @get:Input
    abstract val projectPriDefaultQualifiers: ListProperty<String>

    @get:Input
    abstract val enableDefaultProjectPriResources: Property<Boolean>

    @get:Input
    abstract val makePriExecutable: Property<String>

    @get:Input
    abstract val windowsSdkVersion: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appxManifestFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectPriResourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectPriLayoutFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectPriContentFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultProjectPriResourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultProjectPriLayoutFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultProjectPriContentFiles: ConfigurableFileCollection

    @get:Internal
    abstract val defaultProjectPriResourceRoot: DirectoryProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredMetadataFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredHostManifestFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredTargetArtifactFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredHostDllFiles: ConfigurableFileCollection

    init {
        generateProjectPri.convention(true)
        projectPriIndexName.convention("")
        projectPriFallbackIndexName.convention("Application")
        projectPriInitialPath.convention("")
        projectPriDefaultLanguage.convention("")
        projectPriDefaultQualifiers.convention(listOf("scale-200", "contrast-standard"))
        enableDefaultProjectPriResources.convention(true)
        makePriExecutable.convention("")
        windowsSdkVersion.convention("")
    }

    @TaskAction
    fun stage() {
        val outputRoot = outputDirectory.get().asFile.toPath()
        GradleFileOperations.cleanDirectory(outputRoot)
        Files.createDirectories(outputRoot)
        (runtimeAssets.get() + dependencyIdentityFiles.files.flatMap(::readRuntimeAssets))
            .map(Path::of)
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (source.isRegularFile()) {
                    GradleFileOperations.copyFile(source, outputRoot.resolve(source.name))
                }
            }
        (authoredMetadataFiles.files.map { it.absolutePath } + dependencyIdentityFiles.files.flatMap(::readAuthoredMetadata))
            .map(Path::of)
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (source.isRegularFile()) {
                    GradleFileOperations.copyFile(source, outputRoot.resolve(source.name))
                }
            }
        (authoredHostManifestFiles.files.map { it.absolutePath } + dependencyIdentityFiles.files.flatMap(::readAuthoredHostManifests))
            .map(Path::of)
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (source.isRegularFile()) {
                    GradleFileOperations.copyFile(source, outputRoot.resolve(source.name))
                }
            }
        stageAuthoringHostRuntimeConfigs(
            sources = authoredHostManifestFiles.files + dependencyIdentityFiles.files.flatMap(::readAuthoredHostManifests).map { java.io.File(it) },
            outputRoot = outputRoot,
        )
        (authoredTargetArtifactFiles.files.map { it.absolutePath } + dependencyIdentityFiles.files.flatMap(::readAuthoredTargetArtifacts))
            .map(Path::of)
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (source.isRegularFile()) {
                    GradleFileOperations.copyFile(source, outputRoot.resolve(source.name))
                }
            }
        authoredHostDllFiles.files
            .asSequence()
            .map { it.toPath() }
            .filter { it.isRegularFile() && it.name.endsWith(".dll", ignoreCase = true) }
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source -> GradleFileOperations.copyFile(source, outputRoot.resolve(source.name)) }
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
            if (resolved.identity.isWindowsAppSdkRootPackage()) {
                stageWindowsAppSdkVersionInfo(resolved.packageRoot, outputRoot)
            }
            stageLiftedRegistrations(resolved.identity, resolved.packageRoot, outputRoot)
            stageFrameworkNativeAssets(
                resolved.packageRoot.resolve("runtimes-framework").resolve(rid).resolve("native"),
                outputRoot,
            )
        }
        generateProjectPri(outputRoot)
    }

    private fun resolveNuGetPackages(
        identities: List<WinRtNuGetPackageIdentity>,
        roots: List<Path>,
    ): List<io.github.composefluent.winrt.metadata.WinRtNuGetResolvedPackage> {
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

    private fun stageTopLevelDlls(packageRoot: Path, outputRoot: Path) {
        Files.list(packageRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".dll", ignoreCase = true) }
                .forEach { source -> GradleFileOperations.copyFile(source, outputRoot.resolve(source.name)) }
        }
    }

    private fun stageRuntimeNativeDlls(nativeRoot: Path, outputRoot: Path) {
        if (!nativeRoot.isDirectory()) {
            return
        }
        Files.walk(nativeRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".dll", ignoreCase = true) }
                .forEach { source -> GradleFileOperations.copyFile(source, outputRoot.resolve(source.name)) }
        }
    }

    private fun stageFrameworkNativeAssets(nativeRoot: Path, outputRoot: Path) {
        if (!nativeRoot.isDirectory()) {
            return
        }
        Files.walk(nativeRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() }
                .forEach { source -> GradleFileOperations.copyFile(source, outputRoot.resolve(source.relativeTo(nativeRoot))) }
        }
    }

    private fun stageWindowsAppSdkVersionInfo(packageRoot: Path, outputRoot: Path) {
        val versionInfo = packageRoot.resolve("include").resolve("WindowsAppSDK-VersionInfo.h")
        if (versionInfo.isRegularFile()) {
            GradleFileOperations.copyFile(versionInfo, outputRoot.resolve("include").resolve(versionInfo.name))
        }
    }

    private fun stageLiftedRegistrations(
        identity: WinRtNuGetPackageIdentity,
        packageRoot: Path,
        outputRoot: Path,
    ) {
        Files.walk(packageRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.equals("LiftedWinRTClassRegistrations.xml", ignoreCase = true) }
                .forEach { source ->
                    GradleFileOperations.copyFile(
                        source,
                        outputRoot
                            .resolve("registrations")
                            .resolve(identity.normalizedPackageId)
                            .resolve(source.relativeTo(packageRoot)),
                    )
                }
        }
    }

    private fun generateProjectPri(outputRoot: Path) {
        if (!generateProjectPri.get() || !isWindowsHost()) {
            return
        }
        val inputPris = Files.walk(outputRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".pri", ignoreCase = true) }
                .filterNot { it.name.equals("resources.pri", ignoreCase = true) }
                .filterNot { it.name.startsWith("resources.language-", ignoreCase = true) }
                .sorted()
                .toList()
        }
        val makePri = discoverMakePriExecutable() ?: run {
            logger.warn("Skipping application PRI generation because makepri.exe was not found.")
            return
        }
        val projectPriRoot = temporaryDir.toPath().resolve("project-pri")
        GradleFileOperations.cleanDirectory(projectPriRoot)
        Files.createDirectories(projectPriRoot)
        val copiedProjectPriItems = ProjectPriInputStager(
            projectPriRoot = projectPriRoot,
            projectPriInitialPath = projectPriInitialPath.get(),
            defaultProjectResourceRoot = defaultProjectPriResourceRoot.orNull?.asFile?.toPath(),
            targetPaths = emptyMap(),
            excludedFromBuildPaths = emptySet(),
        ).stage(
            componentPriFiles = inputPris,
            componentPriBaseRoot = outputRoot,
            explicitResourceFiles = projectPriResourceFiles.files.map { it.toPath() },
            explicitLayoutFiles = projectPriLayoutFiles.files.map { it.toPath() },
            explicitContentFiles = projectPriContentFiles.files.map { it.toPath() },
            explicitEmbedFiles = emptyList(),
            defaultResourceFiles = defaultProjectPriResourceFiles.files.map { it.toPath() },
            defaultLayoutFiles = defaultProjectPriLayoutFiles.files.map { it.toPath() },
            defaultContentFiles = defaultProjectPriContentFiles.files.map { it.toPath() },
            includeDefaultProjectResources = enableDefaultProjectPriResources.get(),
        )
        if (copiedProjectPriItems.isEmpty()) {
            return
        }
        ApplicationPackagePayloadWriter.copyPackagePayloads(projectPriRoot, outputRoot, copiedProjectPriItems)
        val configRoot = temporaryDir.toPath().resolve("project-pri-config")
        ProjectPriGenerator.generateApplicationPri(
            makePri,
            outputRoot,
            projectPriRoot,
            configRoot,
            projectPriIndexName(),
            ProjectPriManifestSupport.fullIndexDefaultQualifiers(
                ProjectPriManifestSupport.defaultLanguage(projectPriDefaultLanguage.get(), appxManifestFiles.files),
                projectPriDefaultQualifiers.get(),
            ),
            copiedProjectPriItems,
            logger,
        )
    }

    private fun projectPriIndexName(): String =
        ProjectPriManifestSupport.indexName(projectPriIndexName.get(), projectPriFallbackIndexName.get(), appxManifestFiles.files)

    private fun discoverMakePriExecutable(): Path? {
        return ProjectPriToolResolver.makePriExecutable(makePriExecutable.get(), windowsSdkVersion.get(), runtimeIdentifier.get())
    }

    private fun stageAuthoringHostRuntimeConfigs(
        sources: Collection<java.io.File>,
        outputRoot: Path,
    ) {
        sources
            .mapNotNull { source -> readAuthoringHostRuntimeConfig(source) }
            .groupBy { it.assemblyName }
            .forEach { (assemblyName, configs) ->
                val activatableClasses = configs
                    .flatMap { it.activatableClasses.entries }
                    .associate { it.key to it.value }
                if (activatableClasses.isNotEmpty()) {
                    writeAuthoringHostRuntimeConfig(
                        output = outputRoot.resolve("$assemblyName.runtimeconfig.json"),
                        activatableClasses = activatableClasses,
                    )
                }
            }
    }

    private fun readAuthoringHostRuntimeConfig(source: java.io.File): AuthoringHostRuntimeConfig? {
        val content = source.takeIf { it.isFile }?.readText().orEmpty()
        val assemblyName = readJsonString(content, "assemblyName") ?: return null
        if (assemblyName.isBlank()) {
            return null
        }
        val targetArtifact = readJsonString(content, "targetArtifact").orEmpty()
        val explicitTargets = readJsonStringMap(content, "activatableClassTargets")
        val defaultTargets = readJsonStringArray(content, "activatableClasses")
            .filter { it.isNotBlank() && targetArtifact.isNotBlank() }
            .associateWith { targetArtifact }
        val activatableClasses = defaultTargets + explicitTargets
        return AuthoringHostRuntimeConfig(
            assemblyName = assemblyName,
            activatableClasses = activatableClasses,
        )
    }

    private fun writeAuthoringHostRuntimeConfig(
        output: Path,
        activatableClasses: Map<String, String>,
    ) {
        Files.createDirectories(output.parent)
        Files.writeString(
            output,
            buildString {
                appendLine("{")
                appendLine("  \"schemaVersion\": 1,")
                appendLine("  \"model\": \"jvm-authoring-host-runtime-config\",")
                appendLine("  \"activatableClasses\": ${activatableClasses.toJsonObject()}")
                appendLine("}")
            },
        )
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

private data class AuthoringHostRuntimeConfig(
    val assemblyName: String,
    val activatableClasses: Map<String, String>,
)

private fun WinRtNuGetPackageIdentity.isWindowsAppSdkRootPackage(): Boolean =
    normalizedPackageId.equals("Microsoft.WindowsAppSDK", ignoreCase = true)

internal fun isWindowsHost(): Boolean =
    System.getProperty("os.name").contains("Windows", ignoreCase = true)

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

internal fun readAuthoredTargetArtifacts(identityFile: java.io.File): List<String> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val match = Regex(""""authoredTargetArtifacts"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(content) ?: return emptyList()
    return readJsonStringArray(match.groupValues[1])
}

private fun readJsonString(content: String, name: String): String? =
    Regex(""""${Regex.escape(name)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .find(content)
        ?.groupValues
        ?.get(1)
        ?.decodeJsonString()

private fun readJsonStringMap(content: String, name: String): Map<String, String> {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyMap()
    return Regex(""""((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .findAll(match.groupValues[1])
        .associate { it.groupValues[1].decodeJsonString() to it.groupValues[2].decodeJsonString() }
}

private fun readJsonStringArray(content: String, name: String): List<String> {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyList()
    return readJsonStringArray(match.groupValues[1])
}

private fun readJsonStringArray(content: String): List<String> =
    Regex(""""((?:\\.|[^"\\])*)"""")
        .findAll(content)
        .map { it.groupValues[1].decodeJsonString() }
        .toList()

private fun String.decodeJsonString(): String =
    replace("\\\"", "\"").replace("\\\\", "\\")

private fun Map<String, String>.toJsonObject(): String =
    entries
        .toList()
        .sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${key.toJsonString()}: ${value.toJsonString()}"
        }
