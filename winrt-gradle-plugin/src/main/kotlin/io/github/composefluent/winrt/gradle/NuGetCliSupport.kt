package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipInputStream
import kotlin.concurrent.withLock
import kotlin.io.path.notExists

internal class NuGetCliSupport(
    private val executable: String,
    private val cliVersion: String,
    private val cliCacheDirectory: Path,
    private val scratchDirectory: Path? = null,
    private val logger: Logger,
) {
    fun run(
        arguments: List<String>,
        workingDirectory: Path? = null,
        description: String,
    ): NuGetInvocation = if (arguments.isNuGetInstallCommand()) {
        val effectiveArguments = arguments.withNuGetInstallStabilityArguments()
        withNuGetInstallLock(description) {
            runUnlocked(effectiveArguments, workingDirectory, description)
        }
    } else {
        runUnlocked(arguments, workingDirectory, description)
    }

    private fun runUnlocked(
        arguments: List<String>,
        workingDirectory: Path?,
        description: String,
    ): NuGetInvocation {
        val configuredInvocation = invokeNuGet(executable, arguments, workingDirectory)
        if (configuredInvocation.isSuccess) {
            return configuredInvocation
        }

        val cachedCommand = cachedNuGetCommand()
        if (cachedCommand == executable) {
            throw nugetFailure(description, configuredInvocation, null)
        }

        logger.info(
            "Configured Microsoft NuGet CLI '$executable' failed for $description; " +
                "retrying with cached CLI $cachedCommand.",
        )
        val cachedInvocation = invokeNuGet(cachedCommand, arguments, workingDirectory)
        if (cachedInvocation.isSuccess) {
            return cachedInvocation
        }

        throw nugetFailure(description, configuredInvocation, cachedInvocation)
    }

    private fun <T> withNuGetInstallLock(
        description: String,
        action: () -> T,
    ): T = installJvmLock.withLock {
        val lockFile = cliCacheDirectory.resolve(INSTALL_LOCK_FILE_NAME)
        Files.createDirectories(lockFile.parent)
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            acquireNuGetInstallLock(channel, lockFile, description).use {
                action()
            }
        }
    }

    private fun acquireNuGetInstallLock(
        channel: FileChannel,
        lockFile: Path,
        description: String,
    ): FileLock {
        while (true) {
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock != null) {
                return lock
            }
            logger.info("Waiting for NuGet install lock at $lockFile before $description.")
            runCatching {
                Thread.sleep(INSTALL_LOCK_RETRY_MILLIS)
            }.onFailure { error ->
                if (error is InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw GradleException("Interrupted while waiting for NuGet install lock at $lockFile.", error)
                }
                throw error
            }
        }
    }

    private fun invokeNuGet(
        executable: String,
        arguments: List<String>,
        workingDirectory: Path?,
    ): NuGetInvocation {
        val command = listOf(executable) + arguments
        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(true)
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile())
        }
        if (scratchDirectory != null) {
            Files.createDirectories(scratchDirectory)
            val scratchPath = scratchDirectory.toString()
            processBuilder.environment()["TEMP"] = scratchPath
            processBuilder.environment()["TMP"] = scratchPath
            processBuilder.environment()["TMPDIR"] = scratchPath
            processBuilder.environment()["NUGET_SCRATCH"] = scratchPath
        }
        val process = runCatching { processBuilder.start() }.getOrElse { error ->
            return NuGetInvocation(
                executable = executable,
                exitCode = -1,
                output = error.message.orEmpty(),
            )
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return NuGetInvocation(
            executable = executable,
            exitCode = exitCode,
            output = output,
        )
    }

    private fun cachedNuGetCommand(): String {
        val cachedExecutable = cliCacheDirectory
            .resolve(cliVersion)
            .resolve("nuget.exe")
        if (cachedExecutable.notExists()) {
            downloadNuGetCli(cachedExecutable)
        }
        return cachedExecutable.toString()
    }

    private fun downloadNuGetCli(targetExecutable: Path) {
        val packageUrl = URI(
            "https://api.nuget.org/v3-flatcontainer/nuget.commandline/$cliVersion/nuget.commandline.$cliVersion.nupkg",
        ).toURL()
        Files.createDirectories(targetExecutable.parent)
        logger.lifecycle("Downloading Microsoft NuGet CLI $cliVersion to $targetExecutable")
        packageUrl.openStream().use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }
                    .firstOrNull { entry -> entry.name.equals("tools/nuget.exe", ignoreCase = true) }
                    ?: throw GradleException("NuGet.CommandLine $cliVersion package does not contain tools/nuget.exe.")
                Files.newOutputStream(targetExecutable).use { output ->
                    zip.copyTo(output)
                }
            }
        }
        if (targetExecutable.notExists()) {
            throw GradleException("Failed to cache Microsoft NuGet CLI at $targetExecutable")
        }
    }

    private fun nugetFailure(
        description: String,
        configuredInvocation: NuGetInvocation,
        cachedInvocation: NuGetInvocation?,
    ): GradleException {
        val configuredMessage =
            "configured '${configuredInvocation.executable}' exited ${configuredInvocation.exitCode}:$LINE_SEPARATOR${configuredInvocation.output}"
        val cachedMessage = cachedInvocation?.let {
            "${LINE_SEPARATOR}cached '${it.executable}' exited ${it.exitCode}:$LINE_SEPARATOR${it.output}"
        }.orEmpty()
        return GradleException("Microsoft NuGet CLI failed to $description.$LINE_SEPARATOR$configuredMessage$cachedMessage")
    }

    companion object {
        private const val INSTALL_LOCK_FILE_NAME = "nuget-install.lock"
        private const val INSTALL_LOCK_RETRY_MILLIS = 250L
        private val installJvmLock = ReentrantLock()
    }
}

internal data class NuGetInvocation(
    val executable: String,
    val exitCode: Int,
    val output: String,
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}

private val LINE_SEPARATOR: String = System.lineSeparator()

private fun List<String>.isNuGetInstallCommand(): Boolean =
    firstOrNull()?.equals("install", ignoreCase = true) == true

private fun List<String>.withNuGetInstallStabilityArguments(): List<String> =
    this + listOfMissingNuGetOptions("-DisableParallelProcessing")

private fun List<String>.listOfMissingNuGetOptions(vararg options: String): List<String> {
    val present = asSequence()
        .filter { argument -> argument.startsWith("-") }
        .map { argument -> argument.substringBefore(":").lowercase() }
        .toSet()
    return options.filterNot { option -> option.lowercase() in present }
}
