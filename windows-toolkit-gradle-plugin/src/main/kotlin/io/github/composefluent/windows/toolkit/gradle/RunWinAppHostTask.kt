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
import org.gradle.work.DisableCachingByDefault
import org.gradle.process.ExecOperations
import java.io.FileOutputStream
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import javax.inject.Inject

@DisableCachingByDefault(because = "Runs an application against the local Windows environment")
abstract class RunWinAppHostTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    init {
        args.convention(emptyList())
        jvmArgs.convention(emptyList())
        environmentVariables.convention(emptyMap())
        supportsXamlHotReload.convention(false)
        supportsXamlPreview.convention(supportsXamlHotReload)
        sdkPreview.convention(false)
        designPreview.convention(project.providers.environmentVariable(io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT).map { it == "1" }.orElse(false))
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
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
    abstract val supportsXamlPreview: org.gradle.api.provider.Property<Boolean>

    @get:Internal
    abstract val nativeCompilerHome: org.gradle.api.provider.Property<String>

    @get:Internal
    val nativeDebugDirectory = project.providers.environmentVariable(NATIVE_DEBUG_DIRECTORY)

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
        val applicationArguments = args.get().toMutableList()
        if (designPreview.get()) {
            check(supportsXamlPreview.get()) { "The XAML preview host requires a supported WinUI development launch." }
            applicationArguments += io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol.PREVIEW_ARGUMENT
        }
        val debugDirectory = nativeDebugDirectory.orNull?.takeIf { nativeCompilerHome.orNull?.isNotBlank() == true }
        val debugScript = if (debugDirectory == null) null else temporaryDir.toPath().resolve("native-debug.ps1").also { script ->
            Files.writeString(script, nativeDebugLauncherScript)
            Files.writeString(temporaryDir.toPath().resolve("native-debug.json"), buildJsonObject {
                put("executable", hostExecutable.get().asFile.absolutePath)
                put("directory", workingDirectory.get().asFile.absolutePath)
                put("debugDirectory", debugDirectory)
                put("arguments", JsonArray(applicationArguments.map(::JsonPrimitive)))
            }.toString())
        }
        execOperations.exec { spec ->
            spec.executable = if (debugScript == null) hostExecutable.get().asFile.absolutePath else "powershell.exe"
            spec.workingDir = workingDirectory.get().asFile
            if (debugScript == null) spec.args(applicationArguments)
            else spec.args("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", debugScript.toString(),
                "-Configuration", temporaryDir.toPath().resolve("native-debug.json").toString())
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
