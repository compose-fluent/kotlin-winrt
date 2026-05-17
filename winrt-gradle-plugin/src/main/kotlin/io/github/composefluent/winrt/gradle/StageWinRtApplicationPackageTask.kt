package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
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
        generateProjectPri(outputRoot)
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
            logger.warn("Skipping application PRI generation because makepri.exe was not found.")
            return
        }
        val configRoot = temporaryDir.toPath().resolve("project-pri-config")
        ProjectPriGenerator.generateApplicationPri(
            makePri,
            outputRoot,
            projectPriRoot,
            configRoot,
            projectPriIndexName(),
            projectPriDefaultQualifierPairs(),
            copiedProjectPriItems,
            logger,
        )
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
