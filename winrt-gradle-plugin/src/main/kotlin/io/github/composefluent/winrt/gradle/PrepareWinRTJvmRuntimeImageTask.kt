package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Materializes the JVM image consumed by the native application launcher.
 *
 * A supplied image is copied byte-for-byte. Otherwise the selected build JDK's jlink is used to
 * create a small, deterministic image. The task deliberately does not infer an external runtime
 * from the Gradle daemon: external mode is wired separately and never produces this output.
 */
@DisableCachingByDefault(because = "jlink output is toolchain-specific and may contain platform metadata.")
abstract class PrepareWinRTJvmRuntimeImageTask : DefaultTask() {
    @get:Input
    abstract val runtimeMode: Property<String>

    @get:Input
    abstract val javaHome: Property<String>

    @get:Input
    abstract val expectedJavaMajor: Property<Int>

    @get:Input
    abstract val runtimeIdentifier: Property<String>

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceImage: DirectoryProperty

    @get:Input
    abstract val modules: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        runtimeMode.convention(WinRTJvmRuntimeMode.Bundled.name)
        javaHome.convention("")
        expectedJavaMajor.convention(25)
        runtimeIdentifier.convention(currentWindowsRuntimeIdentifier())
        modules.convention(listOf("java.base", "java.desktop", "java.logging", "java.management", "java.naming", "jdk.crypto.ec", "jdk.management", "jdk.unsupported"))
    }

    @TaskAction
    fun prepare() {
        if (runtimeMode.get() != WinRTJvmRuntimeMode.Bundled.name) {
            GradleFileOperations.cleanDirectory(outputDirectory.get().asFile.toPath())
            return
        }
        val output = outputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val supplied = sourceImage.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
        if (supplied != null) {
            if (!supplied.isDirectory()) {
                throw GradleException("Configured JVM runtime image is not a directory: $supplied")
            }
            if (output == supplied) {
                // Reusing an explicitly supplied image is allowed, but it must still be a valid
                // image. In particular, do not let the output cleanup erase the input first.
            validateImage(supplied, "configured JVM runtime image")
                return
            }
            runtimeImageOverlapError(supplied, output, "configured JVM runtime image")?.let { message ->
                throw GradleException(message)
            }
            GradleFileOperations.cleanDirectory(output)
            copyDirectory(supplied, output)
            validateImage(output, "configured JVM runtime image")
            return
        }

        val javaHomeValue = javaHome.orNull?.trim().orEmpty()
        if (javaHomeValue.isBlank()) {
            throw GradleException(
                "Bundled JVM runtime image requires a resolved Java toolchain home; " +
                    "configure application.jvmToolchain(...) or install the requested Gradle toolchain.",
            )
        }
        val javaRoot = Path.of(javaHomeValue).toAbsolutePath().normalize()
        val jlink = listOf(
            javaRoot.resolve("bin").resolve(if (isWindowsHost()) "jlink.exe" else "jlink"),
            javaRoot.resolve("jre").resolve("bin").resolve(if (isWindowsHost()) "jlink.exe" else "jlink"),
        ).firstOrNull(Path::isRegularFile)
            ?: throw GradleException(
                "Cannot create bundled JVM runtime image because jlink was not found under $javaRoot. " +
                    "Provide application.jvmRuntimeImage or use externalJvmRuntime(...).",
            )
        val requestedModules = modules.get().map(String::trim).filter(String::isNotBlank).distinct()
        if (requestedModules.isEmpty()) {
            throw GradleException("Bundled JVM runtime image requires at least one jlink module.")
        }
        // jlink requires its output path not to exist.  Cleaning the contents is insufficient
        // because Gradle keeps @OutputDirectory itself in place between task executions.
        GradleFileOperations.deleteDirectory(output)
        Files.createDirectories(output.parent)
        val result = runProcess(
            listOf(
                jlink.toString(),
                "--add-modules",
                requestedModules.joinToString(","),
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--compress=2",
                "--output",
                output.toString(),
            ),
            output.parent,
        )
        if (result.exitCode != 0) {
            throw GradleException(
                "Failed to create bundled JVM runtime image with $jlink (exit code ${result.exitCode}).\n${result.output}",
            )
        }
        validateImage(output, "generated JVM runtime image")
    }

    private fun validateImage(root: Path, description: String) {
        val launcherName = if (isWindowsHost()) "java.exe" else "java"
        val launcher = listOf(
            root.resolve("bin").resolve(launcherName),
            root.resolve("jre").resolve("bin").resolve(launcherName),
        ).firstOrNull(Path::isRegularFile)
        val jvm = listOf(
            root.resolve("bin").resolve("server").resolve("jvm.dll"),
            root.resolve("jre").resolve("bin").resolve("server").resolve("jvm.dll"),
            root.resolve("bin").resolve("jvm.dll"),
        ).firstOrNull(Path::isRegularFile)
        if (launcher == null || (isWindowsHost() && jvm == null)) {
            throw GradleException(
                "$description at $root is incomplete; expected $launcherName" +
                    if (isWindowsHost()) " and a Windows JVM library (bin/server/jvm.dll, jre/bin/server/jvm.dll, or bin/jvm.dll)." else ".",
            )
        }
        validateJvmRuntime(root, expectedJavaMajor.get(), runtimeIdentifier.get(), description)
    }

    private fun copyDirectory(source: Path, target: Path) {
        Files.walk(source).use { stream ->
            stream.forEach { path ->
                val relative = source.relativize(path)
                val destination = target.resolve(relative.toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination)
                } else if (Files.isRegularFile(path)) {
                    Files.createDirectories(destination.parent)
                    Files.copy(path, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun runProcess(arguments: List<String>, workingDirectory: Path): JvmRuntimeProcessResult {
        val output = ByteArrayOutputStream()
        val process = ProcessBuilder(arguments)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        process.inputStream.copyTo(output)
        return JvmRuntimeProcessResult(process.waitFor(), output.toString(Charsets.UTF_8))
    }
}

/** Validates the executable JVM contract instead of accepting a directory by file presence alone. */
internal fun validateJvmRuntime(root: Path, expectedMajor: Int, runtimeIdentifier: String, description: String) {
    val launcherName = if (isWindowsHost()) "java.exe" else "java"
    val launcher = listOf(
        root.resolve("bin").resolve(launcherName),
        root.resolve("jre").resolve("bin").resolve(launcherName),
    ).firstOrNull(Path::isRegularFile)
        ?: throw GradleException("$description at $root does not contain a runnable $launcherName.")
    val process = runCatching {
        ProcessBuilder(
            launcher.toString(),
            "-XshowSettings:properties",
            "-version",
        ).redirectErrorStream(true).start().let { child ->
            val output = child.inputStream.bufferedReader().readText()
            val exitCode = child.waitFor()
            exitCode to output
        }
    }.getOrElse { error ->
        throw GradleException("Cannot inspect $description at $root: ${error.message}", error)
    }
    if (process.first != 0) {
        throw GradleException(
            "Cannot inspect $description at $root (java exited with ${process.first}).\n${process.second}",
        )
    }
    val version = Regex("(?:java.version|java.runtime.version)\\s*=\\s*(\\d+)")
        .find(process.second)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (version == null || version != expectedMajor) {
        throw GradleException(
            "$description at $root uses Java ${version ?: "an unknown version"}, " +
                "but Java $expectedMajor is required for this application.",
        )
    }
    val architecture = Regex("os.arch\\s*=\\s*([^\\r\\n]+)")
        .find(process.second)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.lowercase()
    val expectedArchitecture = when (runtimeIdentifier.lowercase()) {
        "win-x64" -> setOf("amd64", "x86_64", "x64")
        "win-x86" -> setOf("x86", "i386", "i686")
        "win-arm64" -> setOf("aarch64", "arm64")
        else -> emptySet()
    }
    if (expectedArchitecture.isNotEmpty() && architecture !in expectedArchitecture) {
        throw GradleException(
            "$description at $root reports architecture '${architecture ?: "unknown"}', " +
                "but runtime identifier '$runtimeIdentifier' requires ${expectedArchitecture.joinToString("/")}.",
        )
    }
}

private data class JvmRuntimeProcessResult(
    val exitCode: Int,
    val output: String,
)

/**
 * Returns a diagnostic when cleaning [output] could delete [source], or when copying [source]
 * into [output] would recurse. The paths are canonicalized through existing ancestors so a
 * symlinked image/output cannot bypass the check.
 */
internal fun runtimeImageOverlapError(source: Path, output: Path, description: String): String? {
    val normalizedSource = canonicalPathForOverlap(source)
    val normalizedOutput = canonicalPathForOverlap(output)
    if (sameOrDescendant(normalizedSource, normalizedOutput)) {
        return "$description source cannot be inside the output directory: " +
            "source=$normalizedSource, output=$normalizedOutput"
    }
    if (sameOrDescendant(normalizedOutput, normalizedSource)) {
        return "$description output cannot be inside the source image: " +
            "source=$normalizedSource, output=$normalizedOutput"
    }
    return null
}

private fun canonicalPathForOverlap(path: Path): Path {
    val normalized = path.toAbsolutePath().normalize()
    val missing = ArrayDeque<Path>()
    var existing = normalized
    while (!Files.exists(existing) && existing.parent != null) {
        missing.addFirst(existing.fileName)
        existing = existing.parent
    }
    val canonicalExisting = runCatching { existing.toRealPath() }.getOrDefault(existing)
    return missing.fold(canonicalExisting) { parent, child -> parent.resolve(child) }.normalize()
}

private fun sameOrDescendant(path: Path, parent: Path): Boolean {
    if (path.startsWith(parent)) return true
    if (!isWindowsHost()) return false
    val pathText = path.toString().trimEnd('\\', '/')
    val parentText = parent.toString().trimEnd('\\', '/')
    return pathText.equals(parentText, ignoreCase = true) ||
        pathText.startsWith("$parentText\\", ignoreCase = true) ||
        pathText.startsWith("$parentText/", ignoreCase = true)
}
