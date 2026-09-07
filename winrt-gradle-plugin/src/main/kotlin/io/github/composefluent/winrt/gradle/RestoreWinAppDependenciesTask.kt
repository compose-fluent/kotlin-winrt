package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

@DisableCachingByDefault(because = "WinApp restore writes absolute NuGet-cache paths and updates external package caches.")
abstract class RestoreWinAppDependenciesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configurationFile: RegularFileProperty

    /** Directory used as the WinApp restore base so NuGet.Config hierarchy is preserved. */
    @get:Internal
    abstract val restoreBaseDirectory: DirectoryProperty

    @Input
    fun getRestoreBaseDirectoryPath(): String =
        restoreBaseDirectory.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()?.toString().orEmpty()

    /** Optional explicit NuGet.Config; it is an input for invalidation and must live in its parent directory. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nugetConfigFile: RegularFileProperty

    @get:OutputDirectory
    abstract val winAppDirectory: DirectoryProperty

    @get:Internal
    abstract val winmdLockFile: RegularFileProperty

    @get:Input
    abstract val winAppCliExecutable: Property<String>

    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

    @get:Input
    abstract val restoreEnabled: Property<Boolean>

    @get:Input
    abstract val includeToolingPackages: Property<Boolean>

    @get:Input
    abstract val winAppCliVersion: Property<String>

    @get:Input
    abstract val winAppCliPackageSha512: Property<String>

    @get:Internal
    abstract val winAppCliCacheDirectory: DirectoryProperty

    @get:Input
    abstract val offline: Property<Boolean>

    init {
        doNotTrackState("WinApp CLI restore updates external package caches and may inspect locked NuGet metadata.")
        winAppCliExecutable.convention("winapp")
        winAppCliVersion.convention(WinAppCliDefaults.VERSION)
        winAppCliPackageSha512.convention(WinAppCliDefaults.PACKAGE_SHA512)
        nugetPackages.convention(emptyList())
        restoreEnabled.convention(true)
        includeToolingPackages.convention(false)
        offline.convention(false)
    }

    @TaskAction
    fun restore() {
        if (!isWindowsHost()) {
            throw GradleException("WinApp CLI restore requires a Windows host.")
        }
        val config = configurationFile.get().asFile.toPath().toAbsolutePath().normalize()
        val workspace = config.parent
        val configuredNuGetConfig = nugetConfigFile.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
        if (configuredNuGetConfig != null) {
            if (!Files.isRegularFile(configuredNuGetConfig)) {
                throw GradleException("Configured NuGet.Config does not exist: $configuredNuGetConfig")
            }
            if (!configuredNuGetConfig.fileName.toString().equals("NuGet.Config", ignoreCase = true)) {
                throw GradleException(
                    "Configured NuGet config must be named NuGet.Config so WinApp CLI can discover it: " +
                        configuredNuGetConfig,
                )
            }
        }
        val restoreBase = restoreBaseDirectory.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
            ?: configuredNuGetConfig?.parent
            ?: project.projectDir.toPath().toAbsolutePath().normalize()
        if (!Files.isDirectory(restoreBase)) {
            throw GradleException("WinApp restore base directory does not exist: $restoreBase")
        }
        val output = winAppDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        require(output.parent == workspace) {
            "WinApp restore output must be the .winapp directory directly below its generated workspace: $output"
        }
        if (!restoreEnabled.get()) {
            GradleFileOperations.cleanDirectory(output)
            return
        }

        val packageSpecs = nugetPackages.get() + dependencyIdentityFiles.files.flatMap(::readNuGetPackages)
        if (packageSpecs.isEmpty() && !includeToolingPackages.get()) {
            GradleFileOperations.cleanDirectory(output)
            Files.createDirectories(output)
            Files.createDirectories(output.resolve("bin"))
            Files.writeString(
                winmdLockFile.get().asFile.toPath(),
                """
                {
                  "schema": ${WinAppRestoreLockfileReader.SUPPORTED_SCHEMA},
                  "packages": []
                }
                """.trimIndent() + System.lineSeparator(),
            )
            return
        }

        if (offline.get()) {
            val existingLock = output.resolve("winmds.lock.json")
            if (existingLock.isRegularFile()) {
                runCatching {
                    validateRestore(existingLock, packageSpecs)
                }.onSuccess {
                    logger.lifecycle("Reusing verified WinApp restore from $output because Gradle is offline.")
                    return
                }
            }
            throw GradleException(
                "WinApp restore cannot run offline because no verified lock/cache exists at $output. " +
                    "Run once without --offline to populate the WinApp and NuGet caches.",
            )
        }

        // WinApp CLI writes `.winapp` below its restore base and NuGet resolves
        // NuGet.Config/credential-provider settings from the current directory and its
        // ancestors. Keep the disposable workspace below the selected restore base so the
        // configured project/user NuGet hierarchy remains effective; the workspace is removed
        // after the lock and package roots have been validated and moved to the task output.
        val restoreWorkspace = createRestoreWorkspace(restoreBase)
        val configWorkspace = Files.createTempDirectory(restoreBase, ".kotlin-winrt-winapp-config-")
        try {
            GradleFileOperations.copyFile(config, configWorkspace.resolve("winapp.yaml"))
            logger.lifecycle("Restoring WinApp dependencies from $config")
            winAppCli().run(
                arguments = listOf(
                    "restore",
                    restoreWorkspace.toString(),
                    "--config-dir",
                    configWorkspace.toString(),
                    "--quiet",
                ),
                workingDirectory = restoreWorkspace,
                description = "restore NuGet dependencies for $workspace",
            )

            val restoredOutput = restoreWorkspace.resolve(".winapp")
            validateRestore(restoredOutput.resolve("winmds.lock.json"), packageSpecs)
            GradleFileOperations.deleteDirectory(output)
            moveDirectory(restoredOutput, output)
        } finally {
            GradleFileOperations.deleteDirectory(restoreWorkspace)
            GradleFileOperations.deleteDirectory(configWorkspace)
        }
    }

    private fun validateRestore(lockFile: Path, packageSpecs: List<String>) {
        val lockfile = WinAppRestoreLockfileReader.read(lockFile)
        val missingWinmds = lockfile.winmdFiles.filterNot(Files::isRegularFile)
        if (missingWinmds.isNotEmpty()) {
            throw GradleException(
                "WinApp restore lockfile references missing WinMD files:${System.lineSeparator()}" +
                    missingWinmds.joinToString(System.lineSeparator()),
            )
        }
        val restoredPackageRoots = readWinAppRestoredPackageRoots(
            lockFiles = listOf(lockFile.toFile()),
            rootPackageSpecs = packageSpecs,
        )
        val missingPackageRoots = restoredPackageRoots.filterNot(Files::isDirectory)
        if (missingPackageRoots.isNotEmpty()) {
            throw GradleException(
                "WinApp restore lockfile references missing NuGet package roots:${System.lineSeparator()}" +
                    missingPackageRoots.joinToString(System.lineSeparator()),
            )
        }
    }

    private fun createRestoreWorkspace(restoreBase: Path): Path {
        return Files.createTempDirectory(restoreBase, ".kotlin-winrt-winapp-")
    }

    private fun moveDirectory(source: Path, target: Path) {
        if (source.root == target.root) {
            Files.move(source, target)
            return
        }
        Files.walk(source).use { stream ->
            stream.forEach { path ->
                val destination = target.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination)
                } else if (Files.isRegularFile(path)) {
                    Files.createDirectories(destination.parent)
                    Files.copy(path, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        GradleFileOperations.deleteDirectory(source)
    }

    private fun winAppCli(): WinAppCliSupport = WinAppCliSupport(
        configuredExecutable = winAppCliExecutable.get(),
        cliVersion = winAppCliVersion.get(),
        packageSha512 = winAppCliPackageSha512.get(),
        cliCacheDirectory = winAppCliCacheDirectory.get().asFile.toPath(),
        offline = offline.get(),
        logger = logger,
    )
}
