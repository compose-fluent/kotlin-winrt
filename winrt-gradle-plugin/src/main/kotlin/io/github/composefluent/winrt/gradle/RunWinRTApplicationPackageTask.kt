package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/** Package-aware development launch, corresponding to CsWinRT's MsixPackage launch profiles. */
@DisableCachingByDefault(because = "Registers and launches a development package in the local Windows user profile.")
abstract class RunWinRTApplicationPackageTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val deploymentDirectory: DirectoryProperty

    @get:Input
    abstract val packageType: Property<String>

    @get:Input
    abstract val selfContained: Property<Boolean>

    @get:Input
    abstract val applicationVariant: Property<String>

    @get:Input
    @get:Option(option = "args", description = "Windows command line to pass to the packaged application.")
    abstract val args: Property<String>

    @get:Input
    @get:Option(option = "detach", description = "Launch the packaged app and return without waiting for it to exit.")
    abstract val detach: Property<Boolean>

    @get:Input
    @get:Option(option = "debug-output", description = "Capture native debug output and exceptions through WinApp CLI.")
    abstract val debugOutput: Property<Boolean>

    @get:Input
    @get:Option(option = "no-launch", description = "Register the development package without launching it.")
    abstract val noLaunch: Property<Boolean>

    @get:Input
    abstract val winAppCliExecutable: Property<String>

    @get:Input
    abstract val winAppCliVersion: Property<String>

    @get:Input
    abstract val winAppCliPackageSha512: Property<String>

    @get:Internal
    abstract val winAppCliCacheDirectory: DirectoryProperty

    @get:Internal
    abstract val winAppWorkspace: DirectoryProperty

    @get:Input
    abstract val offline: Property<Boolean>

    init {
        packageType.convention(WindowsPackageType.Packaged.name)
        selfContained.convention(false)
        applicationVariant.convention("default")
        args.convention("")
        detach.convention(false)
        debugOutput.convention(false)
        noLaunch.convention(false)
        winAppCliExecutable.convention("winapp")
        winAppCliVersion.convention(WinAppCliDefaults.VERSION)
        winAppCliPackageSha512.convention(WinAppCliDefaults.PACKAGE_SHA512)
        offline.convention(false)
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        if (!isWindowsHost()) {
            throw GradleException("Packaged application development runs require Windows.")
        }
        if (packageType.get() != WindowsPackageType.Packaged.name) {
            throw GradleException(
                "Configure winRT.application { packageType = WindowsPackageType.Packaged } " +
                    "before running a packaged application.",
            )
        }
        // WinApp 0.6 folder-mode run always adds framework dependencies; it has no --self-contained option.
        if (selfContained.get()) {
            throw GradleException(
                "WinApp CLI packaged development runs do not support self-contained Windows App SDK deployment. " +
                    "Use winRT.application { windowsAppSdkDeployment = WindowsAppSdkDeployment.FrameworkDependent } " +
                    "for development runs, " +
                    "or install and activate the self-contained MSIX.",
            )
        }
        val input = packageDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val output = deploymentDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        if (output.startsWith(input) || input.startsWith(output)) {
            throw GradleException("Packaged run deployment directory must be separate from its build input: $output and $input.")
        }
        val manifest = input.resolve("AppxManifest.xml")
        val errors = ProjectPriManifestSupport.validatePackageManifest(manifest) +
            ProjectPriManifestSupport.validatePackageManifestPayload(manifest, input)
        if (errors.isNotEmpty()) {
            throw GradleException("Cannot run packaged application from $input:\n${errors.joinToString("\n")}")
        }
        val cli = WinAppCliSupport(
            configuredExecutable = winAppCliExecutable.get(),
            cliVersion = winAppCliVersion.get(),
            packageSha512 = winAppCliPackageSha512.get(),
            cliCacheDirectory = winAppCliCacheDirectory.get().asFile.toPath(),
            offline = offline.get(),
            logger = logger,
        ).resolve()
        val arguments = buildList {
            add("run")
            add(input.toString())
            add("--manifest")
            add(manifest.toString())
            add("--output-appx-directory")
            add(output.toString())
            if (detach.get()) add("--detach")
            if (debugOutput.get()) add("--debug-output")
            if (noLaunch.get()) add("--no-launch")
            if (args.get().isNotEmpty()) {
                add("--args=${args.get()}")
            }
        }
        logger.lifecycle("Running packaged Kotlin/WinRT application ${applicationVariant.get()}")
        // Gradle owns the CLI process so output is streamed and cancellation reaches the runner.
        execOperations.exec { spec ->
            spec.commandLine(winAppCliCommandLine(cli.command, arguments))
            spec.workingDir = winAppWorkspace.get().asFile
        }
    }
}
