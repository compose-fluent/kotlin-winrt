package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.name

/** Publishes module-owned AppX resources as package-root-relative content. */
@CacheableTask
abstract class GenerateAppxResourcesArtifactTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val resourceRoots: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceInputs: ConfigurableFileCollection

    init {
        resourceRoots.convention(emptyList())
    }

    @TaskAction
    fun generate() {
        val output = outputFile.get().asFile.toPath().toAbsolutePath().normalize()
        Files.createDirectories(output.parent)
        Files.deleteIfExists(output)
        val inputs = collectAppxResourceInputs(resourceRoots.get().map(Path::of))
            .filterNot { input ->
                input.relativePath.parent == null && input.relativePath.name.equals("AppxManifest.xml", ignoreCase = true)
            }
        ZipOutputStream(Files.newOutputStream(output)).use { zip ->
            inputs.forEach { input ->
                val entryName = input.relativePathString.trimStart('/')
                require(entryName.isNotBlank()) { "AppX resource artifact contains an empty package path." }
                zip.putNextEntry(ZipEntry(entryName))
                Files.newInputStream(input.source).use { stream -> stream.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
