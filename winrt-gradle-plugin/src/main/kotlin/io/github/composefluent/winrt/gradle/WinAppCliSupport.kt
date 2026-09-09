package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.InputStream
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Comparator
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipInputStream
import kotlin.concurrent.withLock
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

internal object WinAppCliDefaults {
    const val VERSION: String = "0.6.0"

    // SHA-512 published by the NuGet CDN for Microsoft.Windows.SDK.BuildTools.WinApp 0.6.0.
    const val PACKAGE_SHA512: String =
        "1ba4ff25d575fcba7e7cfe2afb72b2e238d0874650e3d6f03b7c53765057907c" +
            "fe1c3ce5904338eeecab20c734bcf65524faa61dd4ab4299c29c21839256c1be"

    fun packageUri(version: String = VERSION): URI {
        val normalizedVersion = version.lowercase()
        return URI(
            "https://api.nuget.org/v3-flatcontainer/" +
                "microsoft.windows.sdk.buildtools.winapp/$normalizedVersion/" +
                "microsoft.windows.sdk.buildtools.winapp.$normalizedVersion.nupkg",
        )
    }
}

internal enum class WinAppCliSource {
    System,
    Managed,
}

internal data class ResolvedWinAppCli(
    val command: String,
    val version: String,
    val source: WinAppCliSource,
)

internal data class WinAppCliInvocation(
    val executable: String,
    val exitCode: Int,
    val output: String,
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}

internal class WinAppCliSupport(
    private val configuredExecutable: String,
    private val cliVersion: String,
    private val packageSha512: String,
    private val cliCacheDirectory: Path,
    private val offline: Boolean,
    private val logger: Logger,
    private val hostRuntimeIdentifier: String = winAppCliHostRuntimeIdentifier(),
    private val packageUri: URI = WinAppCliDefaults.packageUri(cliVersion),
    private val packageStream: (URI) -> InputStream = { uri -> uri.toURL().openStream() },
    private val commandRunner: (
        executable: String,
        arguments: List<String>,
        workingDirectory: Path?,
        environment: Map<String, String>,
    ) -> WinAppCliInvocation = ::invokeWinAppCli,
) {
    @Volatile
    private var resolvedExecutable: ResolvedWinAppCli? = null

    fun resolve(): ResolvedWinAppCli =
        resolvedExecutable ?: resolveLock.withLock {
            resolvedExecutable ?: resolveUncached().also { resolvedExecutable = it }
        }

    fun run(
        arguments: List<String>,
        workingDirectory: Path? = null,
        environment: Map<String, String> = emptyMap(),
        description: String,
    ): WinAppCliInvocation {
        val resolved = resolve()
        val invocation = commandRunner(resolved.command, arguments, workingDirectory, environment)
        if (!invocation.isSuccess) {
            throw GradleException(
                "WinApp CLI ${resolved.version} failed to $description " +
                    "with exit code ${invocation.exitCode}:${System.lineSeparator()}${invocation.output}",
            )
        }
        if (invocation.output.isNotBlank()) {
            logger.info(invocation.output.trimEnd())
        }
        return invocation
    }

    private fun resolveUncached(): ResolvedWinAppCli {
        val systemCommand = configuredExecutable.trim().ifEmpty { "winapp" }
        val systemProbe = probe(systemCommand)
        if (systemProbe == cliVersion) {
            return ResolvedWinAppCli(systemCommand, systemProbe, WinAppCliSource.System)
        }
        if (systemProbe != null) {
            logger.info(
                "System WinApp CLI '$systemCommand' reports version $systemProbe; " +
                    "kotlin-winrt requires $cliVersion and will use its managed copy.",
            )
        } else {
            logger.info(
                "System WinApp CLI '$systemCommand' is unavailable; " +
                    "kotlin-winrt will use its managed copy.",
            )
        }

        val managedExecutable = withProvisioningLock {
            val cached = cachedExecutable()
            if (!isCompleteCache(cached) || probe(cached.toString()) != cliVersion) {
                if (offline) {
                    throw GradleException(
                        "WinApp CLI $cliVersion is not available in $cliCacheDirectory and Gradle is offline. " +
                            "Install a compatible 'winapp' command or run once without --offline to populate the cache.",
                    )
                }
                provision(cached)
            }
            cached
        }
        val managedVersion = probe(managedExecutable.toString())
        if (managedVersion != cliVersion) {
            throw GradleException(
                "Managed WinApp CLI at $managedExecutable reports version " +
                    "${managedVersion ?: "<unavailable>"}; expected $cliVersion.",
            )
        }
        return ResolvedWinAppCli(managedExecutable.toString(), managedVersion, WinAppCliSource.Managed)
    }

    private fun probe(executable: String): String? {
        val invocation = commandRunner(executable, listOf("--version"), null, emptyMap())
        if (!invocation.isSuccess) {
            return null
        }
        return VERSION_PATTERN.find(invocation.output)?.value
    }

    private fun cachedExecutable(): Path =
        cliCacheDirectory
            .resolve(cliVersion)
            .resolve(packageSha512.lowercase().take(CACHE_KEY_SHA512_PREFIX_LENGTH))
            .resolve(hostRuntimeIdentifier)
            .resolve(WINAPP_EXECUTABLE_NAME)

    private fun isCompleteCache(executable: Path): Boolean =
        executable.isRegularFile() &&
            executable.parent.resolve(COMPLETE_MARKER_NAME).let { marker ->
                marker.isRegularFile() && runCatching { Files.readString(marker) == completeMarkerContent() }.getOrDefault(false)
            }

    private fun provision(targetExecutable: Path) {
        requireSupportedHostRuntimeIdentifier()
        val targetDirectory = targetExecutable.parent
        val versionDirectory = targetDirectory.parent
        Files.createDirectories(versionDirectory)
        if (targetDirectory.isDirectory()) {
            GradleFileOperations.deleteDirectory(targetDirectory)
        }

        val archive = Files.createTempFile(versionDirectory, "winapp-cli-", ".nupkg.part")
        val extraction = Files.createTempDirectory(versionDirectory, "winapp-cli-extract-")
        try {
            logger.lifecycle("Downloading WinApp CLI $cliVersion from $packageUri")
            val actualSha512 = downloadPackage(archive)
            if (!actualSha512.equals(packageSha512, ignoreCase = true)) {
                throw GradleException(
                    "WinApp CLI package checksum mismatch for $packageUri: " +
                        "expected $packageSha512, got $actualSha512.",
                )
            }
            extractHostTools(archive, extraction)
            val extractedExecutable = extraction.resolve(WINAPP_EXECUTABLE_NAME)
            if (!extractedExecutable.isRegularFile()) {
                throw GradleException(
                    "Microsoft.Windows.SDK.BuildTools.WinApp $cliVersion does not contain " +
                        "tools/$hostRuntimeIdentifier/$WINAPP_EXECUTABLE_NAME.",
                )
            }
            Files.writeString(
                extraction.resolve(COMPLETE_MARKER_NAME),
                completeMarkerContent(),
            )
            moveDirectory(extraction, targetDirectory)
        } finally {
            Files.deleteIfExists(archive)
            if (extraction.isDirectory()) {
                GradleFileOperations.deleteDirectory(extraction)
            }
        }
    }

    private fun downloadPackage(target: Path): String {
        val digest = MessageDigest.getInstance("SHA-512")
        packageStream(packageUri).use { input ->
            DigestInputStream(input, digest).use { hashingInput ->
                Files.newOutputStream(target).use { output ->
                    hashingInput.copyTo(output)
                }
            }
        }
        return digest.digest().toHexString()
    }

    private fun extractHostTools(archive: Path, destination: Path) {
        val prefix = "tools/$hostRuntimeIdentifier/"
        Files.newInputStream(archive).use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val normalizedName = entry.name.replace('\\', '/')
                    if (!entry.isDirectory && normalizedName.startsWith(prefix, ignoreCase = true)) {
                        val relativeName = normalizedName.substring(prefix.length)
                        if (relativeName.isNotBlank()) {
                            val output = destination.resolve(relativeName).normalize()
                            if (!output.startsWith(destination)) {
                                throw GradleException("WinApp CLI package contains an unsafe entry: ${entry.name}")
                            }
                            Files.createDirectories(output.parent)
                            Files.newOutputStream(output).use { file -> zip.copyTo(file) }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun moveDirectory(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun requireSupportedHostRuntimeIdentifier() {
        if (hostRuntimeIdentifier != "win-x64" && hostRuntimeIdentifier != "win-arm64") {
            throw GradleException(
                "The managed WinApp CLI distribution does not support host runtime identifier " +
                    "'$hostRuntimeIdentifier'. Install a compatible system 'winapp' command instead.",
            )
        }
    }

    private fun completeMarkerContent(): String =
        "version=$cliVersion\nsha512=${packageSha512.lowercase()}\n"

    private fun <T> withProvisioningLock(action: () -> T): T = provisioningJvmLock.withLock {
        val lockFile = cliCacheDirectory.resolve(PROVISIONING_LOCK_FILE_NAME)
        Files.createDirectories(lockFile.parent)
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            acquireProvisioningLock(channel, lockFile).use { action() }
        }
    }

    private fun acquireProvisioningLock(channel: FileChannel, lockFile: Path): FileLock {
        while (true) {
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock != null) {
                return lock
            }
            logger.info("Waiting for WinApp CLI provisioning lock at $lockFile.")
            try {
                Thread.sleep(PROVISIONING_LOCK_RETRY_MILLIS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw GradleException("Interrupted while waiting for WinApp CLI provisioning lock at $lockFile.", error)
            }
        }
    }

    companion object {
        private const val WINAPP_EXECUTABLE_NAME = "winapp.exe"
        private const val COMPLETE_MARKER_NAME = ".complete"
        private const val CACHE_KEY_SHA512_PREFIX_LENGTH = 16
        private const val PROVISIONING_LOCK_FILE_NAME = "winapp-cli.lock"
        private const val PROVISIONING_LOCK_RETRY_MILLIS = 250L
        private val VERSION_PATTERN = Regex("""\b\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?\b""")
        private val provisioningJvmLock = ReentrantLock()
        private val resolveLock = ReentrantLock()
    }
}

private fun invokeWinAppCli(
    executable: String,
    arguments: List<String>,
    workingDirectory: Path?,
    environment: Map<String, String>,
): WinAppCliInvocation {
    val processBuilder = ProcessBuilder(winAppCliCommandLine(executable, arguments))
        .redirectErrorStream(true)
    if (workingDirectory != null) {
        processBuilder.directory(workingDirectory.toFile())
    }
    processBuilder.environment().putAll(environment)
    val process = runCatching { processBuilder.start() }.getOrElse { error ->
        return WinAppCliInvocation(
            executable = executable,
            exitCode = -1,
            output = error.message.orEmpty(),
        )
    }
    val output = process.inputStream.bufferedReader().use { it.readText() }
    return WinAppCliInvocation(
        executable = executable,
        exitCode = process.waitFor(),
        output = output,
    )
}

internal fun winAppCliCommandLine(executable: String, arguments: List<String>): List<String> =
    if (
        System.getProperty("os.name").contains("Windows", ignoreCase = true) &&
        (executable.endsWith(".cmd", ignoreCase = true) || executable.endsWith(".bat", ignoreCase = true))
    ) {
        listOf("cmd.exe", "/d", "/c", executable) + arguments
    } else {
        listOf(executable) + arguments
    }

private fun winAppCliHostRuntimeIdentifier(): String {
    val architecture = System.getProperty("os.arch").lowercase()
    return when {
        "aarch64" in architecture || "arm64" in architecture -> "win-arm64"
        "x86" in architecture && "64" !in architecture -> "win-x86"
        else -> "win-x64"
    }
}

private fun ByteArray.toHexString(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte) }
