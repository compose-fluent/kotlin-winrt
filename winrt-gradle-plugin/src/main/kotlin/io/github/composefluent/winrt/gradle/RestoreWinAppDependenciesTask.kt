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
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
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

    /** Package roots are inputs because the restore lock points into the external NuGet cache. */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageContentFiles: ConfigurableFileCollection

    /** Tracks inherited NuGet.Config files that WinApp CLI will discover from restoreBaseDirectory. */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nugetConfigHierarchyFiles: ConfigurableFileCollection

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
        // The task still updates external caches, but its declared workspace output and
        // configuration inputs are sufficient for Gradle's local up-to-date checks. Keeping
        // state tracking enabled prevents every compile from invoking WinApp restore.
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
        val packageSpecs = nugetPackages.get() + dependencyIdentityFiles.files.flatMap(::readNuGetPackages)
        val existingLock = output.resolve("winmds.lock.json")
        if (restoreEnabled.get() && existingLock.isRegularFile()) {
            runCatching {
                validateRestore(existingLock, packageSpecs)
                validateRestoreContext(output, config, restoreBase, packageSpecs, existingLock)
            }.onSuccess {
                logger.lifecycle("Reusing verified WinApp restore from $output.")
                return
            }
        }
        if (!restoreEnabled.get()) {
            GradleFileOperations.cleanDirectory(output)
            return
        }

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
            writeRestoreContext(output, config, restoreBase, packageSpecs, winmdLockFile.get().asFile.toPath())
            return
        }

        if (offline.get()) {
            val existingLock = output.resolve("winmds.lock.json")
            if (existingLock.isRegularFile()) {
                runCatching {
                    validateRestore(existingLock, packageSpecs)
                    validateRestoreContext(output, config, restoreBase, packageSpecs, existingLock)
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
            writeRestoreContext(
                restoredOutput,
                config,
                restoreBase,
                packageSpecs,
                restoredOutput.resolve("winmds.lock.json"),
            )
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
        val emptyPackageRoots = lockfile.packages.mapNotNull { packageEntry ->
            val root = packageRoot(lockFile, lockfile, packageEntry)
            if (Files.isDirectory(root) && Files.walk(root).use { stream -> stream.anyMatch(Files::isRegularFile) }) {
                null
            } else {
                root
            }
        }
        if (emptyPackageRoots.isNotEmpty()) {
            throw GradleException(
                "WinApp restore lockfile references empty NuGet package roots:${System.lineSeparator()}" +
                    emptyPackageRoots.joinToString(System.lineSeparator()),
            )
        }
    }

    private fun writeRestoreContext(
        output: Path,
        config: Path,
        restoreBase: Path,
        packageSpecs: List<String>,
        lockFile: Path,
    ) {
        Files.createDirectories(output)
        Files.writeString(output.resolve(RESTORE_CONTEXT_FILE), restoreContext(config, restoreBase, packageSpecs, lockFile))
    }

    private fun validateRestoreContext(
        output: Path,
        config: Path,
        restoreBase: Path,
        packageSpecs: List<String>,
        lockFile: Path,
    ) {
        val context = output.resolve(RESTORE_CONTEXT_FILE)
        if (!context.isRegularFile()) {
            throw GradleException(
                "WinApp restore cache at $output has no verified restore context. " +
                    "Run restore once without --offline.",
            )
        }
        val expected = restoreContext(config, restoreBase, packageSpecs, lockFile)
        val actual = Files.readString(context)
        if (actual != expected) {
            throw GradleException(
                "WinApp restore cache at $output does not match the current configuration, NuGet.Config, " +
                    "tool version, or restored package contents. Run restore once without --offline.",
            )
        }
    }

    private fun restoreContext(config: Path, restoreBase: Path, packageSpecs: List<String>, lock: Path): String {
        val inventory = if (lock.isRegularFile()) packageInventory(lock) else "pending"
        val lines = linkedMapOf(
            "schema" to "1",
            "configurationSha256" to sha256(config),
            "nugetConfigSha256" to effectiveNuGetConfigFingerprint(restoreBase),
            "packageSpecs" to packageSpecs.joinToString("\u001f"),
            "includeToolingPackages" to includeToolingPackages.get().toString(),
            "winAppCliVersion" to winAppCliVersion.get(),
            "winAppCliPackageSha512" to winAppCliPackageSha512.get(),
            "dependencyIdentitySha256" to dependencyIdentityFingerprint(),
            "packageInventory" to inventory,
        )
        return lines.entries.joinToString(System.lineSeparator()) { (key, value) -> "$key=$value" } +
            System.lineSeparator()
    }

    private fun dependencyIdentityFingerprint(): String =
        dependencyIdentityFiles.files
            .filter(File::isFile)
            .sortedBy(File::getAbsolutePath)
            .joinToString("\u001f") { file -> "${file.name}:${sha256(file.toPath())}" }

    private fun effectiveNuGetConfigFingerprint(restoreBase: Path): String =
        effectiveNuGetConfigFiles(restoreBase)
            .joinToString("\u001f") { file -> "${file.toAbsolutePath().normalize()}:${sha256(file)}" }

    private fun effectiveNuGetConfigFiles(restoreBase: Path): List<Path> {
        val files = linkedSetOf<Path>()
        var current: Path? = restoreBase.toAbsolutePath().normalize()
        while (current != null) {
            Files.list(current).use { entries ->
                entries.filter { path ->
                    Files.isRegularFile(path) && path.fileName.toString().equals("NuGet.Config", ignoreCase = true)
                }.forEach { files.add(it.toAbsolutePath().normalize()) }
            }
            current = current.parent
        }
        System.getenv("APPDATA")?.takeIf(String::isNotBlank)?.let { appData ->
            val userConfig = Path.of(appData).resolve("NuGet").resolve("NuGet.Config")
            if (Files.isRegularFile(userConfig)) files.add(userConfig.toAbsolutePath().normalize())
        }
        return files.sortedBy { it.toString().lowercase() }
    }

    private fun packageInventory(lockFile: Path): String {
        val lockfile = WinAppRestoreLockfileReader.read(lockFile)
        val digest = MessageDigest.getInstance("SHA-256")
        lockfile.packages.sortedBy { "${it.name.lowercase()}:${it.version.lowercase()}" }.forEach { packageEntry ->
            val root = packageRoot(lockFile, lockfile, packageEntry)
            if (!Files.isDirectory(root)) return@forEach
            Files.walk(root).use { stream ->
                stream.filter(Files::isRegularFile).sorted().forEach { file ->
                    digest.update(root.relativize(file).toString().replace('\\', '/').toByteArray())
                    digest.update(0.toByte())
                    digest.update(sha256(file).toByteArray())
                    digest.update(0.toByte())
                }
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun packageRoot(lockFile: Path, lockfile: WinAppRestoreLockfile, packageEntry: WinAppRestoredPackage): Path {
        val cacheRoot = lockfile.nugetCacheDirectory
            ?: throw GradleException("WinApp restore lockfile $lockFile has no absolute nuget_cache_dir.")
        if (!cacheRoot.isAbsolute) {
            throw GradleException("WinApp restore lockfile $lockFile has a relative nuget_cache_dir.")
        }
        val root = cacheRoot.resolve(packageEntry.name.lowercase()).resolve(packageEntry.version).normalize()
        if (!root.startsWith(cacheRoot.normalize())) {
            throw GradleException("WinApp restore lockfile $lockFile contains an unsafe package path for ${packageEntry.name}.")
        }
        return root
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
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

private const val RESTORE_CONTEXT_FILE = "restore-context.sha256"
