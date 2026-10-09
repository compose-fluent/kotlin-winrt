package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.FileOutputStream
import javax.inject.Inject

abstract class RunWinAppHostTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    init {
        args.convention(emptyList())
        jvmArgs.convention(emptyList())
        environmentVariables.convention(emptyMap())
        supportsXamlHotReload.convention(false)
        sdkPreview.convention(false)
        designPreview.convention(project.providers.environmentVariable(io.github.composefluent.winrt.runtime.WinRTXamlHotReloadProtocol.PREVIEW_ENVIRONMENT).map { it == "1" }.orElse(false))
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

    /** Configuration fact exported to the IDE; the runtime still requires an explicit development session. */
    @get:Internal
    abstract val supportsXamlHotReload: org.gradle.api.provider.Property<Boolean>

    @get:Internal
    abstract val sdkPreview: org.gradle.api.provider.Property<Boolean>

    @get:Internal
    abstract val designPreview: org.gradle.api.provider.Property<Boolean>

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
            if (designPreview.get()) {
                check(supportsXamlHotReload.get()) { "The XAML preview host requires a JVM WinUI development launch." }
                spec.args(io.github.composefluent.winrt.runtime.WinRTXamlHotReloadProtocol.PREVIEW_ARGUMENT)
            }
            val configuredEnvironment = environmentVariables.get()
            spec.environment(configuredEnvironment)
            val configuredJvmArgs = jvmArgs.get()
            if (configuredJvmArgs.isNotEmpty()) {
                spec.environment("KOTLIN_WINRT_JVM_OPTIONS", configuredJvmArgs.joinToString(";"))
            } else if (!configuredEnvironment.containsKey("KOTLIN_WINRT_JVM_OPTIONS")) {
                spec.environment.remove("KOTLIN_WINRT_JVM_OPTIONS")
            }
            spec.configureOutput()
        }
    }
}
