package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

/** Only the stable registrar declaration is needed before type inference.
 * Its typed registration bodies are filled by the compiler in the owning module.
 */
@CacheableTask
abstract class GenerateWinUiXamlLibraryRegistrarTask : DefaultTask() {
    @get:Input abstract val assemblyName: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        val name = "KotlinXamlLibraryDefinitions_" + assemblyName.get().replace(Regex("[^A-Za-z0-9_]"), "_")
        val directory = outputDirectory.get().asFile
        directory.mkdirs()
        directory.listFiles()?.filter { it.isFile && it.name != "$name.kt" }?.forEach { it.delete() }
        GradleFileOperations.writeStringIfChanged(directory.resolve("$name.kt").toPath(), """
            package io.github.composefluent.winrt.generated.xaml
            object $name {
                private val registration: Unit = registerTypes()
                private fun registerTypes() { error("XAML library registrar was not compiled by kotlin-winrt") }
                fun registerAll() { registration }
            }
        """.trimIndent() + "\n")
    }
}
