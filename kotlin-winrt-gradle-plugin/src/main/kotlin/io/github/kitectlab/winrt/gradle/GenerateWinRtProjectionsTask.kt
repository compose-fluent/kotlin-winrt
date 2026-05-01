package io.github.kitectlab.winrt.gradle

import io.github.kitectlab.winrt.metadata.WinRtMetadataLoader
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionContext
import io.github.kitectlab.winrt.metadata.WinRtMetadataSource
import io.github.kitectlab.winrt.metadata.WinRtNuGetPackageIdentity
import io.github.kitectlab.winrt.metadata.WinRtNuGetPackageResolver
import io.github.kitectlab.winrt.metadata.filterProjectionSurface
import io.github.kitectlab.winrt.projections.generator.KotlinProjectionGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.process.ExecOperations
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
import javax.inject.Inject
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.asSequence

abstract class GenerateWinRtProjectionsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val metadataInputs: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataInputFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    @get:Input
    abstract val includeNamespaces: ListProperty<String>

    @get:Input
    abstract val includeTypes: ListProperty<String>

    @get:Input
    abstract val excludeNamespaces: ListProperty<String>

    @get:Input
    abstract val excludeTypes: ListProperty<String>

    @get:Input
    abstract val additionExcludeNamespaces: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val includeWindowsSdkExtensions: Property<Boolean>

    @get:Input
    abstract val nugetExecutable: Property<String>

    @get:Input
    abstract val nugetCliVersion: Property<String>

    @get:Internal
    abstract val nugetCliCacheDirectory: DirectoryProperty

    @get:Input
    abstract val restoreNuGetPackages: Property<Boolean>

    @get:Input
    abstract val useNuGetCliGlobalPackages: Property<Boolean>

    @get:Input
    abstract val nugetGlobalPackagesRoots: ListProperty<String>

    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @get:Internal
    abstract val authoringScannerClasspath: ConfigurableFileCollection

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        val sources = metadataSources()
        val baseModel = WinRtMetadataLoader.loadSources(sources).filterProjectionSurface(
            namespaces = includeNamespaces.get().toSet(),
            types = includeTypes.get().toSet(),
            excludedNamespaces = excludeNamespaces.get().toSet(),
            excludedTypes = excludeTypes.get().toSet(),
        )
        val generatedRoot = outputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val authoringMetadataIndex = generatedRoot.resolve("kotlin-winrt-authoring/metadata-index.tsv")
        Files.createDirectories(authoringMetadataIndex.parent)
        writeAuthoringMetadataIndex(baseModel, authoringMetadataIndex)
        val authoringSourceRoots = sourceRoots.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .filterNot { sourceRoot -> sourceRoot.startsWith(generatedRoot) }
            .filter(::containsKotlinSource)
        val authoringCandidates = if (authoringSourceRoots.isEmpty()) {
            emptyList()
        } else {
            val scannerWorkDirectory = temporaryDir.toPath().resolve("authoring-scanner")
            Files.createDirectories(scannerWorkDirectory)
            val candidatesFile = scannerWorkDirectory.resolve("candidates.tsv")
            runAuthoringScanner(
                sourceRoots = authoringSourceRoots,
                metadataIndex = authoringMetadataIndex,
                candidatesFile = candidatesFile,
            )
            KotlinWinRtAuthoringCandidateFile.read(candidatesFile)
        }
        val model = KotlinWinRtAuthoringMetadataModel.mergeAuthoredRuntimeClasses(baseModel, authoringCandidates)
        KotlinProjectionGenerator(
            emitSupportFiles = true,
            projectionContext = WinRtMetadataProjectionContext(
                sources = sources,
                include = includeNamespaces.get().toSet() + includeTypes.get().toSet(),
                exclude = excludeNamespaces.get().toSet() + excludeTypes.get().toSet(),
                additionExclude = additionExcludeNamespaces.get().toSet(),
            ),
        ).generateTo(model, outputDirectory.get().asFile.toPath())
        writeAuthoringMetadataIndex(model, authoringMetadataIndex)
        if (authoringCandidates.isNotEmpty()) {
            KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
                candidates = authoringCandidates,
                metadataModel = model,
                outputDirectory = outputDirectory.get().asFile.toPath(),
            )
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
        val restoredPackageDirectories = if (restoreNuGetPackages.get() && packageIdentities.isNotEmpty()) {
            restoreNuGetPackages(packageIdentities)
        } else {
            emptyList()
        }
        val packageIdentitiesFromRoots = if (restoreNuGetPackages.get()) {
            emptyList()
        } else {
            val missingNuGetIdentities = packageIdentities.filterNot { identity ->
                isNuGetPackageAvailable(identity, nugetRoots)
            }
            require(missingNuGetIdentities.isEmpty()) {
                "NuGet packages are missing from the configured NuGet cache and restoreNuGetPackages is false: ${missingNuGetIdentities.joinToString()}"
            }
            packageIdentities
        }
        val resolvedNuGetSources = packageIdentitiesFromRoots.map { identity ->
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
            val invocation = nuGetCli().run(
                arguments = listOf("locals", "global-packages", "-list"),
                description = "locate global-packages",
            )
            WinRtNuGetPackageResolver.parseNuGetGlobalPackagesOutput(invocation.output)
        }.getOrElse { error ->
            logger.info("NuGet CLI global-packages lookup failed: ${error.message}")
            emptyList()
        }
    }

    private fun containsKotlinSource(root: Path): Boolean {
        if (Files.isRegularFile(root)) {
            return root.extension == "kt"
        }
        if (!Files.isDirectory(root)) {
            return false
        }
        return Files.walk(root).use { stream ->
            stream.asSequence().any { path -> Files.isRegularFile(path) && path.extension == "kt" }
        }
    }

    private fun writeAuthoringMetadataIndex(
        model: io.github.kitectlab.winrt.metadata.WinRtMetadataModel,
        output: Path,
    ) {
        val lines = model.namespaces
            .flatMap { namespace -> namespace.types }
            .sortedBy { type -> type.qualifiedName }
            .map { type ->
                listOf(
                    type.qualifiedName,
                    type.kind.name,
                    type.implementedInterfaces
                        .filter { implementation -> implementation.isOverridable }
                        .map { implementation -> implementation.interfaceName }
                        .distinct()
                        .sorted()
                        .joinToString(";"),
                ).joinToString("\t")
            }
        Files.write(output, lines)
    }

    private fun runAuthoringScanner(
        sourceRoots: List<Path>,
        metadataIndex: Path,
        candidatesFile: Path,
    ) {
        execOperations.javaexec { spec ->
            spec.classpath = authoringScannerClasspath
            spec.mainClass.set("io.github.kitectlab.winrt.compiler.KotlinWinRtAuthoringScannerCli")
            spec.args(
                buildList {
                    add("--metadata-index")
                    add(metadataIndex.toString())
                    add("--output")
                    add(candidatesFile.toString())
                    sourceRoots.forEach { root ->
                        add("--source-root")
                        add(root.toString())
                    }
                },
            )
        }
    }

    private fun nuGetCli(): NuGetCliSupport = NuGetCliSupport(
        executable = nugetExecutable.get(),
        cliVersion = nugetCliVersion.get(),
        cliCacheDirectory = nugetCliCacheDirectory.get().asFile.toPath(),
        logger = logger,
    )
}
