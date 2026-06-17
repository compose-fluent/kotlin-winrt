package io.github.composefluent.winrt.gradle

import com.squareup.kotlinpoet.ClassName
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRtAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRtAuthoringCandidateFile
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRtAuthoringMetadataModel
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRtAuthoringTypeDetailsRenderer
import io.github.composefluent.winrt.compiler.authoring.authoringTypeDetailsRegistrarName
import io.github.composefluent.winrt.compiler.authoring.readAuthoringMetadataIndex
import io.github.composefluent.winrt.compiler.authoring.readAuthoringMetadataIndexRows as parseAuthoringMetadataIndexRows
import io.github.composefluent.winrt.compiler.authoring.writeAuthoringMetadataIndex
import io.github.composefluent.winrt.metadata.WinRtMetadataLoader
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtNamespace
import io.github.composefluent.winrt.metadata.WinRtMetadataSource
import io.github.composefluent.winrt.metadata.WinRtNuGetPackageIdentity
import io.github.composefluent.winrt.metadata.WinRtNuGetPackageResolver
import io.github.composefluent.winrt.metadata.WinRtTypeRef
import io.github.composefluent.winrt.metadata.WinRtTypeRefKind
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.filterProjectionSurface
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import io.github.composefluent.winrt.projections.generator.redirectedWinAppSdkProjectionSurfaceTypeReferences
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

    @get:Internal
    abstract val legacyOutputDirectories: ConfigurableFileCollection

    @get:Input
    abstract val emitJvmAuthoringHostExports: Property<Boolean>

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
            parameters.legacyOutputDirectories.from(legacyOutputDirectories)
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
            parameters.generateWindowsSdkProjection.set(generateWindowsSdkProjection)
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
            parameters.emitJvmAuthoringHostExports.set(emitJvmAuthoringHostExports)
            parameters.authoringScannerClasspath.from(authoringScannerClasspath)
            parameters.authoringScannerJvmArgs.set(authoringScannerJvmArgs)
            parameters.workDirectory.set(temporaryDir)
        }
    }
}

internal interface GenerateWinRtProjectionsWorkParameters : WorkParameters {
    val outputDirectory: DirectoryProperty
    val authoringTypeDetailsOutputDirectory: DirectoryProperty
    val legacyOutputDirectories: ConfigurableFileCollection
    val emitJvmAuthoringHostExports: Property<Boolean>
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
    val generateWindowsSdkProjection: Property<Boolean>
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
        parameters.legacyOutputDirectories.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .filterNot { legacyRoot -> legacyRoot == generatedRoot || legacyRoot == authoringTypeDetailsRoot }
            .forEach(GradleFileOperations::deleteDirectory)
        cleanDirectory(generatedRoot)
        cleanDirectory(authoringTypeDetailsRoot)
        var sources = metadataSources().withWindowsSdkSourceForProjectionRoots(
            includeNames = parameters.includeNamespaces.get().toSet() + parameters.includeTypes.get().toSet(),
            version = parameters.windowsSdkVersion.orNull,
            includeExtensions = parameters.includeWindowsSdkExtensions.get(),
        )
        val effectiveExcludeTypes = parameters.excludeTypes.get() + automaticProjectionExcludeTypes(parameters.nugetPackages.get())
        var unfilteredModel = WinRtMetadataLoader.loadSources(sources)
        sources = sources.withWindowsSdkSourceForUnresolvedWindowsReferences(
            model = unfilteredModel,
            version = parameters.windowsSdkVersion.orNull,
            includeExtensions = parameters.includeWindowsSdkExtensions.get(),
        )
        unfilteredModel = WinRtMetadataLoader.loadSources(sources)
        val effectiveIncludeTypes = parameters.includeTypes.get() +
            automaticXamlComponentResourceDictionaryTypes(unfilteredModel, parameters.includeTypes.get().toSet())
        val dependencyProjectionSurfaceTypes = dependencyProjectionSurfaceTypeNames(parameters.dependencyIdentityFiles.files)
        val effectiveSources = sources.withWindowsSdkSourceForProjectionRoots(
            includeNames = parameters.includeNamespaces.get().toSet() + effectiveIncludeTypes.toSet() + dependencyProjectionSurfaceTypes.toSet(),
            version = parameters.windowsSdkVersion.orNull,
            includeExtensions = parameters.includeWindowsSdkExtensions.get(),
        )
        if (effectiveSources != sources) {
            sources = effectiveSources
            unfilteredModel = WinRtMetadataLoader.loadSources(sources)
        }
        val applicationPackagingOnly = parameters.projectModel.get() == "application" &&
            parameters.metadataInputs.get().isEmpty() &&
            parameters.includeNamespaces.get().isEmpty() &&
            parameters.includeTypes.get().isEmpty() &&
            !parameters.generateWindowsSdkProjection.get()
        val baseModel = if (applicationPackagingOnly) {
            WinRtMetadataModel(emptyList())
        } else {
            unfilteredModel.filterProjectionSurface(
                namespaces = parameters.includeNamespaces.get().toSet(),
                types = (effectiveIncludeTypes + dependencyProjectionSurfaceTypes).toSet(),
                excludedNamespaces = parameters.excludeNamespaces.get().toSet(),
                excludedTypes = effectiveExcludeTypes.toSet(),
                additionalTypeReferences = ::redirectedWinAppSdkProjectionSurfaceTypeReferences,
            )
        }
        val authoringMetadataBaseModel = unfilteredModel.filterProjectionSurface(
            namespaces = parameters.includeNamespaces.get().toSet(),
            types = (effectiveIncludeTypes + dependencyProjectionSurfaceTypes).toSet(),
            excludedNamespaces = parameters.excludeNamespaces.get().toSet(),
            excludedTypes = effectiveExcludeTypes.toSet(),
            additionalTypeReferences = ::redirectedWinAppSdkProjectionSurfaceTypeReferences,
        )
        val authoringMetadataIndex = generatedRoot.resolve("kotlin-winrt-authoring/metadata-index.tsv")
        Files.createDirectories(authoringMetadataIndex.parent)
        writeAuthoringMetadataIndex(
            mergedAuthoringMetadataIndexTypes(authoringMetadataBaseModel, parameters.dependencyIdentityFiles.files),
            authoringMetadataIndex,
        )
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
        KotlinWinRtAuthoringCandidateFile.write(
            generatedRoot.resolve("kotlin-winrt-authoring/authored-candidates.tsv"),
            authoringCandidates,
        )
        val dependencyProjectionTypeNames = dependencyProjectedTypeNames(baseModel, parameters.dependencyIdentityFiles.files)
        val exportedAuthoringCandidates = authoringCandidates.filter(KotlinWinRtAuthoredTypeCandidate::isPublic)
        val model = KotlinWinRtAuthoringMetadataModel.mergeAuthoredRuntimeClasses(baseModel, exportedAuthoringCandidates)
        val authoringCandidateMetadataRoots = authoringCandidateMetadataRootNames(authoringCandidates)
        val authoringDetailsBaseModel = unfilteredModel.filterProjectionSurface(
            namespaces = parameters.includeNamespaces.get().toSet(),
            types = (effectiveIncludeTypes + dependencyProjectionSurfaceTypes + authoringCandidateMetadataRoots).toSet(),
            excludedNamespaces = parameters.excludeNamespaces.get().toSet(),
            excludedTypes = effectiveExcludeTypes.toSet(),
            additionalTypeReferences = ::redirectedWinAppSdkProjectionSurfaceTypeReferences,
        )
        val authoringModel = KotlinWinRtAuthoringMetadataModel.mergeAuthoredRuntimeClasses(
            authoringDetailsBaseModel,
            exportedAuthoringCandidates,
        )
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
            hostExportsClassName = winRtAuthoringHostExportsClassName(parameters.authoringTargetArtifactName.get()),
            candidates = exportedAuthoringCandidates,
            outputFile = generatedRoot.resolve("kotlin-winrt-authoring/${parameters.authoringAssemblyName.get()}.host.json"),
        )
        val projectionModel = if (parameters.projectModel.get() == "application") baseModel else model
        val authoredRuntimeClassNames = authoringCandidates
            .mapTo(mutableSetOf()) { candidate -> candidate.sourceTypeName }
            .filterTo(mutableSetOf(), String::isNotBlank)
        val projectionContext = WinRtMetadataProjectionContext(
            sources = sources,
            include = parameters.includeNamespaces.get().toSet() +
                effectiveIncludeTypes.toSet() +
                dependencyProjectionSurfaceTypes.toSet() +
                authoringCandidateMetadataRoots.toSet(),
            exclude = parameters.excludeNamespaces.get().toSet() + effectiveExcludeTypes.toSet(),
            excludedTypes = effectiveExcludeTypes.toSet(),
            additionExclude = parameters.additionExcludeNamespaces.get().toSet(),
            component = exportedAuthoringCandidates.isNotEmpty(),
        )
        KotlinProjectionGenerator(
            emitSupportFiles = true,
            groupProjectionFilesByPackageOnWrite = true,
            projectionContext = projectionContext,
            suppressedProjectionTypeNames = dependencyProjectionTypeNames + authoredRuntimeClassNames,
            authoredRuntimeClassNames = authoredRuntimeClassNames,
            supportOwnerIdentity = parameters.authoringTargetArtifactName.get(),
            emitJvmAuthoringHostExports = parameters.emitJvmAuthoringHostExports.get(),
        ).generateTo(projectionModel, parameters.outputDirectory.get().asFile.toPath())
        writeAuthoringMetadataIndex(
            mergedAuthoringMetadataIndexTypes(authoringModel, parameters.dependencyIdentityFiles.files),
            authoringMetadataIndex,
        )
        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = authoringCandidates,
            metadataModel = authoringModel,
            outputDirectory = authoringTypeDetailsRoot,
            assemblyName = parameters.authoringAssemblyName.get(),
        )
        writeAuthoringTypeDetailsRegistrarSupport(
            generatedRoot = generatedRoot,
            assemblyName = parameters.authoringAssemblyName.get(),
        )
    }

    private fun writeAuthoringTypeDetailsRegistrarSupport(
        generatedRoot: Path,
        assemblyName: String,
    ) {
        val supportRoot = generatedRoot.resolve("kotlin-winrt-support")
        Files.createDirectories(supportRoot)
        val registrarClassName = "io.github.composefluent.winrt.projections.support." +
            authoringTypeDetailsRegistrarName(assemblyName)
        Files.writeString(
            supportRoot.resolve("authoring-type-details-registrars.tsv"),
            "className\n$registrarClassName\n",
        )
        val manifest = supportRoot.resolve("compiler-support.tsv")
        val existing = if (Files.isRegularFile(manifest)) {
            Files.readAllLines(manifest).filter(String::isNotBlank)
        } else {
            listOf("kind\tclassName\tsourceFile\tentries\towner")
        }
        val row = listOf(
            "authoring-type-details-registrar",
            registrarClassName,
            "authoring-type-details-registrars.tsv",
            "1",
            "",
        ).joinToString("\t")
        if (row !in existing.drop(1)) {
            Files.writeString(manifest, (existing + row).joinToString(separator = "\n", postfix = "\n"))
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

    private fun winRtAuthoringHostExportsClassName(ownerIdentity: String): String {
        val suffix = ownerIdentity.toKotlinSupportIdentifierSuffix()
        return if (suffix.isBlank()) {
            "io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports"
        } else {
            "io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_$suffix"
        }
    }

    private fun String.toKotlinSupportIdentifierSuffix(): String =
        buildString {
            this@toKotlinSupportIdentifierSuffix.trim().forEach { char ->
                append(if (char.isLetterOrDigit()) char else '_')
            }
        }.trim('_')
            .replace(Regex("_+"), "_")
            .let { suffix ->
                if (suffix.firstOrNull()?.isDigit() == true) "_$suffix" else suffix
            }

    private fun metadataSources(): List<WinRtMetadataSource> {
        val applicationPackagingOnly = parameters.projectModel.get() == "application" &&
            parameters.metadataInputs.get().isEmpty() &&
            parameters.includeNamespaces.get().isEmpty() &&
            parameters.includeTypes.get().isEmpty() &&
            !parameters.generateWindowsSdkProjection.get()
        val explicitSources = parameters.metadataInputs.get().map(WinRtMetadataSource::parse)
        val hasProjectionFilter = parameters.includeNamespaces.get().isNotEmpty() || parameters.includeTypes.get().isNotEmpty()
        val packageSpecs = (parameters.nugetPackages.get() + parameters.dependencyIdentityFiles.files.flatMap(::readNuGetPackages))
            .distinct()
            .sorted()
        val sdkSource = if (
            parameters.generateWindowsSdkProjection.get() ||
            parameters.windowsSdkVersion.isPresent ||
            explicitSources.isNotEmpty() ||
            hasProjectionFilter ||
            packageSpecs.isNotEmpty() ||
            parameters.includeWindowsSdkExtensions.get()
        ) {
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
        val packageIdentities = packageSpecs.map(::parseNuGetPackageIdentity)
        val nugetRoots = explicitNuGetRoots + cliNuGetRoots
        val packageIdentitiesFromRoots = if (parameters.restoreNuGetPackages.get()) {
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
        val restoredPackageDirectories = if (parameters.restoreNuGetPackages.get()) {
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
        val dependencyAuthoredMetadataSources = parameters.dependencyIdentityFiles.files
            .flatMap(::readAuthoredMetadata)
            .map(Path::of)
            .filter(Files::isRegularFile)
            .map(WinRtMetadataSource::path)
        val sources = explicitSources + sdkSource + resolvedNuGetSources + restoredNuGetSources + dependencyAuthoredMetadataSources
        return if (applicationPackagingOnly) {
            sources
        } else {
            sources.ifEmpty {
                if (hasProjectionFilter && parameters.generateWindowsSdkProjection.get()) {
                    listOf(WinRtMetadataSource.windowsSdk())
                } else {
                    emptyList()
                }
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

internal fun dependencyProjectionSurfaceTypeNames(
    identityFiles: Iterable<java.io.File>,
): List<String> =
    identityFiles
        .flatMap { identityFile ->
            val identity = readProjectionSurfaceIdentity(identityFile)
            identity.includeTypes + identity.projectedTypes.orEmpty()
        }
        .distinct()
        .sorted()

internal fun mergedAuthoringMetadataIndexTypes(
    model: WinRtMetadataModel,
    identityFiles: Iterable<java.io.File>,
) = (
    model.namespaces
        .flatMap { namespace -> namespace.types }
        .map { type ->
            io.github.composefluent.winrt.compiler.authoring.IndexedWinRtType(
                qualifiedName = type.qualifiedName,
                kind = type.kind.name,
                overridableInterfaces = type.implementedInterfaces
                    .filter { implementation -> implementation.isOverridable }
                    .map { implementation -> implementation.interfaceName }
                    .distinct()
                    .sorted(),
                baseTypeName = type.baseTypeName.orEmpty(),
            )
        } +
        identityFiles
            .flatMap { identityFile ->
                readAuthoringMetadataIndexRows(identityFile)
                    .let { rows ->
                        parseAuthoringMetadataIndexRows(
                            rows,
                            "${identityFile.absolutePath} authoringMetadataIndexRows",
                        ).values
                    }
            } +
        identityFiles
            .flatMap(::readAuthoringMetadataIndexes)
            .map(Path::of)
            .filter(Files::isRegularFile)
            .flatMap { path -> readAuthoringMetadataIndex(path).values }
    )
    .distinctBy { type -> type.qualifiedName }
    .sortedBy { type -> type.qualifiedName }

private fun dependencyProjectedTypeNames(
    model: WinRtMetadataModel,
    identity: ProjectionSurfaceIdentity,
): List<String> {
    return (identity.includeTypes + identity.projectedTypes.orEmpty())
        .distinct()
        .filter { typeName -> model.namespaces.any { namespace -> namespace.types.any { it.qualifiedName == typeName } } }
}

internal fun automaticXamlComponentResourceDictionaryTypes(
    model: WinRtMetadataModel,
    includeTypes: Set<String>,
): List<String> {
    val xamlResourceDictionaryProjected = "Microsoft.UI.Xaml.ResourceDictionary" in includeTypes ||
        "Windows.UI.Xaml.ResourceDictionary" in includeTypes
    if (!xamlResourceDictionaryProjected) {
        return emptyList()
    }
    return model.namespaces
        .asSequence()
        .flatMap { namespace -> namespace.types.asSequence() }
        .filter { type -> type.kind == WinRtTypeKind.RuntimeClass }
        .filter { type ->
            type.baseTypeName == "Microsoft.UI.Xaml.ResourceDictionary" ||
                type.baseTypeName == "Windows.UI.Xaml.ResourceDictionary"
        }
        .map(WinRtTypeDefinition::qualifiedName)
        .filterNot { typeName -> typeName.startsWith("Microsoft.") || typeName.startsWith("Windows.") }
        .distinct()
        .sorted()
        .toList()
}

private fun automaticProjectionExcludeTypes(nugetPackages: List<String>): List<String> =
    if (nugetPackages.any { packageSpec -> packageSpec.substringBefore('@').equals("Microsoft.WindowsAppSDK", ignoreCase = true) }) {
        listOf("Windows.UI.Composition")
    } else {
        emptyList()
    }

internal fun List<WinRtMetadataSource>.withWindowsSdkSourceForProjectionRoots(
    includeNames: Set<String>,
    version: String? = null,
    includeExtensions: Boolean = false,
): List<WinRtMetadataSource> {
    if (any { source -> source is WinRtMetadataSource.WindowsSdk }) {
        return this
    }
    if (includeNames.none { name -> name == "Windows" || name.startsWith("Windows.") }) {
        return this
    }
    return listOf(
        WinRtMetadataSource.windowsSdk(
            version = version,
            includeExtensions = includeExtensions,
        ),
    ) + this
}

internal fun authoringCandidateMetadataRootNames(candidates: List<KotlinWinRtAuthoredTypeCandidate>): List<String> =
    candidates
        .flatMap { candidate -> candidate.winRtInterfaceNames + candidate.winRtBaseClassName.orEmpty() + candidate.sourceTypeName }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()

internal fun List<WinRtMetadataSource>.withWindowsSdkSourceForUnresolvedWindowsReferences(
    model: WinRtMetadataModel,
    version: String? = null,
    includeExtensions: Boolean = false,
): List<WinRtMetadataSource> {
    if (any { source -> source is WinRtMetadataSource.WindowsSdk }) {
        return this
    }
    if (model.unresolvedWindowsSdkTypeReferences().isEmpty()) {
        return this
    }
    return listOf(
        WinRtMetadataSource.windowsSdk(
            version = version,
            includeExtensions = includeExtensions,
        ),
    ) + this
}

internal fun WinRtMetadataModel.unresolvedWindowsSdkTypeReferences(): Set<String> {
    val definedTypeNames = namespaces
        .flatMap(WinRtNamespace::types)
        .mapTo(mutableSetOf(), WinRtTypeDefinition::qualifiedName)
    return namespaces
        .asSequence()
        .flatMap { namespace -> namespace.types.asSequence() }
        .flatMap { type -> type.referencedTypeNames().asSequence() }
        .filter { typeName -> typeName.startsWith("Windows.") }
        .filterNot(definedTypeNames::contains)
        .toSortedSet()
}

private fun WinRtTypeDefinition.referencedTypeNames(): Set<String> = buildSet {
    fun addTypeRef(type: WinRtTypeRef) {
        when (type.kind) {
            WinRtTypeRefKind.Named -> {
                type.qualifiedName?.let(::add)
                type.typeArguments.forEach(::addTypeRef)
            }
            WinRtTypeRefKind.Array -> type.elementType?.let(::addTypeRef)
            WinRtTypeRefKind.GenericTypeParameter,
            WinRtTypeRefKind.MethodTypeParameter,
            WinRtTypeRefKind.Unknown -> Unit
        }
    }

    fun addTypeName(typeName: String) {
        addTypeRef(WinRtTypeRef.fromDisplayName(typeName))
    }

    baseType?.let(::addTypeRef)
    defaultInterface?.let(::addTypeRef)
    implementedInterfaces.forEach { implementation -> addTypeRef(implementation.interfaceType) }
    genericParameters.forEach { parameter -> parameter.constraintTypes.forEach(::addTypeRef) }
    fields.forEach { field ->
        addTypeRef(field.type)
        addTypeName(field.typeName)
    }
    methods.forEach { method ->
        addTypeRef(method.returnType)
        addTypeName(method.returnTypeName)
        method.parameters.forEach { parameter ->
            addTypeRef(parameter.type)
            addTypeName(parameter.typeName)
        }
    }
    properties.forEach { property ->
        addTypeRef(property.type)
        addTypeName(property.typeName)
    }
    events.forEach { event ->
        addTypeRef(event.delegateType)
        addTypeName(event.delegateTypeName)
    }
    activation.activatableFactoryInterface?.let(::addTypeRef)
    activation.staticInterfaces.forEach(::addTypeRef)
    activation.composableFactoryInterface?.let(::addTypeRef)
    activation.factories.forEach { factory -> addTypeRef(factory.interfaceType) }
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
