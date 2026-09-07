package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
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

@CacheableTask
abstract class StageWinRTApplicationPackageTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeAssetsDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val applicationVariant: Property<String>

    @get:OutputFile
    abstract val resourceResolutionReport: org.gradle.api.file.RegularFileProperty

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

    @get:Input
    @get:Optional
    abstract val windowsSdkRegistryRoots: ListProperty<String>

    @get:Input
    abstract val runtimeIdentifier: Property<String>

    /** Whether the package should declare restored Windows App SDK framework dependencies. */
    @get:Input
    abstract val includeFrameworkPackageDependencies: Property<Boolean>

    @get:Input
    abstract val executableBaseName: Property<String>

    @get:Input
    abstract val projectPriTargetPaths: MapProperty<String, String>

    @get:Input
    abstract val projectPriExcludedFromBuildPaths: SetProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appxManifestFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resolvedNuGetPackageManifestFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val winAppRestoreLockFiles: ConfigurableFileCollection

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
    abstract val projectPriEmbedFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packagePayloadFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appxResourceArchives: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultAppxResourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val rootPackagePayloadFiles: ConfigurableFileCollection

    @get:Input
    abstract val deferredManifestPayloadPaths: ListProperty<String>

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

    @get:Input
    abstract val defaultAppxResourceRoots: ListProperty<String>

    @Input
    fun getDefaultProjectPriResourceRootPath(): String =
        defaultProjectPriResourceRoot.orNull
            ?.asFile
            ?.toPath()
            ?.toAbsolutePath()
            ?.normalize()
            ?.toString()
            .orEmpty()

    @Input
    fun getDefaultAppxResourceRootsPath(): List<String> =
        defaultAppxResourceRoots.get()
            .map { Path.of(it).toAbsolutePath().normalize().toString() }

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
        windowsSdkRegistryRoots.convention(emptyList())
        projectPriTargetPaths.convention(emptyMap())
        projectPriExcludedFromBuildPaths.convention(emptySet())
        deferredManifestPayloadPaths.convention(emptyList())
        defaultAppxResourceRoots.convention(emptyList())
        executableBaseName.convention("app")
        applicationVariant.convention("default")
        includeFrameworkPackageDependencies.convention(true)
        resourceResolutionReport.convention(
            project.layout.buildDirectory.file("kotlin-winrt/reports/appx-resource-resolution.json"),
        )
    }

    @TaskAction
    fun stage() {
        val runtimeAssetsRoot = runtimeAssetsDirectory.get().asFile.toPath()
        val outputRoot = outputDirectory.get().asFile.toPath()
        GradleFileOperations.cleanDirectory(outputRoot)
        Files.createDirectories(outputRoot)
        if (runtimeAssetsRoot.isDirectory()) {
            Files.walk(runtimeAssetsRoot).use { stream ->
                stream.asSequence()
                    .filter { it.isRegularFile() }
                    .forEach { source -> GradleFileOperations.copyFile(source, outputRoot.resolve(source.relativeTo(runtimeAssetsRoot))) }
            }
        }
        val packagePayloadDecisions = resolvePackagePayloadDecisions()
        packagePayloadDecisions.forEach { decision ->
            GradleFileOperations.copyFile(decision.source, outputRoot.resolve(decision.target))
        }
        ApplicationPackagePayloadWriter.writeResolutionReport(
            resourceResolutionReport.get().asFile.toPath(),
            packagePayloadDecisions,
        )
        stageAppxManifest(outputRoot)
        val restoredPackageRoots = winAppRestoreLockFiles.files
            .filter(java.io.File::isFile)
            .let { lockFiles ->
                if (lockFiles.isEmpty()) {
                    emptyList()
                } else {
                    readWinAppRestoredPackageRoots(lockFiles)
                }
            }
        AppxManifestPackageSupport.mergeRuntimeDependenciesAndExtensions(
            manifest = outputRoot.resolve("AppxManifest.xml"),
            packageRoot = outputRoot,
            resolvedPackageManifestFiles = resolvedNuGetPackageManifestFiles.files.map { it.toPath() },
            restoredPackageRoots = restoredPackageRoots,
            runtimeIdentifier = runtimeIdentifier.get(),
            includeFrameworkDependencies = includeFrameworkPackageDependencies.get(),
        )
        WinRTApplicationManifestGenerator.writeApplicationManifest(
            outputRoot,
            executableBaseName.get(),
            winRTManifestProcessorArchitecture(runtimeIdentifier.get()),
            redirectDlls = false,
        )
        val generatedPri = generateProjectPri(
            outputRoot = outputRoot,
            selectedPayloads = packagePayloadDecisions,
            runtimeAssetInputs = runtimeAssetInputs(runtimeAssetsRoot, outputRoot),
        )
        if (generatedPri) {
            val makePri = discoverMakePriExecutable()
                ?: throw GradleException("Cannot dump application PRI because makepri.exe was not found.")
            val priDump = temporaryDir.toPath().resolve("resources.pri.dump.xml")
            if (!ProjectPriGenerator.dumpApplicationPri(
                    makePri = makePri,
                    pri = outputRoot.resolve("resources.pri"),
                    dump = priDump,
                    workingDirectory = outputRoot,
                    logger = logger,
                )
            ) {
                throw GradleException("Failed to dump application PRI for staged app package.")
            }
            ApplicationPackagePayloadWriter.recordGeneratedPri(
                resourceResolutionReport.get().asFile.toPath(),
                outputRoot,
                priDump,
            )
        }
        validateStagedManifestPayload(outputRoot)
    }

    private fun stageAppxManifest(outputRoot: Path) {
        val configuredManifests = appxManifestFiles.files
            .map { it.toPath() }
        val manifest = configuredManifests
            .filter { it.isRegularFile() }
            .sorted()
            .firstOrNull()
            ?: if (configuredManifests.isEmpty()) {
                findAppxManifest(defaultAppxResourceInputs())
            } else {
                null
            }
            ?: return
        val manifestErrors = ProjectPriManifestSupport.validatePackageManifest(manifest)
        if (manifestErrors.isNotEmpty()) {
            throw GradleException(
                "Invalid AppX manifest ${manifest.toAbsolutePath().normalize()}:\n" +
                    manifestErrors.joinToString(separator = "\n") { "- $it" },
            )
        }
        GradleFileOperations.copyFile(manifest, outputRoot.resolve("AppxManifest.xml"))
    }

    private fun defaultAppxResourceInputs(): List<AppxResourceInput> =
        collectAppxResourceInputs(defaultAppxResourceRoots.get().map(Path::of))

    private fun runtimeAssetInputs(runtimeAssetsRoot: Path, outputRoot: Path): List<AppxResourceInput> {
        if (!runtimeAssetsRoot.isDirectory()) return emptyList()
        return Files.walk(runtimeAssetsRoot).use { stream ->
            stream.asSequence()
                .filter(Path::isRegularFile)
                .map { source ->
                    AppxResourceInput(
                        source = outputRoot.resolve(runtimeAssetsRoot.relativize(source).toString()).normalize(),
                        relativePath = runtimeAssetsRoot.relativize(source),
                    )
                }
                .sortedBy { input -> input.relativePathString.lowercase() }
                .toList()
        }
    }

    private fun resolvePackagePayloadDecisions(): List<PackagePayloadDecision> {
        val projectRoot = defaultProjectPriResourceRoot.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
        val dependencyInputs = unpackDependencyAppxResources()
        return ApplicationPackagePayloadWriter.resolvePackagePayloads(
            conventionInputs = defaultAppxResourceInputs(),
            dependencyInputs = dependencyInputs,
            explicitPayloadFiles = packagePayloadFiles.files.map { it.toPath() },
            rootPayloadFiles = rootPackagePayloadFiles.files.map { it.toPath() },
            projectRoot = projectRoot,
            targetPaths = projectPriTargetPaths.get(),
            excludedPaths = projectPriExcludedFromBuildPaths.get(),
        )
    }

    private fun unpackDependencyAppxResources(): List<AppxResourceInput> {
        val archives = appxResourceArchives.files.filter { it.isFile }
        if (archives.isEmpty()) return emptyList()
        val root = temporaryDir.toPath().resolve("dependency-appx-resources")
        GradleFileOperations.cleanDirectory(root)
        Files.createDirectories(root)
        return archives.sortedBy { it.absolutePath.lowercase() }.flatMapIndexed { archiveIndex, archive ->
            val archiveRoot = root.resolve(archiveIndex.toString())
            Files.createDirectories(archiveRoot)
            val entryKeys = linkedSetOf<String>()
            java.util.zip.ZipFile(archive).use { zip ->
                zip.entries().asSequence().filterNot { it.isDirectory }.map { entry ->
                    val normalizedEntryName = entry.name.replace('\\', '/')
                    if (!entryKeys.add(normalizedEntryName.lowercase())) {
                        throw GradleException(
                            "AppX resource artifact contains duplicate package path '${entry.name}': $archive",
                        )
                    }
                    val target = archiveRoot.resolve(normalizedEntryName.replace('/', java.io.File.separatorChar)).normalize()
                    if (!target.startsWith(archiveRoot)) {
                        throw GradleException("AppX resource artifact contains an unsafe path: ${entry.name}")
                    }
                    Files.createDirectories(target.parent)
                    zip.getInputStream(entry).use { input -> Files.newOutputStream(target).use(input::copyTo) }
                    AppxResourceInput(target, Path.of(normalizedEntryName.replace('/', java.io.File.separatorChar)))
                }.toList()
            }
        }
    }

    private fun validateStagedManifestPayload(outputRoot: Path) {
        val manifest = outputRoot.resolve("AppxManifest.xml")
        if (!manifest.isRegularFile()) return
        val deferredReferences = deferredManifestPayloadPaths.get()
            .map { path -> path.toSafeRelativePath("deferred manifest payload path").normalize() }
            .toSet()
        val payloadErrors = ProjectPriManifestSupport.validatePackageManifestPayload(
            manifest,
            outputRoot,
            deferredReferences,
        )
        if (payloadErrors.isNotEmpty()) {
            throw GradleException(
                "Invalid AppX manifest payload references in ${manifest.toAbsolutePath().normalize()}:\n" +
                    payloadErrors.joinToString(separator = "\n") { "- $it" },
            )
        }
    }

    private fun generateProjectPri(
        outputRoot: Path,
        selectedPayloads: Collection<PackagePayloadDecision>,
        runtimeAssetInputs: Collection<AppxResourceInput>,
    ): Boolean {
        if (!generateProjectPri.get() || !isWindowsHost()) {
            return false
        }
        val inputPris = Files.walk(outputRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".pri", ignoreCase = true) }
                .filterNot { it.name.equals("resources.pri", ignoreCase = true) }
                .filterNot { it.name.startsWith("resources.language-", ignoreCase = true) }
                .sorted()
                .toList()
        }
        val projectPriRoot = temporaryDir.toPath().resolve("project-pri")
        GradleFileOperations.cleanDirectory(projectPriRoot)
        Files.createDirectories(projectPriRoot)
        val selectedPayloadInputs = selectedPayloads.mapNotNull { decision ->
            val source = outputRoot.resolve(decision.target).normalize()
            if (source.isRegularFile()) AppxResourceInput(source, decision.target) else null
        }
        val selectedTargetKeys = selectedPayloadInputs.mapTo(linkedSetOf()) { input ->
            input.relativePathString.replace('\\', '/').lowercase()
        }
        val resolvedAppxResourceInputs = selectedPayloadInputs + runtimeAssetInputs.filterNot { input ->
            input.relativePathString.replace('\\', '/').lowercase() in selectedTargetKeys
        }
        val copiedProjectPriItems = ProjectPriInputStager(
            projectPriRoot = projectPriRoot,
            projectPriInitialPath = projectPriInitialPath.get(),
            defaultProjectResourceRoot = defaultProjectPriResourceRoot.orNull?.asFile?.toPath(),
            targetPaths = projectPriTargetPaths.get(),
            excludedFromBuildPaths = projectPriExcludedFromBuildPaths.get(),
        ).stage(
            componentPriFiles = inputPris,
            componentPriBaseRoot = outputRoot,
            appxResourceFiles = resolvedAppxResourceInputs,
            explicitResourceFiles = projectPriResourceFiles.files.map { it.toPath() },
            explicitLayoutFiles = projectPriLayoutFiles.files.map { it.toPath() },
            explicitContentFiles = projectPriContentFiles.files.map { it.toPath() },
            explicitEmbedFiles = projectPriEmbedFiles.files.map { it.toPath() },
            defaultResourceFiles = defaultProjectPriResourceFiles.files.map { it.toPath() },
            defaultLayoutFiles = defaultProjectPriLayoutFiles.files.map { it.toPath() },
            defaultContentFiles = defaultProjectPriContentFiles.files.map { it.toPath() },
            includeDefaultProjectResources = enableDefaultProjectPriResources.get(),
        )
        if (copiedProjectPriItems.isEmpty()) {
            return false
        }
        ApplicationPackagePayloadWriter.copyPackagePayloads(projectPriRoot, outputRoot, copiedProjectPriItems)
        val makePri = discoverMakePriExecutable() ?: run {
            throw GradleException("Cannot generate application PRI because makepri.exe was not found.")
        }
        val configRoot = temporaryDir.toPath().resolve("project-pri-config")
        if (!ProjectPriGenerator.generateApplicationPri(
            makePri,
            outputRoot,
            projectPriRoot,
            configRoot,
            projectPriIndexName(),
            projectPriDefaultQualifierPairs(),
            copiedProjectPriItems,
            logger,
        )) {
            throw GradleException("Failed to generate application PRI for staged app package.")
        }
        return true
    }

    private fun projectPriDefaultLanguageValue(): String =
        ProjectPriManifestSupport.defaultLanguage(projectPriDefaultLanguage.get(), appxManifestFiles.files)

    private fun projectPriDefaultQualifierPairs(): List<Pair<String, String>> =
        ProjectPriManifestSupport.fullIndexDefaultQualifiers(projectPriDefaultLanguageValue(), projectPriDefaultQualifiers.get())

    private fun projectPriIndexName(): String =
        ProjectPriManifestSupport.indexName(projectPriIndexName.get(), projectPriFallbackIndexName.get(), appxManifestFiles.files)

    private fun discoverMakePriExecutable(): Path? {
        return ProjectPriToolResolver.makePriExecutable(
            configuredExecutable = makePriExecutable.get(),
            windowsSdkVersion = windowsSdkVersion.get(),
            runtimeIdentifier = runtimeIdentifier.get(),
            registryRoots = windowsSdkRegistryRoots.get().orNullIfEmpty(),
        )
    }

}
