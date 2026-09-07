package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareWinRTJvmRuntimeImageTaskTest {
    @Test
    fun copies_a_supplied_bundled_runtime_image() {
        val root = Files.createTempDirectory("kotlin-winrt-jvm-image-")
        val source = createValidRuntimeImage(root.resolve("source"))
        val output = root.resolve("output")

        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.create("prepareRuntime", PrepareWinRTJvmRuntimeImageTask::class.java)
        task.sourceImage.set(source.toFile())
        task.outputDirectory.set(output.toFile())
        task.expectedJavaMajor.set(currentJavaMajor())
        task.runtimeIdentifier.set(currentWindowsRuntimeIdentifier())
        task.prepare()

        assertTrue(Files.isRegularFile(output.resolve("bin/java.exe")))
        assertTrue(Files.isRegularFile(output.resolve("bin/server/jvm.dll")))
    }

    private fun createValidRuntimeImage(output: java.nio.file.Path): java.nio.file.Path {
        val javaHome = java.nio.file.Path.of(System.getProperty("java.home"))
        val jlink = javaHome.resolve("bin").resolve(if (isWindowsHost()) "jlink.exe" else "jlink")
        assertTrue("Test JVM must provide jlink: $jlink", Files.isRegularFile(jlink))
        val process = ProcessBuilder(
            jlink.toString(),
            "--add-modules",
            "java.base",
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--output",
            output.toString(),
        ).redirectErrorStream(true).start()
        val outputText = process.inputStream.bufferedReader().readText()
        assertEquals("jlink output:\n$outputText", 0, process.waitFor())
        return output
    }

    private fun currentJavaMajor(): Int =
        Regex("^(\\d+)")
            .find(System.getProperty("java.specification.version"))
            ?.groupValues
            ?.getOrNull(1)
            ?.toInt()
            ?: error("Could not determine the current test JVM major version")

    @Test
    fun external_mode_clears_stale_bundled_output_without_creating_an_image() {
        val root = Files.createTempDirectory("kotlin-winrt-jvm-external-")
        val output = root.resolve("output")
        Files.createDirectories(output)
        Files.writeString(output.resolve("stale.txt"), "stale")

        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.create("prepareRuntime", PrepareWinRTJvmRuntimeImageTask::class.java)
        task.runtimeMode.set(WinRTJvmRuntimeMode.External.name)
        task.outputDirectory.set(output.toFile())
        task.prepare()

        assertFalse(Files.exists(output.resolve("stale.txt")))
        assertTrue(Files.isDirectory(output))
    }

    @Test
    fun rejects_an_output_directory_inside_the_supplied_image() {
        val root = Files.createTempDirectory("kotlin-winrt-jvm-image-nested-")
        val source = root.resolve("source")
        val output = source.resolve("generated")
        Files.createDirectories(source.resolve("bin/server"))
        Files.writeString(source.resolve("bin/java.exe"), "java")
        Files.writeString(source.resolve("bin/server/jvm.dll"), "jvm")

        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.create("prepareRuntime", PrepareWinRTJvmRuntimeImageTask::class.java)
        task.sourceImage.set(source.toFile())
        task.outputDirectory.set(output.toFile())

        val error = runCatching { task.prepare() }.exceptionOrNull()

        assertTrue(error is GradleException)
        assertTrue(error!!.message.orEmpty().contains("inside the source image"))
    }

    @Test
    fun rejects_a_source_image_inside_the_output_without_deleting_the_source() {
        val root = Files.createTempDirectory("kotlin-winrt-jvm-image-output-nested-")
        val output = root.resolve("output")
        val source = output.resolve("source")
        Files.createDirectories(source.resolve("bin/server"))
        val sentinel = source.resolve("sentinel.txt")
        Files.writeString(source.resolve("bin/java.exe"), "java")
        Files.writeString(source.resolve("bin/server/jvm.dll"), "jvm")
        Files.writeString(sentinel, "must-survive")

        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.create("prepareRuntime", PrepareWinRTJvmRuntimeImageTask::class.java)
        task.sourceImage.set(source.toFile())
        task.outputDirectory.set(output.toFile())

        val error = runCatching { task.prepare() }.exceptionOrNull()

        assertTrue(error is GradleException)
        assertTrue(error!!.message.orEmpty().contains("inside the output directory"))
        assertTrue(Files.isRegularFile(sentinel))
        assertTrue(Files.readString(sentinel) == "must-survive")
    }

    @Test
    fun validates_an_in_place_supplied_image_instead_of_cleaning_it() {
        val root = Files.createTempDirectory("kotlin-winrt-jvm-image-same-")
        val image = root.resolve("image")
        Files.createDirectories(image)
        val sentinel = image.resolve("sentinel.txt")
        Files.writeString(sentinel, "must-survive")

        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.create("prepareRuntime", PrepareWinRTJvmRuntimeImageTask::class.java)
        task.sourceImage.set(image.toFile())
        task.outputDirectory.set(image.toFile())

        val error = runCatching { task.prepare() }.exceptionOrNull()

        assertTrue(error is GradleException)
        assertTrue(error!!.message.orEmpty().contains("configured JVM runtime image"))
        assertTrue(Files.isRegularFile(sentinel))
        assertTrue(Files.readString(sentinel) == "must-survive")
    }
}
