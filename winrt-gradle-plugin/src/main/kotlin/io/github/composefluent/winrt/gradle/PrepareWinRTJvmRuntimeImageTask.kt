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
        javaHome.convention(System.getProperty("java.home"))
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
            if (output == supplied) return
            if (output.startsWith(supplied)) {
                throw GradleException(
                    "Configured JVM runtime image output cannot be inside the source image: " +
                        "source=$supplied, output=$output",
                )
            }
            GradleFileOperations.cleanDirectory(output)
            copyDirectory(supplied, output)
            validateImage(output, "configured JVM runtime image")
            return
        }

        val javaRoot = Path.of(javaHome.get()).toAbsolutePath().normalize()
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

private data class JvmRuntimeProcessResult(
    val exitCode: Int,
    val output: String,
)
