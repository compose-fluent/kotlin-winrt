package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareWinRTJvmRuntimeImageTaskTest {
    @Test
    fun copies_a_supplied_bundled_runtime_image() {
        val root = Files.createTempDirectory("kotlin-winrt-jvm-image-")
        val source = root.resolve("source")
        val output = root.resolve("output")
        Files.createDirectories(source.resolve("bin/server"))
        Files.writeString(source.resolve("bin/java.exe"), "java")
        Files.writeString(source.resolve("bin/server/jvm.dll"), "jvm")

        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.create("prepareRuntime", PrepareWinRTJvmRuntimeImageTask::class.java)
        task.sourceImage.set(source.toFile())
        task.outputDirectory.set(output.toFile())
        task.prepare()

        assertTrue(Files.isRegularFile(output.resolve("bin/java.exe")))
        assertTrue(Files.isRegularFile(output.resolve("bin/server/jvm.dll")))
    }

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
}
