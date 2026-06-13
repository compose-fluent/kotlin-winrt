package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoringCandidateFile
import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoringMetadataModel
import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoringTypeDetailsRenderer
import io.github.composefluent.winrt.metadata.WinRtMetadataLoader
import io.github.composefluent.winrt.metadata.WinRtMetadataSource
import io.github.composefluent.winrt.metadata.WinRtNuGetPackageIdentity
import io.github.composefluent.winrt.metadata.WinRtNuGetPackageResolver
import io.github.composefluent.winrt.metadata.filterProjectionSurface
import io.github.composefluent.winrt.projections.generator.redirectedWinAppSdkProjectionSurfaceTypeReferences
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
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
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.asSequence

@CacheableTask
abstract class GenerateWinRtCompilerAuthoredTypeDetailsTask @Inject constructor() : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val legacyOutputDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerCandidates: ConfigurableFileCollection

    @get:Input
    abstract val metadataInputs: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataInputFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

    @get:Input
    abstract val includeNamespaces: ListProperty<String>

    @get:Input
    abstract val includeTypes: ListProperty<String>

    @get:Input
    abstract val excludeNamespaces: ListProperty<String>

    @get:Input
    abstract val excludeTypes: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val includeWindowsSdkExtensions: Property<Boolean>

    @get:Input
    abstract val generateWindowsSdkProjection: Property<Boolean>

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

    @get:Input
    abstract val authoringAssemblyName: Property<String>

    @TaskAction
    fun generate() {
        val outputRoot = outputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        legacyOutputDirectories.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .filterNot(outputRoot::equals)
            .forEach(GradleFileOperations::deleteDirectory)
        GradleFileOperations.cleanDirectory(outputRoot)
        val candidates = compilerCandidates.files
            .singleOrNull()
            ?.takeIf { file -> file.isFile }
            ?.toPath()
            ?.let(KotlinWinRtAuthoringCandidateFile::read)
            .orEmpty()
        var sources = metadataSources().withWindowsSdkSourceForProjectionRoots(
            includeNames = includeNamespaces.get().toSet() + includeTypes.get().toSet(),
            version = windowsSdkVersion.orNull,
            includeExtensions = includeWindowsSdkExtensions.get(),
        )
        var unfilteredModel = WinRtMetadataLoader.loadSources(sources)
        sources = sources.withWindowsSdkSourceForUnresolvedWindowsReferences(
            model = unfilteredModel,
            version = windowsSdkVersion.orNull,
            includeExtensions = includeWindowsSdkExtensions.get(),
        )
        unfilteredModel = WinRtMetadataLoader.loadSources(sources)
        val exportedCandidates = candidates.filter { candidate -> candidate.isPublic }
        val authoringCandidateMetadataRoots = authoringCandidateMetadataRootNames(candidates)
        val baseModel = unfilteredModel.filterProjectionSurface(
            namespaces = includeNamespaces.get().toSet(),
            types = (includeTypes.get() + dependencyProjectionSurfaceTypeNames(dependencyIdentityFiles.files) + authoringCandidateMetadataRoots).toSet(),
            excludedNamespaces = excludeNamespaces.get().toSet(),
            excludedTypes = excludeTypes.get().toSet(),
            additionalTypeReferences = ::redirectedWinAppSdkProjectionSurfaceTypeReferences,
        )
        val model = KotlinWinRtAuthoringMetadataModel.mergeAuthoredRuntimeClasses(
            baseModel,
            exportedCandidates,
        )
        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = candidates,
            metadataModel = model,
            outputDirectory = outputRoot,
            assemblyName = authoringAssemblyName.get(),
        )
    }

    private fun metadataSources(): List<WinRtMetadataSource> {
        val explicitSources = metadataInputs.get().map(WinRtMetadataSource::parse)
        val hasProjectionFilter = includeNamespaces.get().isNotEmpty() || includeTypes.get().isNotEmpty()
        val packageSpecs = (nugetPackages.get() + dependencyIdentityFiles.files.flatMap(::readNuGetPackages))
            .distinct()
            .sorted()
        val sdkSource = if (
            generateWindowsSdkProjection.get() ||
            windowsSdkVersion.isPresent ||
            explicitSources.isNotEmpty() ||
            hasProjectionFilter ||
            packageSpecs.isNotEmpty() ||
            includeWindowsSdkExtensions.get()
        ) {
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
        val packageIdentities = packageSpecs.map(::parseNuGetPackageIdentity)
        val nugetRoots = explicitNuGetRoots + cliNuGetRoots
        val packageIdentitiesFromRoots = if (restoreNuGetPackages.get()) {
            packageIdentities.filter { identity ->
                isNuGetPackageClosureAvailable(identity, nugetRoots)
            }
        } else {
            val missingNuGetIdentities = packageIdentities.filterNot { identity ->
                isNuGetPackageClosureAvailable(identity, nugetRoots)
            }
            require(missingNuGetIdentities.isEmpty()) {
                "NuGet packages are missing from the configured NuGet cache and restoreNuGetPackages is false: ${missingNuGetIdentities.joinToString()}"
            }
            packageIdentities
        }
        val restoredPackageDirectories = if (restoreNuGetPackages.get()) {
            val identitiesFromRoots = packageIdentitiesFromRoots.toSet()
            val missingNuGetIdentities = packageIdentities.filterNot { identity -> identity in identitiesFromRoots }
            restoreNuGetPackages(missingNuGetIdentities)
        } else {
            emptyList()
        }
        val resolvedNuGetSources = packageIdentitiesFromRoots.map { identity ->
            WinRtMetadataSource.nugetPackage(
                packageId = identity.normalizedPackageId,
                version = identity.normalizedVersion,
                globalPackagesRoots = nugetRoots,
            )
        }
        val restoredNuGetSources = restoredPackageDirectories.map(WinRtMetadataSource::nugetPackage)
        val sources = explicitSources + sdkSource + resolvedNuGetSources + restoredNuGetSources
        return sources.ifEmpty {
            if (hasProjectionFilter && generateWindowsSdkProjection.get()) {
                listOf(WinRtMetadataSource.windowsSdk())
            } else {
                emptyList()
            }
        }
    }

    private fun isNuGetPackageClosureAvailable(
        identity: WinRtNuGetPackageIdentity,
        globalPackagesRoots: List<Path>,
    ): Boolean {
        val roots = WinRtNuGetPackageResolver.globalPackagesRoots(explicitRoots = globalPackagesRoots)
        return runCatching {
            WinRtNuGetPackageResolver.resolveClosure(identity, roots)
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

    private fun nuGetCli(): NuGetCliSupport = NuGetCliSupport(
        executable = nugetExecutable.get(),
        cliVersion = nugetCliVersion.get(),
        cliCacheDirectory = nugetCliCacheDirectory.get().asFile.toPath(),
        scratchDirectory = temporaryDir.toPath().resolve("nuget-scratch"),
        logger = logger,
    )
}
