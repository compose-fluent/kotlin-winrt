package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import java.nio.file.Files
import javax.inject.Inject

/** Provides local Kotlin type and property declarations to XamlCompiler's first pass. */
@CacheableTask
abstract class GenerateWinRTXamlApplicationHeaderTask @Inject constructor(
    private val exec: ExecOperations,
    private val fileSystem: FileSystemOperations,
    private val objects: ObjectFactory,
) : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val metadataIndex: RegularFileProperty

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val preparedMetadataManifest: RegularFileProperty

    @get:Internal
    abstract val referenceFiles: ConfigurableFileCollection
    @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.NONE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    val inputReferenceFiles get() = objects.fileCollection().from(
        preparedMetadataManifest.map { readPreparedMetadataCache(it.asFile.toPath()).files }, referenceFiles)

    @get:Classpath abstract val scannerClasspath: ConfigurableFileCollection
    @get:Input abstract val scannerJvmArgs: ListProperty<String>
    @get:Input abstract val sourceRootOwners: MapProperty<String, String>
    @get:Input abstract val emitSources: Property<Boolean>
    @get:Input abstract val assemblyName: Property<String>
    @get:OutputFile abstract val outputFile: RegularFileProperty
    @get:OutputDirectory abstract val sourceOutputDirectory: DirectoryProperty

    init {
        scannerJvmArgs.convention(emptyList())
        sourceRootOwners.convention(emptyMap())
        emitSources.convention(true)
        assemblyName.convention(project.name)
    }

    @TaskAction fun generate() {
        val output = outputFile.get().asFile.toPath().toAbsolutePath().normalize()
        Files.createDirectories(output.parent)
        Files.deleteIfExists(output)
        val sourceOutput = sourceOutputDirectory.get().asFile
        fileSystem.delete { it.delete(sourceOutput) }
        sourceOutput.mkdirs()
        val records = dependencyIdentityFiles.files.flatMap { readDependencyAuthoredMetadataRecords(it, "xamlSchemaRecords") }
        records.groupBy { it.fileName.lowercase() }.forEach { (name, copies) ->
            require(copies.map { it.contentBase64 }.distinct().size == 1) { "Conflicting dependency XAML schema: $name" }
        }
        val dependencySchemas = writeDependencyAuthoredMetadataRecords(records,
            sourceOutput.toPath().resolve("dependency-schemas"))
        sourceOutput.resolve("references.txt").writeText((inputReferenceFiles.files.map { it.absolutePath } +
            dependencySchemas.map { it.toFile().absolutePath }).distinct().sorted().joinToString("\n", postfix = "\n"))
        exec.javaexec { spec ->
            spec.classpath = scannerClasspath
            spec.mainClass.set("io.github.composefluent.winrt.compiler.KotlinWinRTAuthoringScannerCli")
            spec.workingDir(output.parent.toFile())
            spec.jvmArgs(scannerJvmArgs.get() + "-Djava.io.tmpdir=${output.parent}")
            spec.args(buildList {
                add("--xaml-header")
                add("--xaml-assembly-name"); add(assemblyName.get())
                add("--metadata-index"); add(metadataIndex.get().asFile.absolutePath)
                add("--output"); add(output.toString())
                if (emitSources.get()) { add("--xaml-header-sources"); add(sourceOutput.absolutePath) }
                sourceRootOwners.get().toSortedMap().forEach { (root, owner) ->
                    add("--source-root-owner"); add(root); add(owner)
                }
                sourceRoots.files.filter { it.exists() &&
                    !isKotlinWindowsToolkitPluginOwnedAuthoringSourceRoot(it.toPath()) }
                    .sortedBy { it.absolutePath }.forEach {
                    add("--source-root"); add(it.absolutePath)
                }
                (inputReferenceFiles.files + dependencySchemas.map { it.toFile() }).sortedBy { it.absolutePath }.forEach {
                    add("--reference"); add(it.absolutePath)
                }
            })
        }
        check(Files.isRegularFile(output)) { "Kotlin XAML application header was not produced: $output" }
    }
}
