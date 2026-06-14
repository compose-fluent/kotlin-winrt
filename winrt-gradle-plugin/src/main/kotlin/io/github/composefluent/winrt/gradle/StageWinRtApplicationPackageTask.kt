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
abstract class StageWinRtApplicationPackageTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeAssetsDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

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
    abstract val runtimeIdentifier: Property<String>

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
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val rootPackagePayloadFiles: ConfigurableFileCollection

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

    @Input
    fun getDefaultProjectPriResourceRootPath(): String =
        defaultProjectPriResourceRoot.orNull
            ?.asFile
            ?.toPath()
            ?.toAbsolutePath()
            ?.normalize()
            ?.toString()
            .orEmpty()

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
        projectPriTargetPaths.convention(emptyMap())
        projectPriExcludedFromBuildPaths.convention(emptySet())
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
        stageAppxManifest(outputRoot)
        stagePackagePayloads(outputRoot)
        stageRootPackagePayloads(outputRoot)
        generateProjectPri(outputRoot)
        validateStagedManifestPayload(outputRoot)
    }

    private fun stageAppxManifest(outputRoot: Path) {
        val manifest = appxManifestFiles.files
            .map { it.toPath() }
            .filter { it.isRegularFile() }
            .sorted()
            .firstOrNull() ?: return
        val manifestErrors = ProjectPriManifestSupport.validatePackageManifest(manifest)
        if (manifestErrors.isNotEmpty()) {
            throw GradleException(
                "Invalid AppX manifest ${manifest.toAbsolutePath().normalize()}:\n" +
                    manifestErrors.joinToString(separator = "\n") { "- $it" },
            )
        }
        GradleFileOperations.copyFile(manifest, outputRoot.resolve("AppxManifest.xml"))
    }

    private fun validateStagedManifestPayload(outputRoot: Path) {
        val manifest = outputRoot.resolve("AppxManifest.xml")
        if (!manifest.isRegularFile()) return
        val payloadErrors = ProjectPriManifestSupport.validatePackageManifestPayload(manifest, outputRoot)
        if (payloadErrors.isNotEmpty()) {
            throw GradleException(
                "Invalid AppX manifest payload references in ${manifest.toAbsolutePath().normalize()}:\n" +
                    payloadErrors.joinToString(separator = "\n") { "- $it" },
            )
        }
    }

    private fun stagePackagePayloads(outputRoot: Path) {
        val projectRoot = defaultProjectPriResourceRoot.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
        val targetPaths = projectPriTargetPaths.get()
        val excludedPaths = projectPriExcludedFromBuildPaths.get()
        packagePayloadFiles.files.asSequence()
            .map { it.toPath() }
            .filter { it.toAbsolutePath().normalize().toString() !in excludedPaths }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (!Files.exists(source)) {
                    throw GradleException("Declared package payload does not exist: ${source.toAbsolutePath().normalize()}")
                }
                val explicitTarget = targetPaths[source.toAbsolutePath().normalize().toString()]
                    ?.toSafeRelativePath("packagePayload target path")
                if (source.isDirectory()) {
                    Files.walk(source).use { stream ->
                        stream.asSequence()
                            .filter { it.isRegularFile() }
                            .filter { it.toAbsolutePath().normalize().toString() !in excludedPaths }
                            .sorted()
                            .forEach { child ->
                                val relativeTarget = explicitTarget?.resolve(child.relativeTo(source))
                                    ?: child.defaultPackagePayloadTarget(source, projectRoot)
                                GradleFileOperations.copyFile(child, outputRoot.resolve(relativeTarget))
                            }
                    }
                } else if (source.isRegularFile()) {
                    val relativeTarget = explicitTarget ?: source.defaultPackagePayloadTarget(source.parent, projectRoot)
                    GradleFileOperations.copyFile(source, outputRoot.resolve(relativeTarget))
                }
            }
    }

    private fun stageRootPackagePayloads(outputRoot: Path) {
        rootPackagePayloadFiles.files.asSequence()
            .map { it.toPath() }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (!source.isRegularFile()) {
                    throw GradleException("Declared root package payload must be a file: ${source.toAbsolutePath().normalize()}")
                }
                GradleFileOperations.copyFile(source, outputRoot.resolve(source.name))
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
        val projectPriRoot = temporaryDir.toPath().resolve("project-pri")
        GradleFileOperations.cleanDirectory(projectPriRoot)
        Files.createDirectories(projectPriRoot)
        val copiedProjectPriItems = ProjectPriInputStager(
            projectPriRoot = projectPriRoot,
            projectPriInitialPath = projectPriInitialPath.get(),
            defaultProjectResourceRoot = defaultProjectPriResourceRoot.orNull?.asFile?.toPath(),
            targetPaths = projectPriTargetPaths.get(),
            excludedFromBuildPaths = projectPriExcludedFromBuildPaths.get(),
        ).stage(
            componentPriFiles = inputPris,
            componentPriBaseRoot = outputRoot,
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
            return
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
    }

    private fun projectPriDefaultLanguageValue(): String =
        ProjectPriManifestSupport.defaultLanguage(projectPriDefaultLanguage.get(), appxManifestFiles.files)

    private fun projectPriDefaultQualifierPairs(): List<Pair<String, String>> =
        ProjectPriManifestSupport.fullIndexDefaultQualifiers(projectPriDefaultLanguageValue(), projectPriDefaultQualifiers.get())

    private fun projectPriIndexName(): String =
        ProjectPriManifestSupport.indexName(projectPriIndexName.get(), projectPriFallbackIndexName.get(), appxManifestFiles.files)

    private fun discoverMakePriExecutable(): Path? {
        return ProjectPriToolResolver.makePriExecutable(makePriExecutable.get(), windowsSdkVersion.get(), runtimeIdentifier.get())
    }

}

private fun Path.defaultPackagePayloadTarget(fallbackRoot: Path, projectRoot: Path?): Path {
    val normalized = toAbsolutePath().normalize()
    return if (projectRoot != null && normalized.startsWith(projectRoot)) {
        normalized.relativeTo(projectRoot)
    } else {
        normalized.relativeTo(fallbackRoot)
    }
}
