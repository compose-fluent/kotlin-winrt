package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.FileOutputStream
import javax.inject.Inject

abstract class RunWinRTApplicationHostTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    init {
        args.convention(emptyList())
        jvmArgs.convention(emptyList())
        environmentVariables.convention(emptyMap())
    }

    @get:InputFile
    abstract val hostExecutable: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workingDirectory: DirectoryProperty

    @get:Input
    abstract val args: ListProperty<String>

    @get:Input
    abstract val jvmArgs: ListProperty<String>

    @get:Input
    abstract val environmentVariables: MapProperty<String, String>

    @get:Optional
    @get:OutputFile
    abstract val outputLog: RegularFileProperty

    @TaskAction
    fun run() {
        val logFile = outputLog.orNull?.asFile
        if (logFile != null) {
            logFile.parentFile.mkdirs()
            FileOutputStream(logFile).use { output ->
                execHost {
                    standardOutput = output
                    errorOutput = output
                }
            }
        } else {
            execHost()
        }
    }

    private fun execHost(configureOutput: org.gradle.process.ExecSpec.() -> Unit = {}) {
        execOperations.exec { spec ->
            spec.executable = hostExecutable.get().asFile.absolutePath
            spec.workingDir = workingDirectory.get().asFile
            spec.args(args.get())
            spec.environment(environmentVariables.get())
            val configuredJvmArgs = jvmArgs.get()
            if (configuredJvmArgs.isNotEmpty()) {
                spec.environment("KOTLIN_WINRT_JVM_OPTIONS", configuredJvmArgs.joinToString(";"))
            }
            spec.configureOutput()
        }
    }
}
