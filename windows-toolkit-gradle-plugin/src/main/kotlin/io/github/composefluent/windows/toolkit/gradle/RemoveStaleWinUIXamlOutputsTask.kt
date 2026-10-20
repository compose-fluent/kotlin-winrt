package io.github.composefluent.windows.toolkit.gradle

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Generated-output cleanup, like CsWinRTCleanGenerateProjectionOutputs, belongs to execution. */
@DisableCachingByDefault(because = "Removes obsolete outputs owned by other XAML tasks")
abstract class RemoveStaleWinUIXamlOutputsTask : DefaultTask() {
    @get:Internal abstract val generatedDirectory: DirectoryProperty
    @get:Internal abstract val intermediateDirectory: DirectoryProperty
    @get:Input abstract val xamlSourcesPresent: Property<Boolean>
    @get:Input abstract val exportsLibrarySchema: Property<Boolean>
    @get:Inject protected abstract val fileSystemOperations: FileSystemOperations

    init {
        onlyIf { !xamlSourcesPresent.get() }
    }

    @TaskAction
    fun removeStaleOutputs() {
        // Do not enumerate these output directories in a Provider used by Delete:
        // Gradle can query that provider while constructing/serializing the task graph.
        val preserveApplication = exportsLibrarySchema.get()
        val obsolete = listOf(generatedDirectory, intermediateDirectory).flatMap { directory ->
            directory.get().asFile.listFiles().orEmpty().filter {
                // The reference resolver is also used by plain Kotlin schema libraries.
                // It owns this shared manifest even when there are no XAML pages.
                it.name != "library" && it.name != "compiler-references.txt" &&
                    !(preserveApplication && it.name == "application")
            }
        }
        didWork = fileSystemOperations.delete { it.delete(obsolete) }.didWork
    }
}
