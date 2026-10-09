package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Like CsWinRT's ResolveReferences boundary, resolve only after package restore. */
@DisableCachingByDefault(because = "The reference manifest contains absolute NuGet cache paths")
abstract class ResolveWinRTXamlReferencesTask : DefaultTask() {
    @get:Input abstract val nugetPackages: ListProperty<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val restoreLockFiles: ConfigurableFileCollection
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun resolve() {
        val packages = nugetPackages.get()
        val references = if (packages.isEmpty()) emptyList() else
            readWinAppProjectionWinmdFiles(restoreLockFiles.files, packages)
        GradleFileOperations.writeStringIfChanged(outputFile.get().asFile.toPath(),
            references.map { it.toAbsolutePath().normalize().toString() }.distinct().sorted()
                .joinToString("\n", postfix = "\n"))
    }
}
