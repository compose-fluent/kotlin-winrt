package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

@CacheableTask
abstract class GenerateWinAppConfigurationTask : DefaultTask() {
    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @get:Input
    abstract val includeToolingPackages: Property<Boolean>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        nugetPackages.convention(emptyList())
        includeToolingPackages.convention(false)
    }

    @TaskAction
    fun generate() {
        val packageSpecs = nugetPackages.get() + dependencyIdentityFiles.files.flatMap(::readNuGetPackages)
        val packages = resolveWinAppPackagePins(
            packageSpecs = packageSpecs,
            toolingPackages = if (packageSpecs.isNotEmpty() || includeToolingPackages.get()) {
                WinAppConfigurationDefaults.toolingPackages
            } else {
                emptyList()
            },
        )
        val output = outputFile.get().asFile.toPath()
        Files.createDirectories(output.parent)
        Files.writeString(output, renderWinAppConfiguration(packages))
    }
}
