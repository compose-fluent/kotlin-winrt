package io.github.composefluent.winrt.gradle

import com.squareup.kotlinpoet.ClassName
import io.github.composefluent.winrt.metadata.WinRtMetadataLoader
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtMetadataSource
import io.github.composefluent.winrt.metadata.WinRtNuGetPackageIdentity
import io.github.composefluent.winrt.metadata.WinRtNuGetPackageResolver
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.filterProjectionSurface
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.nio.file.Files
import java.nio.file.Path
import java.security.CodeSource
import javax.inject.Inject
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.asSequence

@CacheableTask
abstract class GenerateWinRtProjectionsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val authoringTypeDetailsOutputDirectory: DirectoryProperty

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

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

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

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nugetPackageContentFiles: ConfigurableFileCollection

    @get:Input
    abstract val projectModel: Property<String>

    @get:Input
    abstract val authoringAssemblyName: Property<String>

    @get:Input
    abstract val authoringTargetArtifactName: Property<String>

    @get:Classpath
    abstract val authoringScannerClasspath: ConfigurableFileCollection

    @get:Input
    abstract val authoringScannerJvmArgs: ListProperty<String>

    @get:Classpath
    abstract val generatorWorkerClasspath: ConfigurableFileCollection

    @get:Input
    abstract val generatorWorkerJvmArgs: ListProperty<String>

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun generate() {
        workerExecutor.processIsolation { spec ->
            spec.classpath.from(generatorWorkerClasspath)
            spec.forkOptions.jvmArgs(generatorWorkerJvmArgs.get())
        }.submit(GenerateWinRtProjectionsWorkAction::class.java) { parameters ->
            parameters.outputDirectory.set(outputDirectory)
            parameters.authoringTypeDetailsOutputDirectory.set(authoringTypeDetailsOutputDirectory)
            parameters.metadataInputs.set(metadataInputs)
            parameters.metadataInputFiles.from(metadataInputFiles)
            parameters.sourceRoots.from(sourceRoots)
            parameters.includeNamespaces.set(includeNamespaces)
            parameters.includeTypes.set(includeTypes)
            parameters.excludeNamespaces.set(excludeNamespaces)
            parameters.excludeTypes.set(excludeTypes)
            parameters.additionExcludeNamespaces.set(additionExcludeNamespaces)
            parameters.dependencyIdentityFiles.from(dependencyIdentityFiles)
            parameters.windowsSdkVersion.set(windowsSdkVersion)
            parameters.includeWindowsSdkExtensions.set(includeWindowsSdkExtensions)
            parameters.nugetExecutable.set(nugetExecutable)
            parameters.nugetCliVersion.set(nugetCliVersion)
            parameters.nugetCliCacheDirectory.set(nugetCliCacheDirectory)
            parameters.restoreNuGetPackages.set(restoreNuGetPackages)
            parameters.useNuGetCliGlobalPackages.set(useNuGetCliGlobalPackages)
            parameters.nugetGlobalPackagesRoots.set(nugetGlobalPackagesRoots)
            parameters.nugetPackages.set(nugetPackages)
            parameters.projectModel.set(projectModel)
            parameters.authoringAssemblyName.set(authoringAssemblyName)
            parameters.authoringTargetArtifactName.set(authoringTargetArtifactName)
            parameters.authoringScannerClasspath.from(authoringScannerClasspath)
            parameters.authoringScannerJvmArgs.set(authoringScannerJvmArgs)
            parameters.workDirectory.set(temporaryDir)
        }
    }
}

internal interface GenerateWinRtProjectionsWorkParameters : WorkParameters {
    val outputDirectory: DirectoryProperty
    val authoringTypeDetailsOutputDirectory: DirectoryProperty
    val metadataInputs: ListProperty<String>
    val metadataInputFiles: ConfigurableFileCollection
    val sourceRoots: ConfigurableFileCollection
    val includeNamespaces: ListProperty<String>
    val includeTypes: ListProperty<String>
    val excludeNamespaces: ListProperty<String>
    val excludeTypes: ListProperty<String>
    val additionExcludeNamespaces: ListProperty<String>
    val dependencyIdentityFiles: ConfigurableFileCollection
    val windowsSdkVersion: Property<String>
    val includeWindowsSdkExtensions: Property<Boolean>
    val nugetExecutable: Property<String>
    val nugetCliVersion: Property<String>
    val nugetCliCacheDirectory: DirectoryProperty
    val restoreNuGetPackages: Property<Boolean>
    val useNuGetCliGlobalPackages: Property<Boolean>
    val nugetGlobalPackagesRoots: ListProperty<String>
    val nugetPackages: ListProperty<String>
    val projectModel: Property<String>
    val authoringAssemblyName: Property<String>
    val authoringTargetArtifactName: Property<String>
    val authoringScannerClasspath: ConfigurableFileCollection
    val authoringScannerJvmArgs: ListProperty<String>
    val workDirectory: DirectoryProperty
}

internal abstract class GenerateWinRtProjectionsWorkAction : WorkAction<GenerateWinRtProjectionsWorkParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    private val logger = Logging.getLogger(GenerateWinRtProjectionsWorkAction::class.java)

    override fun execute() {
        logGeneratorRuntimeClasspath()
        val generatedRoot = parameters.outputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val authoringTypeDetailsRoot = parameters.authoringTypeDetailsOutputDirectory.get().asFile.toPath()
            .toAbsolutePath()
            .normalize()
        cleanDirectory(generatedRoot)
        cleanDirectory(authoringTypeDetailsRoot)
        val sources = metadataSources()
        val baseModel = WinRtMetadataLoader.loadSources(sources).filterProjectionSurface(
            namespaces = parameters.includeNamespaces.get().toSet(),
            types = parameters.includeTypes.get().toSet(),
            excludedNamespaces = parameters.excludeNamespaces.get().toSet(),
            excludedTypes = parameters.excludeTypes.get().toSet(),
        )
        val authoringMetadataIndex = generatedRoot.resolve("kotlin-winrt-authoring/metadata-index.tsv")
        Files.createDirectories(authoringMetadataIndex.parent)
        writeAuthoringMetadataIndex(baseModel, authoringMetadataIndex)
        val authoringSourceRoots = parameters.sourceRoots.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .filterNot { sourceRoot -> sourceRoot.startsWith(generatedRoot) }
            .filter(::containsKotlinSource)
        val authoringCandidates = if (authoringSourceRoots.isEmpty()) {
            emptyList()
        } else {
            val scannerWorkDirectory = parameters.workDirectory.get().asFile.toPath().resolve("authoring-scanner")
            Files.createDirectories(scannerWorkDirectory)
            val candidatesFile = scannerWorkDirectory.resolve("candidates.tsv")
            runAuthoringScanner(
                sourceRoots = authoringSourceRoots,
                metadataIndex = authoringMetadataIndex,
                candidatesFile = candidatesFile,
            )
            KotlinWinRtAuthoringCandidateFile.read(candidatesFile)
        }
        val dependencyProjectionTypeNames = dependencyProjectedTypeNames(baseModel, parameters.dependencyIdentityFiles.files)
        val exportedAuthoringCandidates = authoringCandidates.filter(KotlinWinRtAuthoredTypeCandidate::isPublic)
        val model = KotlinWinRtAuthoringMetadataModel.mergeAuthoredRuntimeClasses(baseModel, exportedAuthoringCandidates)
        KotlinWinRtAuthoringMetadataModel.writeDescriptor(
            candidates = exportedAuthoringCandidates,
            outputFile = generatedRoot.resolve("kotlin-winrt-authoring/authored-metadata.tsv"),
        )
        KotlinWinRtAuthoringMetadataModel.writeWinmd(
            assemblyName = parameters.authoringAssemblyName.get(),
            candidates = exportedAuthoringCandidates,
            outputFile = generatedRoot.resolve("kotlin-winrt-authoring/${parameters.authoringAssemblyName.get()}.winmd"),
        )
        KotlinWinRtAuthoringMetadataModel.writeHostManifest(
            assemblyName = parameters.authoringAssemblyName.get(),
            targetArtifactName = parameters.authoringTargetArtifactName.get(),
            candidates = exportedAuthoringCandidates,
            outputFile = generatedRoot.resolve("kotlin-winrt-authoring/${parameters.authoringAssemblyName.get()}.host.json"),
        )
        KotlinProjectionGenerator(
            emitSupportFiles = true,
            projectionContext = WinRtMetadataProjectionContext(
                sources = sources,
                include = parameters.includeNamespaces.get().toSet() + parameters.includeTypes.get().toSet(),
                exclude = parameters.excludeNamespaces.get().toSet() + parameters.excludeTypes.get().toSet(),
                additionExclude = parameters.additionExcludeNamespaces.get().toSet(),
            ),
            suppressedProjectionTypeNames = (
                dependencyProjectionTypeNames +
                    authoringCandidates
                        .mapTo(mutableSetOf()) { candidate -> candidate.sourceTypeName }
                        .filterTo(mutableSetOf(), String::isNotBlank)
                ),
        ).generateTo(model, parameters.outputDirectory.get().asFile.toPath())
        writeAuthoringMetadataIndex(model, authoringMetadataIndex)
        if (authoringCandidates.isNotEmpty()) {
            KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
                candidates = authoringCandidates,
                metadataModel = model,
                outputDirectory = authoringTypeDetailsRoot,
            )
        }
    }

    private fun cleanDirectory(path: Path) {
        if (!Files.exists(path)) {
            return
        }
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }

    private fun metadataSources(): List<WinRtMetadataSource> {
        val applicationPackagingOnly = parameters.projectModel.get() == "application" &&
            parameters.metadataInputs.get().isEmpty() &&
            parameters.includeNamespaces.get().isEmpty() &&
            parameters.includeTypes.get().isEmpty() &&
            !parameters.windowsSdkVersion.isPresent &&
            !parameters.includeWindowsSdkExtensions.get()
        val explicitSources = parameters.metadataInputs.get().map(WinRtMetadataSource::parse)
        val sdkSource = if (parameters.windowsSdkVersion.isPresent || parameters.includeWindowsSdkExtensions.get()) {
            listOf(
                WinRtMetadataSource.windowsSdk(
                    version = parameters.windowsSdkVersion.orNull,
                    includeExtensions = parameters.includeWindowsSdkExtensions.get(),
                ),
            )
        } else {
            emptyList()
        }
        val explicitNuGetRoots = parameters.nugetGlobalPackagesRoots.get().map(Path::of)
        val cliNuGetRoots = nugetCliGlobalPackagesRoots()
        val packageIdentities = if (applicationPackagingOnly) {
            emptyList()
        } else {
            parameters.nugetPackages.get().map(::parseNuGetPackageIdentity)
        }
        val nugetRoots = explicitNuGetRoots + cliNuGetRoots
        val restoredPackageDirectories = if (parameters.restoreNuGetPackages.get() && packageIdentities.isNotEmpty()) {
            restoreNuGetPackages(packageIdentities)
        } else {
            emptyList()
        }
        val packageIdentitiesFromRoots = if (parameters.restoreNuGetPackages.get()) {
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
        val sources = explicitSources + sdkSource + resolvedNuGetSources + restoredNuGetSources
        val hasProjectionFilter = parameters.includeNamespaces.get().isNotEmpty() || parameters.includeTypes.get().isNotEmpty()
        return if (applicationPackagingOnly) {
            sources
        } else {
            sources.ifEmpty {
                if (hasProjectionFilter) {
                    listOf(WinRtMetadataSource.windowsSdk())
                } else {
                    emptyList()
                }
            }
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

        val installRoot = parameters.workDirectory.get().asFile.toPath().resolve("nuget-install")
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
        if (!parameters.useNuGetCliGlobalPackages.get()) {
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

    private fun logGeneratorRuntimeClasspath() {
        val kotlinPoetClass = ClassName::class.java
        val kotlinStdlibClass = Sequence::class.java
        logger.lifecycle(
            "kotlin-winrt generator worker runtime: KotlinPoet={}, Kotlin stdlib={}, classloader={}",
            codeSourceLocation(kotlinPoetClass.protectionDomain?.codeSource),
            codeSourceLocation(kotlinStdlibClass.protectionDomain?.codeSource),
            GenerateWinRtProjectionsWorkAction::class.java.classLoader,
        )
        runCatching {
            ClassName("kotlin", "String")
        }.getOrElse { error ->
            throw IllegalStateException(
                "kotlin-winrt generator worker loaded an incompatible KotlinPoet from " +
                    "${codeSourceLocation(kotlinPoetClass.protectionDomain?.codeSource)}. " +
                    "The projection generator must run in the isolated worker classpath configured by " +
                    KOTLIN_WINRT_GENERATOR_WORKER_CONFIGURATION +
                    ", not a downstream Gradle/buildSrc parent classloader.",
                error,
            )
        }
    }

    private fun codeSourceLocation(codeSource: CodeSource?): String =
        codeSource?.location?.toString() ?: "<unknown>"

    private fun writeAuthoringMetadataIndex(
        model: io.github.composefluent.winrt.metadata.WinRtMetadataModel,
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
                    type.baseTypeName.orEmpty(),
                ).joinToString("\t")
            }
        Files.write(output, lines)
    }

    private fun runAuthoringScanner(
        sourceRoots: List<Path>,
        metadataIndex: Path,
        candidatesFile: Path,
    ) {
        val scannerWorkDirectory = candidatesFile.parent
        execOperations.javaexec { spec ->
            spec.classpath = parameters.authoringScannerClasspath
            spec.mainClass.set("io.github.composefluent.winrt.compiler.KotlinWinRtAuthoringScannerCli")
            spec.workingDir(scannerWorkDirectory.toFile())
            spec.jvmArgs(
                parameters.authoringScannerJvmArgs.get() +
                    "-Djava.io.tmpdir=${scannerWorkDirectory.toAbsolutePath().normalize()}",
            )
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
        executable = parameters.nugetExecutable.get(),
        cliVersion = parameters.nugetCliVersion.get(),
        cliCacheDirectory = parameters.nugetCliCacheDirectory.get().asFile.toPath(),
        scratchDirectory = parameters.workDirectory.get().asFile.toPath().resolve("nuget-scratch"),
        logger = logger,
    )
}

internal fun dependencyProjectedTypeNames(
    model: WinRtMetadataModel,
    identityFiles: Iterable<java.io.File>,
): Set<String> =
    identityFiles
        .flatMap { identityFile -> dependencyProjectedTypeNames(model, readProjectionSurfaceIdentity(identityFile)) }
        .toSortedSet()

private fun dependencyProjectedTypeNames(
    model: WinRtMetadataModel,
    identity: ProjectionSurfaceIdentity,
): List<String> {
    identity.projectedTypes?.let { projectedTypes ->
        return projectedTypes
            .filter { typeName -> model.namespaces.any { namespace -> namespace.types.any { it.qualifiedName == typeName } } }
    }
    return model.filterProjectionSurface(
        namespaces = identity.includeNamespaces.toSet(),
        types = identity.includeTypes.toSet(),
        excludedNamespaces = identity.excludeNamespaces.toSet(),
        excludedTypes = identity.excludeTypes.toSet(),
    ).namespaces.flatMap { namespace -> namespace.types.map(WinRtTypeDefinition::qualifiedName) }
}

internal data class ProjectionSurfaceIdentity(
    val includeNamespaces: List<String>,
    val includeTypes: List<String>,
    val projectedTypes: List<String>?,
    val excludeNamespaces: List<String>,
    val excludeTypes: List<String>,
)

internal fun readProjectionSurfaceIdentity(identityFile: java.io.File): ProjectionSurfaceIdentity {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    return ProjectionSurfaceIdentity(
        includeNamespaces = readIdentityStringArray(content, "includeNamespaces"),
        includeTypes = readIdentityStringArray(content, "includeTypes"),
        projectedTypes = readOptionalIdentityStringArray(content, "projectedTypes"),
        excludeNamespaces = readIdentityStringArray(content, "excludeNamespaces"),
        excludeTypes = readIdentityStringArray(content, "excludeTypes"),
    )
}

private fun readIdentityStringArray(content: String, name: String): List<String> {
    return readOptionalIdentityStringArray(content, name).orEmpty()
}

private fun readOptionalIdentityStringArray(content: String, name: String): List<String>? {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return null
    return Regex(""""((?:\\.|[^"\\])*)"""")
        .findAll(match.groupValues[1])
        .map { it.groupValues[1].decodeIdentityJsonString() }
        .toList()
}

private fun String.decodeIdentityJsonString(): String =
    replace("\\\"", "\"").replace("\\\\", "\\")
