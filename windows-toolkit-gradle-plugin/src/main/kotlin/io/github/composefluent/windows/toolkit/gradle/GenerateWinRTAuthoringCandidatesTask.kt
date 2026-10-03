package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.nio.file.Files
import javax.inject.Inject

/**
 * Scans project Kotlin declarations for authored WinRT classes.
 *
 * This source-sensitive step is deliberately separate from WinMD projection
 * generation. Its output is a semantic candidate file, so unchanged source
 * declarations leave the imported projection task up-to-date even when the
 * scanner itself has to inspect a changed source file.
 */
@CacheableTask
abstract class GenerateWinRTAuthoringCandidatesTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataIndex: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val xamlDeclarations: RegularFileProperty

    @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilationXamlDeclarations: ConfigurableFileCollection

    @get:Input abstract val sourceRootOwners: MapProperty<String, String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val xamlSupportSources: ConfigurableFileCollection

    @get:Classpath
    abstract val scannerClasspath: ConfigurableFileCollection

    @get:Input
    abstract val scannerJvmArgs: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        scannerJvmArgs.convention(emptyList())
        sourceRootOwners.convention(emptyMap())
    }

    @TaskAction
    fun scan() {
        val output = outputFile.get().asFile.toPath().toAbsolutePath().normalize()
        Files.createDirectories(output.parent)
        val temporaryOutput = output.resolveSibling(".${output.fileName}.tmp")
        val roots = (sourceRoots.files.filterNot { isKotlinWindowsToolkitPluginOwnedAuthoringSourceRoot(it.toPath()) } + xamlSupportSources.files)
            .map { file -> file.toPath().toAbsolutePath().normalize() }
            .filter { path -> Files.exists(path) }
        if (roots.isEmpty()) {
            GradleFileOperations.writeStringIfChanged(output, "")
            return
        }
        try {
            execOperations.javaexec { spec ->
                spec.classpath = scannerClasspath
                spec.mainClass.set("io.github.composefluent.winrt.compiler.KotlinWinRTAuthoringScannerCli")
                spec.workingDir(output.parent.toFile())
                spec.jvmArgs(
                    scannerJvmArgs.get() + "-Djava.io.tmpdir=${output.parent}",
                )
                spec.args(
                    buildList {
                        add("--metadata-index")
                        add(metadataIndex.get().asFile.absolutePath)
                        add("--output")
                        add(temporaryOutput.toString())
                        (listOfNotNull(xamlDeclarations.orNull?.asFile) + compilationXamlDeclarations.files).distinct().sortedBy { it.absolutePath }.forEach { declarations ->
                            add("--xaml-declarations")
                            add(declarations.absolutePath)
                        }
                        sourceRootOwners.get().toSortedMap().forEach { (root, owner) ->
                            add("--source-root-owner"); add(root); add(owner)
                        }
                        roots.forEach { root ->
                            add("--source-root")
                            add(root.toString())
                        }
                    }
                )
            }
            check(Files.isRegularFile(temporaryOutput)) {
                "kotlin-winrt authoring scanner did not produce $temporaryOutput"
            }
            GradleFileOperations.writeStringIfChanged(output, Files.readString(temporaryOutput))
        } finally {
            Files.deleteIfExists(temporaryOutput)
        }
    }
}
