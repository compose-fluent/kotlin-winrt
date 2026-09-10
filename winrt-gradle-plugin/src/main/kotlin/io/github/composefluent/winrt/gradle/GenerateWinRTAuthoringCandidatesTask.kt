package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
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

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    @get:Classpath
    abstract val scannerClasspath: ConfigurableFileCollection

    @get:Input
    abstract val scannerJvmArgs: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        scannerJvmArgs.convention(emptyList())
    }

    @TaskAction
    fun scan() {
        val output = outputFile.get().asFile.toPath().toAbsolutePath().normalize()
        Files.createDirectories(output.parent)
        val roots = sourceRoots.files
            .map { file -> file.toPath().toAbsolutePath().normalize() }
            .filter { path -> Files.exists(path) }
        if (roots.isEmpty()) {
            Files.writeString(output, "")
            return
        }
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
                    add(output.toString())
                    roots.forEach { root ->
                        add("--source-root")
                        add(root.toString())
                    }
                },
            )
        }
        check(Files.isRegularFile(output)) {
            "kotlin-winrt authoring scanner did not produce $output"
        }
    }
}
