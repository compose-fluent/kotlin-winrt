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
import io.github.composefluent.winrt.ide.model.WinRTPreparedLaunch
import javax.inject.Inject

@DisableCachingByDefault(because = "Runs an application against the local Windows environment")
abstract class RunWinAppHostTask @Inject constructor(
    private val execOperations: ExecOperations,
    private val fileSystem: org.gradle.api.file.FileSystemOperations,
) : DefaultTask() {
    init {
        restartSlot.convention(project.providers.environmentVariable(WinRTPreparedLaunch.SLOT_ENVIRONMENT).orElse(""))
        args.convention(emptyList())
        jvmArgs.convention(emptyList())
        environmentVariables.convention(emptyMap())
        supportsXamlHotReload.convention(false)
        supportsXamlPreview.convention(supportsXamlHotReload)
        sdkPreview.convention(false)
        detach.convention(project.providers.environmentVariable("KOTLIN_WINRT_RELEASE_BUILD_LOCK").map { it == "1" }.orElse(false))
        noLaunch.convention(project.providers.environmentVariable("KOTLIN_WINRT_REGISTER_ONLY").map { it == "1" }.orElse(false))
        previewHostExecutable.convention(deployedExecutable)
        designPreview.convention(project.providers.environmentVariable(io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT).map { it == "1" }.orElse(false))
    }

    @get:Input abstract val restartSlot: org.gradle.api.provider.Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val hostExecutable: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workingDirectory: DirectoryProperty

    @get:Input
    abstract val args: ListProperty<String>

    @org.gradle.api.tasks.options.Option(option = "args", description = "Application arguments")
    fun applicationArgs(value: String) {
        args.set(org.apache.tools.ant.types.Commandline.translateCommandline(value).toList())
    }

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

    /** Separate the running Native payload from linker/staging outputs. Rebuilds
     * may update staging while the previous development process remains alive. */
    @get:Internal abstract val deploymentDirectory: DirectoryProperty
    @get:Internal abstract val previewBuildInputs: org.gradle.api.file.ConfigurableFileCollection
    @get:Internal abstract val developmentBuildTask: org.gradle.api.provider.Property<String>
    @get:Internal val deployedExecutable: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>
        get() = project.layout.file(deploymentDirectory.zip(hostExecutable) { directory, executable ->
            WinRTPreparedLaunch.deployment(directory.asFile.toPath(), restartSlot.get()).resolve(executable.asFile.name).toFile()
        }).orElse(hostExecutable)
    @get:Internal abstract val previewHostExecutable: RegularFileProperty
    @get:Input abstract val detach: org.gradle.api.provider.Property<Boolean>
    @get:Input
    @get:org.gradle.api.tasks.options.Option(option = "no-launch", description = "Prepare the isolated development payload without launching it")
    abstract val noLaunch: org.gradle.api.provider.Property<Boolean>

    @get:Optional
    @get:OutputFile
    abstract val outputLog: RegularFileProperty

    @TaskAction
    fun run() {
        deploymentDirectory.orNull?.asFile?.let { destination ->
            val source = workingDirectory.get().asFile.toPath().toAbsolutePath().normalize()
            val target = WinRTPreparedLaunch.deployment(destination.toPath(), restartSlot.get())
            require(restartSlot.get().isEmpty() || noLaunch.get() && nativeCompilerHome.orNull?.isNotBlank() == true) {
                "Prepared restart payloads require an isolated Native no-launch task."
            }
            require(!target.startsWith(source) && !source.startsWith(target)) {
                "The development deployment must be separate from the staging directory."
            }
            fileSystem.sync { it.from(source); it.into(target) }
            if (nativeCompilerHome.orNull?.isNotBlank() == true && previewBuildInputs.files.isNotEmpty()) {
                io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.write(
                    target.parent.resolve(io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.FILE_NAME),
                    previewBuildInputs.files.map { it.toPath() })
            }
        }
        if (nativeCompilerHome.orNull?.isNotBlank() == true && deploymentDirectory.isPresent) {
            // Keep the exact resolved launch contract for a failed-replacement rollback.
            val environment = mutableMapOf<String, String>().also(::configureEnvironment)
            WinRTPreparedLaunch(deployedExecutable.get().asFile.absolutePath, applicationArguments(), environment)
                .write(runtimeDirectory().toPath().resolve(WinRTPreparedLaunch.FILE_NAME))
        }
        if (noLaunch.get()) return
        if (detach.get() && nativeDebugDirectory.orNull.isNullOrBlank()) {
            val log = outputLog.orNull?.asFile ?: temporaryDir.resolve("application.log")
            log.parentFile.mkdirs()
            ProcessBuilder(listOf(deployedExecutable.get().asFile.absolutePath) + applicationArguments())
                .directory(runtimeDirectory()).redirectErrorStream(true).redirectOutput(log)
                .apply { configureEnvironment(environment()) }.start().outputStream.close()
            logger.lifecycle("Development application started; output: ${log.absolutePath}")
            return
        }
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

    private fun runtimeDirectory() = deploymentDirectory.orNull?.asFile?.let {
        WinRTPreparedLaunch.deployment(it.toPath(), restartSlot.get()).toFile()
    } ?: workingDirectory.get().asFile

    private fun applicationArguments(): List<String> {
        val applicationArguments = args.get().toMutableList()
        if (designPreview.get()) {
            check(supportsXamlPreview.get()) { "The XAML preview host requires a supported WinUI development launch." }
            applicationArguments += io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol.PREVIEW_ARGUMENT
        }
        return applicationArguments
    }

    private fun configureEnvironment(environment: MutableMap<String, String>) {
        val configured = environmentVariables.get()
        environment.putAll(configured)
        if (deploymentDirectory.isPresent) environment["KOTLIN_WINRT_RUNTIME_ASSETS_ROOT"] = runtimeDirectory().absolutePath
        val options = jvmArgs.get()
        if (options.isNotEmpty()) environment["KOTLIN_WINRT_JVM_OPTIONS"] = options.joinToString(";")
        else if (!configured.containsKey("KOTLIN_WINRT_JVM_OPTIONS")) environment.remove("KOTLIN_WINRT_JVM_OPTIONS")
    }

    private fun execHost(configureOutput: org.gradle.process.ExecSpec.() -> Unit = {}) {
        val applicationArguments = applicationArguments()
        val debugDirectory = nativeDebugDirectory.orNull?.takeIf { nativeCompilerHome.orNull?.isNotBlank() == true }
        val debugScript = if (debugDirectory == null) null else temporaryDir.toPath().resolve("native-debug.ps1").also { script ->
            Files.writeString(script, nativeDebugLauncherScript)
            Files.writeString(temporaryDir.toPath().resolve("native-debug.json"), buildJsonObject {
                put("executable", deployedExecutable.get().asFile.absolutePath)
                put("directory", runtimeDirectory().absolutePath)
                put("debugDirectory", debugDirectory)
                put("arguments", JsonArray(applicationArguments.map(::JsonPrimitive)))
            }.toString())
        }
        execOperations.exec { spec ->
            spec.executable = if (debugScript == null) deployedExecutable.get().asFile.absolutePath else "powershell.exe"
            spec.workingDir = runtimeDirectory()
            if (debugScript == null) spec.args(applicationArguments)
            else spec.args("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", debugScript.toString(),
                "-Configuration", temporaryDir.toPath().resolve("native-debug.json").toString())
            val environment = spec.environment.mapValues { it.value.toString() }.toMutableMap()
            configureEnvironment(environment)
            spec.setEnvironment(environment)
            spec.configureOutput()
        }
    }
}
